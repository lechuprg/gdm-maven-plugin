package org.example.gdm.export.neo4j;

import org.example.gdm.exception.ExportException;
import org.example.gdm.export.*;
import org.example.gdm.model.Dependency;
import org.example.gdm.model.DependencyGraph;
import org.example.gdm.model.MavenModule;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Exports dependency graphs to Neo4j database.
 */
public class Neo4jExporter implements DatabaseExporter {

    private static final Logger log = LoggerFactory.getLogger(Neo4jExporter.class);
    private static final int BATCH_SIZE = 500;

    private final String connectionUrl;
    private final String username;
    private final String password;
    private final RetryExecutor retryExecutor;

    private Driver driver;

    public Neo4jExporter(String connectionUrl, String username, String password) {
        this.connectionUrl = connectionUrl;
        this.username = username;
        this.password = password;
        this.retryExecutor = new RetryExecutor();
    }

    @Override
    public void connect() throws ExportException {
        retryExecutor.executeVoid(() -> {
            log.debug("Connecting to Neo4j at {}", connectionUrl);
            driver = GraphDatabase.driver(connectionUrl, AuthTokens.basic(username, password));
            // Verify connection
            driver.verifyConnectivity();
            log.info("Connected to Neo4j successfully");
        }, "Neo4j connection");
    }

    @Override
    public SchemaVersion checkSchemaVersion() throws ExportException {
        return retryExecutor.execute(() -> {
            try (Session session = driver.session()) {
                // Check if schema version exists
                Result result = session.run(
                        "MATCH (v:SchemaVersion {id: 'current'}) RETURN v.version AS version, v.appliedAt AS appliedAt"
                );

                if (result.hasNext()) {
                    Record record = result.single();
                    String version = record.get("version").asString();
                    Object appliedAtValue = record.get("appliedAt").asObject();
                    Instant appliedAt = parseDateTime(appliedAtValue);

                    SchemaVersion schemaVersion = new SchemaVersion(version, appliedAt);
                    log.debug("Found schema version: {}", schemaVersion);
                    return schemaVersion;
                } else {
                    // Create schema version
                    log.info("Creating schema version metadata");
                    session.run(
                            "MERGE (v:SchemaVersion {id: 'current'}) " +
                            "SET v.version = $version, v.appliedAt = datetime()",
                            Map.of("version", SchemaVersion.CURRENT_VERSION)
                    );
                    return new SchemaVersion(SchemaVersion.CURRENT_VERSION, Instant.now());
                }
            }
        }, "Check schema version");
    }

    @Override
    public ExportResult exportGraph(DependencyGraph graph) throws ExportException {
        long startTime = System.currentTimeMillis();

        try (Session session = driver.session()) {
            int modulesExported = 0;
            int dependenciesExported = 0;
            int conflictsDetected = 0;

            // Start a transaction for the entire export
            try (Transaction tx = session.beginTransaction()) {
                // 1. Upsert all modules
                for (MavenModule module : graph.getModules()) {
                    upsertModule(tx, module);
                    modulesExported++;
                }

                // 2. Delete old dependencies from root module
                MavenModule root = graph.getRootModule();
                deleteOldDependencies(tx, root);

                // 3. Create new dependencies in batches
                List<Dependency> dependencies = graph.getDependencies();
                for (int i = 0; i < dependencies.size(); i += BATCH_SIZE) {
                    int end = Math.min(i + BATCH_SIZE, dependencies.size());
                    List<Dependency> batch = dependencies.subList(i, end);

                    for (Dependency dep : batch) {
                        createDependency(tx, dep);
                        dependenciesExported++;
                        if (!dep.isResolved()) {
                            conflictsDetected++;
                        }
                    }

                    log.debug("Processed batch {}-{} of {} dependencies", i + 1, end, dependencies.size());
                }

                // Commit transaction
                tx.commit();
                log.info("Transaction committed successfully");
            }

            long executionTime = System.currentTimeMillis() - startTime;
            return ExportResult.success(modulesExported, dependenciesExported, conflictsDetected, executionTime);

        } catch (ServiceUnavailableException e) {
            throw new ExportException("Neo4j service unavailable: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ExportException("Export failed: " + e.getMessage(), e);
        }
    }

    @Override
    public int cleanupOldVersions(String groupId, String artifactId) throws ExportException {
        return retryExecutor.execute(() -> {
            try (Session session = driver.session()) {
                // Fetch all versions
                Result result = session.run(
                        "MATCH (m:MavenModule {groupId: $groupId, artifactId: $artifactId}) " +
                        "RETURN m.version AS version, id(m) AS nodeId",
                        Map.of("groupId", groupId, "artifactId", artifactId)
                );

                List<VersionInfo> versions = new ArrayList<>();
                while (result.hasNext()) {
                    Record record = result.next();
                    versions.add(new VersionInfo(
                            record.get("version").asString(),
                            record.get("nodeId").asLong()
                    ));
                }

                if (versions.size() <= 1) {
                    log.debug("No old versions to clean up for {}:{}", groupId, artifactId);
                    return 0;
                }

                // Sort versions using Maven ComparableVersion (highest first)
                versions.sort((a, b) -> {
                    org.apache.maven.artifact.versioning.ComparableVersion v1 =
                            new org.apache.maven.artifact.versioning.ComparableVersion(b.version);
                    org.apache.maven.artifact.versioning.ComparableVersion v2 =
                            new org.apache.maven.artifact.versioning.ComparableVersion(a.version);
                    return v1.compareTo(v2);
                });

                // Keep the first (latest), delete the rest
                List<Long> toDelete = versions.subList(1, versions.size())
                        .stream()
                        .map(v -> v.nodeId)
                        .toList();

                // Mark as not latest
                session.run(
                        "MATCH (m:MavenModule) WHERE id(m) IN $ids SET m.isLatest = false",
                        Map.of("ids", toDelete)
                );

                // Delete old versions
                Result deleteResult = session.run(
                        "MATCH (m:MavenModule {groupId: $groupId, artifactId: $artifactId}) " +
                        "WHERE m.isLatest = false " +
                        "DETACH DELETE m " +
                        "RETURN count(m) AS deleted",
                        Map.of("groupId", groupId, "artifactId", artifactId)
                );

                int deleted = deleteResult.single().get("deleted").asInt();
                log.info("Deleted {} old versions of {}:{}", deleted, groupId, artifactId);
                return deleted;
            }
        }, "Cleanup old versions");
    }

    @Override
    public boolean isConnected() {
        return driver != null;
    }

    @Override
    public void close() {
        if (driver != null) {
            try {
                driver.close();
                log.debug("Neo4j connection closed");
            } catch (Exception e) {
                log.warn("Error closing Neo4j connection: {}", e.getMessage());
            }
            driver = null;
        }
    }

    @Override
    public String getDatabaseType() {
        return "neo4j";
    }

    // ========== Private Methods ==========

    private void upsertModule(Transaction tx, MavenModule module) {
        tx.run(
                "MERGE (m:MavenModule {groupId: $groupId, artifactId: $artifactId, version: $version}) " +
                "SET m.packaging = $packaging, " +
                "    m.exportTimestamp = datetime(), " +
                "    m.isLatest = true",
                Map.of(
                        "groupId", module.getGroupId(),
                        "artifactId", module.getArtifactId(),
                        "version", module.getVersion(),
                        "packaging", module.getPackaging()
                )
        );
    }

    private void deleteOldDependencies(Transaction tx, MavenModule source) {
        tx.run(
                "MATCH (m:MavenModule {groupId: $groupId, artifactId: $artifactId, version: $version})" +
                "-[r:DEPENDS_ON]->() DELETE r",
                Map.of(
                        "groupId", source.getGroupId(),
                        "artifactId", source.getArtifactId(),
                        "version", source.getVersion()
                )
        );
    }

    private void createDependency(Transaction tx, Dependency dep) {
        tx.run(
                "MATCH (source:MavenModule {groupId: $sourceGroupId, artifactId: $sourceArtifactId, version: $sourceVersion}), " +
                "      (target:MavenModule {groupId: $targetGroupId, artifactId: $targetArtifactId, version: $targetVersion}) " +
                "CREATE (source)-[:DEPENDS_ON {" +
                "    scope: $scope, " +
                "    optional: $optional, " +
                "    depth: $depth, " +
                "    isResolved: $isResolved, " +
                "    exportTimestamp: datetime()" +
                "}]->(target)",
                Map.ofEntries(
                        Map.entry("sourceGroupId", dep.getSource().getGroupId()),
                        Map.entry("sourceArtifactId", dep.getSource().getArtifactId()),
                        Map.entry("sourceVersion", dep.getSource().getVersion()),
                        Map.entry("targetGroupId", dep.getTarget().getGroupId()),
                        Map.entry("targetArtifactId", dep.getTarget().getArtifactId()),
                        Map.entry("targetVersion", dep.getTarget().getVersion()),
                        Map.entry("scope", dep.getScope()),
                        Map.entry("optional", dep.isOptional()),
                        Map.entry("depth", dep.getDepth()),
                        Map.entry("isResolved", dep.isResolved())
                )
        );
    }

    private Instant parseDateTime(Object value) {
        if (value == null) {
            return Instant.now();
        }
        if (value instanceof ZonedDateTime) {
            return ((ZonedDateTime) value).toInstant();
        }
        if (value instanceof java.time.LocalDateTime) {
            return ((java.time.LocalDateTime) value).toInstant(ZoneOffset.UTC);
        }
        return Instant.now();
    }

    private record VersionInfo(String version, long nodeId) {}
}


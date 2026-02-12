package org.example.gdm.export.neo4j;

import org.example.gdm.exception.ExportException;
import org.example.gdm.export.*;
import org.example.gdm.model.Dependency;
import org.example.gdm.model.DependencyGraph;
import org.example.gdm.model.MavenModule;
import org.example.gdm.model.ProjectModule;
import org.example.gdm.model.ProjectStructure;
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
    public ExportResult exportGraph(DependencyGraph graph, Set<String> projectModuleGAVs, String nodeLabel) throws ExportException {
        long startTime = System.currentTimeMillis();

        try (Session session = driver.session()) {
            int modulesExported = 0;
            int dependenciesExported = 0;
            int conflictsDetected = 0;

            // Start a transaction for the entire export
            try (Transaction tx = session.beginTransaction()) {
                // 1. Upsert all modules (skip project modules - they are already ProjectModule nodes)
                for (MavenModule module : graph.getModules()) {
                    if (!projectModuleGAVs.contains(module.getGAV())) {
                        upsertModule(tx, module);
                        modulesExported++;
                    } else {
                        log.debug("Skipping MavenModule creation for project module: {}", module.getGAV());
                    }
                }

                // 2. Delete old dependencies from root module
                MavenModule root = graph.getRootModule();
                deleteOldDependencies(tx, root, projectModuleGAVs, nodeLabel);

                // 3. Create new dependencies in batches
                List<Dependency> dependencies = graph.getDependencies();
                for (int i = 0; i < dependencies.size(); i += BATCH_SIZE) {
                    int end = Math.min(i + BATCH_SIZE, dependencies.size());
                    List<Dependency> batch = dependencies.subList(i, end);

                    for (Dependency dep : batch) {
                        createDependency(tx, dep, projectModuleGAVs, nodeLabel);
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
    public int exportProjectStructure(ProjectStructure projectStructure, String nodeLabel) throws ExportException {
        log.info("Exporting project structure: {} modules with label '{}'", projectStructure.getModuleCount(), nodeLabel);

        try (Session session = driver.session()) {
            int modulesExported = 0;

            // 1. Ensure uniqueness constraint exists for the node label (separate from transaction)
            try {
                session.run(
                        "CREATE CONSTRAINT " + nodeLabel.toLowerCase() + "_unique IF NOT EXISTS " +
                        "FOR (p:" + nodeLabel + ") " +
                        "REQUIRE (p.groupId, p.artifactId, p.version) IS UNIQUE"
                );
                log.debug("{} uniqueness constraint ensured", nodeLabel);
            } catch (Exception e) {
                // Constraint may already exist, which is fine
                log.debug("Constraint creation skipped (may already exist): {}", e.getMessage());
            }

            // 2. Now execute write operations in a transaction
            try (Transaction tx = session.beginTransaction()) {

                // 2. Create all nodes with the specified label
                for (ProjectModule module : projectStructure.getAllModules()) {
                    upsertProjectModule(tx, module, nodeLabel);
                    modulesExported++;
                }

                // 3. Create CONTAINS_MODULE relationships
                for (ProjectStructure.ModuleRelationship rel : projectStructure.getContainsModuleRelationships()) {
                    createContainsModuleRelationship(tx, rel.parent(), rel.child(), nodeLabel);
                }

                // Note: Nodes are NOT linked to MavenModule nodes via IS_A.
                // They directly participate in DEPENDS_ON relationships.

                tx.commit();
                log.info("Project structure export committed: {} modules with label '{}'", modulesExported, nodeLabel);
            }

            return modulesExported;

        } catch (ServiceUnavailableException e) {
            throw new ExportException("Neo4j service unavailable: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ExportException("Project structure export failed: " + e.getMessage(), e);
        }
    }

    @Override
    public int cleanupOldVersions(String groupId, String artifactId, Set<String> exportedModules) throws ExportException {
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

                // Keep the first (latest), check the rest for external dependencies
                List<Long> toDelete = new ArrayList<>();
                for (int i = 1; i < versions.size(); i++) {
                    VersionInfo oldVersion = versions.get(i);
                    String oldGAV = groupId + ":" + artifactId + ":" + oldVersion.version;

                    // Check if this old version has incoming dependencies from modules outside current export
                    if (!hasExternalDependencies(session, groupId, artifactId, oldVersion.version, exportedModules)) {
                        toDelete.add(oldVersion.nodeId);
                        log.debug("Old version {} has no external dependencies, will be deleted", oldGAV);
                    } else {
                        log.info("Keeping old version {} - has dependencies from other projects", oldGAV);
                    }
                }

                if (toDelete.isEmpty()) {
                    log.debug("No old versions eligible for deletion for {}:{}", groupId, artifactId);
                    return 0;
                }

                // Mark as not latest
                session.run(
                        "MATCH (m:MavenModule) WHERE id(m) IN $ids SET m.isLatest = false",
                        Map.of("ids", toDelete)
                );

                // Delete old versions that have no external dependencies
                Result deleteResult = session.run(
                        "MATCH (m:MavenModule) " +
                        "WHERE id(m) IN $ids " +
                        "DETACH DELETE m " +
                        "RETURN count(m) AS deleted",
                        Map.of("ids", toDelete)
                );

                int deleted = deleteResult.single().get("deleted").asInt();
                log.info("Deleted {} old versions of {}:{}", deleted, groupId, artifactId);
                return deleted;
            }
        }, "Cleanup old versions");
    }

    /**
     * Checks if a module version has incoming DEPENDS_ON relationships from modules
     * that are NOT part of the current export session.
     *
     * @param session         Neo4j session
     * @param groupId         module group ID
     * @param artifactId      module artifact ID
     * @param version         module version
     * @param exportedModules set of GAVs exported in current session
     * @return true if there are external dependencies pointing to this module
     */
    private boolean hasExternalDependencies(Session session, String groupId, String artifactId,
                                             String version, Set<String> exportedModules) {
        // Find all modules that depend on this version
        Result result = session.run(
                "MATCH (source:MavenModule)-[:DEPENDS_ON]->(target:MavenModule " +
                "{groupId: $groupId, artifactId: $artifactId, version: $version}) " +
                "RETURN source.groupId AS srcGroupId, source.artifactId AS srcArtifactId, " +
                "       source.version AS srcVersion",
                Map.of("groupId", groupId, "artifactId", artifactId, "version", version)
        );

        while (result.hasNext()) {
            Record record = result.next();
            String sourceGAV = record.get("srcGroupId").asString() + ":" +
                              record.get("srcArtifactId").asString() + ":" +
                              record.get("srcVersion").asString();

            // If the source is NOT in the exported modules, it's an external dependency
            if (!exportedModules.contains(sourceGAV)) {
                log.debug("Found external dependency: {} -> {}:{}:{}",
                         sourceGAV, groupId, artifactId, version);
                return true;
            }
        }
        return false;
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

    private void deleteOldDependencies(Transaction tx, MavenModule source, Set<String> projectModuleGAVs, String nodeLabel) {
        String sourceGAV = source.getGAV();
        boolean isProjectModule = projectModuleGAVs.contains(sourceGAV);

        if (isProjectModule) {
            // Delete dependencies from node with custom label
            tx.run(
                    "MATCH (m:" + nodeLabel + " {groupId: $groupId, artifactId: $artifactId, version: $version})" +
                    "-[r:DEPENDS_ON]->() DELETE r",
                    Map.of(
                            "groupId", source.getGroupId(),
                            "artifactId", source.getArtifactId(),
                            "version", source.getVersion()
                    )
            );
        } else {
            // Delete dependencies from MavenModule node
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
    }

    private void createDependency(Transaction tx, Dependency dep, Set<String> projectModuleGAVs, String nodeLabel) {
        String sourceGAV = dep.getSource().getGAV();
        String targetGAV = dep.getTarget().getGAV();
        boolean sourceIsProject = projectModuleGAVs.contains(sourceGAV);
        boolean targetIsProject = projectModuleGAVs.contains(targetGAV);

        String sourceLabel = sourceIsProject ? nodeLabel : "MavenModule";
        String targetLabel = targetIsProject ? nodeLabel : "MavenModule";

        tx.run(
                "MATCH (source:" + sourceLabel + " {groupId: $sourceGroupId, artifactId: $sourceArtifactId, version: $sourceVersion}), " +
                "      (target:" + targetLabel + " {groupId: $targetGroupId, artifactId: $targetArtifactId, version: $targetVersion}) " +
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

    // ========== ProjectModule Methods ==========


    /**
     * Upserts a project module node with the specified label.
     * The node will have a property "ProjectModule: true" to identify it as a project module.
     */
    private void upsertProjectModule(Transaction tx, ProjectModule module, String nodeLabel) {
        tx.run(
                "MERGE (p:" + nodeLabel + " {groupId: $groupId, artifactId: $artifactId, version: $version}) " +
                "SET p.isRootProject = $isRootProject, " +
                "    p.ProjectModule = true, " +
                "    p.exportTimestamp = datetime()",
                Map.of(
                        "groupId", module.getGroupId(),
                        "artifactId", module.getArtifactId(),
                        "version", module.getVersion(),
                        "isRootProject", module.isRootProject()
                )
        );
        log.debug("Upserted {} node: {}:{}", nodeLabel, module.getArtifactId(), module.getVersion());
    }

    /**
     * Creates a CONTAINS_MODULE relationship between parent and child nodes.
     */
    private void createContainsModuleRelationship(Transaction tx, ProjectModule parent, ProjectModule child, String nodeLabel) {
        tx.run(
                "MATCH (parent:" + nodeLabel + " {groupId: $parentGroupId, artifactId: $parentArtifactId, version: $parentVersion}), " +
                "      (child:" + nodeLabel + " {groupId: $childGroupId, artifactId: $childArtifactId, version: $childVersion}) " +
                "MERGE (parent)-[:CONTAINS_MODULE]->(child)",
                Map.ofEntries(
                        Map.entry("parentGroupId", parent.getGroupId()),
                        Map.entry("parentArtifactId", parent.getArtifactId()),
                        Map.entry("parentVersion", parent.getVersion()),
                        Map.entry("childGroupId", child.getGroupId()),
                        Map.entry("childArtifactId", child.getArtifactId()),
                        Map.entry("childVersion", child.getVersion())
                )
        );
        log.debug("Created CONTAINS_MODULE: {} -> {}", parent.getArtifactId(), child.getArtifactId());
    }


    private record VersionInfo(String version, long nodeId) {}
}


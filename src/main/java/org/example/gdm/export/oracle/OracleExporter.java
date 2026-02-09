package org.example.gdm.export.oracle;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.example.gdm.exception.ExportException;
import org.example.gdm.export.*;
import org.example.gdm.model.Dependency;
import org.example.gdm.model.DependencyGraph;
import org.example.gdm.model.MavenModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Exports dependency graphs to Oracle database.
 */
public class OracleExporter implements DatabaseExporter {

    private static final Logger log = LoggerFactory.getLogger(OracleExporter.class);
    private static final int BATCH_SIZE = 500;

    private final String connectionUrl;
    private final String username;
    private final String password;
    private final RetryExecutor retryExecutor;

    private HikariDataSource dataSource;

    public OracleExporter(String connectionUrl, String username, String password) {
        this.connectionUrl = connectionUrl;
        this.username = username;
        this.password = password;
        this.retryExecutor = new RetryExecutor();
    }

    @Override
    public void connect() throws ExportException {
        retryExecutor.executeVoid(() -> {
            log.debug("Connecting to Oracle at {}", connectionUrl);

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(connectionUrl);
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(10);
            config.setConnectionTimeout(30000);
            config.setDriverClassName("oracle.jdbc.OracleDriver");

            dataSource = new HikariDataSource(config);

            // Verify connection
            try (Connection conn = dataSource.getConnection()) {
                log.info("Connected to Oracle successfully");
            }
        }, "Oracle connection");
    }

    @Override
    public SchemaVersion checkSchemaVersion() throws ExportException {
        return retryExecutor.execute(() -> {
            try (Connection conn = dataSource.getConnection()) {
                // Check if schema version exists
                String selectSql = "SELECT version, applied_at FROM gdm_schema_version WHERE ROWNUM = 1";
                try (PreparedStatement ps = conn.prepareStatement(selectSql);
                     ResultSet rs = ps.executeQuery()) {

                    if (rs.next()) {
                        String version = rs.getString("version");
                        Timestamp appliedAt = rs.getTimestamp("applied_at");
                        SchemaVersion schemaVersion = new SchemaVersion(
                                version,
                                appliedAt != null ? appliedAt.toInstant() : Instant.now()
                        );
                        log.debug("Found schema version: {}", schemaVersion);
                        return schemaVersion;
                    }
                }

                // Create schema version
                log.info("Creating schema version metadata");
                String insertSql = "INSERT INTO gdm_schema_version (version, applied_at) VALUES (?, CURRENT_TIMESTAMP)";
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setString(1, SchemaVersion.CURRENT_VERSION);
                    ps.executeUpdate();
                }
                conn.commit();

                return new SchemaVersion(SchemaVersion.CURRENT_VERSION, Instant.now());
            }
        }, "Check schema version");
    }

    @Override
    public ExportResult exportGraph(DependencyGraph graph) throws ExportException {
        long startTime = System.currentTimeMillis();

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            int modulesExported = 0;
            int dependenciesExported = 0;
            int conflictsDetected = 0;

            try {
                // 1. Upsert all modules and collect their IDs
                Map<String, Long> moduleIds = new HashMap<>();
                for (MavenModule module : graph.getModules()) {
                    long moduleId = upsertModule(conn, module);
                    moduleIds.put(module.getGAV(), moduleId);
                    modulesExported++;
                }

                // 2. Delete old dependencies from root module
                MavenModule root = graph.getRootModule();
                Long rootId = moduleIds.get(root.getGAV());
                deleteOldDependencies(conn, rootId);

                // 3. Insert new dependencies in batches
                List<Dependency> dependencies = graph.getDependencies();

                String insertSql = "INSERT INTO maven_dependencies " +
                        "(source_module_id, target_module_id, scope, optional, depth, is_resolved, export_timestamp) " +
                        "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";

                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (int i = 0; i < dependencies.size(); i++) {
                        Dependency dep = dependencies.get(i);

                        Long sourceId = moduleIds.get(dep.getSource().getGAV());
                        Long targetId = moduleIds.get(dep.getTarget().getGAV());

                        if (sourceId == null || targetId == null) {
                            log.warn("Skipping dependency with missing module: {} -> {}",
                                    dep.getSource().getGAV(), dep.getTarget().getGAV());
                            continue;
                        }

                        ps.setLong(1, sourceId);
                        ps.setLong(2, targetId);
                        ps.setString(3, dep.getScope());
                        ps.setInt(4, dep.isOptional() ? 1 : 0);
                        ps.setInt(5, dep.getDepth());
                        ps.setInt(6, dep.isResolved() ? 1 : 0);
                        ps.addBatch();

                        dependenciesExported++;
                        if (!dep.isResolved()) {
                            conflictsDetected++;
                        }

                        // Execute batch
                        if ((i + 1) % BATCH_SIZE == 0 || i == dependencies.size() - 1) {
                            ps.executeBatch();
                            log.debug("Processed batch up to {} of {} dependencies", i + 1, dependencies.size());
                        }
                    }
                }

                // Commit transaction
                conn.commit();
                log.info("Transaction committed successfully");

                long executionTime = System.currentTimeMillis() - startTime;
                return ExportResult.success(modulesExported, dependenciesExported, conflictsDetected, executionTime);

            } catch (SQLException e) {
                conn.rollback();
                log.error("Transaction rolled back due to error: {}", e.getMessage());
                throw e;
            }

        } catch (SQLException e) {
            throw new ExportException("Export failed: " + e.getMessage(), e);
        }
    }

    @Override
    public int cleanupOldVersions(String groupId, String artifactId) throws ExportException {
        return retryExecutor.execute(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);

                try {
                    // Fetch all versions
                    String selectSql = "SELECT id, version FROM maven_modules " +
                            "WHERE group_id = ? AND artifact_id = ?";

                    List<VersionInfo> versions = new ArrayList<>();
                    try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                        ps.setString(1, groupId);
                        ps.setString(2, artifactId);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                versions.add(new VersionInfo(
                                        rs.getString("version"),
                                        rs.getLong("id")
                                ));
                            }
                        }
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
                            .map(v -> v.id)
                            .toList();

                    // Mark as not latest
                    String updateSql = "UPDATE maven_modules SET is_latest = 0 WHERE id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                        for (Long id : toDelete) {
                            ps.setLong(1, id);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }

                    // Delete old versions (CASCADE will delete dependencies)
                    String deleteSql = "DELETE FROM maven_modules WHERE is_latest = 0 " +
                            "AND group_id = ? AND artifact_id = ?";
                    int deleted;
                    try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                        ps.setString(1, groupId);
                        ps.setString(2, artifactId);
                        deleted = ps.executeUpdate();
                    }

                    conn.commit();
                    log.info("Deleted {} old versions of {}:{}", deleted, groupId, artifactId);
                    return deleted;

                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            }
        }, "Cleanup old versions");
    }

    @Override
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    @Override
    public void close() {
        if (dataSource != null) {
            try {
                dataSource.close();
                log.debug("Oracle connection closed");
            } catch (Exception e) {
                log.warn("Error closing Oracle connection: {}", e.getMessage());
            }
            dataSource = null;
        }
    }

    @Override
    public String getDatabaseType() {
        return "oracle";
    }

    // ========== Private Methods ==========

    private long upsertModule(Connection conn, MavenModule module) throws SQLException {
        // Try to find existing module
        String selectSql = "SELECT id FROM maven_modules " +
                "WHERE group_id = ? AND artifact_id = ? AND version = ?";

        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, module.getGroupId());
            ps.setString(2, module.getArtifactId());
            ps.setString(3, module.getVersion());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong("id");
                    // Update existing module
                    updateModule(conn, id, module);
                    return id;
                }
            }
        }

        // Insert new module
        return insertModule(conn, module);
    }

    private void updateModule(Connection conn, long id, MavenModule module) throws SQLException {
        String updateSql = "UPDATE maven_modules SET " +
                "packaging = ?, export_timestamp = CURRENT_TIMESTAMP, is_latest = 1 " +
                "WHERE id = ?";

        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setString(1, module.getPackaging());
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    private long insertModule(Connection conn, MavenModule module) throws SQLException {
        String insertSql = "INSERT INTO maven_modules " +
                "(group_id, artifact_id, version, packaging, export_timestamp, is_latest) " +
                "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, 1)";

        try (PreparedStatement ps = conn.prepareStatement(insertSql, new String[]{"ID"})) {
            ps.setString(1, module.getGroupId());
            ps.setString(2, module.getArtifactId());
            ps.setString(3, module.getVersion());
            ps.setString(4, module.getPackaging());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }

        throw new SQLException("Failed to get generated ID for module: " + module.getGAV());
    }

    private void deleteOldDependencies(Connection conn, Long sourceModuleId) throws SQLException {
        String deleteSql = "DELETE FROM maven_dependencies WHERE source_module_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setLong(1, sourceModuleId);
            int deleted = ps.executeUpdate();
            log.debug("Deleted {} old dependencies", deleted);
        }
    }

    private record VersionInfo(String version, long id) {}
}


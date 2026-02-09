package org.example.gdm.integration;

import org.example.gdm.export.oracle.OracleExporter;
import org.example.gdm.model.Dependency;
import org.example.gdm.model.DependencyGraph;
import org.example.gdm.model.MavenModule;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for OracleExporter using Testcontainers.
 */
@Disabled
@Testcontainers
@DisplayName("Oracle Exporter Integration Tests")
class OracleExportIT {

    @Container
    private static final OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-xe:21-slim-faststart")
            .withDatabaseName("testDB")
            .withUsername("testUser")
            .withPassword("testPassword");

    private static OracleExporter exporter;
    private static Connection connection;

    @BeforeAll
    static void setUp() throws Exception {
        oracleContainer.start();

        // Create tables
        try (Connection conn = DriverManager.getConnection(oracleContainer.getJdbcUrl(), "sys as sysdba", "Oradoc_db1");
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE USER testUser IDENTIFIED BY testPassword");
            stmt.execute("GRANT CONNECT, RESOURCE, DBA TO testUser");

            try (Connection userConn = DriverManager.getConnection(oracleContainer.getJdbcUrl(), "testUser", "testPassword");
                 Statement userStmt = userConn.createStatement()) {

                userStmt.execute(
                    "CREATE TABLE maven_modules (" +
                    "    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY, " +
                    "    group_id VARCHAR2(255) NOT NULL, " +
                    "    artifact_id VARCHAR2(255) NOT NULL, " +
                    "    version VARCHAR2(100) NOT NULL, " +
                    "    packaging VARCHAR2(50), " +
                    "    export_timestamp TIMESTAMP, " +
                    "    is_latest NUMBER(1), " +
                    "    CONSTRAINT uk_maven_module UNIQUE (group_id, artifact_id, version)" +
                    ")"
                );
                userStmt.execute(
                    "CREATE TABLE maven_dependencies (" +
                    "    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY, " +
                    "    source_module_id NUMBER NOT NULL, " +
                    "    target_module_id NUMBER NOT NULL, " +
                    "    scope VARCHAR2(20), " +
                    "    optional NUMBER(1), " +
                    "    depth NUMBER(2), " +
                    "    is_resolved NUMBER(1), " +
                    "    export_timestamp TIMESTAMP, " +
                    "    CONSTRAINT fk_dep_source FOREIGN KEY (source_module_id) REFERENCES maven_modules(id) ON DELETE CASCADE, " +
                    "    CONSTRAINT fk_dep_target FOREIGN KEY (target_module_id) REFERENCES maven_modules(id) ON DELETE CASCADE" +
                    ")"
                );
                userStmt.execute(
                    "CREATE TABLE gdm_schema_version (version VARCHAR2(20) PRIMARY KEY, applied_at TIMESTAMP)"
                );
            }
        }

        exporter = new OracleExporter(
                oracleContainer.getJdbcUrl(),
                "testUser",
                "testPassword"
        );
        exporter.connect();

        connection = DriverManager.getConnection(oracleContainer.getJdbcUrl(), "testUser", "testPassword");
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (exporter != null) {
            exporter.close();
        }
        if (connection != null) {
            connection.close();
        }
        oracleContainer.stop();
    }

    @Test
    @DisplayName("should export a simple graph to Oracle")
    void shouldExportSimpleGraph() throws Exception {
        // Given
        MavenModule root = new MavenModule("com.example", "root", "1.0.0");
        MavenModule depA = new MavenModule("com.example", "dep-a", "1.0.0");
        DependencyGraph graph = new DependencyGraph(root);
        graph.addDependency(new Dependency(root, depA, "compile", false, 0, true));

        // When
        exporter.exportGraph(graph);

        // Then
        try (Statement stmt = connection.createStatement()) {
            ResultSet rsModules = stmt.executeQuery("SELECT count(*) FROM maven_modules");
            rsModules.next();
            assertThat(rsModules.getInt(1)).isEqualTo(2);

            ResultSet rsDeps = stmt.executeQuery("SELECT count(*) FROM maven_dependencies");
            rsDeps.next();
            assertThat(rsDeps.getInt(1)).isEqualTo(1);
        }
    }
}


package org.example.gdm.integration;

import org.example.gdm.model.Dependency;
import org.example.gdm.model.DependencyGraph;
import org.example.gdm.model.MavenModule;
import org.junit.Ignore;
import org.junit.jupiter.api.*;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.example.gdm.export.neo4j.Neo4jExporter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Neo4jExporter using Testcontainers.
 */
@Disabled
@Testcontainers
@DisplayName("Neo4j Exporter Integration Tests")
class Neo4jExportIT {

    @Container
    private static final Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:5")
            .withAdminPassword("password");

    private static Neo4jExporter exporter;
    private static Driver driver;

    @BeforeAll
    static void setUp() throws Exception {
        neo4jContainer.start();

        exporter = new Neo4jExporter(
                neo4jContainer.getBoltUrl(),
                "piotrwalczyk@gmail.com",
                "Lechuprg123!"
        );
        exporter.connect();

        driver = GraphDatabase.driver(neo4jContainer.getBoltUrl(), AuthTokens.basic("neo4j", "password"));
    }

    @AfterAll
    static void tearDown() {
        if (exporter != null) {
            exporter.close();
        }
        if (driver != null) {
            driver.close();
        }
        neo4jContainer.stop();
    }

    @Test
    @DisplayName("should export a simple graph to Neo4j")
    void shouldExportSimpleGraph() throws Exception {
        // Given
        MavenModule root = new MavenModule("com.example", "root", "1.0.0");
        MavenModule depA = new MavenModule("com.example", "dep-a", "1.0.0");
        DependencyGraph graph = new DependencyGraph(root);
        graph.addDependency(new Dependency(root, depA, "compile", false, 0, true));

        // When
        exporter.exportGraph(graph);

        // Then
        try (Session session = driver.session()) {
            long moduleCount = session.run("MATCH (n:MavenModule) RETURN count(n) AS count")
                    .single().get("count").asLong();
            long depCount = session.run("MATCH ()-[r:DEPENDS_ON]->() RETURN count(r) AS count")
                    .single().get("count").asLong();

            assertThat(moduleCount).isEqualTo(2);
            assertThat(depCount).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("should handle version conflicts correctly")
    void shouldHandleVersionConflicts() throws Exception {
        // Given
        MavenModule root = new MavenModule("com.example", "root", "1.0.0");
        MavenModule depA = new MavenModule("com.example", "dep-a", "1.0.0");
        MavenModule depB_v1 = new MavenModule("com.example", "dep-b", "1.0.0");
        MavenModule depB_v2 = new MavenModule("com.example", "dep-b", "2.0.0");

        DependencyGraph graph = new DependencyGraph(root);
        graph.addDependency(new Dependency(root, depA, "compile", false, 0, true));
        graph.addDependency(new Dependency(depA, depB_v1, "compile", false, 1, false)); // conflicted
        graph.addDependency(new Dependency(root, depB_v2, "compile", false, 0, true)); // resolved

        // When
        exporter.exportGraph(graph);

        // Then
        try (Session session = driver.session()) {
            long resolvedCount = session.run("MATCH ()-[r:DEPENDS_ON {isResolved: true}]->() RETURN count(r) AS count")
                    .single().get("count").asLong();
            long conflictCount = session.run("MATCH ()-[r:DEPENDS_ON {isResolved: false}]->() RETURN count(r) AS count")
                    .single().get("count").asLong();

            assertThat(resolvedCount).isEqualTo(2);
            assertThat(conflictCount).isEqualTo(1);
        }
    }
}


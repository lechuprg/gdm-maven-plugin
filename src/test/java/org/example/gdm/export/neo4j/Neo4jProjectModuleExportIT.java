package org.example.gdm.export.neo4j;

import org.example.gdm.model.ProjectModule;
import org.example.gdm.model.ProjectStructure;
import org.junit.jupiter.api.*;
import org.neo4j.driver.*;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Neo4jExporter's ProjectModule functionality using Testcontainers.
 */
@Disabled("Requires Docker for Testcontainers")
@Testcontainers
@DisplayName("Neo4j ProjectModule Export Integration Tests")
class Neo4jProjectModuleExportIT {

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
                "neo4j",
                "password"
        );
        exporter.connect();

        driver = GraphDatabase.driver(neo4jContainer.getBoltUrl(),
                AuthTokens.basic("neo4j", "password"));
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

    @BeforeEach
    void cleanDatabase() {
        try (Session session = driver.session()) {
            session.run("MATCH (n) DETACH DELETE n");
        }
    }

    @Test
    @DisplayName("should export single module project structure")
    void shouldExportSingleModuleProjectStructure() throws Exception {
        // Given
        ProjectStructure structure = ProjectStructure.singleModule(
                "com.example", "single-app", "1.0.0");

        // When
        int exported = exporter.exportProjectStructure(structure);

        // Then
        assertThat(exported).isEqualTo(1);

        try (Session session = driver.session()) {
            // Verify ProjectModule node was created
            long moduleCount = session.run(
                    "MATCH (n:ProjectModule) RETURN count(n) AS count")
                    .single().get("count").asLong();
            assertThat(moduleCount).isEqualTo(1);

            // Verify properties
            var result = session.run(
                    "MATCH (n:ProjectModule {artifactId: 'single-app'}) " +
                    "RETURN n.groupId AS groupId, n.version AS version, n.isRootProject AS isRoot");
            var record = result.single();
            assertThat(record.get("groupId").asString()).isEqualTo("com.example");
            assertThat(record.get("version").asString()).isEqualTo("1.0.0");
            assertThat(record.get("isRoot").asBoolean()).isTrue();
        }
    }

    @Test
    @DisplayName("should export multi-module project structure with CONTAINS_MODULE relationships")
    void shouldExportMultiModuleProjectStructure() throws Exception {
        // Given
        ProjectModule root = ProjectModule.builder()
                .groupId("com.example")
                .artifactId("parent")
                .version("1.0.0")
                .isRootProject(true)
                .build();

        ProjectModule subA = ProjectModule.builder()
                .groupId("com.example")
                .artifactId("sub-a")
                .version("1.0.0")
                .isRootProject(false)
                .build();

        ProjectModule subB = ProjectModule.builder()
                .groupId("com.example")
                .artifactId("sub-b")
                .version("1.0.0")
                .isRootProject(false)
                .build();

        root.addSubmodule(subA);
        root.addSubmodule(subB);

        ProjectStructure structure = new ProjectStructure(root);

        // When
        int exported = exporter.exportProjectStructure(structure);

        // Then
        assertThat(exported).isEqualTo(3);

        try (Session session = driver.session()) {
            // Verify all ProjectModule nodes
            long moduleCount = session.run(
                    "MATCH (n:ProjectModule) RETURN count(n) AS count")
                    .single().get("count").asLong();
            assertThat(moduleCount).isEqualTo(3);

            // Verify CONTAINS_MODULE relationships
            long relCount = session.run(
                    "MATCH ()-[r:CONTAINS_MODULE]->() RETURN count(r) AS count")
                    .single().get("count").asLong();
            assertThat(relCount).isEqualTo(2);

            // Verify specific relationships
            var submodules = session.run(
                    "MATCH (parent:ProjectModule {artifactId: 'parent'})-[:CONTAINS_MODULE]->(child) " +
                    "RETURN child.artifactId AS artifactId ORDER BY artifactId")
                    .list(r -> r.get("artifactId").asString());
            assertThat(submodules).containsExactly("sub-a", "sub-b");
        }
    }

    @Test
    @DisplayName("should export nested module structure")
    void shouldExportNestedModuleStructure() throws Exception {
        // Given: root -> sub-a -> nested
        ProjectModule nested = ProjectModule.builder()
                .groupId("com.example")
                .artifactId("nested")
                .version("1.0.0")
                .isRootProject(false)
                .build();

        ProjectModule subA = ProjectModule.builder()
                .groupId("com.example")
                .artifactId("sub-a")
                .version("1.0.0")
                .isRootProject(false)
                .submodule(nested)
                .build();

        ProjectModule root = ProjectModule.builder()
                .groupId("com.example")
                .artifactId("root")
                .version("1.0.0")
                .isRootProject(true)
                .submodule(subA)
                .build();

        ProjectStructure structure = new ProjectStructure(root);

        // When
        int exported = exporter.exportProjectStructure(structure);

        // Then
        assertThat(exported).isEqualTo(3);

        try (Session session = driver.session()) {
            // Verify chain: root -> sub-a -> nested
            var path = session.run(
                    "MATCH path = (root:ProjectModule {isRootProject: true})" +
                    "-[:CONTAINS_MODULE*]->(leaf:ProjectModule {artifactId: 'nested'}) " +
                    "RETURN [n IN nodes(path) | n.artifactId] AS chain")
                    .single().get("chain").asList(Value::asString);
            assertThat(path).containsExactly("root", "sub-a", "nested");
        }
    }

    @Test
    @DisplayName("should NOT create IS_A relationship - ProjectModule and MavenModule are independent")
    void shouldNotCreateIsARelationshipMavenModuleExists() throws Exception {
        // Given: Create a MavenModule first
        try (Session session = driver.session()) {
            session.run(
                    "CREATE (m:MavenModule {groupId: 'com.example', artifactId: 'my-app', version: '1.0.0'})");
        }

        ProjectStructure structure = ProjectStructure.singleModule(
                "com.example", "my-app", "1.0.0");

        // When
        exporter.exportProjectStructure(structure);

        // Then: IS_A relationships should NOT be created anymore
        // ProjectModule nodes are separate from MavenModule nodes
        try (Session session = driver.session()) {
            long isACount = session.run(
                    "MATCH (p:ProjectModule)-[r:IS_A]->(m:MavenModule) RETURN count(r) AS count")
                    .single().get("count").asLong();
            assertThat(isACount).isEqualTo(0);

            // ProjectModule should exist independently
            long projectModuleCount = session.run(
                    "MATCH (p:ProjectModule {artifactId: 'my-app'}) RETURN count(p) AS count")
                    .single().get("count").asLong();
            assertThat(projectModuleCount).isEqualTo(1);

            // MavenModule should also exist (we created it above)
            long mavenModuleCount = session.run(
                    "MATCH (m:MavenModule {artifactId: 'my-app'}) RETURN count(m) AS count")
                    .single().get("count").asLong();
            assertThat(mavenModuleCount).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("should not create IS_A relationship - ProjectModule is independent of MavenModule")
    void shouldNotCreateIsARelationshipProjectModuleIsIndependent() throws Exception {
        // Given: No MavenModule exists
        ProjectStructure structure = ProjectStructure.singleModule(
                "com.example", "no-maven", "1.0.0");

        // When
        exporter.exportProjectStructure(structure);

        // Then: No IS_A relationships should exist (this is the expected new behavior)
        try (Session session = driver.session()) {
            long isACount = session.run(
                    "MATCH (p:ProjectModule)-[r:IS_A]->(m:MavenModule) RETURN count(r) AS count")
                    .single().get("count").asLong();
            assertThat(isACount).isEqualTo(0);

            // ProjectModule should still exist
            long moduleCount = session.run(
                    "MATCH (n:ProjectModule {artifactId: 'no-maven'}) RETURN count(n) AS count")
                    .single().get("count").asLong();
            assertThat(moduleCount).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("should use MERGE for idempotent exports")
    void shouldUseMergeForIdempotentExports() throws Exception {
        // Given
        ProjectStructure structure = ProjectStructure.singleModule(
                "com.example", "idempotent-test", "1.0.0");

        // When: Export twice
        exporter.exportProjectStructure(structure);
        exporter.exportProjectStructure(structure);

        // Then: Should still have only one node
        try (Session session = driver.session()) {
            long moduleCount = session.run(
                    "MATCH (n:ProjectModule {artifactId: 'idempotent-test'}) RETURN count(n) AS count")
                    .single().get("count").asLong();
            assertThat(moduleCount).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("should create uniqueness constraint for ProjectModule")
    void shouldCreateUniquenessConstraintForProjectModule() throws Exception {
        // Given
        ProjectStructure structure = ProjectStructure.singleModule(
                "com.example", "constraint-test", "1.0.0");

        // When
        exporter.exportProjectStructure(structure);

        // Then: Verify constraint exists
        try (Session session = driver.session()) {
            var constraints = session.run("SHOW CONSTRAINTS").list();
            boolean hasProjectModuleConstraint = constraints.stream()
                    .anyMatch(r -> {
                        var labelsOrTypes = r.get("labelsOrTypes").asList(Value::asString);
                        return labelsOrTypes.contains("ProjectModule");
                    });
            assertThat(hasProjectModuleConstraint).isTrue();
        }
    }
}


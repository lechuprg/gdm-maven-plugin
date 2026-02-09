package org.example.gdm.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for DependencyGraph.
 */
class DependencyGraphTest {

    private MavenModule rootModule;
    private MavenModule moduleA;
    private MavenModule moduleB;
    private MavenModule moduleC_v1;
    private MavenModule moduleC_v2;

    @BeforeEach
    void setUp() {
        rootModule = new MavenModule("com.example", "root", "1.0.0");
        moduleA = new MavenModule("com.example", "module-a", "1.0.0");
        moduleB = new MavenModule("com.example", "module-b", "1.0.0");
        moduleC_v1 = new MavenModule("com.example", "module-c", "1.0.0");
        moduleC_v2 = new MavenModule("com.example", "module-c", "2.0.0");
    }

    @Nested
    @DisplayName("Constructor")
    class Constructor {

        @Test
        @DisplayName("should create graph with root module")
        void shouldCreateGraphWithRootModule() {
            DependencyGraph graph = new DependencyGraph(rootModule);

            assertThat(graph.getRootModule()).isEqualTo(rootModule);
            assertThat(graph.getModules()).contains(rootModule);
            assertThat(graph.getModuleCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should throw when root module is null")
        void shouldThrowWhenRootModuleNull() {
            assertThatThrownBy(() -> new DependencyGraph(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("rootModule");
        }
    }

    @Nested
    @DisplayName("Adding Modules and Dependencies")
    class AddingModulesAndDependencies {

        @Test
        @DisplayName("should add module to graph")
        void shouldAddModuleToGraph() {
            DependencyGraph graph = new DependencyGraph(rootModule);
            graph.addModule(moduleA);

            assertThat(graph.getModules()).contains(moduleA);
            assertThat(graph.getModuleCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("should not add duplicate module")
        void shouldNotAddDuplicateModule() {
            DependencyGraph graph = new DependencyGraph(rootModule);
            graph.addModule(moduleA);
            graph.addModule(moduleA);

            assertThat(graph.getModuleCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("should add dependency and its modules")
        void shouldAddDependencyAndItsModules() {
            DependencyGraph graph = new DependencyGraph(rootModule);
            Dependency dep = new Dependency(rootModule, moduleA, "compile", false, 0, true);

            graph.addDependency(dep);

            assertThat(graph.getModules()).contains(rootModule, moduleA);
            assertThat(graph.getDependencies()).contains(dep);
            assertThat(graph.getDependencyCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Query Methods")
    class QueryMethods {

        private DependencyGraph graph;
        private Dependency directDep1;
        private Dependency directDep2;
        private Dependency transitiveDep;
        private Dependency conflictDep;

        @BeforeEach
        void setUpGraph() {
            graph = new DependencyGraph(rootModule);

            // Root -> A (direct, depth=0)
            directDep1 = new Dependency(rootModule, moduleA, "compile", false, 0, true);
            graph.addDependency(directDep1);

            // Root -> B (direct, depth=0)
            directDep2 = new Dependency(rootModule, moduleB, "test", false, 0, true);
            graph.addDependency(directDep2);

            // A -> C:2.0 (transitive, depth=1, resolved)
            transitiveDep = new Dependency(moduleA, moduleC_v2, "compile", false, 1, true);
            graph.addDependency(transitiveDep);

            // B -> C:1.0 (transitive, depth=1, NOT resolved - conflict)
            conflictDep = new Dependency(moduleB, moduleC_v1, "compile", false, 1, false);
            graph.addDependency(conflictDep);
        }

        @Test
        @DisplayName("should return direct dependencies")
        void shouldReturnDirectDependencies() {
            List<Dependency> directDeps = graph.getDirectDependencies();

            assertThat(directDeps).containsExactlyInAnyOrder(directDep1, directDep2);
        }

        @Test
        @DisplayName("should return transitive dependencies")
        void shouldReturnTransitiveDependencies() {
            List<Dependency> transitiveDeps = graph.getTransitiveDependencies();

            assertThat(transitiveDeps).containsExactlyInAnyOrder(transitiveDep, conflictDep);
        }

        @Test
        @DisplayName("should return resolved dependencies")
        void shouldReturnResolvedDependencies() {
            List<Dependency> resolvedDeps = graph.getResolvedDependencies();

            assertThat(resolvedDeps).containsExactlyInAnyOrder(directDep1, directDep2, transitiveDep);
        }

        @Test
        @DisplayName("should return conflicted dependencies")
        void shouldReturnConflictedDependencies() {
            List<Dependency> conflictedDeps = graph.getConflictedDependencies();

            assertThat(conflictedDeps).containsExactly(conflictDep);
        }

        @Test
        @DisplayName("should return dependencies from source")
        void shouldReturnDependenciesFromSource() {
            List<Dependency> fromRoot = graph.getDependenciesFrom(rootModule);

            assertThat(fromRoot).containsExactlyInAnyOrder(directDep1, directDep2);
        }

        @Test
        @DisplayName("should return dependencies to target")
        void shouldReturnDependenciesToTarget() {
            List<Dependency> toModuleA = graph.getDependenciesTo(moduleA);

            assertThat(toModuleA).containsExactly(directDep1);
        }

        @Test
        @DisplayName("should find module by GAV")
        void shouldFindModuleByGav() {
            Optional<MavenModule> found = graph.findModule("com.example", "module-a", "1.0.0");

            assertThat(found).isPresent().contains(moduleA);
        }

        @Test
        @DisplayName("should return empty when module not found")
        void shouldReturnEmptyWhenModuleNotFound() {
            Optional<MavenModule> found = graph.findModule("com.example", "non-existent", "1.0.0");

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("should find all module versions")
        void shouldFindAllModuleVersions() {
            List<MavenModule> versions = graph.findModuleVersions("com.example", "module-c");

            assertThat(versions).containsExactlyInAnyOrder(moduleC_v1, moduleC_v2);
        }

        @Test
        @DisplayName("should return unique GAs")
        void shouldReturnUniqueGas() {
            Set<String> gas = graph.getUniqueGAs();

            assertThat(gas).containsExactlyInAnyOrder(
                    "com.example:root",
                    "com.example:module-a",
                    "com.example:module-b",
                    "com.example:module-c"
            );
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTest {

        @Test
        @DisplayName("should build graph with modules and dependencies")
        void shouldBuildGraphWithModulesAndDependencies() {
            Dependency dep = new Dependency(rootModule, moduleA, "compile", false, 0, true);

            DependencyGraph graph = DependencyGraph.builder(rootModule)
                    .addModule(moduleB)
                    .addDependency(dep)
                    .build();

            assertThat(graph.getRootModule()).isEqualTo(rootModule);
            assertThat(graph.getModules()).contains(rootModule, moduleA, moduleB);
            assertThat(graph.getDependencies()).contains(dep);
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTest {

        @Test
        @DisplayName("should include module and dependency counts")
        void shouldIncludeModuleAndDependencyCounts() {
            DependencyGraph graph = new DependencyGraph(rootModule);
            graph.addDependency(new Dependency(rootModule, moduleA, "compile", false, 0, true));

            String result = graph.toString();

            assertThat(result)
                    .contains("rootModule=" + rootModule)
                    .contains("moduleCount=2")
                    .contains("dependencyCount=1");
        }
    }
}


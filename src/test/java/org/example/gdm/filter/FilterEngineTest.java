package org.example.gdm.filter;

import org.example.gdm.model.Dependency;
import org.example.gdm.model.DependencyGraph;
import org.example.gdm.model.MavenModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FilterEngine.
 */
class FilterEngineTest {

    private MavenModule rootModule;
    private MavenModule springCore;
    private MavenModule springWeb;
    private MavenModule springTest;
    private MavenModule guava;
    private MavenModule junit;
    private MavenModule companyApp;

    @BeforeEach
    void setUp() {
        rootModule = new MavenModule("com.example", "my-app", "1.0.0");
        springCore = new MavenModule("org.springframework", "spring-core", "5.0.0");
        springWeb = new MavenModule("org.springframework", "spring-web", "5.0.0");
        springTest = new MavenModule("org.springframework", "spring-test", "5.0.0");
        guava = new MavenModule("com.google.guava", "guava", "30.0");
        junit = new MavenModule("org.junit.jupiter", "junit-jupiter", "5.8.0");
        companyApp = new MavenModule("com.company", "internal-app", "1.0.0");
    }

    private DependencyGraph createTestGraph() {
        DependencyGraph graph = new DependencyGraph(rootModule);

        // Direct dependencies (depth=0)
        graph.addDependency(new Dependency(rootModule, springCore, "compile", false, 0, true));
        graph.addDependency(new Dependency(rootModule, guava, "compile", false, 0, true));
        graph.addDependency(new Dependency(rootModule, junit, "test", false, 0, true));

        // Transitive dependencies (depth=1)
        graph.addDependency(new Dependency(springCore, springWeb, "compile", false, 1, true));
        graph.addDependency(new Dependency(springCore, springTest, "test", false, 1, true));
        graph.addDependency(new Dependency(guava, companyApp, "runtime", false, 1, true));

        return graph;
    }

    @Nested
    @DisplayName("No Filters")
    class NoFilters {

        @Test
        @DisplayName("should include all dependencies when no filters")
        void shouldIncludeAllDependenciesWhenNoFilters() {
            DependencyGraph graph = createTestGraph();
            FilterEngine engine = new FilterEngine(null, null, null);

            FilterEngine.FilterResult result = engine.filter(graph);

            assertThat(result.getFilteredCount()).isEqualTo(6);
            assertThat(result.getTotalExcluded()).isEqualTo(0);
            assertThat(result.getFilteredGraph().getModules()).hasSize(7); // root + 6 deps
        }

        @Test
        @DisplayName("should include all dependencies with empty filter lists")
        void shouldIncludeAllWithEmptyLists() {
            DependencyGraph graph = createTestGraph();
            FilterEngine engine = new FilterEngine(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
            );

            FilterEngine.FilterResult result = engine.filter(graph);

            assertThat(result.getFilteredCount()).isEqualTo(6);
        }
    }

    @Nested
    @DisplayName("Exclude Filters")
    class ExcludeFilters {



        @Test
        @DisplayName("should exclude with wildcard pattern")
        void shouldExcludeWithWildcardPattern() {
            DependencyGraph graph = createTestGraph();
            FilterEngine engine = new FilterEngine(
                    null,
                    Arrays.asList("*:*-test"),
                    null
            );

            FilterEngine.FilterResult result = engine.filter(graph);

            assertThat(result.getFilteredGraph().getModules())
                    .doesNotContain(springTest);
        }
    }

    @Nested
    @DisplayName("Include Filters")
    class IncludeFilters {

        @Test
        @DisplayName("should only include matching dependencies")
        void shouldOnlyIncludeMatchingDependencies() {
            DependencyGraph graph = createTestGraph();
            FilterEngine engine = new FilterEngine(
                    Arrays.asList("org.springframework:*"),
                    null,
                    null
            );

            FilterEngine.FilterResult result = engine.filter(graph);

            // Should include spring-core, spring-web, spring-test
            assertThat(result.getFilteredCount()).isEqualTo(3);
            assertThat(result.getExcludedByInclude()).isEqualTo(3); // guava, junit, companyApp
            assertThat(result.getFilteredGraph().getModules())
                    .contains(springCore, springWeb, springTest)
                    .doesNotContain(guava, junit, companyApp);
        }

        @Test
        @DisplayName("should match any of multiple include patterns")
        void shouldMatchAnyOfMultiplePatterns() {
            DependencyGraph graph = createTestGraph();
            FilterEngine engine = new FilterEngine(
                    Arrays.asList("org.springframework:*", "com.google.guava:*"),
                    null,
                    null
            );

            FilterEngine.FilterResult result = engine.filter(graph);

            assertThat(result.getFilteredGraph().getModules())
                    .contains(springCore, springWeb, springTest, guava)
                    .doesNotContain(junit, companyApp);
        }
    }

    @Nested
    @DisplayName("Include and Exclude Combined")
    class IncludeAndExcludeCombined {

        @Test
        @DisplayName("exclude should have priority over include")
        void excludeShouldHavePriority() {
            DependencyGraph graph = createTestGraph();
            FilterEngine engine = new FilterEngine(
                    Arrays.asList("org.springframework:*"),
                    Arrays.asList("*:*-test"),
                    null
            );

            FilterEngine.FilterResult result = engine.filter(graph);

            // Should include spring-core, spring-web but NOT spring-test
            assertThat(result.getFilteredGraph().getModules())
                    .contains(springCore, springWeb)
                    .doesNotContain(springTest);
        }
    }

    @Nested
    @DisplayName("Scope Filters")
    class ScopeFilters {

        @Test
        @DisplayName("should filter by single scope")
        void shouldFilterBySingleScope() {
            DependencyGraph graph = createTestGraph();
            FilterEngine engine = new FilterEngine(
                    null,
                    null,
                    Arrays.asList("compile")
            );

            FilterEngine.FilterResult result = engine.filter(graph);

            // Should only include compile dependencies
            assertThat(result.getExcludedByScope()).isEqualTo(3); // test and runtime deps
            assertThat(result.getFilteredGraph().getDependencies())
                    .allMatch(d -> d.getScope().equals("compile"));
        }

        @Test
        @DisplayName("should filter by multiple scopes")
        void shouldFilterByMultipleScopes() {
            DependencyGraph graph = createTestGraph();
            FilterEngine engine = new FilterEngine(
                    null,
                    null,
                    Arrays.asList("compile", "runtime")
            );

            FilterEngine.FilterResult result = engine.filter(graph);

            assertThat(result.getFilteredGraph().getDependencies())
                    .allMatch(d -> d.getScope().equals("compile") || d.getScope().equals("runtime"));
        }

        @Test
        @DisplayName("should be case-insensitive for scopes")
        void shouldBeCaseInsensitiveForScopes() {
            DependencyGraph graph = createTestGraph();
            FilterEngine engine = new FilterEngine(
                    null,
                    null,
                    Arrays.asList("COMPILE", "Runtime")
            );

            FilterEngine.FilterResult result = engine.filter(graph);

            assertThat(result.getFilteredGraph().getDependencies())
                    .allMatch(d -> d.getScope().equals("compile") || d.getScope().equals("runtime"));
        }
    }

    @Nested
    @DisplayName("Root Module Handling")
    class RootModuleHandling {

        @Test
        @DisplayName("should always include root module")
        void shouldAlwaysIncludeRootModule() {
            DependencyGraph graph = createTestGraph();
            // Exclude everything
            FilterEngine engine = new FilterEngine(
                    Arrays.asList("nonexistent:pattern"),
                    null,
                    null
            );

            FilterEngine.FilterResult result = engine.filter(graph);

            assertThat(result.getFilteredGraph().getRootModule()).isEqualTo(rootModule);
            assertThat(result.getFilteredGraph().getModules()).contains(rootModule);
        }
    }

    @Nested
    @DisplayName("Filter Result")
    class FilterResultTest {

        @Test
        @DisplayName("should calculate total excluded correctly")
        void shouldCalculateTotalExcludedCorrectly() {
            FilterEngine.FilterResult result = new FilterEngine.FilterResult(
                    null, 100, 70, 10, 15, 5
            );

            assertThat(result.getTotalExcluded()).isEqualTo(30);
        }

        @Test
        @DisplayName("should report hasExclusions correctly")
        void shouldReportHasExclusionsCorrectly() {
            FilterEngine.FilterResult resultWithExclusions = new FilterEngine.FilterResult(
                    null, 100, 70, 10, 15, 5
            );
            FilterEngine.FilterResult resultWithoutExclusions = new FilterEngine.FilterResult(
                    null, 100, 100, 0, 0, 0
            );

            assertThat(resultWithExclusions.hasExclusions()).isTrue();
            assertThat(resultWithoutExclusions.hasExclusions()).isFalse();
        }
    }

    @Nested
    @DisplayName("All Filters Combined")
    class AllFiltersCombined {

        @Test
        @DisplayName("should apply all filters in correct order")
        void shouldApplyAllFiltersInCorrectOrder() {
            DependencyGraph graph = createTestGraph();
            FilterEngine engine = new FilterEngine(
                    Arrays.asList("org.springframework:*", "com.google.guava:*"),  // include
                    Arrays.asList("*:*-test"),                                       // exclude
                    Arrays.asList("compile")                                         // scope
            );

            FilterEngine.FilterResult result = engine.filter(graph);

            // Only spring-core, spring-web (compile scope) and guava (compile) should remain
            // spring-test excluded by pattern
            // junit excluded by include filter (not spring or guava)
            // companyApp excluded by scope (runtime) and include filter

            List<MavenModule> modules = result.getFilteredGraph().getModules().stream()
                    .filter(m -> !m.equals(rootModule))
                    .toList();

            assertThat(modules).containsExactlyInAnyOrder(springCore, guava, springWeb);
        }
    }
}


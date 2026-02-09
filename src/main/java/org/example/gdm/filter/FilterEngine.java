package org.example.gdm.filter;

import org.example.gdm.model.Dependency;
import org.example.gdm.model.DependencyGraph;
import org.example.gdm.model.MavenModule;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Filters dependencies based on include/exclude patterns and scopes.
 *
 * <p>Filter Logic:</p>
 * <ol>
 *   <li>Check exclude filters first - reject if matches any</li>
 *   <li>If include filters exist - must match at least one</li>
 *   <li>Check scope filters - reject if not in allowed scopes</li>
 * </ol>
 *
 * <p>Note: Root module is NEVER filtered out.</p>
 */
public class FilterEngine {

    private final List<PatternMatcher> includeMatchers;
    private final List<PatternMatcher> excludeMatchers;
    private final Set<String> allowedScopes;

    /**
     * Creates a new FilterEngine with the specified filters.
     *
     * @param includeFilters patterns for dependencies to include (null or empty = include all)
     * @param excludeFilters patterns for dependencies to exclude (null or empty = exclude none)
     * @param exportScopes   scopes to include (null or empty = all scopes)
     */
    public FilterEngine(List<String> includeFilters, List<String> excludeFilters, List<String> exportScopes) {
        this.includeMatchers = parsePatterns(includeFilters);
        this.excludeMatchers = parsePatterns(excludeFilters);
        this.allowedScopes = parseScopes(exportScopes);
    }

    /**
     * Filters the dependency graph according to configured filters.
     *
     * @param graph the original dependency graph
     * @return a new filtered dependency graph
     */
    public FilterResult filter(DependencyGraph graph) {
        MavenModule rootModule = graph.getRootModule();

        Set<MavenModule> includedModules = new LinkedHashSet<>();
        List<Dependency> includedDependencies = new ArrayList<>();

        int excludedByPattern = 0;
        int excludedByInclude = 0;
        int excludedByScope = 0;

        // Root module is always included
        includedModules.add(rootModule);

        for (Dependency dep : graph.getDependencies()) {
            MavenModule target = dep.getTarget();

            // Step 1: Check exclude patterns
            if (matchesAnyExclude(target)) {
                excludedByPattern++;
                continue;
            }

            // Step 2: Check include patterns (if defined)
            if (!includeMatchers.isEmpty() && !matchesAnyInclude(target)) {
                excludedByInclude++;
                continue;
            }

            // Step 3: Check scope
            if (!allowedScopes.isEmpty() && !allowedScopes.contains(dep.getScope().toLowerCase())) {
                excludedByScope++;
                continue;
            }

            // Dependency passes all filters
            includedModules.add(dep.getSource());
            includedModules.add(target);
            includedDependencies.add(dep);
        }

        DependencyGraph filteredGraph = graph.filter(includedModules, includedDependencies);

        return new FilterResult(
                filteredGraph,
                graph.getDependencyCount(),
                includedDependencies.size(),
                excludedByPattern,
                excludedByInclude,
                excludedByScope
        );
    }

    /**
     * Checks if a module matches any exclude pattern.
     */
    private boolean matchesAnyExclude(MavenModule module) {
        return excludeMatchers.stream()
                .anyMatch(m -> m.matches(module.getGroupId(), module.getArtifactId()));
    }

    /**
     * Checks if a module matches any include pattern.
     */
    private boolean matchesAnyInclude(MavenModule module) {
        return includeMatchers.stream()
                .anyMatch(m -> m.matches(module.getGroupId(), module.getArtifactId()));
    }

    /**
     * Parses pattern strings into PatternMatcher objects.
     */
    private List<PatternMatcher> parsePatterns(List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return Collections.emptyList();
        }
        return patterns.stream()
                .filter(p -> p != null && !p.trim().isEmpty())
                .map(PatternMatcher::new)
                .collect(Collectors.toList());
    }

    /**
     * Parses scope strings into a normalized set.
     */
    private Set<String> parseScopes(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return Collections.emptySet(); // Empty means all scopes allowed
        }
        return scopes.stream()
                .filter(s -> s != null && !s.trim().isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    /**
     * Returns the include patterns.
     */
    public List<String> getIncludePatterns() {
        return includeMatchers.stream()
                .map(PatternMatcher::getPattern)
                .collect(Collectors.toList());
    }

    /**
     * Returns the exclude patterns.
     */
    public List<String> getExcludePatterns() {
        return excludeMatchers.stream()
                .map(PatternMatcher::getPattern)
                .collect(Collectors.toList());
    }

    /**
     * Returns the allowed scopes.
     */
    public Set<String> getAllowedScopes() {
        return Collections.unmodifiableSet(allowedScopes);
    }

    /**
     * Result of filtering a dependency graph.
     */
    public static class FilterResult {
        private final DependencyGraph filteredGraph;
        private final int originalCount;
        private final int filteredCount;
        private final int excludedByPattern;
        private final int excludedByInclude;
        private final int excludedByScope;

        public FilterResult(DependencyGraph filteredGraph, int originalCount, int filteredCount,
                            int excludedByPattern, int excludedByInclude, int excludedByScope) {
            this.filteredGraph = filteredGraph;
            this.originalCount = originalCount;
            this.filteredCount = filteredCount;
            this.excludedByPattern = excludedByPattern;
            this.excludedByInclude = excludedByInclude;
            this.excludedByScope = excludedByScope;
        }

        public DependencyGraph getFilteredGraph() {
            return filteredGraph;
        }

        public int getOriginalCount() {
            return originalCount;
        }

        public int getFilteredCount() {
            return filteredCount;
        }

        public int getExcludedByPattern() {
            return excludedByPattern;
        }

        public int getExcludedByInclude() {
            return excludedByInclude;
        }

        public int getExcludedByScope() {
            return excludedByScope;
        }

        public int getTotalExcluded() {
            return excludedByPattern + excludedByInclude + excludedByScope;
        }

        public boolean hasExclusions() {
            return getTotalExcluded() > 0;
        }

        @Override
        public String toString() {
            return String.format(
                    "FilterResult{original=%d, filtered=%d, excluded=[pattern=%d, include=%d, scope=%d]}",
                    originalCount, filteredCount, excludedByPattern, excludedByInclude, excludedByScope
            );
        }
    }
}


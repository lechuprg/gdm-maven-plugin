package org.example.gdm.model;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a complete dependency graph.
 * Contains modules (nodes) and dependencies (edges).
 */
public class DependencyGraph {

    private final MavenModule rootModule;
    private final Set<MavenModule> modules;
    private final List<Dependency> dependencies;

    /**
     * Creates a new DependencyGraph with the specified root module.
     */
    public DependencyGraph(MavenModule rootModule) {
        this.rootModule = Objects.requireNonNull(rootModule, "rootModule cannot be null");
        this.modules = new LinkedHashSet<>();
        this.dependencies = new ArrayList<>();
        this.modules.add(rootModule);
    }

    /**
     * Creates a new DependencyGraph using the builder pattern.
     */
    public static Builder builder(MavenModule rootModule) {
        return new Builder(rootModule);
    }

    // Getters

    public MavenModule getRootModule() {
        return rootModule;
    }

    public Set<MavenModule> getModules() {
        return Collections.unmodifiableSet(modules);
    }

    public List<Dependency> getDependencies() {
        return Collections.unmodifiableList(dependencies);
    }

    // Modification methods

    /**
     * Adds a module to the graph.
     */
    public void addModule(MavenModule module) {
        modules.add(module);
    }

    /**
     * Adds a dependency to the graph.
     * Also ensures both source and target modules are in the graph.
     */
    public void addDependency(Dependency dependency) {
        modules.add(dependency.getSource());
        modules.add(dependency.getTarget());
        dependencies.add(dependency);
    }

    // Query methods

    /**
     * Returns the number of modules in the graph.
     */
    public int getModuleCount() {
        return modules.size();
    }

    /**
     * Returns the number of dependencies in the graph.
     */
    public int getDependencyCount() {
        return dependencies.size();
    }

    /**
     * Returns all direct dependencies (depth = 0) from the root module.
     */
    public List<Dependency> getDirectDependencies() {
        return dependencies.stream()
                .filter(d -> d.getDepth() == 0)
                .collect(Collectors.toList());
    }

    /**
     * Returns all transitive dependencies (depth > 0).
     */
    public List<Dependency> getTransitiveDependencies() {
        return dependencies.stream()
                .filter(d -> d.getDepth() > 0)
                .collect(Collectors.toList());
    }

    /**
     * Returns all resolved dependencies (not omitted due to conflicts).
     */
    public List<Dependency> getResolvedDependencies() {
        return dependencies.stream()
                .filter(Dependency::isResolved)
                .collect(Collectors.toList());
    }

    /**
     * Returns all conflicted dependencies (omitted due to version conflicts).
     */
    public List<Dependency> getConflictedDependencies() {
        return dependencies.stream()
                .filter(d -> !d.isResolved())
                .collect(Collectors.toList());
    }

    /**
     * Returns dependencies for a specific source module.
     */
    public List<Dependency> getDependenciesFrom(MavenModule source) {
        return dependencies.stream()
                .filter(d -> d.getSource().equals(source))
                .collect(Collectors.toList());
    }

    /**
     * Returns dependencies targeting a specific module.
     */
    public List<Dependency> getDependenciesTo(MavenModule target) {
        return dependencies.stream()
                .filter(d -> d.getTarget().equals(target))
                .collect(Collectors.toList());
    }

    /**
     * Finds a module by its GAV coordinates.
     */
    public Optional<MavenModule> findModule(String groupId, String artifactId, String version) {
        return modules.stream()
                .filter(m -> m.getGroupId().equals(groupId) &&
                            m.getArtifactId().equals(artifactId) &&
                            m.getVersion().equals(version))
                .findFirst();
    }

    /**
     * Finds all modules with the same GA (different versions).
     */
    public List<MavenModule> findModuleVersions(String groupId, String artifactId) {
        return modules.stream()
                .filter(m -> m.getGroupId().equals(groupId) &&
                            m.getArtifactId().equals(artifactId))
                .collect(Collectors.toList());
    }

    /**
     * Returns all unique GA identifiers in the graph.
     */
    public Set<String> getUniqueGAs() {
        return modules.stream()
                .map(MavenModule::getGA)
                .collect(Collectors.toSet());
    }

    /**
     * Creates a filtered copy of this graph.
     * The root module is always included.
     */
    public DependencyGraph filter(Set<MavenModule> includedModules, List<Dependency> includedDependencies) {
        DependencyGraph filtered = new DependencyGraph(rootModule);

        for (MavenModule module : includedModules) {
            filtered.addModule(module);
        }

        for (Dependency dep : includedDependencies) {
            filtered.addDependency(dep);
        }

        return filtered;
    }

    @Override
    public String toString() {
        return "DependencyGraph{" +
                "rootModule=" + rootModule +
                ", moduleCount=" + modules.size() +
                ", dependencyCount=" + dependencies.size() +
                ", conflictCount=" + getConflictedDependencies().size() +
                '}';
    }

    /**
     * Returns a detailed string representation of the graph.
     */
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DependencyGraph:\n");
        sb.append("  Root: ").append(rootModule.getGAV()).append("\n");
        sb.append("  Modules (").append(modules.size()).append("):\n");
        for (MavenModule module : modules) {
            sb.append("    - ").append(module.getGAV()).append("\n");
        }
        sb.append("  Dependencies (").append(dependencies.size()).append("):\n");
        for (Dependency dep : dependencies) {
            sb.append("    - ").append(dep.getEdgeDescription()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Builder for DependencyGraph.
     */
    public static class Builder {
        private final DependencyGraph graph;

        public Builder(MavenModule rootModule) {
            this.graph = new DependencyGraph(rootModule);
        }

        public Builder addModule(MavenModule module) {
            graph.addModule(module);
            return this;
        }

        public Builder addDependency(Dependency dependency) {
            graph.addDependency(dependency);
            return this;
        }

        public DependencyGraph build() {
            return graph;
        }
    }
}


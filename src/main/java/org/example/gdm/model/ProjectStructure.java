package org.example.gdm.model;

import java.util.*;

/**
 * Represents the complete project structure including the root module and all sub-modules.
 * This is used to capture the multi-module Maven project hierarchy for export to Neo4j
 * as ProjectModule nodes with CONTAINS_MODULE relationships.
 */
public class ProjectStructure {

    private final ProjectModule rootModule;
    private final Map<String, ProjectModule> modulesByGAV;

    /**
     * Creates a new ProjectStructure with the specified root module.
     */
    public ProjectStructure(ProjectModule rootModule) {
        this.rootModule = Objects.requireNonNull(rootModule, "rootModule cannot be null");
        this.modulesByGAV = new LinkedHashMap<>();
        indexModules(rootModule);
    }

    /**
     * Creates a ProjectStructure for a single-module project.
     * The provided module is treated as the root project with no submodules.
     */
    public static ProjectStructure singleModule(String groupId, String artifactId, String version) {
        ProjectModule root = ProjectModule.builder()
                .groupId(groupId)
                .artifactId(artifactId)
                .version(version)
                .isRootProject(true)
                .build();
        return new ProjectStructure(root);
    }

    /**
     * Creates a ProjectStructure for a single-module project from a MavenModule.
     */
    public static ProjectStructure fromMavenModule(MavenModule mavenModule) {
        return singleModule(
                mavenModule.getGroupId(),
                mavenModule.getArtifactId(),
                mavenModule.getVersion()
        );
    }

    private void indexModules(ProjectModule module) {
        modulesByGAV.put(module.getGAV(), module);
        for (ProjectModule sub : module.getSubmodules()) {
            indexModules(sub);
        }
    }

    /**
     * Returns the root project module.
     */
    public ProjectModule getRootModule() {
        return rootModule;
    }

    /**
     * Returns all project modules (root + all sub-modules at any depth).
     */
    public Collection<ProjectModule> getAllModules() {
        return Collections.unmodifiableCollection(modulesByGAV.values());
    }

    /**
     * Returns the number of modules in the project structure.
     */
    public int getModuleCount() {
        return modulesByGAV.size();
    }

    /**
     * Finds a project module by its GAV coordinates.
     */
    public Optional<ProjectModule> findModule(String groupId, String artifactId, String version) {
        String gav = groupId + ":" + artifactId + ":" + version;
        return Optional.ofNullable(modulesByGAV.get(gav));
    }

    /**
     * Checks if this is a single-module project (no sub-modules).
     */
    public boolean isSingleModule() {
        return modulesByGAV.size() == 1;
    }

    /**
     * Checks if this is a multi-module project.
     */
    public boolean isMultiModule() {
        return modulesByGAV.size() > 1;
    }

    /**
     * Returns all parent-child relationships as pairs.
     * Each pair represents a CONTAINS_MODULE relationship from parent to child.
     */
    public List<ModuleRelationship> getContainsModuleRelationships() {
        List<ModuleRelationship> relationships = new ArrayList<>();
        collectRelationships(rootModule, relationships);
        return relationships;
    }

    private void collectRelationships(ProjectModule parent, List<ModuleRelationship> relationships) {
        for (ProjectModule child : parent.getSubmodules()) {
            relationships.add(new ModuleRelationship(parent, child));
            collectRelationships(child, relationships);
        }
    }

    @Override
    public String toString() {
        return "ProjectStructure{" +
                "root=" + rootModule.getGAV() +
                ", moduleCount=" + modulesByGAV.size() +
                ", multiModule=" + isMultiModule() +
                '}';
    }

    /**
     * Represents a parent-child relationship between project modules.
     */
    public record ModuleRelationship(ProjectModule parent, ProjectModule child) {
        public ModuleRelationship {
            Objects.requireNonNull(parent, "parent cannot be null");
            Objects.requireNonNull(child, "child cannot be null");
        }
    }

    /**
     * Builder for constructing ProjectStructure instances.
     */
    public static class Builder {
        private ProjectModule.Builder rootBuilder;
        private final Map<String, ProjectModule.Builder> buildersByPath = new LinkedHashMap<>();
        private final Map<String, List<String>> childrenByParentPath = new LinkedHashMap<>();

        public Builder rootModule(String groupId, String artifactId, String version) {
            this.rootBuilder = ProjectModule.builder()
                    .groupId(groupId)
                    .artifactId(artifactId)
                    .version(version)
                    .isRootProject(true);
            buildersByPath.put("", rootBuilder);
            return this;
        }

        /**
         * Adds a submodule at the specified path relative to the root.
         *
         * @param relativePath Path like "sub-a" or "sub-a/sub-a-child"
         * @param groupId      The effective groupId
         * @param artifactId   The artifactId
         * @param version      The effective version
         */
        public Builder submodule(String relativePath, String groupId, String artifactId, String version) {
            ProjectModule.Builder subBuilder = ProjectModule.builder()
                    .groupId(groupId)
                    .artifactId(artifactId)
                    .version(version)
                    .isRootProject(false);
            buildersByPath.put(relativePath, subBuilder);

            // Determine parent path
            String parentPath = "";
            int lastSlash = relativePath.lastIndexOf('/');
            if (lastSlash > 0) {
                parentPath = relativePath.substring(0, lastSlash);
            }

            childrenByParentPath.computeIfAbsent(parentPath, k -> new ArrayList<>()).add(relativePath);
            return this;
        }

        public ProjectStructure build() {
            if (rootBuilder == null) {
                throw new IllegalStateException("Root module must be specified");
            }

            // Build modules in order (parents before children)
            Map<String, ProjectModule> builtModules = new LinkedHashMap<>();

            // Build in breadth-first order
            Queue<String> queue = new LinkedList<>();
            queue.add("");

            while (!queue.isEmpty()) {
                String path = queue.poll();
                ProjectModule.Builder builder = buildersByPath.get(path);
                if (builder == null) continue;

                // Build children first recursively
                List<String> childPaths = childrenByParentPath.getOrDefault(path, Collections.emptyList());
                for (String childPath : childPaths) {
                    if (!builtModules.containsKey(childPath)) {
                        queue.add(childPath);
                    }
                }
            }

            // Now build bottom-up
            ProjectModule root = buildModuleRecursively("", builtModules);
            return new ProjectStructure(root);
        }

        private ProjectModule buildModuleRecursively(String path, Map<String, ProjectModule> cache) {
            if (cache.containsKey(path)) {
                return cache.get(path);
            }

            ProjectModule.Builder builder = buildersByPath.get(path);
            if (builder == null) {
                throw new IllegalStateException("No builder for path: " + path);
            }

            // Build children first
            List<String> childPaths = childrenByParentPath.getOrDefault(path, Collections.emptyList());
            for (String childPath : childPaths) {
                ProjectModule child = buildModuleRecursively(childPath, cache);
                builder.submodule(child);
            }

            ProjectModule module = builder.build();
            cache.put(path, module);
            return module;
        }
    }
}


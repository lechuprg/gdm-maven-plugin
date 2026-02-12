package org.example.gdm.model;

import java.util.*;

/**
 * Represents a Maven project module (root or sub-module) within a multi-module project.
 * This provides a higher-level structural view of Maven modules, capturing the parent-child
 * relationships defined in the POM hierarchy.
 *
 * Identified by GAV (groupId:artifactId:version), same as MavenModule.
 */
public class ProjectModule {

    private final String groupId;
    private final String artifactId;
    private final String version;
    private final boolean isRootProject;
    private final List<ProjectModule> submodules;

    /**
     * Creates a new ProjectModule with required GAV coordinates.
     */
    public ProjectModule(String groupId, String artifactId, String version, boolean isRootProject) {
        this.groupId = Objects.requireNonNull(groupId, "groupId cannot be null");
        this.artifactId = Objects.requireNonNull(artifactId, "artifactId cannot be null");
        this.version = Objects.requireNonNull(version, "version cannot be null");
        this.isRootProject = isRootProject;
        this.submodules = new ArrayList<>();
    }

    /**
     * Creates a new ProjectModule using the builder pattern.
     */
    public static Builder builder() {
        return new Builder();
    }

    // Getters

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public boolean isRootProject() {
        return isRootProject;
    }

    /**
     * Returns the list of direct submodules of this project module.
     */
    public List<ProjectModule> getSubmodules() {
        return Collections.unmodifiableList(submodules);
    }

    /**
     * Adds a submodule to this project module.
     */
    public void addSubmodule(ProjectModule submodule) {
        Objects.requireNonNull(submodule, "submodule cannot be null");
        submodules.add(submodule);
    }

    /**
     * Returns whether this project module has any submodules.
     */
    public boolean hasSubmodules() {
        return !submodules.isEmpty();
    }

    /**
     * Returns all submodules (at any depth) recursively.
     */
    public List<ProjectModule> getAllSubmodulesRecursively() {
        List<ProjectModule> all = new ArrayList<>();
        collectSubmodulesRecursively(this, all);
        return all;
    }

    private void collectSubmodulesRecursively(ProjectModule module, List<ProjectModule> collector) {
        for (ProjectModule sub : module.submodules) {
            collector.add(sub);
            collectSubmodulesRecursively(sub, collector);
        }
    }

    /**
     * Returns the GA (groupId:artifactId) identifier.
     */
    public String getGA() {
        return groupId + ":" + artifactId;
    }

    /**
     * Returns the full GAV (groupId:artifactId:version) identifier.
     */
    public String getGAV() {
        return groupId + ":" + artifactId + ":" + version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectModule that = (ProjectModule) o;
        return Objects.equals(groupId, that.groupId) &&
               Objects.equals(artifactId, that.artifactId) &&
               Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version);
    }

    @Override
    public String toString() {
        return "ProjectModule{" + getGAV() + ", isRoot=" + isRootProject +
               ", submodules=" + submodules.size() + "}";
    }

    /**
     * Builder for ProjectModule.
     */
    public static class Builder {
        private String groupId;
        private String artifactId;
        private String version;
        private boolean isRootProject = false;
        private final List<ProjectModule> submodules = new ArrayList<>();

        public Builder groupId(String groupId) {
            this.groupId = groupId;
            return this;
        }

        public Builder artifactId(String artifactId) {
            this.artifactId = artifactId;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder isRootProject(boolean isRootProject) {
            this.isRootProject = isRootProject;
            return this;
        }

        public Builder submodule(ProjectModule submodule) {
            this.submodules.add(submodule);
            return this;
        }

        public Builder submodules(List<ProjectModule> submodules) {
            this.submodules.addAll(submodules);
            return this;
        }

        public ProjectModule build() {
            ProjectModule module = new ProjectModule(groupId, artifactId, version, isRootProject);
            for (ProjectModule sub : submodules) {
                module.addSubmodule(sub);
            }
            return module;
        }
    }
}


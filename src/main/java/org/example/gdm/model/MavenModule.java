package org.example.gdm.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a Maven module/artifact.
 * Identified by GAV (groupId:artifactId:version).
 */
public class MavenModule {

    private final String groupId;
    private final String artifactId;
    private final String version;
    private String packaging;
    private Instant exportTimestamp;
    private boolean isLatest;

    /**
     * Creates a new MavenModule with required GAV coordinates.
     */
    public MavenModule(String groupId, String artifactId, String version) {
        this.groupId = Objects.requireNonNull(groupId, "groupId cannot be null");
        this.artifactId = Objects.requireNonNull(artifactId, "artifactId cannot be null");
        this.version = Objects.requireNonNull(version, "version cannot be null");
        this.packaging = "jar";
        this.exportTimestamp = Instant.now();
        this.isLatest = true;
    }

    /**
     * Creates a new MavenModule using the builder pattern.
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

    public String getPackaging() {
        return packaging;
    }

    public Instant getExportTimestamp() {
        return exportTimestamp;
    }

    public boolean isLatest() {
        return isLatest;
    }

    // Setters for mutable properties

    public void setPackaging(String packaging) {
        this.packaging = packaging;
    }

    public void setExportTimestamp(Instant exportTimestamp) {
        this.exportTimestamp = exportTimestamp;
    }

    public void setLatest(boolean latest) {
        isLatest = latest;
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
        MavenModule that = (MavenModule) o;
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
        return getGAV();
    }

    /**
     * Builder for MavenModule.
     */
    public static class Builder {
        private String groupId;
        private String artifactId;
        private String version;
        private String packaging = "jar";
        private Instant exportTimestamp = Instant.now();
        private boolean isLatest = true;

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

        public Builder packaging(String packaging) {
            this.packaging = packaging;
            return this;
        }

        public Builder exportTimestamp(Instant exportTimestamp) {
            this.exportTimestamp = exportTimestamp;
            return this;
        }

        public Builder isLatest(boolean isLatest) {
            this.isLatest = isLatest;
            return this;
        }

        public MavenModule build() {
            MavenModule module = new MavenModule(groupId, artifactId, version);
            module.setPackaging(packaging);
            module.setExportTimestamp(exportTimestamp);
            module.setLatest(isLatest);
            return module;
        }
    }
}


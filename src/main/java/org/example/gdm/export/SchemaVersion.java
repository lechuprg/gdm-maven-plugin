package org.example.gdm.export;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents the database schema version.
 */
public class SchemaVersion {

    public static final String CURRENT_VERSION = "1.0.0";

    private final String version;
    private final Instant appliedAt;

    public SchemaVersion(String version, Instant appliedAt) {
        this.version = Objects.requireNonNull(version, "version cannot be null");
        this.appliedAt = appliedAt != null ? appliedAt : Instant.now();
    }

    public String getVersion() {
        return version;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    /**
     * Checks if this version is compatible with the current plugin version.
     */
    public boolean isCompatible() {
        return version.equals(CURRENT_VERSION);
    }

    /**
     * Checks if this version is older than the current plugin version.
     */
    public boolean isOlderThanCurrent() {
        return compareVersions(version, CURRENT_VERSION) < 0;
    }

    /**
     * Checks if this version is newer than the current plugin version.
     */
    public boolean isNewerThanCurrent() {
        return compareVersions(version, CURRENT_VERSION) > 0;
    }

    /**
     * Simple semantic version comparison.
     * Returns negative if v1 < v2, positive if v1 > v2, 0 if equal.
     */
    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");

        int maxLength = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < maxLength; i++) {
            int p1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int p2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
            if (p1 != p2) {
                return p1 - p2;
            }
        }
        return 0;
    }

    private int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SchemaVersion that = (SchemaVersion) o;
        return Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version);
    }

    @Override
    public String toString() {
        return "SchemaVersion{" +
                "version='" + version + '\'' +
                ", appliedAt=" + appliedAt +
                '}';
    }
}


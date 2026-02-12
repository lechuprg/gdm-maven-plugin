package org.example.gdm.version;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.example.gdm.exception.ExportException;
import org.example.gdm.export.DatabaseExporter;
import org.example.gdm.model.DependencyGraph;
import org.example.gdm.model.MavenModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Service for cleaning up old module versions.
 * Uses Maven's ComparableVersion for correct version ordering.
 */
public class VersionCleanupService {

    private static final Logger log = LoggerFactory.getLogger(VersionCleanupService.class);

    private final DatabaseExporter exporter;

    public VersionCleanupService(DatabaseExporter exporter) {
        this.exporter = exporter;
    }

    /**
     * Cleans up old versions for all modules in the graph.
     * Old versions are only deleted if they have no incoming dependencies
     * from modules outside the current export session.
     *
     * @param graph the dependency graph
     * @return total number of versions deleted
     * @throws ExportException if cleanup fails
     */
    public int cleanupOldVersions(DependencyGraph graph) throws ExportException {
        Set<String> processedGAs = new HashSet<>();
        int totalDeleted = 0;

        // Build set of all exported module GAVs
        Set<String> exportedModuleGAVs = new HashSet<>();
        for (MavenModule module : graph.getModules()) {
            exportedModuleGAVs.add(module.getGAV());
        }

        for (MavenModule module : graph.getModules()) {
            String ga = module.getGA();

            // Skip if already processed
            if (processedGAs.contains(ga)) {
                continue;
            }
            processedGAs.add(ga);

            try {
                int deleted = exporter.cleanupOldVersions(module.getGroupId(), module.getArtifactId(), exportedModuleGAVs);
                totalDeleted += deleted;
            } catch (ExportException e) {
                log.warn("Failed to cleanup old versions for {}: {}", ga, e.getMessage());
                // Continue with other modules
            }
        }

        log.info("Cleanup complete: deleted {} old versions from {} modules",
                totalDeleted, processedGAs.size());
        return totalDeleted;
    }

    /**
     * Sorts versions according to Maven semantics (highest first).
     *
     * @param versions list of version strings
     * @return sorted list (highest version first)
     */
    public static List<String> sortVersionsDescending(List<String> versions) {
        List<String> sorted = new ArrayList<>(versions);
        sorted.sort((v1, v2) -> {
            ComparableVersion cv1 = new ComparableVersion(v2); // Reversed for descending
            ComparableVersion cv2 = new ComparableVersion(v1);
            return cv1.compareTo(cv2);
        });
        return sorted;
    }

    /**
     * Determines the latest version from a list.
     *
     * @param versions list of version strings
     * @return the latest version, or empty if list is empty
     */
    public static Optional<String> findLatestVersion(List<String> versions) {
        if (versions == null || versions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(sortVersionsDescending(versions).get(0));
    }

    /**
     * Compares two Maven versions.
     *
     * @return negative if v1 < v2, positive if v1 > v2, 0 if equal
     */
    public static int compareVersions(String v1, String v2) {
        return new ComparableVersion(v1).compareTo(new ComparableVersion(v2));
    }
}


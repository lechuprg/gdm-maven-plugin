package org.example.gdm.export;

import org.example.gdm.exception.ExportException;
import org.example.gdm.model.DependencyGraph;
import org.example.gdm.model.ProjectStructure;

import java.util.Set;

/**
 * Interface for database exporters.
 * Implementations export dependency graphs to specific database types.
 */
public interface DatabaseExporter extends AutoCloseable {

    /**
     * Establishes connection to the database.
     *
     * @throws ExportException if connection fails
     */
    void connect() throws ExportException;

    /**
     * Checks the schema version in the database.
     * Creates the version metadata if it doesn't exist.
     *
     * @return the current schema version
     * @throws ExportException if check fails
     */
    SchemaVersion checkSchemaVersion() throws ExportException;

    /**
     * Exports the dependency graph to the database.
     *
     * @param graph the dependency graph to export
     * @return the export result with statistics
     * @throws ExportException if export fails
     */
    default ExportResult exportGraph(DependencyGraph graph) throws ExportException {
        return exportGraph(graph, Set.of());
    }

    /**
     * Exports the dependency graph to the database.
     * For modules that are part of the project structure (identified by projectModuleGAVs),
     * dependencies will originate from ProjectModule nodes instead of MavenModule nodes.
     *
     * @param graph             the dependency graph to export
     * @param projectModuleGAVs set of GAV strings for modules that are project modules
     * @return the export result with statistics
     * @throws ExportException if export fails
     */
    ExportResult exportGraph(DependencyGraph graph, Set<String> projectModuleGAVs) throws ExportException;

    /**
     * Exports the project module structure to the database.
     * This creates ProjectModule nodes and CONTAINS_MODULE relationships.
     * ProjectModule nodes are distinct from MavenModule nodes - project modules
     * are NOT duplicated as MavenModule nodes.
     *
     * @param projectStructure the project structure to export
     * @return the number of project modules exported
     * @throws ExportException if export fails
     */
    default int exportProjectStructure(ProjectStructure projectStructure) throws ExportException {
        // Default no-op implementation for backward compatibility
        return 0;
    }

    /**
     * Cleans up old versions of a module, keeping only the latest.
     *
     * @param groupId    the group ID
     * @param artifactId the artifact ID
     * @return the number of versions deleted
     * @throws ExportException if cleanup fails
     * @deprecated Use {@link #cleanupOldVersions(String, String, Set)} instead
     */
    @Deprecated
    default int cleanupOldVersions(String groupId, String artifactId) throws ExportException {
        return cleanupOldVersions(groupId, artifactId, Set.of());
    }

    /**
     * Cleans up old versions of a module, keeping only the latest.
     * Old versions are only deleted if they have no incoming dependencies
     * from modules outside the current export session.
     *
     * @param groupId         the group ID
     * @param artifactId      the artifact ID
     * @param exportedModules set of GAV strings (groupId:artifactId:version) exported in current session
     * @return the number of versions deleted
     * @throws ExportException if cleanup fails
     */
    int cleanupOldVersions(String groupId, String artifactId, Set<String> exportedModules) throws ExportException;

    /**
     * Checks if the exporter is connected to the database.
     *
     * @return true if connected
     */
    boolean isConnected();

    /**
     * Closes the database connection.
     */
    @Override
    void close();

    /**
     * Returns the database type (e.g., "neo4j", "oracle").
     */
    String getDatabaseType();
}


package org.example.gdm.export;

import org.example.gdm.exception.ExportException;
import org.example.gdm.model.DependencyGraph;

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
    ExportResult exportGraph(DependencyGraph graph) throws ExportException;

    /**
     * Cleans up old versions of a module, keeping only the latest.
     *
     * @param groupId    the group ID
     * @param artifactId the artifact ID
     * @return the number of versions deleted
     * @throws ExportException if cleanup fails
     */
    int cleanupOldVersions(String groupId, String artifactId) throws ExportException;

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


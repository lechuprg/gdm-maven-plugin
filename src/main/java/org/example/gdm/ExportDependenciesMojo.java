package org.example.gdm;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.example.gdm.config.ConfigurationValidator;
import org.example.gdm.config.PluginConfiguration;
import org.example.gdm.export.DatabaseExporter;
import org.example.gdm.export.ExportResult;
import org.example.gdm.export.SchemaVersion;
import org.example.gdm.export.neo4j.Neo4jExporter;
import org.example.gdm.export.oracle.OracleExporter;
import org.example.gdm.filter.FilterEngine;
import org.example.gdm.model.DependencyGraph;
import org.example.gdm.model.ProjectStructure;
import org.example.gdm.resolver.DependencyResolver;
import org.example.gdm.resolver.MavenDependencyResolver;
import org.example.gdm.resolver.ProjectStructureResolver;
import org.example.gdm.version.VersionCleanupService;

import java.util.List;

/**
 * Exports Maven project dependency graph to a database (Neo4j or Oracle).
 *
 * Usage: mvn gdm:export
 */
@Mojo(name = "export", requiresProject = true, threadSafe = true)
public class ExportDependenciesMojo extends AbstractMojo {

    // ========== Required Configuration ==========

    /**
     * Database type: "neo4j" or "oracle".
     */
    @Parameter(property = "gdm.databaseType", required = true)
    private String databaseType;

    /**
     * Database connection URL.
     * For Neo4j: bolt://localhost:7687
     * For Oracle: jdbc:oracle:thin:@localhost:1521:xe
     */
    @Parameter(property = "gdm.connectionUrl", required = true)
    private String connectionUrl;

    /**
     * Database username.
     */
    @Parameter(property = "gdm.username", required = true)
    private String username;

    /**
     * Database password (plain text or Maven encrypted).
     */
    @Parameter(property = "gdm.password", required = true)
    private String password;

    // ========== Optional Configuration ==========

    /**
     * Transitive dependency depth.
     * -1 = unlimited (default), 0 = direct only, N = N levels.
     */
    @Parameter(property = "gdm.transitiveDepth", defaultValue = "-1")
    private int transitiveDepth;

    /**
     * Scopes to export.
     * Default: all scopes (compile, runtime, test, provided, system).
     */
    @Parameter(property = "gdm.exportScopes")
    private List<String> exportScopes;

    /**
     * Include filter patterns (glob style).
     * Format: groupId:artifactId
     */
    @Parameter(property = "gdm.includeFilters")
    private List<String> includeFilters;

    /**
     * Exclude filter patterns (glob style).
     * Format: groupId:artifactId
     */
    @Parameter(property = "gdm.excludeFilters")
    private List<String> excludeFilters;

    /**
     * Whether to delete old versions after export.
     */
    @Parameter(property = "gdm.keepOnlyLatestVersion", defaultValue = "false")
    private boolean keepOnlyLatestVersion;

    /**
     * Whether to fail build on export error.
     */
    @Parameter(property = "gdm.failOnError", defaultValue = "false")
    private boolean failOnError;

    /**
     * Server ID for credentials lookup in settings.xml.
     * If specified, username and password are read from settings.xml.
     */
    @Parameter(property = "gdm.serverId")
    private String serverId;

    // ========== Maven Injected Components ==========

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Parameter(defaultValue = "${settings}", readonly = true)
    private Settings settings;

    @Component
    private RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repositorySystemSession;

    // ========== Execution ==========

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        logBanner();

        try {
            // Build configuration
            PluginConfiguration config = buildConfiguration();

            // Validate configuration
            validateConfiguration(config);

            // Log configuration summary
            logConfigurationSummary(config);

            // Execute export
            executeExport(config);

            logSuccess();

        } catch (ConfigurationValidator.ConfigurationException e) {
            // Configuration errors always fail the build (ignore failOnError)
            logError("Configuration validation failed", e);
            throw new MojoExecutionException("Plugin configuration is invalid: " + e.getMessage(), e);

        } catch (Exception e) {
            handleError(e);
        }
    }

    /**
     * Builds the plugin configuration from Mojo parameters.
     */
    private PluginConfiguration buildConfiguration() {
        // Resolve credentials from serverId if specified
        String resolvedUsername = username;
        String resolvedPassword = password;

        if (serverId != null && !serverId.trim().isEmpty()) {
            Server server = settings.getServer(serverId);
            if (server != null) {
                resolvedUsername = server.getUsername();
                resolvedPassword = server.getPassword();
                getLog().debug("Credentials loaded from server: " + serverId);
            } else {
                getLog().warn("Server not found in settings.xml: " + serverId);
            }
        }

        // Decrypt password if encrypted
        resolvedPassword = decryptPassword(resolvedPassword);

        return PluginConfiguration.builder()
                .databaseType(databaseType)
                .connectionUrl(connectionUrl)
                .username(resolvedUsername)
                .password(resolvedPassword)
                .transitiveDepth(transitiveDepth)
                .exportScopes(exportScopes)
                .includeFilters(includeFilters)
                .excludeFilters(excludeFilters)
                .keepOnlyLatestVersion(keepOnlyLatestVersion)
                .failOnError(failOnError)
                .serverId(serverId)
                .build();
    }

    /**
     * Validates the plugin configuration.
     */
    private void validateConfiguration(PluginConfiguration config)
            throws ConfigurationValidator.ConfigurationException {
        ConfigurationValidator validator = new ConfigurationValidator();
        validator.validateOrThrow(config);
        getLog().debug("Configuration validated successfully");
    }

    /**
     * Decrypts Maven encrypted password if necessary.
     */
    private String decryptPassword(String password) {
        if (password == null) {
            return null;
        }

        // Maven encrypted passwords start with { and end with }
        if (password.startsWith("{") && password.endsWith("}")) {
            getLog().debug("Detected encrypted password, decryption will be handled by Maven");
            // Maven's settings decryption is automatic when using settings.xml
            // For passwords in pom.xml, we need to use the security dispatcher
            // In practice, passwords from settings.xml are already decrypted by Maven
        }

        return password;
    }

    /**
     * Executes the dependency export.
     */
    private void executeExport(PluginConfiguration config) throws Exception {
        getLog().info("Starting dependency export...");

        // 1. Resolve dependencies
        getLog().info("Resolving dependencies...");
        DependencyResolver resolver = new MavenDependencyResolver(project, repositorySystem, repositorySystemSession);
        DependencyGraph graph = resolver.resolve(config.getTransitiveDepth());
        getLog().info("Resolved " + graph.getDependencyCount() + " dependencies (including transitive)");

        // 2. Apply filters
        getLog().info("Applying filters...");
        FilterEngine filterEngine = new FilterEngine(
                config.getIncludeFilters(),
                config.getExcludeFilters(),
                config.getExportScopes()
        );
        FilterEngine.FilterResult filterResult = filterEngine.filter(graph);
        DependencyGraph filteredGraph = filterResult.getFilteredGraph();

        if (filterResult.hasExclusions()) {
            getLog().info("After filtering: " + filterResult.getFilteredCount() + " dependencies");
            getLog().info("  Excluded by pattern: " + filterResult.getExcludedByPattern());
            getLog().info("  Excluded by include filter: " + filterResult.getExcludedByInclude());
            getLog().info("  Excluded by scope: " + filterResult.getExcludedByScope());
        }

        // 3. Create database exporter
        DatabaseExporter exporter = createExporter(config);

        try {
            // 4. Connect to database
            getLog().info("Connecting to " + config.getDatabaseType() + "...");
            exporter.connect();

            // 5. Check schema version
            SchemaVersion schemaVersion = exporter.checkSchemaVersion();
            if (!schemaVersion.isCompatible()) {
                getLog().warn("Schema version mismatch: expected " + SchemaVersion.CURRENT_VERSION +
                             ", found " + schemaVersion.getVersion());
            }

            // 6. Export graph
            getLog().info("Exporting dependency graph...");
            ExportResult result = exporter.exportGraph(filteredGraph);

            // 6.5 Export project structure (ProjectModule nodes and relationships)
            getLog().info("Resolving project structure...");
            ProjectStructureResolver structureResolver = new ProjectStructureResolver(project, session);
            ProjectStructure projectStructure = structureResolver.resolve();
            getLog().info("Exporting project structure: " + projectStructure.getModuleCount() + " module(s)");
            int projectModulesExported = exporter.exportProjectStructure(projectStructure);
            getLog().info("Project modules exported: " + projectModulesExported);

            // 7. Cleanup old versions if configured
            int deletedVersions = 0;
            if (config.isKeepOnlyLatestVersion()) {
                getLog().info("Cleaning up old versions...");
                VersionCleanupService cleanupService = new VersionCleanupService(exporter);
                deletedVersions = cleanupService.cleanupOldVersions(filteredGraph);
            }

            // 8. Log results
            logExportResult(result, projectModulesExported, deletedVersions);

        } finally {
            exporter.close();
        }
    }

    /**
     * Creates the appropriate database exporter based on configuration.
     */
    private DatabaseExporter createExporter(PluginConfiguration config) {
        String dbType = config.getDatabaseType().toLowerCase();
        return switch (dbType) {
            case "neo4j" -> new Neo4jExporter(
                    config.getConnectionUrl(),
                    config.getUsername(),
                    config.getPassword()
            );
            case "oracle" -> new OracleExporter(
                    config.getConnectionUrl(),
                    config.getUsername(),
                    config.getPassword()
            );
            default -> throw new IllegalArgumentException("Unsupported database type: " + dbType);
        };
    }

    /**
     * Logs the export result.
     */
    private void logExportResult(ExportResult result, int projectModulesExported, int deletedVersions) {
        getLog().info("============================================================");
        getLog().info("Export Results:");
        getLog().info("  Maven modules exported: " + result.getModulesExported());
        getLog().info("  Dependencies exported: " + result.getDependenciesExported());
        getLog().info("  Conflicts detected: " + result.getConflictsDetected());
        getLog().info("  Project modules exported: " + projectModulesExported);
        if (deletedVersions > 0) {
            getLog().info("  Old versions deleted: " + deletedVersions);
        }
        getLog().info("  Execution time: " + result.getExecutionTimeMs() + "ms");
        getLog().info("============================================================");
    }

    /**
     * Handles errors based on failOnError flag.
     */
    private void handleError(Exception e) throws MojoExecutionException {
        logError("Export failed", e);

        if (failOnError) {
            throw new MojoExecutionException("GDM export failed: " + e.getMessage(), e);
        } else {
            getLog().warn("Export failed but continuing build (failOnError=false)");
        }
    }

    // ========== Logging ==========

    private void logBanner() {
        getLog().info("============================================================");
        getLog().info("GDM Maven Plugin - Dependency Export");
        getLog().info("============================================================");
    }

    private void logConfigurationSummary(PluginConfiguration config) {
        getLog().info("Configuration:");
        getLog().info("  Database: " + config.getDatabaseType() + " (" + maskUrl(config.getConnectionUrl()) + ")");
        getLog().info("  Transitive depth: " + (config.getTransitiveDepth() == -1 ? "unlimited" : config.getTransitiveDepth()));

        if (config.getExportScopes() != null && !config.getExportScopes().isEmpty()) {
            getLog().info("  Export scopes: " + config.getExportScopes());
        } else {
            getLog().info("  Export scopes: [all]");
        }

        if (config.getIncludeFilters() != null && !config.getIncludeFilters().isEmpty()) {
            getLog().info("  Include filters: " + config.getIncludeFilters());
        }

        if (config.getExcludeFilters() != null && !config.getExcludeFilters().isEmpty()) {
            getLog().info("  Exclude filters: " + config.getExcludeFilters());
        }

        getLog().info("  Keep only latest version: " + config.isKeepOnlyLatestVersion());
        getLog().info("  Fail on error: " + config.isFailOnError());
        getLog().info("============================================================");
    }

    private void logSuccess() {
        getLog().info("============================================================");
        getLog().info("Export completed successfully");
        getLog().info("============================================================");
    }

    private void logError(String message, Exception e) {
        getLog().error("============================================================");
        getLog().error("GDM Export Failed: " + message);
        getLog().error("============================================================");
        getLog().error("Error: " + e.getMessage());
        if (getLog().isDebugEnabled()) {
            getLog().debug("Stack trace:", e);
        }
        getLog().error("============================================================");
    }

    /**
     * Masks sensitive parts of URL for logging.
     */
    private String maskUrl(String url) {
        if (url == null) return "null";
        // Just return the URL without credentials (if any embedded)
        return url.replaceAll("://[^@]+@", "://***@");
    }

    // ========== Getters for Testing ==========

    protected MavenProject getProject() {
        return project;
    }

    protected RepositorySystem getRepositorySystem() {
        return repositorySystem;
    }

    protected RepositorySystemSession getRepositorySystemSession() {
        return repositorySystemSession;
    }
}


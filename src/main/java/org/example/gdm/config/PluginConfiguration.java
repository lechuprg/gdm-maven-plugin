package org.example.gdm.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Plugin configuration model.
 * Contains all configuration parameters for the GDM Maven Plugin.
 */
public class PluginConfiguration {

    /**
     * Database type - either "neo4j" or "oracle".
     */
    private String databaseType;

    /**
     * Database connection URL.
     * For Neo4j: bolt://localhost:7687
     * For Oracle: jdbc:oracle:thin:@localhost:1521:xe
     */
    private String connectionUrl;

    /**
     * Database username.
     */
    private String username;

    /**
     * Database password (plain text or Maven encrypted).
     */
    private String password;

    /**
     * Transitive dependency depth.
     * -1 = unlimited, 0 = direct only, N = N levels.
     * Default: -1
     */
    private int transitiveDepth = -1;

    /**
     * Scopes to export.
     * Default: all scopes (compile, runtime, test, provided, system).
     */
    private List<String> exportScopes = new ArrayList<>();

    /**
     * Include filter patterns (glob style).
     * Format: groupId:artifactId
     */
    private List<String> includeFilters = new ArrayList<>();

    /**
     * Exclude filter patterns (glob style).
     * Format: groupId:artifactId
     */
    private List<String> excludeFilters = new ArrayList<>();

    /**
     * Whether to delete old versions after export.
     * Default: false
     */
    private boolean keepOnlyLatestVersion = false;

    /**
     * Whether to fail build on export error.
     * Default: false
     */
    private boolean failOnError = false;

    /**
     * Server ID for credentials lookup in settings.xml.
     */
    private String serverId;

    /**
     * Custom node label for project modules in Neo4j.
     * Default: "ProjectModule"
     */
    private String nodeLabel = "ProjectModule";

    // Constructors

    public PluginConfiguration() {
    }

    // Builder pattern for easy construction
    public static Builder builder() {
        return new Builder();
    }

    // Getters and Setters

    public String getDatabaseType() {
        return databaseType;
    }

    public void setDatabaseType(String databaseType) {
        this.databaseType = databaseType;
    }

    public String getConnectionUrl() {
        return connectionUrl;
    }

    public void setConnectionUrl(String connectionUrl) {
        this.connectionUrl = connectionUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getTransitiveDepth() {
        return transitiveDepth;
    }

    public void setTransitiveDepth(int transitiveDepth) {
        this.transitiveDepth = transitiveDepth;
    }

    public List<String> getExportScopes() {
        return exportScopes;
    }

    public void setExportScopes(List<String> exportScopes) {
        this.exportScopes = exportScopes != null ? exportScopes : new ArrayList<>();
    }

    public List<String> getIncludeFilters() {
        return includeFilters;
    }

    public void setIncludeFilters(List<String> includeFilters) {
        this.includeFilters = includeFilters != null ? includeFilters : new ArrayList<>();
    }

    public List<String> getExcludeFilters() {
        return excludeFilters;
    }

    public void setExcludeFilters(List<String> excludeFilters) {
        this.excludeFilters = excludeFilters != null ? excludeFilters : new ArrayList<>();
    }

    public boolean isKeepOnlyLatestVersion() {
        return keepOnlyLatestVersion;
    }

    public void setKeepOnlyLatestVersion(boolean keepOnlyLatestVersion) {
        this.keepOnlyLatestVersion = keepOnlyLatestVersion;
    }

    public boolean isFailOnError() {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getNodeLabel() {
        return nodeLabel;
    }

    public void setNodeLabel(String nodeLabel) {
        this.nodeLabel = nodeLabel != null ? nodeLabel : "ProjectModule";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PluginConfiguration that = (PluginConfiguration) o;
        return transitiveDepth == that.transitiveDepth &&
                keepOnlyLatestVersion == that.keepOnlyLatestVersion &&
                failOnError == that.failOnError &&
                Objects.equals(databaseType, that.databaseType) &&
                Objects.equals(connectionUrl, that.connectionUrl) &&
                Objects.equals(username, that.username) &&
                Objects.equals(password, that.password) &&
                Objects.equals(exportScopes, that.exportScopes) &&
                Objects.equals(includeFilters, that.includeFilters) &&
                Objects.equals(excludeFilters, that.excludeFilters) &&
                Objects.equals(serverId, that.serverId) &&
                Objects.equals(nodeLabel, that.nodeLabel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(databaseType, connectionUrl, username, password,
                transitiveDepth, exportScopes, includeFilters, excludeFilters,
                keepOnlyLatestVersion, failOnError, serverId, nodeLabel);
    }

    @Override
    public String toString() {
        return "PluginConfiguration{" +
                "databaseType='" + databaseType + '\'' +
                ", connectionUrl='" + connectionUrl + '\'' +
                ", username='" + username + '\'' +
                ", password='***'" +
                ", transitiveDepth=" + transitiveDepth +
                ", exportScopes=" + exportScopes +
                ", includeFilters=" + includeFilters +
                ", excludeFilters=" + excludeFilters +
                ", keepOnlyLatestVersion=" + keepOnlyLatestVersion +
                ", failOnError=" + failOnError +
                ", serverId='" + serverId + '\'' +
                ", nodeLabel='" + nodeLabel + '\'' +
                '}';
    }

    /**
     * Builder for PluginConfiguration.
     */
    public static class Builder {
        private final PluginConfiguration config = new PluginConfiguration();

        public Builder databaseType(String databaseType) {
            config.setDatabaseType(databaseType);
            return this;
        }

        public Builder connectionUrl(String connectionUrl) {
            config.setConnectionUrl(connectionUrl);
            return this;
        }

        public Builder username(String username) {
            config.setUsername(username);
            return this;
        }

        public Builder password(String password) {
            config.setPassword(password);
            return this;
        }

        public Builder transitiveDepth(int transitiveDepth) {
            config.setTransitiveDepth(transitiveDepth);
            return this;
        }

        public Builder exportScopes(List<String> exportScopes) {
            config.setExportScopes(exportScopes);
            return this;
        }

        public Builder includeFilters(List<String> includeFilters) {
            config.setIncludeFilters(includeFilters);
            return this;
        }

        public Builder excludeFilters(List<String> excludeFilters) {
            config.setExcludeFilters(excludeFilters);
            return this;
        }

        public Builder keepOnlyLatestVersion(boolean keepOnlyLatestVersion) {
            config.setKeepOnlyLatestVersion(keepOnlyLatestVersion);
            return this;
        }

        public Builder failOnError(boolean failOnError) {
            config.setFailOnError(failOnError);
            return this;
        }

        public Builder serverId(String serverId) {
            config.setServerId(serverId);
            return this;
        }

        public Builder nodeLabel(String nodeLabel) {
            config.setNodeLabel(nodeLabel);
            return this;
        }

        public PluginConfiguration build() {
            return config;
        }
    }
}


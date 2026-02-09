package org.example.gdm.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates plugin configuration parameters.
 * Throws ConfigurationException if validation fails.
 */
public class ConfigurationValidator {

    /**
     * Valid database types.
     */
    private static final Set<String> VALID_DATABASE_TYPES = Set.of("neo4j", "oracle");

    /**
     * Valid Maven dependency scopes.
     */
    private static final Set<String> VALID_SCOPES = Set.of(
            "compile", "runtime", "test", "provided", "system"
    );

    /**
     * Pattern for filter validation: groupId:artifactId with optional wildcards.
     * Allows: letters, numbers, dots, hyphens, underscores, asterisks, question marks.
     */
    private static final Pattern FILTER_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9.*?_-]+:[a-zA-Z0-9.*?_-]+$"
    );

    /**
     * Validates the plugin configuration.
     *
     * @param config the configuration to validate
     * @return list of validation errors (empty if valid)
     */
    public List<String> validate(PluginConfiguration config) {
        List<String> errors = new ArrayList<>();

        // Required field validation
        validateRequired(config, errors);

        // Value validation
        validateValues(config, errors);

        return errors;
    }

    /**
     * Validates configuration and throws exception if invalid.
     *
     * @param config the configuration to validate
     * @throws ConfigurationException if validation fails
     */
    public void validateOrThrow(PluginConfiguration config) throws ConfigurationException {
        List<String> errors = validate(config);
        if (!errors.isEmpty()) {
            throw new ConfigurationException(
                    "Invalid plugin configuration:\n- " + String.join("\n- ", errors),
                    errors
            );
        }
    }

    private void validateRequired(PluginConfiguration config, List<String> errors) {
        if (isBlank(config.getDatabaseType())) {
            errors.add("databaseType is required");
        }

        if (isBlank(config.getConnectionUrl())) {
            errors.add("connectionUrl is required");
        }

        if (isBlank(config.getUsername())) {
            errors.add("username is required");
        }

        // Password is required but can be empty string if DB allows
        if (config.getPassword() == null) {
            errors.add("password is required (can be empty string if database allows)");
        }
    }

    private void validateValues(PluginConfiguration config, List<String> errors) {
        // Database type validation
        if (!isBlank(config.getDatabaseType()) &&
            !VALID_DATABASE_TYPES.contains(config.getDatabaseType().toLowerCase())) {
            errors.add("databaseType must be one of: " + VALID_DATABASE_TYPES +
                       ", but was: " + config.getDatabaseType());
        }

        // Transitive depth validation
        if (config.getTransitiveDepth() < -1) {
            errors.add("transitiveDepth must be -1 (unlimited) or >= 0, but was: " +
                       config.getTransitiveDepth());
        }

        // Export scopes validation
        if (config.getExportScopes() != null) {
            for (String scope : config.getExportScopes()) {
                if (!VALID_SCOPES.contains(scope.toLowerCase())) {
                    errors.add("Invalid export scope: " + scope +
                               ". Must be one of: " + VALID_SCOPES);
                }
            }
        }

        // Include filters validation
        validateFilters(config.getIncludeFilters(), "includeFilters", errors);

        // Exclude filters validation
        validateFilters(config.getExcludeFilters(), "excludeFilters", errors);

        // Connection URL format validation
        validateConnectionUrl(config, errors);
    }

    private void validateFilters(List<String> filters, String filterName, List<String> errors) {
        if (filters == null) return;

        for (String filter : filters) {
            if (isBlank(filter)) {
                errors.add(filterName + " contains empty filter");
            } else if (!FILTER_PATTERN.matcher(filter).matches()) {
                errors.add(filterName + " contains invalid pattern: " + filter +
                           ". Must match format: groupId:artifactId (with optional * or ? wildcards)");
            }
        }
    }

    private void validateConnectionUrl(PluginConfiguration config, List<String> errors) {
        if (isBlank(config.getConnectionUrl()) || isBlank(config.getDatabaseType())) {
            return; // Already validated as required
        }

        String url = config.getConnectionUrl();
        String dbType = config.getDatabaseType().toLowerCase();

        if ("neo4j".equals(dbType)) {
            if (!url.startsWith("bolt://") && !url.startsWith("bolt+s://") &&
                !url.startsWith("neo4j://") && !url.startsWith("neo4j+s://")) {
                errors.add("connectionUrl for Neo4j must start with bolt://, bolt+s://, neo4j://, or neo4j+s://, but was: " + url);
            }
        } else if ("oracle".equals(dbType)) {
            if (!url.startsWith("jdbc:oracle:")) {
                errors.add("connectionUrl for Oracle must start with jdbc:oracle:, but was: " + url);
            }
        }
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Exception thrown when configuration validation fails.
     */
    public static class ConfigurationException extends Exception {
        private final List<String> validationErrors;

        public ConfigurationException(String message, List<String> validationErrors) {
            super(message);
            this.validationErrors = validationErrors;
        }

        public List<String> getValidationErrors() {
            return validationErrors;
        }
    }
}


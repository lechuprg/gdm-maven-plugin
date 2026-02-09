package org.example.gdm.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ConfigurationValidator.
 */
class ConfigurationValidatorTest {

    private ConfigurationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ConfigurationValidator();
    }

    @Nested
    @DisplayName("Required Field Validation")
    class RequiredFieldValidation {

        @Test
        @DisplayName("should pass when all required fields are present")
        void shouldPassWhenAllRequiredFieldsPresent() {
            PluginConfiguration config = validConfig();

            List<String> errors = validator.validate(config);

            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("should fail when databaseType is missing")
        void shouldFailWhenDatabaseTypeMissing() {
            PluginConfiguration config = validConfig();
            config.setDatabaseType(null);

            List<String> errors = validator.validate(config);

            assertThat(errors).contains("databaseType is required");
        }

        @Test
        @DisplayName("should fail when databaseType is empty")
        void shouldFailWhenDatabaseTypeEmpty() {
            PluginConfiguration config = validConfig();
            config.setDatabaseType("  ");

            List<String> errors = validator.validate(config);

            assertThat(errors).contains("databaseType is required");
        }

        @Test
        @DisplayName("should fail when connectionUrl is missing")
        void shouldFailWhenConnectionUrlMissing() {
            PluginConfiguration config = validConfig();
            config.setConnectionUrl(null);

            List<String> errors = validator.validate(config);

            assertThat(errors).contains("connectionUrl is required");
        }

        @Test
        @DisplayName("should fail when username is missing")
        void shouldFailWhenUsernameMissing() {
            PluginConfiguration config = validConfig();
            config.setUsername(null);

            List<String> errors = validator.validate(config);

            assertThat(errors).contains("username is required");
        }

        @Test
        @DisplayName("should fail when password is null")
        void shouldFailWhenPasswordNull() {
            PluginConfiguration config = validConfig();
            config.setPassword(null);

            List<String> errors = validator.validate(config);

            assertThat(errors).anyMatch(e -> e.contains("password is required"));
        }

        @Test
        @DisplayName("should pass when password is empty string")
        void shouldPassWhenPasswordEmpty() {
            PluginConfiguration config = validConfig();
            config.setPassword("");

            List<String> errors = validator.validate(config);

            assertThat(errors).noneMatch(e -> e.contains("password is required"));
        }
    }

    @Nested
    @DisplayName("Database Type Validation")
    class DatabaseTypeValidation {

        @Test
        @DisplayName("should accept 'neo4j' as valid database type")
        void shouldAcceptNeo4j() {
            PluginConfiguration config = validConfig();
            config.setDatabaseType("neo4j");

            List<String> errors = validator.validate(config);

            assertThat(errors).noneMatch(e -> e.contains("databaseType must be"));
        }

        @Test
        @DisplayName("should accept 'oracle' as valid database type")
        void shouldAcceptOracle() {
            PluginConfiguration config = validConfig();
            config.setDatabaseType("oracle");
            config.setConnectionUrl("jdbc:oracle:thin:@localhost:1521:xe");

            List<String> errors = validator.validate(config);

            assertThat(errors).noneMatch(e -> e.contains("databaseType must be"));
        }

        @Test
        @DisplayName("should reject invalid database type")
        void shouldRejectInvalidDatabaseType() {
            PluginConfiguration config = validConfig();
            config.setDatabaseType("mysql");

            List<String> errors = validator.validate(config);

            assertThat(errors).anyMatch(e -> e.contains("databaseType must be one of"));
        }

        @Test
        @DisplayName("should accept database type case-insensitively")
        void shouldAcceptCaseInsensitive() {
            PluginConfiguration config = validConfig();
            config.setDatabaseType("NEO4J");

            List<String> errors = validator.validate(config);

            assertThat(errors).noneMatch(e -> e.contains("databaseType must be"));
        }
    }

    @Nested
    @DisplayName("Transitive Depth Validation")
    class TransitiveDepthValidation {

        @Test
        @DisplayName("should accept -1 (unlimited)")
        void shouldAcceptUnlimited() {
            PluginConfiguration config = validConfig();
            config.setTransitiveDepth(-1);

            List<String> errors = validator.validate(config);

            assertThat(errors).noneMatch(e -> e.contains("transitiveDepth"));
        }

        @Test
        @DisplayName("should accept 0 (direct only)")
        void shouldAcceptZero() {
            PluginConfiguration config = validConfig();
            config.setTransitiveDepth(0);

            List<String> errors = validator.validate(config);

            assertThat(errors).noneMatch(e -> e.contains("transitiveDepth"));
        }

        @Test
        @DisplayName("should accept positive numbers")
        void shouldAcceptPositive() {
            PluginConfiguration config = validConfig();
            config.setTransitiveDepth(5);

            List<String> errors = validator.validate(config);

            assertThat(errors).noneMatch(e -> e.contains("transitiveDepth"));
        }

        @Test
        @DisplayName("should reject values less than -1")
        void shouldRejectLessThanMinusOne() {
            PluginConfiguration config = validConfig();
            config.setTransitiveDepth(-2);

            List<String> errors = validator.validate(config);

            assertThat(errors).anyMatch(e -> e.contains("transitiveDepth must be -1"));
        }
    }

    @Nested
    @DisplayName("Export Scopes Validation")
    class ExportScopesValidation {

        @Test
        @DisplayName("should accept valid scopes")
        void shouldAcceptValidScopes() {
            PluginConfiguration config = validConfig();
            config.setExportScopes(Arrays.asList("compile", "runtime", "test", "provided", "system"));

            List<String> errors = validator.validate(config);

            assertThat(errors).noneMatch(e -> e.contains("Invalid export scope"));
        }

        @Test
        @DisplayName("should reject invalid scope")
        void shouldRejectInvalidScope() {
            PluginConfiguration config = validConfig();
            config.setExportScopes(Arrays.asList("compile", "invalid"));

            List<String> errors = validator.validate(config);

            assertThat(errors).anyMatch(e -> e.contains("Invalid export scope: invalid"));
        }

        @Test
        @DisplayName("should accept empty scopes list")
        void shouldAcceptEmptyScopes() {
            PluginConfiguration config = validConfig();
            config.setExportScopes(Arrays.asList());

            List<String> errors = validator.validate(config);

            assertThat(errors).noneMatch(e -> e.contains("scope"));
        }
    }

    @Nested
    @DisplayName("Filter Pattern Validation")
    class FilterPatternValidation {

        @Test
        @DisplayName("should accept valid filter pattern")
        void shouldAcceptValidPattern() {
            PluginConfiguration config = validConfig();
            config.setIncludeFilters(Arrays.asList("org.springframework:*"));

            List<String> errors = validator.validate(config);

            assertThat(errors).noneMatch(e -> e.contains("includeFilters"));
        }

        @Test
        @DisplayName("should accept pattern with question mark wildcard")
        void shouldAcceptQuestionMarkWildcard() {
            PluginConfiguration config = validConfig();
            config.setIncludeFilters(Arrays.asList("com.company:my-?-app"));

            List<String> errors = validator.validate(config);

            assertThat(errors).noneMatch(e -> e.contains("includeFilters"));
        }

        @Test
        @DisplayName("should accept pattern with asterisk on both sides")
        void shouldAcceptAsteriskOnBothSides() {
            PluginConfiguration config = validConfig();
            config.setIncludeFilters(Arrays.asList("*:junit"));

            List<String> errors = validator.validate(config);

            assertThat(errors).noneMatch(e -> e.contains("includeFilters"));
        }

        @Test
        @DisplayName("should reject pattern without colon")
        void shouldRejectPatternWithoutColon() {
            PluginConfiguration config = validConfig();
            config.setIncludeFilters(Arrays.asList("orgspringframeworkcore"));

            List<String> errors = validator.validate(config);

            assertThat(errors).anyMatch(e -> e.contains("includeFilters contains invalid pattern"));
        }

        @Test
        @DisplayName("should reject empty filter in list")
        void shouldRejectEmptyFilter() {
            PluginConfiguration config = validConfig();
            config.setIncludeFilters(Arrays.asList("org.springframework:*", ""));

            List<String> errors = validator.validate(config);

            assertThat(errors).anyMatch(e -> e.contains("includeFilters contains empty filter"));
        }

        @Test
        @DisplayName("should validate excludeFilters similarly")
        void shouldValidateExcludeFilters() {
            PluginConfiguration config = validConfig();
            config.setExcludeFilters(Arrays.asList("invalid"));

            List<String> errors = validator.validate(config);

            assertThat(errors).anyMatch(e -> e.contains("excludeFilters contains invalid pattern"));
        }
    }

    @Nested
    @DisplayName("Connection URL Validation")
    class ConnectionUrlValidation {

        @Test
        @DisplayName("should accept valid Neo4j bolt URL")
        void shouldAcceptNeo4jBoltUrl() {
            PluginConfiguration config = validConfig();
            config.setDatabaseType("neo4j");
            config.setConnectionUrl("bolt://localhost:7687");

            List<String> errors = validator.validate(config);

            assertThat(errors).noneMatch(e -> e.contains("connectionUrl for Neo4j"));
        }

        @Test
        @DisplayName("should accept Neo4j bolt+s URL")
        void shouldAcceptNeo4jBoltSUrl() {
            PluginConfiguration config = validConfig();
            config.setDatabaseType("neo4j");
            config.setConnectionUrl("bolt+s://localhost:7687");

            List<String> errors = validator.validate(config);

            assertThat(errors).noneMatch(e -> e.contains("connectionUrl for Neo4j"));
        }

        @Test
        @DisplayName("should accept Neo4j neo4j:// URL")
        void shouldAcceptNeo4jSchemeUrl() {
            PluginConfiguration config = validConfig();
            config.setDatabaseType("neo4j");
            config.setConnectionUrl("neo4j://localhost:7687");

            List<String> errors = validator.validate(config);

            assertThat(errors).noneMatch(e -> e.contains("connectionUrl for Neo4j"));
        }

        @Test
        @DisplayName("should reject invalid Neo4j URL")
        void shouldRejectInvalidNeo4jUrl() {
            PluginConfiguration config = validConfig();
            config.setDatabaseType("neo4j");
            config.setConnectionUrl("http://localhost:7687");

            List<String> errors = validator.validate(config);

            assertThat(errors).anyMatch(e -> e.contains("connectionUrl for Neo4j must start with"));
        }

        @Test
        @DisplayName("should accept valid Oracle JDBC URL")
        void shouldAcceptOracleUrl() {
            PluginConfiguration config = validConfig();
            config.setDatabaseType("oracle");
            config.setConnectionUrl("jdbc:oracle:thin:@localhost:1521:xe");

            List<String> errors = validator.validate(config);

            assertThat(errors).noneMatch(e -> e.contains("connectionUrl for Oracle"));
        }

        @Test
        @DisplayName("should reject invalid Oracle URL")
        void shouldRejectInvalidOracleUrl() {
            PluginConfiguration config = validConfig();
            config.setDatabaseType("oracle");
            config.setConnectionUrl("jdbc:mysql://localhost:3306/db");

            List<String> errors = validator.validate(config);

            assertThat(errors).anyMatch(e -> e.contains("connectionUrl for Oracle must start with"));
        }
    }

    @Nested
    @DisplayName("validateOrThrow Method")
    class ValidateOrThrowMethod {

        @Test
        @DisplayName("should not throw for valid configuration")
        void shouldNotThrowForValidConfig() {
            PluginConfiguration config = validConfig();

            assertThatCode(() -> validator.validateOrThrow(config))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should throw ConfigurationException for invalid configuration")
        void shouldThrowForInvalidConfig() {
            PluginConfiguration config = validConfig();
            config.setDatabaseType(null);

            assertThatThrownBy(() -> validator.validateOrThrow(config))
                    .isInstanceOf(ConfigurationValidator.ConfigurationException.class)
                    .hasMessageContaining("databaseType is required");
        }

        @Test
        @DisplayName("should include all errors in exception")
        void shouldIncludeAllErrorsInException() {
            PluginConfiguration config = new PluginConfiguration();

            assertThatThrownBy(() -> validator.validateOrThrow(config))
                    .isInstanceOf(ConfigurationValidator.ConfigurationException.class)
                    .satisfies(ex -> {
                        ConfigurationValidator.ConfigurationException ce =
                                (ConfigurationValidator.ConfigurationException) ex;
                        assertThat(ce.getValidationErrors()).hasSizeGreaterThan(1);
                    });
        }
    }

    /**
     * Creates a valid configuration for testing.
     */
    private PluginConfiguration validConfig() {
        return PluginConfiguration.builder()
                .databaseType("neo4j")
                .connectionUrl("bolt://localhost:7687")
                .username("neo4j")
                .password("password")
                .build();
    }
}


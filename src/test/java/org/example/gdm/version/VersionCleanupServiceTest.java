package org.example.gdm.version;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for VersionCleanupService version comparison.
 */
class VersionCleanupServiceTest {

    @Nested
    @DisplayName("Version Sorting")
    class VersionSorting {

        @Test
        @DisplayName("should sort numeric versions correctly")
        void shouldSortNumericVersionsCorrectly() {
            List<String> versions = Arrays.asList("1.0", "1.9", "1.10", "2.0", "1.8");

            List<String> sorted = VersionCleanupService.sortVersionsDescending(versions);

            assertThat(sorted).containsExactly("2.0", "1.10", "1.9", "1.8", "1.0");
        }

        @Test
        @DisplayName("should sort semantic versions correctly")
        void shouldSortSemanticVersionsCorrectly() {
            List<String> versions = Arrays.asList("1.0.0", "1.0.1", "1.1.0", "2.0.0", "1.0.10");

            List<String> sorted = VersionCleanupService.sortVersionsDescending(versions);

            assertThat(sorted).containsExactly("2.0.0", "1.1.0", "1.0.10", "1.0.1", "1.0.0");
        }

        @Test
        @DisplayName("should sort snapshots correctly")
        void shouldSortSnapshotsCorrectly() {
            List<String> versions = Arrays.asList("1.0.0-SNAPSHOT", "1.0.0", "1.0.1-SNAPSHOT");

            List<String> sorted = VersionCleanupService.sortVersionsDescending(versions);

            // 1.0.1-SNAPSHOT > 1.0.0 > 1.0.0-SNAPSHOT
            assertThat(sorted).containsExactly("1.0.1-SNAPSHOT", "1.0.0", "1.0.0-SNAPSHOT");
        }

        @Test
        @DisplayName("should sort qualifiers correctly")
        void shouldSortQualifiersCorrectly() {
            List<String> versions = Arrays.asList(
                    "1.0.0-alpha",
                    "1.0.0-beta",
                    "1.0.0-rc1",
                    "1.0.0",
                    "1.0.0-milestone"
            );

            List<String> sorted = VersionCleanupService.sortVersionsDescending(versions);

            // Release > milestone > rc > beta > alpha
            assertThat(sorted.get(0)).isEqualTo("1.0.0");
            assertThat(sorted).contains("1.0.0-alpha", "1.0.0-beta");
        }

        @Test
        @DisplayName("should handle empty list")
        void shouldHandleEmptyList() {
            List<String> versions = Arrays.asList();

            List<String> sorted = VersionCleanupService.sortVersionsDescending(versions);

            assertThat(sorted).isEmpty();
        }

        @Test
        @DisplayName("should handle single version")
        void shouldHandleSingleVersion() {
            List<String> versions = Arrays.asList("1.0.0");

            List<String> sorted = VersionCleanupService.sortVersionsDescending(versions);

            assertThat(sorted).containsExactly("1.0.0");
        }
    }

    @Nested
    @DisplayName("Find Latest Version")
    class FindLatestVersion {

        @Test
        @DisplayName("should find latest version")
        void shouldFindLatestVersion() {
            List<String> versions = Arrays.asList("1.0.0", "2.0.0", "1.5.0");

            Optional<String> latest = VersionCleanupService.findLatestVersion(versions);

            assertThat(latest).isPresent().contains("2.0.0");
        }

        @Test
        @DisplayName("should return empty for null list")
        void shouldReturnEmptyForNull() {
            Optional<String> latest = VersionCleanupService.findLatestVersion(null);

            assertThat(latest).isEmpty();
        }

        @Test
        @DisplayName("should return empty for empty list")
        void shouldReturnEmptyForEmptyList() {
            Optional<String> latest = VersionCleanupService.findLatestVersion(Arrays.asList());

            assertThat(latest).isEmpty();
        }
    }

    @Nested
    @DisplayName("Version Comparison")
    class VersionComparison {

        @ParameterizedTest
        @DisplayName("should compare versions correctly")
        @CsvSource({
                "1.0, 2.0, -1",
                "2.0, 1.0, 1",
                "1.0, 1.0, 0",
                "1.10, 1.9, 1",
                "1.0.0, 1.0.0-SNAPSHOT, 1",
                "1.0.0-alpha, 1.0.0-beta, -1",
                "1.0.0, 1.0.0-RC1, 1"
        })
        void shouldCompareVersions(String v1, String v2, int expectedSign) {
            int result = VersionCleanupService.compareVersions(v1, v2);

            if (expectedSign < 0) {
                assertThat(result).isNegative();
            } else if (expectedSign > 0) {
                assertThat(result).isPositive();
            } else {
                assertThat(result).isZero();
            }
        }
    }
}


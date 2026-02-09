package org.example.gdm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for MavenModule.
 */
class MavenModuleTest {

    @Nested
    @DisplayName("Constructor")
    class Constructor {

        @Test
        @DisplayName("should create module with GAV")
        void shouldCreateModuleWithGav() {
            MavenModule module = new MavenModule("org.example", "my-app", "1.0.0");

            assertThat(module.getGroupId()).isEqualTo("org.example");
            assertThat(module.getArtifactId()).isEqualTo("my-app");
            assertThat(module.getVersion()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("should set default packaging to jar")
        void shouldSetDefaultPackagingToJar() {
            MavenModule module = new MavenModule("org.example", "my-app", "1.0.0");

            assertThat(module.getPackaging()).isEqualTo("jar");
        }

        @Test
        @DisplayName("should set default isLatest to true")
        void shouldSetDefaultIsLatestToTrue() {
            MavenModule module = new MavenModule("org.example", "my-app", "1.0.0");

            assertThat(module.isLatest()).isTrue();
        }

        @Test
        @DisplayName("should set exportTimestamp to now")
        void shouldSetExportTimestampToNow() {
            Instant before = Instant.now();
            MavenModule module = new MavenModule("org.example", "my-app", "1.0.0");
            Instant after = Instant.now();

            assertThat(module.getExportTimestamp())
                    .isAfterOrEqualTo(before)
                    .isBeforeOrEqualTo(after);
        }

        @Test
        @DisplayName("should throw when groupId is null")
        void shouldThrowWhenGroupIdNull() {
            assertThatThrownBy(() -> new MavenModule(null, "my-app", "1.0.0"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("groupId");
        }

        @Test
        @DisplayName("should throw when artifactId is null")
        void shouldThrowWhenArtifactIdNull() {
            assertThatThrownBy(() -> new MavenModule("org.example", null, "1.0.0"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("artifactId");
        }

        @Test
        @DisplayName("should throw when version is null")
        void shouldThrowWhenVersionNull() {
            assertThatThrownBy(() -> new MavenModule("org.example", "my-app", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("version");
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTest {

        @Test
        @DisplayName("should build module with all properties")
        void shouldBuildModuleWithAllProperties() {
            Instant timestamp = Instant.now();

            MavenModule module = MavenModule.builder()
                    .groupId("org.example")
                    .artifactId("my-app")
                    .version("1.0.0")
                    .packaging("war")
                    .exportTimestamp(timestamp)
                    .isLatest(false)
                    .build();

            assertThat(module.getGroupId()).isEqualTo("org.example");
            assertThat(module.getArtifactId()).isEqualTo("my-app");
            assertThat(module.getVersion()).isEqualTo("1.0.0");
            assertThat(module.getPackaging()).isEqualTo("war");
            assertThat(module.getExportTimestamp()).isEqualTo(timestamp);
            assertThat(module.isLatest()).isFalse();
        }
    }

    @Nested
    @DisplayName("GA and GAV")
    class GaAndGav {

        @Test
        @DisplayName("should return correct GA")
        void shouldReturnCorrectGa() {
            MavenModule module = new MavenModule("org.example", "my-app", "1.0.0");

            assertThat(module.getGA()).isEqualTo("org.example:my-app");
        }

        @Test
        @DisplayName("should return correct GAV")
        void shouldReturnCorrectGav() {
            MavenModule module = new MavenModule("org.example", "my-app", "1.0.0");

            assertThat(module.getGAV()).isEqualTo("org.example:my-app:1.0.0");
        }
    }

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("should be equal when GAV matches")
        void shouldBeEqualWhenGavMatches() {
            MavenModule module1 = new MavenModule("org.example", "my-app", "1.0.0");
            MavenModule module2 = new MavenModule("org.example", "my-app", "1.0.0");

            assertThat(module1).isEqualTo(module2);
            assertThat(module1.hashCode()).isEqualTo(module2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when groupId differs")
        void shouldNotBeEqualWhenGroupIdDiffers() {
            MavenModule module1 = new MavenModule("org.example", "my-app", "1.0.0");
            MavenModule module2 = new MavenModule("org.other", "my-app", "1.0.0");

            assertThat(module1).isNotEqualTo(module2);
        }

        @Test
        @DisplayName("should not be equal when artifactId differs")
        void shouldNotBeEqualWhenArtifactIdDiffers() {
            MavenModule module1 = new MavenModule("org.example", "my-app", "1.0.0");
            MavenModule module2 = new MavenModule("org.example", "other-app", "1.0.0");

            assertThat(module1).isNotEqualTo(module2);
        }

        @Test
        @DisplayName("should not be equal when version differs")
        void shouldNotBeEqualWhenVersionDiffers() {
            MavenModule module1 = new MavenModule("org.example", "my-app", "1.0.0");
            MavenModule module2 = new MavenModule("org.example", "my-app", "2.0.0");

            assertThat(module1).isNotEqualTo(module2);
        }

        @Test
        @DisplayName("equality should not depend on packaging")
        void equalityShouldNotDependOnPackaging() {
            MavenModule module1 = new MavenModule("org.example", "my-app", "1.0.0");
            module1.setPackaging("jar");

            MavenModule module2 = new MavenModule("org.example", "my-app", "1.0.0");
            module2.setPackaging("war");

            assertThat(module1).isEqualTo(module2);
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTest {

        @Test
        @DisplayName("should return GAV as string representation")
        void shouldReturnGavAsString() {
            MavenModule module = new MavenModule("org.example", "my-app", "1.0.0");

            assertThat(module.toString()).isEqualTo("org.example:my-app:1.0.0");
        }
    }
}


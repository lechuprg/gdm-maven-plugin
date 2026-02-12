package org.example.gdm.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ProjectModule.
 */
class ProjectModuleTest {

    private ProjectModule rootModule;
    private ProjectModule subModuleA;
    private ProjectModule subModuleB;

    @BeforeEach
    void setUp() {
        rootModule = new ProjectModule("com.example", "root-project", "1.0.0", true);
        subModuleA = new ProjectModule("com.example", "sub-a", "1.0.0", false);
        subModuleB = new ProjectModule("com.example", "sub-b", "1.0.0", false);
    }

    @Nested
    @DisplayName("Constructor")
    class Constructor {

        @Test
        @DisplayName("should create module with required properties")
        void shouldCreateModuleWithRequiredProperties() {
            ProjectModule module = new ProjectModule("org.test", "my-artifact", "2.0.0", true);

            assertThat(module.getGroupId()).isEqualTo("org.test");
            assertThat(module.getArtifactId()).isEqualTo("my-artifact");
            assertThat(module.getVersion()).isEqualTo("2.0.0");
            assertThat(module.isRootProject()).isTrue();
            assertThat(module.getSubmodules()).isEmpty();
        }

        @Test
        @DisplayName("should throw NPE for null groupId")
        void shouldThrowNpeForNullGroupId() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new ProjectModule(null, "artifact", "1.0.0", false))
                    .withMessage("groupId cannot be null");
        }

        @Test
        @DisplayName("should throw NPE for null artifactId")
        void shouldThrowNpeForNullArtifactId() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new ProjectModule("group", null, "1.0.0", false))
                    .withMessage("artifactId cannot be null");
        }

        @Test
        @DisplayName("should throw NPE for null version")
        void shouldThrowNpeForNullVersion() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new ProjectModule("group", "artifact", null, false))
                    .withMessage("version cannot be null");
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTest {

        @Test
        @DisplayName("should build module with all properties")
        void shouldBuildModuleWithAllProperties() {
            ProjectModule module = ProjectModule.builder()
                    .groupId("com.test")
                    .artifactId("test-module")
                    .version("3.0.0")
                    .isRootProject(true)
                    .build();

            assertThat(module.getGroupId()).isEqualTo("com.test");
            assertThat(module.getArtifactId()).isEqualTo("test-module");
            assertThat(module.getVersion()).isEqualTo("3.0.0");
            assertThat(module.isRootProject()).isTrue();
        }

        @Test
        @DisplayName("should build module with submodules")
        void shouldBuildModuleWithSubmodules() {
            ProjectModule child1 = new ProjectModule("com.example", "child-1", "1.0.0", false);
            ProjectModule child2 = new ProjectModule("com.example", "child-2", "1.0.0", false);

            ProjectModule root = ProjectModule.builder()
                    .groupId("com.example")
                    .artifactId("parent")
                    .version("1.0.0")
                    .isRootProject(true)
                    .submodule(child1)
                    .submodule(child2)
                    .build();

            assertThat(root.getSubmodules()).containsExactly(child1, child2);
        }
    }

    @Nested
    @DisplayName("Submodules")
    class Submodules {

        @Test
        @DisplayName("should add submodule")
        void shouldAddSubmodule() {
            rootModule.addSubmodule(subModuleA);

            assertThat(rootModule.getSubmodules()).containsExactly(subModuleA);
            assertThat(rootModule.hasSubmodules()).isTrue();
        }

        @Test
        @DisplayName("should return empty list when no submodules")
        void shouldReturnEmptyListWhenNoSubmodules() {
            assertThat(rootModule.getSubmodules()).isEmpty();
            assertThat(rootModule.hasSubmodules()).isFalse();
        }

        @Test
        @DisplayName("should throw NPE when adding null submodule")
        void shouldThrowNpeWhenAddingNullSubmodule() {
            assertThatNullPointerException()
                    .isThrownBy(() -> rootModule.addSubmodule(null))
                    .withMessage("submodule cannot be null");
        }

        @Test
        @DisplayName("should return unmodifiable list of submodules")
        void shouldReturnUnmodifiableListOfSubmodules() {
            rootModule.addSubmodule(subModuleA);
            List<ProjectModule> submodules = rootModule.getSubmodules();

            assertThatThrownBy(() -> submodules.add(subModuleB))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("should get all submodules recursively")
        void shouldGetAllSubmodulesRecursively() {
            ProjectModule nestedModule = new ProjectModule("com.example", "nested", "1.0.0", false);
            subModuleA.addSubmodule(nestedModule);
            rootModule.addSubmodule(subModuleA);
            rootModule.addSubmodule(subModuleB);

            List<ProjectModule> allSubmodules = rootModule.getAllSubmodulesRecursively();

            assertThat(allSubmodules).containsExactly(subModuleA, nestedModule, subModuleB);
        }
    }

    @Nested
    @DisplayName("Identifiers")
    class Identifiers {

        @Test
        @DisplayName("should return GA identifier")
        void shouldReturnGaIdentifier() {
            assertThat(rootModule.getGA()).isEqualTo("com.example:root-project");
        }

        @Test
        @DisplayName("should return GAV identifier")
        void shouldReturnGavIdentifier() {
            assertThat(rootModule.getGAV()).isEqualTo("com.example:root-project:1.0.0");
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal when GAV matches")
        void shouldBeEqualWhenGavMatches() {
            ProjectModule module1 = new ProjectModule("com.example", "test", "1.0.0", true);
            ProjectModule module2 = new ProjectModule("com.example", "test", "1.0.0", false);

            assertThat(module1).isEqualTo(module2);
            assertThat(module1.hashCode()).isEqualTo(module2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when artifactId differs")
        void shouldNotBeEqualWhenArtifactIdDiffers() {
            ProjectModule module1 = new ProjectModule("com.example", "test-1", "1.0.0", true);
            ProjectModule module2 = new ProjectModule("com.example", "test-2", "1.0.0", true);

            assertThat(module1).isNotEqualTo(module2);
        }

        @Test
        @DisplayName("should not be equal when version differs")
        void shouldNotBeEqualWhenVersionDiffers() {
            ProjectModule module1 = new ProjectModule("com.example", "test", "1.0.0", true);
            ProjectModule module2 = new ProjectModule("com.example", "test", "2.0.0", true);

            assertThat(module1).isNotEqualTo(module2);
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTest {

        @Test
        @DisplayName("should include GAV and isRoot in toString")
        void shouldIncludeGavAndIsRootInToString() {
            String str = rootModule.toString();

            assertThat(str).contains("com.example:root-project:1.0.0");
            assertThat(str).contains("isRoot=true");
        }
    }
}


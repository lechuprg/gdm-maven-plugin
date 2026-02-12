package org.example.gdm.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ProjectStructure.
 */
class ProjectStructureTest {

    private ProjectModule rootModule;
    private ProjectModule subModuleA;
    private ProjectModule subModuleB;
    private ProjectModule nestedModule;

    @BeforeEach
    void setUp() {
        rootModule = ProjectModule.builder()
                .groupId("com.example")
                .artifactId("root-project")
                .version("1.0.0")
                .isRootProject(true)
                .build();

        subModuleA = ProjectModule.builder()
                .groupId("com.example")
                .artifactId("sub-a")
                .version("1.0.0")
                .isRootProject(false)
                .build();

        subModuleB = ProjectModule.builder()
                .groupId("com.example")
                .artifactId("sub-b")
                .version("1.0.0")
                .isRootProject(false)
                .build();

        nestedModule = ProjectModule.builder()
                .groupId("com.example")
                .artifactId("nested")
                .version("1.0.0")
                .isRootProject(false)
                .build();
    }

    @Nested
    @DisplayName("Constructor")
    class Constructor {

        @Test
        @DisplayName("should create structure with root module")
        void shouldCreateStructureWithRootModule() {
            ProjectStructure structure = new ProjectStructure(rootModule);

            assertThat(structure.getRootModule()).isEqualTo(rootModule);
            assertThat(structure.getModuleCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should throw NPE for null root module")
        void shouldThrowNpeForNullRootModule() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new ProjectStructure(null))
                    .withMessage("rootModule cannot be null");
        }
    }

    @Nested
    @DisplayName("Single Module")
    class SingleModule {

        @Test
        @DisplayName("should create single module structure from GAV")
        void shouldCreateSingleModuleStructureFromGav() {
            ProjectStructure structure = ProjectStructure.singleModule(
                    "org.test", "single-module", "2.0.0");

            assertThat(structure.isSingleModule()).isTrue();
            assertThat(structure.isMultiModule()).isFalse();
            assertThat(structure.getRootModule().getGroupId()).isEqualTo("org.test");
            assertThat(structure.getRootModule().getArtifactId()).isEqualTo("single-module");
            assertThat(structure.getRootModule().getVersion()).isEqualTo("2.0.0");
            assertThat(structure.getRootModule().isRootProject()).isTrue();
        }

        @Test
        @DisplayName("should create structure from MavenModule")
        void shouldCreateStructureFromMavenModule() {
            MavenModule mavenModule = new MavenModule("com.test", "from-maven", "3.0.0");

            ProjectStructure structure = ProjectStructure.fromMavenModule(mavenModule);

            assertThat(structure.getRootModule().getGroupId()).isEqualTo("com.test");
            assertThat(structure.getRootModule().getArtifactId()).isEqualTo("from-maven");
            assertThat(structure.getRootModule().getVersion()).isEqualTo("3.0.0");
        }
    }

    @Nested
    @DisplayName("Multi Module")
    class MultiModule {

        @Test
        @DisplayName("should include all modules in getAllModules")
        void shouldIncludeAllModulesInGetAllModules() {
            rootModule.addSubmodule(subModuleA);
            rootModule.addSubmodule(subModuleB);
            ProjectStructure structure = new ProjectStructure(rootModule);

            Collection<ProjectModule> allModules = structure.getAllModules();

            assertThat(allModules).containsExactlyInAnyOrder(rootModule, subModuleA, subModuleB);
            assertThat(structure.getModuleCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("should detect multi-module project")
        void shouldDetectMultiModuleProject() {
            rootModule.addSubmodule(subModuleA);
            ProjectStructure structure = new ProjectStructure(rootModule);

            assertThat(structure.isMultiModule()).isTrue();
            assertThat(structure.isSingleModule()).isFalse();
        }

        @Test
        @DisplayName("should include nested modules")
        void shouldIncludeNestedModules() {
            subModuleA.addSubmodule(nestedModule);
            rootModule.addSubmodule(subModuleA);
            rootModule.addSubmodule(subModuleB);
            ProjectStructure structure = new ProjectStructure(rootModule);

            assertThat(structure.getModuleCount()).isEqualTo(4);
            assertThat(structure.getAllModules()).containsExactlyInAnyOrder(
                    rootModule, subModuleA, subModuleB, nestedModule);
        }
    }

    @Nested
    @DisplayName("Find Module")
    class FindModule {

        @Test
        @DisplayName("should find module by GAV")
        void shouldFindModuleByGav() {
            rootModule.addSubmodule(subModuleA);
            ProjectStructure structure = new ProjectStructure(rootModule);

            Optional<ProjectModule> found = structure.findModule("com.example", "sub-a", "1.0.0");

            assertThat(found).isPresent();
            assertThat(found.get()).isEqualTo(subModuleA);
        }

        @Test
        @DisplayName("should return empty when module not found")
        void shouldReturnEmptyWhenModuleNotFound() {
            ProjectStructure structure = new ProjectStructure(rootModule);

            Optional<ProjectModule> found = structure.findModule("com.example", "not-found", "1.0.0");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("CONTAINS_MODULE Relationships")
    class ContainsModuleRelationships {

        @Test
        @DisplayName("should return empty list for single module")
        void shouldReturnEmptyListForSingleModule() {
            ProjectStructure structure = new ProjectStructure(rootModule);

            List<ProjectStructure.ModuleRelationship> relationships = structure.getContainsModuleRelationships();

            assertThat(relationships).isEmpty();
        }

        @Test
        @DisplayName("should return relationships for flat multi-module")
        void shouldReturnRelationshipsForFlatMultiModule() {
            rootModule.addSubmodule(subModuleA);
            rootModule.addSubmodule(subModuleB);
            ProjectStructure structure = new ProjectStructure(rootModule);

            List<ProjectStructure.ModuleRelationship> relationships = structure.getContainsModuleRelationships();

            assertThat(relationships).hasSize(2);
            assertThat(relationships).anySatisfy(rel -> {
                assertThat(rel.parent()).isEqualTo(rootModule);
                assertThat(rel.child()).isEqualTo(subModuleA);
            });
            assertThat(relationships).anySatisfy(rel -> {
                assertThat(rel.parent()).isEqualTo(rootModule);
                assertThat(rel.child()).isEqualTo(subModuleB);
            });
        }

        @Test
        @DisplayName("should return relationships for nested modules")
        void shouldReturnRelationshipsForNestedModules() {
            subModuleA.addSubmodule(nestedModule);
            rootModule.addSubmodule(subModuleA);
            ProjectStructure structure = new ProjectStructure(rootModule);

            List<ProjectStructure.ModuleRelationship> relationships = structure.getContainsModuleRelationships();

            assertThat(relationships).hasSize(2);
            assertThat(relationships).anySatisfy(rel -> {
                assertThat(rel.parent()).isEqualTo(rootModule);
                assertThat(rel.child()).isEqualTo(subModuleA);
            });
            assertThat(relationships).anySatisfy(rel -> {
                assertThat(rel.parent()).isEqualTo(subModuleA);
                assertThat(rel.child()).isEqualTo(nestedModule);
            });
        }
    }

    @Nested
    @DisplayName("ModuleRelationship")
    class ModuleRelationshipTest {

        @Test
        @DisplayName("should create relationship with parent and child")
        void shouldCreateRelationshipWithParentAndChild() {
            ProjectStructure.ModuleRelationship relationship =
                    new ProjectStructure.ModuleRelationship(rootModule, subModuleA);

            assertThat(relationship.parent()).isEqualTo(rootModule);
            assertThat(relationship.child()).isEqualTo(subModuleA);
        }

        @Test
        @DisplayName("should throw NPE for null parent")
        void shouldThrowNpeForNullParent() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new ProjectStructure.ModuleRelationship(null, subModuleA))
                    .withMessage("parent cannot be null");
        }

        @Test
        @DisplayName("should throw NPE for null child")
        void shouldThrowNpeForNullChild() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new ProjectStructure.ModuleRelationship(rootModule, null))
                    .withMessage("child cannot be null");
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTest {

        @Test
        @DisplayName("should include key info in toString")
        void shouldIncludeKeyInfoInToString() {
            rootModule.addSubmodule(subModuleA);
            ProjectStructure structure = new ProjectStructure(rootModule);

            String str = structure.toString();

            assertThat(str).contains("root-project");
            assertThat(str).contains("moduleCount=2");
            assertThat(str).contains("multiModule=true");
        }
    }
}


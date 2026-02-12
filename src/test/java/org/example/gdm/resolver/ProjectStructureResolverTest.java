package org.example.gdm.resolver;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.example.gdm.model.ProjectModule;
import org.example.gdm.model.ProjectStructure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProjectStructureResolver.
 */
@ExtendWith(MockitoExtension.class)
class ProjectStructureResolverTest {

    @Mock
    private MavenProject project;

    @Mock
    private MavenSession session;

    private ProjectStructureResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ProjectStructureResolver(project, session);
    }

    @Nested
    @DisplayName("Constructor")
    class Constructor {

        @Test
        @DisplayName("should throw NPE for null project")
        void shouldThrowNpeForNullProject() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new ProjectStructureResolver(null, session))
                    .withMessage("project cannot be null");
        }

        @Test
        @DisplayName("should allow null session")
        void shouldAllowNullSession() {
            assertThatCode(() -> new ProjectStructureResolver(project, null))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Single Module Project")
    class SingleModuleProject {

        @Test
        @DisplayName("should resolve single module project with no submodules")
        void shouldResolveSingleModuleProjectWithNoSubmodules() {
            // Given
            when(project.getGroupId()).thenReturn("com.example");
            when(project.getArtifactId()).thenReturn("single-module");
            when(project.getVersion()).thenReturn("1.0.0");
            when(project.getModules()).thenReturn(Collections.emptyList());

            // When
            ProjectStructure structure = resolver.resolve();

            // Then
            assertThat(structure).isNotNull();
            assertThat(structure.isSingleModule()).isTrue();

            ProjectModule root = structure.getRootModule();
            assertThat(root.getGroupId()).isEqualTo("com.example");
            assertThat(root.getArtifactId()).isEqualTo("single-module");
            assertThat(root.getVersion()).isEqualTo("1.0.0");
            assertThat(root.isRootProject()).isTrue();
        }

        @Test
        @DisplayName("should resolve single module project with null modules list")
        void shouldResolveSingleModuleProjectWithNullModulesList() {
            // Given
            when(project.getGroupId()).thenReturn("com.example");
            when(project.getArtifactId()).thenReturn("single-module");
            when(project.getVersion()).thenReturn("1.0.0");
            when(project.getModules()).thenReturn(null);

            // When
            ProjectStructure structure = resolver.resolve();

            // Then
            assertThat(structure).isNotNull();
            assertThat(structure.isSingleModule()).isTrue();
        }
    }

    @Nested
    @DisplayName("Multi Module Project")
    class MultiModuleProject {

        @Test
        @DisplayName("should detect multi-module project from modules list")
        void shouldDetectMultiModuleProjectFromModulesList() {
            // Given
            when(project.getGroupId()).thenReturn("com.example");
            when(project.getArtifactId()).thenReturn("parent");
            when(project.getVersion()).thenReturn("1.0.0");
            when(project.getModules()).thenReturn(Arrays.asList("sub-a", "sub-b"));
            when(session.getProjects()).thenReturn(null); // No reactor info

            // When
            ProjectStructure structure = resolver.resolve();

            // Then
            assertThat(structure).isNotNull();
            assertThat(structure.isMultiModule()).isTrue();
            assertThat(structure.getModuleCount()).isEqualTo(3); // root + 2 submodules
        }

        @Test
        @DisplayName("should resolve from reactor when session has projects")
        void shouldResolveFromReactorWhenSessionHasProjects() {
            // Given
            when(project.getGroupId()).thenReturn("com.example");
            when(project.getArtifactId()).thenReturn("parent");
            when(project.getVersion()).thenReturn("1.0.0");
            when(project.getModules()).thenReturn(Arrays.asList("sub-a", "sub-b"));

            MavenProject subA = mock(MavenProject.class);
            when(subA.getGroupId()).thenReturn("com.example");
            when(subA.getArtifactId()).thenReturn("sub-a");
            when(subA.getVersion()).thenReturn("1.0.0");
            when(subA.getParent()).thenReturn(project);

            MavenProject subB = mock(MavenProject.class);
            when(subB.getGroupId()).thenReturn("com.example");
            when(subB.getArtifactId()).thenReturn("sub-b");
            when(subB.getVersion()).thenReturn("1.0.0");
            when(subB.getParent()).thenReturn(project);

            List<MavenProject> reactorProjects = Arrays.asList(project, subA, subB);
            when(session.getProjects()).thenReturn(reactorProjects);

            // When
            ProjectStructure structure = resolver.resolve();

            // Then
            assertThat(structure).isNotNull();
            assertThat(structure.isMultiModule()).isTrue();
            assertThat(structure.getModuleCount()).isEqualTo(3);

            // Verify relationships
            var relationships = structure.getContainsModuleRelationships();
            assertThat(relationships).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Submodule fallback")
    class SubmoduleFallback {

        @Test
        @DisplayName("should create submodules with inherited groupId and version when reactor unavailable")
        void shouldCreateSubmodulesWithInheritedGavWhenReactorUnavailable() {
            // Given
            when(project.getGroupId()).thenReturn("com.example");
            when(project.getArtifactId()).thenReturn("parent");
            when(project.getVersion()).thenReturn("2.0.0");
            when(project.getModules()).thenReturn(Arrays.asList("child-module"));
            when(session.getProjects()).thenReturn(Collections.emptyList());

            // When
            ProjectStructure structure = resolver.resolve();

            // Then
            assertThat(structure.getModuleCount()).isEqualTo(2);

            var submodules = structure.getRootModule().getSubmodules();
            assertThat(submodules).hasSize(1);
            assertThat(submodules.get(0).getGroupId()).isEqualTo("com.example");
            assertThat(submodules.get(0).getArtifactId()).isEqualTo("child-module");
            assertThat(submodules.get(0).getVersion()).isEqualTo("2.0.0");
            assertThat(submodules.get(0).isRootProject()).isFalse();
        }
    }
}


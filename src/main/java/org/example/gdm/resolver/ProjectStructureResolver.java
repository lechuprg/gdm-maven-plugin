package org.example.gdm.resolver;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.example.gdm.model.ProjectModule;
import org.example.gdm.model.ProjectStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Resolves the project module structure from Maven projects.
 *
 * This resolver extracts the parent-child relationships between Maven modules
 * in a multi-module project and creates a ProjectStructure that can be exported
 * to Neo4j as ProjectModule nodes with CONTAINS_MODULE relationships.
 */
public class ProjectStructureResolver {

    private static final Logger log = LoggerFactory.getLogger(ProjectStructureResolver.class);

    private final MavenProject project;
    private final MavenSession session;

    /**
     * Creates a new ProjectStructureResolver.
     *
     * @param project The current Maven project
     * @param session The Maven session (may be null for single-module projects)
     */
    public ProjectStructureResolver(MavenProject project, MavenSession session) {
        this.project = Objects.requireNonNull(project, "project cannot be null");
        this.session = session;
    }

    /**
     * Resolves the project structure for the current project.
     *
     * For single-module projects, returns a ProjectStructure with just the root module.
     * For multi-module projects, returns a ProjectStructure with the full module hierarchy.
     *
     * @return The resolved ProjectStructure
     */
    public ProjectStructure resolve() {
        log.info("Resolving project structure for {}:{}:{}",
                project.getGroupId(), project.getArtifactId(), project.getVersion());

        // Check if this is a multi-module project
        List<String> modules = project.getModules();

        if (modules == null || modules.isEmpty()) {
            // Single module project - check if we're a submodule
            MavenProject executionRoot = findExecutionRoot();
            if (executionRoot != null && !executionRoot.equals(project)) {
                // We're a submodule, try to resolve from the execution root
                log.debug("Current project is a submodule, resolving from execution root");
                return resolveFromRoot(executionRoot);
            }

            // Simple single-module project
            log.info("Single-module project detected");
            return ProjectStructure.singleModule(
                    project.getGroupId(),
                    project.getArtifactId(),
                    project.getVersion()
            );
        }

        // Multi-module project
        log.info("Multi-module project detected with {} direct submodules", modules.size());
        return resolveMultiModuleStructure();
    }

    /**
     * Finds the execution root project (top-level project being built).
     */
    private MavenProject findExecutionRoot() {
        if (session == null) {
            return null;
        }

        // The execution root is the top-level project
        MavenProject root = session.getTopLevelProject();
        if (root != null) {
            return root;
        }

        // Fall back to the current project if no execution root
        return null;
    }

    /**
     * Resolves the project structure from a given root project.
     */
    private ProjectStructure resolveFromRoot(MavenProject rootProject) {
        ProjectModule rootModule = createProjectModule(rootProject, true);

        // Get all projects in the reactor (session)
        if (session != null) {
            List<MavenProject> allProjects = session.getProjects();
            if (allProjects != null && !allProjects.isEmpty()) {
                Map<String, ProjectModule> moduleMap = new LinkedHashMap<>();
                moduleMap.put(rootModule.getGAV(), rootModule);

                // Build map of all modules
                for (MavenProject mp : allProjects) {
                    if (!mp.equals(rootProject)) {
                        ProjectModule pm = createProjectModule(mp, false);
                        moduleMap.put(pm.getGAV(), pm);
                    }
                }

                // Establish parent-child relationships
                for (MavenProject mp : allProjects) {
                    if (!mp.equals(rootProject)) {
                        MavenProject parent = mp.getParent();
                        if (parent != null) {
                            String parentGAV = parent.getGroupId() + ":" + parent.getArtifactId() + ":" + parent.getVersion();
                            String childGAV = mp.getGroupId() + ":" + mp.getArtifactId() + ":" + mp.getVersion();

                            ProjectModule parentModule = moduleMap.get(parentGAV);
                            ProjectModule childModule = moduleMap.get(childGAV);

                            if (parentModule != null && childModule != null) {
                                parentModule.addSubmodule(childModule);
                            }
                        }
                    }
                }

                return new ProjectStructure(rootModule);
            }
        }

        // Fall back to simple structure if session info unavailable
        return new ProjectStructure(rootModule);
    }

    /**
     * Resolves the project structure for a multi-module project.
     */
    private ProjectStructure resolveMultiModuleStructure() {
        ProjectModule rootModule = createProjectModule(project, true);

        // Try to resolve using session's reactor projects
        if (session != null) {
            List<MavenProject> reactorProjects = session.getProjects();
            if (reactorProjects != null && reactorProjects.size() > 1) {
                return resolveFromReactor(rootModule, reactorProjects);
            }
        }

        // Fall back to module names only (limited info)
        List<String> moduleNames = project.getModules();
        log.debug("Resolving {} submodules from POM module declarations", moduleNames.size());

        // We can only create stub modules with the artifactId
        // In a real build, the reactor would have full info
        for (String moduleName : moduleNames) {
            // Create submodule with inherited groupId/version
            ProjectModule submodule = ProjectModule.builder()
                    .groupId(project.getGroupId())
                    .artifactId(moduleName)
                    .version(project.getVersion())
                    .isRootProject(false)
                    .build();
            rootModule.addSubmodule(submodule);
        }

        log.info("Resolved project structure: {} total modules",
                1 + rootModule.getSubmodules().size());

        return new ProjectStructure(rootModule);
    }

    /**
     * Resolves project structure using reactor projects from Maven session.
     */
    private ProjectStructure resolveFromReactor(ProjectModule rootModule, List<MavenProject> reactorProjects) {
        log.debug("Resolving project structure from {} reactor projects", reactorProjects.size());

        // Build a map of all modules keyed by GAV
        Map<String, ProjectModule> modulesByGAV = new LinkedHashMap<>();

        modulesByGAV.put(rootModule.getGAV(), rootModule);

        // Create ProjectModule for each reactor project
        for (MavenProject mp : reactorProjects) {
            String gav = mp.getGroupId() + ":" + mp.getArtifactId() + ":" + mp.getVersion();
            if (!modulesByGAV.containsKey(gav)) {
                ProjectModule pm = createProjectModule(mp, false);
                modulesByGAV.put(gav, pm);
            }
        }

        // Establish parent-child relationships based on Maven parent references
        for (MavenProject mp : reactorProjects) {
            String childGAV = mp.getGroupId() + ":" + mp.getArtifactId() + ":" + mp.getVersion();
            if (childGAV.equals(rootModule.getGAV())) {
                continue; // Skip root
            }

            MavenProject parent = mp.getParent();
            if (parent != null) {
                String parentGAV = parent.getGroupId() + ":" + parent.getArtifactId() + ":" + parent.getVersion();
                ProjectModule parentModule = modulesByGAV.get(parentGAV);
                ProjectModule childModule = modulesByGAV.get(childGAV);

                if (parentModule != null && childModule != null) {
                    // Check if already added to avoid duplicates
                    if (!parentModule.getSubmodules().contains(childModule)) {
                        parentModule.addSubmodule(childModule);
                        log.debug("Added {} as submodule of {}", childModule.getArtifactId(), parentModule.getArtifactId());
                    }
                }
            }
        }

        int totalModules = modulesByGAV.size();
        log.info("Resolved project structure: {} total modules", totalModules);

        return new ProjectStructure(rootModule);
    }

    /**
     * Creates a ProjectModule from a MavenProject.
     */
    private ProjectModule createProjectModule(MavenProject mp, boolean isRoot) {
        return ProjectModule.builder()
                .groupId(mp.getGroupId())
                .artifactId(mp.getArtifactId())
                .version(mp.getVersion())
                .isRootProject(isRoot)
                .build();
    }
}


package org.example.gdm.resolver;

import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.example.gdm.exception.ResolutionException;
import org.example.gdm.model.Dependency;
import org.example.gdm.model.DependencyGraph;
import org.example.gdm.model.MavenModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Resolves Maven dependencies using the Maven Resolver (Aether) API.
 */
public class MavenDependencyResolver implements DependencyResolver {

    private static final Logger log = LoggerFactory.getLogger(MavenDependencyResolver.class);

    private final MavenProject project;
    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession session;

    public MavenDependencyResolver(MavenProject project,
                                   RepositorySystem repositorySystem,
                                   RepositorySystemSession session) {
        this.project = project;
        this.repositorySystem = repositorySystem;
        this.session = session;
    }

    @Override
    public DependencyGraph resolve(int transitiveDepth) throws ResolutionException {
        log.info("Resolving dependencies for {}:{}:{} with depth {}",
                project.getGroupId(), project.getArtifactId(), project.getVersion(),
                transitiveDepth == -1 ? "unlimited" : transitiveDepth);

        try {
            // Create the root module
            MavenModule rootModule = MavenModule.builder()
                    .groupId(project.getGroupId())
                    .artifactId(project.getArtifactId())
                    .version(project.getVersion())
                    .packaging(project.getPackaging())
                    .build();

            DependencyGraph graph = new DependencyGraph(rootModule);

            // Collect dependencies
            CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRoot(new org.eclipse.aether.graph.Dependency(
                    new DefaultArtifact(
                            project.getGroupId(),
                            project.getArtifactId(),
                            project.getPackaging(),
                            project.getVersion()
                    ), null
            ));

            // Add project dependencies
            for (org.apache.maven.model.Dependency dep : project.getDependencies()) {
                org.eclipse.aether.graph.Dependency aetherDep = new org.eclipse.aether.graph.Dependency(
                        new DefaultArtifact(
                                dep.getGroupId(),
                                dep.getArtifactId(),
                                dep.getClassifier(),
                                dep.getType() != null ? dep.getType() : "jar",
                                dep.getVersion()
                        ),
                        dep.getScope(),
                        dep.isOptional()
                );
                collectRequest.addDependency(aetherDep);
            }

            // Add repositories
            collectRequest.setRepositories(project.getRemoteProjectRepositories());

            // Collect the dependency tree
            CollectResult collectResult = repositorySystem.collectDependencies(session, collectRequest);
            DependencyNode rootNode = collectResult.getRoot();

            // Build the graph with conflict detection
            buildGraph(graph, rootModule, rootNode, transitiveDepth);

            log.info("Resolved {} modules and {} dependencies",
                    graph.getModuleCount(), graph.getDependencyCount());
            log.info("Conflicts detected: {}", graph.getConflictedDependencies().size());

            return graph;

        } catch (DependencyCollectionException e) {
            throw new ResolutionException("Failed to collect dependencies: " + e.getMessage(), e);
        }
    }

    private void buildGraph(DependencyGraph graph, MavenModule parentModule,
                           DependencyNode node, int maxDepth) {
        // Visit children
        for (DependencyNode child : node.getChildren()) {
            processNode(graph, parentModule, child, 0, maxDepth, new HashSet<>());
        }
    }

    private void processNode(DependencyGraph graph, MavenModule parentModule,
                            DependencyNode node, int currentDepth, int maxDepth,
                            Set<String> visited) {
        // Check depth limit
        if (maxDepth >= 0 && currentDepth > maxDepth) {
            return;
        }

        org.eclipse.aether.graph.Dependency dep = node.getDependency();
        if (dep == null) {
            return;
        }

        Artifact artifact = dep.getArtifact();
        String gav = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();

        // Avoid cycles
        if (visited.contains(gav)) {
            log.debug("Skipping already visited: {}", gav);
            return;
        }
        visited.add(gav);

        // Create module for this dependency
        MavenModule module = MavenModule.builder()
                .groupId(artifact.getGroupId())
                .artifactId(artifact.getArtifactId())
                .version(artifact.getVersion())
                .packaging(artifact.getExtension())
                .build();

        graph.addModule(module);

        // Determine if this is resolved or conflicted
        boolean isResolved = !isOmittedForConflict(node);

        // Create dependency edge
        Dependency dependency = Dependency.builder()
                .source(parentModule)
                .target(module)
                .scope(dep.getScope() != null ? dep.getScope() : "compile")
                .optional(dep.isOptional())
                .depth(currentDepth)
                .isResolved(isResolved)
                .build();

        graph.addDependency(dependency);

        if (!isResolved) {
            log.debug("Conflict detected: {} (omitted)", gav);
        }

        // Process children (transitive dependencies)
        if (isResolved) {
            for (DependencyNode child : node.getChildren()) {
                processNode(graph, module, child, currentDepth + 1, maxDepth, new HashSet<>(visited));
            }
        }
    }

    /**
     * Checks if a node was omitted due to a version conflict.
     */
    private boolean isOmittedForConflict(DependencyNode node) {
        // Check if node has data indicating it was omitted
        Map<?, ?> data = node.getData();
        if (data != null) {
            Object winner = data.get("conflict.winner");
            if (winner != null) {
                return true;
            }
        }

        // Check if this is a duplicate that was replaced
        // In Aether, an omitted node typically has no children and specific markers
        // A more robust check would involve comparing versions in the graph
        return false;
    }
}


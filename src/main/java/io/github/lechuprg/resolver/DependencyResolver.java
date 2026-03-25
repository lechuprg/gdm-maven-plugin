package io.github.lechuprg.resolver;

import io.github.lechuprg.exception.ResolutionException;
import io.github.lechuprg.model.DependencyGraph;

/**
 * Interface for dependency resolvers.
 */
public interface DependencyResolver {

    /**
     * Resolves the dependency graph for the current project.
     *
     * @param transitiveDepth the maximum depth for transitive dependencies
     *                        (-1 = unlimited, 0 = direct only, N = N levels)
     * @return the resolved dependency graph
     * @throws ResolutionException if resolution fails
     */
    DependencyGraph resolve(int transitiveDepth) throws ResolutionException;
}


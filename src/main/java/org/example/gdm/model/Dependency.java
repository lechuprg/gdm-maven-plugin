package org.example.gdm.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a dependency relationship between two Maven modules.
 * This is an edge in the dependency graph.
 */
public class Dependency {

    private final MavenModule source;
    private final MavenModule target;
    private final String scope;
    private final boolean optional;
    private final int depth;
    private final boolean isResolved;
    private Instant exportTimestamp;

    /**
     * Creates a new Dependency.
     *
     * @param source     the source module (dependent)
     * @param target     the target module (dependency)
     * @param scope      the dependency scope (compile, runtime, test, provided, system)
     * @param optional   whether this is an optional dependency
     * @param depth      depth from root module (0 = direct dependency)
     * @param isResolved whether this dependency is resolved (true) or omitted due to conflict (false)
     */
    public Dependency(MavenModule source, MavenModule target, String scope,
                      boolean optional, int depth, boolean isResolved) {
        this.source = Objects.requireNonNull(source, "source cannot be null");
        this.target = Objects.requireNonNull(target, "target cannot be null");
        this.scope = scope != null ? scope : "compile";
        this.optional = optional;
        this.depth = depth;
        this.isResolved = isResolved;
        this.exportTimestamp = Instant.now();
    }

    /**
     * Creates a new Dependency using the builder pattern.
     */
    public static Builder builder() {
        return new Builder();
    }

    // Getters

    public MavenModule getSource() {
        return source;
    }

    public MavenModule getTarget() {
        return target;
    }

    public String getScope() {
        return scope;
    }

    public boolean isOptional() {
        return optional;
    }

    public int getDepth() {
        return depth;
    }

    public boolean isResolved() {
        return isResolved;
    }

    public Instant getExportTimestamp() {
        return exportTimestamp;
    }

    public void setExportTimestamp(Instant exportTimestamp) {
        this.exportTimestamp = exportTimestamp;
    }

    /**
     * Returns a string representation of this dependency edge.
     */
    public String getEdgeDescription() {
        return source.getGAV() + " -> " + target.getGAV() +
               " (" + scope + ", depth=" + depth +
               ", resolved=" + isResolved +
               (optional ? ", optional" : "") + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Dependency that = (Dependency) o;
        return depth == that.depth &&
               Objects.equals(source, that.source) &&
               Objects.equals(target, that.target) &&
               Objects.equals(scope, that.scope);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, target, scope, depth);
    }

    @Override
    public String toString() {
        return getEdgeDescription();
    }

    /**
     * Builder for Dependency.
     */
    public static class Builder {
        private MavenModule source;
        private MavenModule target;
        private String scope = "compile";
        private boolean optional = false;
        private int depth = 0;
        private boolean isResolved = true;
        private Instant exportTimestamp = Instant.now();

        public Builder source(MavenModule source) {
            this.source = source;
            return this;
        }

        public Builder target(MavenModule target) {
            this.target = target;
            return this;
        }

        public Builder scope(String scope) {
            this.scope = scope;
            return this;
        }

        public Builder optional(boolean optional) {
            this.optional = optional;
            return this;
        }

        public Builder depth(int depth) {
            this.depth = depth;
            return this;
        }

        public Builder isResolved(boolean isResolved) {
            this.isResolved = isResolved;
            return this;
        }

        public Builder exportTimestamp(Instant exportTimestamp) {
            this.exportTimestamp = exportTimestamp;
            return this;
        }

        public Dependency build() {
            Dependency dep = new Dependency(source, target, scope, optional, depth, isResolved);
            dep.setExportTimestamp(exportTimestamp);
            return dep;
        }
    }
}


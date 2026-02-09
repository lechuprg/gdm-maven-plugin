package org.example.gdm.export;

/**
 * Result of a dependency graph export operation.
 */
public class ExportResult {

    private final int modulesExported;
    private final int dependenciesExported;
    private final int conflictsDetected;
    private final int oldVersionsDeleted;
    private final long executionTimeMs;
    private final boolean success;
    private final String errorMessage;

    private ExportResult(Builder builder) {
        this.modulesExported = builder.modulesExported;
        this.dependenciesExported = builder.dependenciesExported;
        this.conflictsDetected = builder.conflictsDetected;
        this.oldVersionsDeleted = builder.oldVersionsDeleted;
        this.executionTimeMs = builder.executionTimeMs;
        this.success = builder.success;
        this.errorMessage = builder.errorMessage;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ExportResult success(int modules, int dependencies, int conflicts, long timeMs) {
        return builder()
                .modulesExported(modules)
                .dependenciesExported(dependencies)
                .conflictsDetected(conflicts)
                .executionTimeMs(timeMs)
                .success(true)
                .build();
    }

    public static ExportResult failure(String errorMessage) {
        return builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }

    public int getModulesExported() {
        return modulesExported;
    }

    public int getDependenciesExported() {
        return dependenciesExported;
    }

    public int getConflictsDetected() {
        return conflictsDetected;
    }

    public int getOldVersionsDeleted() {
        return oldVersionsDeleted;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        if (success) {
            return String.format(
                    "ExportResult{success=true, modules=%d, dependencies=%d, conflicts=%d, deleted=%d, time=%dms}",
                    modulesExported, dependenciesExported, conflictsDetected, oldVersionsDeleted, executionTimeMs
            );
        } else {
            return String.format("ExportResult{success=false, error='%s'}", errorMessage);
        }
    }

    public static class Builder {
        private int modulesExported;
        private int dependenciesExported;
        private int conflictsDetected;
        private int oldVersionsDeleted;
        private long executionTimeMs;
        private boolean success = true;
        private String errorMessage;

        public Builder modulesExported(int modulesExported) {
            this.modulesExported = modulesExported;
            return this;
        }

        public Builder dependenciesExported(int dependenciesExported) {
            this.dependenciesExported = dependenciesExported;
            return this;
        }

        public Builder conflictsDetected(int conflictsDetected) {
            this.conflictsDetected = conflictsDetected;
            return this;
        }

        public Builder oldVersionsDeleted(int oldVersionsDeleted) {
            this.oldVersionsDeleted = oldVersionsDeleted;
            return this;
        }

        public Builder executionTimeMs(long executionTimeMs) {
            this.executionTimeMs = executionTimeMs;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public ExportResult build() {
            return new ExportResult(this);
        }
    }
}


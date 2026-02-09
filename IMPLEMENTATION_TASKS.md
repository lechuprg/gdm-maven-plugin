# GDM Maven Plugin - Implementation Tasks

**Based on:** TECHNICAL_SPEC_v3.md, ARCHITECTURE_v3.md  
**Date:** 2026-02-09  
**Total Tasks:** 20

## Implementation Progress

| Task | Description | Status | Files Created |
|------|-------------|--------|---------------|
| 1 | Maven Plugin Project Setup | ✅ DONE | pom.xml, package structure |
| 2 | Configuration Model & Validation | ✅ DONE | PluginConfiguration.java, ConfigurationValidator.java, ConfigurationValidatorTest.java |
| 3 | Export Mojo Implementation | ✅ DONE | ExportDependenciesMojo.java |
| 4 | Core Domain Model | ✅ DONE | MavenModule.java, Dependency.java, DependencyGraph.java, tests |
| 5 | Maven Resolver Integration | ✅ DONE | DependencyResolver.java, MavenDependencyResolver.java |
| 6 | Version Conflict Detection | ✅ DONE | Integrated in MavenDependencyResolver |
| 7 | Glob Pattern Matcher | ✅ DONE | PatternMatcher.java, PatternMatcherTest.java |
| 8 | Filter Engine Implementation | ✅ DONE | FilterEngine.java, FilterEngineTest.java |
| 9 | Database Exporter Interface | ✅ DONE | DatabaseExporter.java, ExportResult.java, SchemaVersion.java |
| 10 | Neo4j Exporter Implementation | ✅ DONE | Neo4jExporter.java |
| 11 | Oracle Exporter Implementation | ✅ DONE | OracleExporter.java |
| 12 | Connection Retry Logic | ✅ DONE | RetryExecutor.java, RetryExecutorTest.java |
| 13 | Version Cleanup with ComparableVersion | ✅ DONE | VersionCleanupService.java, VersionCleanupServiceTest.java |
| 14 | Error Handling Framework | ✅ DONE | GdmException.java, ConfigurationException.java, ResolutionException.java, ExportException.java |
| 15 | Logging Implementation | ✅ DONE | Integrated in ExportDependenciesMojo |
| 16 | Unit Tests for Core Components | ✅ DONE | Multiple test files created |
| 17 | Integration Tests | ✅ DONE | Neo4jExportIT.java, OracleExportIT.java |
| 18 | Maven Integration Tests | ⏳ TODO | - |
| 19 | Plugin Documentation | ✅ DONE | README.md |
| 20 | Performance Optimization & Final Testing | ⏳ TODO | - |

---

## Phase 1: Project Setup & Core Infrastructure

### Task 1: Maven Plugin Project Setup
**Priority:** Critical | **Estimate:** 2h

**Description:**
Set up the Maven plugin project structure with proper dependencies and build configuration.

**Acceptance Criteria:**
- [ ] Create Maven project with `maven-plugin` packaging
- [ ] Add dependencies: Maven Plugin API, Maven Plugin Annotations, Maven Resolver (Aether)
- [ ] Add dependencies: Neo4j Java Driver, Oracle JDBC (ojdbc), HikariCP
- [ ] Configure `maven-plugin-plugin` for goal generation
- [ ] Create base package structure: `org.example.gdm`
- [ ] Verify project builds with `mvn clean install`

**Files to create:**
- `pom.xml` (update existing)
- `src/main/java/org/example/gdm/` package structure

---

### Task 2: Configuration Model & Validation
**Priority:** Critical | **Estimate:** 3h

**Description:**
Implement the plugin configuration model with all parameters and validation logic.

**Acceptance Criteria:**
- [ ] Create `PluginConfiguration` class with all parameters from spec
- [ ] Implement `ConfigurationValidator` class
- [ ] Validate required fields: databaseType, connectionUrl, username, password
- [ ] Validate databaseType: must be `neo4j` or `oracle`
- [ ] Validate transitiveDepth: must be -1 or >= 0
- [ ] Validate exportScopes: each must be compile|runtime|test|provided|system
- [ ] Validate filter patterns: must match `groupId:artifactId` format
- [ ] Throw `MojoExecutionException` on validation failure (ignore failOnError)

**Files to create:**
- `src/main/java/org/example/gdm/config/PluginConfiguration.java`
- `src/main/java/org/example/gdm/config/ConfigurationValidator.java`
- `src/test/java/org/example/gdm/config/ConfigurationValidatorTest.java`

---

### Task 3: Export Mojo Implementation
**Priority:** Critical | **Estimate:** 4h

**Description:**
Implement the main Maven Mojo that orchestrates the export process.

**Acceptance Criteria:**
- [ ] Create `ExportDependenciesMojo` extending `AbstractMojo`
- [ ] Annotate with `@Mojo(name = "export", defaultPhase = LifecyclePhase.NONE)`
- [ ] Inject Maven project, session, and repository system
- [ ] Define all `@Parameter` fields matching configuration spec
- [ ] Implement `execute()` method with full workflow orchestration
- [ ] Handle password decryption (Maven encrypted passwords)
- [ ] Implement proper error handling based on `failOnError` flag
- [ ] Add INFO logging for start/end and statistics

**Files to create:**
- `src/main/java/org/example/gdm/ExportDependenciesMojo.java`

---

## Phase 2: Data Model

### Task 4: Core Domain Model
**Priority:** Critical | **Estimate:** 2h

**Description:**
Implement the core domain model classes for representing Maven modules and dependencies.

**Acceptance Criteria:**
- [ ] Create `MavenModule` class with: groupId, artifactId, version, packaging, exportTimestamp, isLatest
- [ ] Create `Dependency` class with: source, target, scope, optional, depth, isResolved, exportTimestamp
- [ ] Create `DependencyGraph` class with: modules (Set), dependencies (List), root module
- [ ] Implement `equals()`, `hashCode()`, `toString()` for all classes
- [ ] Create builder patterns for complex object construction

**Files to create:**
- `src/main/java/org/example/gdm/model/MavenModule.java`
- `src/main/java/org/example/gdm/model/Dependency.java`
- `src/main/java/org/example/gdm/model/DependencyGraph.java`
- `src/test/java/org/example/gdm/model/MavenModuleTest.java`

---

## Phase 3: Dependency Resolution

### Task 5: Maven Resolver Integration
**Priority:** Critical | **Estimate:** 6h

**Description:**
Implement dependency resolution using Maven Resolver (Aether) API.

**Acceptance Criteria:**
- [ ] Create `DependencyResolver` interface
- [ ] Implement `MavenDependencyResolver` using Eclipse Aether
- [ ] Create `DependencyResolutionRequest` with proper session setup
- [ ] Resolve full dependency tree from current Maven project
- [ ] Traverse `DependencyNode` tree with depth control (transitiveDepth)
- [ ] Collect all modules and edges (parent→child relationships)
- [ ] Respect depth semantics: depth=0 for direct deps, depth=N for transitive

**Files to create:**
- `src/main/java/org/example/gdm/resolver/DependencyResolver.java`
- `src/main/java/org/example/gdm/resolver/MavenDependencyResolver.java`
- `src/test/java/org/example/gdm/resolver/MavenDependencyResolverTest.java`

---

### Task 6: Version Conflict Detection
**Priority:** High | **Estimate:** 4h

**Description:**
Implement detection of version conflicts from Maven Resolver results.

**Acceptance Criteria:**
- [ ] Create `ConflictDetector` class
- [ ] Identify omitted nodes in dependency graph (conflict losers)
- [ ] Extract conflict reason from `DependencyNode` data
- [ ] Mark dependencies with `isResolved=false` for omitted versions
- [ ] Mark dependencies with `isResolved=true` for effective versions
- [ ] Export BOTH conflicting versions as modules
- [ ] Log conflicts at WARN level

**Files to create:**
- `src/main/java/org/example/gdm/resolver/ConflictDetector.java`
- `src/test/java/org/example/gdm/resolver/ConflictDetectorTest.java`

---

## Phase 4: Filter Engine

### Task 7: Glob Pattern Matcher
**Priority:** High | **Estimate:** 3h

**Description:**
Implement glob-style pattern matching for dependency filters.

**Acceptance Criteria:**
- [ ] Create `PatternMatcher` class
- [ ] Support `*` wildcard (zero or more characters)
- [ ] Support `?` wildcard (exactly one character)
- [ ] Parse pattern format: `groupId:artifactId`
- [ ] Match against `MavenModule` objects
- [ ] NOT regex - only glob patterns
- [ ] Handle edge cases: empty pattern, pattern without wildcard

**Test cases:**
- `org.springframework:*` matches `org.springframework:spring-core`
- `*:junit` matches `junit:junit` and `org.junit:junit`
- `com.company:my-?-app` matches `com.company:my-1-app` but NOT `my-10-app`

**Files to create:**
- `src/main/java/org/example/gdm/filter/PatternMatcher.java`
- `src/test/java/org/example/gdm/filter/PatternMatcherTest.java`

---

### Task 8: Filter Engine Implementation
**Priority:** High | **Estimate:** 3h

**Description:**
Implement the complete filter engine with include/exclude and scope filtering.

**Acceptance Criteria:**
- [ ] Create `FilterEngine` class
- [ ] Create `DependencyFilter` for include/exclude patterns
- [ ] Create `ScopeFilter` for scope filtering
- [ ] Apply filter logic: exclude first, then include
- [ ] Filters work on OUTPUT only (not on traversal)
- [ ] Root module is NEVER filtered
- [ ] Log filtered-out count at WARN level
- [ ] Return filtered `DependencyGraph`

**Filter Logic:**
1. Check exclude filters → reject if match
2. If include filters exist → must match at least one
3. Check scope filters → reject if not in exportScopes

**Files to create:**
- `src/main/java/org/example/gdm/filter/FilterEngine.java`
- `src/main/java/org/example/gdm/filter/DependencyFilter.java`
- `src/main/java/org/example/gdm/filter/ScopeFilter.java`
- `src/test/java/org/example/gdm/filter/FilterEngineTest.java`

---

## Phase 5: Database Layer

### Task 9: Database Exporter Interface
**Priority:** Critical | **Estimate:** 2h

**Description:**
Define the database exporter abstraction layer.

**Acceptance Criteria:**
- [ ] Create `DatabaseExporter` interface with methods:
  - `connect()` - establish connection
  - `checkSchemaVersion()` - verify/update schema version
  - `exportGraph(DependencyGraph)` - main export method
  - `cleanup(String groupId, String artifactId)` - version cleanup
  - `close()` - close connection
- [ ] Create `ExportResult` class with statistics
- [ ] Create `DatabaseException` for database errors
- [ ] Define `SchemaVersion` value object

**Files to create:**
- `src/main/java/org/example/gdm/export/DatabaseExporter.java`
- `src/main/java/org/example/gdm/export/ExportResult.java`
- `src/main/java/org/example/gdm/export/DatabaseException.java`
- `src/main/java/org/example/gdm/export/SchemaVersion.java`

---

### Task 10: Neo4j Exporter Implementation
**Priority:** Critical | **Estimate:** 6h

**Description:**
Implement the Neo4j database exporter using official Neo4j Java Driver.

**Acceptance Criteria:**
- [ ] Create `Neo4jExporter` implementing `DatabaseExporter`
- [ ] Implement connection with Neo4j Driver
- [ ] Implement schema version check (MERGE SchemaVersion node)
- [ ] Implement module MERGE (upsert)
- [ ] Implement dependency DELETE (old deps) + CREATE (new deps)
- [ ] Use single transaction for entire export
- [ ] Implement batch processing (500 records per batch)
- [ ] Use camelCase for properties (groupId, artifactId, isResolved)

**Cypher Operations:**
- MERGE for modules
- DELETE relationships where source = current module
- CREATE for new DEPENDS_ON relationships

**Files to create:**
- `src/main/java/org/example/gdm/export/neo4j/Neo4jExporter.java`
- `src/main/java/org/example/gdm/export/neo4j/Neo4jConnectionManager.java`
- `src/test/java/org/example/gdm/export/neo4j/Neo4jExporterTest.java`

---

### Task 11: Oracle Exporter Implementation
**Priority:** Critical | **Estimate:** 6h

**Description:**
Implement the Oracle database exporter using JDBC.

**Acceptance Criteria:**
- [ ] Create `OracleExporter` implementing `DatabaseExporter`
- [ ] Implement connection with HikariCP connection pool
- [ ] Implement schema version check (SELECT/INSERT gdm_schema_version)
- [ ] Implement module MERGE (Oracle MERGE statement)
- [ ] Implement dependency DELETE + batch INSERT
- [ ] Use single transaction (setAutoCommit=false)
- [ ] Implement batch processing with PreparedStatement.addBatch()
- [ ] Use snake_case for columns (group_id, artifact_id, is_resolved)

**SQL Operations:**
- MERGE INTO maven_modules
- DELETE FROM maven_dependencies WHERE source_module_id = ?
- INSERT INTO maven_dependencies (batch)

**Files to create:**
- `src/main/java/org/example/gdm/export/oracle/OracleExporter.java`
- `src/main/java/org/example/gdm/export/oracle/OracleConnectionManager.java`
- `src/test/java/org/example/gdm/export/oracle/OracleExporterTest.java`

---

### Task 12: Connection Retry Logic
**Priority:** High | **Estimate:** 3h

**Description:**
Implement retry logic for database connection errors.

**Acceptance Criteria:**
- [ ] Create `RetryExecutor` utility class
- [ ] Implement retry: 3 attempts, 2 seconds backoff (fixed)
- [ ] Retry ONLY for connection errors:
  - `SocketTimeoutException`
  - `ConnectException`
  - Network-related `IOException`
- [ ] NO retry for:
  - SQL syntax errors
  - Constraint violations
  - Authentication failures
- [ ] Log each retry attempt at ERROR level
- [ ] After exhausted retries: respect `failOnError` flag

**Files to create:**
- `src/main/java/org/example/gdm/export/RetryExecutor.java`
- `src/main/java/org/example/gdm/export/RetryableException.java`
- `src/test/java/org/example/gdm/export/RetryExecutorTest.java`

---

## Phase 6: Version Management

### Task 13: Version Cleanup with ComparableVersion
**Priority:** High | **Estimate:** 4h

**Description:**
Implement version cleanup using Maven's ComparableVersion for correct sorting.

**Acceptance Criteria:**
- [ ] Create `VersionCleanupService` class
- [ ] Fetch all versions for (groupId, artifactId) from database
- [ ] Sort versions using `org.apache.maven.artifact.versioning.ComparableVersion`
- [ ] Keep only highest version (first after sort)
- [ ] Mark others as `is_latest=0`
- [ ] DELETE non-latest versions (CASCADE deletes dependencies)
- [ ] Execute in SEPARATE transaction (after main export)
- [ ] Log deleted versions at INFO level

**Why Java sorting:** Oracle `ORDER BY version DESC` doesn't handle Maven versions correctly (1.10 vs 1.9, snapshots, qualifiers).

**Files to create:**
- `src/main/java/org/example/gdm/version/VersionCleanupService.java`
- `src/test/java/org/example/gdm/version/VersionCleanupServiceTest.java`

---

## Phase 7: Error Handling & Logging

### Task 14: Error Handling Framework
**Priority:** High | **Estimate:** 3h

**Description:**
Implement comprehensive error handling based on failOnError flag.

**Acceptance Criteria:**
- [ ] Create `GdmException` base exception class
- [ ] Create specific exceptions: `ConfigurationException`, `ResolutionException`, `ExportException`
- [ ] Implement error handling in Mojo based on `failOnError`:
  - `failOnError=false` → log ERROR, continue build, return success
  - `failOnError=true` → throw `MojoExecutionException`, fail build
- [ ] Configuration validation errors ALWAYS fail (ignore failOnError)
- [ ] Transaction errors trigger full ROLLBACK
- [ ] Log detailed error messages with actionable suggestions

**Files to create:**
- `src/main/java/org/example/gdm/exception/GdmException.java`
- `src/main/java/org/example/gdm/exception/ConfigurationException.java`
- `src/main/java/org/example/gdm/exception/ResolutionException.java`
- `src/main/java/org/example/gdm/exception/ExportException.java`

---

### Task 15: Logging Implementation
**Priority:** Medium | **Estimate:** 2h

**Description:**
Implement structured logging according to specification.

**Acceptance Criteria:**
- [ ] Use Maven's built-in Log (`getLog()` from AbstractMojo)
- [ ] INFO: Start/end export, configuration summary, statistics
- [ ] DEBUG: Each dependency processed, filter evaluation, DB operations
- [ ] WARN: Filtered-out count, version conflicts, schema mismatch
- [ ] ERROR: Connection failures, database errors, configuration errors
- [ ] NEVER log passwords (even at DEBUG)
- [ ] Format statistics in readable blocks with separators

**Log Format Example:**
```
[INFO] ============================================================
[INFO] GDM Maven Plugin - Dependency Export
[INFO] ============================================================
```

**Files to modify:**
- `ExportDependenciesMojo.java` - add logging throughout

---

## Phase 8: Testing

### Task 16: Unit Tests for Core Components
**Priority:** High | **Estimate:** 6h

**Description:**
Implement comprehensive unit tests for all core components.

**Test Coverage:**
- [ ] `PatternMatcherTest` - glob pattern matching
- [ ] `FilterEngineTest` - include/exclude/scope filtering
- [ ] `ConfigurationValidatorTest` - validation rules
- [ ] `MavenModuleTest` - equals, hashCode
- [ ] `ConflictDetectorTest` - conflict detection
- [ ] `RetryExecutorTest` - retry logic and error classification
- [ ] `VersionCleanupServiceTest` - ComparableVersion sorting

**Acceptance Criteria:**
- [ ] Use JUnit 5
- [ ] Use Mockito for mocking
- [ ] Minimum 80% code coverage for core classes
- [ ] Test edge cases and error scenarios

---

### Task 17: Integration Tests
**Priority:** High | **Estimate:** 8h

**Description:**
Implement integration tests with real databases.

**Acceptance Criteria:**
- [ ] Use Testcontainers for Neo4j and Oracle
- [ ] Test full export workflow end-to-end
- [ ] Test overwrite semantics (same GAV twice)
- [ ] Test version conflict export (both versions in DB)
- [ ] Test cleanup (keepOnlyLatestVersion=true)
- [ ] Test filter integration (verify filtered deps not in DB)
- [ ] Test transaction rollback on error
- [ ] Test retry logic with simulated connection failures

**Files to create:**
- `src/test/java/org/example/gdm/integration/Neo4jExportIT.java`
- `src/test/java/org/example/gdm/integration/OracleExportIT.java`
- `src/test/java/org/example/gdm/integration/FullWorkflowIT.java`

---

### Task 18: Maven Integration Tests
**Priority:** Medium | **Estimate:** 4h

**Description:**
Implement Maven integration tests using maven-invoker-plugin.

**Acceptance Criteria:**
- [ ] Create sample Maven projects in `src/it/`
- [ ] Test single module project export
- [ ] Test multi-module project export
- [ ] Test with various configurations
- [ ] Verify database state after export
- [ ] Configure maven-invoker-plugin in pom.xml

**Files to create:**
- `src/it/simple-project/pom.xml`
- `src/it/multi-module/pom.xml`
- `src/it/simple-project/verify.groovy`

---

## Phase 9: Documentation & Finalization

### Task 19: Plugin Documentation
**Priority:** Medium | **Estimate:** 3h

**Description:**
Create user-facing documentation and examples.

**Acceptance Criteria:**
- [ ] Create `README.md` with quick start guide
- [ ] Document all configuration parameters
- [ ] Provide example configurations (minimal, full)
- [ ] Document filter pattern syntax with examples
- [ ] Document error messages and troubleshooting
- [ ] Add sample queries for Neo4j and Oracle
- [ ] Generate plugin documentation with maven-plugin-plugin

**Files to create:**
- `README.md`
- `src/site/apt/usage.apt` (Maven site docs)
- `src/site/apt/examples.apt`

---

### Task 20: Performance Optimization & Final Testing
**Priority:** Medium | **Estimate:** 4h

**Description:**
Optimize performance and validate against performance targets.

**Acceptance Criteria:**
- [ ] Benchmark with various project sizes (50, 100, 500, 1000+ deps)
- [ ] Validate performance targets:
  - < 50 deps: < 2s
  - 50-100 deps: < 5s
  - 100-500 deps: < 15s
  - 500-1000 deps: < 30s
- [ ] Optimize batch processing if needed
- [ ] Optimize database queries (use indexes)
- [ ] Memory profiling for large projects
- [ ] Final end-to-end testing
- [ ] Update documentation with performance notes

**Files to modify:**
- Various exporter classes for optimization
- `README.md` with performance notes

---

## Task Dependencies

```
Task 1 (Setup)
    ↓
Task 2 (Config) → Task 3 (Mojo)
    ↓
Task 4 (Model)
    ↓
Task 5 (Resolver) → Task 6 (Conflicts)
    ↓
Task 7 (Pattern) → Task 8 (Filter)
    ↓
Task 9 (Interface)
    ↓
Task 10 (Neo4j) ←→ Task 11 (Oracle)
    ↓
Task 12 (Retry) → Task 13 (Cleanup)
    ↓
Task 14 (Errors) → Task 15 (Logging)
    ↓
Task 16 (Unit Tests) → Task 17 (Integration) → Task 18 (Maven IT)
    ↓
Task 19 (Docs) → Task 20 (Performance)
```

---

## Summary

| Phase | Tasks | Estimated Hours |
|-------|-------|-----------------|
| 1. Setup & Infrastructure | 1-3 | 9h |
| 2. Data Model | 4 | 2h |
| 3. Dependency Resolution | 5-6 | 10h |
| 4. Filter Engine | 7-8 | 6h |
| 5. Database Layer | 9-12 | 17h |
| 6. Version Management | 13 | 4h |
| 7. Error Handling & Logging | 14-15 | 5h |
| 8. Testing | 16-18 | 18h |
| 9. Documentation & Final | 19-20 | 7h |
| **Total** | **20** | **~78h** |

---

**End of Implementation Tasks**

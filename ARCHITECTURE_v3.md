# GDM Maven Plugin - Architecture Design Document

**Version:** 3.0.0  
**Date:** 2026-02-09  
**Status:** Implementation Ready

---

## 1. Executive Summary

GDM (Graph Dependency Maven) Plugin is a Maven plugin for exporting Maven project dependency graphs to databases (Neo4j, Oracle). The plugin provides:

- Export of dependency graph with full control over transitive dependencies
- Dependency filtering based on groupId and artifactId
- Version management with overwrite or versioning policy
- Automatic cleanup of old versions
- Tracking of version conflicts
- Full integration with Maven Resolver (Aether) API

---

## 2. Key Design Decisions

### 2.1 Database Configuration

- **URL and credentials** placed in `pom.xml`
- **Database schema** must be created beforehand (outside the plugin)
- **Schema available** in separate documentation file (`database-schema.sql`)
- Plugin does NOT automatically create tables, but **updates version metadata** (SchemaVersion)

### 2.2 Module Identification

- Identification by: **`groupId:artifactId:version`** (GAV)
- Different versions of the same module are separate entries in the database
- Multi-module projects: each module exports its dependencies independently

### 2.3 Multiple Builds

- Same version (GAV) built multiple times → **OVERWRITE** (overwrite)
- Latest build always has the newest data
- **Overwrite algorithm:**
  1. UPSERT module (update timestamp)
  2. DELETE all dependencies where `source_module_id = current module`
  3. INSERT new dependencies

### 2.4 Transitive Dependencies - Depth

```
Project A (root)
├── B:1.0 (depth=0) ← direct dependency, edge: A→B
│   ├── D:1.0 (depth=1) ← transitive, edge: B→D
│   │   └── F:1.0 (depth=2) ← transitive, edge: D→F
│   └── E:1.0 (depth=1) ← transitive, edge: B→E
└── C:1.0 (depth=0) ← direct dependency, edge: A→C
```

**Semantics:**
- **Depth**: counted from root module (exported project)
- **Edges**: we export **parent→child** relationships exactly as in Maven tree (Tree Edges)
- **Example**: for depth=1 we export: A→B, A→C, B→D, B→E (but not D→F)

**Configuration:**
- **Depth 0**: only direct dependencies (edges: A→B, A→C)
- **Depth 1**: direct + their dependencies (edges: A→B, A→C, B→D, B→E)
- **Depth N**: N levels of transitive
- **Depth -1**: unlimited (entire graph)

### 2.5 Dependency Filters

**Pattern Format (glob-style):**
```
groupId:artifactId
```

**Wildcards:**
- `*` - any string (zero or more characters)
- `?` - exactly one character
- **NOT regex**, only glob pattern

**Examples:**
```
org.springframework:*              ← all artifacts from org.springframework group
org.springframework:spring-core    ← specific artifact
com.company:*                      ← all from com.company group
*:spring-*                         ← pattern on artifactId (any group)
*:junit                            ← specific artifactId from any group
```

**Filter Application Logic:**
1. Collect **entire dependency graph** according to transitiveDepth (filters don't affect traversal)
2. For each dependency in graph:
   - If matches **exclude** → reject from export
   - If **include filters** exist → dependency must match at least one
   - Otherwise → include
3. Export only filtered dependencies

**Note:** Filters work only on **output** (export result), not on traversal. This ensures conflicts are detected correctly even for filtered modules.

### 2.6 Version Conflicts

**Conflict Definition:**
- Conflict = same artifact (GA) appears in graph with different versions
- Maven Resolver (dependency mediation) chooses one version → **resolved**
- Other versions are **requested but omitted**

**Information Source:**
- We use **Maven Resolver API** (`DependencyResolutionResult`)
- Resolver provides effective graph + information about omitted nodes

**What we export:**
```
Project A
├── B:1.0 → C:2.0  (requested, omitted for conflict)
└── D:1.0 → C:3.0  (resolved, actually used)
```

**Exported relationships:**
- A→B:1.0 (depth=0, is_resolved=true)
- A→D:1.0 (depth=0, is_resolved=true)
- B:1.0→C:2.0 (depth=1, **is_resolved=false**) ← CONFLICT
- D:1.0→C:3.0 (depth=1, **is_resolved=true**) ← RESOLVED

**In database:**
- Both modules C:2.0 and C:3.0 are saved
- Relationships marked with `is_resolved` flag (true/false)
- Queries can filter by `is_resolved=true` to see only "actual" dependencies

### 2.7 Version Management

- **Cleanup granularity**: per module (groupId:artifactId)
- **Determining "latest"**: by version number (**Maven ComparableVersion**)
- **Cleanup executed in Java**: fetch versions, sort with ComparableVersion, delete old ones
- **Action on deletion**: full deletion (CASCADE delete)
- **No archiving**: we don't keep historical data

**Note:** Oracle `ORDER BY version DESC` **does NOT work correctly** for Maven versions (1.10 vs 1.9, snapshots, qualifiers). Therefore sorting must be done in Java.

### 2.8 Scope Dependencies

**By default we export all scopes:**
- compile
- runtime
- test
- provided
- system

**User can select only specific scopes** through configuration.

**We skip:**
- `import` (only in dependencyManagement, not part of dependency graph)

**Optional dependencies:**
- Exported with `optional=true` flag
- Optional dependencies are **included** in traversal (Maven Resolver considers them)

### 2.9 Parent POM

- Exported as normal module (packaging=pom)
- Marked in database as `packaging='pom'`
- Its dependencies are treated normally
- Parent POM relationships (child→parent) are **NOT exported** as dependencies

### 2.10 Inter-Module Dependencies

In multi-module projects:
- Each module exports its dependencies independently
- If module-A depends on module-B from the same project → export as normal dependency
- No special marking for internal dependencies

### 2.11 Execution

- **No automatic binding** to Maven lifecycle phase
- **Manual invocation**: `mvn gdm:export`
- **All parameters** through `pom.xml`
- No `mvn gdm:test-connection`

### 2.12 Export Metadata

We export only:
- **GAV**: groupId, artifactId, version
- **Timestamp**: export date/time
- **Packaging**: type (jar, war, pom, etc.)
- **isLatest**: Boolean flag (for cleanup)

**We DON'T export:**
- Build user, host, duration
- Git commit/branch
- Maven/Java version

### 2.13 Password Management

- **By default**: plain text in pom.xml
- **Optionally**: Maven password encryption (settings.xml + settings-security.xml)
- Plugin automatically decrypts if user applied encryption
- **We DON'T support**: environment variables (maybe in future)

### 2.14 Supported Databases

- **MVP**: Neo4j (default)
- **MVP**: Oracle
- **Not included**: MongoDB, PostgreSQL, MySQL

### 2.15 Batch Processing

- **Batch size**: 500 records per insert
- **Unit**: 500 dependency records
- **Transaction**: one transaction per entire export (all-or-nothing)
- **Rollback**: complete rollback if any batch fails

### 2.16 Error Handling

- **failOnError=false** (default): only warning in log, build continues
- **Retry logic**: 3 attempts on connection errors with 2s backoff
- **Scope**: retry only for connection issues, not for query errors
- **Retry error classification:**
  - Connection timeout → RETRY
  - Connection refused → RETRY
  - Network errors → RETRY
  - SQL errors → NO RETRY
  - Constraint violations → NO RETRY
  - Authentication failures → NO RETRY

### 2.17 Logging

```
INFO:  - Start export
       - End export + statistics
       - Number of modules, dependencies processed
       - Schema version check result
       
DEBUG: - Each dependency being processed
       - Filter evaluation (matched/rejected)
       - Database operations (INSERT/UPDATE/DELETE)
       - Batch processing progress
       
WARN:  - Dependencies filtered out (summary)
       - Version conflicts detected
       - Older versions being deleted
       - Schema version mismatch (but continuing)
       
ERROR: - Connection failures
       - Database errors
       - Configuration errors
       - Validation failures
```

### 2.18 Schema Versioning

**Neo4j:**
```cypher
MERGE (v:SchemaVersion {id: 'current'})
SET v.version = '1.0.0', v.appliedAt = datetime()
```

**Oracle:**
```sql
CREATE TABLE gdm_schema_version (
    version VARCHAR2(20) PRIMARY KEY,
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
-- Plugin executes MERGE (INSERT or UPDATE)
```

**Workflow:**
1. Plugin checks if SchemaVersion node/table exists
2. If doesn't exist → **creates it** (this is not creating "tables", just metadata)
3. If exists → checks version compatibility
4. If mismatch → WARN (and continue or fail depending on failOnError)

---

## 3. High-Level Architecture

```
┌─────────────────────────────────┐
│  Maven Build (mvn gdm:export)   │
└────────────┬────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────┐
│           ExportDependenciesMojo                    │
│  - Parse configuration from pom.xml                 │
│  - Validate configuration                           │
│  - Setup database connection                        │
└────────────┬────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────┐
│       DependencyResolver                            │
│  - Maven Resolver API (Aether)                      │
│  - Resolve dependency tree (with depth control)     │
│  - Collect conflict information                     │
│  - Build DependencyGraph                            │
└────────────┬────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────┐
│       FilterEngine                                  │
│  - Apply include/exclude filters (on output)        │
│  - Filter by scope                                  │
│  - Pattern matching (glob)                          │
└────────────┬────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────┐
│       DependencyGraph (filtered)                    │
│  - MavenModule nodes                                │
│  - Dependency relationships (parent→child)          │
│  - Conflict information (is_resolved flags)         │
└────────────┬────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────┐
│       DatabaseExporter (Interface)                  │
│  - Neo4jExporter                                    │
│  - OracleExporter                                   │
└────────────┬────────────────────────────────────────┘
             │
     ┌───────┴───────┐
     ▼               ▼
 ┌────────┐     ┌────────┐
 │ Neo4j  │     │ Oracle │
 │Database│     │Database│
 └────────┘     └────────┘
```

---

## 4. System Components

### 4.1 Plugin Execution Layer

**Classes:**
- `ExportDependenciesMojo` - main goal (Maven Mojo)
- `PluginConfiguration` - configuration from pom.xml
- `ConfigurationValidator` - parameter validation

**Responsibilities:**
- Parse configuration from pom.xml
- Validate parameters (required fields, valid values)
- Manage export lifecycle
- Error handling and logging
- Retry logic for connection errors

### 4.2 Dependency Resolution Engine

**Classes:**
- `DependencyResolver` - interface
- `MavenDependencyResolver` - implementation with Maven Resolver API (Aether)
- `DependencyTreeBuilder` - tree building with depth control
- `ConflictDetector` - version conflict detection

**Functionality:**
- Resolve dependency tree using `DependencyResolutionResult`
- Recursive resolve transitive with depth control
- Version conflict detection (omitted nodes)
- Collect `is_resolved` information per dependency

**Maven Resolver API Contract:**
```java
// We use:
DependencyResolutionResult result = resolver.resolveDependencies(session, request);
DependencyNode rootNode = result.getDependencyGraph();

// rootNode contains:
// - effective dependencies (is_resolved=true)
// - omitted dependencies (is_resolved=false) with "omitted for conflict" info
```

### 4.3 Filter Engine

**Classes:**
- `FilterEngine` - orchestration
- `PatternMatcher` - glob pattern matching (*, ?)
- `DependencyFilter` - include/exclude filtering
- `ScopeFilter` - scope filtering

**Functionality:**
- **Glob pattern matching** (not regex): `*` and `?`
- Include/exclude filtering **on output** (not on traversal)
- Scope filtering
- Log filtered-out dependencies (WARN level)

**Pattern Matching DSL:**
```
org.springframework:*        → matches org.springframework:spring-core, org.springframework:spring-web
com.company:my-?-app         → matches com.company:my-1-app, com.company:my-2-app
*:junit                      → matches any-group:junit
```

### 4.4 Data Model

**Classes:**
- `MavenModule` - module/artifact (GAV + metadata)
- `Dependency` - relationship between modules (edge in graph)
- `DependencyGraph` - complete graph (nodes + edges)
- `ExportMetadata` - export metadata

**MavenModule:**
```java
class MavenModule {
    String groupId;
    String artifactId;
    String version;
    String packaging;
    Instant exportTimestamp;
    boolean isLatest;
}
```

**Dependency:**
```java
class Dependency {
    MavenModule source;
    MavenModule target;
    String scope;
    boolean optional;
    int depth;              // from root
    boolean isResolved;     // true=effective, false=conflict/omitted
    Instant exportTimestamp;
}
```

### 4.5 Database Exporters

**Interface:**
- `DatabaseExporter` - abstraction

**Implementations:**
- `Neo4jExporter` - export to Neo4j (Cypher)
- `OracleExporter` - export to Oracle (JDBC)

**Common Functionalities:**
- Connection management (retry logic)
- Schema version check & update
- Transaction handling (all-or-nothing)
- Batch processing (500 records per batch)
- UPSERT modules
- DELETE old dependencies (overwrite)
- INSERT new dependencies
- Cleanup old versions (ComparableVersion sorting in Java)

---

## 5. Neo4j Schema

### Nodes

```cypher
(:MavenModule {
    groupId: String,
    artifactId: String,
    version: String,
    packaging: String,           // jar, war, pom, etc.
    exportTimestamp: DateTime,
    isLatest: Boolean
})

(:SchemaVersion {
    id: String,                 // 'current'
    version: String,            // '1.0.0'
    appliedAt: DateTime
})
```

### Relationships

```cypher
(source:MavenModule)-[:DEPENDS_ON {
    scope: String,              // compile, runtime, test, provided, system
    optional: Boolean,
    depth: Integer,             // 0 = direct, 1+ = transitive (from root)
    isResolved: Boolean,        // true = effective, false = conflict/omitted
    exportTimestamp: DateTime
}]->(target:MavenModule)
```

**Note:** Relationships represent **parent→child edges** in Maven tree, not "root→all".

### Constraints & Indexes

```cypher
CREATE CONSTRAINT module_unique IF NOT EXISTS
FOR (m:MavenModule)
REQUIRE (m.groupId, m.artifactId, m.version) IS UNIQUE;

CREATE INDEX module_ga IF NOT EXISTS
FOR (m:MavenModule) ON (m.groupId, m.artifactId);

CREATE INDEX module_latest IF NOT EXISTS
FOR (m:MavenModule) ON (m.isLatest);

CREATE INDEX dep_depth IF NOT EXISTS
FOR ()-[r:DEPENDS_ON]-() ON (r.depth);

CREATE INDEX dep_resolved IF NOT EXISTS
FOR ()-[r:DEPENDS_ON]-() ON (r.isResolved);
```

---

## 6. Oracle Schema

```sql
CREATE TABLE maven_modules (
    id                  NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id            VARCHAR2(255) NOT NULL,
    artifact_id         VARCHAR2(255) NOT NULL,
    version             VARCHAR2(100) NOT NULL,
    packaging           VARCHAR2(50) DEFAULT 'jar',
    export_timestamp    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_latest           NUMBER(1) DEFAULT 1,
    CONSTRAINT uk_maven_module UNIQUE (group_id, artifact_id, version)
);

CREATE TABLE maven_dependencies (
    id                  NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_module_id    NUMBER NOT NULL,
    target_module_id    NUMBER NOT NULL,
    scope               VARCHAR2(20) DEFAULT 'compile',
    optional            NUMBER(1) DEFAULT 0,
    depth               NUMBER(2) DEFAULT 0,
    is_resolved         NUMBER(1) DEFAULT 1,
    export_timestamp    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dep_source 
        FOREIGN KEY (source_module_id) 
        REFERENCES maven_modules(id) ON DELETE CASCADE,
    CONSTRAINT fk_dep_target 
        FOREIGN KEY (target_module_id) 
        REFERENCES maven_modules(id) ON DELETE CASCADE,
    CONSTRAINT uk_dependency 
        UNIQUE (source_module_id, target_module_id, scope, depth)
);

CREATE TABLE gdm_schema_version (
    version             VARCHAR2(20) PRIMARY KEY,
    applied_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_module_ga ON maven_modules(group_id, artifact_id);
CREATE INDEX idx_module_latest ON maven_modules(is_latest);
CREATE INDEX idx_dep_source ON maven_dependencies(source_module_id);
CREATE INDEX idx_dep_target ON maven_dependencies(target_module_id);
CREATE INDEX idx_dep_resolved ON maven_dependencies(is_resolved);
CREATE INDEX idx_dep_depth ON maven_dependencies(depth);
CREATE INDEX idx_dep_scope ON maven_dependencies(scope);
```

**Change in UNIQUE CONSTRAINT:**
- Old: `UNIQUE (source_module_id, target_module_id, scope, is_resolved)` ← lost information
- **New**: `UNIQUE (source_module_id, target_module_id, scope, depth)` ← preserves full structure

**Rationale:** This change allows exporting the same dependency at different depths (different paths in graph).

---

## 7. Graph Export Shape & Depth Semantics

### Definition

**Graph Shape:** Tree Edges (we export parent→child relationships exactly as in Maven tree)

**Example:**
```
Project A (root)
├── B:1.0 (depth=0)
│   ├── D:1.0 (depth=1)
│   │   └── F:1.0 (depth=2)
│   └── E:1.0 (depth=1)
└── C:1.0 (depth=0)
```

**Exported edges:**
- A→B (depth=0, counted from root A)
- A→C (depth=0)
- B→D (depth=1, counted from root A)
- B→E (depth=1)
- D→F (depth=2)

**Nodes:** A, B, C, D, E, F

**Depth semantics:**
- Depth counted from **root module** (exported project)
- Depth on **edge** indicates "at what depth from root is the target node"
- Traversal: if transitiveDepth=1, we traverse through dependencies to depth=1, but **export all edges** on that path

### Transitive Depth Control

**transitiveDepth=0** (only direct):
- Edges: A→B, A→C
- Nodes: A, B, C

**transitiveDepth=1**:
- Edges: A→B, A→C, B→D, B→E
- Nodes: A, B, C, D, E

**transitiveDepth=2**:
- Edges: A→B, A→C, B→D, B→E, D→F
- Nodes: A, B, C, D, E, F

**transitiveDepth=-1** (unlimited):
- All edges in entire graph

---

## 8. Resolver Contract (Maven Resolver / Aether)

### API

Plugin uses **Maven Resolver API** (org.eclipse.aether):

```java
// Main workflow:
DependencyResolutionRequest request = new DependencyResolutionRequest()
    .setMavenProject(project)
    .setRepositorySession(session);

DependencyResolutionResult result = resolver.resolveDependencies(session, request);
DependencyNode rootNode = result.getDependencyGraph();
```

### DependencyNode

Each node in graph contains:
- `Dependency dependency` - GAV, scope, optional information
- `List<DependencyNode> children` - direct dependencies
- **Conflict information** - if node is omitted, has reason information

### Conflicts & Resolved

**How we detect conflicts:**
1. Resolver returns **effective graph** (with chosen version)
2. Resolver marks **omitted nodes** (rejected by mediation)
3. Check for each node: `node.getDependency().isOptional()` + conflict data analysis

**Mapping to is_resolved:**
- Node/edge in effective graph → `is_resolved=true`
- Node omitted for conflict → `is_resolved=false`

### Exclusions

- Exclusions are already processed by Resolver
- Excluded dependencies **will not appear** in graph
- Plugin doesn't need to handle them separately

### Scope

- Resolver respects dependency scope
- Plugin can additionally filter by scope (through `exportScopes` config)

---

## 9. Export Flow (detailed)

```
1. Maven executes: mvn gdm:export
                    │
2. ExportDependenciesMojo starts
   - Load configuration from pom.xml
   - Validate configuration (required fields, valid values)
   - Decrypt password if encrypted
                    │
3. DependencyResolver resolves dependencies
   - Get Maven project
   - Create DependencyResolutionRequest
   - Resolve dependency graph (Maven Resolver API)
   - Traverse tree with depth control (up to transitiveDepth)
   - Collect nodes & edges
   - Detect conflicts (omitted nodes)
   - Build DependencyGraph (with is_resolved flags)
                    │
4. FilterEngine applies filters
   - For each dependency in graph:
     - Check exclude patterns → reject if match
     - Check include patterns → reject if no match
     - Check scope filters → reject if not in exportScopes
   - Build filtered DependencyGraph
   - Log filtered-out dependencies (WARN)
                    │
5. Connect to database
   - Retry 3 times on connection error (2s backoff)
   - Check schema version compatibility
   - Update/create SchemaVersion metadata
                    │
6. Start transaction
                    │
7. Process modules (batch 500)
   - For each module in graph:
     - UPSERT module (GAV)
     - If exists: UPDATE exportTimestamp
     - DELETE old dependencies where source_module_id = this module
     - Mark as is_latest=true
                    │
8. Process dependencies (batch 500)
   - For each dependency in graph:
     - Get source and target module IDs
     - INSERT dependency with (scope, depth, optional, is_resolved)
   - Commit batch every 500 records
                    │
9. Commit transaction
                    │
10. Cleanup (if keepOnlyLatestVersion=true)
    - For each unique (groupId, artifactId):
      - Fetch all versions from DB
      - Sort in Java using ComparableVersion
      - Keep highest version
      - Mark others as is_latest=0
      - DELETE non-latest (CASCADE)
                    │
11. Log statistics and finish
    - Modules exported: X
    - Dependencies exported: Y
    - Conflicts detected: Z
    - Old versions deleted: W
```

---

## 10. Version Conflict Handling (detailed)

### Scenario

```
Project A
├── B:1.0 ──> C:2.0  (requested)
└── D:1.0 ──> C:3.0  (requested)

Maven Resolver chooses C:3.0 (dependency mediation: nearest wins)
```

### What Maven Resolver Returns

**Effective graph:**
- A→B:1.0
- A→D:1.0
- B→ (omitted node C:2.0, reason: "omitted for conflict with C:3.0")
- D→C:3.0

### What We Export

**Modules:**
- A:version (root)
- B:1.0
- C:2.0 (node exists, but edge to it has is_resolved=false)
- C:3.0 (node exists, edge has is_resolved=true)
- D:1.0

**Dependencies (edges):**
```
A → B:1.0          (depth=0, is_resolved=true)
A → D:1.0          (depth=0, is_resolved=true)
B:1.0 → C:2.0      (depth=1, is_resolved=false)  ← CONFLICT (omitted)
D:1.0 → C:3.0      (depth=1, is_resolved=true)   ← RESOLVED (effective)
```

### SQL Representation

```sql
-- Module C:2.0 (omitted version)
INSERT INTO maven_modules (group_id, artifact_id, version, packaging, is_latest)
VALUES ('com.example', 'C', '2.0', 'jar', 1);

-- Module C:3.0 (resolved version)
INSERT INTO maven_modules (group_id, artifact_id, version, packaging, is_latest)
VALUES ('com.example', 'C', '3.0', 'jar', 1);

-- Conflicted dependency (requested but not used)
INSERT INTO maven_dependencies 
(source_module_id, target_module_id, scope, depth, is_resolved)
VALUES (
    (SELECT id FROM maven_modules WHERE group_id='com.example' AND artifact_id='B' AND version='1.0'),
    (SELECT id FROM maven_modules WHERE group_id='com.example' AND artifact_id='C' AND version='2.0'),
    'compile',
    1,
    0  -- NOT RESOLVED (omitted)
);

-- Resolved dependency (actually used)
INSERT INTO maven_dependencies 
(source_module_id, target_module_id, scope, depth, is_resolved)
VALUES (
    (SELECT id FROM maven_modules WHERE group_id='com.example' AND artifact_id='D' AND version='1.0'),
    (SELECT id FROM maven_modules WHERE group_id='com.example' AND artifact_id='C' AND version='3.0'),
    'compile',
    1,
    1  -- RESOLVED (effective)
);
```

### Queries

**Show only effective dependencies:**
```sql
SELECT * FROM maven_dependencies WHERE is_resolved = 1;
```

**Show conflicts:**
```sql
SELECT * FROM maven_dependencies WHERE is_resolved = 0;
```

---

## 11. Cleanup Strategy (ComparableVersion in Java)

### When

```xml
<keepOnlyLatestVersion>true</keepOnlyLatestVersion>
```

### Algorithm (executed in Java)

```
For each unique (groupId, artifactId):
  1. Fetch all versions from database
  2. Parse versions using org.apache.maven.artifact.versioning.ComparableVersion
  3. Sort versions (highest first)
  4. Keep highest version only
  5. Mark others as is_latest=0
  6. DELETE FROM maven_modules WHERE is_latest=0 (CASCADE deletes dependencies)
```

### Why in Java?

**Problem:** Oracle `ORDER BY version DESC` doesn't work correctly for Maven versions:
- `1.10` vs `1.9` → alphabetically 1.9 > 1.10 (wrong!)
- `1.0-SNAPSHOT` vs `1.0` → alphabetically unpredictable
- `1.0-RC1` vs `1.0` → qualifiers have special semantics

**Solution:** `ComparableVersion` in Java understands Maven version semantics.

### Pseudocode

```java
// For each (groupId, artifactId):
List<MavenModule> versions = fetchAllVersions(groupId, artifactId);

// Sort using ComparableVersion
versions.sort((a, b) -> 
    new ComparableVersion(b.version).compareTo(new ComparableVersion(a.version))
);

// Keep first (highest), delete rest
MavenModule latest = versions.get(0);
List<MavenModule> toDelete = versions.subList(1, versions.size());

// Mark as not latest
for (MavenModule old : toDelete) {
    update("UPDATE maven_modules SET is_latest=0 WHERE id=?", old.id);
}

// Delete (CASCADE)
delete("DELETE FROM maven_modules WHERE is_latest=0 AND group_id=? AND artifact_id=?", 
       groupId, artifactId);
```

### SQL (only DELETE, sorting already done in Java)

```sql
-- Mark old versions (done in Java after sorting)
UPDATE maven_modules
SET is_latest = 0
WHERE id IN (/* list of old version IDs from Java */);

-- Delete old versions
DELETE FROM maven_modules
WHERE group_id = 'com.company' AND artifact_id = 'my-app' AND is_latest = 0;
-- CASCADE automatically deletes dependencies
```

---

## 12. Overwrite Semantics (detailed)

### Scenario

Same module (GAV) built multiple times → last export overwrites previous.

### Algorithm

```
1. UPSERT module:
   - Check if module (GAV) exists
   - If exists: UPDATE exportTimestamp
   - If not exists: INSERT

2. DELETE old dependencies:
   - DELETE FROM maven_dependencies 
     WHERE source_module_id = (SELECT id FROM maven_modules WHERE GAV=...)
   - This removes all old edges outgoing from this module

3. INSERT new dependencies:
   - INSERT new edges (batch 500)

4. Result: same module, new dependencies, new timestamp
```

### SQL (Oracle)

```sql
-- 1. UPSERT module (Oracle MERGE)
MERGE INTO maven_modules m
USING (SELECT 'com.company' AS group_id, 
              'my-app' AS artifact_id, 
              '1.0.0' AS version FROM dual) src
ON (m.group_id = src.group_id 
    AND m.artifact_id = src.artifact_id 
    AND m.version = src.version)
WHEN MATCHED THEN
    UPDATE SET m.export_timestamp = CURRENT_TIMESTAMP, m.is_latest = 1
WHEN NOT MATCHED THEN
    INSERT (group_id, artifact_id, version, export_timestamp, is_latest)
    VALUES (src.group_id, src.artifact_id, src.version, CURRENT_TIMESTAMP, 1);

-- 2. DELETE old dependencies
DELETE FROM maven_dependencies
WHERE source_module_id = (
    SELECT id FROM maven_modules 
    WHERE group_id='com.company' AND artifact_id='my-app' AND version='1.0.0'
);

-- 3. INSERT new dependencies (batch)
INSERT INTO maven_dependencies 
(source_module_id, target_module_id, scope, depth, optional, is_resolved)
VALUES (?, ?, ?, ?, ?, ?);
-- ... repeat for all dependencies in batch
```

### Neo4j (Cypher)

```cypher
// 1. MERGE module
MERGE (m:MavenModule {groupId: 'com.company', artifactId: 'my-app', version: '1.0.0'})
SET m.exportTimestamp = datetime(),
    m.isLatest = true,
    m.packaging = 'jar';

// 2. DELETE old relationships
MATCH (m:MavenModule {groupId: 'com.company', artifactId: 'my-app', version: '1.0.0'})
       -[r:DEPENDS_ON]->()
DELETE r;

// 3. CREATE new relationships
MATCH (source:MavenModule {groupId: 'com.company', artifactId: 'my-app', version: '1.0.0'}),
      (target:MavenModule {groupId: 'org.springframework', artifactId: 'spring-core', version: '5.0'})
CREATE (source)-[:DEPENDS_ON {
    scope: 'compile',
    depth: 0,
    optional: false,
    isResolved: true,
    exportTimestamp: datetime()
}]->(target);
```

---

## 13. Error Handling & Resilience

### Connection Errors

**Retry logic:**
- **Retry:** 3 attempts
- **Backoff:** 2 seconds between attempts
- **Scope:** connection errors only (timeout, refused, network)
- **NO retry for:** SQL errors, constraint violations, authentication failures

**Workflow:**
```
Attempt 1: Now
         ↓ (ConnectionException)
Wait 2s
Attempt 2: After 2s
         ↓ (ConnectionException)
Wait 2s
Attempt 3: After 2s
         ↓ (ConnectionException)
         → Give up
         → If failOnError=false: log ERROR, continue
         → If failOnError=true: throw exception, fail build
```

### Transactional Errors

- Entire export in **one transaction**
- If fail midway (e.g. batch 3 of 5 fails) → **ROLLBACK everything**
- No partial changes in database
- **Isolation:** READ_COMMITTED (default)

### Validation Errors

**Configuration validation (before export):**
- Required fields (databaseType, connectionUrl, username, password)
- Valid values (databaseType: neo4j|oracle, transitiveDepth: -1 or >=0)
- Pattern syntax (filters must be in groupId:artifactId format)

**Schema version validation:**
- Check if schema version in DB == expected version
- If mismatch: WARN (and continue or fail depending on failOnError)

### Error Logging

```
[ERROR] GDM Export failed: Cannot connect to database bolt://localhost:7687
[ERROR] Retried 3 times with 2s backoff
[ERROR] Cause: Connection refused
[ERROR] Action: Check if database is running and connection URL is correct
[ERROR] Build will continue (failOnError=false)
```

---

## 14. Performance Considerations

### Batch Processing

- **Batch size:** 500 records per batch (configurable? not in MVP)
- **Target:** <5s for <100 deps, <30s for <1000 deps
- **Optimization:** 
  - Bulk inserts (PreparedStatement batch)
  - Defer constraint checks (in transaction)
  - Indexes on key columns

### Connection Pooling

- **Neo4j:** Driver has built-in connection pooling
- **Oracle:** HikariCP with default settings
  - max pool size: 10
  - connection timeout: 30s
- Reuse connection across batches (one transaction)

### Memory Management

- **Stream processing:** don't load entire graph into memory at once
- **Large projects:** process dependencies in chunks
- **Target:** handle 10,000+ dependencies without OOM

### Indexes

All key columns/properties have indexes (see sections 5 and 6).

---

## 15. Multi-Module Projects

### Example

```
parent-pom (packaging=pom)
├── module-a (packaging=jar)
│   └── depends on: spring-core:5.0
├── module-b (packaging=jar)
│   └── depends on: module-a:1.0.0
└── module-c (packaging=jar)
    └── depends on: module-b:1.0.0
```

### Export Workflow

Each module exports **independently** (plugin is invoked per-module by Maven).

**Module-a export:**
```
Nodes: module-a:1.0.0, spring-core:5.0
Edges: module-a → spring-core (depth=0)
```

**Module-b export:**
```
Nodes: module-b:1.0.0, module-a:1.0.0, spring-core:5.0
Edges: 
  module-b → module-a (depth=0)
  module-a → spring-core (depth=1, transitive from module-a)
```

**Module-c export:**
```
Nodes: module-c:1.0.0, module-b:1.0.0, module-a:1.0.0, spring-core:5.0
Edges:
  module-c → module-b (depth=0)
  module-b → module-a (depth=1)
  module-a → spring-core (depth=2)
```

### Internal Dependencies

Inter-module dependencies (e.g. module-b→module-a) are treated as **normal dependencies**:
- Exported with full GAV
- Marked with scope, depth, etc.
- No special treatment

### Parent POM

Parent POM (packaging=pom):
- Exported as normal module
- `packaging='pom'` in database
- Usually no dependencies (only dependencyManagement)
- Parent relationships (child→parent) are **NOT exported** as dependencies

---

## 16. Test Plan (no code, only criteria)

### Unit Tests

**PatternMatcher:**
- `org.springframework:*` matches `org.springframework:spring-core`
- `*:junit` matches `junit:junit` and `org.junit:junit`
- `com.company:my-?-app` matches `com.company:my-1-app` but not `my-10-app`

**FilterEngine:**
- Include + exclude: exclude has priority
- Include without exclude: only matched are included
- No include: everything is included (except excluded)

**ComparableVersion sorting:**
- `[1.10, 1.9, 1.8]` → sorted: `[1.10, 1.9, 1.8]` (not alphabetically!)
- `[1.0, 1.0-SNAPSHOT, 1.0-RC1]` → sorted correctly per Maven semantics

**Depth calculation:**
- Root module has depth=0 (for its direct deps)
- Transitive deps have depth=parent_depth+1

**Retry logic classification:**
- ConnectionException → RETRY
- SQLException → NO RETRY
- ConstraintViolationException → NO RETRY

### Integration Tests

**Full export workflow:**
- Create simple Maven project with several dependencies
- Run `mvn gdm:export`
- Verify in DB:
  - Number of modules (expected count)
  - Number of dependencies (expected count)
  - Depth values correct
  - is_resolved flags correct for conflicts

**Conflict detection:**
- Project with intentional version conflict
- Check that both versions are in DB
- Check that is_resolved=false for omitted, true for resolved

**Overwrite:**
- Export same GAV twice
- Check that timestamp changed
- Check that old dependencies were deleted

**Cleanup:**
- Export several versions of same GA
- Run with keepOnlyLatestVersion=true
- Check that only latest remains

### Maven Integration Tests (IT)

**Multi-module project:**
- Parent + 3 modules
- Each module has dependencies
- Run mvn gdm:export on parent (reactor build)
- Check that all modules are in DB

**Filter integration:**
- Export with includeFilters
- Check that only matched dependencies are in DB

**Scope filter:**
- Export with exportScopes=[compile, runtime]
- Check that test dependencies are not in DB

### Contract Tests

**Maven Resolver API:**
- Mock DependencyResolutionResult
- Check that conflicts are correctly detected
- Check that omitted nodes are marked is_resolved=false

**Database schema compatibility:**
- Check that schema version check works
- Check that mismatch is logged (WARN)

---

## 17. Security Notes

### Credentials

- Stored in pom.xml (plain or encrypted with Maven encryption)
- **Recommendation:** use Maven password encryption for production
- Environment variables NOT supported in MVP (maybe v1.1)

### Database

- **Recommendation:** use secure DB connection (SSL/TLS for Neo4j, encrypted JDBC for Oracle)
- **Recommendation:** restrict database user permissions (only INSERT/UPDATE/DELETE on GDM tables)
- **Recommendation:** use exclude filters for sensitive internal dependencies

### Logging

- **DO NOT log** passwords in plain text (even at DEBUG level)
- **DO NOT log** connection strings with passwords

---

## 18. Future Enhancements

- **v1.1:** Command-line overrides (-DtransitiveDepth=1)
- **v1.2:** Environment variables for credentials
- **v1.3:** License tracking (dependency licenses)
- **v1.4:** Vulnerability scanning integration (CVE tracking)
- **v2.0:** REST API, GraphQL API, Web UI for query/visualization
- **v2.1:** PostgreSQL, MySQL support

---

## 19. Definitions and Glossary

**GAV:** groupId:artifactId:version (unique Maven artifact identification)

**GA:** groupId:artifactId (identification without version)

**Depth:** Dependency depth counted from root module. Direct dependency has depth=0, its transitive dependencies have depth=1, etc.

**Tree Edges:** Graph model where we export parent→child relationships exactly as in Maven tree (not flattened to root→all).

**Resolved:** Dependency that is actually used in project (Maven Resolver chose this version in dependency mediation process).

**Conflict:** Situation when same artifact (GA) appears in graph with different versions. Maven chooses one (resolved), others are omitted.

**Omitted:** Dependency that was requested but rejected by Maven Resolver (e.g. due to version conflict).

**Transitive dependency:** Indirect dependency - dependency of your dependency.

**Effective graph:** Dependency graph after conflict resolution by Maven Resolver (only resolved dependencies).

**ComparableVersion:** Maven class (`org.apache.maven.artifact.versioning.ComparableVersion`) for comparing versions according to Maven semantics.

**Glob pattern:** Pattern matching with wildcard `*` (zero or more characters) and `?` (exactly one character).

---

**End of Architecture Document v3**


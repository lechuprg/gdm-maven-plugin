# GDM Maven Plugin - Technical Specification

**Version:** 3.0.0  
**Date:** 2026-02-09  
**Status:** Implementation Ready

---

## 1. Plugin Configuration (pom.xml)

### Minimal Configuration (Neo4j)

```xml
<plugin>
    <groupId>org.example.gdm</groupId>
    <artifactId>gdm-maven-plugin</artifactId>
    <version>1.0.0</version>
    <configuration>
        <databaseType>neo4j</databaseType>
        <connectionUrl>bolt://localhost:7687</connectionUrl>
        <username>neo4j</username>
        <password>password</password>
    </configuration>
</plugin>
```

### Complete Configuration (Oracle)

```xml
<plugin>
    <groupId>org.example.gdm</groupId>
    <artifactId>gdm-maven-plugin</artifactId>
    <version>1.0.0</version>
    <configuration>
        <!-- Required -->
        <databaseType>neo4j|oracle</databaseType>
        <connectionUrl>bolt://localhost:7687</connectionUrl>
        <username>db_user</username>
        <password>{encrypted_password}</password>
        
        <!-- Optional - Dependency Resolution -->
        <transitiveDepth>2</transitiveDepth>
        <exportScopes>
            <scope>compile</scope>
            <scope>runtime</scope>
        </exportScopes>
        
        <!-- Optional - Filters -->
        <includeFilters>
            <filter>com.company:*</filter>
            <filter>org.springframework:*</filter>
        </includeFilters>
        <excludeFilters>
            <filter>*:*-test</filter>
        </excludeFilters>
        
        <!-- Optional - Version Management -->
        <keepOnlyLatestVersion>true</keepOnlyLatestVersion>
        
        <!-- Optional - Execution -->
        <failOnError>false</failOnError>
    </configuration>
</plugin>
```

### Configuration Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `databaseType` | String | **Yes** | - | `neo4j` or `oracle` |
| `connectionUrl` | String | **Yes** | - | Connection URL (bolt:// for Neo4j, jdbc: for Oracle) |
| `username` | String | **Yes** | - | Database user |
| `password` | String | **Yes** | - | Database password (plain or Maven encrypted) |
| `transitiveDepth` | Integer | No | -1 | -1=unlimited, 0=direct only, N=N levels |
| `exportScopes` | List<String> | No | [all] | compile, runtime, test, provided, system |
| `includeFilters` | List<String> | No | [] | Include patterns (glob style) |
| `excludeFilters` | List<String> | No | [] | Exclude patterns (glob style) |
| `keepOnlyLatestVersion` | Boolean | No | false | Delete old versions after export |
| `failOnError` | Boolean | No | false | Fail build on export error |

### Configuration Validation

**Required field validation:**
- `databaseType` must be present and one of: `neo4j`, `oracle`
- `connectionUrl` must be present and non-empty
- `username` must be present and non-empty
- `password` must be present (can be empty string if DB allows)

**Value validation:**
- `transitiveDepth`: must be -1 or >= 0
- `exportScopes`: each scope must be one of: compile, runtime, test, provided, system
- `includeFilters` / `excludeFilters`: each filter must match pattern `groupId:artifactId` (with optional `*` or `?`)

**Validation errors:**
- Invalid configuration → log ERROR and fail build immediately (regardless of `failOnError`)

---

## 2. Filter Pattern Format

### Pattern Syntax (Glob-style)

```
groupId:artifactId
```

**Wildcards:**
- `*` - matches zero or more characters
- `?` - matches exactly one character
- **NOT regex** - only glob pattern matching

### Examples

```
org.springframework:*              ← all Spring artifacts
org.springframework:spring-core    ← specific artifact
com.company:*                      ← all company artifacts
*:junit                            ← specific artifact from any group
*:spring-*                         ← any group, artifact starting with "spring-"
com.company:my-?-app               ← matches my-1-app, my-2-app, etc.
```

### Filter Application Logic

**Processing order:**

1. Collect entire dependency graph (filters don't affect traversal)
2. For each dependency in graph:
   - **Step 1:** Check EXCLUDE filters
     - If matches any exclude pattern → REJECT, skip to next dependency
   - **Step 2:** Check INCLUDE filters
     - If no include filters defined → ACCEPT
     - If include filters defined:
       - If matches at least one include pattern → ACCEPT
       - Otherwise → REJECT
3. Export only accepted dependencies

**Filter Scope:**
- Filters apply to **output only**, not to traversal
- Conflicts are detected on full graph (before filtering)
- Root module is never filtered (always exported)

### Configuration Example with Logic

```xml
<includeFilters>
    <filter>org.springframework:*</filter>
    <filter>com.company:*</filter>
</includeFilters>
<excludeFilters>
    <filter>*:*-test</filter>
    <filter>*:*-dev</filter>
</excludeFilters>
```

**Results:**
- ✅ `org.springframework:spring-core:5.0` - matches include, not excluded
- ❌ `org.springframework:spring-test:5.0` - matches include, but excluded (ends with -test)
- ✅ `com.company:my-app:1.0` - matches include, not excluded
- ❌ `com.google:guava:30.0` - doesn't match any include filter
- ❌ `com.company:my-app-dev:1.0` - matches include, but excluded (ends with -dev)

---

## 3. Scope Filtering

### Default Scopes (if not specified)

```xml
<exportScopes>
    <scope>compile</scope>
    <scope>runtime</scope>
    <scope>test</scope>
    <scope>provided</scope>
    <scope>system</scope>
</exportScopes>
```

**Behavior:** Export dependencies from all scopes.

### Custom Scopes - Production Only

```xml
<exportScopes>
    <scope>compile</scope>
    <scope>runtime</scope>
</exportScopes>
```

**Behavior:** Export only compile and runtime dependencies. Test, provided, and system dependencies are filtered out.

### Special Cases

**Always filtered out:**
- `import` scope (only exists in dependencyManagement, not in dependency graph)

**Optional dependencies:**
- Included in export (with `optional=true` flag)
- Maven Resolver includes them in graph
- Filter by scope still applies

**Scope inheritance:**
- Transitive dependencies inherit scope transformation rules from Maven
- Plugin uses Maven Resolver's scope calculation

---

## 4. Password Management

### Plain Text (Development)

```xml
<configuration>
    <password>MySecurePassword123</password>
</configuration>
```

**Use case:** Local development, testing  
**Security:** Low - password visible in pom.xml

### Maven Password Encryption (Production)

**Step 1: Create master password**
```bash
mvn --encrypt-master-password
# Enter master password: myMasterPassword
# Output: {masterPasswordHash}
```

**Step 2: Store master password**
```xml
<!-- ~/.m2/settings-security.xml -->
<settingsSecurity>
    <master>{masterPasswordHash}</master>
</settingsSecurity>
```

**Step 3: Encrypt database password**
```bash
mvn --encrypt-password
# Enter password: MySecurePassword123
# Output: {encryptedPassword}
```

**Step 4: Use encrypted password**

**Option A: In pom.xml directly**
```xml
<configuration>
    <password>{encryptedPassword}</password>
</configuration>
```

**Option B: In settings.xml (recommended)**
```xml
<!-- ~/.m2/settings.xml -->
<servers>
    <server>
        <id>gdm-database</id>
        <username>db_user</username>
        <password>{encryptedPassword}</password>
    </server>
</servers>
```

```xml
<!-- pom.xml -->
<configuration>
    <serverId>gdm-database</serverId>
</configuration>
```

**Plugin behavior:**
- Automatically detects encrypted password (starts with `{` and ends with `}`)
- Uses Maven's decryption mechanism
- Falls back to plain text if not encrypted

---

## 5. Database Schemas

### Neo4j Schema (Cypher)

```cypher
-- Create schema version node
MERGE (v:SchemaVersion {id: 'current'})
SET v.version = '1.0.0', v.appliedAt = datetime();

-- Module uniqueness constraint
CREATE CONSTRAINT module_unique IF NOT EXISTS
FOR (m:MavenModule)
REQUIRE (m.groupId, m.artifactId, m.version) IS UNIQUE;

-- Performance indexes
CREATE INDEX module_ga IF NOT EXISTS
FOR (m:MavenModule) ON (m.groupId, m.artifactId);

CREATE INDEX module_latest IF NOT EXISTS
FOR (m:MavenModule) ON (m.isLatest);

CREATE INDEX dep_depth IF NOT EXISTS
FOR ()-[r:DEPENDS_ON]-() ON (r.depth);

CREATE INDEX dep_resolved IF NOT EXISTS
FOR ()-[r:DEPENDS_ON]-() ON (r.isResolved);
```

**Rationale for new indexes (v3):**
- `dep_depth` and `dep_resolved` indexes were added to optimize common queries for analyzing dependency graphs by depth and resolution status, which were identified as frequent use cases after v2.

**Node properties:**
```
MavenModule:
- groupId: String
- artifactId: String
- version: String
- packaging: String (jar, war, pom, etc.)
- exportTimestamp: DateTime
- isLatest: Boolean

SchemaVersion:
- id: String ('current')
- version: String ('1.0.0')
- appliedAt: DateTime
```

**Relationship properties:**
```
DEPENDS_ON:
- scope: String (compile, runtime, test, provided, system)
- optional: Boolean
- depth: Integer (0=direct, 1+=transitive from root)
- isResolved: Boolean (true=effective, false=conflict)
- exportTimestamp: DateTime
```

**Naming Convention Note:**
- **Oracle (SQL):** Uses `snake_case` (e.g., `group_id`, `export_timestamp`) following traditional SQL standards.
- **Neo4j (Cypher) & JSON:** Uses `camelCase` (e.g., `groupId`, `exportTimestamp`) following modern Java and API development standards.
- The plugin handles this mapping internally.

### Oracle Schema (SQL)

```sql
-- Create tables
CREATE TABLE maven_modules (
    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id VARCHAR2(255) NOT NULL,
    artifact_id VARCHAR2(255) NOT NULL,
    version VARCHAR2(100) NOT NULL,
    packaging VARCHAR2(50) DEFAULT 'jar',
    export_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_latest NUMBER(1) DEFAULT 1,
    CONSTRAINT uk_maven_module UNIQUE (group_id, artifact_id, version)
);

CREATE TABLE maven_dependencies (
    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_module_id NUMBER NOT NULL,
    target_module_id NUMBER NOT NULL,
    scope VARCHAR2(20) DEFAULT 'compile',
    optional NUMBER(1) DEFAULT 0,
    depth NUMBER(2) DEFAULT 0,
    is_resolved NUMBER(1) DEFAULT 1,
    export_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
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
    version VARCHAR2(20) PRIMARY KEY,
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_module_ga ON maven_modules(group_id, artifact_id);
CREATE INDEX idx_module_latest ON maven_modules(is_latest);
CREATE INDEX idx_dep_source ON maven_dependencies(source_module_id);
CREATE INDEX idx_dep_target ON maven_dependencies(target_module_id);
CREATE INDEX idx_dep_resolved ON maven_dependencies(is_resolved);
CREATE INDEX idx_dep_depth ON maven_dependencies(depth);
CREATE INDEX idx_dep_scope ON maven_dependencies(scope);
```

**Important schema changes from v2:**
- `maven_dependencies.uk_dependency` now includes `depth` instead of `is_resolved`
- Rationale: Allows same dependency at different depths (different paths in graph)

---

## 6. Batch Processing

### Batch Configuration

- **Batch size:** 500 records per batch (hardcoded in MVP)
- **Unit:** Dependencies (each dependency = 1 record)
- **Transaction scope:** Entire export in one transaction

### Batch Processing Flow

**Example:** 2,347 dependencies to export

```
Start Transaction
├─ Batch 1: Process dependencies 1-500       ✓
├─ Batch 2: Process dependencies 501-1000    ✓
├─ Batch 3: Process dependencies 1001-1500   ✓
├─ Batch 4: Process dependencies 1501-2000   ✓
├─ Batch 5: Process dependencies 2001-2347   ✓
└─ Commit Transaction                         ✓
```

**Total time:** ~3-5 seconds (depending on database performance)

### Batch Error Handling

**If batch fails:**
1. Log batch number and error details
2. ROLLBACK entire transaction (all batches)
3. No partial data in database
4. If `failOnError=true` → fail build
5. If `failOnError=false` → log ERROR, continue build

**Error logging:**
```
[ERROR] Batch 3/5 failed: Constraint violation
[ERROR] Dependency: com.example:my-lib:1.0 -> org.springframework:spring-core:5.0
[ERROR] Rolling back entire export transaction
[ERROR] 0 of 2347 dependencies exported
```

### Performance Optimization

**Techniques used:**
- PreparedStatement with batch execution
- Defer constraint checking until commit
- Reuse single connection/transaction
- Bulk insert operations

---

## 7. Retry Logic

### Retry Scope

**Retry is triggered ONLY for:**
- Connection timeout (SocketTimeoutException)
- Connection refused (ConnectException)
- Network errors (IOException subtypes related to network)
- Database unavailable errors

**NO retry for:**
- SQL syntax errors
- Constraint violations (unique, foreign key)
- Authentication failures (bad credentials)
- Authorization failures (insufficient permissions)
- Data type errors

### Retry Strategy

```
Attempt 1: Execute now
         ↓ (ConnectionException caught)
         Log: "Connection failed, retrying in 2s (1/3)"
         ↓
Wait 2 seconds
         ↓
Attempt 2: Execute
         ↓ (ConnectionException caught)
         Log: "Connection failed, retrying in 2s (2/3)"
         ↓
Wait 2 seconds
         ↓
Attempt 3: Execute (last attempt)
         ↓ (ConnectionException caught)
         Log: "Connection failed, no more retries"
         ↓
Handle based on failOnError setting
```

**After all retries exhausted:**
- If `failOnError=false` → log ERROR, continue build, return success
- If `failOnError=true` → throw MojoExecutionException, fail build

### Retry Configuration

**Hardcoded in MVP:**
- Max attempts: 3
- Backoff: 2 seconds (fixed, not exponential)
- Total max wait time: 4 seconds (2s + 2s)

**Future enhancement (v1.1):**
- Configurable retry count
- Configurable backoff strategy
- Exponential backoff

---

## 8. Export Execution Flow

### Detailed Step-by-Step Flow

```
1. Plugin Initialization
   ├─ Parse configuration from pom.xml
   ├─ Validate required parameters
   ├─ Validate parameter values (types, ranges)
   └─ Log configuration summary (INFO)
   
2. Password Decryption (if needed)
   ├─ Check if password is encrypted (starts with '{')
   ├─ If encrypted: use Maven decryption mechanism
   └─ If decryption fails: log ERROR, fail build
   
3. Dependency Resolution
   ├─ Get current Maven project
   ├─ Create DependencyResolutionRequest
   ├─ Set transitive depth limit
   ├─ Execute Maven Resolver API
   ├─ Traverse dependency tree (depth-first)
   ├─ Collect nodes and edges
   ├─ Detect version conflicts (omitted nodes)
   ├─ Mark is_resolved flags
   └─ Build DependencyGraph object
   
4. Filter Application
   ├─ For each dependency in graph:
   │  ├─ Check exclude filters → reject if match
   │  ├─ Check include filters → reject if no match
   │  └─ Check scope filters → reject if not in exportScopes
   ├─ Build filtered DependencyGraph
   ├─ Count filtered-out dependencies
   └─ Log filter results (INFO/WARN)
   
5. Database Connection
   ├─ Attempt connection (with retry)
   │  ├─ Attempt 1
   │  ├─ If fail: wait 2s, Attempt 2
   │  └─ If fail: wait 2s, Attempt 3
   ├─ If all attempts fail: handle per failOnError
   └─ Log connection success (INFO)
   
6. Schema Version Check
   ├─ Query SchemaVersion table/node
   ├─ If not exists: create with version 1.0.0
   ├─ If exists: compare version
   ├─ If mismatch: log WARN
   └─ Continue (or fail if failOnError=true)
   
7. Start Database Transaction
   └─ Set isolation level: READ_COMMITTED
   
8. Module Processing (Upsert)
   ├─ For each unique module in graph:
   │  ├─ Check if module (GAV) exists
   │  ├─ If exists: UPDATE export_timestamp, is_latest=1
   │  └─ If not exists: INSERT module
   └─ Log modules upserted count (INFO)
   
9. Delete Old Dependencies
   ├─ For each module being exported:
   │  └─ DELETE FROM dependencies WHERE source_module_id = module.id
   └─ Log dependencies deleted count (DEBUG)
   
10. Insert New Dependencies (Batched)
    ├─ Batch 1 (dependencies 1-500)
    │  └─ INSERT 500 dependency records
    ├─ Batch 2 (dependencies 501-1000)
    │  └─ INSERT 500 dependency records
    ├─ Batch 3 (dependencies 1001-1500)
    │  └─ INSERT 500 dependency records
    ├─ Batch 4 (dependencies 1501-2000)
    │  └─ INSERT 500 dependency records
    ├─ Batch 5 (dependencies 2001-2347)
    │  └─ INSERT 347 dependency records
    └─ Log batch progress (DEBUG)
    
11. Commit Transaction
    ├─ Commit all changes
    └─ Log commit success (INFO)
    
12. Cleanup Old Versions (if enabled)
    ├─ If keepOnlyLatestVersion=false: skip
    ├─ For each unique (groupId, artifactId):
    │  ├─ Fetch all versions from DB
    │  ├─ Sort versions using ComparableVersion (in Java)
    │  ├─ Keep highest version
    │  ├─ Mark others as is_latest=0
    │  └─ DELETE WHERE is_latest=0 (CASCADE)
    └─ Log cleanup results (INFO)
    
13. Export Summary
    ├─ Log total modules exported
    ├─ Log total dependencies exported
    ├─ Log conflicts detected count
    ├─ Log old versions deleted count
    ├─ Log total execution time
    └─ Return SUCCESS
```

### Error Handling at Each Step

- **Steps 1-2:** Validation errors → immediate fail (ignore failOnError)
- **Step 3:** Resolution errors → log ERROR, fail if failOnError=true
- **Step 4:** Filter errors → log ERROR, fail if failOnError=true
- **Step 5:** Connection errors → retry 3 times, then handle per failOnError
- **Step 6:** Schema mismatch → log WARN, continue or fail per failOnError
- **Steps 7-11:** Transaction errors → ROLLBACK, handle per failOnError
- **Step 12:** Cleanup errors → log ERROR, continue (cleanup is optional)

---

## 9. Version Conflict Detection & Representation

### Conflict Scenario

```
Project A (root)
├── B:1.0 ──> C:2.0  (requested by B)
└── D:1.0 ──> C:3.0  (requested by D)

Maven Resolver: Chooses C:3.0 (dependency mediation)
Reason: Nearest wins (both at depth 1, but 3.0 > 2.0)
```

### What Maven Resolver Returns

**Effective graph structure:**
```
A (root)
├── B:1.0 (effective)
│   └── C:2.0 (OMITTED for conflict with C:3.0)
└── D:1.0 (effective)
    └── C:3.0 (effective, chosen version)
```

**DependencyNode properties:**
- B→C:2.0 node: marked as omitted, reason: "conflict with C:3.0"
- D→C:3.0 node: part of effective graph

### What We Export

**Modules table/nodes:**
| groupId | artifactId | version | is_latest |
|---------|------------|---------|-----------|
| com.example | A | 1.0.0 | 1 |
| com.example | B | 1.0 | 1 |
| com.example | C | 2.0 | 1 |
| com.example | C | 3.0 | 1 |
| com.example | D | 1.0 | 1 |

**Dependencies table/relationships:**
| source | target | depth | scope | is_resolved |
|--------|--------|-------|-------|-------------|
| A:1.0.0 | B:1.0 | 0 | compile | 1 (true) |
| A:1.0.0 | D:1.0 | 0 | compile | 1 (true) |
| B:1.0 | C:2.0 | 1 | compile | **0 (false)** ← CONFLICT |
| D:1.0 | C:3.0 | 1 | compile | **1 (true)** ← RESOLVED |

### SQL Representation (Oracle)

```sql
-- Insert conflicted dependency
INSERT INTO maven_dependencies 
(source_module_id, target_module_id, scope, depth, is_resolved, export_timestamp)
VALUES (
    (SELECT id FROM maven_modules 
     WHERE group_id='com.example' AND artifact_id='B' AND version='1.0'),
    (SELECT id FROM maven_modules 
     WHERE group_id='com.example' AND artifact_id='C' AND version='2.0'),
    'compile',
    1,
    0,  -- NOT RESOLVED (conflict)
    CURRENT_TIMESTAMP
);

-- Insert resolved dependency
INSERT INTO maven_dependencies 
(source_module_id, target_module_id, scope, depth, is_resolved, export_timestamp)
VALUES (
    (SELECT id FROM maven_modules 
     WHERE group_id='com.example' AND artifact_id='D' AND version='1.0'),
    (SELECT id FROM maven_modules 
     WHERE group_id='com.example' AND artifact_id='C' AND version='3.0'),
    'compile',
    1,
    1,  -- RESOLVED (effective)
    CURRENT_TIMESTAMP
);
```

### Cypher Representation (Neo4j)

```cypher
// Create conflicted relationship
MATCH (source:MavenModule {groupId: 'com.example', artifactId: 'B', version: '1.0'}),
      (target:MavenModule {groupId: 'com.example', artifactId: 'C', version: '2.0'})
CREATE (source)-[:DEPENDS_ON {
    scope: 'compile',
    depth: 1,
    optional: false,
    isResolved: false,  // CONFLICT
    exportTimestamp: datetime()
}]->(target);

// Create resolved relationship
MATCH (source:MavenModule {groupId: 'com.example', artifactId: 'D', version: '1.0'}),
      (target:MavenModule {groupId: 'com.example', artifactId: 'C', version: '3.0'})
CREATE (source)-[:DEPENDS_ON {
    scope: 'compile',
    depth: 1,
    optional: false,
    isResolved: true,  // RESOLVED
    exportTimestamp: datetime()
}]->(target);
```

### Query Examples

**Find all conflicts:**
```sql
-- Oracle
SELECT 
    sm.group_id || ':' || sm.artifact_id || ':' || sm.version AS source,
    tm.group_id || ':' || tm.artifact_id || ':' || tm.version AS target,
    md.scope
FROM maven_dependencies md
JOIN maven_modules sm ON md.source_module_id = sm.id
JOIN maven_modules tm ON md.target_module_id = tm.id
WHERE md.is_resolved = 0;
```

```cypher
// Neo4j
MATCH (source:MavenModule)-[r:DEPENDS_ON {isResolved: false}]->(target:MavenModule)
RETURN source.groupId + ':' + source.artifactId + ':' + source.version AS source,
       target.groupId + ':' + target.artifactId + ':' + target.version AS target,
       r.scope AS scope;
```

**Find effective dependencies only:**
```sql
-- Oracle
SELECT * FROM maven_dependencies WHERE is_resolved = 1;
```

```cypher
// Neo4j
MATCH (source)-[r:DEPENDS_ON {isResolved: true}]->(target)
RETURN source, r, target;
```

---

## 10. Version Cleanup Algorithm

### When Triggered

```xml
<keepOnlyLatestVersion>true</keepOnlyLatestVersion>
```

**Execution timing:** After successful export and commit. This process runs in a **new, separate transaction**. A failure during cleanup will not roll back the main export.

### Algorithm (executed in Java)

```
For each unique (groupId, artifactId) in exported modules:
  
  1. Fetch all versions from database
     SELECT id, version FROM maven_modules 
     WHERE group_id = ? AND artifact_id = ?
  
  2. Parse versions using Maven ComparableVersion
     List<ComparableVersion> versions = ...
  
  3. Sort versions (descending = highest first)
     versions.sort(Comparator.reverseOrder())
  
  4. Identify latest version
     latestVersion = versions.get(0)
  
  5. Mark non-latest versions
     UPDATE maven_modules SET is_latest = 0
     WHERE group_id = ? AND artifact_id = ? 
       AND version != latestVersion
  
  6. Delete non-latest versions
     DELETE FROM maven_modules
     WHERE group_id = ? AND artifact_id = ? 
       AND is_latest = 0
     
     Note: CASCADE delete automatically removes dependencies
  
  7. Log cleanup result
     INFO: Deleted X old versions of groupId:artifactId
```

### Why Sorting in Java?

**Problem with database sorting:**

Oracle/SQL `ORDER BY version DESC` uses **lexicographic** (alphabetical) sorting:
- `1.9` > `1.10` (alphabetically, **wrong** for versions!)
- `1.0` vs `1.0-SNAPSHOT` - unpredictable
- `1.0-RC1` vs `1.0` - qualifiers have special Maven semantics

**Maven ComparableVersion correctly handles:**
- Numeric comparison: `1.10 > 1.9`
- Qualifiers: `1.0 > 1.0-RC1 > 1.0-SNAPSHOT`
- Special tokens: `1.0-alpha < 1.0-beta < 1.0-milestone < 1.0-rc < 1.0`

### Java Implementation Pseudocode

```java
public void cleanupOldVersions(String groupId, String artifactId) {
    // 1. Fetch all versions
    List<MavenModule> modules = jdbcTemplate.query(
        "SELECT id, version FROM maven_modules WHERE group_id=? AND artifact_id=?",
        new Object[]{groupId, artifactId},
        (rs, rowNum) -> new MavenModule(rs.getLong("id"), rs.getString("version"))
    );
    
    // 2. Sort using ComparableVersion
    modules.sort((a, b) -> 
        new ComparableVersion(b.getVersion())
            .compareTo(new ComparableVersion(a.getVersion()))
    );
    
    // 3. Keep first (latest), delete rest
    if (modules.size() <= 1) {
        return; // Nothing to clean up
    }
    
    MavenModule latest = modules.get(0);
    List<MavenModule> toDelete = modules.subList(1, modules.size());
    
    // 4. Mark as not latest
    List<Long> idsToDelete = toDelete.stream()
        .map(MavenModule::getId)
        .collect(Collectors.toList());
    
    jdbcTemplate.update(
        "UPDATE maven_modules SET is_latest = 0 WHERE id IN (?)",
        idsToDelete
    );
    
    // 5. Delete (CASCADE)
    int deleted = jdbcTemplate.update(
        "DELETE FROM maven_modules WHERE is_latest = 0 AND group_id=? AND artifact_id=?",
        groupId, artifactId
    );
    
    // 6. Log
    log.info("Deleted {} old versions of {}:{}, kept version {}", 
             deleted, groupId, artifactId, latest.getVersion());
}
```

### SQL Statements Used

**Oracle:**
```sql
-- Fetch versions
SELECT id, version FROM maven_modules 
WHERE group_id = :groupId AND artifact_id = :artifactId;

-- Mark old versions (after Java sorting)
UPDATE maven_modules SET is_latest = 0 
WHERE id IN (:oldVersionIds);

-- Delete old versions
DELETE FROM maven_modules 
WHERE group_id = :groupId AND artifact_id = :artifactId AND is_latest = 0;
```

**Neo4j (Cypher):**
```cypher
// Fetch all versions
MATCH (m:MavenModule {groupId: $groupId, artifactId: $artifactId})
RETURN m.version AS version, id(m) AS nodeId;

// Mark old versions (after Java sorting)
MATCH (m:MavenModule)
WHERE id(m) IN $oldVersionIds
SET m.isLatest = false;

// Delete old versions
MATCH (m:MavenModule {groupId: $groupId, artifactId: $artifactId, isLatest: false})
DETACH DELETE m;
```

---

## 11. Multi-Module Projects

### Export Behavior

**Maven reactor build:** When you run `mvn gdm:export` on parent POM, Maven executes goal for each module independently.

**Result:** Each module exports its own dependencies separately.

### Example Project Structure

```
parent-pom (packaging=pom)
├── module-a (packaging=jar)
│   └── depends on: spring-core:5.0
├── module-b (packaging=jar)
│   └── depends on: module-a:1.0.0
└── module-c (packaging=jar)
    └── depends on: module-b:1.0.0
```

### Individual Module Exports

**module-a export:**
```
Modules: module-a:1.0.0, spring-core:5.0
Dependencies:
- module-a:1.0.0 → spring-core:5.0 (depth=0, scope=compile)
```

**module-b export:**
```
Modules: module-b:1.0.0, module-a:1.0.0, spring-core:5.0
Dependencies:
- module-b:1.0.0 → module-a:1.0.0 (depth=0, scope=compile)
- module-a:1.0.0 → spring-core:5.0 (depth=1, scope=compile)
```

**module-c export:**
```
Modules: module-c:1.0.0, module-b:1.0.0, module-a:1.0.0, spring-core:5.0
Dependencies:
- module-c:1.0.0 → module-b:1.0.0 (depth=0, scope=compile)
- module-b:1.0.0 → module-a:1.0.0 (depth=1, scope=compile)
- module-a:1.0.0 → spring-core:5.0 (depth=2, scope=compile)
```

### Inter-Module Dependencies

**Treatment:** Inter-module dependencies (e.g., module-b → module-a) are treated as **normal dependencies**:
- Exported with full GAV
- Marked with scope, depth, optional flags
- No special "internal" flag or marking

**Rationale:** Simplifies data model and queries. Users can identify inter-module deps by checking if groupId matches project groupId.

### Parent POM Export

**Parent POM characteristics:**
```xml
<artifactId>parent-pom</artifactId>
<packaging>pom</packaging>
```

**Export:**
- Module record created: `parent-pom:1.0.0` with `packaging='pom'`
- Usually zero dependencies (POM modules typically only have dependencyManagement)
- If parent POM has actual dependencies → they are exported normally

**Parent relationships:** Child→Parent relationships (Maven parent/child) are **NOT exported** as dependencies. Only dependency relationships are exported.

---

## 12. Metadata Exported

### What We Export (Per Module)

```json
{
  "groupId": "com.company",
  "artifactId": "my-app",
  "version": "1.0.0",
  "packaging": "jar",
  "exportTimestamp": "2026-02-09T10:30:45Z",
  "isLatest": true
}
```

### What We DON'T Export

**Build environment:**
- Build user / username
- Build host / machine name
- Build timestamp (module's own build time)
- Build number / CI job number

**Source control:**
- Git commit hash
- Git branch name
- Git repository URL
- SVN revision

**Build tools:**
- Maven version
- Java version / JDK version
- Operating system

**Build results:**
- Build duration
- Test results
- Code coverage
- Build status (success/failure of original build)

**Rationale:** Keep data model simple and focused on dependency graph. Additional metadata can be added in future versions if needed.

---

## 13. Error Messages

### Connection Failure

```
[ERROR] ============================================================
[ERROR] GDM Export Failed: Database Connection Error
[ERROR] ============================================================
[ERROR] Database URL: bolt://localhost:7687
[ERROR] Database type: neo4j
[ERROR] Attempted: 3 times with 2s backoff
[ERROR] Error: Connection refused
[ERROR] ============================================================
[ERROR] Possible causes:
[ERROR] - Database is not running
[ERROR] - Incorrect connection URL
[ERROR] - Firewall blocking connection
[ERROR] - Network issue
[ERROR] ============================================================
[ERROR] Action: Verify database is running and accessible
[ERROR] Build will continue (failOnError=false)
```

### Schema Version Mismatch

```
[WARN] ============================================================
[WARN] Schema Version Mismatch Detected
[WARN] ============================================================
[WARN] Expected plugin version: 1.0.0
[WARN] Database schema version: 0.9.0
[WARN] ============================================================
[WARN] This may cause compatibility issues
[WARN] Recommendation: Update database schema to version 1.0.0
[WARN] Continuing export (failOnError=false)
[WARN] ============================================================
```

### Configuration Validation Failure

```
[ERROR] ============================================================
[ERROR] Plugin Configuration Invalid
[ERROR] ============================================================
[ERROR] Missing required parameter: databaseType
[ERROR] Invalid parameter: transitiveDepth=-2 (must be -1 or >= 0)
[ERROR] Invalid scope: unknown (must be one of: compile, runtime, test, provided, system)
[ERROR] ============================================================
[ERROR] Fix configuration in pom.xml and retry
[ERROR] Build failed
```

### Filter Results Empty

```
[INFO] ============================================================
[INFO] Export Filter Results
[INFO] ============================================================
[INFO] Total dependencies resolved: 247
[INFO] After filtering: 0 dependencies
[INFO] ============================================================
[INFO] Include filters: [com.mycompany:*]
[INFO] Exclude filters: [*:*-test, *:*-dev]
[INFO] Export scopes: [compile, runtime]
[INFO] ============================================================
[INFO] No dependencies matched filters - nothing exported
[INFO] This may indicate too restrictive filters
```

### Batch Processing Error

```
[ERROR] ============================================================
[ERROR] Export Failed: Batch Processing Error
[ERROR] ============================================================
[ERROR] Batch: 3 of 5
[ERROR] Records in batch: 500
[ERROR] Failed record: com.example:my-lib:1.0 -> org.springframework:spring-core:5.0
[ERROR] Error: Constraint violation - duplicate entry
[ERROR] ============================================================
[ERROR] Rolling back entire export (0 of 2347 dependencies saved)
[ERROR] Build will continue (failOnError=false)
```

---

## 14. Performance Targets

### Target Execution Times

| Project Size | Dependencies | Modules | Target Time | Notes |
|--------------|--------------|---------|-------------|-------|
| Tiny         | < 50         | 1-5     | < 2s        | Typical library |
| Small        | 50-100       | 5-10    | < 5s        | Simple application |
| Medium       | 100-500      | 10-20   | < 15s       | Standard enterprise app |
| Large        | 500-1000     | 20-50   | < 30s       | Large application |
| Very Large   | 1000-5000    | 50+     | < 60s       | Microservices platform |
| Huge         | > 5000       | 100+    | < 120s      | Monolith or large platform |

**Disclaimer:** These are performance **targets**, not guarantees. Actual execution time can vary significantly based on the factors below.

### Performance Factors

**What affects performance:**
- Number of dependencies (main factor)
- Database network latency
- Database write performance
- Number of version conflicts
- Filter complexity (minimal impact)
- Transitive depth (affects resolution time)

**What doesn't significantly affect performance:**
- Number of modules (unless 100+)
- Filter patterns (filters are fast)
- Scope filtering (minimal overhead)

### Performance Optimization

**Already implemented:**
- Batch inserts (500 records per batch)
- Single transaction (reduces overhead)
- PreparedStatement reuse
- Connection pooling
- Indexes on all key columns

**Future optimizations (v1.1+):**
- Parallel batch processing
- Async dependency resolution
- Caching of resolved dependencies
- Incremental export (only changed dependencies)

---

## 15. Execution Command

### Basic Execution

```bash
mvn gdm:export
```

**Behavior:**
- Executes export goal on current project
- Uses configuration from pom.xml
- Logs to console (INFO level by default)

### Multi-Module Execution

```bash
# From parent POM directory
mvn gdm:export
```

**Behavior:**
- Maven reactor processes each module
- Each module exports independently
- Order: depth-first (respects dependencies)

### With Maven Options

```bash
# With debug logging
mvn gdm:export -X

# Quiet mode (errors only)
mvn gdm:export -q

# Offline mode (use cached dependencies)
mvn gdm:export -o

# Skip tests, just export
mvn clean install -DskipTests gdm:export
```

### Integration with Build Lifecycle

**Option 1: Manual execution (recommended for MVP)**
```bash
mvn clean install
mvn gdm:export
```

**Option 2: Bound to phase (future enhancement)**
```xml
<plugin>
    <groupId>org.example.gdm</groupId>
    <artifactId>gdm-maven-plugin</artifactId>
    <version>1.0.0</version>
    <executions>
        <execution>
            <phase>install</phase>
            <goals>
                <goal>export</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

```bash
mvn install  # Automatically runs gdm:export
```

### Command-Line Overrides

**NOT supported in MVP:**
```bash
# These DON'T work in v1.0.0:
mvn gdm:export -DtransitiveDepth=1
mvn gdm:export -DdatabaseUrl=bolt://localhost:7687
mvn gdm:export -DfailOnError=true
```

**Workaround:** Modify pom.xml configuration

**Future (v1.1+):** Command-line property overrides will be supported

---

## 16. Logging Specification

### Log Levels and Content

**INFO (default):**
```
[INFO] ============================================================
[INFO] GDM Maven Plugin - Dependency Export
[INFO] ============================================================
[INFO] Configuration:
[INFO]   Database: neo4j (bolt://localhost:7687)
[INFO]   Transitive depth: 2
[INFO]   Export scopes: [compile, runtime]
[INFO]   Include filters: [com.company:*]
[INFO]   Exclude filters: []
[INFO] ============================================================
[INFO] Resolving dependencies...
[INFO] Dependencies resolved: 247 (including transitive)
[INFO] Applying filters...
[INFO] After filtering: 198 dependencies
[INFO] Connecting to database...
[INFO] Connection successful
[INFO] Schema version: 1.0.0 (compatible)
[INFO] Exporting modules...
[INFO] Exported 45 modules
[INFO] Exporting dependencies...
[INFO] Exported 198 dependencies in 4 batches
[INFO] Version conflicts detected: 3
[INFO] Cleanup: Deleted 12 old versions
[INFO] ============================================================
[INFO] Export completed successfully in 4.2s
[INFO] ============================================================
```

**DEBUG (-X):**
```
[DEBUG] Resolving dependency: org.springframework:spring-core:5.0
[DEBUG] Found in repository: central
[DEBUG] Transitive dependencies: 12
[DEBUG] Filter evaluation: org.springframework:spring-core:5.0
[DEBUG]   - Exclude filters: no match
[DEBUG]   - Include filters: match [com.company:*] -> REJECT (no match)
[DEBUG]   - Result: FILTERED OUT
[DEBUG] Batch 1: Processing 500 dependencies
[DEBUG] INSERT dependency: com.company:app:1.0 -> org.springframework:spring-core:5.0
[DEBUG] Batch 1: Completed in 1.2s
```

**WARN:**
```
[WARN] Schema version mismatch: expected 1.0.0, found 0.9.0
[WARN] 49 dependencies filtered out by include/exclude filters
[WARN] Version conflict: com.example:lib requested as 1.0 and 2.0, using 2.0
[WARN] Deleted 12 old versions of com.company:my-app
```

**ERROR:**
```
[ERROR] Connection failed: bolt://localhost:7687
[ERROR] Retrying in 2s (attempt 1/3)
[ERROR] Connection failed: bolt://localhost:7687
[ERROR] Retrying in 2s (attempt 2/3)
[ERROR] Connection failed: bolt://localhost:7687
[ERROR] All retry attempts exhausted
[ERROR] Export failed: Cannot connect to database
[ERROR] Build will continue (failOnError=false)
```

### Log Format

**Pattern:**
```
[LEVEL] MESSAGE
```

**With timestamps (optional):**
```
2026-02-09 10:30:45 [INFO] Export completed successfully
```

### Sensitive Data Handling

**Never log:**
- Passwords (plain text)
- Encrypted passwords
- Database connection strings with passwords
- Authentication tokens

**Safe to log:**
- Database URL without credentials
- Username
- Configuration parameters (except password)
- Query results
- Dependency GAVs

---

**End of Technical Specification v3**

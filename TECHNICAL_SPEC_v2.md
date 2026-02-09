# GDM Maven Plugin - Technical Specification

**Version:** 1.0.0  
**Date:** 2026-02-09  

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
| `includeFilters` | List<String> | No | [] | Include patterns |
| `excludeFilters` | List<String> | No | [] | Exclude patterns |
| `keepOnlyLatestVersion` | Boolean | No | false | Delete old versions |
| `failOnError` | Boolean | No | false | Fail build on export error |

---

## 2. Filter Pattern Format

**Maven Pattern Syntax:**
```
groupId:artifactId
```

**Examples:**
```
org.springframework:*              ← all Spring artifacts
org.springframework:spring-core    ← specific artifact
com.company:*                      ← all company artifacts
*:junit                            ← specific artifact any group
```

**Filter Logic:**

1. Check EXCLUDE filters first
   - If matches any exclude → REJECT
2. If no INCLUDE filters defined
   - ACCEPT all (that didn't match exclude)
3. If INCLUDE filters defined
   - Must match at least one INCLUDE filter
   - REJECT if no match

**Example:**
```xml
<includeFilters>
    <filter>org.springframework:*</filter>
</includeFilters>
<excludeFilters>
    <filter>*:*-test</filter>
</excludeFilters>
```

Result:
- ✅ org.springframework:spring-core:5.0 (included group, not test artifact)
- ❌ org.springframework:spring-test:5.0 (excluded: artifact ends with -test)
- ❌ com.google:guava:30.0 (not in include group)

---

## 3. Scope Filtering

**Default scopes** (if not specified):
```xml
<exportScopes>
    <scope>compile</scope>
    <scope>runtime</scope>
    <scope>test</scope>
    <scope>provided</scope>
    <scope>system</scope>
</exportScopes>
```

**Custom - only production scopes:**
```xml
<exportScopes>
    <scope>compile</scope>
    <scope>runtime</scope>
</exportScopes>
```

**Filtered out (always):**
- `import` (only in dependencyManagement)
- optional dependencies still included

---

## 4. Password Management

### Plain Text (Simple)

```xml
<configuration>
    <password>MySecurePassword123</password>
</configuration>
```

### Maven Password Encryption

**Step 1: Create master password**
```bash
mvn --encrypt-master-password
# Enter password: myMasterPassword
# Generates: ~/.m2/settings-security.xml
```

**Step 2: Encrypt password**
```bash
mvn --encrypt-password
# Enter password: MySecurePassword123
# Output: {encrypted_string}
```

**Step 3: Use in settings.xml**
```xml
<!-- ~/.m2/settings.xml -->
<servers>
    <server>
        <id>gdm-database</id>
        <username>db_user</username>
        <password>{encrypted_string}</password>
    </server>
</servers>
```

**Step 4: Use in pom.xml**
```xml
<configuration>
    <serverId>gdm-database</serverId>
    <!-- OR reference encrypted password directly -->
    <password>{encrypted_string}</password>
</configuration>
```

---

## 5. Database Schemas

### Neo4j Schema

```cypher
-- Schema version check
MERGE (v:SchemaVersion {id: 'current'})
SET v.version = '1.0.0', v.appliedAt = datetime();

-- Module constraint
CREATE CONSTRAINT module_unique IF NOT EXISTS
FOR (m:MavenModule)
REQUIRE (m.groupId, m.artifactId, m.version) IS UNIQUE;

-- Indexes
CREATE INDEX module_ga IF NOT EXISTS
FOR (m:MavenModule) ON (m.groupId, m.artifactId);

CREATE INDEX module_latest IF NOT EXISTS
FOR (m:MavenModule) ON (m.isLatest);
```

### Oracle Schema

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
        REFERENCES maven_modules(id) ON DELETE CASCADE
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
```

---

## 6. Batch Processing

### Batch Size

- **Size:** 500 records per batch
- **Unit:** 500 dependency records = 500 inserts
- **Transaction:** One transaction for entire export

### Batch Flow

```
Total dependencies: 2,347
Batch 1: records 1-500      ✓
Batch 2: records 501-1000   ✓
Batch 3: records 1001-1500  ✓
Batch 4: records 1501-2000  ✓
Batch 5: records 2001-2347  ✓
COMMIT all                  ✓
```

### Error Handling

- If any batch fails → ROLLBACK all
- No partial commits
- Log error with batch details
- If `failOnError=true` → fail build

---

## 7. Retry Logic

### Connection Errors Only

**Retry triggered for:**
- Connection timeout
- Connection refused
- Network errors
- Database unavailable

**No retry for:**
- SQL errors
- Constraint violations
- Authentication failures

### Retry Strategy

```
Attempt 1: Now
         ↓ (fail)
Wait 2s
Attempt 2: After 2s
         ↓ (fail)
Wait 2s
Attempt 3: After 2s
         ↓ (fail)
         → Give up
```

**After all retries fail:**
- If `failOnError=false` → log ERROR, continue
- If `failOnError=true` → throw exception, fail build

---

## 8. Export Execution Flow

```
1. Start
   │
2. Load configuration from pom.xml
   │
3. Resolve Maven dependencies
   ├─ Get current project
   ├─ Resolve direct dependencies
   ├─ Recursively resolve transitive (up to transitiveDepth)
   ├─ Apply filters (include/exclude)
   ├─ Filter by scopes
   └─ Build DependencyGraph
   │
4. Connect to database
   ├─ Retry 3 times on connection error
   └─ Check schema version
   │
5. Start transaction
   │
6. Process modules (batch 500)
   ├─ Check if module exists
   ├─ If new: INSERT
   ├─ If exists: UPDATE timestamp, DELETE old dependencies
   └─ Mark as is_latest=true
   │
7. Process dependencies (batch 500)
   ├─ Check for version conflicts
   ├─ For each dependency:
   │  ├─ Get source and target modules
   │  ├─ Check if relationship exists
   │  ├─ If not: INSERT
   │  └─ If yes: UPDATE is_resolved flag
   └─ Continue next batch
   │
8. Mark old versions as not latest
   │
9. Commit transaction
   │
10. Cleanup (if keepOnlyLatestVersion=true)
    ├─ Identify old versions
    └─ DELETE (with CASCADE)
    │
11. Log statistics and finish
```

---

## 9. Version Conflict Detection & Representation

### Scenario

```
Project A
├── B:1.0 → C:2.0 ┐
└── D:1.0 → C:3.0 ├─ CONFLICT: Maven chooses C:3.0
                   ┘
```

### What We Export

**Modules:**
- A:version (root)
- B:1.0
- C:2.0 (requested but not used)
- C:3.0 (resolved, actually used)
- D:1.0

**Dependencies:**
```
A -> B:1.0          (depth=0, is_resolved=true)
A -> D:1.0          (depth=0, is_resolved=true)
B:1.0 -> C:2.0      (depth=1, is_resolved=false)  ← CONFLICT
D:1.0 -> C:3.0      (depth=1, is_resolved=true)   ← RESOLVED
```

### SQL Representation

```sql
-- Conflicted dependency (not resolved)
INSERT INTO maven_dependencies 
(source_module_id, target_module_id, scope, depth, is_resolved, export_timestamp)
VALUES (
    (SELECT id FROM maven_modules WHERE ga='B:1.0'),
    (SELECT id FROM maven_modules WHERE ga='C:2.0'),
    'compile',
    1,
    0,  -- NOT RESOLVED
    CURRENT_TIMESTAMP
);

-- Resolved dependency
INSERT INTO maven_dependencies 
(source_module_id, target_module_id, scope, depth, is_resolved, export_timestamp)
VALUES (
    (SELECT id FROM maven_modules WHERE ga='D:1.0'),
    (SELECT id FROM maven_modules WHERE ga='C:3.0'),
    'compile',
    1,
    1,  -- RESOLVED
    CURRENT_TIMESTAMP
);
```

---

## 10. Version Cleanup

### When Triggered

```xml
<keepOnlyLatestVersion>true</keepOnlyLatestVersion>
```

### Algorithm

```
For each unique (groupId, artifactId):
  1. Get all versions
  2. Sort by Maven version (highest first)
  3. Keep highest version, delete rest
```

### SQL

```sql
-- Get latest version for module
WITH latest AS (
    SELECT id, version
    FROM maven_modules
    WHERE group_id = 'com.company' 
      AND artifact_id = 'my-app'
    ORDER BY version DESC  -- Maven ComparableVersion
    FETCH FIRST 1 ROWS ONLY
)
-- Delete all others
DELETE FROM maven_modules
WHERE group_id = 'com.company'
  AND artifact_id = 'my-app'
  AND id NOT IN (SELECT id FROM latest);
```

---

## 11. Multi-Module Projects

### Export per Module

Each module in multi-module project exports independently.

### Example

```
parent-pom (packaging=pom)
├── module-a (packaging=jar)
├── module-b (packaging=jar)
└── module-c (packaging=jar)
```

**Each module exports:**
- Its direct dependencies
- Its transitive dependencies (up to configured depth)
- Internal project dependencies (to module-a, module-b, etc.)

### Internal Dependencies

Internal project dependencies (e.g., module-b depends on module-a) are exported as normal dependencies:

```
module-b:1.0 -> module-a:1.0 (depth=0, scope=compile)
```

---

## 12. Parent POM Handling

### POM Packaging

Parent POM has `packaging=pom`:

```xml
<module>
    <groupId>com.company</groupId>
    <artifactId>parent</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
</module>
```

### Export

- Export as normal module
- **In database:** Mark with `packaging='pom'`
- **Dependencies:** Usually empty or only dependencyManagement
- **Not filtered:** `import` scope from dependencyManagement

---

## 13. Metadata Exported

### What We Export

Per module:
```
groupId: "com.company"
artifactId: "my-app"
version: "1.0.0"
packaging: "jar|war|pom"
exportTimestamp: "2026-02-09T10:30:45Z"
isLatest: true
```

### What We DON'T Export

- Build user
- Build host
- Git commit / branch
- Maven version
- Java version
- Build duration
- Build number

---

## 14. Error Messages

### Connection Failed

```
[ERROR] GDM Export failed: Cannot connect to database bolt://localhost:7687
[ERROR] Retried 3 times with 2s backoff
[ERROR] Cause: Connection refused
[ERROR] Action: Check if database is running and URL is correct
```

### Schema Version Mismatch

```
[WARN] GDM Schema version mismatch
[WARN] Expected: 1.0.0
[WARN] Database: 0.9.0
[WARN] Recommendation: Update database schema
```

### Filter Results Empty

```
[INFO] No dependencies matched filters
[INFO] Include filters: [org.company:*]
[INFO] Exclude filters: [*:*-test]
[INFO] Result: 0 dependencies exported
```

---

## 15. Performance Targets

| Project Size | Dependencies | Target Time |
|--------------|--------------|-------------|
| Tiny         | < 50         | < 2s        |
| Small        | 50-100       | < 5s        |
| Medium       | 100-500      | < 15s       |
| Large        | 500-1000     | < 30s       |
| Very Large   | > 1000       | < 60s       |

---

## 16. Execution Command

```bash
mvn gdm:export
```

All configuration comes from `pom.xml`.

No command-line overrides of `-D` properties supported in MVP.

---

**End of Technical Specification**


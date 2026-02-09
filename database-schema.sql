-- GDM Maven Plugin - Database Schema
-- Supports: Neo4j, Oracle
-- Date: 2026-02-09

-- ============================================================================
-- ORACLE SCHEMA
-- ============================================================================

-- Table: maven_modules
-- Stores Maven modules/artifacts
-- Primary key: (group_id, artifact_id, version) - uniqueness constraint
CREATE TABLE maven_modules (
    id                      NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id                VARCHAR2(255) NOT NULL,
    artifact_id             VARCHAR2(255) NOT NULL,
    version                 VARCHAR2(100) NOT NULL,
    packaging               VARCHAR2(50) DEFAULT 'jar',
    export_timestamp        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_latest               NUMBER(1) DEFAULT 1 CHECK (is_latest IN (0, 1)),
    CONSTRAINT uk_maven_module UNIQUE (group_id, artifact_id, version)
);

-- Table: maven_dependencies
-- Stores dependency relationships between modules
-- Tracks: scope, optional flag, depth, conflict resolution
CREATE TABLE maven_dependencies (
    id                      NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_module_id        NUMBER NOT NULL,
    target_module_id        NUMBER NOT NULL,
    scope                   VARCHAR2(20) DEFAULT 'compile',
    optional                NUMBER(1) DEFAULT 0 CHECK (optional IN (0, 1)),
    depth                   NUMBER(2) DEFAULT 0 CHECK (depth >= 0),
    is_resolved             NUMBER(1) DEFAULT 1 CHECK (is_resolved IN (0, 1)),
    export_timestamp        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dep_source
        FOREIGN KEY (source_module_id)
        REFERENCES maven_modules(id) ON DELETE CASCADE,
    CONSTRAINT fk_dep_target
        FOREIGN KEY (target_module_id)
        REFERENCES maven_modules(id) ON DELETE CASCADE,
    CONSTRAINT uk_dependency
        UNIQUE (source_module_id, target_module_id, scope, depth)
);

-- Table: gdm_schema_version
-- Tracks schema version for migration/compatibility
CREATE TABLE gdm_schema_version (
    version                 VARCHAR2(20) PRIMARY KEY,
    applied_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- INDEXES FOR ORACLE
-- ============================================================================

-- Index for finding all artifacts of a group
CREATE INDEX idx_module_ga
    ON maven_modules(group_id, artifact_id);

-- Index for finding latest versions only
CREATE INDEX idx_module_latest
    ON maven_modules(is_latest);

-- Index for export timestamp (useful for cleanup operations)
CREATE INDEX idx_module_timestamp
    ON maven_modules(export_timestamp);

-- Index for dependency source (find what depends on a module)
CREATE INDEX idx_dep_source
    ON maven_dependencies(source_module_id);

-- Index for dependency target (find what a module depends on)
CREATE INDEX idx_dep_target
    ON maven_dependencies(target_module_id);

-- Index for conflict detection (is_resolved=0 means conflict)
CREATE INDEX idx_dep_resolved
    ON maven_dependencies(is_resolved);

-- Index for depth queries (find direct vs transitive)
CREATE INDEX idx_dep_depth
    ON maven_dependencies(depth);

-- Index for scope queries
CREATE INDEX idx_dep_scope
    ON maven_dependencies(scope);

-- ============================================================================
-- INITIAL SETUP
-- ============================================================================

-- Insert schema version
INSERT INTO gdm_schema_version (version) VALUES ('1.0.0');
COMMIT;

-- ============================================================================
-- NEO4J SCHEMA
-- ============================================================================

-- Neo4j doesn't use SQL, but here's the equivalent Cypher commands:

/*
-- Create schema version node (Cypher)
MERGE (v:SchemaVersion {id: 'current'})
SET v.version = '1.0.0', v.appliedAt = datetime();

-- Module node uniqueness constraint
CREATE CONSTRAINT module_unique IF NOT EXISTS
FOR (m:MavenModule)
REQUIRE (m.groupId, m.artifactId, m.version) IS UNIQUE;

-- Module node properties:
-- - groupId: String
-- - artifactId: String
-- - version: String
-- - packaging: String (jar, war, pom, etc)
-- - exportTimestamp: DateTime
-- - isLatest: Boolean

-- Dependency relationship:
-- DEPENDS_ON properties:
-- - scope: String (compile, runtime, test, provided, system)
-- - optional: Boolean
-- - depth: Integer (0=direct, 1+=transitive)
-- - isResolved: Boolean (true=resolved/used, false=conflict)
-- - exportTimestamp: DateTime

-- Indexes
CREATE INDEX module_ga IF NOT EXISTS
FOR (m:MavenModule) ON (m.groupId, m.artifactId);

CREATE INDEX module_latest IF NOT EXISTS
FOR (m:MavenModule) ON (m.isLatest);

CREATE INDEX dep_depth IF NOT EXISTS
FOR ()-[r:DEPENDS_ON]-() ON (r.depth);

CREATE INDEX dep_resolved IF NOT EXISTS
FOR ()-[r:DEPENDS_ON]-() ON (r.isResolved);

-- Sample queries:

-- 1. Find all dependencies of a module
MATCH (m:MavenModule {groupId: 'com.company', artifactId: 'my-app', version: '1.0.0'})
      -[r:DEPENDS_ON*]->(dep)
WHERE m.isLatest = true
RETURN m, r, dep;

-- 2. Find modules depending on a library
MATCH (source:MavenModule)-[r:DEPENDS_ON]->(target:MavenModule)
WHERE target.groupId = 'org.springframework'
  AND source.isLatest = true
RETURN source, r, target;

-- 3. Find version conflicts
MATCH (a:MavenModule)-[r:DEPENDS_ON {isResolved: false}]->(b:MavenModule)
RETURN a, r, b;

-- 4. Find all versions of a module
MATCH (m:MavenModule {groupId: 'com.company', artifactId: 'my-app'})
RETURN m
ORDER BY m.version DESC;

-- 5. Find dependency path between modules
MATCH path = shortestPath(
  (source:MavenModule {groupId: 'com.company', artifactId: 'app-a'})
  -[r:DEPENDS_ON*]->
  (target:MavenModule {groupId: 'org.apache', artifactId: 'commons-lang3'})
)
RETURN path;
*/

-- ============================================================================
-- EXAMPLE QUERIES FOR ORACLE
-- ============================================================================

/*

-- 1. Find all direct dependencies of a module
SELECT
    tm.group_id,
    tm.artifact_id,
    tm.version,
    md.scope,
    md.optional
FROM maven_modules sm
JOIN maven_dependencies md ON sm.id = md.source_module_id
JOIN maven_modules tm ON md.target_module_id = tm.id
WHERE sm.group_id = 'com.company'
  AND sm.artifact_id = 'my-app'
  AND sm.version = '1.0.0'
  AND md.depth = 0
ORDER BY tm.group_id, tm.artifact_id;

-- 2. Find all versions of a module
SELECT
    version,
    export_timestamp,
    is_latest,
    (SELECT COUNT(*) FROM maven_dependencies
     WHERE source_module_id = mm.id) as dependency_count
FROM maven_modules mm
WHERE group_id = 'com.company'
  AND artifact_id = 'my-app'
ORDER BY export_timestamp DESC;

-- 3. Find modules that depend on a library
SELECT DISTINCT
    sm.group_id,
    sm.artifact_id,
    sm.version
FROM maven_modules sm
JOIN maven_dependencies md ON sm.id = md.source_module_id
JOIN maven_modules tm ON md.target_module_id = tm.id
WHERE tm.group_id = 'org.slf4j'
  AND tm.artifact_id = 'slf4j-api'
  AND sm.is_latest = 1
ORDER BY sm.group_id, sm.artifact_id;

-- 4. Find version conflicts (is_resolved=0)
SELECT
    sm.group_id,
    sm.artifact_id,
    sm.version,
    tm.group_id as target_group,
    tm.artifact_id as target_artifact,
    tm.version as target_version,
    md.scope,
    md.is_resolved
FROM maven_modules sm
JOIN maven_dependencies md ON sm.id = md.source_module_id
JOIN maven_modules tm ON md.target_module_id = tm.id
WHERE md.is_resolved = 0
ORDER BY sm.group_id, sm.artifact_id;

-- 5. Find transitive path to a dependency
WITH RECURSIVE dep_chain AS (
    SELECT
        source_module_id,
        target_module_id,
        depth,
        CAST(CONCAT(source_module_id, ' -> ', target_module_id) AS VARCHAR2(4000)) as path
    FROM maven_dependencies
    WHERE source_module_id = (SELECT id FROM maven_modules
                              WHERE group_id = 'com.company'
                              AND artifact_id = 'my-app'
                              AND is_latest = 1)

    UNION ALL

    SELECT
        dc.source_module_id,
        md.target_module_id,
        md.depth,
        dc.path || ' -> ' || md.target_module_id
    FROM dep_chain dc
    JOIN maven_dependencies md ON dc.target_module_id = md.source_module_id
    WHERE dc.depth < 5  -- prevent infinite recursion
)
SELECT DISTINCT path FROM dep_chain
WHERE target_module_id = (SELECT id FROM maven_modules
                          WHERE group_id = 'org.slf4j'
                          AND artifact_id = 'slf4j-api'
                          AND is_latest = 1);

-- 6. List modules that were deleted in cleanup
SELECT
    group_id,
    artifact_id,
    version,
    export_timestamp,
    is_latest
FROM maven_modules
WHERE is_latest = 0
ORDER BY group_id, artifact_id, export_timestamp DESC;

-- 7. Statistics: dependency count per module
SELECT
    m.group_id,
    m.artifact_id,
    m.version,
    COUNT(md.id) as dependency_count,
    SUM(CASE WHEN md.depth = 0 THEN 1 ELSE 0 END) as direct_deps,
    SUM(CASE WHEN md.depth > 0 THEN 1 ELSE 0 END) as transitive_deps
FROM maven_modules m
LEFT JOIN maven_dependencies md ON m.id = md.source_module_id
WHERE m.is_latest = 1
GROUP BY m.group_id, m.artifact_id, m.version
ORDER BY dependency_count DESC;

*/

-- ============================================================================
-- NOTES
-- ============================================================================

/*
Column Descriptions:

maven_modules:
- id: Surrogate key
- group_id: Maven groupId (e.g., "org.springframework")
- artifact_id: Maven artifactId (e.g., "spring-core")
- version: Version string (e.g., "5.3.0")
- packaging: Type of artifact (jar, war, pom, etc). Default: jar
- export_timestamp: When this version was exported
- is_latest: Flag indicating if this is the latest version of the module
             Useful for cleanup operations and latest-version queries

maven_dependencies:
- id: Surrogate key
- source_module_id: FK to maven_modules (the module that declares the dependency)
- target_module_id: FK to maven_modules (the dependency)
- scope: Maven scope (compile, runtime, test, provided, system)
- optional: Flag for optional dependencies
- depth: 0=direct dependency, 1=transitive (1 level), 2=transitive (2 levels), etc.
- is_resolved: 1=this dependency is actually used (resolved by Maven)
               0=this dependency was requested but not used (conflict)
- export_timestamp: When this dependency relationship was recorded

gdm_schema_version:
- version: Schema version (e.g., "1.0.0")
- applied_at: When schema was created/updated
*/

-- ============================================================================
-- MIGRATION NOTES
-- ============================================================================

/*
Future schema migrations should:
1. Update gdm_schema_version table with new version
2. Add new columns as NOT NULL with DEFAULT values (for backward compatibility)
3. Create new tables as needed
4. Update indexes if queries change
5. Test with sample data before deployment

Example migration script (future):
UPDATE gdm_schema_version SET version = '1.1.0';
ALTER TABLE maven_modules ADD (new_column VARCHAR2(100) DEFAULT 'value');
*/

-- End of database schema


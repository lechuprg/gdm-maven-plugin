# Neo4j Module Representation Implementation

## Overview
This document describes the implementation of the Neo4j Module Representation specification as defined in `NEO4J_MODULE_REPRESENTATION.md`. The implementation adds support for `ProjectModule` nodes, `CONTAINS_MODULE` relationships (for parent-child module hierarchy), and `IS_A` relationships (linking ProjectModules to MavenModules) to the existing Neo4j dependency graph model.

## Architecture

### Key Design Principles

1. **Additive**: The implementation is strictly additive and does not modify or remove any existing `MavenModule` nodes or `DEPENDS_ON` relationships.

2. **Backward Compatible**: Existing clients using only `MavenModule` and `DEPENDS_ON` can continue to work without any changes.

3. **Idempotent**: All operations use MERGE and IF NOT EXISTS clauses to allow safe re-export.

4. **Transaction Safe**: Schema modifications (constraint creation) are separated from write operations to avoid Neo4j transaction conflicts.

## Components

### 1. Model Classes

#### `ProjectModule.java`
- Represents a Maven module within a project structure
- **Properties**:
  - `groupId`: Group ID (inherited from parent POM if necessary)
  - `artifactId`: Artifact ID
  - `version`: Version (inherited from parent POM if necessary)
  - `isRootProject`: Boolean flag (true for root, false for sub-modules)
- **Relationships**: Parent-child via `submodules` list
- **Key Methods**:
  - `addSubmodule(ProjectModule)`: Adds a child module
  - `getSubmodules()`: Returns unmodifiable list of direct children
  - `getAllSubmodulesRecursively()`: Returns all descendants
  - `getGAV()`: Returns groupId:artifactId:version identifier

#### `ProjectStructure.java`
- Holds complete project structure (root + all sub-modules)
- **Key Methods**:
  - `getAllModules()`: Returns all modules (root + all levels of sub-modules)
  - `getModuleCount()`: Returns total module count
  - `isSingleModule()` / `isMultiModule()`: Checks project type
  - `getContainsModuleRelationships()`: Returns parent-child pairs for Neo4j relationships
  - `findModule(groupId, artifactId, version)`: Looks up module by GAV
- **Factory Methods**:
  - `singleModule(groupId, artifactId, version)`: Creates single-module project
  - `fromMavenModule(MavenModule)`: Creates from existing MavenModule
  - `Builder`: For constructing complex multi-module hierarchies

### 2. Resolver

#### `ProjectStructureResolver.java`
- Extracts project module hierarchy from Maven project model
- **Resolution Strategy**:
  1. For single-module projects (no modules defined): Returns root module only
  2. For multi-module projects: Uses Maven session's reactor projects
  3. Fallback: Uses POM `<modules>` declarations with inherited groupId/version
- **Key Methods**:
  - `resolve()`: Main entry point, handles all cases
  - `resolveFromReactor()`: Establishes relationships from Maven parent references
  - `resolveMultiModuleStructure()`: Handles module declarations in POM

### 3. Exporter Implementation

#### `Neo4jExporter.exportProjectStructure(ProjectStructure)`
- Main method for exporting project structure to Neo4j
- **Execution Flow**:
  1. **Schema Setup** (outside transaction): Creates `ProjectModule` uniqueness constraint
  2. **Node Creation**: MERGE all ProjectModule nodes with properties
  3. **CONTAINS_MODULE Relationships**: Create directed edges (parent → child)
  4. **IS_A Relationships**: Link ProjectModules to existing MavenModules (only if match found)
- **Transaction Management**: 
  - Constraint creation is done outside of transactions (Neo4j requirement)
  - All write operations happen within a single transaction
  - Automatic retry on transient failures via `RetryExecutor`

#### Key Helper Methods
- `upsertProjectModule(tx, module)`: MERGE ProjectModule node with properties
- `createContainsModuleRelationship(tx, parent, child)`: MERGE CONTAINS_MODULE edge
- `createIsARelationship(tx, module)`: Create IS_A link to MavenModule if exists

### 4. Integration with Mojo

#### `ExportDependenciesMojo.java`
- Modified execution flow:
  1. Resolve dependencies (existing)
  2. Apply filters (existing)
  3. Export dependency graph (existing)
  4. **NEW**: Resolve project structure
  5. **NEW**: Export project structure
  6. Cleanup old versions (existing)
  7. Log results (updated to include ProjectModule count)

## Cypher Operations

### ProjectModule Constraint
```cypher
CREATE CONSTRAINT project_module_unique IF NOT EXISTS
FOR (p:ProjectModule)
REQUIRE (p.groupId, p.artifactId, p.version) IS UNIQUE;
```

### Create ProjectModule Node
```cypher
MERGE (p:ProjectModule {groupId: $groupId, artifactId: $artifactId, version: $version})
SET p.isRootProject = $isRootProject,
    p.exportTimestamp = datetime()
```

### Create CONTAINS_MODULE Relationship
```cypher
MATCH (parent:ProjectModule {groupId: $parentGroupId, ...}),
      (child:ProjectModule {groupId: $childGroupId, ...})
MERGE (parent)-[:CONTAINS_MODULE]->(child)
```

### Create IS_A Relationship
```cypher
MATCH (p:ProjectModule {groupId: $groupId, artifactId: $artifactId, version: $version}),
      (m:MavenModule {groupId: $groupId, artifactId: $artifactId, version: $version})
MERGE (p)-[:IS_A]->(m)
```

## Testing

### Unit Tests

1. **`ProjectModuleTest.java`**
   - Constructor validation (NPE for null parameters)
   - Builder pattern
   - Submodule management (add, list, recursive)
   - Identifier generation (GA, GAV)
   - Equality and hashing
   - String representation

2. **`ProjectStructureTest.java`**
   - Single-module project handling
   - Multi-module with relationships
   - Nested module hierarchies
   - Module lookup
   - Relationship extraction
   - Factory methods

3. **`ProjectStructureResolverTest.java`**
   - Single-module resolution
   - Multi-module detection
   - Reactor project integration
   - Submodule fallback with inherited properties

### Integration Tests

**`Neo4jProjectModuleExportIT.java`** (using Testcontainers)
- Single module project export
- Multi-module project with CONTAINS_MODULE relationships
- Nested module structures (3+ levels)
- IS_A relationship creation (with and without MavenModule)
- Idempotent exports (MERGE deduplication)
- Constraint creation
- Error handling and Neo4j connectivity

## Example Data Flow

### Single-Module Project
```
MavenProject (com.example:my-app:1.0.0)
    ↓
ProjectStructureResolver.resolve()
    ↓
ProjectStructure (1 module: root)
    ↓
Neo4jExporter.exportProjectStructure()
    ↓
Neo4j:
  - ProjectModule {groupId: com.example, artifactId: my-app, version: 1.0.0, isRootProject: true}
  - IS_A → MavenModule (if exists)
```

### Multi-Module Project
```
MavenProject (com.example:parent:1.0.0)
  - Module: sub-a
  - Module: sub-b
    ↓
ProjectStructureResolver.resolve() [from reactor]
    ↓
ProjectStructure (3 modules: root + 2 children)
    ↓
Neo4jExporter.exportProjectStructure()
    ↓
Neo4j:
  - ProjectModule {parent} (isRootProject: true)
  - ProjectModule {sub-a} (isRootProject: false)
  - ProjectModule {sub-b} (isRootProject: false)
  - parent -[:CONTAINS_MODULE]-> sub-a
  - parent -[:CONTAINS_MODULE]-> sub-b
  - All IS_A → MavenModule (if exists)
```

## Configuration

No additional Maven plugin configuration is required. The feature is automatically activated during the export phase when using the `gdm:export` goal.

## Usage Example

Standard Maven command:
```bash
mvn gdm:export \
  -Dgdm.databaseType=neo4j \
  -Dgdm.connectionUrl=bolt://localhost:7687 \
  -Dgdm.username=neo4j \
  -Dgdm.password=password \
  -Dgdm.transitiveDepth=2
```

The plugin will:
1. Resolve dependencies up to depth 2
2. Export dependency graph to MavenModule/DEPENDS_ON
3. Resolve project structure (automatically detects single/multi-module)
4. Export ProjectModule hierarchy with CONTAINS_MODULE relationships
5. Create IS_A links between ProjectModules and MavenModules

## Error Handling

- **Constraint Already Exists**: Logged as debug, no failure
- **MavenModule Not Found**: IS_A relationship simply not created
- **Missing MavenSession**: Fallback to POM module declarations
- **Transaction Conflicts**: Constraint creation done outside transactions
- **Retry Logic**: Transient failures automatically retried via RetryExecutor

## Compatibility

### With Existing Code
- ✅ No changes to MavenModule class or structure
- ✅ No changes to DEPENDS_ON relationship creation
- ✅ No changes to existing export methods
- ✅ No changes to existing tests

### With Neo4j
- ✅ Works with Neo4j 4.x and 5.x
- ✅ Uses Neo4j driver 5.x API
- ✅ Compatible with both Auth and No-Auth setups
- ✅ Handles schema modifications correctly

### With Maven
- ✅ Works with Maven 3.6+
- ✅ Compatible with multi-module builds
- ✅ Uses standard Maven Resolver (Aether) API
- ✅ Respects parent POM inheritance

## Future Enhancements

Potential improvements for future versions:
1. Export of POM parent relationships (not just `<modules>`)
2. Support for module aliases in CI/CD scenarios
3. Transitive module dependencies analysis
4. Module ownership and maintainer tracking
5. Module version alignment validation


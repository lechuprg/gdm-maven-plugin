# Neo4j Representation for Project Modules and Sub-modules

## 1. Overview

This document specifies the requirements for representing a multi-module Maven project's structure within the Neo4j graph database. The goal is to model the parent-child relationships between a project's root module and its sub-modules, as well as their dependencies on other modules.

### 1.1 Scope and Non-Goals

- This specification is **additive** to the existing Neo4j representation based on `MavenModule` nodes and `DEPENDS_ON` relationships.
- Existing labels, node properties, relationships, indexes, and constraints on `MavenModule` **MUST NOT** be changed or removed as part of implementing this spec.
- The `ProjectModule` layer provides a higher-level structural view of the same Maven modules (root and sub-modules). It does **not** replace the `MavenModule` representation or its dependency semantics.
- Consumers that only use `MavenModule` and `DEPENDS_ON` can continue to do so and may ignore `ProjectModule` entirely without any behavior change.

## 2. Requirements

### 2.1. Project Module Node

- A new node label, `ProjectModule`, shall be used to represent both the root project module and its sub-modules.
- Each `ProjectModule` node must have the following properties:
    - `groupId`: The effective `groupId` of the Maven module (after inheritance from parent POMs is applied).
    - `artifactId`: The `artifactId` of the Maven module.
    - `version`: The effective `version` of the Maven module (after inheritance from parent POMs is applied).
    - `isRootProject`: A boolean flag set to `true` for the main/root project module and `false` for all other modules in that project tree.
- For a given build import, there should be **at most one** `ProjectModule` node with `isRootProject = true` per logical project tree.
- A uniqueness constraint must be created for `ProjectModule` nodes based on `groupId`, `artifactId`, and `version`. This constraint is independent of any constraints on `MavenModule` nodes and MUST NOT modify them.

### 2.2. Parent-Child Relationships (Module Hierarchy)

- A `CONTAINS_MODULE` relationship shall be used to link a parent `ProjectModule` to its sub-modules.
- This relationship MUST be directed from the parent module to the sub-module.
- The canonical pattern is:
  - `(parent:ProjectModule)-[:CONTAINS_MODULE]->(child:ProjectModule)`
- For each `<module>` entry in a POM's `<modules>` section, there SHALL be a corresponding `CONTAINS_MODULE` relationship from the `ProjectModule` representing the parent POM to the `ProjectModule` representing the child module.
- Nested module structures SHALL be represented by chaining `CONTAINS_MODULE` relationships, for example:
  - `root` → `sub-module-a` → `sub-module-a-child` as
    - `(root)-[:CONTAINS_MODULE]->(subModuleA)`
    - `(subModuleA)-[:CONTAINS_MODULE]->(subModuleAChild)`
- The `CONTAINS_MODULE` relationships form a directed, acyclic tree (or forest when multiple projects are imported into the same database). Cycles in `CONTAINS_MODULE` are not allowed.

### 2.3. Linking to Maven Module Nodes

- Each `ProjectModule` node SHOULD be linked to its corresponding `MavenModule` node when such a node exists.
- A new relationship, `IS_A`, shall be used for this purpose.
- The direction of the relationship shall be from the `ProjectModule` to the `MavenModule`.
- The canonical pattern is:
  - `(proj:ProjectModule)-[:IS_A]->(mvn:MavenModule)`
- Matching rules:
  - A `ProjectModule` SHOULD be linked via `IS_A` to a `MavenModule` that has the **same** `groupId`, `artifactId`, and `version`.
  - If no corresponding `MavenModule` with matching coordinates exists, no `IS_A` relationship is created for that `ProjectModule`.
  - This spec does **not** change how `MavenModule` nodes or `DEPENDS_ON` relationships are created or updated; it only adds `IS_A` edges from `ProjectModule` to already-existing `MavenModule` nodes.
- This linkage allows leveraging the existing dependency graph of `MavenModule` nodes while querying through the higher-level `ProjectModule` structure.

## 3. Neo4j Implementation Details (Cypher)

### 3.1. Constraints

Create a uniqueness constraint for `ProjectModule` nodes.

```cypher
CREATE CONSTRAINT project_module_unique IF NOT EXISTS
FOR (p:ProjectModule)
REQUIRE (p.groupId, p.artifactId, p.version) IS UNIQUE;
```

This constraint applies **only** to `ProjectModule` nodes and does not affect any existing constraints or indexes on `MavenModule`.

### 3.2. Example Data Model (Flat Multi-Module Project)

Consider a project `my-project` with two direct sub-modules, `my-app` and `my-lib`.

**Nodes:**

- **Root Project:**
  ```json
  {
    "label": "ProjectModule",
    "properties": {
      "groupId": "com.example",
      "artifactId": "my-project",
      "version": "1.0.0-SNAPSHOT",
      "isRootProject": true
    }
  }
  ```
- **Sub-module `my-app`:**
  ```json
  {
    "label": "ProjectModule",
    "properties": {
      "groupId": "com.example",
      "artifactId": "my-app",
      "version": "1.0.0-SNAPSHOT",
      "isRootProject": false
    }
  }
  ```
- **Sub-module `my-lib`:**
  ```json
  {
    "label": "ProjectModule",
    "properties": {
      "groupId": "com.example",
      "artifactId": "my-lib",
      "version": "1.0.0-SNAPSHOT",
      "isRootProject": false
    }
  }
  ```

**Relationships:**

```cypher
// Find the project modules
MATCH (root:ProjectModule {artifactId: 'my-project'})
MATCH (app:ProjectModule {artifactId: 'my-app'})
MATCH (lib:ProjectModule {artifactId: 'my-lib'})

// Create parent-child relationships
MERGE (root)-[:CONTAINS_MODULE]->(app)
MERGE (root)-[:CONTAINS_MODULE]->(lib)

// Link ProjectModules to their corresponding MavenModule nodes
MATCH (rootProj:ProjectModule {groupId: 'com.example', artifactId: 'my-project', version: '1.0.0-SNAPSHOT'}),
      (rootMvn:MavenModule {groupId: 'com.example', artifactId: 'my-project', version: '1.0.0-SNAPSHOT'})
MERGE (rootProj)-[:IS_A]->(rootMvn)

MATCH (appProj:ProjectModule {groupId: 'com.example', artifactId: 'my-app', version: '1.0.0-SNAPSHOT'}),
      (appMvn:MavenModule {groupId: 'com.example', artifactId: 'my-app', version: '1.0.0-SNAPSHOT'})
MERGE (appProj)-[:IS_A]->(appMvn)

MATCH (libProj:ProjectModule {groupId: 'com.example', artifactId: 'my-lib', version: '1.0.0-SNAPSHOT'}),
      (libMvn:MavenModule {groupId: 'com.example', artifactId: 'my-lib', version: '1.0.0-SNAPSHOT'})
MERGE (libProj)-[:IS_A]->(libMvn)
```

### 3.3. Example Data Model (Nested Modules)

Consider a nested module structure where `my-app` itself contains a sub-module `my-app-child`.

**Additional Node:**

- **Nested sub-module `my-app-child`:**
  ```json
  {
    "label": "ProjectModule",
    "properties": {
      "groupId": "com.example",
      "artifactId": "my-app-child",
      "version": "1.0.0-SNAPSHOT",
      "isRootProject": false
    }
  }
  ```

**Additional Relationship:**

```cypher
MATCH (app:ProjectModule {groupId: 'com.example', artifactId: 'my-app', version: '1.0.0-SNAPSHOT'})
MATCH (child:ProjectModule {groupId: 'com.example', artifactId: 'my-app-child', version: '1.0.0-SNAPSHOT'})
MERGE (app)-[:CONTAINS_MODULE]->(child)
```

This demonstrates a multi-level containment tree using `CONTAINS_MODULE`.

## 4. Example Queries

### 4.1. Find all sub-modules of a project

```cypher
MATCH (root:ProjectModule {isRootProject: true, artifactId: 'my-project'})
MATCH (root)-[:CONTAINS_MODULE*]->(submodule)
RETURN submodule.artifactId, submodule.version;
```

This query returns all sub-modules at any depth below the root project.

### 4.2. Find all modules (root + sub-modules) of a project

```cypher
MATCH (root:ProjectModule {isRootProject: true, artifactId: 'my-project'})
OPTIONAL MATCH (root)-[:CONTAINS_MODULE*0..]->(p)
RETURN DISTINCT p.artifactId AS artifactId, p.version AS version;
```

This query returns the root project and all its modules by traversing `CONTAINS_MODULE` from the root.

### 4.3. Find all dependencies of a project and its sub-modules

This query finds all dependencies (both internal between sub-modules and external to other libraries) for a given project, by traversing through `ProjectModule` to `MavenModule` and then following existing `DEPENDS_ON` relationships.

```cypher
MATCH (root:ProjectModule {isRootProject: true, artifactId: 'my-project'})
// Get the root project and all its sub-modules
CALL {
    WITH root
    MATCH (root)-[:CONTAINS_MODULE*0..]->(p)
    RETURN DISTINCT p AS projectModule
}
// Link to the MavenModule representation (existing model)
MATCH (projectModule)-[:IS_A]->(sourceModule:MavenModule)
// Find all outgoing dependencies using the existing DEPENDS_ON edges
MATCH (sourceModule)-[d:DEPENDS_ON]->(targetModule:MavenModule)
RETURN sourceModule.artifactId AS source,
       d.scope AS scope,
       targetModule.groupId AS targetGroup,
       targetModule.artifactId AS targetArtifact,
       targetModule.version AS targetVersion;
```

## 5. Edge Cases and Special Considerations

- **Single-module projects:**
  - Implementations MAY create a `ProjectModule` node for single-module projects with `isRootProject = true` and no `CONTAINS_MODULE` relationships.
  - Clients SHOULD be prepared for both cases (presence or absence of `ProjectModule` for single-module builds).
- **Aggregator vs. leaf modules:**
  - Both aggregator (packaging `pom`) and leaf modules (e.g., `jar`, `war`) are represented as `ProjectModule` nodes. The distinction is inferred from existing `MavenModule` metadata and is out of scope for this spec.
- **Multiple versions of the same module:**
  - If multiple versions of the same `groupId`/`artifactId` are imported into the same Neo4j database, each version forms its own `ProjectModule` node (distinguished by `version`) and may belong to different project trees.
- **Missing MavenModule nodes:**
  - It is allowed for a `ProjectModule` to exist without a corresponding `MavenModule` node. In this case, no `IS_A` relationship is created, and existing dependency graphs remain unaffected.
- **Multiple roots / workspaces:**
  - A database MAY contain multiple independent root projects. Each such root project SHALL have its own `ProjectModule` with `isRootProject = true`, and its own containment tree.

## 6. Compatibility with Existing Maven Graph Model

- Existing `MavenModule` nodes and `DEPENDS_ON` relationships remain the **source of truth** for dependency information.
- Implementations of this spec MUST NOT rename, delete, or otherwise change existing `MavenModule` nodes or `DEPENDS_ON` relationships.
- The `ProjectModule` nodes, `CONTAINS_MODULE` relationships, and `IS_A` relationships are strictly additive and can be adopted incrementally by clients.
- Queries that previously operated only on `MavenModule` and `DEPENDS_ON` will continue to work unchanged; they do not need to be aware of `ProjectModule` to function correctly.

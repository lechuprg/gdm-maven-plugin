# GDM Maven Plugin - README

This Maven plugin exports project dependency graphs to a database (Neo4j or Oracle) for analysis.

## Features

- Export dependency graph with transitive dependencies.
- Control transitive dependency depth.
- Filter dependencies by groupId, artifactId, and scope.
- Detect and represent version conflicts.
- Automatically clean up old module versions.
- Supports Neo4j and Oracle databases.

## Quick Start

1.  **Add Plugin to `pom.xml`**

    Add the following to your `pom.xml`'s `<build><plugins>` section:

    ```xml
    <plugin>
        <groupId>org.example.gdm</groupId>
        <artifactId>gdm-maven-plugin</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <configuration>
            <!-- Required -->
            <databaseType>neo4j</databaseType>
            <connectionUrl>bolt://localhost:7687</connectionUrl>
            <username>neo4j</username>
            <password>your_password</password>
            
            <!-- Optional -->
            <transitiveDepth>2</transitiveDepth>
            <exportScopes>
                <scope>compile</scope>
                <scope>runtime</scope>
            </exportScopes>
            <keepOnlyLatestVersion>true</keepOnlyLatestVersion>
        </configuration>
    </plugin>
    ```

2.  **Run the Export**

    Execute the following command from your project's root directory:

    ```bash
    mvn gdm:export
    ```

## Configuration

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `databaseType` | String | **Yes** | - | `neo4j` or `oracle` |
| `connectionUrl` | String | **Yes** | - | Connection URL (e.g., `bolt://localhost:7687`) |
| `username` | String | **Yes** | - | Database username |
| `password` | String | **Yes** | - | Database password (plain or Maven encrypted) |
| `transitiveDepth` | Integer | No | `-1` | `-1`=unlimited, `0`=direct only, `N`=N levels |
| `exportScopes` | List | No | all | `compile`, `runtime`, `test`, `provided`, `system` |
| `includeFilters` | List | No | - | Include patterns (e.g., `com.company:*`) |
| `excludeFilters` | List | No | - | Exclude patterns (e.g., `*:*-test`) |
| `keepOnlyLatestVersion` | Boolean | No | `false` | Delete old versions after export |
| `failOnError` | Boolean | No | `false` | Fail build on export error |
| `serverId` | String | No | - | Server ID from `settings.xml` for credentials |
| `nodeLabel` | String | No | `ProjectModule` | Custom node label for project modules in Neo4j. The node will have a `ProjectModule: true` property. |

### Filter Patterns

Filters use a glob-style pattern `groupId:artifactId`:
- `*`: matches zero or more characters.
- `?`: matches exactly one character.

**Examples:**
- `org.springframework:*`
- `*:junit`
- `com.company:my-?-app`

## Database Schema

The required database schema is defined in `database-schema.sql`. You must create the schema before running the plugin for the first time.

### Neo4j Model
- **Nodes**: `(:MavenModule {groupId, artifactId, version, ...})`
- **Project Module Nodes**: `(:<nodeLabel> {groupId, artifactId, version, ProjectModule: true, ...})` - The label is configurable via `nodeLabel` parameter (default: `ProjectModule`). All project module nodes have a `ProjectModule: true` property for easy identification.
- **Relationships**: `-[:DEPENDS_ON {scope, depth, isResolved, ...}]->`, `-[:CONTAINS_MODULE]->`

### Oracle Model
- **Tables**: `maven_modules`, `maven_dependencies`, `gdm_schema_version`

## Building from Source

To build the plugin from source, run:
```bash
mvn clean install
```
This will compile the code, run all unit and integration tests, and install the plugin in your local Maven repository.


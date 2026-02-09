# GDM Maven Plugin - Architecture Design Document

**Version:** 1.0.0  
**Date:** 2026-02-09  
**Status:** Final Design

---

## 1. Executive Summary

GDM (Graph Dependency Maven) Plugin to wtyczka Maven do eksportowania grafu zależności projektów Maven do baz danych (Neo4j, Oracle). Plugin umożliwia:

- Eksport dependency graph z pełną kontrolą nad transitive dependencies
- Filtrowanie dependencies na podstawie groupId i artifactId
- Zarządzanie wersjami z polityka nadpisywania lub wersjonowania
- Automatyczne usuwanie starych wersji
- Śledzenie konfliktów wersji

---

## 2. Kluczowe Decyzje Projektowe

### 2.1 Konfiguracja Bazy Danych

- **URL i credentials** umieszczane w `pom.xml`
- **Schemat bazy** musi być utworzony wcześniej (poza pluginem)
- **Schemat dostępny** w osobnym pliku dokumentacji (`database-schema.sql`)
- Plugin NIE tworzy automatycznie tabel

### 2.2 Identyfikacja Modułów

- Identyfikacja przez: **`groupId:artifactId`**
- Różne wersje tego samego modułu to osobne wpisy w bazie
- Multi-module projects: każdy moduł eksportuje swoje dependencies osobno

### 2.3 Wielokrotne Buildy

- Ta sama wersja (GAV) budowana wielokrotnie → **OVERWRITE** (nadpisanie)
- Ostatni build ma zawsze najnowsze dane
- Stare dependencies są usuwane, nowe są dodawane

### 2.4 Transitive Dependencies - Głębokość

```
Project A
├── B:1.0 (depth=0) ← direct dependency
│   ├── D:1.0 (depth=1) ← transitive
│   │   └── F:1.0 (depth=2) ← transitive
│   └── E:1.0 (depth=1) ← transitive
└── C:1.0 (depth=0) ← direct dependency
```

- **Depth 0**: tylko direct dependencies
- **Depth 1**: direct + ich dependencies
- **Depth N**: N poziomów transitive
- **Depth -1**: unlimited (cały graf)

### 2.5 Filtry Zależności

**Format Maven pattern:**
```
groupId:artifactId
```

**Przykłady:**
```
org.springframework.*              ← cała grupa
org.springframework:spring-core    ← konkretny artifact
com.company:*                      ← wszystkie z grupy
*:spring-*                         ← pattern na artifactId
```

**Logika aplikacji filtrów:**
1. Jeśli dependency pasuje do **exclude** → odrzuć
2. Jeśli są **include filters** → dependency musi pasować do przynajmniej jednego
3. W przeciwnym razie → include

### 2.6 Konflikty Wersji

Gdy Maven resolve'uje konflikt (np. A→B:2.0, ale C→B:3.0):
- Eksportujemy **obie wersje**
- Oznaczamy która jest faktycznie używana (`is_resolved=true`)
- Pokazujemy konflikt w bazie danych

### 2.7 Zarządzanie Wersjami

- **Cleanup granularity**: per moduł (groupId:artifactId)
- **Określanie "najnowszej"**: po numerze wersji (Maven ComparableVersion)
- **Akcja przy usuwaniu**: pełne usunięcie (CASCADE delete)
- **Brak archiwizacji**: nie przechowujemy historycznych danych

### 2.8 Scope Dependencies

**Domyślnie eksportujemy wszystkie scopy:**
- compile
- runtime
- test
- provided
- system

**Użytkownik może wybrać tylko konkretne scopy** przez konfigurację.

**Pomijamy:**
- `import` (tylko w dependencyManagement)
- optional dependencies są eksportowane razem z main dependency

### 2.9 Parent POM

- Eksportujemy jako normalny moduł (packaging=pom)
- Zaznaczamy w bazie że to pom packaging
- Jego dependencies są traktowane normalnie

### 2.10 Inter-Module Dependencies

W multi-module projects:
- Każdy moduł eksportuje swoje dependencies
- Jeśli module-A zależy od module-B z tego samego projektu → eksportujemy jako normalną dependency
- Brak specjalnych oznaczeń

### 2.11 Execution

- **Brak automatycznego bindowania** do fazy Maven lifecycle
- **Ręczne wywołanie**: `mvn gdm:export`
- **Wszystkie parametry** przez `pom.xml`
- Brak `mvn gdm:test-connection`

### 2.12 Metadane Exportu

Eksportujemy tylko:
- **GAV**: groupId, artifactId, version
- **Timestamp**: data/czas exportu
- **Packaging**: typ (jar, war, pom, itd.)

### 2.13 Password Management

- **Domyślnie**: plain text w pom.xml
- **Opcjonalnie**: Maven password encryption (user konfiguruje zgodnie z dokumentacją)
- Plugin automatycznie dekryptuje jeśli użytkownik zastosował encryption

### 2.14 Obsługiwane Bazy Danych

- **MVP**: Neo4j (default)
- **Later**: Oracle
- **Brak**: MongoDB, PostgreSQL, MySQL

### 2.15 Batch Processing

- **Batch size**: 500 records na insert
- **Jednostka**: 500 dependency records
- **Transaction**: jedna transakcja per cały export (all-or-nothing)
- **Rollback**: całkowity rollback jeśli któryś batch failuje

### 2.16 Error Handling

- **failOnError=false** (domyślnie): tylko warning w logu, build continues
- **Retry logic**: 3 próby na connection errors z backoff 2s
- **Scope**: retry tylko dla connection issues, nie dla query errors

### 2.17 Logging

```
INFO:  - Start export
       - End export + statistics
       - Number of modules, dependencies processed
       
DEBUG: - Each dependency being processed
       - Filter evaluation
       - Database operations
       
WARN:  - Dependencies filtered out
       - Version conflicts detected
       - Older versions being deleted
       
ERROR: - Connection failures
       - Database errors
       - Configuration errors
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
```

Plugin sprawdza version i ostrzega o incompatibility jeśli mismatch.

---

## 3. Architektura Wysokopoziomowa

```
┌─────────────────────────────────┐
│  Maven Build (mvn gdm:export)   │
└────────────┬────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────┐
│           ExportDependenciesMojo                    │
│  - Parse configuration from pom.xml                 │
│  - Setup database connection                        │
└────────────┬────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────┐
│       DependencyResolver                            │
│  - Resolve Maven dependencies                       │
│  - Apply filters                                    │
│  - Build dependency graph with depth control       │
└────────────┬────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────┐
│       DependencyGraph                               │
│  - MavenModule nodes                                │
│  - Dependency relationships                         │
│  - Conflict information                             │
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

## 4. Komponenty Systemu

### 4.1 Plugin Execution Layer

**Klasy:**
- `ExportDependenciesMojo` - główny goal
- `PluginConfiguration` - konfiguracja z pom.xml

**Odpowiedzialności:**
- Parsowanie konfiguracji
- Zarządzanie lifecycle'em exportu
- Error handling i logging

### 4.2 Dependency Resolution Engine

**Klasy:**
- `DependencyResolver` - interfejs
- `MavenDependencyResolver` - implementacja z Maven API
- `DependencyTreeBuilder` - budowanie drzewa
- `TransitiveDepthController` - kontrola głębokości

**Funkcjonalność:**
- Resolve direct dependencies
- Rekurencyjne resolve transitive z kontrolą głębokości
- Detencja konfliktów wersji

### 4.3 Filter Engine

**Klasy:**
- `FilterEngine` - orkestracja
- `PatternFilter` - pattern matching Maven style
- `ScopeFilter` - filtrowanie po scope

**Funkcjonalność:**
- Include/exclude filtering
- Pattern matching na groupId i artifactId
- Scope filtering

### 4.4 Data Model

**Klasy:**
- `MavenModule` - moduł/artifact
- `Dependency` - relacja między modułami
- `DependencyGraph` - kompletny graf
- `ExportMetadata` - metadane exportu

### 4.5 Database Exporters

**Interfejs:**
- `DatabaseExporter` - abstrakacja

**Implementacje:**
- `Neo4jExporter` - export do Neo4j
- `OracleExporter` - export do Oracle

**Wspólne funkcjonalności:**
- Connection management
- Transaction handling
- Batch processing (500 records)
- Version conflict resolution
- Cleanup old versions

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
    depth: Integer,             // 0 = direct, 1+ = transitive
    isResolved: Boolean,        // true = faktycznie użyta, false = konflikt
    exportTimestamp: DateTime
}]->(target:MavenModule)
```

### Constraints & Indexes

```cypher
CREATE CONSTRAINT module_unique IF NOT EXISTS
FOR (m:MavenModule)
REQUIRE (m.groupId, m.artifactId, m.version) IS UNIQUE;

CREATE INDEX module_ga IF NOT EXISTS
FOR (m:MavenModule) ON (m.groupId, m.artifactId);

CREATE INDEX module_latest IF NOT EXISTS
FOR (m:MavenModule) ON (m.isLatest);
```

---

## 6. Oracle Schema

```sql
CREATE TABLE maven_modules (
    id                  NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id            VARCHAR2(255) NOT NULL,
    artifact_id         VARCHAR2(255) NOT NULL,
    version             VARCHAR2(100) NOT NULL,
    packaging           VARCHAR2(50),
    export_timestamp    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_latest           NUMBER(1) DEFAULT 1,
    CONSTRAINT uk_maven_module UNIQUE (group_id, artifact_id, version)
);

CREATE TABLE maven_dependencies (
    id                  NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_module_id    NUMBER NOT NULL,
    target_module_id    NUMBER NOT NULL,
    scope               VARCHAR2(20),
    optional            NUMBER(1) DEFAULT 0,
    depth               NUMBER(2),
    is_resolved         NUMBER(1) DEFAULT 1,
    export_timestamp    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dep_source 
        FOREIGN KEY (source_module_id) 
        REFERENCES maven_modules(id) ON DELETE CASCADE,
    CONSTRAINT fk_dep_target 
        FOREIGN KEY (target_module_id) 
        REFERENCES maven_modules(id) ON DELETE CASCADE,
    CONSTRAINT uk_dependency 
        UNIQUE (source_module_id, target_module_id, scope, is_resolved)
);

CREATE TABLE gdm_schema_version (
    version             VARCHAR2(20) PRIMARY KEY,
    applied_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_module_ga ON maven_modules(group_id, artifact_id);
CREATE INDEX idx_dep_source ON maven_dependencies(source_module_id);
CREATE INDEX idx_dep_target ON maven_dependencies(target_module_id);
```

---

## 7. Flow Exportu

```
1. Maven executes: mvn gdm:export
                    │
2. ExportDependenciesMojo starts
   - Load configuration from pom.xml
   - Resolve database connection
                    │
3. DependencyResolver resolves dependencies
   - Get Maven project
   - Resolve direct dependencies
   - For each: recursively resolve transitive up to depth limit
   - Apply filters (include/exclude)
   - Build complete graph
                    │
4. DatabaseExporter exports graph
   - Check schema version compatibility
   - Start transaction
   - Batch process modules (500 per batch)
   - Batch process dependencies (500 per batch)
   - Detect version conflicts
   - Mark old versions as not latest
   - Commit transaction
                    │
5. Cleanup (if configured)
   - Delete old versions (cascade)
   - Log cleanup stats
                    │
6. Report results
   - Log statistics
   - Print export summary
```

---

## 8. Version Conflict Handling

**Scenariusz:**
```
Project A
├── B:1.0 ──> C:2.0
└── D:1.0 ──> C:3.0
```

Maven resolve'uje konflikt i wybiera C:3.0 (nearest wins).

**Co eksportujemy:**
- C:2.0 node (is_resolved=false) - to was requested
- C:3.0 node (is_resolved=true) - to is used
- Obie relationships (A→B→C:2.0, A→D→C:3.0)
- Dodatkowo: A→C:3.0 (resolved version)

**W bazie:**
```sql
-- C:2.0 entry
INSERT INTO maven_dependencies (source_module_id, target_module_id, is_resolved)
VALUES ((SELECT id FROM maven_modules WHERE ga='B:1.0'), 
        (SELECT id FROM maven_modules WHERE ga='C:2.0'), 
        0);  -- not resolved

-- C:3.0 entry (resolved)
INSERT INTO maven_dependencies (source_module_id, target_module_id, is_resolved)
VALUES ((SELECT id FROM maven_modules WHERE ga='D:1.0'), 
        (SELECT id FROM maven_modules WHERE ga='C:3.0'), 
        1);  -- resolved
```

---

## 9. Cleanup Strategy

**When:** Jeśli konfiguracja zawiera `keepOnlyLatestVersion=true`

**Process:**
1. Identyfikuj wszystkie wersje modułu (groupId:artifactId)
2. Posortuj po numerze wersji (Maven ComparableVersion)
3. Oznacz wszystkie poza ostatnią jako `is_latest=0`
4. Usuń wszystkie non-latest wersje (CASCADE)

**SQL:**
```sql
-- Mark old versions
UPDATE maven_modules
SET is_latest = 0
WHERE group_id = 'com.company' AND artifact_id = 'my-app'
  AND version <> '1.1.0';

-- Delete old versions (CASCADE deletes dependencies)
DELETE FROM maven_modules
WHERE group_id = 'com.company' AND artifact_id = 'my-app'
  AND is_latest = 0;
```

---

## 10. Error Handling & Resilience

### Connection Errors

- **Retry:** 3 próby
- **Backoff:** 2 seconds między próbami
- **Scope:** connection errors only
- **Action:** 
  - failOnError=false → log ERROR, continue
  - failOnError=true → throw exception, fail build

### Transactional Errors

- Całe eksportowanie w jednej transakcji
- Jeśli fail w połowie → ROLLBACK
- Brak częściowych zmian w bazie

### Validation

- Sprawdzenie schema version przy starcie
- Warning jeśli mismatch
- Continue / stop (depending on failOnError)

---

## 11. Performance Considerations

### Batch Processing

- **Batch size:** 500 records per batch
- **Target:** <5s dla <100 deps, <30s dla <1000 deps
- **Optimization:** Use bulk inserts, defer constraints check

### Connection Pooling

- Dla Oracle: HikariCP z default settings
- Reuse connection across batches

### Memory Management

- Stream processing dependencies
- Nie trzymaj całego grafu w pamięci dla huge projects

---

## 12. Multi-Module Projects

**Przykład:**
```
parent-pom (packaging=pom)
├── module-a (packaging=jar)
│   └── depends on: spring-core:5.0
├── module-b (packaging=jar)
│   └── depends on: module-a
└── module-c (packaging=jar)
    └── depends on: module-b
```

**Eksport:**
1. parent-pom eksportuje się (brak dependencies zazwyczaj)
2. module-a eksportuje swoje deps + internal dep na parent
3. module-b eksportuje swoje deps + internal dep na parent + dep na module-a
4. module-c eksportuje swoje deps + internal dep na parent + dep na module-b

**Relacje w bazie:**
```
module-a -> spring-core:5.0
module-b -> module-a
module-b -> spring-core:5.0 (transitive z module-a)
module-c -> module-b
module-c -> module-a (transitive z module-b)
module-c -> spring-core:5.0 (transitive z module-b→module-a)
```

---

## 13. Security Notes

### Credentials

- Stored in pom.xml (plain or encrypted with Maven encryption)
- Use Maven password encryption for production
- Environment variables NOT supported in MVP

### Database

- Recommend: use secure DB connection
- Recommend: restrict access to database user
- Use exclude filters for sensitive internal dependencies

---

## 14. Future Enhancements

- **v1.1:** Oracle support
- **v1.2:** License tracking
- **v1.3:** Vulnerability scanning integration
- **v2.0:** REST API, GraphQL API, Web UI

---

**End of Architecture Document**


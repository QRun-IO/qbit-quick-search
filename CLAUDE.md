# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## Project Overview

**QBit Quick Search** - A QQQ QBit providing OpenSearch-backed full-text search across QQQ applications.

## Build Commands

```bash
mvn compile          # Compile
mvn test             # Run unit tests
mvn test -Dtest=ClassName  # Run single test class
mvn package          # Build JAR
mvn verify           # Full verify, including integration tests
mvn verify -Pqqq-snapshot  # Verify against 4.1.0-SNAPSHOT (override with -Dqqq.snapshot.version)
```

Integration tests (require Docker) run via `mvn verify` using maven-failsafe-plugin.
Unit tests exclude `*IntegrationTest.java` and `*IT.java`.
Integration tests use `@ExtendWith(RequiresDockerCondition.class)`: skipped without Docker locally, failed when `CI=true`.

## Architecture

**Entry point:** `QuickSearchQBitProducer.produce(QInstance)` validates config, discovers annotated entity classes, initializes the singleton `QuickSearchOpenSearchClient`, creates an `IndexEventPublisher`, registers all metadata into the `QInstance`, and attaches customizers to source tables.

**Key Classes:**
- `QuickSearchQBitProducer` - Orchestrator: produces tables, processes, app, and registers customizers
- `QuickSearchQBitConfig` - Configuration (backend name, OpenSearch connection, feature flags)
- `QuickSearchQBitContext` - Static singleton holding the live config, client, publisher, and discovered table configs
- `QuickSearchOpenSearchClient` - All OpenSearch operations (ensure index, bulk index, delete, search)
- `QuickSearchableTableConfig` - Runtime config for a single indexed table (fields, weights, basepull settings)

**Customizers** (`customizers/` package, all three registered when `enableRealTimeIndexing = true`):
- `QuickSearchPostInsertCustomizer` - Fires after insert, publishes index events
- `QuickSearchPostUpdateCustomizer` - Fires after update, re-reads the full records from the source table, publishes index events
- `QuickSearchPostDeleteCustomizer` - Fires after delete, publishes delete events

**Processes** (`processes/` package):
- `AbstractIndexingStep` - Base class with shared indexing logic (query source table, build documents, publish events, update run record)
- `BasepullIndexStep` - Queries records modified since last run timestamp
- `FullReindexStep` - Wipes and rebuilds the index for one or all tables
- `ReconcileIndexStep` - Re-indexes each table in primary-key order, then removes documents with no source record (no wipe, so no search blackout)
- `IndexingUtils` - Converts `QRecord` values to indexed text using field configs

**Publisher** (`publisher/` package):
- `IndexEventPublisher` - Strategy interface with `publishIndexEvents()`, `publishDeleteEvents()`, `close()`
- `SynchronousIndexEventPublisher` - Default implementation; processes events immediately via the OpenSearch client
- `IndexEvent` / `IndexEventAction` - Event model

**Actions** (`actions/` package):
- `QuickSearchAction` - Executes a paginated multi-match search across all indexed fields with per-field boosting
- `QuickSearchInput` / `QuickSearchOutput` / `QuickSearchResult` - Search API model

**Tables Produced:**
- `quickSearchIndex` - One row per indexed table; tracks enabled state, basepull interval, last run times
- `quickSearchIndexRun` - Run history with status, record counts, and error messages

**Annotation Discovery:**
- `@QuickSearchable(tableName = "...")` - Mark entity classes; defines basepull settings
- `@QuickSearchField(weight, includeLabel)` - Mark fields to include in indexed text; takes precedence over `fields[]` array

## Code Style

- 3-space indentation, opening braces on next line
- Javadoc flower box comments (80-char width with `**` borders)
- Fluent-style setters (`withX()`)
- Use wrapper types (`Integer`, `Boolean`) not primitives

## Dependencies

- Java 21
- Parent `com.kingsrook:qbit-build-parent:2.0.0`, the only source of the qqq version (QQQ 4.0.0); do not re-import `qqq-bom-pom` here (ADR-0007), except in the opt-in `qqq-snapshot` profile (`-Pqqq-snapshot`), which imports `qqq-bom-pom:${qqq.snapshot.version}` (default 4.1.0-SNAPSHOT) and adds the Central snapshots repository
- OpenSearch Java Client 2.10.0
- Apache HttpClient5 and jackson-datatype-jsr310, versions managed by the qqq BOM (keep them unpinned so they match its httpcore5 and jackson)
- JUnit 5 + AssertJ + Mockito for testing
- Testcontainers 1.21.4 for integration tests (requires Docker)

## Knowledge base

Second-brain vault notes covering this repo and the QQQ platform it plugs into:

- Platform hub: `$SECOND_BRAIN_VAULT/knowledge/qqq/qqq-hub.md` (start here; read `knowledge/qqq/architecture/metadata-model.md` for QBit mechanics)
- This repo's dossier: `$SECOND_BRAIN_VAULT/knowledge/qqq/repos/qbit-quick-search.md` (reviewed at develop @ `8d9ec2711fde`, 2026-07-04)
- Production-readiness audit + P0-P3 roadmap: `$SECOND_BRAIN_VAULT/projects/qbit-quick-search.md`

Note: the dossier was reviewed before the qqq 4.0 re-pin; trust this file for build and dependency facts.

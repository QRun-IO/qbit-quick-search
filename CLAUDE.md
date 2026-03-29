# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## Project Overview

**QBit Quick Search** - A QQQ QBit providing OpenSearch-backed full-text search across QQQ applications. Current version: 0.1.0.

## Build Commands

```bash
mvn compile          # Compile
mvn test             # Run all tests (174 tests)
mvn test -Dtest=ClassName  # Run single test class
mvn package          # Build JAR
mvn verify           # Full verify with coverage check
```

Integration tests (require Docker) run via `mvn verify` using maven-failsafe-plugin.
Unit tests exclude `*IntegrationTest.java` and `*IT.java`.

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
- `QuickSearchPostUpdateCustomizer` - Fires after update, publishes index events
- `QuickSearchPostDeleteCustomizer` - Fires after delete, publishes delete events

**Processes** (`processes/` package):
- `AbstractIndexingStep` - Base class with shared indexing logic (query source table, build documents, publish events, update run record)
- `BasepullIndexStep` - Queries records modified since last run timestamp
- `FullReindexStep` - Wipes and rebuilds the index for one or all tables
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
- QQQ Backend Core 0.40.0-SNAPSHOT (via `qqq-bom-pom`)
- OpenSearch Java Client 2.10.0
- Apache HttpClient5 5.3
- jackson-datatype-jsr310 2.21.0 (Instant serialization for OpenSearch client)
- JUnit 5 + AssertJ + Mockito for testing
- Testcontainers for integration tests (requires Docker)

# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## Project Overview

**QBit Quick Search** - A QQQ QBit providing global search across QQQ applications via OpenSearch. Version 0.1.0 has been released.

## Build Commands

```bash
mvn compile          # Compile
mvn test             # Run all tests (95 tests)
mvn test -Dtest=ClassName  # Run single test class
mvn package          # Build JAR
mvn verify           # Full verify with coverage check
```

## Architecture

**Key Classes:**
- `QuickSearchQBitProducer` - Main producer, registers tables/processes/app
- `QuickSearchQBitConfig` - Configuration (backend, OpenSearch connection)
- `QuickSearchOpenSearchClient` - OpenSearch operations wrapper
- `FullReindexStep` / `BasepullIndexStep` - Indexing process steps
- `QuickSearchAction` - Search API endpoint
- `QuickSearchDeleteCustomizer` - Auto-removes docs on record delete

**Tables Produced:**
- `quickSearchIndex` - Config/status per indexed table
- `quickSearchIndexRun` - Indexing run history

**Annotation Discovery:**
- `@QuickSearchable(tableName = "...")` - Mark entity classes
- `@QuickSearchField` - Mark fields to include in search text

## Code Style

- 3-space indentation, opening braces on next line
- Javadoc flower box comments (80-char width with `**` borders)
- Fluent-style setters (`withX()`)
- Use wrapper types (`Integer`, `Boolean`) not primitives

## Dependencies

- Java 21, QQQ Backend Core 0.35.0
- OpenSearch Java Client 2.10.0
- JUnit 5 + AssertJ + Mockito for testing
- Testcontainers for integration tests

## Current Status

**v0.1.0 Released** - Initial release with:
- OpenSearch indexing (full reindex + basepull)
- Annotation-driven table discovery
- Search API via QuickSearchAction
- Delete customizer for sync

**Pending Work** (see `./docs/TODO.md`):
- README documentation with examples
- UI status widget
- Table actions for manual reindex
- E2E verification with real QQQ app

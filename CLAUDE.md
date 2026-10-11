# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## Project Overview

**QBit Quick Search** - A QQQ QBit providing OpenSearch-backed full-text search across QQQ applications.

## Build Commands

```bash
./mvnw compile          # Compile (always use the wrapper: Maven 3.10 cannot read the parent POM)
./mvnw test             # Run unit tests
./mvnw test -Dtest=ClassName  # Run single test class
./mvnw package          # Build JAR
CI=true ./mvnw verify   # Full verify: integration tests (Docker) and the JaCoCo gate
./mvnw verify -Dopensearch.test.image=opensearchproject/opensearch:3.9.0   # other server line
./mvnw verify -Dqqq.snapshot.version=4.1.0-RC.1   # other qqq 4.1 build (profile is active by default)
```

Integration tests (require Docker) run via `mvn verify` using maven-failsafe-plugin.
Unit tests exclude `*IntegrationTest.java` and `*IT.java`.
Integration tests use `@ExtendWith(RequiresDockerCondition.class)`: skipped without Docker locally, failed when `CI=true`.

## Architecture

**Entry point:** `QuickSearchQBitProducer implements QBitMetaDataProducer<QuickSearchQBitConfig>`. QQQ's default `produce()` registers `QBitMetaData` (`com.kingsrook.qbits:quick-search`), validates the config, runs the component producers in `metadata/` (tables, processes, PVS, app), then `postProduceActions` discovers searchable tables, builds the `QuickSearchRuntime`, registers `QuickSearchRecordChangeListener` and `QuickSearchRuntimeService`, and in `FAIL_FAST` mode prepares the index. `produce(QInstance)` adds everything to the instance (0.x call style still works).

**Key Classes:**
- `QuickSearchQBitConfig` - Host-facing config: connection (`opensearchUrl`), `authMode`, `tls`, timeouts, startup mode, scheduling, permission rules, search bounds, tables. List-based, null-safe `validate`.
- `QuickSearchRuntime` - Instance-scoped live state (discovered tables, lazy client, publisher, enabled cache), held on the config and resolved via `QuickSearchRuntime.get()` from `QContext`. `QuickSearchQBitContext` is a deprecated static facade over it.
- `opensearch/QuickSearchOpenSearchClient` - All OpenSearch operations; the configured index name is an alias over `{alias}-v2-{epoch}` physical indexes (mapping v2, `_meta.quickSearchMappingVersion`); versioned bulk writes (`external_gte`); alias swap; wraps every error in `QException`.
- `opensearch/OpenSearchTransportFactory` - Builds the transport for `NONE`/`BASIC` (Apache HttpClient 5 with TLS, timeouts, customizer SPI) or `AWS_SIGV4` (`AwsSdk2Transport`); resolves `${env.X}` secrets.
- `listeners/QuickSearchRecordChangeListener` - Real-time path: builds events in the write's transaction, publishes after commit, records failures in `quickSearchFailedEvent`.
- `processes/AbstractIndexingStep` - Keyset paging by primary key with display values, run records, drift detection, run purge. `BasepullIndexStep` (replays failed events, start-anchored watermark with overlap), `ReconcileIndexStep` (in place, orphan sweep), `FullReindexStep` (all tables: new physical index plus alias swap; one table: reconcile algorithm).
- `actions/QuickSearchAction` - Permission-aware search (table READ filter, `QueryAction` post-filter), bounds, `tableNames`, `limitPerTable`, `tableLabel`.
- `actions/QuickSearchRecordSearchProvider` - QQQ core `RecordSearchProviderInterface` (`POST /qqq/v1/search`): claims indexed, enabled tables; one `_msearch` per call returning ordered record ids per table; bounded by `recordSearchTimeoutMillis`; failures make core fall back and start a 30 s backoff (`QuickSearchRuntime.markRecordSearchFailed`). Registered, with default table `searchFields`, when `serveCoreRecordSearch` is true.
- `metadata/*` - Component producers and the table/process helpers.

**Tables Produced:** `quickSearchIndex`, `quickSearchIndexRun`, `quickSearchFailedEvent` (plus a PVS over `quickSearchIndex`).

**Annotation Discovery:** `@QuickSearchable(tableName = "...")` on entity classes; `@QuickSearchField(weight, includeLabel)` on fields; hidden fields are excluded.

## Code Style

- 3-space indentation, opening braces on next line
- Javadoc flower box comments (80-char width with `**` borders)
- Fluent-style setters (`withX()`)
- Use wrapper types (`Integer`, `Boolean`) not primitives

## Dependencies

- Java 21
- Parent `com.kingsrook:qbit-build-parent:2.0.0` (qqq 4.0.0). 1.0 needs qqq 4.1, so the `qqq-snapshot` profile is active by default and imports `qqq-bom-pom` ahead of the parent's BOM; remove that activation when the parent moves to 4.1 (ADR-0007). 1.1 temporarily imports `4.1.0-SNAPSHOT` for the record-search provider SPI (QRun-IO/qqq#1042); re-pin to the QQQ release that carries it before releasing 1.1.0
- OpenSearch Java Client 3.10.0; optional AWS SDK v2 `apache-client` and `sts` for `AWS_SIGV4`
- Apache HttpClient5 and jackson-datatype-jsr310, versions managed by the qqq BOM (keep them unpinned so they match its httpcore5 and jackson)
- JUnit 5 + AssertJ + Mockito for testing
- Testcontainers 1.21.4 for integration tests (requires Docker)

## Knowledge base

Second-brain vault notes covering this repo and the QQQ platform it plugs into:

- Platform hub: `$SECOND_BRAIN_VAULT/knowledge/qqq/qqq-hub.md` (start here; read `knowledge/qqq/architecture/metadata-model.md` for QBit mechanics)
- This repo's dossier: `$SECOND_BRAIN_VAULT/knowledge/qqq/repos/qbit-quick-search.md` (reviewed at develop @ `8d9ec2711fde`, 2026-07-04)
- Production-readiness audit + P0-P3 roadmap: `$SECOND_BRAIN_VAULT/projects/qbit-quick-search.md`

Note: the dossier was reviewed before the qqq 4.0 re-pin; trust this file for build and dependency facts.

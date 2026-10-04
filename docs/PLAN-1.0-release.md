# PLAN: Quick Search 1.0 release readiness

> Executed natively in-session, task by task, test-first, one commit per task on `feature/GH-1-0-release-readiness`. Spec: `docs/initiatives/quick-search-1.0.md` (PRD) and `docs/audits/2026-10-04/README.md` (blockers B1 to B25). Decisions D1 to D11 are resolved with the PRD's recommendations unless noted under "Decisions applied".

## Goal

Close every audit blocker and ship a QBit that is a standard QQQ 4.1 citizen, keeps a trustworthy index, searches safely, connects to real OpenSearch deployments, and documents all of it.

## Decisions applied

| ID | Resolution used here |
|---|---|
| D1 | API-only in 1.0; `QuickSearchResult` gains `tableLabel`, input gains `tableNames` and `limitPerTable`; the core SPI is proposed separately (TODO) |
| D2 | 1.0.0-SNAPSHOT on this branch; 0.3.0 compat release remains the qqq#921 chain's job |
| D3 | Table-level READ filter plus `QueryAction` post-filter by primary key (`applyRecordSecurityLocks`, default true) |
| D4 | `schedulerName` null leaves processes unscheduled with a startup WARN (not an error), so qqq-all keeps booting; set it to schedule |
| D5 | Full reindex of all tables builds a fresh physical index and swaps the alias; a single-table full reindex uses the reconcile algorithm (index in place, then delete stale) |
| D6 | `authMode` `AWS_SIGV4` included with optional AWS SDK v2 dependencies |
| D7 | `RecordChangeListenerInterface` plus `addAfterCommitCallback` |
| D8 | Listener never fails the write; failures go to `quickSearchFailedEvent`, replayed by the scheduled basepull |
| D9 | Supported platforms statement as in the audit |
| D10 | 4.1-only: the build uses qqq 4.1.0-RC.1 through the (now default-active) `qqq-snapshot` profile until parent 2.1.0 ships; customizers are deleted |
| D11 | `startupMode` default `FAIL_FAST`; `DEGRADED` available |

## Architecture

- `QuickSearchQBitProducer implements QBitMetaDataProducer<QuickSearchQBitConfig>`; component producers in the package build the two tables, three processes, failed-event table, table PVS and app; `postProduceActions` registers the record-change listener and the runtime service and resolves discovered tables.
- `QuickSearchRuntime` (instance-scoped, held `transient` on the config, resolved through `QContext.getQInstance().getQBits()`) owns discovered tables, the lazily built client, the publisher, the enabled-table cache and lifecycle. `QuickSearchQBitContext` stays as a deprecated static facade delegating to it (tests keep working).
- `QuickSearchRecordChangeListener` replaces the three customizers; publishes after commit when a transaction is present; records failures in `quickSearchFailedEvent`.
- `QuickSearchOpenSearchClient` gains a transport factory (`NONE`, `BASIC`, `AWS_SIGV4`, TLS block, timeouts), mapping v2 (search analyzer, dynamic templates, `_meta` version), alias management, health, and wraps `OpenSearchException`.
- Indexing steps page by primary key, anchor the basepull watermark at run start, honour `enabled`, sweep orphans, detect drift, purge run history, replay failed events, and use `external_gte` versioning from the timestamp field.
- `QuickSearchAction` filters tables by permission and enabled state, bounds inputs, supports `tableNames` and `limitPerTable`, post-filters through `QueryAction`, returns `tableLabel`.

## Tasks (each ends with green `mvn test`, then a commit)

1. Build: default-active `qqq-snapshot` profile on `4.1.0-RC.1`, `revision` 1.0.0-SNAPSHOT, opensearch-java 3.10.0 with the `Refresh.True` fix, JaCoCo `prepare-agent` with explicit version, drop JUnit 5.10.0 pins (fallback: keep if JUnit 6 breaks a dependency). Files: `pom.xml`, `QuickSearchOpenSearchClient.java:427`.
2. Config surface and runtime holder: new fields (`startupMode`, `schedulerName`, `basepullRepeatSeconds`, `reconcileCronExpression`, `reconcileCronTimeZoneId`, `enableBasepullProcess`, `enableMaintenanceProcesses`, `adminPermissionRules`, `tablePermissionRules`, `applyRecordSecurityLocks`, `maxSearchLimit`, `basepullOverlapSeconds`, `runHistoryRetentionDays`, `maxFieldLength`, `opensearchUrl`, `authMode`, `awsRegion`, `awsServiceName`, `awsAssumeRoleArn`, `allowPlaintextCredentials`, `tls*`, `connectTimeoutMillis`, `responseTimeoutMillis`, `maxConnections`, `transportCustomizer`), deprecated `enableScheduledProcesses` alias, null-safe validation with field-existence checks, `getDefaultBackendNameForTables`, settable `tableMetaDataCustomizer`, `@JsonIgnore` secret getters, redacting `toString`, `transient` publisher. `QuickSearchRuntime` class; `QuickSearchQBitContext` facade. Tests: `QuickSearchQBitConfigTest`, new `QuickSearchRuntimeTest`.
3. Producer contract: `QBitMetaDataProducer`, `QBitMetaData` (artifactId `quick-search`, version from `quick-search.properties` filtered by Maven), component producers, unique key on `tableName`, permission rules, schedules, `QuickSearchRuntimeService`, `isEnabled` false without config, compat shims (`withConfig`, `getConfig`, `produce(QInstance)` adds and returns an empty output). Tests: `QuickSearchQBitProducerTest` updated plus new assertions (qbit registered, sourceQBitName, schedule, permission rules, unique key).
4. Real-time path: `QuickSearchRecordChangeListener`, `quickSearchFailedEvent` table and entity, failed-event replay in basepull, delete customizers and their tests, new `QuickSearchRecordChangeListenerTest` (insert/update/delete, after-commit via a fake transaction, rollback discards, failure writes a failed-event row).
5. Client: transport factory with auth modes, TLS, timeouts, user agent; secret interpretation via `QMetaDataVariableInterpreter`; mapping v2 with `_meta.mappingVersion`; `lenient` boosts and `operator=AND`; `OpenSearchException` wrapping; alias helpers (`createIndexForAlias`, `swapAlias`, `deleteIndex`, `resolvePhysicalIndex`); `health()`; `external_gte` versioning on bulk index; byte-capped bulk batches. Tests: `QuickSearchOpenSearchClientTest` (config to transport mapping, validation of modes), integration tests updated for mapping v2 and the "stella/stone" and mixed-type cases.
6. Indexing steps: keyset paging in `AbstractIndexingStep`; basepull start-anchored watermark with overlap and `GREATER_THAN_OR_EQUALS`, `enabled` honoured, failures aggregated, run purge, failed-event replay; full reindex alias swap (all tables) or in-place (single table), error message on FAILED; reconcile orphan sweep and `enabled`; drift detection sets `status = NEEDS_REINDEX`; `IndexingUtils` display values, label fallback, hidden-field exclusion, truncation, safe label formatting. Tests: step tests updated plus new scenarios from audit 05.
7. Search: `QuickSearchInput extends AbstractActionInput`, `tableNames`, `limitPerTable`, bounds, permission and enabled filtering, `QueryAction` post-filter, `tableLabel`, `totalHitsIsLowerBound`, `track_total_hits`. Tests: `QuickSearchActionTest` with a permission-denied session and bounds.
8. Admin surface: process frontend steps with an optional `tableName` field backed by a discovered-tables PVS, admin table fields (`lastRunStatus`, `lastErrorMessage`, `lastReconcileTime`, `documentCount`, `realTimeErrorCount`), read-only tracking fields, run-to-index PVS, labels, icon, health process.
9. Docs and CI: README rewrite, `CHANGELOG.md`, `docs/MIGRATION-1.0.md`, configuration reference, supported platforms and roles, parameterized Testcontainers image with a 2.19.6 and 3.9.0 matrix in CI, CLAUDE.md and AGENTS.md architecture updates.
10. Close-out: full `mvn verify` with Docker, PRD and audit status columns, TODO and SESSION-STATE, second-brain save.

## Open Questions

- Parent 2.1.0 re-pin happens when qbit-bom publishes it; until then the default-active profile carries 4.1.0-RC.1 (recorded as a deviation from ADR-0007, to be reverted).
- The qqq core `RecordSearchProviderInterface` proposal is filed as a follow-up, not implemented here.

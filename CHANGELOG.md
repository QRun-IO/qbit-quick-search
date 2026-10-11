# Changelog

All notable changes to this project are documented here. The format follows Keep a Changelog; versions follow semantic versioning.

## [1.1.0] - Unreleased

Requires the QQQ release that carries the record-search provider SPI (QRun-IO/qqq#1042); built against 4.1.0-SNAPSHOT until it is released. See `docs/MIGRATION-1.1.md`.

### Added
- `QuickSearchRecordSearchProvider`: serves QQQ core record search (`RecordSearchAction`, `POST /qqq/v1/search`, the Next UI global search) from OpenSearch for the tables Quick Search indexes. One `_msearch` request with a per-table top-N search (same query, AND semantics and field weights as `QuickSearchAction`); returns record ids only, which core re-reads through `QueryAction` (permissions, record locks, labels). Audit finding F-11, decision D1.
- `serveCoreRecordSearch` (default `true`): registers the provider and gives indexed tables core `searchFields` (visible string and integer searchable fields) when they have none. Host `searchFields` and a host-registered provider are never replaced.
- `recordSearchTimeoutMillis` (default 2500): wall-clock limit on the OpenSearch request; on timeout or any failure core falls back to its own search, and the provider claims no tables for 30 seconds.
- `QuickSearchOpenSearchClient.searchRecordIdsPerTable`.

### Security
- Fields of type `PASSWORD` are no longer indexed (as hidden fields already were). Reconcile or fully reindex once to remove values a 1.0 index may hold.

## [1.0.0-RC.2] - 2026-10-06

Second release candidate, published to Maven Central from `release/1.0`. Fixes the five pre-GA findings from the PR #10 review (#13 to #17); the changes are listed under 1.0.0 below. Requires QQQ 4.1. No schema change, but two new status values: `REBUILDING` on `quickSearchIndex.status` and `AWAITING_REINDEX` on `quickSearchFailedEvent.status`; a host that sized its own `quickSearchFailedEvent.status` column needs at least 16 characters. Known gaps before GA are tracked in #12.

## [1.0.0-RC.1] - 2026-10-05

First release candidate of 1.0, published to Maven Central from `release/1.0`. It contains every change listed under 1.0.0 below. Requires QQQ 4.1 (built against 4.1.0-RC.1). Known gaps before GA are tracked in #12.

## [1.0.0] - Unreleased

Requires QQQ 4.1. Closes every blocker of the 2026-10-04 production-readiness audit (`docs/audits/2026-10-04/README.md`).

### Added
- Standard QQQ 4.1 QBit contract: `QuickSearchQBitProducer implements QBitMetaDataProducer`, self-registered `QBitMetaData` (`com.kingsrook.qbits:quick-search`, real artifact version), `sourceQBitName` on produced metadata, component producers, `QuickSearchRuntimeService`.
- Real-time indexing through `RecordChangeListenerInterface`, published after commit via `QBackendTransaction.addAfterCommitCallback`.
- `quickSearchFailedEvent` table: failed real-time events are recorded and replayed by basepull.
- Scheduling: `schedulerName`, `basepullRepeatSeconds`, `reconcileCronExpression`, `reconcileCronTimeZoneId`.
- Full reindex of all tables builds a fresh physical index and swaps the alias; the configured index name is now an alias.
- Mapping version 2: separate search analyzer, dynamic template mapping `fieldValues.*` as text, `_meta.quickSearchMappingVersion`; outdated indexes are detected and rebuilt by a full reindex.
- Permission-aware search: table READ permission filter, `QueryAction` post-filter for record security locks, `tableNames`, `limitPerTable`, `tableLabel`, `totalHitsIsLowerBound`, term and page bounds.
- Connection security: `opensearchUrl`, `authMode` (`NONE`, `BASIC`, `AWS_SIGV4`), `tls` block (CA, trust store, mTLS, hostname verification, guarded `insecureSkipVerify`), timeouts, pool size, `User-Agent`, `OpenSearchTransportCustomizer` SPI, `${env.X}` secret references, redacting `toString`, `@JsonIgnore` on secret getters.
- `startupMode` (`FAIL_FAST` default, `DEGRADED`), `enableBasepullProcess` and `enableMaintenanceProcesses`, `basepullOverlapSeconds`, `runHistoryRetentionDays`, `maxFieldLength`, `maxSearchLimit`, `maxBulkRequestBytes`, `applyRecordSecurityLocks`, `adminPermissionRules`, `tablePermissionRules`, `tableMetaDataCustomizer`.
- Admin table fields: `lastReconcileTime`, `lastRunStatus`, `lastErrorMessage`, `documentCount`, `realTimeErrorCount`; `quickSearchIndexRun.quickSearchIndexId` shows the table name; icons and permission rules on the app, tables and processes; unique key on `quickSearchIndex.tableName`.
- Display values and possible-value labels are indexed; record labels fall back to QQQ's record label format; hidden fields are excluded; values are stored as strings and truncated to `maxFieldLength`.
- External versioning (`external_gte`) from the basepull timestamp field so stale writes never overwrite newer documents.
- Reconcile removes documents of tables no longer configured; drift between configured fields and the index row sets `status = NEEDS_REINDEX`.
- Maven Wrapper (3.9.11), JaCoCo coverage gate actually enforced, parameterized Testcontainers image (`-Dopensearch.test.image`).

### Changed
- Build targets qqq 4.1 (4.1.0-RC.1 through the default-active `qqq-snapshot` profile until `qbit-build-parent` 2.1.0 is released), opensearch-java 3.10.0, JUnit from the parent BOM.
- Basepull and full reindex page by primary key; the basepull watermark is the run start (minus overlap) with greater-than-or-equal semantics.
- Basepull fails at the end when any table failed (one table's failure does not stop the others).
- Query terms use the AND operator and are no longer edge-ngrammed at search time.
- `QuickSearchOpenSearchClient.search` takes a collection of allowed tables; `deleteDocumentsForTable` returns the deleted count; all OpenSearch errors surface as `QException`.
- `IndexingUtils.normalizeSearchText` no longer lower-cases (analyzers do).
- `enableScheduledProcesses` is deprecated in favour of `enableBasepullProcess` and `enableMaintenanceProcesses`.
- `QuickSearchQBitContext` is deprecated; state lives on `QuickSearchRuntime`, resolved from the `QInstance`.
- Search `offset` counts results the session can read. With record security locks on, hits are scanned from the start in batches of at most 1,000, filtered for access and deduplicated; one call reads at most 10,000 hits over 32 requests, and in per-table mode each of T tables gets max(10,000 / T, 2 x `limitPerTable`) hits. When that share runs out `hasMore` is false and `totalHitsIsLowerBound` is true. With locks on, `totalHits` counts hits confirmed readable. Per-table mode now honours `offset` (#16).
- A full reindex of all tables marks each table `REBUILDING` while it runs; drift detection is suspended for those tables. After a killed run, run the full reindex again (#14).

### Removed
- The three table customizers (`QuickSearchPostInsertCustomizer`, `QuickSearchPostUpdateCustomizer`, `QuickSearchPostDeleteCustomizer`), replaced by `QuickSearchRecordChangeListener`.
- Support for QQQ 4.0.

### Security
- `opensearchUrl` with userinfo (`https://user:pass@host`) fails validation; an `${env.X}` or `${prop.X}` credential or TLS path that resolves to an empty or blank value fails transport creation and names the field, never the value; `tls.hostnameVerification=false` logs a warning (#15).

### Fixed
- Rolled-back writes could be indexed; real-time failures were silent; basepull lost rows modified during a run and rows at the boundary second; unordered paging skipped or duplicated rows; full reindex blanked search; search leaked labels and snippets across tables regardless of permissions; mixed field types across tables broke indexing; non-text searchable fields failed every search; record labels were null for annotation tables; duplicate `quickSearchIndex` rows on concurrent first runs; unbounded `limit`/`offset`; unchecked `OpenSearchException`; unreachable cluster at startup left the host healthy with search broken.
- Deletes made during a full reindex of all tables were lost from the new index; they are now captured and applied after the alias swap (#14).
- An update was dropped without a `quickSearchFailedEvent` row when the listener's re-read failed or the optional AWS SDK was missing (`LinkageError`); both are now recorded for basepull, and a `DEGRADED` start without the AWS SDK no longer aborts host startup (#13).
- Search pages overlapped under record locks, per-table mode ignored `offset`, and a large `maxSearchLimit` could exceed the 10,000 result window (#16).
- A failing document count was swallowed and cleared `documentCount`; it is now logged at warn and the previous count is kept (#17).

## [0.2.0] - 2026-03-30
- Config-driven table indexing (`SearchableTableConfig`).

## [0.1.0] - 2026-03-29
- Initial release.

# Changelog

All notable changes to this project are documented here. The format follows Keep a Changelog; versions follow semantic versioning.

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

### Removed
- The three table customizers (`QuickSearchPostInsertCustomizer`, `QuickSearchPostUpdateCustomizer`, `QuickSearchPostDeleteCustomizer`), replaced by `QuickSearchRecordChangeListener`.
- Support for QQQ 4.0.

### Fixed
- Rolled-back writes could be indexed; real-time failures were silent; basepull lost rows modified during a run and rows at the boundary second; unordered paging skipped or duplicated rows; full reindex blanked search; search leaked labels and snippets across tables regardless of permissions; mixed field types across tables broke indexing; non-text searchable fields failed every search; record labels were null for annotation tables; duplicate `quickSearchIndex` rows on concurrent first runs; unbounded `limit`/`offset`; unchecked `OpenSearchException`; unreachable cluster at startup left the host healthy with search broken.

## [0.2.0] - 2026-03-30
- Config-driven table indexing (`SearchableTableConfig`).

## [0.1.0] - 2026-03-29
- Initial release.

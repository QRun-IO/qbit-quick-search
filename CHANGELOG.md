# Changelog

All notable changes to this project are documented here. The format follows Keep a Changelog; versions follow semantic versioning.

## [Unreleased]

Requires QQQ 4.1.0-RC.6 or later (the spec-managed UI API extension contract).

### Fixed
- Real-time indexing failures now increase the table's error count by the number of failed events. Successful basepulls and full reindexes reset the count. Counter update failures are logged without failing the source write.

### Added
- Every `quick_search_*` table and the Quick Search Admin app carry help that says what the table is for and how its rows get created (`help/quick-search.yaml`, QQQ help-bundle format, applied by `QuickSearchHelp` as a stand-in for core help bundles; host help is never overridden). `QuickSearchTablesGuardrailTest` fails when a table is unused by runtime code, neither primed nor listed as starting empty, or has no such help; `QuickSearchSeededDefaultsTest` proves priming populates the config tables on a fresh backend, is idempotent and keeps admin edits.
- Session-only typeahead: `GET /qqq/v1/qbit/quick-search/suggest` (`getQuickSearchSuggestions`) and `QuickSearchSuggestService.suggest(...)`. Reuses the permission-aware search action with one bounded candidate batch per table; groups expose only record IDs, filtered labels/subtitles, `hasMore` and `truncated`. Defaults to 5 items across 8 tables (maximum 10/20); short terms and unavailable search return empty groups. The existing UI extension and generated contract now carry both status and suggestions.
- Supported host status API (`api.QuickSearchStatusService.get()`), immutable status values, and `QuickSearchApi` home navigation constants (issue #50/F10). Permission-checked, bounded diagnostics refreshes share a short per-runtime cache and reuse saved validation counts. New spec-managed `GET /qqq/v1/qbit/quick-search/status` (`getQuickSearchStatus`) with committed OpenAPI fragment and strict HTTP/contract tests. RC.6 requires the default `/qbit/quick-search` prefix; `/quick-search` is not permitted by core. Existing routes and wire shapes are unchanged.
- Record developer panel: `QuickSearchDeveloperPanelProvider` (id `quickSearch`) adds a "Quick Search" panel to the developer view of every record of an indexed table (`/app/{table}/{id}/dev` in the Next UI), for users who may read Quick Search Index. It shows the table's index row (enabled, status, record count, last basepull and full reindex, searchable fields), the latest index run, the record's indexed document compared field by field with the database (Inspect Search Document's comparison; needs `debugPermissionRules`), the record's failed index events, the Inspect Search Document and Reindex in Search actions the user may run, and links to the admin app and the index row. An unreachable OpenSearch shows a warning in the panel. Registered by `QuickSearchDeveloperPanelsMetaDataProducer`.

- **Data-driven configuration.** The searchable tables and fields are records: `quick_search_index` (the table registry, new columns `source` and `ownerEdited`) and the new `quick_search_index_field` (weight, role `DISPLAY`/`TITLE`/`SUBTITLE`/`BODY`/`KEYWORD`/`EXACT`, analyzer `PREFIX`/`WORD`/`ENGLISH`/`EXACT`, snippet and label flags). Indexing, search, the record-search provider and the diagnostics read the effective config from them (cached 30 s, dropped on change), so changes need no redeploy. Code (`@QuickSearchable`, `searchableTables`) only seeds them; rows an admin edited are never overwritten, and removals are remembered in the new `quick_search_config_tombstone`. Precedence and DDL: `docs/MIGRATION-1.1.md`, "Data-driven configuration".
- **Self-priming.** New code-declared tables and fields get their rows at startup and are built by the next basepull (`PENDING_BUILD`, `BUILDING`); a missing index is recreated and rebuilt, an empty one rebuilt; existing rows are migrated without a rebuild. The Search Diagnostics widget reports it; `QuickSearchStatus` gives hosts the same banner.
- Admin processes with review and result steps (record or table actions, never in the nav): Add Search Index (the valid create: eligible-table picker, estimated record count, default fields), Apply Search Index Changes (previews field changes, then rebuilds), Disable Search Index and Remove Search Index (delete or keep documents), Cancel Index Run (cooperative cancel between batches; runs end `CANCELLED` with counts), Pause Indexing and Resume Indexing (global pause in the new `quick_search_settings`; search keeps serving, changes are deferred and replayed).
- `QuickSearchResult.subtitle` and `displayValues` (from `SUBTITLE` and `DISPLAY` fields); document objects `fieldValuesWord`, `fieldValuesEnglish`, `fieldValuesExact`, added to existing indexes' mapping at startup.
- Possible-value sources `quick_search_eligible_table`, `quick_search_indexable_field`, `quick_search_timestamp_field`, `quick_search_field_role`, `quick_search_field_analyzer`, `quick_search_documents_choice`, `quick_search_config_source`; the join `quick_search_index_join_quick_search_index_field` and the Searchable Fields widget on the index record. Config tables are audited at field level.

### Changed
- Distinguish protected fields from fields that simply cannot be searched. Boolean, blob and hidden fields no longer trigger result rebuilding or erase record labels. Labels are suppressed only when their source fields are protected; unprotected hidden primary keys and timestamps remain usable.
- Exclude PII/PHI-classified fields (including table defaults) and field-security-locked fields everywhere Quick Search indexes or displays values. Configuration rejects protected fields; code defaults drop them with one warning per table. Search and diagnostics omit protected values from older documents. Rebuild existing indexes after upgrading or changing classification metadata. Uses QQQ 4.1.0-RC.6; no dependency changes.
- `quick_search_index`: the generic Create and Delete are off (`TABLE_INSERT`, `TABLE_DELETE`); use Add Search Index and Remove Search Index. `quick_search_searchable_table` is now a custom source over the effective tables. `quick_search_index_run` gains `cancelRequested` and `cancelRequestedBy`.
- Hidden and `isSensitive` fields are never indexed (previously hidden and password only).
- Record screens (record-screen audit): `quick_search_index_run` and `quick_search_failed_event` are written by the QBit only, so Create and Edit are off (Delete stays, for clearing); both get named field sections, record labels and pickers (`quick_search_run_type`, `quick_search_run_status`, `quick_search_failed_event_status`, `quick_search_failed_event_action`, and the table picker for `tableName`). The index record lists its Recent Runs (join `quick_search_index_join_quick_search_index_run`). `quick_search_validation_run.reportJson` uses a JSON code editor. The Basepull, Full Reindex and Reconcile processes run from Quick Search Index's Actions and are no longer app (nav) children; their table input is `searchTableName` (`tableName` still works for API callers, and a Quick Search table name is ignored), so running them from the table no longer limits them to `quick_search_index`. Process input steps now carry `HELP_TEXT` and `EDIT_FORM` components, so the Next UI renders their fields.
- **Snake_case names (owner naming rule, 2026-10-12), for 1.1.0-RC.3.** The tables are now `quick_search_index`, `quick_search_index_run`, `quick_search_failed_event`, `quick_search_diagnostic_event` and `quick_search_validation_run` (they were camelCase). The possible-value sources are `quick_search_index` and `quick_search_searchable_table`. The permission bases are `quick_search_admin` and `quick_search_debug`, and table permissions follow the table names. A table-name prefix is now converted to snake_case and joined with `_`. The old names remain as `@Deprecated` `LEGACY_*` constants. This is metadata only: the QBit ships no DDL. Upgrade steps, with SQL for camelCase databases and for stored references (audit table names, saved views, permission grants), are in `docs/MIGRATION-1.1.md` under "Snake_case names (1.1.0-RC.3)".

## [1.1.0-RC.1] - 2026-10-11

First release candidate of 1.1, published to Maven Central from `release/1.1`. It contains every change listed under 1.1.0 and 1.0.0 below (1.0.0 has not been released as GA). Requires QQQ 4.1.0-RC.2 or later. Upgrade notes: `docs/MIGRATION-1.1.md`.

## [1.1.0] - Unreleased

Requires QQQ 4.1.0-RC.4 or later (the record-search provider SPI arrived in 4.1.0-RC.2; its scores, highlights, totals and paging in 4.1.0-RC.4); built against 4.1.0-RC.4. See `docs/MIGRATION-1.1.md`.

### Added
- `QuickSearchRecordSearchProvider`: serves QQQ core record search (`RecordSearchAction`, `POST /qqq/v1/search`, the Next UI global search) from OpenSearch for the tables Quick Search indexes. One `_msearch` request with a per-table top-N search (same query, AND semantics and field weights as `QuickSearchAction`); returns record ids only, which core re-reads through `QueryAction` (permissions, record locks, labels). Audit finding F-11, decision D1.
- `serveCoreRecordSearch` (default `true`): registers the provider and gives indexed tables core `searchFields` (visible string and integer searchable fields) when they have none. Host `searchFields` and a host-registered provider are never replaced.
- `recordSearchTimeoutMillis` (default 2500): wall-clock limit on the OpenSearch request; on timeout or any failure core falls back to its own search, and the provider claims no tables for 30 seconds.
- `QuickSearchOpenSearchClient.searchRecordIdsPerTable`.
- Record search details for QQQ 4.1.0-RC.4's record-search contract: the provider passes each hit's relevance `score`, a plain-text highlight (match ranges, from OpenSearch highlighting of `fieldValues.*` with private-use markers) and each table's total hits (`totalCountByTable`, counted to 10,000), and honors the request `offset` (`from`). `sort: RECENT` keeps relevance order (the index has no record modify dates). New `QuickSearchOpenSearchClient.searchRecordsPerTable`; `searchRecordIdsPerTable` now delegates to it. This requires QQQ 4.1.0-RC.4 or later.
- Diagnostic tools (#35), on by default (`enableDiagnosticTools`): Search Playground, Validate Search Index (schedulable with `validationCronExpression`; reports kept in the new `quickSearchValidationRun` table), Test Search Analyzer, and on every indexed table and on `quickSearchFailedEvent` rows the record actions Inspect Search Document (database vs index, and why a record does or does not match a term) and Reindex in Search. All are table or record actions, gated by the new `debugPermissionRules` (`quickSearchDebug.hasAccess`; Reindex in Search uses `adminPermissionRules`), rate-limited per user (`diagnosticRunsPerMinute`), and recorded in the new `quickSearchDiagnosticEvent` table. Settings: `enableRecordDiagnosticActions`, `validationCronExpression`, `validationCronTimeZoneId`, `validationSampleSize`.
- Search failure diagnostics: a failed or timed-out OpenSearch request behind core record search or `QuickSearchAction` is recorded as a `SEARCH_FAILED` diagnostic event (tables, duration, error, server; never the term); the runtime counts failures and backoff fallbacks. The Search Diagnostics widget (`quickSearchDiagnostics`, on the admin app home) shows the provider's state, recent failures and the latest validation.
- `QuickSearchDiagnosticsOperations`: read-only probe search (named clauses, field highlights, request and response JSON), explain, document reads, `_analyze`, document sampling, and raw cluster, mapping and settings reads.

### Changed
- Run history purging also purges `quickSearchDiagnosticEvent` and `quickSearchValidationRun` rows older than `runHistoryRetentionDays`.

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
- A real-time insert was indexed with raw values (a foreign key as its id, the record label as "2 - email") until the next basepull. Inserts are now re-read in the write's transaction with display values and possible-value labels, as updates are; a failed re-read is recorded in `quickSearchFailedEvent` for basepull and never fails the insert (#28).

## [0.2.0] - 2026-03-30
- Config-driven table indexing (`SearchableTableConfig`).

## [0.1.0] - 2026-03-29
- Initial release.

# TODO

## Now

- [ ] Sponsor resolves decisions D1 to D11 in `docs/initiatives/quick-search-1.0.md`.
- [ ] Decide how to commit this session's docs (this branch vs a new branch off develop) and whether to keep `AGENTS.md`.
- [ ] Create the GitHub tracking issue for the 1.0 initiative; write its number into the PRD frontmatter.
- [ ] Review and merge PR #8; close Dependabot PR #1 as superseded.
- [ ] Open the qqq issue proposing `RecordSearchProviderInterface` (D1), using the sketch in audit report 01 F-11.

## Epic 1: 4.1 platform and QBit contract (foundational)

- [ ] Break E1 into stories (`docs/epics/platform-and-contract-stories.md`) following the ordered migration plan in `docs/audits/2026-10-04/01-qqq-4.1-integration.md`.
- [ ] `QBitMetaDataProducer`, `QBitMetaData` with real version, component producers, `postProduceActions`, compat shims for `withConfig()`/`produce(QInstance)`.
- [ ] Config surface: `schedulerName`, repeat/cron, `permissionRules`, `startupMode`, `tableMetaDataCustomizer`, `getDefaultBackendNameForTables()`, flag split.
- [ ] `QuickSearchRuntimeService` (`QRuntimeServiceInterface`); `SourceQBitAware` config resolution; deprecate the static context.
- [ ] Unique key on `quickSearchIndex.tableName`; null-safe validation plus validator plugin.
- [ ] Build: JaCoCo `prepare-agent`, drop JUnit 5.10.0 pins, CI matrix (GA plus next snapshot), ITs on OpenSearch 2.19.x and 3.x.

## Epics 2 to 6

- [ ] E2 index trust: `RecordChangeListenerInterface` with after-commit publish, failed-event table, keyset paging, start-anchored watermark, schedules, alias-swap reindex, orphan and drift detection, versioning, run purge.
- [ ] E3 search quality and permissions: search analyzer, AND semantics, `fieldValues` templates, display values and labels, permission filter and `QueryAction` post-filter, bounds, wrapped exceptions.
- [ ] E4 UI integration: `tableNames`, `limitPerTable`, `tableLabel`, typed `recordId`, default fields from `searchFields`, core SPI and provider.
- [ ] E5 connection security: opensearch-java 3.10.0, `opensearchUrl`, `tls` block, `authMode` with SigV4 per D6, `${env.}` secrets, redaction, timeouts, transport customizer SPI, secured IT.
- [ ] E6 ops and docs: process input screens, admin table fields, health, README with supported platforms and roles, migration notes, CHANGELOG; 0.3.0 compat release then 1.0.0; qqq-all and Voyage pins.

## Later (1.x)

- [ ] Amazon OpenSearch Serverless (needs SigV4 `aoss` and removal of `_delete_by_query` and `_refresh` from the design).
- [ ] Decide whether to prototype Jev table routing; see `docs/reference/typesafe-jev-candidates.md`.

## Done this session

- [x] Status assessment and PRD written (2026-10-04).
- [x] Deep-dive audit, five dimensions, consolidated with 25 blockers (2026-10-04).
- [x] 4.1.0-RC.1 compatibility verified: 233 unit plus 12 integration tests green (2026-10-04).

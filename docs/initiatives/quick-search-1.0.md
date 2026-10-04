---
tracker_key:        # fill with the GitHub tracking issue once created
project: qbit-quick-search
status: draft
updated: 2026-10-04
---

# Initiative: QBit Quick Search 1.0 on QQQ 4.1

> Stakeholder-readable PRD. This markdown is canonical; the tracker mirrors it. Scope claims are grounded in the code at `develop` (`000e5bb`) and in QQQ `v4.1.0-RC.1`, and in the deep-dive audit of 2026-10-04 (`docs/audits/2026-10-04/README.md`, blockers B1 to B25).

## Executive Summary

QBit Quick Search gives QQQ applications OpenSearch-backed full-text search across tables. It is at 0.2.1-SNAPSHOT, published publicly as 0.2.0, pinned by Voyage and bundled by `qqq-all`. The July 2026 audit found the index could not be trusted to converge with the source database. PR #7 (merged 2026-09-26) fixed update clobbering, silent bulk failures and added a reconcile process. The 2026-10-04 deep-dive confirmed the QBit builds and passes all tests on QQQ 4.1.0-RC.1 unchanged, and found 25 remaining blockers across trust, security, search quality, platform compatibility and operability.

1.0 means three things. First, a standard QQQ 4.1 QBit: built on the 4.1 build parent, registered through `QBitMetaDataProducer`, scheduled through QQQ's scheduler, composable with other QBits on the same tables, fail-fast at startup. Second, a trustworthy index: no uncommitted or lost writes, no silent failures, deterministic batch jobs, a reindex that keeps serving results, and search that honours QQQ permissions. Third, a credible platform story: current OpenSearch client, self-managed 2.x and 3.x plus Amazon OpenSearch Service managed domains, TLS trust configuration, secrets by reference, and an authentication model that covers basic auth and, if decision D6 says so, AWS IAM SigV4.

The 4.1 release train does not wait for 1.0. A compatibility-only 0.3.0 rides the proposed `qbit-build-parent` 2.1.0 chain (qqq#921). 1.0 follows on the same parent once the epics below land.

## Business Objective

Ship a search QBit that QQQ adopters (qqq-all demo users, Voyage, future Business Platform tenants) can run in production without a reconciliation babysitter, against the OpenSearch deployments they actually have, and that the Next UI can expose directly.

## Problem Statement

Current state at `develop` as of 2026-10-04:

| Area | State | Evidence |
|---|---|---|
| Build | Parent `qbit-build-parent:2.0.0` (qqq 4.0.0). Opt-in `-Pqqq-snapshot` profile. | `pom.xml` |
| 4.1 compatibility | 233 unit tests pass on 4.0.0, 4.1.0-SNAPSHOT and 4.1.0-RC.1; `mvn verify` on RC.1 with Docker passes all 12 integration tests. No source change needed. | surefire and failsafe reports |
| Coverage gate | JaCoCo `check` is skipped (no `prepare-agent`), so the 0.70 / 0.90 thresholds never run. | `mvn verify` output |
| Open PRs | #8 (orb 0.6.8, tag-only GA publish, Apache checkstyle, AssertJ 3.27.7): CI green, awaiting review. #1 superseded by #8. | GitHub |
| Release chain | QQQ 4.1.0-RC.1 published 2026-10-02. Parent 2.1.0-RC.1 and quick-search 0.3.0-RC.1 proposed in qqq#921; qqq-all BOM pins 0.2.1-SNAPSHOT. | qqq#921, qbit-bom#4, qqq-all#10 |
| Index trust | Real-time indexing runs inside the host transaction before commit (B1); failures are swallowed with no record (B2); basepull pages without order-by (B4) and loses rows modified during a run (B5); basepull and reconcile are never scheduled (B6); full reindex wipes first (B10); no unique key on `quickSearchIndex.tableName` (B19). | audit B1, B2, B4, B5, B6, B10, B19 |
| Search quality | Query terms are edge-ngrammed because no search analyzer is set, and multi-match defaults to OR (B7); shared dynamic `fieldValues` object breaks on mixed field types and non-lenient boosts (B8); record labels null for annotation tables and PVS ids indexed instead of labels (B9); unbounded paging and term length (B17). | audit B7, B8, B9, B17 |
| Security | Search has no permission or session checks (B3); no permission rules on processes, tables or app (B16); credentials allowed over plain HTTP and no TLS trust configuration (B11); password stored on serializable metadata (B12). | audit B3, B11, B12, B16 |
| Platform | opensearch-java 2.10.0 is the end-of-life 2.x line (B13); IAM SigV4 and Serverless unsupported; no timeouts (B15); integration tests pin OpenSearch 2.11.0 only (B22). | audit section 3 |
| QBit contract | Plain producer, no `QBitMetaData` (qqq-all hand-registers with a hard-coded version), no `sourceQBitName`, static context, `withCustomizer` throws on occupied slots, best-effort startup (B14). | `QuickSearchQBitProducer`, qqq-all `DemoQBits.java:161` |
| Core overlap | QQQ 4.1 `RecordSearchAction`, `QTableMetaData.searchFields` and `POST /qqq/v1/search` back the Next UI with permissions and no provider SPI; Quick Search has no UI surface and cannot yet satisfy `tableNames` or `limitPerTable`. | qqq commit `7dd36c8ad`; audit section 5 |
| Docs | README shows a `0.2.0-SNAPSHOT` dependency, promises a scheduled basepull, admin-UI per-table reindex, "field label" prefixes, enforced coverage and qbit-crm/wms integrations, none of which hold. | `README.md` |

Doing nothing leaves qqq-all and Voyage shipping a search feature whose basepull never runs, whose index can contain rolled-back and lost writes, whose results leak across tables regardless of permissions, and whose reindex blanks search.

## Vision / Desired Future State

A host adds the dependency, configures the QBit like any other 4.1 QBit with secrets by reference, points it at a self-managed or AWS-managed OpenSearch cluster over TLS, and gets: real-time indexing that reflects committed data and survives slot collisions, a scheduled catch-up and reconcile with a replayed failed-event log, a reindex that keeps serving results, a search API that returns only what the session may read, and a Next UI search box backed by OpenSearch when the QBit is present.

## Scope

### In Scope

- Released 4.1 build parent; CI matrix on the 4.1 GA line plus the next snapshot; Docker-backed integration tests on OpenSearch 2.19.x and 3.x; JaCoCo gate actually enforced.
- Standard QBit contract: `QBitMetaDataProducer`, self-registered `QBitMetaData` with the real artifact version, namespace support, `sourceQBitName`, fail-fast validation including field existence, explicit permission rules, instance-scoped runtime state, composable customizers.
- Index trust: post-commit indexing or a documented contract, failed-event capture and replay, keyset paging everywhere, start-anchored basepull watermark with overlap, scheduling through `QScheduleMetaData`, blackout-free full reindex, unique key on the index table, orphan and drift detection, external document versioning.
- Search quality and safety: search analyzer, AND semantics, typed-safe `fieldValues` mapping, display values and labels, permission-aware results, bounded paging and term length, wrapped OpenSearch exceptions, `tableNames` and `limitPerTable` inputs, `tableLabel` and typed `recordId` outputs.
- Connection security: opensearch-java 3.x, `opensearchUrl`, TLS trust block, `authMode` (`NONE`, `BASIC`, and `AWS_SIGV4` per D6), secrets by `${env.}` reference, redaction, timeouts and pool sizing, transport customizer SPI, client lifecycle.
- Operability and docs: process input screens, admin table status fields, run-history purge, health method, truthful README with a supported-platforms statement and least-privilege roles, configuration reference, migration notes from 0.x, CHANGELOG.

### Out of Scope

- Asynchronous publisher implementations (RabbitMQ, Kafka, ESB). The `IndexEventPublisher` SPI stays and gets `occurredAt` and a version so an async implementation can be correct, but it is marked experimental in 1.0.
- Amazon OpenSearch Serverless (needs SigV4 `aoss`, and lacks `_delete_by_query` and `_refresh`). Legacy Elasticsearch endpoints and OpenSearch 1.x.
- Bearer, JWT, API-key and mTLS as first-class `authMode` values (available through the transport customizer SPI).
- Record-level security beyond the `QueryAction` post-filter (D3). Index-time security keys.
- Multi-index layouts, composite primary keys, synonyms, fuzzy matching, phrase queries, language analyzers, sort options.
- `com.kingsrook` to `io.qrun` groupId migration (ADR-0007). qbit-crm / qbit-wms companion integrations.
- Model-assisted table routing or re-ranking (TypeSafe Jev); candidates in `docs/reference/typesafe-jev-candidates.md` for 1.x.

## Success Criteria

- `com.kingsrook.qbits:qbit-quick-search:1.0.0` on Maven Central, parent importing the released qqq 4.1 BOM, Apache-2.0 metadata, opensearch-java 3.x.
- CI runs the Testcontainers suite on OpenSearch 2.19.x and 3.x latest minor with no silent skips, on 4.1 GA and the next snapshot; JaCoCo thresholds enforced.
- qqq-all `full` profile and Voyage pin 1.0.0; qqq-all's smoke finds a seeded customer through the Next UI search box (or through the API if D1's SPI slips).
- A session without read permission on a table gets zero results from that table; a session with a record security lock sees only its rows. Both covered by tests.
- A rolled-back insert produces no document (or, under the documented alternative, is removed by the next scheduled reconcile). Covered by an H2 transaction test.
- Full reindex of a seeded table returns non-empty results at every point during the run. Covered by an integration test.
- Basepull and reconcile fire on schedule in a host with a configured scheduler; a row modified during a basepull run is indexed by the next run. Covered by tests.
- "stella" does not match "stone"; a table with an integer and a date searchable field searches without error. Covered by integration tests.
- Basic auth over TLS against a cluster with a private CA works with the `tls` block; credentials over plain HTTP are rejected unless explicitly allowed. Covered by a secured Testcontainers test.
- Every blocker B1 to B25 in the 2026-10-04 audit is closed or explicitly deferred with a reason in this doc.

## Key Stakeholders

| Role | Person | Responsibility |
|---|---|---|
| Sponsor / Product Owner | James Maes | Decisions D1 to D11, release approval |
| Engineering Lead | James Maes (with Claude Code) | Build, review, release |
| Downstream consumers | qqq-all (QRun-IO/qqq-all#10); Voyage (pins 0.2.0 from GitHub Packages, `OrderAppMetaDataProvider.produceQuickSearchQBit`) | Pin and verify 1.0.0 |

## Initiative-level Constraints

- Parent `qbit-build-parent` is the only source of the qqq version (ADR-0007). No child BOM imports outside the opt-in snapshot profile. httpclient5 and Jackson stay BOM-managed.
- Apache-2.0 everywhere; Central artifacts are immutable, so metadata fixes ship as new versions.
- 3-space indentation, flower-box Javadoc, fluent `withX()` setters, wrapper types (CODE_STYLE.md).
- JaCoCo gates stay at 0.70 instruction / 0.90 class, and must actually run.
- Source compatibility for the fluent config setters and `QuickSearchAction` input/output used by qqq-all and Voyage (`withOpensearchHost/Port/UseSsl/Username/Password`, `withSearchableTable`, `withSearchableEntityClasses`, `QuickSearchQBitProducer().withConfig().produce(qInstance)`). New fields are additive; deprecated fields keep working through 1.x.
- No secrets in metadata objects, logs, or the frontend `environmentValues` (never name an OpenSearch secret `QQQ_ENV_*`).
- Any mapping change (search analyzer, `fieldValues` templates) lands before the first 1.0 index is created, with an index-version marker so an existing 0.x index is detected and rebuilt rather than reused.

## Assumptions

- QQQ 4.1.0 GA and `qbit-build-parent` 2.1.0 publish before the 1.0 release; work proceeds on `4.1.0-RC.1` via the snapshot profile.
- OpenSearch 2.19.x and 3.x are the supported server lines; opensearch-java 3.10.0 is the client (its only compile break here is `.refresh(true)` to `.refresh(Refresh.True)`).
- Hosts that want scheduling already configure a QQQ scheduler; the QBit does not provide one.
- QQQ 4.1 provides `RecordChangeListenerInterface`, `QBackendTransaction.addAfterCommitCallback` and `QRuntimeServiceInterface`, none of which exist in 4.0.0 (verified by tag). 1.0 uses them and therefore requires qqq 4.1 (D10).

## Risks

| Risk | Mitigation |
|---|---|
| Core `RecordSearchAction` has no SPI, so Next UI integration needs a qqq core change | Raise the SPI as a qqq issue early (D1); 1.0 can ship API-only, UI integration in 1.1 |
| Adopting 4.1-only APIs drops 4.0 hosts | Voyage and qqq-all both move to 4.1; 0.3.0 remains the last 4.0-compatible line (D10) |
| After-commit publishing depends on the backend transaction honouring the callback contract | RDBMS does at the 4.1 tag; verify Mongo and other backends in the integration matrix; flush immediately when no transaction is passed; scheduled reconcile is the backstop |
| Removing the static context changes how custom publishers and process steps obtain the client | Keep static accessors as deprecated delegates to the instance-scoped holder for one minor version |
| Permission post-filtering reduces page sizes unpredictably | Over-fetch (limit times two, capped) and filter down; document that `totalHits` is an upper bound |
| Alias-swap reindex needs index-create and alias privileges on the cluster | Fall back to in-place reconcile when the configured user lacks the privilege; document required roles |
| opensearch-java 3.x bump changes transport behaviour against 2.x servers (HTTP/2 negotiation, URL encoding) | CI matrix on 2.19.6 and 3.9.0; keep the content-compression workaround until proven unnecessary |
| SigV4 pulls AWS SDK v2 artifacts into a QBit | Declare them `optional`; load the AWS transport reflectively or in a separate class only when `authMode=AWS_SIGV4` |

## Dependencies

- qqq 4.1.0 GA (qqq#798, #921), `qbit-build-parent` 2.1.0 (qbit-bom#4), qqq-orb 0.6.8 (PR #8).
- CircleCI executor with Docker for the integration suite (orb capability to confirm).
- qqq core change for a record-search provider SPI (new issue, D1).
- Optional: `software.amazon.awssdk:aws-crt-client` and `auth` for SigV4 (D6).

## Proposed Epics

Estimates are engineer-days with AI-augmented throughput. E1 is foundational and gets broken into stories first. Blocker IDs reference `docs/audits/2026-10-04/README.md`.

### Epic 1: 4.1 platform and QBit contract (foundational, ~7 days)
Purpose: make the QBit a standard 4.1 citizen, following the ordered migration plan in audit report 01. `QBitMetaDataProducer<QuickSearchQBitConfig>` with `getQBitMetaData()` (groupId `com.kingsrook.qbits`, artifactId `quick-search` which qqq-all asserts, version from a Maven-filtered resource, namespace, config); tables, processes and app as package-scanned component producers or `@QMetaDataProducingEntity`; registration and bootstrap in `postProduceActions`; `withConfig()` and a `produce(QInstance)` override that adds to the instance kept as deprecated shims so qqq-all and Voyage compile unchanged; `isEnabled()` false when the config is null; `getDefaultBackendNameForTables()` and a settable `tableMetaDataCustomizer`; namespace as default `tableNamePrefix`. Explicit `QPermissionRules` with config override (B16). Null-safe list-based validation covering backend, field existence, timestamp field and OpenSearch naming rules, plus a `QInstanceValidatorPluginInterface` for late checks (B24). `QuickSearchRuntimeService implements QRuntimeServiceInterface` owning connect, `ensureIndexExists` and close, with `startupMode` per D11 (B14); config resolved from `SourceQBitAware.getSourceQBitConfig()` in steps and the listener, `QuickSearchQBitContext` kept as a deprecated facade for one release. Unique key on `quickSearchIndex.tableName` (B19); split `enableScheduledProcesses` into basepull and maintenance flags with the old name as a deprecated alias (B25). Build: JaCoCo `prepare-agent` with an explicit version, drop the JUnit 5.10.0 pins for the parent's `junit-bom` 6.0.1, parent 2.1.x re-pin when released; CI matrix (GA plus next snapshot), Docker-backed integration tests on 2.19.x and 3.x (B22).

### Epic 2: Index trustworthiness (~7 days)
Purpose: finish the convergence story. Replace the three customizers with one `QuickSearchRecordChangeListener implements RecordChangeListenerInterface` (`appliesTo` only discovered tables; UPDATE keeps the full-record re-read through `event.getTransaction()`), registered with `qInstance.withRecordChangeListener`, publishing through `QBackendTransaction.addAfterCommitCallback` when a transaction is present and immediately otherwise (B1, D7); the listener cannot fail the write, so record failures in a `quickSearchFailedEvent` table that the scheduled run replays, with bounded retry on bulk 429/5xx and failure counts on `quickSearchIndex` (B2, D8); keyset paging in basepull and full reindex reusing the reconcile loop (B4); start-anchored watermark with configurable overlap and greater-than-or-equal (B5); `QScheduleMetaData` on basepull and reconcile with `schedulerName` and per-table interval (B6); full reindex into a fresh index with alias swap and in-place fallback, `conflicts(Proceed)`, error message on FAILED runs (B10); aggregate per-table failures into the step result (B20); honour `enabled` in every path (B21); orphan sweep for removed or renamed tables, config and mapping drift detection with a `NEEDS_REINDEX` status and an index-version marker (B23); external document versioning from `modifyDate`; run-history purge; `IndexEvent.occurredAt` and version.

### Epic 3: Search quality and permission-aware search (~5 days)
Purpose: make results correct and safe. Standard+lowercase `search_analyzer`, `operator=AND` or `minimum_should_match`, boosts that apply to prefix matches (B7); `dynamic_templates` for `fieldValues.*`, `date_detection: false`, stringified values, `lenient(true)` (B8); display values and PVS labels at index time, `QValueFormatter.formatRecordLabel` fallback, per-field `maxLength` (B9); table-level READ permission filter mirroring core, `terms` filter on `sourceTable`, `QueryAction` post-filter by primary key for record locks per D3, hidden and masked fields excluded at index time (B3); bounded `limit`, window and term length, `track_total_hits` with a lower-bound flag (B17); `OpenSearchException` wrapped in `QException` (B18); disabled tables excluded from search (B21).

### Epic 4: Search surface and UI integration (~4 days, partly in qqq core)
Purpose: make search usable from the UI. Input parity with core: `tableNames`, `limitPerTable`, `inputSource`; output parity: `tableLabel`, typed `recordId`; default the field list from `QTableMetaData.searchFields` when present; propose and implement a provider SPI in qqq core so `RecordSearchAction` delegates to a registered provider (D1); ship `QuickSearchRecordSearchProvider`; keep `QuickSearchAction` as the richer backend API (scores, highlights, pagination).

### Epic 5: Connection security and authentication (~5 days)
Purpose: run against real clusters safely. opensearch-java 3.10.0 bump with the `Refresh.True` fix and content-compression workaround kept (B13); `opensearchUrl` and optional host list, https default; `tls` block (CA path, trust store, key store for mTLS, hostname verification, guarded `insecureSkipVerify`) (B11); `authMode` enum `NONE`, `BASIC`, `AWS_SIGV4` with `awsRegion`, `awsServiceName`, `awsAssumeRoleArn` and optional AWS SDK v2 dependencies per D6; `transportCustomizer` `QCodeReference` SPI for bearer, API key and mTLS; `${env.}` interpretation of secrets via `QMetaDataVariableInterpreter` at client construction, `@JsonIgnore` secret getters, redacting `toString()`, `transient` publisher (B12); `allowPlaintextCredentials` gate; `connectTimeoutMillis`, `responseTimeoutMillis`, `maxConnections`, `User-Agent`, byte-capped bulk batches (B15); validation rules and the deprecation path for the old setters; a secured Testcontainers test (`withSecurityEnabled`).

### Epic 6: Operations, docs and release (~4 days)
Purpose: ship 1.0 cleanly. Process input screens with a table PVS and summary screen (B25); admin table fields `lastRunStatus`, `lastErrorMessage`, `lastReconcileTime`, `documentCount`, read-only tracking fields, PVS from run to index, sections and labels; health method surfaced in the admin table; README rewrite (truthful feature list, dependency snippet, supported-platforms statement, least-privilege roles for the security plugin and AWS FGAC, required cluster privileges for alias swap, `QQQ_ENV_*` warning, SPI stability statement); configuration reference; 0.x to 1.0 migration notes including the forced reindex; CHANGELOG and release notes; 0.3.0 compatibility release on parent 2.1.0 first, then 1.0.0; qqq-all and Voyage pin bumps.

## Decisions for the sponsor

Each has a recommendation. Resolve one at a time; answers get folded into tickets as facts.

| ID | Question | Recommendation |
|---|---|---|
| D1 | Integrate with core `RecordSearchAction` via a new qqq SPI, or keep Quick Search API-only? | Propose the SPI in qqq now; ship 1.0 API-only if it slips, UI in 1.1 |
| D2 | Version plan: does the qqq#921 chain ship 0.3.0 (compat only) and 1.0 follows, or does 4.1 wait for 1.0? | 0.3.0 on the RC chain, 1.0 after E1 to E6; do not gate 4.1 on 1.0 |
| D3 | Permission depth for 1.0: table-level read permission only, or record-level via `QueryAction` post-filter? | Table-level plus `QueryAction` post-filter by primary key (reuses locks); no indexed security keys in 1.0 |
| D4 | Scheduling default: schedule automatically when a scheduler exists, or require `schedulerName`? | Require `schedulerName` when basepull is enabled; validation error otherwise so the README promise is enforced |
| D5 | Full reindex strategy: alias swap (needs cluster privileges) or in-place reconcile semantics only? | Alias swap with documented privileges, in-place fallback when `createIndex` is denied |
| D6 | AWS IAM SigV4 in 1.0 (optional AWS SDK v2 dependencies, `authMode=AWS_SIGV4`), or declare unsupported and ship basic-auth FGAC only? | Include it: the transport ships inside opensearch-java, the SDK artifacts are optional, and IAM is the default posture for AWS domains; Serverless stays out |
| D7 | Pre-commit indexing (B1): use 4.1's `RecordChangeListenerInterface` plus `QBackendTransaction.addAfterCommitCallback`, or keep customizers and document pre-commit semantics with scheduled reconcile as the corrector? | Use the 4.1 listener and after-commit callback (reference: `qqq-esb` `EsbRecordChangeListener`); flush immediately when no transaction is passed; keep reconcile scheduled as the backstop; this makes 1.0 4.1-only (D10) |
| D8 | Real-time failure contract (B2): surface as QQQ record warnings plus a failed-event table (host write succeeds), or fail the host write when OpenSearch is down? | Warnings plus failed-event table; never fail the host write for a search index |
| D9 | Supported platforms statement for 1.0: self-managed 2.19.x and 3.x plus AWS managed domains (FGAC basic auth, and IAM if D6); Serverless, Elasticsearch and OpenSearch 1.x unsupported? | Yes, as stated; revisit Serverless in 1.x once `_delete_by_query` is removed from the design |
| D10 | Does 1.0 require qqq 4.1 (adopting `RecordChangeListenerInterface`, after-commit callbacks and `QRuntimeServiceInterface`), or keep a 4.0 fallback path with customizers and `MultiCustomizer`? | 4.1-only; 0.3.0 is the last 4.0 line; delete the customizers in 1.0 rather than carrying two real-time paths |
| D11 | `startupMode` default: `FAIL_FAST` (host refuses to boot when OpenSearch is unreachable) or `DEGRADED` (current behaviour, which qqq-all's `FullQBitsTest` relies on with OpenSearch at `127.0.0.1:1`)? | `FAIL_FAST` by default for production safety; qqq-all's full profile sets `DEGRADED` explicitly in its follow-up PR, and `DEGRADED` surfaces a health notice |

## Definition of Done

All six epics complete; success criteria met; 1.0.0 published on Central and pinned by qqq-all and Voyage; README, configuration reference, migration notes and CHANGELOG accurate; every blocker in `docs/audits/2026-10-04/README.md` closed or deferred with a reason recorded here and in the second-brain project note.

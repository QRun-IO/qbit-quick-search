# TODO

## UI API 1.6 migration (2026-10-15)

- [x] Bump `quick-search` extension to 1.1.0, retain deprecated `QuickSearchErrorV1`, and use core `BasicErrorResponseV1` for errors; preserve operation IDs and success payloads.
- [x] Regenerate the two-operation contract with `-Dquicksearch.openapi.update=true`, then pass the contract test without regeneration; no dangling references or separate baseline were found.
- [x] Set the release BOM default to QQQ `4.1.0-RC.14`; validate offline against the locally installed `4.1.99-SNAPSHOT` using `./mvnw.cmd -o test -Dqqq.snapshot.version=4.1.99-SNAPSHOT -Denforcer.skipRules=requireReleaseDeps` (540 tests, no failures/errors/skips). The command-line exception is needed only because the requested local core is a SNAPSHOT; other guardrails remain enabled.
- [x] Verify strengthened HTTP error-body assertions: 8 status/HTTP tests passed after the full suite.
- [ ] Run Docker integration tests through `verify` in CI; this migration ran the requested `test` lifecycle.


## In-app help bundle (2026-10-14)

- [x] Pin QQQ RC.7, register the host-neutral QBIT bundle, replace the temporary loader, document HOST overrides, and add coverage/no-help-table checks.
- [ ] Host adoption: enable QQQ help startup sync and keep deployment schedules and host-table record actions in HOST bundles.

## Real-time error counter

- [x] Count failed real-time events after commit, serialize concurrent listeners within a JVM, tolerate counter-write failures, and reset after successful basepull/full reindex; add regression tests and field/documentation help.

## Architecture status API (#50 / F10)

- [x] Supported Java status API, bounded cache/rate limiting, administrator authorization, home navigation, spec endpoint and committed fragment.
- [x] Contract and strict HTTP tests; host migration mapping for kof22#164.
- [ ] kof22#164: replace internal probes/duplicate home using `docs/STATUS-API-HOST-MIGRATION.md`.
- [ ] Next UI: vendor the additive Quick Search status fragment when adopting RC.6.
- [ ] Run Docker integration tests in CI; local Docker is unavailable.
- [ ] Resolve the pre-existing repository Checkstyle baseline (438 remaining main-source violations, down from 471 at HEAD); status API adds none.

## Before tagging 1.0.0

- [ ] qqq-all and Voyage adopt `1.0.0-RC.2` (items below) and report back.

- [x] PR #10 (`feature/GH-1-0-release-readiness`) opened and reviewed 2026-10-05; `AGENTS.md` kept.
- [ ] Re-pin to `qbit-build-parent` 2.1.0 when qbit-bom publishes it, and remove the `activeByDefault` on the `qqq-snapshot` profile (ADR-0007).
- [ ] qqq-all follow-up PR: remove the manual `instance.addQBit(...)` for quick-search in `DemoQBits`, pass `withSchedulerName("demoScheduler")`, set `withStartupMode(QuickSearchStartupMode.DEGRADED)` for the full profile test, pin 1.0.0.
- [ ] Voyage: pin 1.0.0, set `schedulerName`, switch to `opensearchUrl` and `${env.}` credentials.
- [ ] CI: add a second test job with `-Dopensearch.test.image=opensearchproject/opensearch:3.9.0` (needs a qqq-orb parameter for extra Maven args, or a plain job).
- [x] Core SPI proposed as QRun-IO/qqq#1042 (D1); the 1.1 adapter `QuickSearchRecordSearchProvider` is on `feature/record-search-provider`.
- [x] Tracking issue #12 created and recorded in the PRD (2026-10-06).
- [x] PR #8 merged 2026-10-05; Dependabot PR #1 closed as superseded.
- [x] PR #10 merged and `1.0.0-RC.1` published to Maven Central from `release/1.0` (2026-10-05).
- [ ] Upstream: tell qbit-bom that the published parent POM has `<version>${revision}</version>`, which Maven 3.10 rejects (this repo works around it with the wrapper on 3.9.11).

## Before 1.0.0 GA (from the PR #10 review, 2026-10-05; filed as #13 to #17 under #12)

- [x] #13 Listener: a failed in-transaction re-read or a missing AWS SDK (`LinkageError`) records `quickSearchFailedEvent` rows (PR #22).
- [x] #14 Full reindex: deletes made during a rebuild are captured (`REBUILDING`, `AWAITING_REINDEX`) and applied after the alias swap (PR #20).
- [x] #15 Transport: warn on `tls.hostnameVerification=false`, empty `${env.X}` treated as missing, userinfo in `opensearchUrl` rejected (PR #18).
- [x] #16 Search: `offset` counts readable results under record locks, per-table `offset`, bounded scan and 10,000 window cap (PR #19).
- [x] #17 `safeCount` logs at warn and keeps the previous `documentCount` (PR #21).
- [x] `1.0.0-RC.2` published to Maven Central from `release/1.0` (2026-10-06).
- [ ] Stuck `REBUILDING` after a killed or overlapping full reindex (from the PR #20 review): only another all-tables reindex clears it, `AWAITING_REINDEX` rows accumulate and drift detection stays off for those tables. No data is lost. Fix needs a liveness check on the `FULL_REINDEX` run record before reconcile may reset the status, plus refusing a second concurrent full reindex.
- [ ] Drift detection still writes `NEEDS_REINDEX` into `status`, so a few-millisecond window can overwrite `REBUILDING`; the durable fix is a separate drift field (schema change, so 1.1 at the earliest).

## Before releasing 1.1.0

- [x] Re-pointed the `qqq-snapshot` profile from `4.1.0-SNAPSHOT` to `4.1.0-RC.2`, the first QQQ release with the record-search provider SPI (2026-10-11).
- [ ] qqq-all / Website-Backend: confirm the Next UI search box is served from OpenSearch (provider registered, indexed tables have `searchFields`).

## Later (1.x)

- [ ] Health widget for the admin app (data exists: `isHealthy`, `documentCount`, `lastRunStatus`).
- [ ] `IndexEvent` sequence number so an async publisher can order INDEX and DELETE; promote the publisher SPI from experimental.
- [ ] Amazon OpenSearch Serverless (needs removal of `_delete_by_query` and `_refresh` from the design).
- [ ] Jev table routing prototype; see `docs/reference/typesafe-jev-candidates.md`.

## Done

- [x] Status assessment, deep-dive audit (25 blockers), PRD (2026-10-04).
- [x] 4.1.0-RC.1 compatibility verified (2026-10-04).
- [x] All 25 audit blockers implemented and tested on the release branch (2026-10-04).

## Secured-data section 15 (2026-10-14)

- [x] Review fix: separate protection from indexability; preserve safe labels, ordinary results and unprotected hidden keys/timestamps, with regression tests.

- [x] Exclude PII/PHI, hidden, sensitive, password and field-locked values from indexing and results on RC.6.
- [x] Add canary coverage for real-time indexing, basepull and stale results; refuse classified config fields.
- [ ] On the QQQ RC.7 upgrade, honour `ClassifiedValues.isClassified(table, field)` (qqq#1182) and `FieldAccessHelper.getLockTier` (qqq#1172).
- [ ] Deployment: rebuild existing indexes and apply host retention policy to old physical indexes/backups containing previously indexed values.

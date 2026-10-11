# TODO

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

- [ ] Re-point the `qqq-snapshot` profile from `4.1.0-SNAPSHOT` to the QQQ release that carries the record-search provider SPI (QRun-IO/qqq#1042).
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

# TODO

## Before tagging 1.0.0

- [ ] qqq-all and Voyage adopt `1.0.0-RC.1` (items below) and report back.

- [x] PR #10 (`feature/GH-1-0-release-readiness`) opened and reviewed 2026-10-05; `AGENTS.md` kept.
- [ ] Re-pin to `qbit-build-parent` 2.1.0 when qbit-bom publishes it, and remove the `activeByDefault` on the `qqq-snapshot` profile (ADR-0007).
- [ ] qqq-all follow-up PR: remove the manual `instance.addQBit(...)` for quick-search in `DemoQBits`, pass `withSchedulerName("demoScheduler")`, set `withStartupMode(QuickSearchStartupMode.DEGRADED)` for the full profile test, pin 1.0.0.
- [ ] Voyage: pin 1.0.0, set `schedulerName`, switch to `opensearchUrl` and `${env.}` credentials.
- [ ] CI: add a second test job with `-Dopensearch.test.image=opensearchproject/opensearch:3.9.0` (needs a qqq-orb parameter for extra Maven args, or a plain job).
- [ ] Open the qqq issue proposing `RecordSearchProviderInterface` (D1); quick-search adapter lands in 1.1.
- [ ] Create the GitHub tracking issue for the 1.0 initiative; write its number into the PRD frontmatter.
- [x] PR #8 merged 2026-10-05; Dependabot PR #1 closed as superseded.
- [x] PR #10 merged and `1.0.0-RC.1` published to Maven Central from `release/1.0` (2026-10-05).
- [ ] Upstream: tell qbit-bom that the published parent POM has `<version>${revision}</version>`, which Maven 3.10 rejects (this repo works around it with the wrapper on 3.9.11).

## Before 1.0.0 GA (from the PR #10 review, 2026-10-05)

- [ ] Listener: if the in-transaction re-read in `fetchCurrentRecords` throws, record a `quickSearchFailedEvent` row from the event's primary keys instead of dropping the update (`QuickSearchRecordChangeListener`).
- [ ] Full reindex: deletes that arrive during a rebuild are not replayed into the new index; capture them (failed-event or a delete log) and apply after the swap.
- [ ] Transport: warn when `tls.hostnameVerification=false`; treat an empty `${env.X}` value as missing and name the field; reject userinfo in `opensearchUrl`.
- [ ] Search: with record locks on, `offset` counts raw hits, so pages can overlap; per-table mode ignores `offset`; cap `fetchSize` at the 10,000 window.
- [ ] `safeCount` in `BasepullIndexStep` swallows exceptions silently; log at warn.

## Later (1.x)

- [ ] Health widget for the admin app (data exists: `isHealthy`, `documentCount`, `lastRunStatus`).
- [ ] `IndexEvent` sequence number so an async publisher can order INDEX and DELETE; promote the publisher SPI from experimental.
- [ ] Amazon OpenSearch Serverless (needs removal of `_delete_by_query` and `_refresh` from the design).
- [ ] Jev table routing prototype; see `docs/reference/typesafe-jev-candidates.md`.

## Done

- [x] Status assessment, deep-dive audit (25 blockers), PRD (2026-10-04).
- [x] 4.1.0-RC.1 compatibility verified (2026-10-04).
- [x] All 25 audit blockers implemented and tested on the release branch (2026-10-04).

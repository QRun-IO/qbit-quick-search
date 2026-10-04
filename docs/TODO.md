# TODO

## Before tagging 1.0.0

- [ ] Review and merge `feature/GH-1-0-release-readiness` (8 commits; nothing pushed). Decide whether to keep `AGENTS.md`.
- [ ] Re-pin to `qbit-build-parent` 2.1.0 when qbit-bom publishes it, and remove the `activeByDefault` on the `qqq-snapshot` profile (ADR-0007).
- [ ] qqq-all follow-up PR: remove the manual `instance.addQBit(...)` for quick-search in `DemoQBits`, pass `withSchedulerName("demoScheduler")`, set `withStartupMode(QuickSearchStartupMode.DEGRADED)` for the full profile test, pin 1.0.0.
- [ ] Voyage: pin 1.0.0, set `schedulerName`, switch to `opensearchUrl` and `${env.}` credentials.
- [ ] CI: add a second test job with `-Dopensearch.test.image=opensearchproject/opensearch:3.9.0` (needs a qqq-orb parameter for extra Maven args, or a plain job).
- [ ] Open the qqq issue proposing `RecordSearchProviderInterface` (D1); quick-search adapter lands in 1.1.
- [ ] Create the GitHub tracking issue for the 1.0 initiative; write its number into the PRD frontmatter.
- [ ] Review and merge PR #8; close Dependabot PR #1 as superseded.
- [ ] Upstream: tell qbit-bom that the published parent POM has `<version>${revision}</version>`, which Maven 3.10 rejects (this repo works around it with the wrapper on 3.9.11).

## Later (1.x)

- [ ] Health widget for the admin app (data exists: `isHealthy`, `documentCount`, `lastRunStatus`).
- [ ] `IndexEvent` sequence number so an async publisher can order INDEX and DELETE; promote the publisher SPI from experimental.
- [ ] Amazon OpenSearch Serverless (needs removal of `_delete_by_query` and `_refresh` from the design).
- [ ] Jev table routing prototype; see `docs/reference/typesafe-jev-candidates.md`.

## Done

- [x] Status assessment, deep-dive audit (25 blockers), PRD (2026-10-04).
- [x] 4.1.0-RC.1 compatibility verified (2026-10-04).
- [x] All 25 audit blockers implemented and tested on the release branch (2026-10-04).

# Session State

Updated: 2026-10-04

## Context

Repo `QRun-IO/qbit-quick-search`, branch `feature/GH-6-repin-and-drift-fix`. Its content is identical to `origin/develop` (`000e5bb`, the squash of PR #7); the branch itself is finished. Untracked: `AGENTS.md` (Codex copy of CLAUDE.md) and everything under `docs/` written this session. Nothing committed.

## This session

- Status assessed: 0.2.1-SNAPSHOT on `qbit-build-parent` 2.0.0 (qqq 4.0.0). 233 unit tests green on qqq 4.0.0, 4.1.0-SNAPSHOT and 4.1.0-RC.1; `mvn verify` on RC.1 with Docker passed all 12 integration tests. JaCoCo check is a no-op (no agent bound).
- Deep-dive audit run as five parallel code-grounded reviews (QQQ 4.1 contract, OpenSearch and AWS compatibility, authentication and secrets, feature completeness, correctness). Consolidated in `docs/audits/2026-10-04/README.md` with 25 blockers B1 to B25; raw reports beside it. Spot-check corrected one agent error (Voyage does pin 0.2.0 and uses the QBit on origin/develop).
- Key new facts: QQQ 4.1 adds `RecordChangeListenerInterface`, `QBackendTransaction.addAfterCommitCallback` and `QRuntimeServiceInterface` (absent in 4.0.0, verified by tag); core 4.1 `RecordSearchAction` has no provider SPI; opensearch-java 2.10.0 is the end-of-life line and 3.10.0 needs a one-line fix.
- PRD rewritten: `docs/initiatives/quick-search-1.0.md` now has six epics (platform and contract, index trust, search quality and permissions, UI integration, connection security and auth, ops and docs) and eleven sponsor decisions D1 to D11.
- Jev candidates parked in `docs/reference/typesafe-jev-candidates.md`.

## Upstream state

- QQQ 4.1.0-RC.1 published 2026-10-02 (pre-release). Parent 2.1.0-RC.1 and quick-search 0.3.0-RC.1 proposed in qqq#921; qqq-all BOM pins 0.2.1-SNAPSHOT.
- PR #8 (`feature/GH-10-public-stack-prep`) green, awaiting review. Dependabot PR #1 superseded by #8.

## Implementation progress (goal: all audit issues, 1.0 ready)

Branch `feature/GH-1-0-release-readiness`. Build with `./mvnw` (Homebrew Maven 3.10.0 rejects the parent POM's `${revision}` version; the wrapper pins 3.9.11). Plan: `docs/PLAN-1.0-release.md`.

- Task 1 done (commit 90cd09d): qqq 4.1.0-RC.1 via default-active profile, opensearch-java 3.10.0, JaCoCo agent, JUnit from parent, image property `opensearch.test.image`.
- Task 2 done (commit f18bf19): config surface, `QuickSearchRuntime`, deprecated `QuickSearchQBitContext` facade, validation tests.
- Tasks 3+4 in progress (uncommitted until green): `QBitMetaDataProducer` producer with component producers in `metadata/`, `QuickSearchRuntimeService`, `listeners/QuickSearchRecordChangeListener` (after-commit publish, failed-event capture), `quickSearchFailedEvent` table, customizers deleted, producer tests rewritten, listener tests added.
- Remaining: task 5 client (auth modes, TLS, timeouts, mapping v2, aliases, exception wrapping), task 6 steps (keyset paging, watermark, enabled, reindex alias swap, orphan sweep, drift, purge, failed-event replay, display values), task 7 search (permissions, bounds, tableNames, tableLabel), task 8 admin surface, task 9 docs and CI, task 10 close-out.

## Next

See `docs/TODO.md`. Sponsor decisions D1 to D11 come first; then break Epic 1 into stories per the migration plan in `docs/audits/2026-10-04/01-qqq-4.1-integration.md`.

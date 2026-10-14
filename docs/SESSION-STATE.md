# Session State

Updated: 2026-10-13

## Status API worktree (2026-10-13, issue #50 / F10)

- Implemented `api.QuickSearchStatusService.get()`, immutable status values, default app/home constants and a spec-managed status endpoint. Admin permission is checked on every call; saved validation counts avoid source scans. Five-second per-runtime cache, two-second wait, one in-flight worker, existing diagnostics rate limiter, bounded table/backlog reads.
- Endpoint uses core's permitted `/qqq/v1/qbit/quick-search/status` namespace (`getQuickSearchStatus`). Requested `/quick-search` is rejected by RC.6's established-prefix allowlist. Core and host clones are read-only; no edits there. No previous Quick Search HTTP operations or Next UI callers found.
- POM targets released-coordinate `4.1.0-RC.6`; local verification uses only `-Dqqq.snapshot.version=4.1.0-ARCHRC6-SNAPSHOT`.
- Host file/line replacement mapping: `docs/STATUS-API-HOST-MIGRATION.md`. Contract: `src/main/resources/openapi/qqq-v1/quick-search.yaml`.
- Verification: 468 unit tests (including strict HTTP, contract, bounds, cache isolation, permissions and timeout recovery); JaCoCo passes. All 41 Docker integration tests skip because Docker is unavailable. Final command: `./mvnw.cmd -B verify -Dqqq.snapshot.version=4.1.0-ARCHRC6-SNAPSHOT`.
- Standalone repository Checkstyle is not green: HEAD has 471 main-source violations; this tree has 438, with zero newly introduced violations. New production files and both new test files pass their style checks. Existing header/import/style cleanup is outside the status API change.
- No commit, push, merge or deployment authorized. Second-brain vault is outside writable roots; durable notes are retained here for later vault synchronization.

## Context

`1.0.0-RC.2` is released (2026-10-06). It fixes the five pre-GA findings from the PR #10 review, each on its own branch and PR into `develop`:

| Issue | PR | Merge commit | Fix |
|---|---|---|---|
| #15 | #18 | `2398e62` | Transport: warn on `hostnameVerification=false`, blank `${env.X}` treated as missing, userinfo in `opensearchUrl` rejected |
| #17 | #21 | `e9ec54a` | `safeCount` logs at warn and keeps the previous `documentCount` |
| #16 | #19 | `9941a70` | Search `offset` counts readable results under record locks; bounded scan; 10,000 window cap |
| #14 | #20 | `dce0f26` | Deletes during a full reindex captured (`REBUILDING`, `AWAITING_REINDEX`) and applied after the alias swap |
| #13 | #22 | `c5c41d4` | Listener re-read failure and missing AWS SDK (`LinkageError`) recorded as failed events |

`develop` (`c5c41d4`) was merged into `release/1.0` as `b7d366c` (only difference from develop: `<revision>1.0.0-RC.1</revision>`). The orb's `publish_release_candidate` workflow succeeded on `b7d366c` (CircleCI build 82) and pushed `c11b2c2` ("Bump version to 1.0.0-RC.2 [skip ci]"). Maven Central serves the jar, pom, sources, javadoc and signatures for `com.kingsrook.qbits:qbit-quick-search:1.0.0-RC.2`.

## Release mechanics (qqq-orb 0.6.8, confirmed in its source)

- RCs publish artifacts only: no git tag and no GitHub release. The GitHub release comes when the release branch merges to `main` and a `vX.Y.Z` tag is pushed.
- Never push a `vX.Y.Z-RC.n` tag: it matches the GA job's `v*` filter, maps back to `release/X.Y.Z`, and would publish a stray `RC.n+1`.
- The next push to `release/1.0` publishes `1.0.0-RC.3`. Keep the release branch's `<revision>` on merge.
- `develop` requires one approving review and the PR author cannot approve their own PR; James merges with `gh pr merge <n> --squash --admin`. The agent cannot run `gh pr merge`, and the auto-mode classifier also refused a background loop that polled PR merge state.
- GitHub SSH times out from this machine; push over HTTPS with `git -c credential.helper='!gh auth git-credential' push https://github.com/QRun-IO/qbit-quick-search.git <ref>`.

## Next

1. qqq-all and Voyage pin `1.0.0-RC.2` and report back; their feedback decides RC.3 or GA.
2. Remaining "Before 1.0.0 GA" items in `docs/TODO.md`: stuck `REBUILDING` after a killed or overlapping full reindex (from the PR #20 review), and the `qbit-build-parent` 2.1.0 re-pin.
3. GA: merge `release/1.0` to `main` and tag `v1.0.0`.

# Session State

Updated: 2026-10-06

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

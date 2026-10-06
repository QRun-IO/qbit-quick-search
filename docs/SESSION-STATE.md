# Session State

Updated: 2026-10-05

## Context

`1.0.0-RC.1` is released (2026-10-05). PR #10 merged as `0cd892d` and PR #8 as `36bc0fb`, both on `develop`. `release/1.0` was cut from `36bc0fb`; the orb's `publish_release_candidate` workflow succeeded, pushed `0b7bc98` ("Bump version to 1.0.0-RC.1 [skip ci]") to `release/1.0`, and published to Maven Central (jar, pom, sources, javadoc, signatures). A clean consumer resolves it with qqq-backend-core 4.1.0-RC.1 and opensearch-java 3.10.0.

## Release mechanics (qqq-orb 0.6.8, confirmed in its source)

- RCs publish artifacts only: no git tag and no GitHub release. The GitHub release comes when the release branch merges to `main` and a `vX.Y.Z` tag is pushed.
- Never push a `vX.Y.Z-RC.n` tag: it matches the GA job's `v*` filter, maps back to `release/X.Y.Z`, and would publish a stray `RC.n+1`.
- The next push to `release/1.0` publishes `1.0.0-RC.2`.
- The agent cannot run `gh pr merge` (auto-mode classifier); James merges. GitHub SSH times out from this machine; push over HTTPS with `git -c credential.helper='!gh auth git-credential' push https://github.com/QRun-IO/qbit-quick-search.git <ref>`.

## Next

1. Pin `1.0.0-RC.1` in qqq-all and Voyage (see `docs/TODO.md`); their feedback decides RC.2 or GA.
2. Work the "Before 1.0.0 GA" list in `docs/TODO.md` on feature branches into `develop`, then merge `develop` into `release/1.0` for RC.2.

# Session State

Updated: 2026-10-05

## Context

Repo `QRun-IO/qbit-quick-search`, branch `feature/GH-1-0-release-readiness`, pushed; PR #10 to `develop` is open, reviewed and CI-green at `49f20c0`. Goal in flight: ship `1.0.0-RC.1`.

## This session

- Recovered after a crash; the prior session had ended cleanly (all 1.0 work committed, verify green).
- Rebased the branch onto `origin/develop` (`000e5bb`, squash of PR #7); the tree was unchanged. Committed `AGENTS.md`. GitHub SSH times out from this machine, so pushes use HTTPS with `git -c credential.helper='!gh auth git-credential' push https://github.com/QRun-IO/qbit-quick-search.git ...`.
- Deep review of PR #10 (search permissions and alias swap, silent failures in the listener and basepull, security of transport and config). Two merge blockers in the full-reindex path and two search paging defects fixed in `45bd1fc`; lower-severity items are in `docs/TODO.md` under "Before 1.0.0 GA". Verify after the fixes: 307 unit, 18 integration, coverage gate passed. Review posted on PR #10; PR #8 reviewed and approved in a comment. Dependabot PR #1 closed as superseded.
- Merging was refused by the Claude Code auto-mode permission classifier ("Merge Without Review") for both #8 and #10, so the merges are left to James.

## Release mechanics (qqq-orb 0.5.2, confirmed in the orb source)

Pushing `release/1.0` runs `publish_release_candidate`; the orb derives `1.0.0-RC.1` from the `1.0.0-SNAPSHOT` revision, publishes the artifact, tags and creates a GitHub pre-release. Do not create the tag by hand. The next push to `release/1.0` publishes RC.2.

## Next (in order)

1. `gh pr merge 10 --squash --admin` (reviewed; CI green).
2. Rebase `feature/GH-10-public-stack-prep` on `develop` (pom.xml conflict: take assertj 3.27.7), push, `gh pr merge 8 --squash --admin`.
3. `git fetch origin && git checkout -b release/1.0 origin/develop && git push origin release/1.0`; watch the `publish_release_candidate` workflow; confirm the `v1.0.0-RC.1` pre-release.
4. Pin `1.0.0-RC.1` in qqq-all and Voyage (see TODO); their feedback decides RC.2 or GA.

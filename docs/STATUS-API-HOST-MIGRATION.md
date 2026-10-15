# Quick Search host migration (kof22 #164)

Issue #50 / architecture finding F10. Paths below are under
`Website-Backend/src/main/java/com/kof22/website/metadata/adminhome/`;
line numbers refer to the local clone inspected on 2026-10-13. No host files were changed.

| Existing host code | Replacement |
| --- | --- |
| `LiveAdminHomeProbes.java:340` `searchConfigured()` | Use `QuickSearchApi.APP_NAME` for the default-name app lookup; for an authorized snapshot use `QuickSearchStatusService.get()` and its state/homeHref. `DISABLED` also includes all indexes disabled, so it is not exactly “qbit absent.” |
| `LiveAdminHomeProbes.java:351` `searchCluster()` | One `QuickSearchStatusService.get()`; map `clusterReachable`, `clusterHealth`, `nodes`, `activeShards`, `unassignedShards`. Remove direct cluster HTTP/auth/CA handling. |
| `LiveAdminHomeProbes.java:386` `searchDocumentCounts(...)` | Reuse the same snapshot's `indexes()` and each row's `tableName()` / `documentCount()` / `sourceCount()` / `countsCheckedAt()`. Remove `QuickSearchOpenSearchClient` access and relabel counts as saved diagnostics, not live. Preserve null as unknown. |
| `QuickSearchHomeWidgets.java:118` `widgets()` | Stop adding the host's duplicate Quick Search home widgets. Link to `status.homeHref()` (or default `QuickSearchApi.HOME_HREF`). Keep the qbit-provided home/diagnostics widget. |
| `QuickSearchHomeWidgets.java:133` `indexFacts()`, `:160` `summary()` and `:196` `syncTile()` | For a compact host overview, use the same `QuickSearchStatusService.get()` value: `state`, `indexes`, `pending`, `exhausted`, `lastSuccessfulSync`, `lastError`. Remove SQL aggregates and live source counts; use saved count timestamps and truncation flags. |
| `QuickSearchHomeWidgets.java:267` `runs()`, `:315` `failedEvents()` | Replace duplicate detailed views with the home link; drill down using the qbit's own Index Run / Failed Event tables from that home. The summary API intentionally does not export detailed histories. |
| `AdminOperationsWidgets.java:209` `quickSearch()` | Render `status.indexes()` for the compact admin overview, and use `status.homeHref()` for its footer link. Remove qbit-table SQL and source-table counts. |
| `AdminOperationsWidgets.java:343` `searchFooter()` | Use `clusterHealth`, `pending`, `exhausted`, `lastSuccessfulSync`, `lastError`, and `checkedAt` from the same snapshot. Detailed recent-run information belongs in the qbit home. |

Call with the existing request's `QContext`/`QSession`; do not elevate a host user's
session to bypass `adminPermissionRules`. Cache hits still check permission.

## UI contract

| Method | Path | operationId |
| --- | --- | --- |
| GET | `/qqq/v1/qbit/quick-search/status` | `getQuickSearchStatus` |
| GET | `/qqq/v1/qbit/quick-search/suggest` | `getQuickSearchSuggestions` |

Suggestions require only a signed-in session, with table READ permissions and
configured record locks applied to results. Do not gate the typeahead UI on the
status endpoint's admin permission. Send `q`, optionally `limitPerTable` (default 5,
maximum 10), `maxTables` (default 8, maximum 20), and comma-separated `tables`.
The response contains `query`, relevance-ordered `groups` and `truncated`; each
group has `tableName`, `tableLabel`, `hasMore` and `items` of `recordId`, `label`,
and nullable `subtitle`. See the [API details](../README.md#typeahead-suggestions)
for normalization, bounded-batch semantics and empty responses during outages.

Extension: `quick-search`, version `1.1.0`; core dependency: `4.1.0-RC.15` (UI API `1.6.0`).
The requested `/quick-search` prefix cannot validate on the supplied RC.6 baseline:
`MiddlewareSpecExtensionsMetaData.ESTABLISHED_PREFIXES` only admits esb, alerts,
assistant, and sql-workbench. We use the default namespace without modifying core.

The suggestions change adds a second endpoint and its schemas; the status
method, path, request and response are unchanged. Next UI `src/` contains no calls to a
Quick Search-specific endpoint today; its core `/qqq/v1/search` integration is unchanged.
Vendor `src/main/resources/openapi/qqq-v1/quick-search.yaml` for clients.

Regenerate the fragment with:

```powershell
./mvnw.cmd -B -Dtest=QuickSearchApiContractTest -Dquicksearch.openapi.update=true test
./mvnw.cmd -B -Dtest=QuickSearchApiContractTest test
```

The fragment covers both registered specs using the committed dependency version.

## Suggestions verification (2026-10-14)

The two-spec contract regenerates and passes without the update flag. Final
`CI=true ./mvnw.cmd -B verify` passes 495 unit/guardrail tests, including strict
HTTP checks for reader access and invalid suggestion parameters. All 9 integration
classes fail their Docker prerequisite because this sandbox cannot access Docker's
named pipe; the 3 new suggest integration tests compile but remain unexecuted.
The configured JaCoCo gates pass when invoked separately with
`./mvnw.cmd -B jacoco:report jacoco:check@check` (74.38% instruction, 91.88% class).
New API files and changed tests pass repository Checkstyle; the full source check
still reports 446 existing violations.

## Original status verification and limits

`./mvnw.cmd -B verify -Dqqq.snapshot.version=4.1.0-ARCHRC6-SNAPSHOT` passes
468 unit tests and JaCoCo, including strict HTTP server startup, permission checks,
OpenAPI contract consistency, cached/disabled/degraded/down states, timeout recovery,
bounded backlog/table lists and prefixed QInstance isolation. All 41 Docker integration
tests skip locally because Docker is unavailable.

The separate Checkstyle check is **not green**: the existing checked-in rules report
471 main-source violations at HEAD and 438 in this worktree, with zero new violations.
New source files and the two new tests pass those rules. No repository-wide header
rewrite or unrelated style cleanup was included.

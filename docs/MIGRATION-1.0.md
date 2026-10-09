# Migrating from 0.x to 1.0

1.0 is a trust and platform release. Hosts that used the 0.2 fluent API keep compiling; the steps below are what changes at runtime.

## Prerequisites

- QQQ 4.1 (the QBit uses record-change listeners, after-commit callbacks and runtime services, none of which exist in 4.0). Until `qbit-build-parent` 2.1.0 is released, the QBit builds against 4.1.0-RC.1.
- OpenSearch 2.19.x or 3.x, or an Amazon OpenSearch Service managed domain. Serverless is not supported.

## Required changes

1. **Remove any manual `QBitMetaData` registration.** The producer now registers `com.kingsrook.qbits:quick-search` itself; a second `addQBit` with that name throws at startup. (qqq-all's `DemoQBits` did this in 0.x.)
2. **Decide the startup mode.** `startupMode` defaults to `FAIL_FAST`: `produce()` fails when OpenSearch is unreachable. Demo profiles that may run without a cluster should set `withStartupMode(QuickSearchStartupMode.DEGRADED)`.
3. **Run a full reindex once after upgrading.** The index mapping changed (version 2: search analyzer, text-typed `fieldValues`). The QBit logs a warning when it finds an index with the old mapping; "Quick Search Full Reindex" with no table name builds a new physical index and swaps the configured name to an alias. Search keeps working during the rebuild. The identity the QBit uses needs index create, delete and alias privileges for this (see the README role).
4. **Set `schedulerName`** (and optionally `basepullRepeatSeconds`, `reconcileCronExpression`) so basepull and reconcile actually run. In 0.x they were never scheduled.
5. **Credentials over HTTP are refused.** Use `opensearchUrl` with `https://`, or set `withAllowPlaintextCredentials(true)` for local development. Prefer `${env.OPENSEARCH_PASSWORD}` references over literal secrets; never name an OpenSearch secret `QQQ_ENV_*` (those are sent to the frontend).
6. **Grant the permissions.** Processes and the admin app now require `quickSearchAdmin.hasAccess`; the operational tables require read and write permissions. Grant the names listed under [Permissions](#permissions) to operators, or override `adminPermissionRules` and `tablePermissionRules`. Search now honours table read permissions and record security locks, so users see fewer results than in 0.x if they lacked access.
7. **Update the operational tables before starting 1.0.** `quickSearchIndex` gains five columns, two columns change type, and there is a new `quickSearchFailedEvent` table. A host that checks its columns at startup rejects the 0.x tables. See [Schema changes](#schema-changes).

## Permissions

QQQ names a permission `{baseName}.{type}`. The admin rules use the base name `quickSearchAdmin`; the table rules have no base name, so each table's name is its base name. With the default rules, grant these to operators:

| Permission | Covers |
|---|---|
| `quickSearchAdmin.hasAccess` | The `quickSearchAdmin` app and the three processes (`quickSearchBasepullIndex`, `quickSearchReconcileIndex`, `quickSearchFullReindex`) |
| `quickSearchIndex.read`, `quickSearchIndex.write` | The `quickSearchIndex` table |
| `quickSearchIndexRun.read`, `quickSearchIndexRun.write` | The `quickSearchIndexRun` table |
| `quickSearchFailedEvent.read`, `quickSearchFailedEvent.write` | The `quickSearchFailedEvent` table |

The tables use `READ_WRITE_PERMISSIONS`: `.read` covers query, get and count, and `.write` covers insert, edit and delete. If you set `tablePermissionRules` to `READ_INSERT_EDIT_DELETE_PERMISSIONS`, grant `.read`, `.insert`, `.edit` and `.delete` on each table instead. With `tableNamePrefix` set, the table permissions carry the prefix (`{prefix}quickSearchIndex.read`); the admin permission stays `quickSearchAdmin.hasAccess`.

Users who only search need none of these. Search checks the read permission of each searched table as that table's own rules name it (for example `customer.read`). The QBit's own reads and writes of its tables go through QQQ's table actions, which do not check permissions, so real-time indexing does not depend on these grants.

For a worked example, qqq-all's RC.2 adoption (QRun-IO/qqq-all#30) adds `infra/postgres/06-quick-search-permissions.sql`, which grants the seven names above to its admin role.

## Schema changes

The QBit declares its tables in metadata and ships no DDL. A backend that creates tables from metadata gets the 1.0 shape on a new database. A host that manages its own schema, or checks columns at startup, must change the 0.x tables before starting 1.0. Names below are QQQ field names and types; your backend's naming decides the physical table and column names.

`quickSearchIndex`:

| Field | Type | Change from 0.x |
|---|---|---|
| `lastReconcileTime` | `DATE_TIME` | New |
| `lastRunStatus` | `STRING` | New. Run type and outcome, for example `FULL_REINDEX COMPLETED` (at most 22 characters) |
| `lastErrorMessage` | `TEXT` | New. Up to 4,000 characters |
| `documentCount` | `INTEGER` | New |
| `realTimeErrorCount` | `INTEGER` | New |
| `searchableFieldsJson` | `TEXT` | Was `STRING` |
| `tableName` | `STRING` | Now required, with a unique key |

`status` holds `ACTIVE`, `NEEDS_REINDEX` (new in 1.0) or `REBUILDING` (new in RC.2), so it needs at least 13 characters. QQQ checks the `tableName` unique key on insert; a unique index in the database also stops two concurrent first runs from both inserting. 0.x could leave duplicate rows, so keep one row per table before adding the index.

`quickSearchIndexRun` has no new columns. `errorMessage` changed from `STRING` to `TEXT` and holds up to 4,000 characters.

`quickSearchFailedEvent` is new:

| Field | Type | Notes |
|---|---|---|
| `id` | `INTEGER` | Primary key, generated by the backend like the other two tables |
| `tableName` | `STRING` | Source table |
| `recordId` | `STRING` | Source record's primary key as text |
| `action` | `STRING` | `INDEX` or `DELETE` |
| `errorMessage` | `TEXT` | Up to 4,000 characters |
| `attempts` | `INTEGER` | Replay attempts |
| `status` | `STRING` | `PENDING`, `EXHAUSTED` or `AWAITING_REINDEX` (new in RC.2); size it for at least 16 characters |
| `createDate`, `modifyDate` | `DATE_TIME` | |

The QBit sets no maximum length on its `STRING` fields. Map `STRING` to a character column sized for the values above, `TEXT` to a type that holds at least 4,000 characters, and `DATE_TIME` to a timestamp, the same way your other QQQ tables do.

If your backend creates tables from metadata and you do not need the 0.x run history, you can instead drop the 0.x `quickSearchIndex` and `quickSearchIndexRun` tables and let startup recreate them, as qqq-all#30 documents. The next basepull, reconcile or full reindex recreates each `quickSearchIndex` row, with `enabled` and `basepullIntervalMinutes` back at their configured defaults.

## Behaviour changes to be aware of

- Real-time indexing happens after commit. Code that searched for a record inside the same transaction that inserted it will not find it until commit.
- Real-time failures no longer vanish: they land in `quickSearchFailedEvent` and are replayed by the next basepull. Review that table after an OpenSearch outage.
- A table without the change-detection field (`modifyDate` by default) is indexed on the first basepull and thereafter only by reconcile.
- `limit` is capped (default 100), `offset + limit` at 10,000, and search terms must be 2 to 100 characters.
- Query semantics changed from OR over n-grams to AND over whole terms with prefix matching: "blue widget" now requires both words.
- `enableScheduledProcesses` still works and sets both `enableBasepullProcess` and `enableMaintenanceProcesses`.
- `QuickSearchQBitContext` is deprecated and removed in 1.1; use `QuickSearchRuntime.get()`.

## Removed

- `QuickSearchPostInsertCustomizer`, `QuickSearchPostUpdateCustomizer`, `QuickSearchPostDeleteCustomizer`. Hosts that referenced them (for example to compose with their own customizers) should remove those references; the record-change listener coexists with host customizers.
- `QuickSearchOpenSearchClient.search(String term, String tableName, ...)`; use the collection overload.

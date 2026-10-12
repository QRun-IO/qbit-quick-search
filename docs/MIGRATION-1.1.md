# Migrating from 1.0 to 1.1

1.1 lets QQQ core's record search (`POST /qqq/v1/search`, the Next UI global search box) use the Quick Search index. Hosts that do nothing get it by default.

## Prerequisites

- The QQQ release that carries the record-search provider SPI (`RecordSearchProviderInterface`, `QInstance.withRecordSearchProvider`), first released in QQQ `4.1.0-RC.2`. 1.1 builds against `4.1.0-RC.2` through the `qqq-snapshot` profile.

## What changes at startup

1. **The QBit registers a record-search provider.** `QuickSearchRecordSearchProvider` becomes the instance's `recordSearchProvider`. If the host already registered a different provider, the QBit keeps it and logs a warning; core search then does not use OpenSearch.
2. **Indexed tables get `searchFields`.** Core only searches tables with `searchFields`. Each indexed table that has none gets its visible string and integer searchable fields. Tables that already have `searchFields` keep them. A table added to the instance after the QBit is produced gets none, so add tables first (as for field validation) or set `searchFields` yourself.
3. **Password fields are no longer indexed.** Fields of type `PASSWORD` are now dropped from the searchable fields like hidden fields. If a 1.0 index holds such values, run "Quick Search Reconcile Index" (or a full reindex) to rewrite the documents without them.

## Behaviour to be aware of

- Next UI search results for indexed tables now come from OpenSearch: all words must match (in any searchable field), with prefix matching and field weights, instead of a contains-match on one field. Tables not indexed by Quick Search keep core's search.
- Results still pass core's security: core re-reads every hit through `QueryAction`, so table permissions and record security locks apply, and records deleted since they were indexed do not appear.
- Records are only as fresh as the index: a record updated in a way that is not yet indexed (for example while real-time indexing is off) is found by its indexed text.
- When OpenSearch is slow or down, core waits at most `recordSearchTimeoutMillis` (default 2500) and then searches with its own contains-query; for the next 30 seconds it skips OpenSearch entirely.

## Opting out

```java
new QuickSearchQBitConfig()
   // ...
   .withServeCoreRecordSearch(false);
```

restores 1.0 behaviour: no provider, no `searchFields` changes.

## Diagnostic tools (1.1.0-RC.2)

The QBit now adds admin tools to validate and debug search (see the README, "Diagnostic tools"). They are on by default.

- **Two new operational tables**, on `backendName` like the others: `quick_search_diagnostic_event` and `quick_search_validation_run` (columns in the README, "Operational tables"). Create them in the host's schema, with the naming the host applies to the other operational tables (`tableMetaDataCustomizer` applies to them too). Until they exist, writing events and saving reports fails quietly (logged at warn) and the tools still run.
- **Permissions.** Grant `quick_search_debug.hasAccess` to the people who may use the read-only tools and see the widget, `quick_search_admin.hasAccess` for Reindex in Search, and `.read` on the two new tables (the QBit writes them; users never do).
- **New processes** have a `tableName` (Quick Search Index, Quick Search Failed Event, and each indexed table), so they appear as table and record actions. They are not added to the admin app's children; a host that strips processes from its nav needs no change.
- **Opting out.** `withEnableDiagnosticTools(false)` adds none of it; `withEnableRecordDiagnosticActions(false)` keeps the tools on Quick Search Index but adds nothing to the indexed tables.

## Snake_case names (1.1.0-RC.3)

Owner naming rule (2026-10-12): table names are lowercase snake_case, and a QBit's tables are prefixed with its full name. RC.3 renames the QQQ metadata names. The QBit ships no DDL, so nothing is dropped or rewritten; what you change depends on how your database tables are named today.

| Kind | Before (RC.1, RC.2) | From RC.3 |
|---|---|---|
| Table | `quickSearchIndex` | `quick_search_index` |
| Table | `quickSearchIndexRun` | `quick_search_index_run` |
| Table | `quickSearchFailedEvent` | `quick_search_failed_event` |
| Table | `quickSearchDiagnosticEvent` | `quick_search_diagnostic_event` |
| Table | `quickSearchValidationRun` | `quick_search_validation_run` |
| Possible-value source | `quickSearchIndex`, `quickSearchSearchableTable` | `quick_search_index`, `quick_search_searchable_table` |
| Table permissions | `quickSearchIndex.read`, `quickSearchIndexRun.read`, ... | `quick_search_index.read`, `quick_search_index_run.read`, ... |
| Admin permission | `quickSearchAdmin.hasAccess` | `quick_search_admin.hasAccess` |
| Debug permission | `quickSearchDebug.hasAccess` | `quick_search_debug.hasAccess` |
| Record actions on failed events | `quickSearchInspect_quickSearchFailedEvent`, `quickSearchReindexRecords_quickSearchFailedEvent` | `quickSearchInspect_quick_search_failed_event`, `quickSearchReindexRecords_quick_search_failed_event` |

Unchanged: Java class names, field names (`quickSearchIndexId`, `tableName`), process, widget and app names (`quickSearchFullReindex`, `quickSearchDiagnostics`, `quickSearchAdmin` app), the OpenSearch index, and the developer panel id.

**With a table-name prefix** (`withTableNamePrefix` or the producer namespace), table and possible-value source names are now the prefix in snake_case joined with `_`: prefix `myApp_` or `myApp` gives `my_app_quick_search_index`, where RC.2 gave `myApp_quickSearchIndex`. Process, widget and app names keep the prefix as given.

**The old names stay available** as `@Deprecated` constants for upgrade scripts: `QuickSearchIndex.LEGACY_TABLE_NAME` (and the same on the other four entities), `QuickSearchSearchableTablePossibleValueSourceProducer.LEGACY_NAME`, and `QuickSearchQBitConfig.LEGACY_ADMIN_PERMISSION_BASE_NAME` / `LEGACY_DEBUG_PERMISSION_BASE_NAME`. They will be removed in 2.0.

### Database tables

- **Your tables are already `quick_search_*`** (for example you set the RDBMS backend name with `inferBackendName` or a table customizer): no DDL. You can drop the customizer, since the QQQ name now equals the database name.
- **Your tables are camelCase** (`"quickSearchIndex"`): rename them, in a maintenance window, before starting RC.3. PostgreSQL example:

```sql
ALTER TABLE "quickSearchIndex"           RENAME TO quick_search_index;
ALTER TABLE "quickSearchIndexRun"        RENAME TO quick_search_index_run;
ALTER TABLE "quickSearchFailedEvent"     RENAME TO quick_search_failed_event;
ALTER TABLE "quickSearchDiagnosticEvent" RENAME TO quick_search_diagnostic_event;
ALTER TABLE "quickSearchValidationRun"   RENAME TO quick_search_validation_run;
```

  Rename the matching sequences, indexes and constraints the same way if your tooling depends on their names. Data is untouched. If you cannot rename yet, point each table at its old name with an `RDBMSTableBackendDetails` table name in a table customizer.

### Stored references

QQQ stores table, process and permission names in your own data. Update them in the same window (PostgreSQL examples; adjust schema and column names to your instance):

```sql
-- audit history: audit_table.name holds the QQQ table name
UPDATE audit_table SET name = 'quick_search_index'            WHERE name = 'quickSearchIndex';
UPDATE audit_table SET name = 'quick_search_index_run'        WHERE name = 'quickSearchIndexRun';
UPDATE audit_table SET name = 'quick_search_failed_event'     WHERE name = 'quickSearchFailedEvent';
UPDATE audit_table SET name = 'quick_search_diagnostic_event' WHERE name = 'quickSearchDiagnosticEvent';
UPDATE audit_table SET name = 'quick_search_validation_run'   WHERE name = 'quickSearchValidationRun';

-- saved views and saved reports
UPDATE saved_view   SET table_name = 'quick_search_index' WHERE table_name = 'quickSearchIndex';
UPDATE saved_report SET table_name = 'quick_search_index' WHERE table_name = 'quickSearchIndex';
-- ... and the same for the other four tables
```

- **Permission grants.** Wherever your roles live (QQQ role tables, an identity provider's group attributes, code), replace `quickSearchAdmin.hasAccess` with `quick_search_admin.hasAccess`, `quickSearchDebug.hasAccess` with `quick_search_debug.hasAccess`, and `quickSearchIndex.read` / `.edit` (and the other tables' permissions) with the `quick_search_*` names. Users without the new grants lose the admin app and tools until they are granted.
- **Bookmarks and links** to `/app/quickSearchIndex/...` move to `/app/quick_search_index/...`.
- **Index rows and failed events** store *your* table names (`quick_search_index.table_name`, `quick_search_failed_event.table_name`), not the QBit's, so they need no change for this rename. The OpenSearch documents are keyed by your tables too, so **no reindex is needed**. If you rename your own tables at the same time, update those two columns and run a full reindex.

### Checklist

1. Back up the database.
2. Rename the tables (only if they are camelCase) and update the stored references above.
3. Update permission grants.
4. Upgrade to RC.3 and start; `startupMode` `FAIL_FAST` reports any table the backend cannot find.
5. Open Admin › Quick Search and run Validate Search Index.

## Data-driven configuration (1.1.0-RC.3)

From RC.3, which tables and fields Quick Search indexes is **data**: standard QQQ tables an admin edits, secured by permissions and audited. Code (`@QuickSearchable` / `@QuickSearchField` entities and `searchableTables`) still works, but it only **seeds** the records. Changes take effect without a redeploy.

### New tables and columns

| Table | One row per | Notes |
|---|---|---|
| `quick_search_index` (existing) | searchable table | New columns `source` (`CODE` or `OWNER`) and `owner_edited`. `basepull_timestamp_field` is now editable. The generic Create and Delete screens are off: use Add Search Index and Remove Search Index. |
| `quick_search_index_field` | searchable field of an index | `field_name`, `weight` (1-1000), `role` (`DISPLAY` T1 display, `TITLE`, `SUBTITLE`, `BODY`, `KEYWORD`, `EXACT`), `analyzer` (blank, `PREFIX`, `WORD`, `ENGLISH`, `EXACT`), `include_in_snippet` (blank: by role), `include_label`, `sort_order`, `source`, `owner_edited`. Unique on (`quick_search_index_id`, `field_name`). Edited from the index record's Searchable Fields list. |
| `quick_search_settings` | one row (id 1) | The global pause (`indexing_paused`, `paused_at`, `paused_by`, `pause_reason`) and the last self-priming report. Created by the QBit. |
| `quick_search_config_tombstone` | table or field an admin removed | Priming never adds back what a tombstone names. Delete the tombstone to let code seed it again. |
| `quick_search_index_run` (existing) | run | New columns `cancel_requested` and `cancel_requested_by` (Cancel Index Run). |

The config tables are audited at field level (QQQ's `audit` tables; without them the audit is skipped with a log line) and use `tablePermissionRules` (by default `quick_search_index_field.read`, `.edit`, and so on). The new processes use `adminPermissionRules` (`quick_search_admin.hasAccess`).

### Precedence: code versus data

1. **The records are the source of truth** once the config tables exist and priming has run. Indexing (real-time, basepull, reconcile, full reindex), search, the core record-search provider and the diagnostic tools read the effective config from them. It is cached for 30 seconds and dropped at once on the server where a config row changes, so an edit takes effect immediately there and within 30 seconds on other servers.
2. **Code seeds; it never overrides an owner.** At startup (and on each basepull until it succeeds), priming:
   - adds a row (source `CODE`, `enabled` per `enabledByDefault`) and its field rows for every code-declared table that has none, and queues its first build;
   - updates the interval and timestamp field of rows nobody edited (`owner_edited` false); `enabled` is set only when a row is created;
   - adds code-declared fields that have no row, and updates the weight and label flag of field rows nobody edited; either change queues a rebuild of that table;
   - never touches a row or field an admin added or edited (`owner_edited` true), and never re-adds anything a tombstone names;
   - never deletes anything: removing an annotation leaves the rows for the admin to remove.
3. **Removals are remembered.** Remove Search Index, and deleting a field row, write a tombstone, so code does not bring them back.
4. **Without the config tables** (before you add the DDL below), or until priming succeeds, Quick Search uses the code-declared tables exactly as before RC.3. So the upgrade can be staged.

### Self-priming

- A new code-declared table gets its rows on startup and is built by the next basepull (status `PENDING_BUILD`, then `BUILDING`, then `ACTIVE`; the run is recorded as `FULL_REINDEX`). No manual step is needed.
- A new install, or an index that was deleted, is recreated by the basepull, and the tables that had documents in it are rebuilt. An index that is empty while a table last counted documents in it is rebuilt too.
- The Search Diagnostics widget on the Quick Search home reports it ("New: 2 tables added and building (...)"), and `QuickSearchStatus.read(runtime).bannerHtml()` gives the same lines for a host's own home widget.
- Existing rows from RC.2 or earlier get their field rows on the first start, with **no rebuild** when the index was built with exactly the code's fields.

### Admin actions

All are standard streamed-ETL processes with a review step and a result step, run from the table's Actions (never from the nav):

| Action | On | What it does |
|---|---|---|
| Add Search Index | Quick Search Index (no selection) | Pick a table you can read that is not indexed and is not internal or secret; set the interval, timestamp field and enabled flag. The review shows the estimated record count, the fields it starts with (record-label fields as titles, then text fields) and what is never indexed (hidden and `isSensitive` fields). Saving queues the first build (or runs it at once when no scheduler runs the basepull). |
| Searchable Fields (child list on the index record) | Quick Search Index Field | Add, edit or remove fields with the standard screens; a field must exist, be indexable and not sensitive, and not be listed twice. The change applies to search and new writes at once and marks the index `NEEDS_REINDEX`. |
| Apply Search Index Changes | Quick Search Index (selected) | The review previews the field changes since the last build and the records the rebuild reads; executing rebuilds the table. |
| Disable Search Index, Remove Search Index | Quick Search Index (selected) | Stop indexing at once. The input step's "Search documents" choice (delete now, or keep) is restated on the review step. Remove also deletes the field rows and pending failed events and leaves a tombstone. Enabling a disabled index again (edit Enabled) queues a build. |
| Cancel Index Run | Quick Search Index Run (selected `RUNNING` runs) | Sets a cooperative cancel flag that the run checks between batches; it ends `CANCELLED` with its counts. A cancelled all-tables rebuild keeps the previous index; a cancelled queued build waits for Apply Search Index Changes. |
| Pause Indexing, Resume Indexing | Quick Search Settings | Pauses all indexing (real-time and scheduled; a running rebuild stops after its batch) while search keeps serving the current index. Changes made while paused are kept (`DEFERRED` failed events) and indexed by the basepull after Resume. The home shows "Paused since ... by ...". |

Roles and analyzers: `TITLE` fields make the hit's label, `SUBTITLE` its subtitle (`QuickSearchResult.subtitle`), `DISPLAY` fields its short details (`QuickSearchResult.displayValues`); `KEYWORD` and `EXACT` fields stay out of the highlighted snippet and match on their own. The `WORD`, `ENGLISH` and `EXACT` analyzers use new document objects (`fieldValuesWord`, `fieldValuesEnglish`, `fieldValuesExact`); their mapping is added to an existing index at startup (no mapping-version change), and documents get them as they are rebuilt. Tables of plain fields produce the same documents and queries as before.

### DDL

The QBit ships no DDL. PostgreSQL, with the snake_case column names QQQ infers (`QInstanceEnricher.setInferredFieldBackendNames`); use the field names as written if your backend maps fields to camelCase columns. Liquibase formatted SQL:

```sql
-- liquibase formatted sql

-- changeset quick-search:1.1.0-RC.3-1
ALTER TABLE quick_search_index ADD COLUMN IF NOT EXISTS source VARCHAR(20);
ALTER TABLE quick_search_index ADD COLUMN IF NOT EXISTS owner_edited BOOLEAN;
ALTER TABLE quick_search_index_run ADD COLUMN IF NOT EXISTS cancel_requested BOOLEAN;
ALTER TABLE quick_search_index_run ADD COLUMN IF NOT EXISTS cancel_requested_by VARCHAR(250);

-- changeset quick-search:1.1.0-RC.3-2
CREATE TABLE IF NOT EXISTS quick_search_index_field (
   id INTEGER GENERATED BY DEFAULT AS IDENTITY NOT NULL,
   quick_search_index_id INTEGER NOT NULL,
   field_name VARCHAR(255) NOT NULL,
   weight INTEGER,
   role VARCHAR(20),
   analyzer VARCHAR(20),
   include_in_snippet BOOLEAN,
   include_label BOOLEAN,
   sort_order INTEGER,
   source VARCHAR(20),
   owner_edited BOOLEAN,
   create_date TIMESTAMP WITHOUT TIME ZONE,
   modify_date TIMESTAMP WITHOUT TIME ZONE,
   CONSTRAINT pk_quick_search_index_field PRIMARY KEY (id),
   CONSTRAINT uq_quick_search_index_field UNIQUE (quick_search_index_id, field_name)
);

-- changeset quick-search:1.1.0-RC.3-3
CREATE TABLE IF NOT EXISTS quick_search_settings (
   id INTEGER GENERATED BY DEFAULT AS IDENTITY NOT NULL,
   indexing_paused BOOLEAN,
   paused_at TIMESTAMP WITHOUT TIME ZONE,
   paused_by VARCHAR(250),
   pause_reason VARCHAR(500),
   last_primed_at TIMESTAMP WITHOUT TIME ZONE,
   last_priming_summary TEXT,
   last_priming_new_tables INTEGER,
   create_date TIMESTAMP WITHOUT TIME ZONE,
   modify_date TIMESTAMP WITHOUT TIME ZONE,
   CONSTRAINT pk_quick_search_settings PRIMARY KEY (id)
);

-- changeset quick-search:1.1.0-RC.3-4
CREATE TABLE IF NOT EXISTS quick_search_config_tombstone (
   id INTEGER GENERATED BY DEFAULT AS IDENTITY NOT NULL,
   table_name VARCHAR(255) NOT NULL,
   field_name VARCHAR(255),
   removed_by VARCHAR(250),
   reason VARCHAR(500),
   create_date TIMESTAMP WITHOUT TIME ZONE,
   CONSTRAINT pk_quick_search_config_tombstone PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_quick_search_config_tombstone_table ON quick_search_config_tombstone (table_name);
```

MySQL: use `INT AUTO_INCREMENT` for the ids, `DATETIME` for the timestamps and `TINYINT(1)` for the booleans, and drop `IF NOT EXISTS` from `ADD COLUMN`.

### Behaviour changes to be aware of

- `quick_search_index` no longer offers Create or Delete (`TABLE_INSERT` and `TABLE_DELETE` are off), so the API cannot insert or delete rows either. Rows come from priming and Add Search Index, and go through Remove Search Index.
- Basepull builds queued tables (`PENDING_BUILD`) itself and recreates a missing index. `NEEDS_REINDEX` still waits for an admin (Apply Search Index Changes, or Rebuild).
- With the config tables, a table that is not in the instance is not part of the effective config, and `ensureIndexRowExists` no longer creates rows (priming does, honouring tombstones).
- Hidden and `isSensitive` fields are never indexed, whether declared in code or added as rows.
- `quick_search_searchable_table` is now a custom possible-value source over the effective tables (it was an enum of the code-declared ones).
- Tables added at runtime get core `searchFields` (for the global search) when they have none, but the per-table Inspect Search Document and Reindex in Search record actions appear only after the next restart.

### Checklist for data-driven configuration

1. Add the DDL above (it is additive).
2. Grant `quick_search_index_field`, `quick_search_settings` and `quick_search_config_tombstone` permissions (read, and edit where admins should change them) next to the existing Quick Search grants.
3. Upgrade and start. The Search Diagnostics widget should not say the searchable tables come from code, and shows the first priming's summary.
4. Optionally move code-declared search config into the records and delete the annotations: the rows stay and are now the owner's.

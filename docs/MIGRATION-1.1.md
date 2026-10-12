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

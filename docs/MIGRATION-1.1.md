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

- **Two new operational tables**, on `backendName` like the others: `quickSearchDiagnosticEvent` and `quickSearchValidationRun` (columns in the README, "Operational tables"). Create them in the host's schema, with the naming the host applies to the other operational tables (`tableMetaDataCustomizer` applies to them too). Until they exist, writing events and saving reports fails quietly (logged at warn) and the tools still run.
- **Permissions.** Grant `quickSearchDebug.hasAccess` to the people who may use the read-only tools and see the widget, `quickSearchAdmin.hasAccess` for Reindex in Search, and `.read` on the two new tables (the QBit writes them; users never do).
- **New processes** have a `tableName` (Quick Search Index, Quick Search Failed Event, and each indexed table), so they appear as table and record actions. They are not added to the admin app's children; a host that strips processes from its nav needs no change.
- **Opting out.** `withEnableDiagnosticTools(false)` adds none of it; `withEnableRecordDiagnosticActions(false)` keeps the tools on Quick Search Index but adds nothing to the indexed tables.

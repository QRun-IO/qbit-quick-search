# Migrating from 1.0 to 1.1

1.1 lets QQQ core's record search (`POST /qqq/v1/search`, the Next UI global search box) use the Quick Search index. Hosts that do nothing get it by default.

## Prerequisites

- The QQQ release that carries the record-search provider SPI (`RecordSearchProviderInterface`, `QInstance.withRecordSearchProvider`), added in QRun-IO/qqq#1042. Until that release exists, 1.1 builds against `4.1.0-SNAPSHOT` through the `qqq-snapshot` profile; it is re-pinned to the release before 1.1.0 ships.

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

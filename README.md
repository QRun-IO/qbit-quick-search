# QBit: Quick Search

[![Version](https://img.shields.io/badge/version-1.1.0--RC.1-blue.svg)](https://github.com/QRun-IO/qbit-quick-search)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java](https://img.shields.io/badge/java-21+-blue.svg)](https://adoptium.net/)

> OpenSearch-backed full-text search across the tables of a QQQ application.

Annotate entity classes or declare tables in config, point the QBit at an OpenSearch cluster, produce it into your `QInstance`, and you get real-time indexing of committed writes, a scheduled catch-up and reconcile, a blackout-free full rebuild, and a permission-aware search API with relevance scoring, typeahead, highlights and pagination.

Requires QQQ 4.1 (the QBit uses 4.1's record-change listeners, after-commit callbacks and runtime services). Serving core search (`POST /qqq/v1/search`, below) needs QQQ 4.1.0-RC.2 or later, the first release that carries the record-search provider SPI. The record developer panel (below) needs QQQ 4.1.0-RC.4 or later, which carries the developer panel contract; the 1.1 line builds against 4.1.0-RC.4 from the next release candidate on.

## What it does

| Capability | How |
|---|---|
| Real-time indexing | One `RecordChangeListenerInterface` registered on the instance; events are published after the write's transaction commits, so rolled-back writes never reach the index. Failures are recorded in `quick_search_failed_event` and replayed by the scheduled basepull. |
| Basepull catch-up | Scheduled process (`schedulerName` plus `basepullRepeatSeconds`) that re-indexes rows changed since the previous run's start, with a configurable overlap, paging by primary key. |
| Reconcile | Scheduled or on-demand process that re-indexes everything in place, then removes documents whose source row is gone, including documents of tables no longer configured. No search blackout. |
| Full rebuild | Indexes every table into a fresh physical index and atomically swaps the alias; the old index keeps serving until the new one is complete. Also upgrades an index created by an older release. |
| Core search | `QuickSearchRecordSearchProvider` serves QQQ's `RecordSearchAction` (`POST /qqq/v1/search`, the Next UI global search box) from OpenSearch for the indexed tables, with a timeout and an automatic fallback to core's own search. |
| Search | `QuickSearchAction`: AND semantics, edge-ngram typeahead with a plain search analyzer, per-field weights, highlights, `tableNames` and `limitPerTable`, bounded paging, `tableLabel` on results. Only tables the session may read are searched; hits are re-read through `QueryAction` so record security locks apply. |
| Connections | HTTP or HTTPS, basic auth, AWS IAM SigV4, custom CA or trust store, mTLS, timeouts, pool size, a transport customizer SPI for anything else. Secrets by `${env.X}` reference. |
| Operations | `quick_search_index` (per-table status, counts, last run, last error), `quick_search_index_run` (history, purged after `runHistoryRetentionDays`), `quick_search_failed_event`, a `quickSearchAdmin` app, explicit permission rules, fail-fast or degraded startup. |

## Supported platforms

Tested in CI against the Docker images named in `pom.xml` (`opensearch.test.image`).

| Target | Status |
|---|---|
| Self-managed OpenSearch 2.19.x and 3.x | Supported, security plugin disabled or HTTP basic authentication, HTTP or HTTPS with a trusted, configured or (development only) unverified certificate |
| Amazon OpenSearch Service managed domains, OpenSearch 2.x or 3.x | Supported with fine-grained access control: either an internal-database master user with `authMode BASIC`, or IAM with `authMode AWS_SIGV4` (`awsRegion`, `awsServiceName es`, optional `awsAssumeRoleArn`). Port 443 over HTTPS. Keep `bulkBatchSize` and `maxBulkRequestBytes` under the instance type's 10 MiB payload limit on small instances. |
| Amazon OpenSearch Serverless | Not supported in 1.0 (`_delete_by_query` and `_refresh`, used by reconcile and full rebuild, are not offered there) |
| Elasticsearch, OpenSearch 1.x | Not supported |

Least-privilege role for the identity the QBit uses, on the alias and its physical indexes (`{indexName}*`): `create_index`, `read`, `write`, `delete`, plus `indices:admin/refresh*`, `indices:admin/aliases`, `indices:admin/delete` (for the alias swap) and `indices:admin/mapping/get`; cluster level `cluster_composite_ops` for `_bulk`. For AWS fine-grained access control map the same to the basic-auth user or the IAM role. Treat the index as sensitive data: it holds copies of every indexed field value.

For `AWS_SIGV4` add these optional dependencies to your host (the QBit declares them `optional`):

```xml
<dependency><groupId>software.amazon.awssdk</groupId><artifactId>apache-client</artifactId></dependency>
<dependency><groupId>software.amazon.awssdk</groupId><artifactId>sts</artifactId></dependency> <!-- only for awsAssumeRoleArn -->
```

## Getting started

```xml
<dependency>
    <groupId>com.kingsrook.qbits</groupId>
    <artifactId>qbit-quick-search</artifactId>
    <version>1.1.0-RC.1</version>
</dependency>
```

`1.1.0-RC.1` is the current release candidate (the 1.1 line, published from `release/1.1`; it needs QQQ 4.1.0-RC.2 or later, see [Upgrading from 1.0](#upgrading-from-10)). Hosts staying on the 1.0 line use `1.0.0-RC.2`; `1.0.0` follows once host applications have adopted it (#12).

Annotate entity classes:

```java
@QuickSearchable(tableName = "customer")
public class Customer
{
   @QuickSearchField(weight = 3)
   private String name;

   @QuickSearchField(weight = 1)
   private String email;

   @QuickSearchField(weight = 2, includeLabel = true)
   private String accountNumber;
}
```

Produce the QBit (after your backends, tables and scheduler are in the instance):

```java
QuickSearchQBitConfig config = new QuickSearchQBitConfig()
   .withBackendName("rdbms")
   .withOpensearchUrl("https://search.example.com:443")
   .withAuthMode(QuickSearchAuthMode.BASIC)
   .withOpensearchUsername("${env.OPENSEARCH_USER}")
   .withOpensearchPassword("${env.OPENSEARCH_PASSWORD}")
   .withOpensearchIndexName("my-app-search")
   .withSchedulerName("myScheduler")
   .withSearchableEntityClasses(List.of(Customer.class, Order.class));

new QuickSearchQBitProducer().withConfig(config).produce(qInstance);
```

The producer implements QQQ's `QBitMetaDataProducer`, registers its own `QBitMetaData` (`com.kingsrook.qbits:quick-search`), and adds its tables, processes and app to the instance. Do not add a `QBitMetaData` for it yourself.

Tables from other QBits or jars, without annotations:

```java
config.withSearchableTable(new SearchableTableConfig("crmContact", List.of(
      new SearchableFieldConfig("firstName").withWeight(2),
      new SearchableFieldConfig("lastName").withWeight(2),
      new SearchableFieldConfig("email").withWeight(3)))
   .withRecordLabelFormat("%s %s", "firstName", "lastName"));
```

Annotation and config-driven tables can be mixed; a table may appear in only one of them. Hidden and password fields are never indexed. A table without the change-detection field (`modifyDate` by default) is covered by reconcile instead of incremental basepull, and the QBit logs a warning for it at startup. Set `basepullTimestampField` to null on a config-driven table to opt out without the warning.

### Searching

```java
QuickSearchOutput output = new QuickSearchAction().execute(new QuickSearchInput()
   .withSearchTerm("widget")
   .withTableNames(List.of("customer", "order"))   // optional
   .withLimit(25)
   .withOffset(0));

for(QuickSearchResult result : output.getResults())
{
   result.getTableName(); result.getTableLabel(); result.getRecordId();
   result.getRecordLabel(); result.getScore(); result.getHighlightSnippet();
}
output.getTotalHits(); output.getTotalHitsIsLowerBound(); output.getHasMore();
```

`limitPerTable` returns up to that many hits from each table instead of one ranked list. Terms shorter than 2 characters return nothing; longer than 100 characters are rejected. `limit` is capped at `maxSearchLimit` and `offset + limit` at 10,000.

`offset` skips N accessible results: with record security locks on, hits a lock hides are not counted, so consecutive pages never overlap or skip a visible hit, and with `limitPerTable` the offset applies within each table. With locks on, `totalHits` counts hits confirmed accessible so far; `totalHitsIsLowerBound` is true when the scan stopped before reading every candidate (page full, result window, or scan budget). A single-list search reads at most 10,000 raw hits in batches of at most 1,000. In per-table mode the T tables share that budget, each getting max(10,000 / T, 2 x `limitPerTable`) hits, so one call reads at most max(10,000, T x 2 x `limitPerTable`) raw hits. Paging within a table stops past that share of raw hits, as it does past 10,000 in a single list: `hasMore` turns false and `totalHitsIsLowerBound` stays true to signal unreachable matches.

Search runs as the current `QContext` session: tables the session cannot read are skipped, and with `applyRecordSecurityLocks` (default) hits are re-read through `QueryAction` so record security locks apply.

### Core search (`POST /qqq/v1/search`)

QQQ core has its own record search (`RecordSearchAction`, served at `POST /qqq/v1/search` and used by the Next UI's global search box): a case-insensitive contains-query over each table's `searchFields`. With `serveCoreRecordSearch` (default `true`) the producer registers `QuickSearchRecordSearchProvider` on the instance, and core asks it to search every table Quick Search indexes. The API and the UI do not change.

- **What is served from OpenSearch.** Tables this QBit indexes and that are enabled in `quick_search_index`. Core offers a table only when it has `searchFields`, the session may read it, and it supports query, so the producer gives each indexed table that has no `searchFields` its visible string and integer searchable fields (never hidden or password fields). `searchFields` you set yourself are kept. A table must be in the `QInstance` before the QBit is produced to get them.
- **How.** One `_msearch` request with one search per table (Quick Search's query, AND semantics and field weights), skipping the request's `offset`; each table returns up to 4 x `limitPerTable` record ids (at most 100) in relevance order, each with its score and a highlight of the matching `fieldValues.*` fields (plain text plus match ranges), and the table's total hits (counted to 10,000). Core re-reads the ids by primary key through `QueryAction`, so table permissions, record security locks and record labels are core's, records deleted since they were indexed drop out, and only highlights of fields the user can see (where core's own highlighting found no match) reach the response, with `tables[].totalCount` and `hasMore`. The index holds no record modify dates, so `sort: RECENT` gets relevance order for indexed tables.
- **Failure.** The OpenSearch request is cancelled after `recordSearchTimeoutMillis` (default 2500). Any failure (timeout, cluster down, index missing) makes core search those tables with its own contains-query instead, so search degrades but never fails. After a failure, Quick Search claims no tables for 30 seconds, so a down cluster does not add a timeout to every search.
- **Opting out.** `withServeCoreRecordSearch(false)` leaves core search and `searchFields` untouched. If the host already registered another record-search provider, the QBit keeps it and logs a warning (an instance has one provider).

`QuickSearchAction` remains the direct API (all hits in one ranked list, `searchableText` highlights, pagination across tables).

### Processes

| Process | Trigger | What it does |
|---|---|---|
| Quick Search Basepull Index | Scheduled every `basepullRepeatSeconds` when `schedulerName` is set; also on demand | Recovers a full reindex that stopped without finishing, replays failed real-time events, re-indexes rows changed since the last run start minus `basepullOverlapSeconds`, purges old run history |
| Quick Search Reconcile Index | Cron via `reconcileCronExpression` when `schedulerName` is set; also on demand (optional `tableName` input) | Re-indexes everything in place, removes documents without a source row and documents of unconfigured tables |
| Quick Search Full Reindex | On demand (optional `tableName` input) | All tables: build a fresh physical index and swap the alias. One table: reconcile algorithm in place. Fails to start while another full reindex is running |

Without `schedulerName` the processes exist but nothing runs them; the QBit logs a warning at startup.

### Managing searchable tables (data-driven, 1.1.0-RC.3)

Which tables and fields are indexed is data: `quick_search_index` (one row per table) and `quick_search_index_field` (its fields: weight, role, analyzer, snippet and label flags), edited in the Quick Search admin app with the standard screens and these record actions (each with a review step; none is in the nav):

- **Add Search Index** (Quick Search Index, Actions): pick a readable, eligible table, review the estimated record count and the fields it starts with, save; the first build is queued.
- **Searchable Fields** on the index record: add, edit or remove fields; then **Apply Search Index Changes** previews the changes and rebuilds.
- **Disable Search Index** and **Remove Search Index**: stop indexing at once and delete or keep the documents. Removals are remembered, so code defaults do not bring them back.
- **Cancel Index Run** (Quick Search Index Run): stops a running reindex after its current batch (`CANCELLED`, with counts).
- **Pause Indexing** and **Resume Indexing** (Quick Search Settings): pause all indexing while search keeps serving; changes are indexed after resume.

`@QuickSearchable` entities and `searchableTables` only seed these records (self-priming at startup: new tables and fields are added and built automatically; rows an admin edited are never overwritten). The precedence rules, the DDL for the new tables and the upgrade steps are in `docs/MIGRATION-1.1.md`, "Data-driven configuration".

### Scheduling

The schedules run only when the host starts through QQQ's `QApplicationLauncher` (`com.kingsrook.qqq.middleware.javalin.QApplicationLauncher`, in `qqq-middleware-javalin`). `QApplicationLauncher.run(new MyApplication(), new QApplicationLauncherConfig())` starts the Javalin server, then the `QScheduleManager` (because the QBit's processes are scheduled), then the instance's runtime services. A host that starts only a server, or builds the `QInstance` itself, gets the processes but nothing runs them on a schedule, unless it calls `QScheduleManager.initInstance(qInstance, systemSessionSupplier).start()` itself. `qqq.scheduleManager.enabled=false` (or `QQQ_SCHEDULE_MANAGER_ENABLED=false`) turns schedules off on a node.

Basepull's fixed interval runs on any QQQ scheduler. `reconcileCronExpression` needs a scheduler that supports cron, which in QQQ 4.1 means a `QuartzSchedulerMetaData`; a `SimpleSchedulerMetaData` runs fixed intervals only. The expression uses Quartz syntax, seconds first (`0 0 3 * * ?` is 03:00 every day), and needs `reconcileCronTimeZoneId`, a Java time zone ID such as `UTC` or `America/Chicago`; QQQ has no default zone. `produce()` rejects a cron without a zone, an invalid expression or zone, and a cron on a scheduler in the instance that does not support cron.

```java
qInstance.addScheduler(new QuartzSchedulerMetaData().withName("quartz").withProperties(quartzProperties));

config.withSchedulerName("quartz")
   .withReconcileCronExpression("0 0 3 * * ?")
   .withReconcileCronTimeZoneId("America/Chicago");
```

### Diagnostic tools

With `enableDiagnosticTools` (default `true`) the QBit adds tools to validate the search and debug it from the admin UI. They are table and record actions (never left-nav entries), gated by permissions, recorded in `quick_search_diagnostic_event`, limited to `diagnosticRunsPerMinute` runs per user and tool, and read-only except Reindex in Search.

| Tool | Where | What it does |
|---|---|---|
| Search Playground | Quick Search Index, Actions (select rows to pre-fill their tables) | Runs a term the way the global search does, as you: the path each table takes (OpenSearch provider, core database search, or not searched) and why; the provider's own request, timed; the global search results you would see; per indexed table, the production query with scores, highlights, matched fields, which hits you can read and which the global search shows, the query DSL and the raw response (hits you cannot read are redacted); the term's tokens; and `explain` for a hit (`table:id`). The form stays open, so you can change the term and run again. |
| Validate Search Index | Quick Search Index, Actions; also scheduled by `validationCronExpression` | Cluster and index health, the alias, leftover physical indexes from interrupted rebuilds, the live mapping (version, field types, analyzers, the `fieldValues` template) and analysis settings, then per table: database rows vs documents, a sample of records checked present and current in the index, a random sample of documents checked to still have a record, the index row's status and last run, and waiting failed events. Each finding is OK, INFO, WARN or FAIL with what to do. The report is saved as a `quick_search_validation_run` row (HTML for the record view, JSON for tools). Database reads run as the system user so counts compare like with like; the report holds counts, ids and field names, never record values. One validation runs at a time per server. |
| Test Search Analyzer | Quick Search Index, Actions | `_analyze` on the live index: the tokens of a text as indexed (edge n-grams of 2 to 20 characters) and as a query, and, given a term, whether every term token is among the text's indexed tokens. |
| Inspect Search Document | Every indexed table's records (Actions), and Quick Search Failed Event rows | The record's document in the index next to the document indexing would write from the database now, field by field (current, stale or missing), with versions and the raw document. Enter a term to see why the record does or does not match it: term length, table enabled, read permission, document present and current, each term token found or not (and why: one character, longer than 20, not in the text, or only in the database's newer values), `explain`, and the record's rank against the number of ids the global search takes. |
| Reindex in Search | Every indexed table's records, and Quick Search Failed Event rows | Rebuild the selected records' documents from the database now (versioned writes, so a newer document is never overwritten); a record that no longer exists has its document removed; a record you cannot read is skipped. Asks to confirm. Needs the admin permission. |
| Search Diagnostics widget | The Quick Search Admin app home (hosts with their own home add `quickSearchDiagnostics`) | Whether the global search is served by OpenSearch on this server or backing off (and why), failure and fallback counts, the latest search failures from every server, and the latest validation run. |

Search failures are recorded too: when the provider's OpenSearch request fails or times out (and core falls back to its own search), or `QuickSearchAction`'s search fails, a `SEARCH_FAILED` event keeps the tables, duration, error class and message, and the server. The user's term is never stored, only its length. At most 120 events are written per server per minute.

Permissions: the read-only tools and the widget use `debugPermissionRules` (default `quick_search_debug.hasAccess`); Reindex in Search uses `adminPermissionRules` (`quick_search_admin.hasAccess`); the two history tables use `tablePermissionRules` (grant `.read` only: rows are written by the QBit). Impersonation ("search as user X") is deliberately not offered: a fabricated session would not carry the real user's security key values, so its results would mislead.

### Record developer panel

With QQQ 4.1.0-RC.4 or later the QBit registers a record developer panel provider (`QuickSearchDeveloperPanelProvider`, id `quickSearch`, in QQQ's `DeveloperPanelsMetaData`). Frontends that render developer panels (the Next UI, `/app/{table}/{id}/dev`) show a "Quick Search" panel on every record of an indexed table, to users who may read Quick Search Index. Every part is permission-checked:

| Part | Shown when |
|---|---|
| Index: enabled, status, record count, last basepull, last full reindex, searchable fields, link to the index row | always (read on Quick Search Index) |
| Latest index run: type, status, start and end, processed, indexed, errors | the user may read Quick Search Index Run |
| Search document: verdict (current, stale, missing) and a Field / Database / Index / Match table | `enableDiagnosticTools` and the debug permission (`debugPermissionRules`); otherwise a note says why it is missing |
| Failed index events of the record | the user may read Quick Search Failed Event and the record has any |
| Actions: Inspect search document, Reindex this record | the record actions exist and the user may run them (debug and admin permission) |
| Link to Quick Search Admin | the user may open the app |

The panel's status is Indexed, Stale, Not indexed, Disabled (the table is turned off in Quick Search Index), or OpenSearch unavailable. When OpenSearch cannot be reached the panel still shows the database-side state, with a warning in place of the document comparison. Hosts without a developer-panel frontend are unaffected.

## Configuration reference

| Field | Default | Description |
|---|---|---|
| `backendName` | required | QQQ backend for the operational tables |
| `opensearchUrl` | | `https://host[:port]`; preferred over `opensearchHost`, `opensearchPort`, `useSsl`; credentials in the URL are rejected |
| `opensearchHost`, `opensearchPort`, `useSsl` | `useSsl=false` | Legacy connection fields |
| `opensearchIndexName` | required | Alias name owned by this QBit; lowercase, OpenSearch naming rules |
| `authMode` | inferred | `NONE`, `BASIC` (inferred when credentials are set), `AWS_SIGV4` |
| `opensearchUsername`, `opensearchPassword` | | BASIC credentials; `${env.X}` references recommended (an empty value counts as missing); refused over plain HTTP unless `allowPlaintextCredentials` |
| `awsRegion`, `awsServiceName`, `awsAssumeRoleArn` | region from `AWS_REGION`; `es` | AWS_SIGV4 settings |
| `tls` | JVM defaults | `caCertificatePath` or `trustStorePath`/`trustStorePassword`/`trustStoreType`, `keyStorePath`/`keyStorePassword`/`keyStoreType` (mTLS), `hostnameVerification` (`false` logs a warning), `insecureSkipVerify` (loopback only unless `allowInsecureInProduction`) |
| `connectTimeoutMillis`, `responseTimeoutMillis`, `maxConnections` | 5000, 60000, 30 | Transport settings |
| `transportCustomizer` | | `QCodeReference` to an `OpenSearchTransportCustomizer` (bearer tokens, API keys, interceptors) |
| `startupMode` | `FAIL_FAST` | `FAIL_FAST` fails `produce()` when the cluster is unreachable; `DEGRADED` boots and retries on first use |
| `enableRealTimeIndexing` | `true` | Register the record-change listener |
| `enableBasepullProcess`, `enableMaintenanceProcesses` | `true` | Register basepull; register full reindex and reconcile. `enableScheduledProcesses` is a deprecated alias for both |
| `schedulerName`, `basepullRepeatSeconds`, `reconcileCronExpression`, `reconcileCronTimeZoneId` | none, 300, none, none | Scheduling; a cron needs the time zone ID and a Quartz scheduler, and nothing runs without `QApplicationLauncher` (see [Scheduling](#scheduling)) |
| `basepullOverlapSeconds` | 300 | Re-read window before the previous run start |
| `defaultBasepullIntervalMinutes` | 5 | Per-table due interval for config-driven tables |
| `runHistoryRetentionDays` | 30 | Purge run records older than this; null keeps them |
| `fullReindexStaleMinutes` | 30 | A running full reindex that shows no sign of life for this long is treated as stopped: it no longer blocks a new one, and basepull recovers its tables. Keep it well above the time one page of `sourceBatchSize` records takes; null means the default |
| `bulkBatchSize`, `maxBulkRequestBytes`, `sourceBatchSize` | 500, 5 MiB, 1000 | Batch sizes |
| `maxFieldLength` | 10000 | Truncate each indexed value |
| `maxSearchLimit` | 100 | Largest page size |
| `applyRecordSecurityLocks` | `true` | Re-read hits through `QueryAction` |
| `serveCoreRecordSearch` | `true` | Register `QuickSearchRecordSearchProvider` so core `POST /qqq/v1/search` uses OpenSearch for indexed tables, and give those tables `searchFields` when they have none |
| `recordSearchTimeoutMillis` | 2500 | Wall-clock limit on the OpenSearch request behind a core search; past it, core falls back to its own search |
| `enableDiagnosticTools` | `true` | Add the diagnostic tools, the `quick_search_diagnostic_event` and `quick_search_validation_run` tables and the diagnostics widget |
| `enableRecordDiagnosticActions` | `true` | With the tools on, add Inspect Search Document and Reindex in Search to every indexed table |
| `validationCronExpression`, `validationCronTimeZoneId` | none | Schedule Validate Search Index; same rules as the reconcile schedule (a cron-capable scheduler such as Quartz, a valid Quartz expression, a time zone ID) |
| `validationSampleSize` | 50 | Records and documents sampled per table by validation (at most 1000) |
| `diagnosticRunsPerMinute` | 30 | Per-user, per-tool limit on diagnostic runs |
| `debugPermissionRules` | `HAS_ACCESS_PERMISSION`, base name `quick_search_debug` | Rules for the read-only diagnostic tools and the widget |
| `adminPermissionRules` | `HAS_ACCESS_PERMISSION`, base name `quick_search_admin` | Rules for the app, the maintenance processes and Reindex in Search |
| `tablePermissionRules` | `READ_WRITE_PERMISSIONS` | Rules for the operational tables |
| `tableMetaDataCustomizer` | | Applied to every produced table (backend details, for example) |
| `searchableEntityClasses`, `searchableTables` | at least one | What to index |
| `indexEventPublisher` | synchronous | Replace the publisher (experimental SPI; see below) |

`@QuickSearchable(tableName, fields, basepullIntervalMinutes, basepullTimestampField, enabledByDefault)` and `@QuickSearchField(weight, includeLabel)` keep their 0.x meaning.

## Operational tables

- `quick_search_index`: one row per table with `source` (`CODE` or `OWNER`), `ownerEdited`, `enabled` (honoured by indexing and search; a change applies at once), `basepullIntervalMinutes`, `basepullTimestampField`, `lastBasepullTime`, `lastReconcileTime`, `lastFullReindexTime`, `lastRunStatus`, `lastErrorMessage`, `recordCount`, `documentCount`, `realTimeErrorCount`, `status` (`ACTIVE`; `PENDING_BUILD` when a build is queued and `BUILDING` while the basepull builds it; `NEEDS_REINDEX` when the fields changed since the last build; `REBUILDING` while a full reindex of all tables runs). Unique on `tableName`.
- `quick_search_index_field`: the searchable fields of each index row: `fieldName`, `weight`, `role` (`DISPLAY`, `TITLE`, `SUBTITLE`, `BODY`, `KEYWORD`, `EXACT`), `analyzer` (`PREFIX`, `WORD`, `ENGLISH`, `EXACT`; blank: by role), `includeInSnippet`, `includeLabel`, `sortOrder`, `source`, `ownerEdited`. Unique on (`quickSearchIndexId`, `fieldName`).
- `quick_search_settings`: one row: `indexingPaused` (with `pausedAt`, `pausedBy`, `pauseReason`) and the last self-priming report (`lastPrimedAt`, `lastPrimingSummary`).
- `quick_search_config_tombstone`: tables (blank `fieldName`) and fields an admin removed; priming never re-adds them.
- `quick_search_index_run`: `runType` (`BASEPULL`, `RECONCILE`, `FULL_REINDEX`), `status` (`RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`), counts, `errorMessage`, `cancelRequested`, `cancelRequestedBy`.
- `quick_search_diagnostic_event` (with `enableDiagnosticTools`): `eventType` (`SEARCH_FAILED`, `TOOL_RUN`, `REINDEX`, `RATE_LIMITED`), `source`, `outcome`, `userName`, `tableNames`, `searchTerm` (tool runs only), `durationMillis`, `errorClass`, `errorMessage` (text), `detail` (text), `hostName`, `createDate`. Purged after `runHistoryRetentionDays`.
- `quick_search_validation_run` (with `enableDiagnosticTools`): `status` (`PASS`, `WARN`, `FAIL`), `triggerType` (`MANUAL`, `SCHEDULED`), `userName`, `tableNames`, `sampleSize`, `tablesChecked`, `failCount`, `warnCount`, `summary`, `reportHtml` (HTML), `reportJson` (text), `startTime`, `endTime`, `createDate`. Purged after `runHistoryRetentionDays`.
- `quick_search_failed_event`: real-time events that could not be applied; `status` `PENDING` or `EXHAUSTED` after 10 attempts. Deletes made while a full reindex of all tables runs are also recorded here with status `AWAITING_REINDEX`: the reindex applies them to the new index after the alias swap (basepull does not replay them). While a table is `REBUILDING`, drift detection is suspended for it. Only one full reindex runs at a time: starting one while another is running fails. A full reindex shows it is running through its `FULL_REINDEX` run records, which stay `RUNNING` with `modifyDate` refreshed after every page. If it is killed mid-run (pod restart, OOM, deploy), recovery is automatic once its run records have shown no sign of life for `fullReindexStaleMinutes` (30 by default): the next basepull, or the next full reindex, marks those run records `FAILED`, returns the tables to `ACTIVE` with `lastBasepullTime` moved back to the run's start, and turns the `AWAITING_REINDEX` rows `PENDING` so basepull replays the deletes. No data is lost. A run that was only slow and is judged stopped notices before its alias swap and fails without swapping, or, after its swap, stops applying its captured deletes; basepull skips a replayed delete whose record exists again.

### Starter defaults and empty-state help

This qbit ships no DDL and no seed changelog: startup self-priming (`QuickSearchConfigPrimer`) is its seed mechanism. On a fresh database it writes a `quick_search_index` row and `quick_search_index_field` rows for every table the code declares searchable, and the single `quick_search_settings` row (id 1). `quick_search_config_tombstone` and the five log and history tables start empty by design (rows appear when an admin removes a table or field, or when the qbit runs). Every table and the Quick Search Admin app carry help (`help/quick-search.yaml`, applied by `QuickSearchHelp` without overriding help the host set) that says what the table is for and how its rows get created, so an empty table explains itself. No host change is needed.

Real-time error counts on Quick Search Index show failures since the last successful full reindex or basepull. Each failed event increases the count; deferred changes while indexing is paused do not. Counter updates are best effort and serialized across listeners in one JVM; concurrent application nodes can still race because QQQ has no arithmetic update API.

## Extending

`IndexEventPublisher` lets a host replace the synchronous publisher with a queue. In 1.0 the SPI is experimental: `IndexEvent` carries the table, record id, action and the record snapshot, but no sequence number, so an asynchronous consumer must order INDEX and DELETE for the same record itself. `OpenSearchTransportCustomizer` customizes the HTTP transport.

## Building

```bash
./mvnw test                                 # unit tests (memory backend, mocked client)
CI=true ./mvnw verify                       # plus Testcontainers integration tests and the coverage gate (Docker required)
./mvnw verify -Dopensearch.test.image=opensearchproject/opensearch:3.9.0
```

Use the wrapper: Maven 3.10 cannot read the published parent POM. The build imports qqq 4.1.0-RC.4 through the default-active `qqq-snapshot` profile until `qbit-build-parent` 2.1.0 ships (override with `-Dqqq.snapshot.version=...`). Without Docker the integration tests are skipped locally; with `CI=true` they fail instead.

## Upgrading from 1.0

See [docs/MIGRATION-1.1.md](docs/MIGRATION-1.1.md). In short: 1.1 needs the QQQ release with the record-search provider SPI; core search starts using OpenSearch for indexed tables by default, and indexed tables gain `searchFields` (set `serveCoreRecordSearch(false)` to keep 1.0 behaviour).

## Upgrading from 0.x

See [docs/MIGRATION-1.0.md](docs/MIGRATION-1.0.md). In short: QQQ 4.1 is required, remove any manual `addQBit` for this QBit, set `schedulerName`, prefer `opensearchUrl` and `${env.}` secrets, grant `quick_search_admin.hasAccess` and the table permissions, add the new operational table columns and `quick_search_failed_event`, and run a full reindex once so the index gets the new mapping.

## Adopting in another app

Follow [Getting started](#getting-started), the [Configuration reference](#configuration-reference) and [Operational tables](#operational-tables) (the host application creates the three tables; this QBit ships no DDL), then run a full reindex. Hosts on 1.0 or 0.x should read the upgrade sections above.

See also the [QQQ adoption checklist](https://github.com/QRun-IO/qqq/blob/develop/docs/migration/adoption-checklist.adoc) for the full per-feature list (QQQ upgrade, Next dashboard, QBits, migrations, permissions).

## License

Apache License, Version 2.0. See [LICENSE](LICENSE).

## UI API for hosts

The `quick-search` extension registers both status and suggestions in the committed
[OpenAPI fragment](src/main/resources/openapi/qqq-v1/quick-search.yaml).

### Typeahead suggestions

`GET /qqq/v1/qbit/quick-search/suggest` (`getQuickSearchSuggestions`) requires a
signed-in session. Quick Search admin permission is not required. Java hosts use
`QuickSearchSuggestService.suggest(q, limitPerTable, maxTables, tables)` in the caller's
`QContext`.

| Query parameter | Meaning |
| --- | --- |
| `q` | Required text, trimmed with consecutive whitespace collapsed. Fewer than 2 characters returns empty groups; more than 100 returns HTTP 400. |
| `limitPerTable` | Integer 1..10; default 5. |
| `maxTables` | Integer 1..20; default 8. |
| `tables` | Optional comma-separated table metadata names. Unknown, disabled and unreadable tables are omitted; an empty filter returns no groups. |

```json
{
  "query": "blue wid",
  "groups": [{
    "tableName": "product",
    "tableLabel": "Products",
    "hasMore": true,
    "items": [{"recordId": "42", "label": "Blue Widget", "subtitle": "Small"}]
  }],
  "truncated": false
}
```

The existing edge-ngram query supports the last word's prefix. Groups are ordered
by their best accessible score, with per-table relevance order preserved. Labels
fall back to `tableLabel #recordId`; subtitles are nullable and come only from the
already-filtered result. Scores, highlights and display values are never returned.
The existing action enforces table READ permissions, enabled-table selection,
configured record security locks (on by default), PHI/PII exclusions and protected
primary keys.

Suggestions make one bounded OpenSearch request per eligible table, fetching at
most `2 * (limitPerTable + 1)` candidates under record locks (also subject to the
host's `maxSearchLimit`). Locked rows can leave a group partially filled.
`hasMore` confirms an extra accessible hit in this batch; false does not prove
there are no further matches. `truncated` means more tables had accessible hits
than `maxTables`. Missing runtime/client or unavailable search returns empty groups.
Missing `q`, invalid integers and out-of-range limits return HTTP 400.

### Administrative status

Requires QQQ **4.1.0-RC.6**. Hosts should use the supported API rather than reach into
`QuickSearchRuntime`, `QuickSearchOpenSearchClient`, diagnostic renderers, or qbit tables:

```java
import com.kingsrook.qbits.quicksearch.api.QuickSearchStatus;
import com.kingsrook.qbits.quicksearch.api.QuickSearchStatusService;

// Runs in the host request's QContext, with the caller's QSession.
QuickSearchStatus status = QuickSearchStatusService.get();
String homeHref = status.homeHref();
```

UI clients use **`GET /qqq/v1/qbit/quick-search/status`**, operationId
`getQuickSearchStatus`, extension `quick-search` version `1.0.0`. The endpoint returns
that same immutable value as JSON; it requires the Quick Search app's configured
`adminPermissionRules` (default `quick_search_admin.hasAccess`). Permission is checked
before every read, including cache hits. No additional source-table permissions are
required: like index validation, this is an administrator's aggregate view of the index.
The committed contract is [quick-search.yaml](src/main/resources/openapi/qqq-v1/quick-search.yaml).

The default core namespace is intentional: RC.6 permits `/qbit/quick-search` but rejects
`/quick-search`, which is not an established legacy prefix. There were no existing
Quick Search UI API routes to preserve. Core source is unchanged.

- `state`: `OK`, `DEGRADED` (including paused indexing, backlog, incomplete diagnostics
  or a refresh timeout), `DOWN` (unavailable health or red health), or `DISABLED`
  (qbit absent or every persisted index disabled). Pausing indexing does not disable search.
- `clusterReachable`, `clusterHealth`, `nodes`, `activeShards`, `unassignedShards` come
  from the existing diagnostics health operation. Null means unknown/not probed.
- `indexes` contains up to 100 persisted index rows. `documentCount` and `sourceCount`
  come from the latest saved `IndexValidator` report, with `countsCheckedAt`. Without
  matching validation, only the last indexing run's document count is available and
  its observation time is unknown. These are **saved counts, not live counts**. A
  missing source count must not be displayed as zero or “in sync.”
- `pending` includes `PENDING` and `AWAITING_REINDEX` failed events; `exhausted` counts
  failed events that gave up. At most 1000 backlog rows are read; `backlogTruncated`
  makes both counts lower bounds. `tablesTruncated` indicates incomplete index rows.
  These counters do not measure an external publisher's queue.
- `lastSuccessfulSync` is the newest successful indexing watermark among the included
  tables (basepull is start-anchored), not proof that all tables are current.
  `lastError` is the newest available indexing/runtime search error or snapshot failure.
- `checkedAt` is snapshot completion time. A per-runtime five-second cache bounds
  polling cost; each refresh uses `DiagnosticsRateLimiter`'s per-user `STATUS` key.
  Rate-limit refusal uses the existing diagnostics error (HTTP 400). A refresh waits
  at most two seconds and retains at most one in-flight worker, even if a backend is
  stuck. Runtime shutdown stops the worker. No source counts, record samples, full
  validation, or per-table OpenSearch calls run on this polling path.

Link to `status.homeHref()` instead of copying the Quick Search home. It includes the
configured metadata prefix. `QuickSearchApi.APP_NAME` (`quickSearchAdmin`) and
`QuickSearchApi.HOME_HREF` (`/app/quickSearchAdmin`) are available for default-name
navigation. Keep detailed runs, failed events, validation and operator tools in the
qbit's own home. [kof22 migration mapping](docs/STATUS-API-HOST-MIGRATION.md).

## Classified data

Quick Search excludes fields classified as **PII** or **PHI**, including fields
that inherit the table's classification. It also excludes hidden, sensitive,
password/masked fields and every field with a `FieldSecurityLock`; a shared
search index cannot enforce access to individual fields for each user. PUBLIC,
CONFIDENTIAL and unclassified fields remain eligible under the usual field rules.

The same rule applies to annotation and code configuration, admin field choices,
real-time writes, basepull, rebuilds, replay, labels, snippets, analyzer copies and
result display fields. An admin trying to add a protected field gets an error.
Protected fields in existing or explicit code configuration are dropped, with one
warning per table metadata lifetime; warnings never contain field values. A
protected primary key prevents indexing the record or sending its ID for deletion.

Protection is separate from search eligibility: boolean, blob, hidden and other
unsupported fields alone do not trigger result filtering or remove record labels.
Labels are omitted when their source fields are protected; unrelated protected
fields do not erase safe labels. Unprotected hidden keys and timestamps can still
provide record IDs and document versions.

After upgrading, or after changing a field's classification or security metadata,
run **Full Reindex** across all indexed tables to remove previously indexed values.
Until then, tables containing protected fields are searched through allowed field
values, and results are rebuilt without old combined labels or highlights.
Diagnostic views also omit protected values and the raw response for these tables.
This does not purge old OpenSearch backups or retained physical indexes; apply your
host's retention policy to those copies.

This release uses the APIs in QQQ **4.1.0-RC.6**. Support for the host's
`AuditValueProtector` classification (`ClassifiedValues`, qqq#1182) and
`FieldAccessHelper.getLockTier` (qqq#1172) is deferred until the qbit moves to RC.7.

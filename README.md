# QBit: Quick Search

[![Version](https://img.shields.io/badge/version-1.0.0--RC.1-blue.svg)](https://github.com/QRun-IO/qbit-quick-search)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java](https://img.shields.io/badge/java-21+-blue.svg)](https://adoptium.net/)

> OpenSearch-backed full-text search across the tables of a QQQ application.

Annotate entity classes or declare tables in config, point the QBit at an OpenSearch cluster, produce it into your `QInstance`, and you get real-time indexing of committed writes, a scheduled catch-up and reconcile, a blackout-free full rebuild, and a permission-aware search API with relevance scoring, typeahead, highlights and pagination.

Requires QQQ 4.1 (the QBit uses 4.1's record-change listeners, after-commit callbacks and runtime services).

## What it does

| Capability | How |
|---|---|
| Real-time indexing | One `RecordChangeListenerInterface` registered on the instance; events are published after the write's transaction commits, so rolled-back writes never reach the index. Failures are recorded in `quickSearchFailedEvent` and replayed by the scheduled basepull. |
| Basepull catch-up | Scheduled process (`schedulerName` plus `basepullRepeatSeconds`) that re-indexes rows changed since the previous run's start, with a configurable overlap, paging by primary key. |
| Reconcile | Scheduled or on-demand process that re-indexes everything in place, then removes documents whose source row is gone, including documents of tables no longer configured. No search blackout. |
| Full rebuild | Indexes every table into a fresh physical index and atomically swaps the alias; the old index keeps serving until the new one is complete. Also upgrades an index created by an older release. |
| Search | `QuickSearchAction`: AND semantics, edge-ngram typeahead with a plain search analyzer, per-field weights, highlights, `tableNames` and `limitPerTable`, bounded paging, `tableLabel` on results. Only tables the session may read are searched; hits are re-read through `QueryAction` so record security locks apply. |
| Connections | HTTP or HTTPS, basic auth, AWS IAM SigV4, custom CA or trust store, mTLS, timeouts, pool size, a transport customizer SPI for anything else. Secrets by `${env.X}` reference. |
| Operations | `quickSearchIndex` (per-table status, counts, last run, last error), `quickSearchIndexRun` (history, purged after `runHistoryRetentionDays`), `quickSearchFailedEvent`, a `quickSearchAdmin` app, explicit permission rules, fail-fast or degraded startup. |

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
    <version>1.0.0-RC.1</version>
</dependency>
```

`1.0.0-RC.1` is the current release candidate; `1.0.0` follows once host applications have adopted it (#12).

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

Annotation and config-driven tables can be mixed; a table may appear in only one of them. Hidden fields are never indexed. A table without the change-detection field (`modifyDate` by default) is covered by reconcile instead of incremental basepull.

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

### Processes

| Process | Trigger | What it does |
|---|---|---|
| Quick Search Basepull Index | Scheduled every `basepullRepeatSeconds` when `schedulerName` is set; also on demand | Replays failed real-time events, re-indexes rows changed since the last run start minus `basepullOverlapSeconds`, purges old run history |
| Quick Search Reconcile Index | Cron via `reconcileCronExpression` when `schedulerName` is set; also on demand (optional `tableName` input) | Re-indexes everything in place, removes documents without a source row and documents of unconfigured tables |
| Quick Search Full Reindex | On demand (optional `tableName` input) | All tables: build a fresh physical index and swap the alias. One table: reconcile algorithm in place |

Without `schedulerName` the processes exist but nothing runs them; the QBit logs a warning at startup.

## Configuration reference

| Field | Default | Description |
|---|---|---|
| `backendName` | required | QQQ backend for the operational tables |
| `opensearchUrl` | | `https://host[:port]`; preferred over `opensearchHost`, `opensearchPort`, `useSsl` |
| `opensearchHost`, `opensearchPort`, `useSsl` | `useSsl=false` | Legacy connection fields |
| `opensearchIndexName` | required | Alias name owned by this QBit; lowercase, OpenSearch naming rules |
| `authMode` | inferred | `NONE`, `BASIC` (inferred when credentials are set), `AWS_SIGV4` |
| `opensearchUsername`, `opensearchPassword` | | BASIC credentials; `${env.X}` references recommended; refused over plain HTTP unless `allowPlaintextCredentials` |
| `awsRegion`, `awsServiceName`, `awsAssumeRoleArn` | region from `AWS_REGION`; `es` | AWS_SIGV4 settings |
| `tls` | JVM defaults | `caCertificatePath` or `trustStorePath`/`trustStorePassword`/`trustStoreType`, `keyStorePath`/`keyStorePassword`/`keyStoreType` (mTLS), `hostnameVerification`, `insecureSkipVerify` (loopback only unless `allowInsecureInProduction`) |
| `connectTimeoutMillis`, `responseTimeoutMillis`, `maxConnections` | 5000, 60000, 30 | Transport settings |
| `transportCustomizer` | | `QCodeReference` to an `OpenSearchTransportCustomizer` (bearer tokens, API keys, interceptors) |
| `startupMode` | `FAIL_FAST` | `FAIL_FAST` fails `produce()` when the cluster is unreachable; `DEGRADED` boots and retries on first use |
| `enableRealTimeIndexing` | `true` | Register the record-change listener |
| `enableBasepullProcess`, `enableMaintenanceProcesses` | `true` | Register basepull; register full reindex and reconcile. `enableScheduledProcesses` is a deprecated alias for both |
| `schedulerName`, `basepullRepeatSeconds`, `reconcileCronExpression`, `reconcileCronTimeZoneId` | none, 300, none | Scheduling |
| `basepullOverlapSeconds` | 300 | Re-read window before the previous run start |
| `defaultBasepullIntervalMinutes` | 5 | Per-table due interval for config-driven tables |
| `runHistoryRetentionDays` | 30 | Purge run records older than this; null keeps them |
| `bulkBatchSize`, `maxBulkRequestBytes`, `sourceBatchSize` | 500, 5 MiB, 1000 | Batch sizes |
| `maxFieldLength` | 10000 | Truncate each indexed value |
| `maxSearchLimit` | 100 | Largest page size |
| `applyRecordSecurityLocks` | `true` | Re-read hits through `QueryAction` |
| `adminPermissionRules` | `HAS_ACCESS_PERMISSION`, base name `quickSearchAdmin` | Rules for the app and processes |
| `tablePermissionRules` | `READ_WRITE_PERMISSIONS` | Rules for the operational tables |
| `tableMetaDataCustomizer` | | Applied to every produced table (backend details, for example) |
| `searchableEntityClasses`, `searchableTables` | at least one | What to index |
| `indexEventPublisher` | synchronous | Replace the publisher (experimental SPI; see below) |

`@QuickSearchable(tableName, fields, basepullIntervalMinutes, basepullTimestampField, enabledByDefault)` and `@QuickSearchField(weight, includeLabel)` keep their 0.x meaning.

## Operational tables

- `quickSearchIndex`: one row per table with `enabled` (honoured by indexing and search, cached for one minute), `basepullIntervalMinutes`, `lastBasepullTime`, `lastReconcileTime`, `lastFullReindexTime`, `lastRunStatus`, `lastErrorMessage`, `recordCount`, `documentCount`, `realTimeErrorCount`, `status` (`ACTIVE` or `NEEDS_REINDEX` when the configured fields changed since the row was created). Unique on `tableName`.
- `quickSearchIndexRun`: `runType` (`BASEPULL`, `RECONCILE`, `FULL_REINDEX`), `status`, counts, `errorMessage`.
- `quickSearchFailedEvent`: real-time events that could not be applied; `status` `PENDING` or `EXHAUSTED` after 10 attempts.

## Extending

`IndexEventPublisher` lets a host replace the synchronous publisher with a queue. In 1.0 the SPI is experimental: `IndexEvent` carries the table, record id, action and the record snapshot, but no sequence number, so an asynchronous consumer must order INDEX and DELETE for the same record itself. `OpenSearchTransportCustomizer` customizes the HTTP transport.

## Building

```bash
./mvnw test                                 # unit tests (memory backend, mocked client)
CI=true ./mvnw verify                       # plus Testcontainers integration tests and the coverage gate (Docker required)
./mvnw verify -Dopensearch.test.image=opensearchproject/opensearch:3.9.0
```

Use the wrapper: Maven 3.10 cannot read the published parent POM. The build imports qqq 4.1.0-RC.1 through the default-active `qqq-snapshot` profile until `qbit-build-parent` 2.1.0 ships (override with `-Dqqq.snapshot.version=...`). Without Docker the integration tests are skipped locally; with `CI=true` they fail instead.

## Upgrading from 0.x

See [docs/MIGRATION-1.0.md](docs/MIGRATION-1.0.md). In short: QQQ 4.1 is required, remove any manual `addQBit` for this QBit, set `schedulerName`, prefer `opensearchUrl` and `${env.}` secrets, and run a full reindex once so the index gets the new mapping.

## License

Apache License, Version 2.0. See [LICENSE](LICENSE).

# QBit: Quick Search

[![Version](https://img.shields.io/badge/version-0.2.0-blue.svg)](https://github.com/QRun-IO/qbit-quick-search)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java](https://img.shields.io/badge/java-21+-blue.svg)](https://adoptium.net/)

> **OpenSearch-Powered Full-Text Search for QQQ Applications**

This QBit provides global search across QQQ application tables via OpenSearch. Annotate entity classes or declare tables via config, configure a connection, produce the QBit, and gain real-time indexing, scheduled basepull catch-up, per-field relevance boosting, and a pagination-aware search API.

## Core Capabilities

- **Real-Time Indexing**: Post-insert, post-update, and post-delete customizers index records immediately on write
- **Basepull Safety Net**: Scheduled process catches records missed during downtime or bulk imports
- **Field-Level Boosting**: `@QuickSearchField(weight = N)` controls relevance ranking per field
- **Edge-Ngram Typeahead**: Custom analyzer enables prefix matching ("ord" finds "order")
- **Paginated Search**: `QuickSearchAction` returns results with scores, highlights, total hits, and hasMore flag
- **Config-Driven Indexing**: `SearchableTableConfig` API indexes tables from third-party QBit jars without modifying their source
- **Extensible Publisher**: `IndexEventPublisher` interface allows swapping in RabbitMQ, Kafka, or any async transport
- **Admin Observability**: Operational tables track index status and run history

## Open Source & Full Control

QBit Quick Search is 100% open source under Apache 2.0. All data stays in your OpenSearch cluster.

## Architecture

### Design Principles

1. **Real-time first, basepull as safety net**: Table customizers index records synchronously on every insert, update, and delete. The basepull process runs on a schedule as a catch-up mechanism for bulk imports, OpenSearch downtime, or any records the real-time path missed.

2. **Annotation and config-driven discovery**: Entity classes marked with `@QuickSearchable` and `@QuickSearchField` are discovered at produce time. Tables from third-party jars can be declared via `SearchableTableConfig` in code. Both paths merge during `produce()`.

3. **Extensible transport**: The `IndexEventPublisher` strategy interface decouples the write path from the indexing path. The default `SynchronousIndexEventPublisher` writes directly to OpenSearch. Consumers can provide a queue-backed implementation for async indexing.

### Technology Stack

- **Java 21** with QQQ 4.x backend modules (versions from `qbit-build-parent` 2.0.0)
- **OpenSearch 2.x** for full-text search with custom edge-ngram analyzer
- **QQQ Framework**: Entities, processes, customizers, permissions, API layer

### Module Organization

```
qbit-quick-search/
  src/main/java/com/kingsrook/qbits/quicksearch/
    QuickSearchQBitConfig.java        -- Configuration and validation
    QuickSearchQBitProducer.java      -- Produces tables, processes, customizers
    QuickSearchQBitContext.java       -- Static runtime context
    QuickSearchableTableConfig.java   -- Per-table index configuration
    SearchableFieldConfig.java        -- Config-driven field definition
    SearchableTableConfig.java        -- Config-driven table definition
    annotations/                      -- @QuickSearchable, @QuickSearchField
    model/                            -- QuickSearchIndex, QuickSearchIndexRun entities
    opensearch/                       -- OpenSearch client, document model, bulk results
    actions/                          -- QuickSearchAction, input/output DTOs
    processes/                        -- AbstractIndexingStep, BasepullIndexStep, FullReindexStep, ReconcileIndexStep
    customizers/                      -- Post-insert, post-update, post-delete customizers
    publisher/                        -- IndexEventPublisher interface, SynchronousIndexEventPublisher
```

## Getting Started

### Prerequisites

- **Java 21+**
- **Maven 3.8+**
- **OpenSearch 2.x** instance
- **QQQ Application** (this is a QBit, not a standalone application)

### Usage

#### Maven dependency

```xml
<dependency>
    <groupId>com.kingsrook.qbits</groupId>
    <artifactId>qbit-quick-search</artifactId>
    <version>0.2.0-SNAPSHOT</version>
</dependency>
```

#### Annotate entity classes

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

#### Minimal setup

```java
QuickSearchQBitConfig config = new QuickSearchQBitConfig()
   .withBackendName("yourBackendName")
   .withOpensearchHost("localhost")
   .withOpensearchPort(9200)
   .withOpensearchIndexName("my-app-search")
   .withSearchableEntityClasses(List.of(Customer.class, Order.class));

new QuickSearchQBitProducer()
   .withConfig(config)
   .produce(qInstance);
```

#### With authentication and SSL

```java
QuickSearchQBitConfig config = new QuickSearchQBitConfig()
   .withBackendName("yourBackendName")
   .withOpensearchHost("search.example.com")
   .withOpensearchPort(443)
   .withUseSsl(true)
   .withOpensearchUsername("admin")
   .withOpensearchPassword("secretPassword")
   .withOpensearchIndexName("my-app-search")
   .withSearchableEntityClasses(List.of(Customer.class));
```

#### With custom publisher (async indexing)

```java
QuickSearchQBitConfig config = new QuickSearchQBitConfig()
   .withBackendName("yourBackendName")
   .withOpensearchHost("localhost")
   .withOpensearchPort(9200)
   .withOpensearchIndexName("my-app-search")
   .withSearchableEntityClasses(List.of(Customer.class))
   .withIndexEventPublisher(new RabbitMqIndexEventPublisher(connectionFactory));
```

#### Config-driven table indexing

When tables come from third-party QBit jars (like qbit-crm or qbit-wms), you cannot add annotations to their entity classes. Use `SearchableTableConfig` and `SearchableFieldConfig` to declare those tables as searchable in your host application config.

```java
QuickSearchQBitConfig config = new QuickSearchQBitConfig()
   .withBackendName("rdbms")
   .withOpensearchHost("localhost")
   .withOpensearchPort(9200)
   .withOpensearchIndexName("voyage-search")
   .withSearchableTable("crmContact", List.of(
      new SearchableFieldConfig("firstName").withWeight(2),
      new SearchableFieldConfig("lastName").withWeight(2),
      new SearchableFieldConfig("email").withWeight(3),
      new SearchableFieldConfig("phone").withWeight(1)))
   .withSearchableTable(new SearchableTableConfig("crmFormSubmission", List.of(
      new SearchableFieldConfig("email").withWeight(3),
      new SearchableFieldConfig("firstName").withWeight(1),
      new SearchableFieldConfig("message").withWeight(1)))
      .withRecordLabelFormat("%s - %s", "formType", "email"));
```

`withSearchableTable()` accepts either a `(String tableName, List<SearchableFieldConfig> fields)` shorthand or a full `SearchableTableConfig` for advanced options.

##### SearchableFieldConfig

| Method | Description |
|--------|-------------|
| `new SearchableFieldConfig(fieldName)` | Constructor; QQQ field name is required |
| `.withWeight(int)` | Boost factor for relevance ranking (default 1) |
| `.withIncludeLabel(boolean)` | Prefix indexed text with the field label (default false) |

##### SearchableTableConfig

| Method | Description |
|--------|-------------|
| `new SearchableTableConfig(tableName, fields)` | Constructor; table name and at least one field required |
| `.withBasepullIntervalMinutes(int)` | Override basepull schedule (defaults to config-level setting) |
| `.withBasepullTimestampField(String)` | Change detection field (default `"modifyDate"`) |
| `.withEnabledByDefault(boolean)` | Whether indexing starts active (default `true`) |
| `.withRecordLabelFormat(String format, String... fields)` | `String.format` pattern and field names for search result display label |

##### Record label override

By default, search results use QQQ's record label for display. Config-driven tables can override this with `withRecordLabelFormat()`. The format string is passed to `String.format()` with field values pulled from the record in the order specified.

```java
new SearchableTableConfig("crmFormSubmission", fields)
   .withRecordLabelFormat("%s - %s", "formType", "email");
// produces labels like "Contact Form - user@example.com"
```

#### Mixing annotations and config

Annotations and config-driven declarations can be used together. Both paths merge during `produce()`. Each table must appear in only one source; duplicate table names across annotation and config sources will produce a validation error.

```java
QuickSearchQBitConfig config = new QuickSearchQBitConfig()
   .withBackendName("rdbms")
   .withOpensearchHost("localhost")
   .withOpensearchPort(9200)
   .withOpensearchIndexName("my-app-search")
   .withSearchableEntityClasses(List.of(Customer.class, Order.class))
   .withSearchableTable("crmContact", List.of(
      new SearchableFieldConfig("firstName").withWeight(2),
      new SearchableFieldConfig("lastName").withWeight(2),
      new SearchableFieldConfig("email").withWeight(3)));
```

## Searching

Once the QBit is produced, use `QuickSearchAction` to execute searches:

```java
QuickSearchOutput output = new QuickSearchAction().execute(
   new QuickSearchInput()
      .withSearchTerm("widget")
      .withLimit(25)
      .withOffset(0));

// Check results
System.out.println("Total hits: " + output.getTotalHits());
System.out.println("Has more: " + output.getHasMore());

for(QuickSearchResult result : output.getResults())
{
   System.out.println(result.getTableName() + ":" + result.getRecordId()
      + " - " + result.getRecordLabel()
      + " (score: " + result.getScore() + ")");
}
```

To filter results to a single table:

```java
QuickSearchOutput output = new QuickSearchAction().execute(
   new QuickSearchInput()
      .withSearchTerm("blue")
      .withTableName("customer")
      .withLimit(10));
```

### Triggering a Full Reindex

The full reindex process can be triggered programmatically:

```java
RunBackendStepInput input = new RunBackendStepInput();
input.addValue("tableName", "customer"); // optional: omit to reindex all tables
RunBackendStepOutput output = new RunBackendStepOutput();
new FullReindexStep().run(input, output);
```

Or through the QQQ admin UI via the "Full Reindex" process.

### Reconciling the Index

If the index may have drifted from the source tables (for example, deletes that
happened while OpenSearch was down), run the reconcile process. It re-indexes
every source record, then removes documents whose source record no longer
exists. It does not wipe the index first, so search keeps working while it runs.

```java
RunBackendStepInput input = new RunBackendStepInput();
input.addValue("tableName", "customer"); // optional: omit to reconcile all tables
RunBackendStepOutput output = new RunBackendStepOutput();
new ReconcileIndexStep().run(input, output);
// output values: recordsIndexed, documentsRemoved
```

Or through the QQQ admin UI via the "Reconcile Index" process. If any document
fails to index, the run is marked `FAILED` and no documents are removed.

## Data Model

### Tables (2)

| Table | Description |
|-------|-------------|
| `quickSearchIndex` | Per-table index configuration, status, and basepull tracking |
| `quickSearchIndexRun` | Individual indexing run history with record counts and error tracking |

### Processes (3)

| Process | Description |
|---------|-------------|
| Basepull Index | Scheduled: queries each source table for records modified since last run, indexes in batches |
| Full Reindex | On-demand: deletes and re-indexes all records for one or all tables |
| Reconcile Index | On-demand: re-indexes all records for one or all tables, then removes documents with no source record, without wiping the index first |

## Configuration Reference

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `backendName` | `String` | required | QQQ backend for operational tables |
| `opensearchHost` | `String` | required | OpenSearch hostname |
| `opensearchPort` | `Integer` | required | OpenSearch port |
| `opensearchIndexName` | `String` | required | OpenSearch index name |
| `searchableEntityClasses` | `List<Class<?>>` | none | Entity classes with `@QuickSearchable` |
| `searchableTables` | `List<SearchableTableConfig>` | none | Config-driven table definitions |
| `opensearchUsername` | `String` | none | HTTP basic auth username |
| `opensearchPassword` | `String` | none | HTTP basic auth password |
| `useSsl` | `Boolean` | `false` | Use HTTPS |
| `tableNamePrefix` | `String` | none | Prefix for produced table names |
| `enableRealTimeIndexing` | `Boolean` | `true` | Register post-insert/update/delete customizers |
| `enableScheduledProcesses` | `Boolean` | `true` | Register scheduled basepull process |
| `defaultBasepullIntervalMinutes` | `Integer` | `5` | Default basepull schedule |
| `bulkBatchSize` | `Integer` | `500` | Documents per OpenSearch bulk request |
| `sourceBatchSize` | `Integer` | `1000` | Records per source table query batch |
| `indexEventPublisher` | `IndexEventPublisher` | sync | Custom publisher for async transport |

At least one of `searchableEntityClasses` or `searchableTables` must be provided. Both can be used together.

## Annotations

### `@QuickSearchable`

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `tableName` | `String` | required | QQQ table name |
| `fields` | `String[]` | `{}` | Fallback field list if no `@QuickSearchField` annotations |
| `basepullIntervalMinutes` | `int` | `5` | Basepull schedule for this table |
| `basepullTimestampField` | `String` | `"modifyDate"` | Field for change detection |
| `enabledByDefault` | `boolean` | `true` | Active on first produce |

### `@QuickSearchField`

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `weight` | `int` | `1` | Boost factor for search relevance |
| `includeLabel` | `boolean` | `false` | Prefix value with field label in indexed text |

## Companion QBit Integration

### qbit-crm

Index `crmContact`, `crmCompany`, and `crmDeal` for full-text global search across CRM records.

### qbit-wms

Index `wmsItem` and `wmsLocation` for warehouse item and location search.

## Testing

```bash
mvn test                    # Run unit tests
mvn verify                  # Run unit tests + integration tests (requires Docker)
mvn test -Dtest=ClassName   # Run single test class
mvn verify -Pqqq-snapshot    # Verify against the next qqq line (4.1.0-SNAPSHOT)
```

The qqq version comes from `qbit-build-parent`. The opt-in `qqq-snapshot`
profile imports `qqq-bom-pom` at `${qqq.snapshot.version}` (default
`4.1.0-SNAPSHOT`) ahead of the parent's BOM and adds the Central snapshots
repository. Pick another version with `-Dqqq.snapshot.version=...`.

Without Docker, integration tests are skipped locally. With `CI=true`, they fail
instead, so a CI build cannot pass with its integration tests silently skipped.

### Coverage

- **70%+ instruction coverage** (JaCoCo enforced)
- **90%+ class coverage** (JaCoCo enforced)
- All unit tests run in-memory via MemoryRecordStore (no OpenSearch required)
- Integration tests use Testcontainers with real OpenSearch

## Documentation

- **[QQQ Wiki](https://github.com/Kingsrook/qqq/wiki)** - Framework documentation
- **[QBit Development Guide](https://github.com/Kingsrook/qqq/wiki/QBit-Development)** - How QBits work

## Contributing

QBit Quick Search is open source and welcomes contributions.

- **[Report Issues](https://github.com/QRun-IO/qbit-quick-search/issues)** - Bug reports and feature requests
- **[QQQ Contribution Guide](https://github.com/Kingsrook/qqq/wiki/Contribution-Guidelines)** - How to contribute

## About Kingsrook

QBit Quick Search is built by **[Kingsrook](https://qrun.io)** - making engineers more productive through intelligent automation and developer tools.

- **Website**: [https://qrun.io](https://qrun.io)
- **Contact**: [contact@kingsrook.com](mailto:contact@kingsrook.com)
- **GitHub**: [https://github.com/QRun-IO](https://github.com/QRun-IO)

## License

This project is licensed under the **Apache License, Version 2.0** - see the [LICENSE](LICENSE) file for details.

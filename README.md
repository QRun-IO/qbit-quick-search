# QBit Quick Search

OpenSearch-backed full-text search for QQQ applications. Annotate your entity classes, configure a connection, call `produce()`, and your QQQ instance gains real-time indexing, basepull catch-up, and a pagination-aware search API.

## Features

- Real-time indexing via post-insert, post-update, and post-delete customizers
- Basepull safety net catches records missed during downtime
- Per-field boost weights for relevance tuning
- Edge-ngram analyzer for typeahead (prefix) search
- Paginated search results via `QuickSearchAction`
- Extensible `IndexEventPublisher` interface (swap in RabbitMQ, Kafka, etc.)
- Admin app with run history tables produced automatically

## Requirements

- Java 21
- OpenSearch 2.x
- QQQ 0.40+

## Quick Start

### 1. Add the Maven dependency

```xml
<dependency>
    <groupId>com.kingsrook.qbits</groupId>
    <artifactId>qbit-quick-search</artifactId>
    <version>0.1.0</version>
</dependency>
```

### 2. Annotate an entity class

```java
@QuickSearchable(tableName = "customer")
public class Customer
{
   @QuickSearchField(weight = 3)
   private String name;

   @QuickSearchField(weight = 1)
   private String email;

   @QuickSearchField(weight = 1, includeLabel = true)
   private String accountNumber;

   // ... other fields
}
```

### 3. Build the config and produce the QBit

```java
QuickSearchQBitConfig config = new QuickSearchQBitConfig()
   .withBackendName("myRdbmsBackend")
   .withOpensearchHost("localhost")
   .withOpensearchPort(9200)
   .withOpensearchIndexName("my-app-search")
   .withSearchableEntityClasses(List.of(Customer.class, Order.class));

new QuickSearchQBitProducer()
   .withConfig(config)
   .produce(qInstance);
```

The producer registers tables, processes, and customizers into the `QInstance`. No further wiring is needed.

## Configuration Reference

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `backendName` | `String` | required | Name of the QQQ backend for operational tables |
| `opensearchHost` | `String` | required | OpenSearch hostname or IP |
| `opensearchPort` | `Integer` | required | OpenSearch port (e.g. 9200) |
| `opensearchIndexName` | `String` | required | Name of the OpenSearch index |
| `searchableEntityClasses` | `List<Class<?>>` | required | Entity classes annotated with `@QuickSearchable` |
| `opensearchUsername` | `String` | none | Username for HTTP basic auth (must pair with password) |
| `opensearchPassword` | `String` | none | Password for HTTP basic auth (must pair with username) |
| `useSsl` | `Boolean` | `false` | Use HTTPS for OpenSearch connections |
| `tableNamePrefix` | `String` | none | Prefix applied to all produced table and process names |
| `enableRealTimeIndexing` | `Boolean` | `true` | Register post-insert/update/delete customizers on source tables |
| `enableScheduledProcesses` | `Boolean` | `true` | Register scheduled basepull and full-reindex processes |
| `defaultBasepullIntervalMinutes` | `Integer` | `5` | Default interval for basepull polling |
| `bulkBatchSize` | `Integer` | `500` | Documents per OpenSearch bulk request |
| `sourceBatchSize` | `Integer` | `1000` | Records fetched per QQQ query during reindex |
| `indexEventPublisher` | `IndexEventPublisher` | built-in sync | Override the publisher (see Extending below) |

## Annotations

### `@QuickSearchable`

Applied to an entity class. Tells the producer which QQQ table this class maps to and how to configure basepull.

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `tableName` | `String` | required | QQQ table name |
| `fields` | `String[]` | `{}` | Field names to index (fallback if no `@QuickSearchField` annotations present) |
| `basepullIntervalMinutes` | `int` | `5` | How often the basepull process checks for new/modified records |
| `basepullTimestampField` | `String` | `"modifyDate"` | Field queried for changes since last basepull |
| `enabledByDefault` | `boolean` | `true` | Whether this table is enabled in the `quickSearchIndex` table on first run |

### `@QuickSearchField`

Applied to individual fields within a `@QuickSearchable` class. Takes precedence over the `fields` array when present.

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `weight` | `int` | `1` | Boost factor in OpenSearch query scoring. Higher values rank this field higher. |
| `includeLabel` | `boolean` | `false` | Prefix the field value with its QQQ field label in the indexed text (useful for codes or numbers that benefit from context) |

Example using both annotations together:

```java
@QuickSearchable(tableName = "order")
public class Order
{
   @QuickSearchField(weight = 5)
   private String orderNumber;

   @QuickSearchField(weight = 2, includeLabel = true)
   private String status;

   @QuickSearchField(weight = 1)
   private String customerName;
}
```

## How It Works

**Indexing** happens through two complementary paths. When `enableRealTimeIndexing` is true, the producer attaches `QuickSearchPostInsertCustomizer`, `QuickSearchPostUpdateCustomizer`, and `QuickSearchPostDeleteCustomizer` to every source table. These customizers fire after each write and immediately dispatch index or delete events through the configured `IndexEventPublisher`. The basepull process (`BasepullIndexStep`) runs on a schedule and queries each source table for records modified since the last run, re-indexing them. Full reindexing is available via `FullReindexStep`.

**Searching** is performed through `QuickSearchAction`. Pass a `QuickSearchInput` with a query string and optional pagination parameters. The action issues a multi-match query across all indexed fields with per-field boost weights applied, and returns a `QuickSearchOutput` containing a list of `QuickSearchResult` objects with table name, record ID, and a display label.

## Extending

To replace the default synchronous publisher, implement `IndexEventPublisher` and supply it in config:

```java
public class RabbitMqIndexEventPublisher implements IndexEventPublisher
{
   @Override
   public void publishIndexEvents(List<IndexEvent> events) throws QException
   {
      // serialize events and publish to exchange
   }

   @Override
   public void publishDeleteEvents(List<IndexEvent> events) throws QException
   {
      // serialize events and publish to exchange
   }

   @Override
   public void close() throws QException
   {
      // release connection resources
   }
}
```

Then wire it in:

```java
QuickSearchQBitConfig config = new QuickSearchQBitConfig()
   // ... connection fields ...
   .withIndexEventPublisher(new RabbitMqIndexEventPublisher(connectionFactory));
```

A separate consumer process reads from the queue and calls `QuickSearchOpenSearchClient` directly to perform the actual bulk index operations.

## License

Apache License, Version 2.0. See [LICENSE](LICENSE).

# QBit: Quick Search

[![Version](https://img.shields.io/badge/version-0.1.0-blue.svg)](https://github.com/QRun-IO/qbit-quick-search)
[![License](https://img.shields.io/badge/license-GNU%20Affero%20GPL%20v3-green.svg)](https://www.gnu.org/licenses/agpl-3.0.en.html)
[![Java](https://img.shields.io/badge/java-21+-blue.svg)](https://adoptium.net/)

> **OpenSearch-Powered Full-Text Search for QQQ Applications**

This QBit provides global search across QQQ application tables via OpenSearch. Annotate entity classes, configure a connection, produce the QBit, and gain real-time indexing, scheduled basepull catch-up, per-field relevance boosting, and a pagination-aware search API.

## Core Capabilities

- **Real-Time Indexing**: Post-insert, post-update, and post-delete customizers index records immediately on write
- **Basepull Safety Net**: Scheduled process catches records missed during downtime or bulk imports
- **Field-Level Boosting**: `@QuickSearchField(weight = N)` controls relevance ranking per field
- **Edge-Ngram Typeahead**: Custom analyzer enables prefix matching ("ord" finds "order")
- **Paginated Search**: `QuickSearchAction` returns results with scores, highlights, total hits, and hasMore flag
- **Extensible Publisher**: `IndexEventPublisher` interface allows swapping in RabbitMQ, Kafka, or any async transport
- **Admin Observability**: Operational tables track index status and run history

## Open Source & Full Control

QBit Quick Search is 100% open source under AGPL v3. All data stays in your OpenSearch cluster.

## Architecture

### Design Principles

1. **Real-time first, basepull as safety net**: Table customizers index records synchronously on every insert, update, and delete. The basepull process runs on a schedule as a catch-up mechanism for bulk imports, OpenSearch downtime, or any records the real-time path missed.

2. **Annotation-driven discovery**: Entity classes marked with `@QuickSearchable` and `@QuickSearchField` are discovered at produce time. No manual registration of fields or tables required.

3. **Extensible transport**: The `IndexEventPublisher` strategy interface decouples the write path from the indexing path. The default `SynchronousIndexEventPublisher` writes directly to OpenSearch. Consumers can provide a queue-backed implementation for async indexing.

### Technology Stack

- **Java 21** with QQQ backend modules
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
    annotations/                      -- @QuickSearchable, @QuickSearchField
    model/                            -- QuickSearchIndex, QuickSearchIndexRun entities
    opensearch/                       -- OpenSearch client, document model, bulk results
    actions/                          -- QuickSearchAction, input/output DTOs
    processes/                        -- AbstractIndexingStep, BasepullIndexStep, FullReindexStep
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
    <version>0.1.0-SNAPSHOT</version>
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

## Data Model

### Tables (2)

| Table | Description |
|-------|-------------|
| `quickSearchIndex` | Per-table index configuration, status, and basepull tracking |
| `quickSearchIndexRun` | Individual indexing run history with record counts and error tracking |

### Processes (2)

| Process | Description |
|---------|-------------|
| Basepull Index | Scheduled: queries each source table for records modified since last run, indexes in batches |
| Full Reindex | On-demand: deletes and re-indexes all records for one or all tables |

## Configuration Reference

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `backendName` | `String` | required | QQQ backend for operational tables |
| `opensearchHost` | `String` | required | OpenSearch hostname |
| `opensearchPort` | `Integer` | required | OpenSearch port |
| `opensearchIndexName` | `String` | required | OpenSearch index name |
| `searchableEntityClasses` | `List<Class<?>>` | required | Entity classes with `@QuickSearchable` |
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
mvn test                    # Run all 174 unit tests
mvn verify                  # Run unit tests + integration tests (requires Docker)
mvn test -Dtest=ClassName   # Run single test class
```

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

- **[Report Issues](https://github.com/QRun-IO/qqq/issues)** - Bug reports and feature requests
- **[QQQ Contribution Guide](https://github.com/Kingsrook/qqq/wiki/Contribution-Guidelines)** - How to contribute

## About Kingsrook

QBit Quick Search is built by **[Kingsrook](https://qrun.io)** - making engineers more productive through intelligent automation and developer tools.

- **Website**: [https://qrun.io](https://qrun.io)
- **Contact**: [contact@kingsrook.com](mailto:contact@kingsrook.com)
- **GitHub**: [https://github.com/QRun-IO](https://github.com/QRun-IO)

## License

This project is licensed under the **GNU Affero General Public License v3.0** - see the [LICENSE](LICENSE) file for details.

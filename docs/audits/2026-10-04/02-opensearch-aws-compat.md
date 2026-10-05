# Audit 02: OpenSearch and AWS compatibility of qbit-quick-search

Date: 2026-10-04. Repo reviewed read-only at `/Users/james.maes/Git.Local/QRunIO/qbit-quick-search-b1` (branch `feature/GH-6-repin-and-drift-fix`). Compile experiments were run on a copy under the scratchpad, never in the repo.

Files reviewed: `src/main/java/com/kingsrook/qbits/quicksearch/opensearch/QuickSearchOpenSearchClient.java` (CLIENT), `OpenSearchDocument.java`, `BulkIndexResult.java`, `QuickSearchQBitConfig.java` (CONFIG), `processes/IndexingUtils.java`, `actions/QuickSearchAction.java`, `pom.xml`, and the three `*IntegrationTest.java` classes.

## 0. Resolved dependency facts (from `mvn dependency:tree` in the repo)

| Artifact | Resolved | Note |
| --- | --- | --- |
| org.opensearch.client:opensearch-java | 2.10.0 | Pinned in pom.xml line 24. Its own POM declares httpclient5 5.3.1, httpcore5 5.2.4, jackson 2.17.0, and a runtime dependency on opensearch-rest-client 2.12.0 (https://repo1.maven.org/maven2/org/opensearch/client/opensearch-java/2.10.0/opensearch-java-2.10.0.pom) |
| org.opensearch.client:opensearch-rest-client | 2.12.0 (runtime) | Pulled only because the 2.x client still ships the deprecated RestClientTransport; the 3.x client POM no longer lists it (https://repo1.maven.org/maven2/org/opensearch/client/opensearch-java/3.9.0/opensearch-java-3.9.0.pom) |
| org.apache.httpcomponents.client5:httpclient5 | 5.6.3 | Managed by the qqq BOM |
| org.apache.httpcomponents.core5:httpcore5 / httpcore5-h2 | 5.4.3 | Managed by the qqq BOM |
| com.fasterxml.jackson.core:* | 2.21.5 | Managed by the qqq BOM |
| software.amazon.awssdk:* (auth, regions, apache-client, netty-nio-client, ...) | 2.41.10 | Already on the classpath, but only transitively via qqq-backend-core -> quicksight. Not something this library should rely on without declaring it |

Maven Central: latest opensearch-java is 3.10.0 (`<latest>3.10.0</latest>` in https://repo1.maven.org/maven2/org/opensearch/client/opensearch-java/maven-metadata.xml); the 2.x line ended at 2.26.0. The OpenSearch Java client docs now install 3.10.0 (https://docs.opensearch.org/latest/clients/java/).

## 1. Research findings (external)

### 1.1 opensearch-java client

* Compatibility matrix: client 2.x.0 supports OpenSearch 1.x to 2.x; client 3.x.0 supports 1.x to 3.x. JDK 8/11/17/21 for both lines. (https://github.com/opensearch-project/opensearch-java/blob/main/COMPATIBILITY.md)
* `ApacheHttpClient5Transport` is the default and recommended transport; `RestClientTransport` and `org.opensearch.client.RestClient` are deprecated and will be removed. (https://docs.opensearch.org/latest/clients/java/)
* OpenSearch 3.0 brings HTTP/2 and `ApacheHttpClient5Transport` switches to HTTP/2 when available; `HttpVersionPolicy` can be forced through `setHttpClientConfigCallback`. (https://github.com/opensearch-project/opensearch-java/blob/main/USER_GUIDE.md)
* 3.0.0 breaking changes (https://raw.githubusercontent.com/opensearch-project/opensearch-java/main/CHANGELOG.md and https://raw.githubusercontent.com/opensearch-project/opensearch-java/main/UPGRADING.md): more conservative URL path encoding, `searchAfter` and `Hit.sort` now `FieldValue`, `listAllPit` -> `getAllPits`, several type corrections, removal of ES-only namespaces (`features`, `shutdown`, `termsEnum`, `indices.upgrade`). Nothing in the transport builders or `JacksonJsonpMapper` changed. One undocumented change does hit this repo: `DeleteByQueryRequest.Builder.refresh(...)` takes the `Refresh` enum instead of `boolean` (see empirical test below).
* Empirical compile check (scratch copy, `opensearch.version` set to 3.10.0): exactly one compile error, CLIENT line 427 `.refresh(true)` ("boolean cannot be converted to org.opensearch.client.opensearch._types.Refresh"). After changing it to `Refresh.True`, `mvn test-compile` and all 233 unit tests pass. Resolved tree with 3.10.0: httpcore5 5.4.3, httpclient5 5.6.3 (BOM), plus a new `commons-logging:1.3.6` transitive. The 3.x client POM is built against httpclient5 5.6 / httpcore5 5.4.2, so the BOM versions are in range.
* httpcore5/httpclient5 constraint with the current 2.10.0 client: it was built against httpclient5 5.3.1 / httpcore5 5.2.4 but runs on 5.6.3 / 5.4.3 here; the repo already carries a workaround for the 5.6 content-compression behaviour (CLIENT lines 124 to 130). Moving to the 3.x client removes that version skew.
* Elasticsearch-compatible endpoints: the compatibility matrix lists only OpenSearch. A maintainer answer on the forum says clients generally only bridge adjacent majors (ES 7.10 <-> OpenSearch 1.x) and that 2.x clients against ES 7.10 hit renamed APIs. (https://forum.opensearch.org/t/opensearch-api-compatibility-with-elastic-search/17647). Legacy Elasticsearch 7.10 domains on AWS should be declared out of scope.
* SigV4: `AwsSdk2Transport(SdkHttpClient, endpointWithoutScheme, "es" | "aoss", Region, AwsSdk2TransportOptions)`; credentials from the default provider chain; `aws-crt-client` is the recommended SdkHttpClient and the AWS SDK `ApacheHttpClient` is discouraged (it cannot send bodies on GET/DELETE and `AwsSdk2Transport` throws `TransportException` for such calls). (https://docs.opensearch.org/latest/clients/java/#connecting-to-amazon-opensearch-service and https://github.com/opensearch-project/opensearch-java/blob/main/guides/auth.md). The AWS SDK artifacts are compileOnly in the client, so the application must declare them (release notes for #628 in https://github.com/opensearch-project/opensearch-java/releases/tag/v3.0.0).

### 1.2 Amazon OpenSearch Service (managed domains)

* Engine versions offered today: OpenSearch 3.5, 3.3, 3.1, 2.19, 2.17, 2.15, 2.13, 2.11, 2.9, 2.7, 2.5, 2.3, 1.3 to 1.0, plus legacy Elasticsearch up to 7.10. 2.3 to 2.9 left standard support in Nov 2025; 2.11 to 2.17 end Nov 2027. (https://docs.aws.amazon.com/opensearch-service/latest/developerguide/what-is.html)
* Endpoint and TLS: "OpenSearch Service only accepts connections over port 80 (HTTP) or 443 (HTTPS)" (https://docs.aws.amazon.com/opensearch-service/latest/developerguide/vpc.html); TLS 1.2 required, 1.3 recommended (https://docs.aws.amazon.com/opensearch-service/latest/developerguide/infrastructure-security.html). No custom port; endpoints are `https://search-<domain>-<id>.<region>.es.amazonaws.com` (https://docs.aws.amazon.com/opensearch-service/latest/developerguide/createupdatedomains.html).
* FGAC: master user is either an IAM principal or an internal-database user. With an internal master user "you can use HTTP basic authentication (as well as IAM credentials)". If the domain access policy names IAM principals, clients must sign with SigV4; "You can't sign a request with a username and password and IAM credentials", so AWS recommends an access policy that does not require signed requests when using basic auth. Master user roles are `all_access` and `security_manager`. (https://docs.aws.amazon.com/opensearch-service/latest/developerguide/fgac.html)
* Signing service name for domains is `es`; for Serverless it is `aoss` and `x-amz-content-sha256` is required. (https://docs.aws.amazon.com/opensearch-service/latest/developerguide/serverless-comparison.html)
* Supported operations: the per-version operation lists for managed domains include `/_bulk`, `/_delete_by_query`, `/_refresh`, `/_reindex`, `/_update_by_query` and all index-path operations, so every call this library makes is available on a managed domain. (https://docs.aws.amazon.com/opensearch-service/latest/developerguide/supported-operations.html)
* Request payload limits: 10 MiB on t2/t3 and `*.large` instance types, 100 MiB on xlarge and above. (https://docs.aws.amazon.com/opensearch-service/latest/developerguide/limits.html)
* Permissions on a FGAC domain follow the security plugin model (section 1.4).

### 1.3 Amazon OpenSearch Serverless (AOSS)

* Supported API table (https://docs.aws.amazon.com/opensearch-service/latest/developerguide/serverless-genref.html): `aoss:CreateIndex` (PUT index), `aoss:DescribeIndex` (HEAD index, GET mappings/settings), `aoss:WriteDocument` (POST _bulk, DELETE index/_doc/id, PUT index/_doc/id "for search collection types only", POST index/_doc), `aoss:ReadDocument` (GET/POST _search, _count, _msearch, PIT). `_delete_by_query`, `_update_by_query`, `_reindex` and `_refresh` do not appear anywhere in the table. Aliases exist under `aoss:CreateCollectionItems` / `UpdateCollectionItems`.
* Collection types: Search, Vector search, Time series. Custom document IDs and upserts are only allowed for search collections. (https://docs.aws.amazon.com/opensearch-service/latest/developerguide/serverless-overview.html and the Python note in https://docs.aws.amazon.com/opensearch-service/latest/developerguide/serverless-clients.html)
* Settings: "The number of shards, number of intervals, and refresh interval are not modifiable and are handled by OpenSearch Serverless"; refresh interval is approximately 10 seconds for search and time series collections. (serverless-overview.html)
* Clients must sign with service `aoss`, include `x-amz-content-sha256`, and "must be compatible with OpenSearch 3.x" (serverless-overview.html; the older serverless-clients.html page still says 2.x). The OpenSearch Java docs note AOSS does not support the `refresh` parameter and does not serve the root endpoint (https://docs.opensearch.org/latest/clients/java/#connecting-to-amazon-opensearch-serverless).
* Data access policy minimum for an ingesting principal: `aoss:CreateIndex`, `aoss:WriteDocument`, `aoss:UpdateIndex` on `index/<collection>/<index>`; this library also needs `aoss:DescribeIndex` (exists check) and `aoss:ReadDocument` (search). (serverless-clients.html)
* Quotas: 1000 indexes per collection (https://docs.aws.amazon.com/general/latest/gr/opensearch-service.html). No per-request payload limit is documented on that page.
* Which of this library's calls fail on AOSS: the connection itself (basic auth transport, no SigV4), `deleteDocumentsForTable` (`_delete_by_query`, CLIENT line 392), `deleteDocumentsIndexedBefore` (`_delete_by_query` with `refresh=true`, CLIENT line 424), `refreshIndex` (`_refresh`, CLIENT line 579), and all indexing with custom `_id` unless the collection is of type Search. `ensureIndexExists`, bulk index/delete, single delete, and search with highlight and from/size are within the supported surface.

### 1.4 Self-managed OpenSearch with the security plugin

* Demo config and Docker image: since 2.12 a custom admin password in `OPENSEARCH_INITIAL_ADMIN_PASSWORD` is required or the node does not start; the password must be 8 to 100 chars, mixed classes, and rated "strong" by zxcvbn. `DISABLE_SECURITY_PLUGIN=true` disables the plugin; the demo config installs self-signed certificates. (https://docs.opensearch.org/latest/install-and-configure/install-opensearch/docker/ and https://docs.opensearch.org/latest/security/configuration/demo-configuration/)
* TLS with self-signed certs: the official Java client example builds an `SSLContext` (trust-all for demo, or a truststore) and installs it via `ClientTlsStrategyBuilder` + `PoolingAsyncClientConnectionManager` inside `setHttpClientConfigCallback`; the RestClient transport variant relies on the JVM truststore system properties. (https://docs.opensearch.org/latest/clients/java/#connecting-to-opensearch)
* Auth backends: HTTP basic against the internal user database; client certificate (mTLS) via `plugins.security.ssl.http.clientauth_mode` and a `clientcert` auth domain mapped to roles by CN (https://docs.opensearch.org/latest/security/authentication-backends/client-auth/); JWT bearer tokens (HMAC, RSA, ECDSA, JWKS since 3.3) in the `Authorization` header (https://docs.opensearch.org/latest/security/authentication-backends/jwt/); API tokens with permissions bound to the token were introduced in OpenSearch 3.7 (https://github.com/opensearch-project/opensearch-build/blob/main/release-notes/opensearch-release-notes-3.7.0.md).
* Permissions for this library's operations (default action groups, https://docs.opensearch.org/latest/security/access-control/default-action-groups/): index-level `create_index` (`indices:admin/create`, `indices:admin/mapping/put`) for `ensureIndexExists`; `read` or `search` for `_search`; `write` (`indices:data/write*`, which covers index, bulk items, delete, and `delete/byquery`) and `delete`; `indices:admin/refresh*` is not in any default group except `manage` (`indices:admin/*`) and must be added for `refreshIndex`; cluster-level `cluster_composite_ops` for `indices:data/write/bulk`. The exists check (HEAD index) also needs an `indices:admin/*` read permission; verify the exact action name against the permissions reference when writing the README role.
* Versions: 2.x is in maintenance (latest 2.19.6, maintained until 4.0 GA); 3.x is current (3.9.0 released 2026-09-29; 3.10.0 scheduled Nov 2026). (https://opensearch.org/releases/)

### 1.5 Testcontainers

* Docker Hub has `opensearchproject/opensearch` tags 2.19.0 to 2.19.6 and 3.0.0 to 3.9.0 (https://hub.docker.com/v2/repositories/opensearchproject/opensearch/tags).
* The official module `org.opensearch:opensearch-testcontainers` is at 4.1.0 (requires Testcontainers 2.0.0+ and JDK 17+); 3.0.x works with Testcontainers 1.20.0+ and JDK 21. `OpenSearchContainer` disables security by default and offers `withSecurityEnabled()` with HTTPS plus generated credentials. (https://github.com/opensearch-project/opensearch-testcontainers/blob/main/README.md and https://repo1.maven.org/maven2/org/opensearch/opensearch-testcontainers/maven-metadata.xml)

### 1.6 Server-side behaviours relevant to the mapping and query design

* `index.max_result_window` default 10000 caps `from + size` (https://docs.opensearch.org/latest/install-and-configure/configuring-opensearch/index-settings/).
* Search analyzer resolution order: query `analyzer`, field `search_analyzer`, index `default_search`, field `analyzer`, `standard`. The docs' edge n-gram example sets `analyzer: edge_ngram_analyzer` with `search_analyzer: standard` so the query is not n-grammed. (https://docs.opensearch.org/latest/analyzers/search-analyzers/)
* Edge n-gram filter: `max_gram` too low means long search terms do not match; the docs suggest a `truncate` filter as search analyzer to mitigate. (https://docs.opensearch.org/latest/analyzers/token-filters/edge-ngram/)
* Dynamic mapping: strings become `text` + `keyword` subfield, or `date` when `date_detection` (default on) matches `strict_date_optional_time`; JSON numbers become `long`/`float`; `index.mapping.total_fields.limit` defaults to 1000; `flat_object` or `dynamic_templates` are the recommended defences. (https://docs.opensearch.org/latest/mappings/ and https://docs.opensearch.org/latest/mappings/mapping-explosion/)

## 2. Compatibility matrix

| Target | Works today? | What breaks | Change needed |
| --- | --- | --- | --- |
| OpenSearch 2.x self-managed, security disabled (the integration-test setup) | Yes | Nothing; this is the only tested configuration (image 2.11.0) | Test against 2.19.x as well (F10) |
| OpenSearch 2.x self-managed, security plugin, basic auth over TLS | Partly | Demo or private-CA certificates fail the JVM default trust check; no truststore or insecure option exists (CONFIG exposes only `useSsl`). Only basic auth is wired; no client-cert, JWT or API-token path | Add TLS trust configuration (F1), document required roles (F13); optional auth-header or SSLContext hooks (F3) |
| OpenSearch 3.x self-managed | Not supported by the pinned client | opensearch-java 2.10.0 is documented for 1.x to 2.x only; the server negotiates HTTP/2 with the Apache 5 transport | Bump to opensearch-java 3.10.0; one-line change at CLIENT line 427 (F2); add 3.x to the IT matrix (F10) |
| AWS managed domain 2.x or 3.x, FGAC with internal-database master user, HTTP basic auth | Yes for 2.x (3.x after F2) | Needs `opensearchPort = 443`, `useSsl = true`, and a domain access policy that does not require signed requests. AWS certificates are publicly trusted so TLS works. Every API the library calls is on the managed-domain supported list. Payload limit 10 MiB on small instances with count-only batching (F8); no timeouts (F9) | F2 for 3.x domains, F8, F9, README guidance |
| AWS managed domain, IAM master user or IAM principals in the access policy (SigV4) | No | Client only does basic auth; AWS rejects unsigned requests when IAM principals are in the policy; no `AwsSdk2Transport` path, region, or service-name configuration | Add an optional SigV4 transport mode: `AwsSdk2Transport` with service `es`, `Region`, default credential chain, `aws-crt-client`; declare the AWS SDK artifacts as optional dependencies (F3) |
| Amazon OpenSearch Serverless (AOSS) | No | Transport (SigV4 `aoss`, `x-amz-content-sha256`); `_delete_by_query` used by FullReindexStep and ReconcileIndexStep; `_refresh` used by ReconcileIndexStep; custom `_id` only on Search collections; refresh lag ~10 s; client must be 3.x | Declare unsupported for 1.0. Supporting it later means: SigV4 transport (F3), replace delete-by-query with search + bulk delete, replace refresh with a bounded wait, and require Search collections (F4) |
| Legacy Elasticsearch 7.10 domains or other ES-compatible endpoints | Untested, not documented | Client matrix lists only OpenSearch; renamed APIs across the 7.10 to 2.x gap | Declare out of scope |

## 3. Findings

Severity: High = breaks a target platform or returns wrong results; Medium = operational risk or avoidable failure; Low = hygiene.

| ID | Severity | Finding | Evidence | Recommended change | 1.0 blocker |
| --- | --- | --- | --- | --- | --- |
| F1 | High | No TLS trust configuration. `useSsl` only flips the scheme; there is no truststore path, PEM CA, or insecure toggle, so self-managed clusters with demo or private-CA certificates fail the handshake. Hostname verification cannot be relaxed either | CLIENT lines 98 to 139; CONFIG lines 54, 420 to 444; official pattern in https://docs.opensearch.org/latest/clients/java/#connecting-to-opensearch | Add `trustStorePath`/`trustStorePassword` (or `caCertificatePem`) and an explicit `insecureSkipTlsVerify` flag; build an `SSLContext` and install it through `ClientTlsStrategyBuilder` + `PoolingAsyncClientConnectionManager` in the existing `setHttpClientConfigCallback`. Log a warning when verification is disabled | Yes |
| F2 | High | opensearch-java pinned to 2.10.0 (2.x line, documented for OpenSearch 1.x to 2.x). AWS already offers 3.1 to 3.5 and 3.x is the current community line. Empirical check: bumping to 3.10.0 yields a single compile error at CLIENT line 427 (`refresh(true)` must become `refresh(Refresh.True)`); 233 unit tests pass afterwards. 3.x also drops the transitive `opensearch-rest-client` and matches the BOM's httpclient5 5.6 / httpcore5 5.4 | pom.xml line 24; COMPATIBILITY.md; CHANGELOG/UPGRADING links in section 1.1; scratch compile log | Bump `opensearch.version` to 3.10.0, fix line 427, re-run ITs; keep the `disableContentCompression()` workaround until proven unnecessary | Yes |
| F3 | High (for AWS IAM) | Only HTTP basic auth is supported. No SigV4 (`AwsSdk2Transport`), no region or service name, no bearer-token or client-certificate hooks. An IAM master user or any IAM principal in the domain access policy makes the domain unreachable | CLIENT lines 109 to 137; CONFIG lines 52, 53; fgac.html; clients/java docs | Introduce an `authMode` (BASIC, AWS_SIGV4, NONE) plus `awsRegion`, `awsServiceName` (default `es`), optional credentials provider; build `AwsSdk2Transport` with `AwsCrtHttpClient`. Declare `software.amazon.awssdk:aws-crt-client` and `auth` as `<optional>true</optional>` rather than relying on the quicksight transitive. Optionally expose a `Consumer<HttpAsyncClientBuilder>` hook for JWT/API-token headers and mTLS | No (declare unsupported in README) |
| F4 | High (for AOSS) | AOSS cannot be targeted: SigV4 `aoss` missing; `_delete_by_query` (CLIENT 392, 424) and `_refresh` (CLIENT 579) are not in the AOSS supported-operations table; custom `_id` requires a Search collection; `refresh=true` parameter unsupported | serverless-genref.html; serverless-overview.html; CLIENT lines 388 to 445, 575 to 585 | Declare AOSS unsupported for 1.0. For a later release: F3, implement table wipe and stale-document removal as search (PIT or `search_after`) + bulk delete, replace `refreshIndex` with a polling wait, document the Search-collection requirement and the `aoss:CreateIndex/DescribeIndex/WriteDocument/ReadDocument/UpdateIndex` data-access policy | No |
| F5 | High | Edge n-gram analyzer (min 2, max 20) is set as the field `analyzer` with no `search_analyzer`, so the query string is also n-grammed. A search for "search" becomes se, sea, sear, searc, search and matches every document containing any token starting with "se", inflating hits and flattening relevance. Tokens longer than 20 chars only index their first 20 grams, and single-character terms never match | CLIENT lines 173 to 192 (`searchableText` property), 472 to 475 (must clause on `searchableText`); search-analyzers doc | Add a second analyzer (`standard` tokenizer + `lowercase`) and set it as `search_analyzer` on `searchableText`; consider `truncate` to 20 in the search analyzer or `preserve_original` so long terms still match; keep `min_gram 2` but document it. Requires index recreation (bump an index-version suffix or document a full reindex) | Yes |
| F6 | High | `fieldValues` is a dynamic `object` that receives raw `QRecord` values (`Integer`, `BigDecimal`, `Instant`, `LocalDate`, `Boolean`, `String`). Dynamic mapping turns them into `long`/`float`/`date`/`boolean`; the first table to index a field name fixes its type for the whole index, so a `status` that is a string in one table and an integer in another causes `mapper_parsing_exception` on the second table. The boost `multi_match` clauses target `fieldValues.<field>` without `lenient: true`, so a non-numeric or non-date search term against a numeric or date `fieldValues` field makes the whole search request fail. Field count also grows with every configured field across all tables toward `index.mapping.total_fields.limit` (1000) | IndexingUtils lines 144 to 156; CLIENT lines 192, 525 to 565; mappings docs (date detection on by default; dynamic rules); mapping-explosion doc | Map `fieldValues` with a `dynamic_templates` rule that maps every `fieldValues.*` string to `text` (edge n-gram analyzer + standard search analyzer) and set `date_detection: false`; convert values to strings in `IndexingUtils` (`getValueString`) so types can never conflict; set `lenient(true)` on the boost `multi_match` queries; consider `flat_object` if per-field boosting is not needed | Yes |
| F7 | Medium | `limit` and `offset` are unbounded; `from + size > 10000` returns a 400 (`index.max_result_window`) and large `size` values are accepted verbatim | QuickSearchAction lines 46 to 79; CLIENT 497 to 502; index-settings doc | Cap `limit` (for example 100) and reject or clamp `offset + limit > 10000` with a clear error; document that deep paging is not supported | No |
| F8 | Medium | Bulk batching is count-based only (`bulkBatchSize` default 500) with no byte cap. On AWS small instance types the HTTP payload limit is 10 MiB; wide records with large text fields can exceed it and the whole batch fails with 413 | CLIENT lines 241 to 274; CONFIG line 58; AWS limits.html | Flush a batch when its serialized size approaches a configurable byte limit (default 5 MiB) in addition to the count; surface 413 clearly | No |
| F9 | Medium | No connect, response or connection-request timeouts and no pool sizing; a hung node blocks the caller indefinitely; single host only, no failover list | CLIENT lines 105 to 139; no `RequestConfig`, `setConnectionManager`, or timeout call anywhere in `src/main/java` | Add `connectTimeoutMs`, `responseTimeoutMs`, `maxConnections` to CONFIG and apply via `setDefaultRequestConfig` and `PoolingAsyncClientConnectionManagerBuilder`; accept a list of hosts (`ApacheHttpClient5TransportBuilder.builder(HttpHost...)`) | Yes |
| F10 | Medium | Integration tests pin `opensearchproject/opensearch:2.11.0` (Oct 2023). Nothing exercises 2.19.x or any 3.x, although AWS offers 2.19 and 3.1 to 3.5 and the community line is 3.9 | OpenSearchIntegrationTest line 76; IndexDriftIntegrationTest line 81; ConfigDrivenIndexingIntegrationTest line 76; releases page; Docker Hub tags | Parameterize the image via a system property (`-Dopensearch.image=...`) and run two CI jobs: `2.19.6` and `3.9.0`. Optionally adopt `org.opensearch:opensearch-testcontainers` 3.0.2 (Testcontainers 1.20+, JDK 21) now and 4.1.0 after moving to Testcontainers 2.x | Yes |
| F11 | Low | ITs disable security via `plugins.security.disabled=true` while also passing `OPENSEARCH_INITIAL_ADMIN_PASSWORD=Admin123!`. Harmless today, but if security is ever enabled that password is unlikely to pass the zxcvbn "strong" check required since 2.12, and the documented image switch is `DISABLE_SECURITY_PLUGIN=true` | the three IT classes lines 79 to 85; demo-configuration doc | Use `DISABLE_SECURITY_PLUGIN=true` (and `DISABLE_INSTALL_DEMO_CONFIG=true`); drop the password, or when adding a secured IT, generate a strong one and connect over HTTPS with F1 in place | No |
| F12 | Low | `opensearch-rest-client:2.12.0` and `httpcore5` 5.2.x come in transitively from the 2.10.0 client even though `RestClientTransport` is never used; version skew with the BOM's httpclient5 5.6.3 already required the compression workaround | dependency tree; CLIENT lines 124 to 130 | Resolved by F2; otherwise exclude `opensearch-rest-client` | No |
| F13 | Low | No documented least-privilege role for the security plugin or AWS FGAC | none in repo | README: index pattern `<opensearchIndexName>`, groups `create_index`, `read`, `write`, `delete`, plus `indices:admin/refresh*` and an `indices:admin/*` read for the exists check; cluster `cluster_composite_ops`. For AWS FGAC map the same to the basic-auth user; for AOSS list the five `aoss:*` permissions | No |
| F14 | Low | Composite `_id` is `sourceTable + ":" + recordId`; `recordId` is mapped `keyword` (correct for term filters and mixed PK types). A table name containing ":" would be ambiguous, and `_id` is limited to 512 bytes | OpenSearchDocument lines 184 to 187; CLIENT line 188 | Validate table names against ":" in CONFIG; keep `keyword` | No |
| F15 | Info | Against 3.x servers the Apache 5 transport negotiates HTTP/2 automatically; nothing in the code forces a version | USER_GUIDE.md HTTP/2 note | No change; mention in README; `HttpVersionPolicy.FORCE_HTTP_1` is available if a proxy misbehaves | No |

## 4. Recommended "supported platforms" statement for the 1.0 README

Supported (tested in CI):

* Self-managed OpenSearch 2.19.x and 3.x (latest minor), reached over HTTP or HTTPS with a certificate the JVM trusts or a configured truststore, with the security plugin disabled or with HTTP basic authentication against the internal user database. Required role: `create_index`, `read`, `write`, `delete` on the quick-search index plus `indices:admin/refresh*`, and the cluster-level `cluster_composite_ops` group.
* Amazon OpenSearch Service managed domains running OpenSearch 2.x or 3.x with fine-grained access control, an internal-database master user (or a mapped internal user), HTTP basic authentication, `opensearchPort = 443`, `useSsl = true`, and a domain access policy that does not require signed requests. Keep `bulkBatchSize` small enough for the instance type's HTTP payload limit (10 MiB on t3 and `*.large`).

Not supported in 1.0:

* IAM / SigV4 authentication to Amazon OpenSearch Service (IAM master user or IAM principals in the access policy).
* Amazon OpenSearch Serverless (requires SigV4 with service `aoss` and does not offer `_delete_by_query` or `_refresh`, which the full-reindex and reconcile processes use).
* Legacy Elasticsearch 7.10 domains and other Elasticsearch-compatible endpoints.
* OpenSearch 1.x.

Pre-conditions for the statement above: F1, F2, F5, F6, F9 and F10 must land first; the statement should name the exact image tags the CI matrix runs.

## 5. Source list

* https://github.com/opensearch-project/opensearch-java/blob/main/COMPATIBILITY.md
* https://docs.opensearch.org/latest/clients/java/
* https://github.com/opensearch-project/opensearch-java/blob/main/USER_GUIDE.md
* https://github.com/opensearch-project/opensearch-java/blob/main/guides/auth.md
* https://raw.githubusercontent.com/opensearch-project/opensearch-java/main/CHANGELOG.md
* https://raw.githubusercontent.com/opensearch-project/opensearch-java/main/UPGRADING.md
* https://github.com/opensearch-project/opensearch-java/releases/tag/v3.0.0
* https://repo1.maven.org/maven2/org/opensearch/client/opensearch-java/maven-metadata.xml
* https://repo1.maven.org/maven2/org/opensearch/client/opensearch-java/2.10.0/opensearch-java-2.10.0.pom
* https://repo1.maven.org/maven2/org/opensearch/client/opensearch-java/3.9.0/opensearch-java-3.9.0.pom
* https://docs.aws.amazon.com/opensearch-service/latest/developerguide/what-is.html
* https://docs.aws.amazon.com/opensearch-service/latest/developerguide/fgac.html
* https://docs.aws.amazon.com/opensearch-service/latest/developerguide/vpc.html
* https://docs.aws.amazon.com/opensearch-service/latest/developerguide/infrastructure-security.html
* https://docs.aws.amazon.com/opensearch-service/latest/developerguide/supported-operations.html
* https://docs.aws.amazon.com/opensearch-service/latest/developerguide/limits.html
* https://docs.aws.amazon.com/opensearch-service/latest/developerguide/serverless-genref.html
* https://docs.aws.amazon.com/opensearch-service/latest/developerguide/serverless-overview.html
* https://docs.aws.amazon.com/opensearch-service/latest/developerguide/serverless-comparison.html
* https://docs.aws.amazon.com/opensearch-service/latest/developerguide/serverless-clients.html
* https://docs.aws.amazon.com/general/latest/gr/opensearch-service.html
* https://docs.opensearch.org/latest/install-and-configure/install-opensearch/docker/
* https://docs.opensearch.org/latest/security/configuration/demo-configuration/
* https://docs.opensearch.org/latest/security/access-control/default-action-groups/
* https://docs.opensearch.org/latest/security/authentication-backends/client-auth/
* https://docs.opensearch.org/latest/security/authentication-backends/jwt/
* https://github.com/opensearch-project/opensearch-build/blob/main/release-notes/opensearch-release-notes-3.7.0.md
* https://opensearch.org/releases/
* https://hub.docker.com/v2/repositories/opensearchproject/opensearch/tags
* https://github.com/opensearch-project/opensearch-testcontainers/blob/main/README.md
* https://docs.opensearch.org/latest/install-and-configure/configuring-opensearch/index-settings/
* https://docs.opensearch.org/latest/analyzers/search-analyzers/
* https://docs.opensearch.org/latest/analyzers/token-filters/edge-ngram/
* https://docs.opensearch.org/latest/mappings/
* https://docs.opensearch.org/latest/mappings/mapping-explosion/
* https://forum.opensearch.org/t/opensearch-api-compatibility-with-elastic-search/17647

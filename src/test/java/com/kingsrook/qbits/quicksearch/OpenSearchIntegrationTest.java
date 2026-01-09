/*
 * Copyright 2024 Kingsrook, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kingsrook.qbits.quicksearch;


import java.time.Duration;
import java.time.Instant;
import java.util.List;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchAction;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchInput;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchOutput;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Integration tests for Quick Search using OpenSearch testcontainers.
 **
 ** These tests require Docker to be running. They run by default and will
 ** be automatically skipped if Docker is unavailable. Set environment variable
 ** SKIP_INTEGRATION_TESTS=true to manually skip these tests.
 **
 ** This test class is named with *IntegrationTest suffix so it can be excluded
 ** from unit test runs if needed via surefire configuration.
 *******************************************************************************/
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OpenSearchIntegrationTest
{
   private static final int OPENSEARCH_PORT = 9200;

   @Container
   static GenericContainer<?> opensearchContainer = new GenericContainer<>("opensearchproject/opensearch:2.11.0")
      .withExposedPorts(OPENSEARCH_PORT)
      .withEnv("discovery.type", "single-node")
      .withEnv("plugins.security.disabled", "true")
      .withEnv("OPENSEARCH_INITIAL_ADMIN_PASSWORD", "Admin123!")
      .waitingFor(Wait.forHttp("/").forPort(OPENSEARCH_PORT).forStatusCode(200))
      .withStartupTimeout(Duration.ofMinutes(2));

   private static QuickSearchQBitConfig        config;
   private static QuickSearchOpenSearchClient  client;



   /***************************************************************************
    ** Set up OpenSearch client after container starts.
    ***************************************************************************/
   @BeforeAll
   static void setUp() throws Exception
   {
      config = new QuickSearchQBitConfig()
         .withOpensearchHost(opensearchContainer.getHost())
         .withOpensearchPort(opensearchContainer.getMappedPort(OPENSEARCH_PORT))
         .withOpensearchIndexName("quick-search-test")
         .withUseSsl(false)
         .withBackendName("testBackend");

      client = new QuickSearchOpenSearchClient(config);

      QuickSearchQBitContext.setConfig(config);
   }



   /***************************************************************************
    ** Clean up after tests.
    ***************************************************************************/
   @AfterAll
   static void tearDown()
   {
      if(client != null)
      {
         client.close();
      }
   }



   /***************************************************************************
    ** Test that we can create the index.
    ***************************************************************************/
   @Test
   @Order(1)
   void testEnsureIndexExists() throws Exception
   {
      client.ensureIndexExists();
   }



   /***************************************************************************
    ** Test indexing a single document.
    ***************************************************************************/
   @Test
   @Order(2)
   void testIndexSingleDocument() throws Exception
   {
      OpenSearchDocument doc = new OpenSearchDocument()
         .withSourceTable("order")
         .withRecordId("12345")
         .withRecordLabel("Order #12345")
         .withSearchableText("John Doe 123 Main Street shipped express delivery")
         .withIndexedAt(Instant.now());

      client.indexDocument(doc);

      // Give OpenSearch time to index
      Thread.sleep(1000);
   }



   /***************************************************************************
    ** Test bulk indexing multiple documents.
    ***************************************************************************/
   @Test
   @Order(3)
   void testBulkIndexDocuments() throws Exception
   {
      List<OpenSearchDocument> docs = List.of(
         new OpenSearchDocument()
            .withSourceTable("customer")
            .withRecordId("100")
            .withRecordLabel("Customer: Alice Smith")
            .withSearchableText("Alice Smith alice@example.com 555-1234")
            .withIndexedAt(Instant.now()),
         new OpenSearchDocument()
            .withSourceTable("customer")
            .withRecordId("101")
            .withRecordLabel("Customer: Bob Johnson")
            .withSearchableText("Bob Johnson bob@company.org 555-5678")
            .withIndexedAt(Instant.now()),
         new OpenSearchDocument()
            .withSourceTable("product")
            .withRecordId("SKU001")
            .withRecordLabel("Widget Pro")
            .withSearchableText("Widget Pro premium quality electronics gadget")
            .withIndexedAt(Instant.now())
      );

      client.indexDocuments(docs);

      // Give OpenSearch time to index
      Thread.sleep(1000);
   }



   /***************************************************************************
    ** Test searching for documents.
    ***************************************************************************/
   @Test
   @Order(4)
   void testSearchDocuments() throws Exception
   {
      List<OpenSearchDocument> results = client.search("John", 10);

      assertThat(results).isNotEmpty();
      assertThat(results.get(0).getSourceTable()).isEqualTo("order");
      assertThat(results.get(0).getRecordId()).isEqualTo("12345");
   }



   /***************************************************************************
    ** Test searching with table filter.
    ***************************************************************************/
   @Test
   @Order(5)
   void testSearchWithTableFilter() throws Exception
   {
      List<OpenSearchDocument> results = client.search("alice", "customer", 10);

      assertThat(results).hasSize(1);
      assertThat(results.get(0).getRecordId()).isEqualTo("100");
   }



   /***************************************************************************
    ** Test that search finds nothing for non-matching query.
    ***************************************************************************/
   @Test
   @Order(6)
   void testSearchNoResults() throws Exception
   {
      List<OpenSearchDocument> results = client.search("xyznonexistent123", 10);

      assertThat(results).isEmpty();
   }



   /***************************************************************************
    ** Test QuickSearchAction end-to-end.
    ***************************************************************************/
   @Test
   @Order(7)
   void testQuickSearchAction() throws Exception
   {
      QuickSearchAction action = new QuickSearchAction();

      QuickSearchInput input = new QuickSearchInput()
         .withSearchTerm("Bob")
         .withLimit(10);

      QuickSearchOutput output = action.execute(input);

      assertThat(output.getResults()).isNotEmpty();
      assertThat(output.getResults().get(0).getTableName()).isEqualTo("customer");
      assertThat(output.getResults().get(0).getRecordId()).isEqualTo("101");
   }



   /***************************************************************************
    ** Test deleting a document.
    ***************************************************************************/
   @Test
   @Order(8)
   void testDeleteDocument() throws Exception
   {
      client.deleteDocument("customer", "100");

      // Give OpenSearch time to process delete
      Thread.sleep(1000);

      List<OpenSearchDocument> results = client.search("Alice", "customer", 10);
      assertThat(results).isEmpty();
   }



   /***************************************************************************
    ** Test deleting all documents for a table.
    ***************************************************************************/
   @Test
   @Order(9)
   void testDeleteDocumentsForTable() throws Exception
   {
      client.deleteDocumentsForTable("product");

      // Give OpenSearch time to process delete
      Thread.sleep(1000);

      List<OpenSearchDocument> results = client.search("widget", "product", 10);
      assertThat(results).isEmpty();
   }

}

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


import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryRecordStore;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchAction;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchInput;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchOutput;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchResult;
import com.kingsrook.qbits.quicksearch.annotations.QuickSearchField;
import com.kingsrook.qbits.quicksearch.annotations.QuickSearchable;
import com.kingsrook.qbits.quicksearch.opensearch.BulkIndexResult;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import com.kingsrook.qbits.quicksearch.processes.FullReindexStep;
import com.kingsrook.qbits.quicksearch.processes.ReconcileIndexStep;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** End-to-end integration test for the Quick Search QBit using a real
 ** OpenSearch instance managed by Testcontainers.
 **
 ** Tests run in order: index creation, full reindex, search with results,
 ** pagination, no-results search, then analyzer precision, mixed field
 ** types, external versioning, alias swap, orphan sweep and table labels.
 *******************************************************************************/
@ExtendWith(RequiresDockerCondition.class)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OpenSearchIntegrationTest
{

   private static final String BACKEND_NAME = "testMemoryBackend";
   private static final String TABLE_NAME   = "testProduct";
   private static final String INDEX_NAME   = "quick_search_integration_test";
   private static final String EVENT_TABLE  = "testEvent";
   private static final String TABLE_LABEL  = "Test Product";

   @Container
   static GenericContainer<?> opensearch = new GenericContainer<>(System.getProperty("opensearch.test.image", "opensearchproject/opensearch:2.19.6"))
      .withExposedPorts(9200)
      .withEnv("discovery.type", "single-node")
      .withEnv("DISABLE_SECURITY_PLUGIN", "true")
      .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
      .waitingFor(Wait.forHttp("/_cluster/health").forStatusCode(200));



   /*******************************************************************************
    ** Test entity class annotated for quick search indexing.
    *******************************************************************************/
   @QuickSearchable(tableName = TABLE_NAME)
   static class TestProduct
   {
      @QuickSearchField(weight = 3)
      private String name;

      @QuickSearchField
      private String description;

      @QuickSearchField(weight = 2, includeLabel = true)
      private String sku;
   }



   /*******************************************************************************
    ** Initialize the QInstance, QBit, and QContext before all tests run.
    *******************************************************************************/
   @BeforeAll
   static void setUpAll() throws Exception
   {
      MemoryRecordStore.getInstance().reset();

      ////////////////////////////////////////////////////
      // Build QInstance with memory backend            //
      ////////////////////////////////////////////////////
      QInstance qInstance = new QInstance();

      qInstance.addBackend(new QBackendMetaData()
         .withName(BACKEND_NAME)
         .withBackendType(MemoryBackendModule.class));

      qInstance.withInstanceDefaultAuthentication(new QAuthenticationMetaData()
         .withName("anonymous")
         .withType(QAuthenticationType.FULLY_ANONYMOUS));

      ////////////////////////////////////////////////////
      // Add testProduct source table                   //
      ////////////////////////////////////////////////////
      qInstance.addTable(new QTableMetaData()
         .withName(TABLE_NAME)
         .withLabel(TABLE_LABEL)
         .withBackendName(BACKEND_NAME)
         .withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("name", QFieldType.STRING))
         .withField(new QFieldMetaData("description", QFieldType.STRING))
         .withField(new QFieldMetaData("sku", QFieldType.STRING))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME)));

      ////////////////////////////////////////////////////////////////////
      // a config-driven table with non-text searchable fields                //
      //////////////////////////////////////////////////////////////////////
      qInstance.addTable(new QTableMetaData()
         .withName(EVENT_TABLE)
         .withBackendName(BACKEND_NAME)
         .withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("title", QFieldType.STRING))
         .withField(new QFieldMetaData("quantity", QFieldType.INTEGER))
         .withField(new QFieldMetaData("occurredAt", QFieldType.DATE_TIME))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME)));

      ////////////////////////////////////////////////////
      // Configure and produce the QBit                //
      ////////////////////////////////////////////////////
      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName(BACKEND_NAME)
         .withOpensearchHost(opensearch.getHost())
         .withOpensearchPort(opensearch.getMappedPort(9200))
         .withOpensearchIndexName(INDEX_NAME)
         .withSearchableEntityClasses(List.of(TestProduct.class))
         .withSearchableTable(new SearchableTableConfig(EVENT_TABLE, List.of(
            new SearchableFieldConfig("title").withWeight(2),
            new SearchableFieldConfig("quantity"),
            new SearchableFieldConfig("occurredAt"))))
         .withEnableRealTimeIndexing(false);

      new QuickSearchQBitProducer().withConfig(config).produce(qInstance);

      QContext.init(qInstance, new QSession());
   }



   /*******************************************************************************
    ** Clean up QContext and QBit state after all tests complete.
    *******************************************************************************/
   @AfterAll
   static void tearDownAll()
   {
      QContext.clear();
      QuickSearchQBitContext.clear();
      MemoryRecordStore.getInstance().reset();
   }



   /*******************************************************************************
    ** Test 1: Verify the OpenSearch client was created and the alias points at
    ** a physical index built with the current mapping.
    *******************************************************************************/
   @Test
   @Order(1)
   void testOpenSearchIndexCreated() throws Exception
   {
      Object rawClient = QuickSearchQBitContext.getClient();
      assertThat(rawClient).isNotNull();
      assertThat(rawClient).isInstanceOf(QuickSearchOpenSearchClient.class);

      QuickSearchQBitConfig storedConfig = QuickSearchQBitContext.getConfig();
      assertThat(storedConfig).isNotNull();
      assertThat(storedConfig.getOpensearchIndexName()).isEqualTo(INDEX_NAME);

      Set<String> physicalIndexes = client().getPhysicalIndexes();
      assertThat(physicalIndexes).as("the configured name is an alias over one physical index").hasSize(1);
      assertThat(physicalIndexes.iterator().next()).startsWith(INDEX_NAME + "-v" + QuickSearchOpenSearchClient.MAPPING_VERSION + "-");
      assertThat(client().isMappingOutdated()).isFalse();
      assertThat(client().isHealthy()).isTrue();
   }



   /*******************************************************************************
    ** Test 2: Insert 5 test products, run FullReindexStep, and verify a search
    ** returns results.
    *******************************************************************************/
   @Test
   @Order(2)
   void testFullReindex() throws Exception
   {
      ////////////////////////////////////////////////////
      // Insert 5 test products into the memory backend //
      ////////////////////////////////////////////////////
      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(TABLE_NAME);
      insertInput.setRecords(List.of(
         new QRecord().withValue("id", 1).withValue("name", "Blue Widget").withValue("description", "A fine widget for all uses").withValue("sku", "BW-001").withValue("modifyDate", Instant.now()),
         new QRecord().withValue("id", 2).withValue("name", "Red Widget").withValue("description", "A premium widget in red").withValue("sku", "RW-002").withValue("modifyDate", Instant.now()),
         new QRecord().withValue("id", 3).withValue("name", "Green Gadget").withValue("description", "A handy gadget for the workshop").withValue("sku", "GG-003").withValue("modifyDate", Instant.now()),
         new QRecord().withValue("id", 4).withValue("name", "Yellow Gadget").withValue("description", "A bright yellow gadget").withValue("sku", "YG-004").withValue("modifyDate", Instant.now()),
         new QRecord().withValue("id", 5).withValue("name", "Purple Widget").withValue("description", "A rare purple widget").withValue("sku", "PW-005").withValue("modifyDate", Instant.now())
      ));
      new InsertAction().execute(insertInput);

      ////////////////////////////////////////////////////
      // Run the full reindex step                      //
      ////////////////////////////////////////////////////
      RunBackendStepInput  input  = new RunBackendStepInput();
      RunBackendStepOutput output = new RunBackendStepOutput();
      new FullReindexStep().run(input, output);

      ////////////////////////////////////////////////////
      // Force index refresh so documents are searchable//
      ////////////////////////////////////////////////////
      ((QuickSearchOpenSearchClient) QuickSearchQBitContext.getClient()).refreshIndex();

      ////////////////////////////////////////////////////
      // Verify at least one result for "widget"        //
      ////////////////////////////////////////////////////
      QuickSearchOutput searchOutput = new QuickSearchAction().execute(
         new QuickSearchInput().withSearchTerm("widget"));

      assertThat(searchOutput.getTotalHits())
         .as("search for 'widget' should return results after full reindex")
         .isGreaterThan(0L);
   }



   /*******************************************************************************
    ** Test 3: Search for "widget" and assert results have required fields.
    *******************************************************************************/
   @Test
   @Order(3)
   void testSearchReturnsResults() throws Exception
   {
      QuickSearchOutput output = new QuickSearchAction().execute(
         new QuickSearchInput().withSearchTerm("widget"));

      assertThat(output.getTotalHits())
         .as("search for 'widget' should return results")
         .isGreaterThan(0L);

      ////////////////////////////////////////////////////
      // Each result must have tableName, recordId,     //
      // and score populated                            //
      ////////////////////////////////////////////////////
      output.getResults().forEach(result ->
      {
         assertThat(result.getTableName()).isEqualTo(TABLE_NAME);
         assertThat(result.getRecordId()).isNotBlank();
         assertThat(result.getScore()).isNotNull().isGreaterThan(0f);
      });
   }



   /*******************************************************************************
    ** Test 4: Search with limit and offset to verify pagination.
    *******************************************************************************/
   @Test
   @Order(4)
   void testSearchPagination() throws Exception
   {
      ////////////////////////////////////////////////////
      // Fetch first page (limit=2, offset=0)           //
      ////////////////////////////////////////////////////
      QuickSearchOutput page1 = new QuickSearchAction().execute(
         new QuickSearchInput().withSearchTerm("widget").withLimit(2).withOffset(0));

      assertThat(page1.getResults()).hasSizeLessThanOrEqualTo(2);

      ////////////////////////////////////////////////////
      // If total hits > 2, hasMore should be true and  //
      // page 2 should return different record IDs      //
      ////////////////////////////////////////////////////
      if(page1.getTotalHits() > 2)
      {
         assertThat(page1.getHasMore()).isTrue();

         QuickSearchOutput page2 = new QuickSearchAction().execute(
            new QuickSearchInput().withSearchTerm("widget").withLimit(2).withOffset(2));

         assertThat(page2.getResults()).isNotEmpty();

         List<String> page1Ids = page1.getResults().stream().map(r -> r.getRecordId()).toList();
         List<String> page2Ids = page2.getResults().stream().map(r -> r.getRecordId()).toList();

         assertThat(page1Ids).doesNotContainAnyElementsOf(page2Ids);

         ////////////////////////////////////////////////////
         // per-table mode honors offset the same way      //
         ////////////////////////////////////////////////////
         QuickSearchOutput perTable1 = new QuickSearchAction().execute(
            new QuickSearchInput().withSearchTerm("widget").withLimitPerTable(2).withOffset(0));
         QuickSearchOutput perTable2 = new QuickSearchAction().execute(
            new QuickSearchInput().withSearchTerm("widget").withLimitPerTable(2).withOffset(2));

         List<String> perTable1Ids = perTable1.getResults().stream().map(r -> r.getTableName() + ":" + r.getRecordId()).toList();
         List<String> perTable2Ids = perTable2.getResults().stream().map(r -> r.getTableName() + ":" + r.getRecordId()).toList();

         assertThat(perTable2Ids).isNotEmpty();
         assertThat(perTable1Ids).doesNotContainAnyElementsOf(perTable2Ids);
      }
   }



   /*******************************************************************************
    ** Test 5: Search for a term that should not match any records.
    *******************************************************************************/
   @Test
   @Order(5)
   void testSearchNoResults() throws Exception
   {
      QuickSearchOutput output = new QuickSearchAction().execute(
         new QuickSearchInput().withSearchTerm("zzzznonexistent"));

      assertThat(output.getTotalHits()).isEqualTo(0L);
      assertThat(output.getResults()).isEmpty();
      assertThat(output.getHasMore()).isFalse();
   }



   /*******************************************************************************
    ** Test 6: a term only matches documents containing a token that starts
    ** with it; "stella" must not match "stone" through the shared "st" prefix.
    *******************************************************************************/
   @Test
   @Order(6)
   void testSearch_doesNotMatchOnSharedPrefixOnly() throws Exception
   {
      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(TABLE_NAME);
      insertInput.setRecords(List.of(
         new QRecord().withValue("id", 6).withValue("name", "Stella").withValue("description", "Named Stella").withValue("sku", "ST-006"),
         new QRecord().withValue("id", 7).withValue("name", "Stone").withValue("description", "Named Stone").withValue("sku", "ST-007")));
      new InsertAction().execute(insertInput);

      reindexTable(TABLE_NAME);

      QuickSearchOutput output = new QuickSearchAction().execute(new QuickSearchInput().withSearchTerm("stella"));

      assertThat(output.getResults()).extracting(QuickSearchResult::getRecordId).containsExactly("6");
      assertThat(output.getTotalHits()).isEqualTo(1L);
   }



   /*******************************************************************************
    ** Test 7: a table with INTEGER and DATE_TIME searchable fields indexes and
    ** searches without a mapping error, and the number is searchable as text.
    *******************************************************************************/
   @Test
   @Order(7)
   void testSearch_tableWithIntegerAndDateTimeSearchableFields() throws Exception
   {
      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(EVENT_TABLE);
      insertInput.setRecords(List.of(
         new QRecord().withValue("id", 1).withValue("title", "Quarterly conference").withValue("quantity", 4242).withValue("occurredAt", Instant.parse("2026-03-01T10:00:00Z")),
         new QRecord().withValue("id", 2).withValue("title", "Board meeting").withValue("quantity", 7).withValue("occurredAt", Instant.parse("2026-04-01T10:00:00Z"))));
      new InsertAction().execute(insertInput);

      reindexTable(EVENT_TABLE);

      QuickSearchOutput byWord = new QuickSearchAction().execute(new QuickSearchInput().withSearchTerm("conference").withTableName(EVENT_TABLE));
      assertThat(byWord.getResults()).extracting(QuickSearchResult::getRecordId).containsExactly("1");

      QuickSearchOutput byNumber = new QuickSearchAction().execute(new QuickSearchInput().withSearchTerm("4242").withTableName(EVENT_TABLE));
      assertThat(byNumber.getResults()).extracting(QuickSearchResult::getRecordId).containsExactly("1");

      QuickSearchOutput byDate = new QuickSearchAction().execute(new QuickSearchInput().withSearchTerm("2026-04").withTableName(EVENT_TABLE));
      assertThat(byDate.getResults()).extracting(QuickSearchResult::getRecordId).containsExactly("2");
   }



   /*******************************************************************************
    ** Test 8: a document written with an older version than the one in the
    ** index is skipped (not a failure) and the newer document stays.
    *******************************************************************************/
   @Test
   @Order(8)
   void testIndexDocuments_olderVersionSkipped_newerStays() throws Exception
   {
      OpenSearchDocument newer = new OpenSearchDocument().withSourceTable(TABLE_NAME).withRecordId("900")
         .withSearchableText("Versioned newer").withIndexedAt(Instant.now()).withFieldValues(Map.of()).withVersion(2000L);
      OpenSearchDocument older = new OpenSearchDocument().withSourceTable(TABLE_NAME).withRecordId("900")
         .withSearchableText("Versioned older").withIndexedAt(Instant.now()).withFieldValues(Map.of()).withVersion(1000L);

      BulkIndexResult first = client().indexDocuments(List.of(newer), 10);
      assertThat(first.getSuccessCount()).isEqualTo(1);

      BulkIndexResult second = client().indexDocuments(List.of(older), 10);
      assertThat(second.getSkippedCount()).isEqualTo(1);
      assertThat(second.getSuccessCount()).isEqualTo(0);
      assertThat(second.getFailureCount()).isEqualTo(0);
      assertThat(second.isFullySuccessful()).isTrue();

      client().refreshIndex();
      SearchResponse<OpenSearchDocument> response = client().search("versioned", List.of(TABLE_NAME), 10, 0, QuickSearchQBitContext.getDiscoveredTables());
      assertThat(response.hits().hits()).hasSize(1);
      assertThat(response.hits().hits().get(0).source().getSearchableText()).isEqualTo("Versioned newer");

      client().deleteDocument(TABLE_NAME, "900");
   }



   /*******************************************************************************
    ** Test 9: a full reindex of all tables builds a new physical index, swaps
    ** the alias to it and removes the old physical index; search keeps working.
    *******************************************************************************/
   @Test
   @Order(9)
   void testFullReindexAllTables_swapsAliasAndRemovesOldPhysicalIndex() throws Exception
   {
      Set<String> before = client().getPhysicalIndexes();
      assertThat(before).hasSize(1);
      String oldPhysicalIndex = before.iterator().next();

      RunBackendStepOutput output = new RunBackendStepOutput();
      new FullReindexStep().run(new RunBackendStepInput(), output);

      assertThat(output.getValueBoolean("aliasSwapped")).isTrue();

      Set<String> after = client().getPhysicalIndexes();
      assertThat(after).hasSize(1);
      assertThat(after).doesNotContain(oldPhysicalIndex);
      assertThat(physicalIndexExists(oldPhysicalIndex)).as("old physical index is deleted after the swap").isFalse();
      assertThat(physicalIndexExists(after.iterator().next())).isTrue();

      QuickSearchOutput searchOutput = new QuickSearchAction().execute(new QuickSearchInput().withSearchTerm("widget"));
      assertThat(searchOutput.getTotalHits()).isGreaterThan(0L);
   }



   /*******************************************************************************
    ** Test 10: reconcile with no table filter removes documents of a table that
    ** is no longer configured.
    *******************************************************************************/
   @Test
   @Order(10)
   void testReconcile_removesDocumentsOfUnconfiguredTable() throws Exception
   {
      client().indexDocuments(List.of(new OpenSearchDocument().withSourceTable("ghostTable").withRecordId("1")
         .withSearchableText("Ghostly leftover").withIndexedAt(Instant.now()).withFieldValues(Map.of())), 10);
      client().refreshIndex();
      assertThat(client().countDocumentsForTable("ghostTable")).isEqualTo(1L);

      RunBackendStepOutput output = new RunBackendStepOutput();
      new ReconcileIndexStep().run(new RunBackendStepInput(), output);

      client().refreshIndex();
      assertThat(output.getValue("orphanDocumentsRemoved")).isEqualTo(1L);
      assertThat(client().countDocumentsForTable("ghostTable")).isEqualTo(0L);
      assertThat(client().countDocumentsForTable(TABLE_NAME)).as("configured tables keep their documents").isGreaterThan(0L);
   }



   /*******************************************************************************
    ** Test 11: search results carry the table's label.
    *******************************************************************************/
   @Test
   @Order(11)
   void testSearchResults_haveTableLabel() throws Exception
   {
      QuickSearchOutput output = new QuickSearchAction().execute(new QuickSearchInput().withSearchTerm("widget"));

      assertThat(output.getResults()).isNotEmpty();
      assertThat(output.getResults()).extracting(QuickSearchResult::getTableLabel).containsOnly(TABLE_LABEL);
      assertThat(output.getResults()).extracting(QuickSearchResult::getRecordLabel).allMatch(label -> label != null && !label.isBlank());
   }



   /*******************************************************************************
    ** Rebuild one table in place and refresh so its documents are searchable.
    *******************************************************************************/
   private static void reindexTable(String tableName) throws Exception
   {
      RunBackendStepInput input = new RunBackendStepInput();
      input.addValue("tableName", tableName);
      new FullReindexStep().run(input, new RunBackendStepOutput());
      client().refreshIndex();
   }



   /*******************************************************************************
    ** Whether a physical index exists, asked of OpenSearch over HTTP.
    *******************************************************************************/
   private static boolean physicalIndexExists(String physicalIndex) throws Exception
   {
      HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + opensearch.getHost() + ":" + opensearch.getMappedPort(9200) + "/" + physicalIndex))
         .method("HEAD", HttpRequest.BodyPublishers.noBody())
         .build();
      HttpResponse<Void> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
      return (response.statusCode() == 200);
   }



   /*******************************************************************************
    ** The live OpenSearch client from the QBit context.
    *******************************************************************************/
   private static QuickSearchOpenSearchClient client()
   {
      return (QuickSearchQBitContext.getClient());
   }

}

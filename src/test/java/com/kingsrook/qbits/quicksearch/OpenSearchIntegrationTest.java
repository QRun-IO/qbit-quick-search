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


import java.time.Instant;
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
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
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchAction;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchInput;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchOutput;
import com.kingsrook.qbits.quicksearch.annotations.QuickSearchField;
import com.kingsrook.qbits.quicksearch.annotations.QuickSearchable;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import com.kingsrook.qbits.quicksearch.processes.FullReindexStep;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** End-to-end integration test for the Quick Search QBit using a real
 ** OpenSearch instance managed by Testcontainers.
 **
 ** Tests run in order: index creation, full reindex, search with results,
 ** pagination, and no-results search.
 *******************************************************************************/
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OpenSearchIntegrationTest
{

   private static final String BACKEND_NAME = "testMemoryBackend";
   private static final String TABLE_NAME   = "testProduct";
   private static final String INDEX_NAME   = "quick_search_integration_test";

   @Container
   static GenericContainer<?> opensearch = new GenericContainer<>("opensearchproject/opensearch:2.11.0")
      .withExposedPorts(9200)
      .withEnv("discovery.type", "single-node")
      .withEnv("plugins.security.disabled", "true")
      .withEnv("OPENSEARCH_INITIAL_ADMIN_PASSWORD", "Admin123!")
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

      qInstance.setAuthentication(new QAuthenticationMetaData()
         .withName("anonymous")
         .withType(QAuthenticationType.FULLY_ANONYMOUS));

      ////////////////////////////////////////////////////
      // Add testProduct source table                   //
      ////////////////////////////////////////////////////
      qInstance.addTable(new QTableMetaData()
         .withName(TABLE_NAME)
         .withBackendName(BACKEND_NAME)
         .withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("name", QFieldType.STRING))
         .withField(new QFieldMetaData("description", QFieldType.STRING))
         .withField(new QFieldMetaData("sku", QFieldType.STRING))
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
    ** Test 1: Verify the OpenSearch client was created and the index exists.
    *******************************************************************************/
   @Test
   @Order(1)
   void testOpenSearchIndexCreated()
   {
      Object rawClient = QuickSearchQBitContext.getClient();
      assertThat(rawClient).isNotNull();
      assertThat(rawClient).isInstanceOf(QuickSearchOpenSearchClient.class);

      QuickSearchQBitConfig storedConfig = QuickSearchQBitContext.getConfig();
      assertThat(storedConfig).isNotNull();
      assertThat(storedConfig.getOpensearchIndexName()).isEqualTo(INDEX_NAME);
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
      // Diagnostic: verify records are in memory store //
      ////////////////////////////////////////////////////
      QueryInput verifyInput = new QueryInput();
      verifyInput.setTableName(TABLE_NAME);
      List<QRecord> sourceRecords = new QueryAction().execute(verifyInput).getRecords();
      System.out.println("[DIAG] Source records in memory: " + sourceRecords.size());
      assertThat(sourceRecords).hasSize(5);

      ////////////////////////////////////////////////////
      // Diagnostic: verify discovered tables in context//
      ////////////////////////////////////////////////////
      List<QuickSearchableTableConfig> tables = QuickSearchQBitContext.getDiscoveredTables();
      System.out.println("[DIAG] Discovered tables: " + (tables != null ? tables.size() : "null"));
      if(tables != null)
      {
         for(QuickSearchableTableConfig t : tables)
         {
            System.out.println("[DIAG]   Table: " + t.getTableName() + ", fields: " + t.getSearchableFields() + ", pk: " + t.getPrimaryKeyField());
         }
      }

      ////////////////////////////////////////////////////
      // Diagnostic: verify OpenSearch client in context //
      ////////////////////////////////////////////////////
      Object client = QuickSearchQBitContext.getClient();
      System.out.println("[DIAG] Client is null: " + (client == null));

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
      // Diagnostic: verify quickSearchIndex rows       //
      ////////////////////////////////////////////////////
      QueryInput indexQueryInput = new QueryInput();
      indexQueryInput.setTableName(QuickSearchIndex.TABLE_NAME);
      List<QRecord> indexRows = new QueryAction().execute(indexQueryInput).getRecords();
      System.out.println("[DIAG] QuickSearchIndex rows: " + indexRows.size());
      for(QRecord row : indexRows)
      {
         System.out.println("[DIAG]   Index row: tableName=" + row.getValueString("tableName") + ", enabled=" + row.getValue("enabled") + ", status=" + row.getValueString("status"));
      }

      ////////////////////////////////////////////////////
      // Diagnostic: verify quickSearchIndexRun rows    //
      ////////////////////////////////////////////////////
      QueryInput runQueryInput = new QueryInput();
      runQueryInput.setTableName(QuickSearchIndexRun.TABLE_NAME);
      List<QRecord> runRows = new QueryAction().execute(runQueryInput).getRecords();
      System.out.println("[DIAG] QuickSearchIndexRun rows: " + runRows.size());
      for(QRecord row : runRows)
      {
         System.out.println("[DIAG]   Run: type=" + row.getValueString("runType") + ", status=" + row.getValueString("status")
            + ", processed=" + row.getValue("recordsProcessed") + ", indexed=" + row.getValue("recordsIndexed")
            + ", errors=" + row.getValue("errorCount") + ", errorMsg=" + row.getValueString("errorMessage"));
      }

      ////////////////////////////////////////////////////
      // Verify at least one result for "widget"        //
      ////////////////////////////////////////////////////
      QuickSearchOutput searchOutput = new QuickSearchAction().execute(
         new QuickSearchInput().withSearchTerm("widget"));

      ////////////////////////////////////////////////////
      // Build diagnostic string for assertion message //
      ////////////////////////////////////////////////////
      StringBuilder diag = new StringBuilder();
      diag.append("sourceRecords=").append(sourceRecords.size());
      diag.append(", discoveredTables=").append(tables != null ? tables.size() : "null");
      diag.append(", clientNull=").append(client == null);
      diag.append(", indexRows=").append(indexRows.size());
      diag.append(", runRows=").append(runRows.size());
      for(QRecord row : runRows)
      {
         diag.append(", run{type=").append(row.getValueString("runType"))
            .append(",status=").append(row.getValueString("status"))
            .append(",processed=").append(row.getValue("recordsProcessed"))
            .append(",indexed=").append(row.getValue("recordsIndexed"))
            .append(",errors=").append(row.getValue("errorCount"))
            .append(",errMsg=").append(row.getValueString("errorMessage"))
            .append("}");
      }
      diag.append(", searchTotalHits=").append(searchOutput.getTotalHits());

      assertThat(searchOutput.getTotalHits())
         .as("Search should find results. Diagnostics: " + diag)
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
         .as("testSearchReturnsResults: depends on testFullReindex having indexed documents. totalHits=" + output.getTotalHits())
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

}

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
import com.kingsrook.qbits.quicksearch.actions.QuickSearchAction;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchInput;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchOutput;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchResult;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import com.kingsrook.qbits.quicksearch.processes.FullReindexStep;
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
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** End-to-end integration test for config-driven table indexing using a real
 ** OpenSearch instance managed by Testcontainers.
 **
 ** Uses only config-driven tables (no annotation-based entity classes). Inserts
 ** records, runs FullReindexStep, and verifies search results including custom
 ** recordLabelFormat.
 *******************************************************************************/
@ExtendWith(RequiresDockerCondition.class)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConfigDrivenIndexingIntegrationTest
{

   private static final String BACKEND_NAME = "testMemoryBackend";
   private static final String TABLE_NAME   = "configProduct";
   private static final String INDEX_NAME   = "config_driven_integration_test";

   @Container
   static GenericContainer<?> opensearch = new GenericContainer<>("opensearchproject/opensearch:2.11.0")
      .withExposedPorts(9200)
      .withEnv("discovery.type", "single-node")
      .withEnv("plugins.security.disabled", "true")
      .withEnv("OPENSEARCH_INITIAL_ADMIN_PASSWORD", "Admin123!")
      .waitingFor(Wait.forHttp("/_cluster/health").forStatusCode(200));



   /*******************************************************************************
    ** Initialize the QInstance using config-driven tables (no annotations),
    ** produce the QBit, and set up QContext before all tests.
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
      // Add configProduct source table                //
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
      // Configure via config-driven API (no annotations)//
      ////////////////////////////////////////////////////
      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName(BACKEND_NAME)
         .withOpensearchHost(opensearch.getHost())
         .withOpensearchPort(opensearch.getMappedPort(9200))
         .withOpensearchIndexName(INDEX_NAME)
         .withEnableRealTimeIndexing(false)
         .withSearchableTable(new SearchableTableConfig(TABLE_NAME, List.of(
            new SearchableFieldConfig("name").withWeight(3),
            new SearchableFieldConfig("description"),
            new SearchableFieldConfig("sku").withWeight(2).withIncludeLabel(true)))
            .withRecordLabelFormat("%s (%s)", "name", "sku"));

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
    ** Test 1: Verify the OpenSearch client was created and context was populated
    ** with config-driven discovered tables.
    *******************************************************************************/
   @Test
   @Order(1)
   void testConfigDrivenTableDiscovered()
   {
      assertThat(QuickSearchQBitContext.getClient()).isNotNull();
      assertThat(QuickSearchQBitContext.getClient()).isInstanceOf(QuickSearchOpenSearchClient.class);
      assertThat(QuickSearchQBitContext.getDiscoveredTables()).isNotNull();
      assertThat(QuickSearchQBitContext.getDiscoveredTables()).hasSize(1);
      assertThat(QuickSearchQBitContext.getDiscoveredTables().get(0).getTableName()).isEqualTo(TABLE_NAME);
   }



   /*******************************************************************************
    ** Test 2: Insert test records, run FullReindexStep, and verify search
    ** returns results for config-driven indexed table.
    *******************************************************************************/
   @Test
   @Order(2)
   void testFullReindex_configDrivenTable() throws Exception
   {
      ////////////////////////////////////////////////////
      // Insert test records into the memory backend    //
      ////////////////////////////////////////////////////
      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(TABLE_NAME);
      insertInput.setRecords(List.of(
         new QRecord().withValue("id", 1).withValue("name", "Blue Widget").withValue("description", "A fine widget").withValue("sku", "BW-001").withValue("modifyDate", Instant.now()),
         new QRecord().withValue("id", 2).withValue("name", "Red Widget").withValue("description", "A premium widget").withValue("sku", "RW-002").withValue("modifyDate", Instant.now()),
         new QRecord().withValue("id", 3).withValue("name", "Green Gadget").withValue("description", "A handy gadget").withValue("sku", "GG-003").withValue("modifyDate", Instant.now())
      ));
      new InsertAction().execute(insertInput);

      ////////////////////////////////////////////////////
      // Run full reindex                               //
      ////////////////////////////////////////////////////
      RunBackendStepInput  input  = new RunBackendStepInput();
      RunBackendStepOutput output = new RunBackendStepOutput();
      new FullReindexStep().run(input, output);

      ////////////////////////////////////////////////////
      // Force index refresh                            //
      ////////////////////////////////////////////////////
      ((QuickSearchOpenSearchClient) QuickSearchQBitContext.getClient()).refreshIndex();

      ////////////////////////////////////////////////////
      // Search for "widget" and verify results         //
      ////////////////////////////////////////////////////
      QuickSearchOutput searchOutput = new QuickSearchAction().execute(
         new QuickSearchInput().withSearchTerm("widget"));

      assertThat(searchOutput.getTotalHits())
         .as("search for 'widget' should return results from config-driven table")
         .isGreaterThan(0L);
   }



   /*******************************************************************************
    ** Test 3: Verify search results have expected fields and the custom
    ** recordLabelFormat is applied.
    *******************************************************************************/
   @Test
   @Order(3)
   void testSearchResults_recordLabelFormatApplied() throws Exception
   {
      QuickSearchOutput searchOutput = new QuickSearchAction().execute(
         new QuickSearchInput().withSearchTerm("widget"));

      assertThat(searchOutput.getResults()).isNotEmpty();

      searchOutput.getResults().forEach(result ->
      {
         assertThat(result.getTableName()).isEqualTo(TABLE_NAME);
         assertThat(result.getRecordId()).isNotBlank();
         assertThat(result.getScore()).isNotNull().isGreaterThan(0f);
         assertThat(result.getRecordLabel()).isNotBlank();
      });

      ///////////////////////////////////////////////////////////////////////////
      // Verify the recordLabelFormat was applied (format is "%s (%s)" with    //
      // name and sku fields, so labels should contain parentheses)            //
      ///////////////////////////////////////////////////////////////////////////
      assertThat(searchOutput.getResults())
         .extracting(QuickSearchResult::getRecordLabel)
         .allMatch(label -> label != null && label.contains("(") && label.contains(")"));
   }



   /*******************************************************************************
    ** Test 4: Verify search for a non-matching term returns zero results.
    *******************************************************************************/
   @Test
   @Order(4)
   void testSearchNoResults_configDrivenTable() throws Exception
   {
      QuickSearchOutput searchOutput = new QuickSearchAction().execute(
         new QuickSearchInput().withSearchTerm("zzzznonexistent"));

      assertThat(searchOutput.getTotalHits()).isEqualTo(0L);
      assertThat(searchOutput.getResults()).isEmpty();
      assertThat(searchOutput.getHasMore()).isFalse();
   }

}

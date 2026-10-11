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


import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.RecordSearchAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.search.RecordSearchInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.search.RecordSearchOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.search.RecordSearchResult;
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
import com.kingsrook.qbits.quicksearch.actions.QuickSearchRecordSearchProvider;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchResult;
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
 ** End-to-end test of core record search (RecordSearchAction, the code behind
 ** POST /qqq/v1/search) served by QuickSearchRecordSearchProvider from a real
 ** OpenSearch (Testcontainers): the producer registers the provider and
 ** searchFields, hits come back in Quick Search's relevance order and are
 ** re-read by primary key (stale ids drop out, limitPerTable applies),
 ** unindexed tables keep core's own search, and with OpenSearch stopped core
 ** falls back to its own search without failing.
 *******************************************************************************/
@ExtendWith(RequiresDockerCondition.class)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RecordSearchProviderIntegrationTest
{
   private static final String BACKEND_NAME  = "testMemoryBackend";
   private static final String PRODUCT_TABLE = "rsProduct";
   private static final String NOTE_TABLE    = "rsNote";
   private static final String INDEX_NAME    = "quick_search_record_search_it";

   @Container
   static GenericContainer<?> opensearch = new GenericContainer<>(System.getProperty("opensearch.test.image", "opensearchproject/opensearch:2.19.6"))
      .withExposedPorts(9200)
      .withEnv("discovery.type", "single-node")
      .withEnv("DISABLE_SECURITY_PLUGIN", "true")
      .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
      .waitingFor(Wait.forHttp("/_cluster/health").forStatusCode(200));

   private static QuickSearchQBitConfig config;



   /*******************************************************************************
    ** Build the instance (an indexed product table and an unindexed note
    ** table with its own searchFields), produce the QBit, index the products.
    *******************************************************************************/
   @BeforeAll
   static void setUpAll() throws Exception
   {
      MemoryRecordStore.getInstance().reset();

      QInstance qInstance = new QInstance();
      qInstance.addBackend(new QBackendMetaData().withName(BACKEND_NAME).withBackendType(MemoryBackendModule.class));
      qInstance.withInstanceDefaultAuthentication(new QAuthenticationMetaData().withName("anonymous").withType(QAuthenticationType.FULLY_ANONYMOUS));

      qInstance.addTable(new QTableMetaData()
         .withName(PRODUCT_TABLE)
         .withLabel("Product")
         .withBackendName(BACKEND_NAME)
         .withPrimaryKeyField("id")
         .withRecordLabelFormat("%s")
         .withRecordLabelFields("name")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("name", QFieldType.STRING))
         .withField(new QFieldMetaData("description", QFieldType.STRING))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME)));

      qInstance.addTable(new QTableMetaData()
         .withName(NOTE_TABLE)
         .withBackendName(BACKEND_NAME)
         .withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("text", QFieldType.STRING))
         .withSearchFields("text"));

      config = new QuickSearchQBitConfig()
         .withBackendName(BACKEND_NAME)
         .withOpensearchHost(opensearch.getHost())
         .withOpensearchPort(opensearch.getMappedPort(9200))
         .withOpensearchIndexName(INDEX_NAME)
         .withSearchableTable(new SearchableTableConfig(PRODUCT_TABLE, List.of(
            new SearchableFieldConfig("name").withWeight(3),
            new SearchableFieldConfig("description"))))
         .withEnableRealTimeIndexing(false);

      new QuickSearchQBitProducer().withConfig(config).produce(qInstance);
      QContext.init(qInstance, new QSession());

      new InsertAction().execute(new InsertInput(PRODUCT_TABLE).withRecords(List.of(
         new QRecord().withValue("id", 1).withValue("name", "Blue Widget").withValue("description", "the original").withValue("modifyDate", Instant.now()),
         new QRecord().withValue("id", 2).withValue("name", "Widget").withValue("description", "comes in blue and green").withValue("modifyDate", Instant.now()),
         new QRecord().withValue("id", 3).withValue("name", "Red Widget").withValue("description", "not that colour").withValue("modifyDate", Instant.now()),
         new QRecord().withValue("id", 4).withValue("name", "Gadget").withValue("description", "no match here").withValue("modifyDate", Instant.now()))));

      new InsertAction().execute(new InsertInput(NOTE_TABLE).withRecords(List.of(
         new QRecord().withValue("id", 1).withValue("text", "a blue widget note"))));

      new FullReindexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());
      config.getRuntime().getClient().refreshIndex();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterAll
   static void tearDownAll()
   {
      if(config != null && config.getRuntime() != null)
      {
         config.getRuntime().close();
      }
      QContext.clear();
      QuickSearchQBitContext.clear();
      MemoryRecordStore.getInstance().reset();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static RecordSearchOutput coreSearch(String term, Integer limitPerTable) throws Exception
   {
      return (new RecordSearchAction().execute(new RecordSearchInput().withSearchTerm(term).withLimitPerTable(limitPerTable)));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static List<Serializable> idsFor(RecordSearchOutput output, String tableName)
   {
      return (output.getResults().stream().filter(r -> tableName.equals(r.getTableName())).map(RecordSearchResult::getRecordId).toList());
   }



   @Test
   @Order(1)
   void testProducerRegisteredProviderAndSearchFields()
   {
      QInstance qInstance = QContext.getQInstance();
      assertThat(qInstance.getRecordSearchProvider().getName()).isEqualTo(QuickSearchRecordSearchProvider.class.getName());
      assertThat(qInstance.getTable(PRODUCT_TABLE).getSearchFields()).containsExactly("name", "description");
      assertThat(qInstance.getTable(NOTE_TABLE).getSearchFields()).containsExactly("text");
   }



   /*******************************************************************************
    ** "blue widget" matches products 1 and 2 in OpenSearch (all terms, any
    ** field); core's contains-search would only find product 1. The order is
    ** Quick Search's relevance order; labels come from core's re-read.
    *******************************************************************************/
   @Test
   @Order(2)
   void testCoreSearchServedFromOpenSearch() throws Exception
   {
      RecordSearchOutput output = coreSearch("blue widget", 5);

      List<String> quickSearchOrder = new QuickSearchAction().execute(new QuickSearchInput().withSearchTerm("blue widget").withTableNames(List.of(PRODUCT_TABLE)))
         .getResults().stream().map(QuickSearchResult::getRecordId).toList();
      assertThat(quickSearchOrder).containsExactlyInAnyOrder("1", "2");

      assertThat(idsFor(output, PRODUCT_TABLE)).containsExactlyElementsOf(quickSearchOrder.stream().map(Integer::valueOf).toList());
      assertThat(output.getResults()).filteredOn(r -> PRODUCT_TABLE.equals(r.getTableName())).extracting(RecordSearchResult::getRecordLabel).contains("Blue Widget", "Widget");
      assertThat(output.getResults()).filteredOn(r -> PRODUCT_TABLE.equals(r.getTableName())).extracting(RecordSearchResult::getTableLabel).containsOnly("Product");

      ////////////////////////////////////////////////
      // the unindexed table keeps core's own search //
      ////////////////////////////////////////////////
      assertThat(idsFor(output, NOTE_TABLE)).containsExactly(1);
      assertThat(config.getRuntime().isRecordSearchBackingOff()).isFalse();
   }



   @Test
   @Order(3)
   void testLimitPerTable() throws Exception
   {
      assertThat(idsFor(coreSearch("widget", 1), PRODUCT_TABLE)).hasSize(1);
      assertThat(idsFor(coreSearch("widget", 5), PRODUCT_TABLE)).containsExactlyInAnyOrder(1, 2, 3);
   }



   /*******************************************************************************
    ** A record deleted from the table but still in the index is dropped by
    ** core's re-read.
    *******************************************************************************/
   @Test
   @Order(4)
   void testStaleIndexHitDropped() throws Exception
   {
      new DeleteAction().execute(new DeleteInput(PRODUCT_TABLE).withPrimaryKeys(List.of(1)));

      assertThat(config.getRuntime().getClient().countDocumentsForTable(PRODUCT_TABLE)).as("the deleted record is still indexed").isEqualTo(4L);

      assertThat(idsFor(coreSearch("blue widget", 5), PRODUCT_TABLE)).containsExactly(2);
   }



   /*******************************************************************************
    ** With OpenSearch stopped, core search still answers, from core's own
    ** contains-search over the searchFields Quick Search set, and the
    ** provider backs off.
    *******************************************************************************/
   @Test
   @Order(5)
   void testFallbackWhenOpenSearchStopped() throws Exception
   {
      opensearch.stop();

      long               start  = System.currentTimeMillis();
      RecordSearchOutput output = coreSearch("widget", 5);
      assertThat(System.currentTimeMillis() - start).isLessThan(15_000);

      assertThat(idsFor(output, PRODUCT_TABLE)).containsExactly(2, 3);
      assertThat(config.getRuntime().isRecordSearchBackingOff()).isTrue();

      assertThat(idsFor(coreSearch("blue", 5), PRODUCT_TABLE)).containsExactly(2);
   }

}

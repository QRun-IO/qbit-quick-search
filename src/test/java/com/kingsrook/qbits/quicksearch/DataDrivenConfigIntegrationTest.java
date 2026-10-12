/*
 * Copyright 2026 Kingsrook, LLC
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.processes.RunProcessAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
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
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchAction;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchInput;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchResult;
import com.kingsrook.qbits.quicksearch.config.QuickSearchStatus;
import com.kingsrook.qbits.quicksearch.metadata.QuickSearchConfigProcessMetaDataHelper;
import com.kingsrook.qbits.quicksearch.model.QuickSearchDocumentsChoice;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexField;
import com.kingsrook.qbits.quicksearch.model.QuickSearchSettings;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import com.kingsrook.qbits.quicksearch.processes.AbstractIndexingStep;
import com.kingsrook.qbits.quicksearch.processes.BasepullIndexStep;
import com.kingsrook.qbits.quicksearch.processes.config.StopIndexingSteps;
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
 ** The data-driven config against a real OpenSearch: self-priming builds a
 ** code-declared table on a fresh install; Add Search Index adds a table at
 ** runtime and it becomes searchable; a field edit (an EXACT field) is
 ** previewed and applied; disabling stops it; the global pause stops
 ** indexing while search keeps serving, and resume catches up; removing
 ** deletes its documents; a lost index is recreated and rebuilt.
 *******************************************************************************/
@ExtendWith(RequiresDockerCondition.class)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DataDrivenConfigIntegrationTest
{
   private static final String BACKEND_NAME  = "testMemoryBackend";
   private static final String PRODUCT_TABLE = "ddProduct";
   private static final String ARTICLE_TABLE = "ddArticle";
   private static final String INDEX_NAME    = "data_driven_integration_test";

   @Container
   static GenericContainer<?> opensearch = new GenericContainer<>(System.getProperty("opensearch.test.image", "opensearchproject/opensearch:2.19.6"))
      .withExposedPorts(9200)
      .withEnv("discovery.type", "single-node")
      .withEnv("DISABLE_SECURITY_PLUGIN", "true")
      .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
      .waitingFor(Wait.forHttp("/_cluster/health").forStatusCode(200));

   private static QuickSearchRuntime runtime;



   /*******************************************************************************
    ** Produce the QBit with one config-declared table (ddProduct) and a table
    ** it does not know (ddArticle).
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
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("name", QFieldType.STRING))
         .withField(new QFieldMetaData("description", QFieldType.STRING))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME)));
      qInstance.addTable(new QTableMetaData()
         .withName(ARTICLE_TABLE)
         .withLabel("Article")
         .withBackendName(BACKEND_NAME)
         .withPrimaryKeyField("id")
         .withRecordLabelFields("title")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("title", QFieldType.STRING))
         .withField(new QFieldMetaData("body", QFieldType.TEXT))
         .withField(new QFieldMetaData("sku", QFieldType.INTEGER))
         .withField(new QFieldMetaData("code", QFieldType.INTEGER).withIsSensitive(true))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME)));

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName(BACKEND_NAME)
         .withOpensearchHost(opensearch.getHost())
         .withOpensearchPort(opensearch.getMappedPort(9200))
         .withOpensearchIndexName(INDEX_NAME)
         .withApplyRecordSecurityLocks(false)
         .withSearchableTable(new SearchableTableConfig(PRODUCT_TABLE, List.of(new SearchableFieldConfig("name").withWeight(3), new SearchableFieldConfig("description"))));

      new QuickSearchQBitProducer().withConfig(config).produce(qInstance);
      QContext.init(qInstance, new QSession());
      runtime = config.getRuntime();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterAll
   static void tearDownAll()
   {
      QContext.clear();
      QuickSearchQBitContext.clear();
      MemoryRecordStore.getInstance().reset();
   }



   /*******************************************************************************
    ** Fresh install: priming registers the code table and queues its build;
    ** the first basepull builds it and it is searchable.
    *******************************************************************************/
   @Test
   @Order(1)
   void testFreshInstall_primesAndBuilds() throws Exception
   {
      insert(PRODUCT_TABLE, new QRecord().withValue("id", 1).withValue("name", "Blue Widget").withValue("description", "a fine widget").withValue("modifyDate", Instant.now()));

      assertThat(runtime.getDiscoveredTables()).extracting(QuickSearchableTableConfig::getTableName).containsExactly(PRODUCT_TABLE);
      assertThat(QuickSearchStatus.read(runtime).buildingText()).contains("New: 1 table added and building (Product)");

      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());
      refresh();
      assertThat(indexRow(PRODUCT_TABLE).getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_ACTIVE);
      assertThat(search("widget")).extracting(QuickSearchResult::getTableName).containsExactly(PRODUCT_TABLE);
   }



   /*******************************************************************************
    ** Create flow: Add Search Index makes ddArticle searchable without a
    ** redeploy; the sensitive field never reaches the index.
    *******************************************************************************/
   @Test
   @Order(2)
   void testAddSearchIndex_tableBecomesSearchable() throws Exception
   {
      insert(ARTICLE_TABLE, new QRecord().withValue("id", 1).withValue("title", "Gadget Guide").withValue("body", "everything about gadgets")
         .withValue("sku", 4711).withValue("code", 990099).withValue("modifyDate", Instant.now()));
      assertThat(search("gadget")).isEmpty();

      RunProcessOutput output = runProcess(QuickSearchConfigProcessMetaDataHelper.ADD_INDEX_PROCESS_NAME, Map.of("searchTableName", ARTICLE_TABLE), null);
      assertThat(output.getException()).isEmpty();
      refresh();

      List<QuickSearchResult> results = search("gadget");
      assertThat(results).extracting(QuickSearchResult::getTableName).containsExactly(ARTICLE_TABLE);
      assertThat(results.get(0).getRecordLabel()).isEqualTo("Gadget Guide");
      assertThat(search("990099")).as("the sensitive field is never indexed").isEmpty();

      //////////////////////////////////////////////////////////////////
      // real-time indexing covers the new table at once              //
      //////////////////////////////////////////////////////////////////
      insert(ARTICLE_TABLE, new QRecord().withValue("id", 2).withValue("title", "Sprocket Notes").withValue("body", "sprockets").withValue("modifyDate", Instant.now()));
      refresh();
      assertThat(search("sprocket")).extracting(QuickSearchResult::getRecordId).containsExactly("2");
   }



   /*******************************************************************************
    ** Field edit then reindex: sku (an integer, not a default field) is added
    ** as an EXACT field; after Apply Search Index Changes the whole value
    ** finds the record and a fragment does not.
    *******************************************************************************/
   @Test
   @Order(3)
   void testFieldEdit_applyReindexes() throws Exception
   {
      Integer indexId = indexRow(ARTICLE_TABLE).getValueInteger("id");
      insert(QuickSearchIndexField.TABLE_NAME, new QRecord().withValue("quickSearchIndexId", indexId).withValue("fieldName", "sku").withValue("role", "EXACT"));
      assertThat(indexRow(ARTICLE_TABLE).getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_NEEDS_REINDEX);

      RunProcessOutput output = runProcess(QuickSearchConfigProcessMetaDataHelper.APPLY_CHANGES_PROCESS_NAME, Map.of(), List.of(indexId));
      assertThat(output.getException()).isEmpty();
      refresh();

      assertThat(indexRow(ARTICLE_TABLE).getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_ACTIVE);
      assertThat(search("4711")).extracting(QuickSearchResult::getRecordId).containsExactly("1");
      assertThat(search("471")).as("EXACT matches the whole value only").isEmpty();
   }



   /*******************************************************************************
    ** Disable stops indexing and hides the table from search.
    *******************************************************************************/
   @Test
   @Order(4)
   void testDisable_stops() throws Exception
   {
      Integer indexId = indexRow(ARTICLE_TABLE).getValueInteger("id");
      runProcess(QuickSearchConfigProcessMetaDataHelper.DISABLE_INDEX_PROCESS_NAME, Map.of(StopIndexingSteps.FIELD_DOCUMENTS, QuickSearchDocumentsChoice.KEEP.name()), List.of(indexId));
      insert(ARTICLE_TABLE, new QRecord().withValue("id", 3).withValue("title", "Doohickey Digest").withValue("modifyDate", Instant.now()));
      refresh();

      assertThat(search("gadget")).as("a disabled table is hidden from search").isEmpty();
      assertThat(runtime.getClient().countDocumentsForTable(ARTICLE_TABLE)).as("documents kept, nothing new indexed").isEqualTo(2L);
   }



   /*******************************************************************************
    ** Global pause: no indexing (real-time changes are deferred), search keeps
    ** serving; resume replays the deferred change.
    *******************************************************************************/
   @Test
   @Order(5)
   void testGlobalPause_noIndexingButSearchWorks() throws Exception
   {
      runProcess(QuickSearchConfigProcessMetaDataHelper.PAUSE_PROCESS_NAME, Map.of(), List.of(QuickSearchSettings.SETTINGS_ID));
      assertThat(runtime.isIndexingPaused()).isTrue();

      insert(PRODUCT_TABLE, new QRecord().withValue("id", 2).withValue("name", "Green Gizmo").withValue("modifyDate", Instant.now()));
      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());
      refresh();

      assertThat(search("gizmo")).as("nothing is indexed while paused").isEmpty();
      assertThat(search("widget")).as("search keeps serving the existing index").extracting(QuickSearchResult::getRecordId).containsExactly("1");
      assertThat(rows(QuickSearchFailedEvent.TABLE_NAME)).extracting(r -> r.getValueString("status")).containsExactly(QuickSearchFailedEvent.STATUS_DEFERRED);

      runProcess(QuickSearchConfigProcessMetaDataHelper.RESUME_PROCESS_NAME, Map.of(), List.of(QuickSearchSettings.SETTINGS_ID));
      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());
      refresh();
      assertThat(search("gizmo")).extracting(QuickSearchResult::getRecordId).containsExactly("2");
      assertThat(rows(QuickSearchFailedEvent.TABLE_NAME)).isEmpty();
   }



   /*******************************************************************************
    ** Remove with delete: the documents are gone and the table stays removed.
    *******************************************************************************/
   @Test
   @Order(6)
   void testRemove_deletesDocuments() throws Exception
   {
      Integer indexId = indexRow(ARTICLE_TABLE).getValueInteger("id");
      runProcess(QuickSearchConfigProcessMetaDataHelper.REMOVE_INDEX_PROCESS_NAME, Map.of(StopIndexingSteps.FIELD_DOCUMENTS, QuickSearchDocumentsChoice.DELETE.name()), List.of(indexId));
      refresh();

      assertThat(indexRow(ARTICLE_TABLE)).isNull();
      assertThat(runtime.getClient().countDocumentsForTable(ARTICLE_TABLE)).isZero();
      assertThat(runtime.getDiscoveredTables()).extracting(QuickSearchableTableConfig::getTableName).containsExactly(PRODUCT_TABLE);
   }



   /*******************************************************************************
    ** A lost index is recreated, and the tables that had documents in it are
    ** rebuilt.
    *******************************************************************************/
   @Test
   @Order(7)
   void testMissingIndex_recreatedAndRebuilt() throws Exception
   {
      QuickSearchOpenSearchClient client = runtime.getClient();
      for(String physical : client.getPhysicalIndexes())
      {
         client.deletePhysicalIndex(physical);
      }
      assertThat(client.indexExists()).isFalse();

      ////////////////////////////////////////////////////////////////////
      // one basepull recreates the index, queues the table and builds it //
      ////////////////////////////////////////////////////////////////////
      RunBackendStepOutput output = new RunBackendStepOutput();
      new BasepullIndexStep().run(new RunBackendStepInput(), output);
      assertThat(client.indexExists()).isTrue();
      assertThat(output.getValueInteger("tablesBuilt")).isEqualTo(1);
      assertThat(indexRow(PRODUCT_TABLE).getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_ACTIVE);
      refresh();
      assertThat(search("widget")).extracting(QuickSearchResult::getRecordId).containsExactly("1");
      assertThat(search("gizmo")).extracting(QuickSearchResult::getRecordId).containsExactly("2");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static List<QuickSearchResult> search(String term) throws QException
   {
      return (new QuickSearchAction().execute(new QuickSearchInput().withSearchTerm(term)).getResults());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void refresh() throws QException
   {
      runtime.getClient().refreshIndex();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void insert(String tableName, QRecord record) throws QException
   {
      new InsertAction().execute(new InsertInput(tableName).withRecords(List.of(record)));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static List<QRecord> rows(String tableName) throws QException
   {
      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(tableName);
      queryInput.setFilter(new QQueryFilter());
      return (CollectionUtils.nonNullList(new QueryAction().execute(queryInput).getRecords()));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static QRecord indexRow(String tableName) throws QException
   {
      return (rows(QuickSearchIndex.TABLE_NAME).stream().filter(r -> tableName.equals(r.getValueString("tableName"))).findFirst().orElse(null));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static RunProcessOutput runProcess(String processName, Map<String, Serializable> values, List<Integer> ids) throws QException
   {
      RunProcessInput input = new RunProcessInput();
      input.setProcessName(processName);
      input.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.SKIP);
      values.forEach(input::addValue);
      if(ids != null)
      {
         input.addValue("queryFilterJson", JsonUtils.toJson(new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.IN, new ArrayList<>(ids)))));
      }
      return (new RunProcessAction().execute(input));
   }

}

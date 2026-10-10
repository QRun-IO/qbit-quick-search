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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
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
import com.kingsrook.qbits.quicksearch.actions.QuickSearchResult;
import com.kingsrook.qbits.quicksearch.annotations.QuickSearchField;
import com.kingsrook.qbits.quicksearch.annotations.QuickSearchable;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import com.kingsrook.qbits.quicksearch.processes.AbstractIndexingStep;
import com.kingsrook.qbits.quicksearch.processes.BasepullIndexStep;
import com.kingsrook.qbits.quicksearch.processes.FullReindexStep;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Integration test, against a real OpenSearch, that a full reindex killed
 ** right after its alias swap (table left REBUILDING, RUNNING run record, a
 ** captured delete never applied, so the deleted record is back in search) is
 ** recovered by the scheduled basepull once its run record goes stale.
 *******************************************************************************/
@ExtendWith(RequiresDockerCondition.class)
@Testcontainers
class FullReindexRecoveryIntegrationTest
{
   private static final String BACKEND_NAME = "reindexRecoveryMemoryBackend";
   private static final String TABLE_NAME   = "recoveryCustomer";
   private static final String INDEX_NAME   = "quick_search_reindex_recovery_test";

   @Container
   static GenericContainer<?> opensearch = new GenericContainer<>(System.getProperty("opensearch.test.image", "opensearchproject/opensearch:2.19.6"))
      .withExposedPorts(9200)
      .withEnv("discovery.type", "single-node")
      .withEnv("DISABLE_SECURITY_PLUGIN", "true")
      .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
      .waitingFor(Wait.forHttp("/_cluster/health").forStatusCode(200));



   /*******************************************************************************
    ** Entity annotated for quick search indexing.
    *******************************************************************************/
   @QuickSearchable(tableName = TABLE_NAME)
   static class RecoveryCustomer
   {
      @QuickSearchField(weight = 2)
      private String name;

      @QuickSearchField
      private String city;
   }



   /*******************************************************************************
    ** Thrown to stop a full reindex the way a killed JVM does: it is not an
    ** Exception, so none of the step's failure handling runs.
    *******************************************************************************/
   static class SimulatedKill extends Error
   {
   }



   /*******************************************************************************
    ** Build the instance, produce the QBit with real-time indexing on and the
    ** record-security re-read off (search returns whatever the index holds),
    ** and initialize QContext.
    *******************************************************************************/
   @BeforeAll
   static void setUpAll() throws Exception
   {
      MemoryRecordStore.getInstance().reset();

      QInstance qInstance = new QInstance();
      qInstance.addBackend(new QBackendMetaData()
         .withName(BACKEND_NAME)
         .withBackendType(MemoryBackendModule.class));
      qInstance.withInstanceDefaultAuthentication(new QAuthenticationMetaData()
         .withName("anonymous")
         .withType(QAuthenticationType.FULLY_ANONYMOUS));
      qInstance.addTable(new QTableMetaData()
         .withName(TABLE_NAME)
         .withBackendName(BACKEND_NAME)
         .withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("name", QFieldType.STRING))
         .withField(new QFieldMetaData("city", QFieldType.STRING))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME)));

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName(BACKEND_NAME)
         .withOpensearchHost(opensearch.getHost())
         .withOpensearchPort(opensearch.getMappedPort(9200))
         .withOpensearchIndexName(INDEX_NAME)
         .withSearchableEntityClasses(List.of(RecoveryCustomer.class))
         .withEnableRealTimeIndexing(true)
         .withApplyRecordSecurityLocks(false);

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
    ** Test: a record deleted during a full reindex that is then killed right
    ** after its swap is back in search (the new index holds the copy read
    ** before the delete, and the captured delete was never applied). While the
    ** dead run's record is fresh it blocks a new full reindex; once it is
    ** stale, basepull recovers the table and replays the delete, and the
    ** record is gone from search.
    *******************************************************************************/
   @Test
   void testKilledFullReindex_isRecoveredByBasepull() throws Exception
   {
      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(TABLE_NAME);
      insertInput.setRecords(List.of(
         new QRecord().withValue("id", 1).withValue("name", "Gwendolyn").withValue("city", "Stalledton"),
         new QRecord().withValue("id", 2).withValue("name", "Stanford").withValue("city", "Stalledton")));
      new InsertAction().execute(insertInput);
      assertThat(searchRecordIds("stalledton")).containsExactlyInAnyOrder("1", "2");

      assertThatThrownBy(() -> new FullReindexStep()
      {
         ////////////////////////////////////////////////////////////////////
         // delete a record once its document is in the new physical index //
         ////////////////////////////////////////////////////////////////////
         @Override
         protected IndexCounts indexAllRecords(QuickSearchOpenSearchClient client, QuickSearchableTableConfig tableConfig, List<QFilterCriteria> extraCriteria, String targetIndex, QuickSearchIndexRun run) throws QException
         {
            IndexCounts counts = super.indexAllRecords(client, tableConfig, extraCriteria, targetIndex, run);

            DeleteInput deleteInput = new DeleteInput();
            deleteInput.setTableName(TABLE_NAME);
            deleteInput.setPrimaryKeys(List.<Serializable>of(1));
            new DeleteAction().execute(deleteInput);

            return (counts);
         }



         ////////////////////////////////////////////////////////////////////
         // die right after the swap, before any table is finished         //
         ////////////////////////////////////////////////////////////////////
         @Override
         protected void updateIndexRow(Integer indexId, Map<String, ? extends Serializable> values) throws QException
         {
            if(values.containsKey("lastFullReindexTime"))
            {
               throw (new SimulatedKill());
            }
            super.updateIndexRow(indexId, values);
         }
      }.run(new RunBackendStepInput(), new RunBackendStepOutput()))
         .isInstanceOf(SimulatedKill.class);

      assertThat(indexRow().getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_REBUILDING);
      assertThat(queryAll(QuickSearchIndexRun.TABLE_NAME)).singleElement()
         .satisfies(run -> assertThat(run.getValueString("status")).isEqualTo(AbstractIndexingStep.RUN_RUNNING));
      assertThat(queryAll(QuickSearchFailedEvent.TABLE_NAME)).singleElement()
         .satisfies(row -> assertThat(row.getValueString("status")).isEqualTo(QuickSearchFailedEvent.STATUS_AWAITING_REINDEX));
      assertThat(searchRecordIds("stalledton"))
         .as("the swapped-in index still holds the deleted record")
         .containsExactlyInAnyOrder("1", "2");

      assertThatThrownBy(() -> new FullReindexStep().run(new RunBackendStepInput(), new RunBackendStepOutput()))
         .as("the dead run's record is still fresh, so it counts as running")
         .isInstanceOf(QException.class)
         .hasMessageContaining("already running");

      backdateRunRecords();
      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      assertThat(searchRecordIds("stalledton")).containsExactly("2");
      assertThat(indexRow().getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_ACTIVE);
      assertThat(queryAll(QuickSearchFailedEvent.TABLE_NAME)).isEmpty();
      assertThat(queryAll(QuickSearchIndexRun.TABLE_NAME))
         .filteredOn(run -> FullReindexStep.RUN_TYPE.equals(run.getValueString("runType")))
         .singleElement()
         .satisfies(run ->
         {
            assertThat(run.getValueString("status")).isEqualTo(AbstractIndexingStep.RUN_FAILED);
            assertThat(run.getValueString("errorMessage")).contains("stopped without finishing");
         });
   }



   /*******************************************************************************
    ** Move the start and heartbeat of every RUNNING run record two hours into
    ** the past, as if its run had stopped refreshing it.
    *******************************************************************************/
   private void backdateRunRecords() throws QException
   {
      Instant twoHoursAgo = Instant.now().minus(2, ChronoUnit.HOURS);
      for(QRecord run : queryAll(QuickSearchIndexRun.TABLE_NAME))
      {
         if(AbstractIndexingStep.RUN_RUNNING.equals(run.getValueString("status")))
         {
            UpdateInput updateInput = new UpdateInput();
            updateInput.setTableName(QuickSearchIndexRun.TABLE_NAME);
            updateInput.setOmitModifyDateUpdate(true);
            updateInput.setRecords(List.of(new QRecord().withValue("id", run.getValue("id")).withValue("startTime", twoHoursAgo).withValue("modifyDate", twoHoursAgo)));
            new UpdateAction().execute(updateInput);
         }
      }
   }



   /*******************************************************************************
    ** The table's quickSearchIndex row.
    *******************************************************************************/
   private QRecord indexRow() throws QException
   {
      return (queryAll(QuickSearchIndex.TABLE_NAME).stream()
         .filter(row -> TABLE_NAME.equals(row.getValueString("tableName")))
         .findFirst().orElseThrow());
   }



   /*******************************************************************************
    ** All rows of a table.
    *******************************************************************************/
   private List<QRecord> queryAll(String tableName) throws QException
   {
      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(tableName);
      return (new QueryAction().execute(queryInput).getRecords());
   }



   /*******************************************************************************
    ** Refresh the index, then return the record IDs search returns for the term.
    *******************************************************************************/
   private List<String> searchRecordIds(String term) throws QException
   {
      QuickSearchQBitContext.getClient().refreshIndex();
      return (new QuickSearchAction().execute(new QuickSearchInput().withSearchTerm(term).withTableName(TABLE_NAME))
         .getResults().stream()
         .map(QuickSearchResult::getRecordId)
         .toList());
   }

}

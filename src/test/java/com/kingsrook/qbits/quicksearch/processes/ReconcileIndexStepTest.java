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

package com.kingsrook.qbits.quicksearch.processes;


import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QCollectingLogger;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qbits.quicksearch.BaseQuickSearchTest;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitContext;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;
import com.kingsrook.qbits.quicksearch.opensearch.BulkIndexResult;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/*******************************************************************************
 ** Tests for ReconcileIndexStep.
 *******************************************************************************/
class ReconcileIndexStepTest extends BaseQuickSearchTest
{
   private QuickSearchOpenSearchClient mockClient;



   /*******************************************************************************
    ** Install a mock client that reports every document as indexed.
    *******************************************************************************/
   @BeforeEach
   void setUpClient() throws QException
   {
      mockClient = mock(QuickSearchOpenSearchClient.class);
      when(mockClient.indexDocuments(anyList(), anyInt())).thenAnswer(invocation ->
      {
         List<?> docs = invocation.getArgument(0);
         return (new BulkIndexResult().withSuccessCount(docs.size()));
      });
      when(mockClient.deleteDocumentsIndexedBefore(anyString(), any())).thenReturn(0L);
      QuickSearchQBitContext.setClient(mockClient);
   }



   /*******************************************************************************
    ** Insert count source records into the testEntity table.
    *******************************************************************************/
   private void insertTestEntities(int count) throws QException
   {
      List<QRecord> records = new ArrayList<>();
      for(int i = 1; i <= count; i++)
      {
         records.add(new QRecord().withValue("name", "Entity " + i).withValue("description", "Description " + i));
      }

      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(TEST_ENTITY_TABLE);
      insertInput.setRecords(records);
      new InsertAction().execute(insertInput);
   }



   /*******************************************************************************
    ** Query all rows of the given table.
    *******************************************************************************/
   private List<QRecord> queryAll(String tableName) throws QException
   {
      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(tableName);
      queryInput.setFilter(new QQueryFilter());
      return (new QueryAction().execute(queryInput).getRecords());
   }



   /*******************************************************************************
    ** Collect the record IDs of every document passed to indexDocuments.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   private List<String> indexedRecordIds() throws QException
   {
      ArgumentCaptor<List<OpenSearchDocument>> captor = ArgumentCaptor.forClass(List.class);
      verify(mockClient, atLeastOnce()).indexDocuments(captor.capture(), anyInt());

      List<String> recordIds = new ArrayList<>();
      for(List<OpenSearchDocument> batch : captor.getAllValues())
      {
         batch.forEach(doc -> recordIds.add(doc.getRecordId()));
      }
      return (recordIds);
   }



   /*******************************************************************************
    ** Test: every source record is re-indexed, then documents not re-indexed by
    ** this run are removed, after a refresh. The table is never wiped first.
    *******************************************************************************/
   @Test
   void testReconcile_reindexesSourceThenRemovesStaleDocuments() throws QException
   {
      insertTestEntities(3);
      when(mockClient.deleteDocumentsIndexedBefore(anyString(), any())).thenReturn(2L);

      Instant beforeRun = Instant.now();
      RunBackendStepOutput output = new RunBackendStepOutput();
      new ReconcileIndexStep().run(new RunBackendStepInput(), output);
      Instant afterRun = Instant.now();

      assertThat(indexedRecordIds()).containsExactlyInAnyOrder("1", "2", "3");

      ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
      InOrder inOrder = inOrder(mockClient);
      inOrder.verify(mockClient).indexDocuments(anyList(), anyInt());
      inOrder.verify(mockClient).refreshIndex();
      inOrder.verify(mockClient).deleteDocumentsIndexedBefore(eq(TEST_ENTITY_TABLE), cutoffCaptor.capture());

      assertThat(cutoffCaptor.getValue()).isBetween(beforeRun, afterRun);
      verify(mockClient, never()).deleteDocumentsForTable(anyString());

      assertThat(output.getValueInteger("recordsIndexed")).isEqualTo(3);
      assertThat(output.getValue("documentsRemoved")).isEqualTo(2L);
   }



   /*******************************************************************************
    ** Test: source records are paged by primary key, so every record is read
    ** exactly once across batches.
    *******************************************************************************/
   @Test
   void testReconcile_pagesByPrimaryKey_eachRecordOnce() throws QException
   {
      QuickSearchQBitContext.getConfig().withSourceBatchSize(2);
      insertTestEntities(5);

      new ReconcileIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      verify(mockClient, times(3)).indexDocuments(anyList(), anyInt());
      assertThat(indexedRecordIds()).containsExactly("1", "2", "3", "4", "5");
   }



   /*******************************************************************************
    ** Test: when any document fails to index, stale documents are not removed
    ** (a document that failed to re-index would otherwise be purged), the run
    ** is marked FAILED, and the step throws.
    *******************************************************************************/
   @Test
   void testReconcile_indexingErrors_keepsDocumentsAndFailsRun() throws QException
   {
      insertTestEntities(2);
      BulkIndexResult partial = new BulkIndexResult();
      partial.addSuccess();
      partial.addFailure("testEntity:2: mapper_parsing_exception");
      when(mockClient.indexDocuments(anyList(), anyInt())).thenReturn(partial);

      assertThatThrownBy(() -> new ReconcileIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput()))
         .isInstanceOf(QException.class)
         .hasMessageContaining("mapper_parsing_exception");

      verify(mockClient, never()).deleteDocumentsIndexedBefore(anyString(), any());

      List<QRecord> runs = queryAll(QuickSearchIndexRun.TABLE_NAME);
      assertThat(runs).hasSize(1);
      assertThat(runs.get(0).getValueString("runType")).isEqualTo("RECONCILE");
      assertThat(runs.get(0).getValueString("status")).isEqualTo("FAILED");
      assertThat(runs.get(0).getValueInteger("errorCount")).isEqualTo(1);
      assertThat(runs.get(0).getValueString("errorMessage")).contains("mapper_parsing_exception");
   }



   /*******************************************************************************
    ** Test: a client exception fails the run record and is rethrown.
    *******************************************************************************/
   @Test
   void testReconcile_clientThrows_failsRunAndThrows() throws QException
   {
      insertTestEntities(1);
      when(mockClient.indexDocuments(anyList(), anyInt())).thenThrow(new QException("connection refused"));

      assertThatThrownBy(() -> new ReconcileIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput()))
         .isInstanceOf(QException.class)
         .hasMessageContaining("connection refused");

      List<QRecord> runs = queryAll(QuickSearchIndexRun.TABLE_NAME);
      assertThat(runs).hasSize(1);
      assertThat(runs.get(0).getValueString("status")).isEqualTo("FAILED");
      assertThat(runs.get(0).getValueString("errorMessage")).contains("connection refused");
      verify(mockClient, never()).deleteDocumentsIndexedBefore(anyString(), any());
   }



   /*******************************************************************************
    ** Test: a successful run completes its run record and updates the index row.
    *******************************************************************************/
   @Test
   void testReconcile_success_completesRunAndUpdatesIndexRow() throws QException
   {
      insertTestEntities(4);
      when(mockClient.countDocumentsForTable(TEST_ENTITY_TABLE)).thenReturn(4L);

      Instant beforeRun = Instant.now();
      new ReconcileIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      List<QRecord> runs = queryAll(QuickSearchIndexRun.TABLE_NAME);
      assertThat(runs).hasSize(1);
      assertThat(runs.get(0).getValueString("runType")).isEqualTo("RECONCILE");
      assertThat(runs.get(0).getValueString("status")).isEqualTo("COMPLETED");
      assertThat(runs.get(0).getValueInteger("recordsProcessed")).isEqualTo(4);
      assertThat(runs.get(0).getValueInteger("recordsIndexed")).isEqualTo(4);

      List<QRecord> indexRows = queryAll(QuickSearchIndex.TABLE_NAME);
      assertThat(indexRows).hasSize(1);
      assertThat(indexRows.get(0).getValueInteger("recordCount")).isEqualTo(4);
      assertThat(indexRows.get(0).getValueInstant("lastReconcileTime")).isAfterOrEqualTo(beforeRun);
      assertThat(indexRows.get(0).getValueInstant("lastBasepullTime")).isAfterOrEqualTo(beforeRun);
      assertThat(indexRows.get(0).getValue("lastFullReindexTime")).as("a reconcile is not a full reindex").isNull();
      assertThat(indexRows.get(0).getValueInteger("documentCount")).isEqualTo(4);
      assertThat(indexRows.get(0).getValueString("status")).isEqualTo("ACTIVE");
      assertThat(indexRows.get(0).getValueString("lastRunStatus")).isEqualTo("RECONCILE COMPLETED");
      assertThat(indexRows.get(0).getValueString("searchableFieldsJson")).isEqualTo(AbstractIndexingStep.buildSearchableFieldsJson(QuickSearchQBitContext.getTableConfig(TEST_ENTITY_TABLE)));
   }



   /*******************************************************************************
    ** Test: a failing document count is logged at WARN with the table name, and
    ** the run still completes, keeping the previous documentCount.
    *******************************************************************************/
   @Test
   void testReconcile_documentCountFails_loggedAsWarning_previousCountKept() throws QException
   {
      insertTestEntities(1);
      insertIndexRow(true, 99);
      when(mockClient.countDocumentsForTable(TEST_ENTITY_TABLE)).thenThrow(new QException("count failed"));

      QCollectingLogger collectingLogger = QLogger.activateCollectingLoggerForClass(ReconcileIndexStep.class);
      try
      {
         new ReconcileIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());
      }
      finally
      {
         QLogger.deactivateCollectingLoggerForClass(ReconcileIndexStep.class);
      }

      assertThat(collectingLogger.getCollectedMessages())
         .filteredOn(m -> Level.WARN.equals(m.getLevel()) && m.getMessage().contains("Could not count indexed documents"))
         .singleElement()
         .satisfies(m ->
         {
            assertThat(m.getMessage()).contains("\"tableName\":\"" + TEST_ENTITY_TABLE + "\"");
            assertThat(m.getMessage()).contains("\"stackTrace\"").contains("count failed");
         });

      QRecord row = queryAll(QuickSearchIndex.TABLE_NAME).get(0);
      assertThat(row.getValueString("lastRunStatus")).isEqualTo("RECONCILE COMPLETED");
      assertThat(row.getValueInteger("recordCount")).isEqualTo(1);
      assertThat(row.getValueInteger("documentCount")).isEqualTo(99);
   }



   /*******************************************************************************
    ** Test: the tableName input limits the run to that table.
    *******************************************************************************/
   @Test
   void testReconcile_unknownTableNameInput_nothingReconciled() throws QException
   {
      insertTestEntities(1);

      RunBackendStepInput input = new RunBackendStepInput();
      input.addValue("tableName", "someOtherTable");
      new ReconcileIndexStep().run(input, new RunBackendStepOutput());

      verify(mockClient, never()).indexDocuments(anyList(), anyInt());
      verify(mockClient, never()).deleteDocumentsIndexedBefore(anyString(), any());
      assertThat(queryAll(QuickSearchIndexRun.TABLE_NAME)).isEmpty();
   }



   /*******************************************************************************
    ** Insert a quickSearchIndex row for testEntity with the given enabled flag.
    *******************************************************************************/
   private void insertIndexRow(Boolean enabled) throws QException
   {
      insertIndexRow(enabled, null);
   }



   /*******************************************************************************
    ** Insert a quickSearchIndex row for testEntity with the given enabled flag
    ** and documentCount.
    *******************************************************************************/
   private void insertIndexRow(Boolean enabled, Integer documentCount) throws QException
   {
      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(QuickSearchIndex.TABLE_NAME);
      insertInput.setRecords(List.of(new QRecord()
         .withValue("tableName", TEST_ENTITY_TABLE)
         .withValue("enabled", enabled)
         .withValue("basepullIntervalMinutes", 5)
         .withValue("documentCount", documentCount)
         .withValue("status", "ACTIVE")));
      new InsertAction().execute(insertInput);
   }



   /*******************************************************************************
    ** Test: a disabled index row is skipped and counted in tablesSkipped.
    *******************************************************************************/
   @Test
   void testReconcile_disabledRow_skipped() throws QException
   {
      insertTestEntities(2);
      insertIndexRow(false);

      RunBackendStepOutput output = new RunBackendStepOutput();
      new ReconcileIndexStep().run(new RunBackendStepInput(), output);

      verify(mockClient, never()).indexDocuments(anyList(), anyInt());
      verify(mockClient, never()).deleteDocumentsIndexedBefore(anyString(), any());
      assertThat(queryAll(QuickSearchIndexRun.TABLE_NAME)).isEmpty();
      assertThat(output.getValueInteger("tablesSkipped")).isEqualTo(1);
      assertThat(output.getValueInteger("recordsIndexed")).isEqualTo(0);
   }



   /*******************************************************************************
    ** Test: without a table filter, documents of tables that are no longer
    ** configured are swept, passing the configured table names to keep.
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void testReconcile_allTables_sweepsOrphanDocumentsOfUnconfiguredTables() throws QException
   {
      insertTestEntities(1);
      when(mockClient.deleteDocumentsForTablesNotIn(any())).thenReturn(7L);

      RunBackendStepOutput output = new RunBackendStepOutput();
      new ReconcileIndexStep().run(new RunBackendStepInput(), output);

      ArgumentCaptor<Collection<String>> keepCaptor = ArgumentCaptor.forClass(Collection.class);
      verify(mockClient).deleteDocumentsForTablesNotIn(keepCaptor.capture());
      assertThat(keepCaptor.getValue()).containsExactly(TEST_ENTITY_TABLE);
      assertThat(output.getValue("orphanDocumentsRemoved")).isEqualTo(7L);
   }



   /*******************************************************************************
    ** Test: with a table filter the orphan sweep does not run.
    *******************************************************************************/
   @Test
   void testReconcile_singleTable_noOrphanSweep() throws QException
   {
      insertTestEntities(1);

      RunBackendStepInput input = new RunBackendStepInput();
      input.addValue("tableName", TEST_ENTITY_TABLE);
      RunBackendStepOutput output = new RunBackendStepOutput();
      new ReconcileIndexStep().run(input, output);

      verify(mockClient).indexDocuments(anyList(), anyInt());
      verify(mockClient, never()).deleteDocumentsForTablesNotIn(any());
      assertThat(output.getValue("orphanDocumentsRemoved")).isEqualTo(0L);
   }



   /*******************************************************************************
    ** Test: a failed run carries the error message on the run record and on
    ** the index row.
    *******************************************************************************/
   @Test
   void testReconcile_failedRun_indexRowCarriesError() throws QException
   {
      insertTestEntities(1);
      when(mockClient.indexDocuments(anyList(), anyInt())).thenThrow(new QException("connection refused"));

      assertThatThrownBy(() -> new ReconcileIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput())).isInstanceOf(QException.class);

      QRecord row = queryAll(QuickSearchIndex.TABLE_NAME).get(0);
      assertThat(row.getValueString("lastRunStatus")).isEqualTo("RECONCILE FAILED");
      assertThat(row.getValueString("lastErrorMessage")).contains("connection refused");
   }

}

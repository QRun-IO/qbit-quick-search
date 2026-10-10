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


import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QCollectingLogger;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateOutput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.statusmessages.SystemErrorStatusMessage;
import com.kingsrook.qbits.quicksearch.BaseQuickSearchTest;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitContext;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;
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
import org.mockito.MockedConstruction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/*******************************************************************************
 ** Tests for FullReindexStep.
 **
 ** Without a tableName input every enabled table is indexed into a new
 ** physical index which then takes over the alias; with a tableName input the
 ** table is rebuilt in place through the reconcile algorithm, never wiped.
 *******************************************************************************/
class FullReindexStepTest extends BaseQuickSearchTest
{
   private static final String PHYSICAL_INDEX = "test-index-v2-1700000000";

   private QuickSearchOpenSearchClient mockClient;



   /*******************************************************************************
    ** Install a mock client that reports every document as indexed, on both
    ** the alias and the physical-index overloads.
    *******************************************************************************/
   @BeforeEach
   void setUpClient() throws QException
   {
      mockClient = mock(QuickSearchOpenSearchClient.class);
      when(mockClient.newPhysicalIndexName()).thenReturn(PHYSICAL_INDEX);
      when(mockClient.indexDocuments(anyString(), anyList(), anyInt())).thenAnswer(invocation ->
      {
         List<?> docs = invocation.getArgument(1);
         return (new BulkIndexResult().withSuccessCount(docs.size()));
      });
      when(mockClient.indexDocuments(anyList(), anyInt())).thenAnswer(invocation ->
      {
         List<?> docs = invocation.getArgument(0);
         return (new BulkIndexResult().withSuccessCount(docs.size()));
      });
      when(mockClient.countDocumentsForTable(anyString())).thenReturn(0L);
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
    ** Query all rows of a table.
    *******************************************************************************/
   private List<QRecord> queryAll(String tableName) throws QException
   {
      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(tableName);
      queryInput.setFilter(new QQueryFilter());
      return (new QueryAction().execute(queryInput).getRecords());
   }



   /*******************************************************************************
    ** The quickSearchIndex row for testEntity.
    *******************************************************************************/
   private QRecord queryIndexRow() throws QException
   {
      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(QuickSearchIndex.TABLE_NAME);
      queryInput.setFilter(new QQueryFilter(new QFilterCriteria("tableName", QCriteriaOperator.EQUALS, TEST_ENTITY_TABLE)));
      List<QRecord> rows = new QueryAction().execute(queryInput).getRecords();
      return (rows.isEmpty() ? null : rows.get(0));
   }



   /*******************************************************************************
    ** Input with a tableName filter.
    *******************************************************************************/
   private RunBackendStepInput inputForTable(String tableName)
   {
      RunBackendStepInput input = new RunBackendStepInput();
      input.addValue("tableName", tableName);
      return (input);
   }



   /*******************************************************************************
    ** Test: without a tableName input, documents go into a fresh physical
    ** index which is refreshed and then takes over the alias; the live index
    ** is never wiped.
    *******************************************************************************/
   @Test
   void testAllTables_indexesIntoNewPhysicalIndexThenSwapsAlias() throws QException
   {
      insertTestEntities(3);

      RunBackendStepOutput output = new RunBackendStepOutput();
      new FullReindexStep().run(new RunBackendStepInput(), output);

      InOrder inOrder = inOrder(mockClient);
      inOrder.verify(mockClient).createPhysicalIndex(PHYSICAL_INDEX);
      inOrder.verify(mockClient).indexDocuments(eq(PHYSICAL_INDEX), anyList(), anyInt());
      inOrder.verify(mockClient).refreshIndex(PHYSICAL_INDEX);
      inOrder.verify(mockClient).swapAliasTo(PHYSICAL_INDEX);

      verify(mockClient, never()).deleteDocumentsForTable(anyString());
      verify(mockClient, never()).indexDocuments(anyList(), anyInt());
      verify(mockClient, never()).deletePhysicalIndex(anyString());

      assertThat(output.getValueInteger("recordsIndexed")).isEqualTo(3);
      assertThat(output.getValueBoolean("aliasSwapped")).isTrue();
   }



   /*******************************************************************************
    ** Test: the documents built from the source records are the ones sent to
    ** the physical index, and the run record counts them.
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void testAllTables_documentsBuiltFromSourceRecords() throws QException
   {
      insertTestEntities(5);

      new FullReindexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      ArgumentCaptor<List<OpenSearchDocument>> captor = ArgumentCaptor.forClass(List.class);
      verify(mockClient).indexDocuments(eq(PHYSICAL_INDEX), captor.capture(), anyInt());
      assertThat(captor.getValue()).extracting(OpenSearchDocument::getRecordId).containsExactly("1", "2", "3", "4", "5");

      List<QRecord> runs = queryAll(QuickSearchIndexRun.TABLE_NAME);
      assertThat(runs).hasSize(1);
      assertThat(runs.get(0).getValueString("runType")).isEqualTo("FULL_REINDEX");
      assertThat(runs.get(0).getValueString("status")).isEqualTo("COMPLETED");
      assertThat(runs.get(0).getValueInteger("recordsProcessed")).isEqualTo(5);
      assertThat(runs.get(0).getValueInteger("recordsIndexed")).isEqualTo(5);
   }



   /*******************************************************************************
    ** Test: the index row is updated after the swap.
    *******************************************************************************/
   @Test
   void testAllTables_updatesIndexRowAfterSwap() throws QException
   {
      insertTestEntities(2);
      when(mockClient.countDocumentsForTable(TEST_ENTITY_TABLE)).thenReturn(2L);

      new FullReindexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      QRecord row = queryIndexRow();
      assertThat(row).isNotNull();
      assertThat(row.getValue("lastFullReindexTime")).isNotNull();
      assertThat(row.getValue("lastBasepullTime")).isNotNull();
      assertThat(row.getValueInteger("recordCount")).isEqualTo(2);
      assertThat(row.getValueInteger("documentCount")).isEqualTo(2);
      assertThat(row.getValueString("status")).isEqualTo("ACTIVE");
      assertThat(row.getValueString("lastRunStatus")).isEqualTo("FULL_REINDEX COMPLETED");
      assertThat(row.getValueString("searchableFieldsJson")).isEqualTo(AbstractIndexingStep.buildSearchableFieldsJson(QuickSearchQBitContext.getTableConfig(TEST_ENTITY_TABLE)));
   }



   /*******************************************************************************
    ** Test: a failing document count after the swap is logged at WARN with the
    ** table name, and the run still completes, keeping the previous
    ** documentCount.
    *******************************************************************************/
   @Test
   void testAllTables_documentCountFails_loggedAsWarning_previousCountKept() throws QException
   {
      insertTestEntities(1);
      insertIndexRow(true, 99);
      when(mockClient.countDocumentsForTable(TEST_ENTITY_TABLE)).thenThrow(new QException("count failed"));

      QCollectingLogger collectingLogger = QLogger.activateCollectingLoggerForClass(ReconcileIndexStep.class);
      try
      {
         new FullReindexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());
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

      QRecord row = queryIndexRow();
      assertThat(row.getValueString("lastRunStatus")).isEqualTo("FULL_REINDEX COMPLETED");
      assertThat(row.getValueInteger("recordCount")).isEqualTo(1);
      assertThat(row.getValueInteger("documentCount")).isEqualTo(99);
   }



   /*******************************************************************************
    ** Test: an empty source table still produces a new physical index, a
    ** swap, and a COMPLETED run with zero counts.
    *******************************************************************************/
   @Test
   void testAllTables_emptyTable_swapsWithZeroCounts() throws QException
   {
      new FullReindexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      verify(mockClient).createPhysicalIndex(PHYSICAL_INDEX);
      verify(mockClient).swapAliasTo(PHYSICAL_INDEX);
      verify(mockClient, never()).indexDocuments(anyString(), anyList(), anyInt());

      List<QRecord> runs = queryAll(QuickSearchIndexRun.TABLE_NAME);
      assertThat(runs).hasSize(1);
      assertThat(runs.get(0).getValueString("status")).isEqualTo("COMPLETED");
      assertThat(runs.get(0).getValueInteger("recordsProcessed")).isEqualTo(0);
      assertThat(runs.get(0).getValueInteger("recordsIndexed")).isEqualTo(0);
   }



   /*******************************************************************************
    ** Test: a missing quickSearchIndex row is created before the reindex.
    *******************************************************************************/
   @Test
   void testLazyIndexRowCreation_noExistingIndexRow_createdBeforeReindex() throws QException
   {
      assertThat(queryAll(QuickSearchIndex.TABLE_NAME)).isEmpty();

      new FullReindexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      List<QRecord> rows = queryAll(QuickSearchIndex.TABLE_NAME);
      assertThat(rows).hasSize(1);
      assertThat(rows.get(0).getValueString("tableName")).isEqualTo(TEST_ENTITY_TABLE);
   }



   /*******************************************************************************
    ** Test: when the client throws while indexing, the new physical index is
    ** deleted, the alias is never swapped, the run is FAILED with the error
    ** message, and the step throws.
    *******************************************************************************/
   @Test
   void testAllTables_clientThrows_deletesPhysicalIndexAndNeverSwaps() throws QException
   {
      insertTestEntities(1);
      when(mockClient.indexDocuments(anyString(), anyList(), anyInt())).thenThrow(new QException("OpenSearch connection refused"));

      assertThatThrownBy(() -> new FullReindexStep().run(new RunBackendStepInput(), new RunBackendStepOutput()))
         .isInstanceOf(QException.class)
         .hasMessageContaining("OpenSearch connection refused");

      verify(mockClient).deletePhysicalIndex(PHYSICAL_INDEX);
      verify(mockClient, never()).swapAliasTo(anyString());

      List<QRecord> runs = queryAll(QuickSearchIndexRun.TABLE_NAME);
      assertThat(runs).hasSize(1);
      assertThat(runs.get(0).getValueString("status")).isEqualTo("FAILED");
      assertThat(runs.get(0).getValueString("errorMessage")).contains("OpenSearch connection refused");

      QRecord row = queryIndexRow();
      assertThat(row.getValueString("lastRunStatus")).isEqualTo("FULL_REINDEX FAILED");
      assertThat(row.getValueString("lastErrorMessage")).contains("OpenSearch connection refused");
   }



   /*******************************************************************************
    ** Test: per-document bulk failures also abort the rebuild, with the first
    ** item error on the run record.
    *******************************************************************************/
   @Test
   void testAllTables_bulkItemFailures_runFailedWithErrorMessage() throws QException
   {
      insertTestEntities(2);
      BulkIndexResult partial = new BulkIndexResult();
      partial.addSuccess();
      partial.addFailure("testEntity:2: mapper_parsing_exception");
      when(mockClient.indexDocuments(anyString(), anyList(), anyInt())).thenReturn(partial);

      assertThatThrownBy(() -> new FullReindexStep().run(new RunBackendStepInput(), new RunBackendStepOutput()))
         .isInstanceOf(QException.class)
         .hasMessageContaining("mapper_parsing_exception");

      verify(mockClient).deletePhysicalIndex(PHYSICAL_INDEX);
      verify(mockClient, never()).swapAliasTo(anyString());

      List<QRecord> runs = queryAll(QuickSearchIndexRun.TABLE_NAME);
      assertThat(runs).hasSize(1);
      assertThat(runs.get(0).getValueString("status")).isEqualTo("FAILED");
      assertThat(runs.get(0).getValueInteger("errorCount")).isEqualTo(1);
      assertThat(runs.get(0).getValueString("errorMessage")).contains("mapper_parsing_exception");
   }



   /*******************************************************************************
    ** Test: a failure to delete the incomplete physical index does not hide
    ** the original error.
    *******************************************************************************/
   @Test
   void testAllTables_cleanupFails_originalErrorStillThrown() throws QException
   {
      insertTestEntities(1);
      when(mockClient.indexDocuments(anyString(), anyList(), anyInt())).thenThrow(new QException("bulk failed"));
      doThrow(new QException("delete failed")).when(mockClient).deletePhysicalIndex(PHYSICAL_INDEX);

      assertThatThrownBy(() -> new FullReindexStep().run(new RunBackendStepInput(), new RunBackendStepOutput()))
         .isInstanceOf(QException.class)
         .hasMessageContaining("bulk failed");
   }



   /*******************************************************************************
    ** Test: with a tableName input the table is rebuilt in place through the
    ** reconcile algorithm: alias writes, refresh, stale delete, no wipe, no
    ** physical index, no swap.
    *******************************************************************************/
   @Test
   void testSingleTable_reconcilesInPlace_neverWipes() throws QException
   {
      insertTestEntities(2);

      RunBackendStepOutput output = new RunBackendStepOutput();
      new FullReindexStep().run(inputForTable(TEST_ENTITY_TABLE), output);

      InOrder inOrder = inOrder(mockClient);
      inOrder.verify(mockClient).indexDocuments(anyList(), anyInt());
      inOrder.verify(mockClient).refreshIndex();
      inOrder.verify(mockClient).deleteDocumentsIndexedBefore(eq(TEST_ENTITY_TABLE), any());

      verify(mockClient, never()).deleteDocumentsForTable(anyString());
      verify(mockClient, never()).createPhysicalIndex(anyString());
      verify(mockClient, never()).swapAliasTo(anyString());
      verify(mockClient, never()).indexDocuments(anyString(), anyList(), anyInt());

      List<QRecord> runs = queryAll(QuickSearchIndexRun.TABLE_NAME);
      assertThat(runs).hasSize(1);
      assertThat(runs.get(0).getValueString("runType")).isEqualTo("FULL_REINDEX");
      assertThat(runs.get(0).getValueString("status")).isEqualTo("COMPLETED");
      assertThat(runs.get(0).getValueInteger("recordsProcessed")).isEqualTo(2);

      assertThat(queryIndexRow().getValue("lastFullReindexTime")).isNotNull();
      assertThat(output.getValueInteger("recordsIndexed")).isEqualTo(2);
      assertThat(output.getValueBoolean("aliasSwapped")).isFalse();
   }



   /*******************************************************************************
    ** Test: a tableName input naming an unknown table reindexes nothing.
    *******************************************************************************/
   @Test
   void testSingleTable_unknownTableName_nothingReindexed() throws QException
   {
      insertTestEntities(1);

      new FullReindexStep().run(inputForTable("someOtherTable"), new RunBackendStepOutput());

      verify(mockClient, never()).indexDocuments(anyList(), anyInt());
      verify(mockClient, never()).createPhysicalIndex(anyString());
      assertThat(queryAll(QuickSearchIndexRun.TABLE_NAME)).isEmpty();
   }



   /*******************************************************************************
    ** Test: a disabled index row is skipped by the all-tables rebuild; the
    ** swap still happens for the (empty) set of enabled tables.
    *******************************************************************************/
   @Test
   void testDisabledRow_skippedByAllTablesReindex() throws QException
   {
      insertTestEntities(2);
      insertIndexRow(false);

      new FullReindexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      verify(mockClient, never()).indexDocuments(anyString(), anyList(), anyInt());
      verify(mockClient, never()).indexDocuments(anyList(), anyInt());
      assertThat(queryAll(QuickSearchIndexRun.TABLE_NAME)).isEmpty();
   }



   /*******************************************************************************
    ** Test: a disabled index row is skipped by the single-table reindex too.
    *******************************************************************************/
   @Test
   void testDisabledRow_skippedBySingleTableReindex() throws QException
   {
      insertTestEntities(2);
      insertIndexRow(false);

      RunBackendStepOutput output = new RunBackendStepOutput();
      new FullReindexStep().run(inputForTable(TEST_ENTITY_TABLE), output);

      verify(mockClient, never()).indexDocuments(anyList(), anyInt());
      verify(mockClient, never()).deleteDocumentsIndexedBefore(anyString(), any());
      assertThat(queryAll(QuickSearchIndexRun.TABLE_NAME)).isEmpty();
      assertThat(output.getValueInteger("recordsIndexed")).isEqualTo(0);
   }



   /*******************************************************************************
    ** Test: source records are paged by primary key into the physical index.
    *******************************************************************************/
   @Test
   void testAllTables_pagesByPrimaryKey() throws QException
   {
      QuickSearchQBitContext.getConfig().withSourceBatchSize(2);
      insertTestEntities(5);

      new FullReindexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      verify(mockClient, times(3)).indexDocuments(eq(PHYSICAL_INDEX), anyList(), anyInt());
   }



   /*******************************************************************************
    ** Test: the index row reads REBUILDING while tables are read, and a delete
    ** captured during the rebuild is applied through the alias after the swap,
    ** then its failed-event row is removed.
    *******************************************************************************/
   @Test
   void testAllTables_appliesCapturedDeletesAfterTheSwap() throws QException
   {
      insertTestEntities(3);
      when(mockClient.deleteDocuments(anyList(), anyInt())).thenReturn(new BulkIndexResult().withSuccessCount(1));

      List<String> statusesDuringRebuild = new ArrayList<>();
      new FullReindexStep()
      {
         @Override
         protected IndexCounts indexAllRecords(QuickSearchOpenSearchClient client, QuickSearchableTableConfig tableConfig, List<QFilterCriteria> extraCriteria, String targetIndex, QuickSearchIndexRun run) throws QException
         {
            IndexCounts counts = super.indexAllRecords(client, tableConfig, extraCriteria, targetIndex, run);
            statusesDuringRebuild.add(FullReindexStepTest.this.queryIndexRow().getValueString("status"));
            insertCapturedDelete("7");
            return (counts);
         }
      }.run(new RunBackendStepInput(), new RunBackendStepOutput());

      assertThat(statusesDuringRebuild).containsExactly(AbstractIndexingStep.STATUS_REBUILDING);

      InOrder inOrder = inOrder(mockClient);
      inOrder.verify(mockClient).swapAliasTo(PHYSICAL_INDEX);
      inOrder.verify(mockClient).deleteDocuments(eq(List.of(TEST_ENTITY_TABLE + ":7")), anyInt());

      assertThat(queryAll(QuickSearchFailedEvent.TABLE_NAME)).isEmpty();
      assertThat(queryIndexRow().getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_ACTIVE);
   }



   /*******************************************************************************
    ** Test: a captured delete the cluster rejects is handed to basepull.
    *******************************************************************************/
   @Test
   void testAllTables_capturedDeleteFailure_becomesPendingForBasepull() throws QException
   {
      insertTestEntities(1);
      insertCapturedDelete("9");
      when(mockClient.deleteDocuments(anyList(), anyInt())).thenThrow(new QException("cluster down"));

      new FullReindexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      List<QRecord> rows = queryAll(QuickSearchFailedEvent.TABLE_NAME);
      assertThat(rows).hasSize(1);
      assertThat(rows.get(0).getValueString("status")).isEqualTo(QuickSearchFailedEvent.STATUS_PENDING);
      assertThat(rows.get(0).getValueString("errorMessage")).contains("cluster down");
   }



   /*******************************************************************************
    ** Test: when the rebuild fails before the swap, the previous status comes
    ** back and the captured deletes still leave the AWAITING_REINDEX state.
    *******************************************************************************/
   @Test
   void testAllTables_failureBeforeSwap_restoresStatusAndDrainsCapturedDeletes() throws QException
   {
      insertTestEntities(1);
      insertIndexRow(true);
      updateIndexRowStatus(AbstractIndexingStep.STATUS_NEEDS_REINDEX);
      insertCapturedDelete("4");
      when(mockClient.deleteDocuments(anyList(), anyInt())).thenReturn(new BulkIndexResult().withSuccessCount(1));
      doThrow(new QException("swap refused")).when(mockClient).swapAliasTo(PHYSICAL_INDEX);

      assertThatThrownBy(() -> new FullReindexStep().run(new RunBackendStepInput(), new RunBackendStepOutput()))
         .isInstanceOf(QException.class);

      assertThat(queryIndexRow().getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_NEEDS_REINDEX);
      assertThat(queryAll(QuickSearchFailedEvent.TABLE_NAME)).isEmpty();
      verify(mockClient).deleteDocuments(eq(List.of(TEST_ENTITY_TABLE + ":4")), anyInt());
   }



   /*******************************************************************************
    ** Insert an AWAITING_REINDEX delete for testEntity, as the listener does
    ** during a full reindex.
    *******************************************************************************/
   private void insertCapturedDelete(String recordId) throws QException
   {
      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(QuickSearchFailedEvent.TABLE_NAME);
      insertInput.setRecords(List.of(new QRecord()
         .withValue("tableName", TEST_ENTITY_TABLE)
         .withValue("recordId", recordId)
         .withValue("action", "DELETE")
         .withValue("attempts", 0)
         .withValue("status", QuickSearchFailedEvent.STATUS_AWAITING_REINDEX)));
      new InsertAction().execute(insertInput);
   }



   /*******************************************************************************
    ** Set the status on the testEntity quickSearchIndex row.
    *******************************************************************************/
   private void updateIndexRowStatus(String status) throws QException
   {
      UpdateInput updateInput = new UpdateInput();
      updateInput.setTableName(QuickSearchIndex.TABLE_NAME);
      updateInput.setRecords(List.of(new QRecord().withValue("id", queryIndexRow().getValue("id")).withValue("status", status)));
      new UpdateAction().execute(updateInput);
   }



   /*******************************************************************************
    ** Test: a captured delete for a record re-created with the same key since
    ** is not applied (its document stays), and its row is removed.
    *******************************************************************************/
   @Test
   void testAllTables_capturedDeleteOfRecreatedRecord_isNotApplied() throws QException
   {
      insertTestEntities(3);
      insertCapturedDelete("2");
      insertCapturedDelete("9");
      when(mockClient.deleteDocuments(anyList(), anyInt())).thenReturn(new BulkIndexResult().withSuccessCount(1));

      new FullReindexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      verify(mockClient, times(1)).deleteDocuments(anyList(), anyInt());
      verify(mockClient).deleteDocuments(eq(List.of(TEST_ENTITY_TABLE + ":9")), anyInt());
      assertThat(queryAll(QuickSearchFailedEvent.TABLE_NAME)).isEmpty();
   }



   /*******************************************************************************
    ** Test: rows the delete action reports as not removed (per-row errors, not
    ** an exception) are not read again; the drain moves on and ends.
    *******************************************************************************/
   @Test
   void testAllTables_capturedRowsNotRemoved_drainStillEnds() throws QException
   {
      QuickSearchQBitContext.getConfig().withSourceBatchSize(1);
      insertTestEntities(1);
      insertCapturedDelete("8");
      insertCapturedDelete("9");

      AtomicInteger deleteCalls = new AtomicInteger(0);
      when(mockClient.deleteDocuments(anyList(), anyInt())).thenAnswer(invocation ->
      {
         ///////////////////////////////////////////////////////////////////
         // fail loudly instead of hanging if the same batch is re-read  //
         ///////////////////////////////////////////////////////////////////
         if(deleteCalls.incrementAndGet() > 10)
         {
            throw (new IllegalStateException("drain re-read the same rows"));
         }
         return (new BulkIndexResult().withSuccessCount(1));
      });

      try(MockedConstruction<DeleteAction> deleteActions = mockConstruction(DeleteAction.class, (deleteAction, context) ->
      {
         DeleteOutput output = new DeleteOutput();
         output.setRecordsWithErrors(List.of(new QRecord().withValue("id", 1).withError(new SystemErrorStatusMessage("row is locked"))));
         when(deleteAction.execute(any())).thenReturn(output);
      }))
      {
         new FullReindexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());
      }

      assertThat(deleteCalls.get()).isEqualTo(2);
      assertThat(queryAll(QuickSearchFailedEvent.TABLE_NAME)).extracting(row -> row.getValueString("status"))
         .containsOnly(QuickSearchFailedEvent.STATUS_AWAITING_REINDEX);
   }



   /*******************************************************************************
    ** Test: a batch that fails before it is applied (here, the source lookup)
    ** stays AWAITING_REINDEX, and the later batches are still applied.
    *******************************************************************************/
   @Test
   void testAllTables_failedCapturedBatch_laterBatchesStillApplied() throws QException
   {
      QuickSearchQBitContext.getConfig().withSourceBatchSize(1);
      insertTestEntities(1);
      insertCapturedDelete("8");
      insertCapturedDelete("9");
      when(mockClient.deleteDocuments(anyList(), anyInt())).thenReturn(new BulkIndexResult().withSuccessCount(1));

      new FullReindexStep()
      {
         @Override
         Set<String> queryExistingRecordIds(String tableName, List<QRecord> rows) throws QException
         {
            if("8".equals(rows.get(0).getValueString("recordId")))
            {
               throw (new QException("source table unavailable"));
            }
            return (super.queryExistingRecordIds(tableName, rows));
         }
      }.run(new RunBackendStepInput(), new RunBackendStepOutput());

      verify(mockClient, times(1)).deleteDocuments(anyList(), anyInt());
      verify(mockClient).deleteDocuments(eq(List.of(TEST_ENTITY_TABLE + ":9")), anyInt());

      List<QRecord> rows = queryAll(QuickSearchFailedEvent.TABLE_NAME);
      assertThat(rows).hasSize(1);
      assertThat(rows.get(0).getValueString("recordId")).isEqualTo("8");
      assertThat(rows.get(0).getValueString("status")).isEqualTo(QuickSearchFailedEvent.STATUS_AWAITING_REINDEX);
   }



   /*******************************************************************************
    ** Insert a RUNNING FULL_REINDEX run record for the given index row, started
    ** startedMinutesAgo and last refreshed (heartbeat) heartbeatMinutesAgo.
    *******************************************************************************/
   private QRecord insertRunningRun(Integer quickSearchIndexId, long startedMinutesAgo, long heartbeatMinutesAgo) throws QException
   {
      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(QuickSearchIndexRun.TABLE_NAME);
      insertInput.setRecords(List.of(new QRecord()
         .withValue("quickSearchIndexId", quickSearchIndexId)
         .withValue("runType", FullReindexStep.RUN_TYPE)
         .withValue("status", AbstractIndexingStep.RUN_RUNNING)));
      QRecord run = new InsertAction().execute(insertInput).getRecords().get(0);
      setRunTimes(run.getValueInteger("id"), Instant.now().minus(startedMinutesAgo, ChronoUnit.MINUTES), Instant.now().minus(heartbeatMinutesAgo, ChronoUnit.MINUTES));
      return (queryRun(run.getValueInteger("id")));
   }



   /*******************************************************************************
    ** Move a run record's start and heartbeat two hours into the past, as if
    ** its run had stopped refreshing it.
    *******************************************************************************/
   private void backdateRun(Integer runId) throws QException
   {
      Instant twoHoursAgo = Instant.now().minus(2, ChronoUnit.HOURS);
      setRunTimes(runId, twoHoursAgo, twoHoursAgo);
   }



   /*******************************************************************************
    ** Set a run record's startTime and modifyDate, without QQQ stamping the
    ** modifyDate with the current time.
    *******************************************************************************/
   private void setRunTimes(Integer runId, Instant startTime, Instant modifyDate) throws QException
   {
      UpdateInput updateInput = new UpdateInput();
      updateInput.setTableName(QuickSearchIndexRun.TABLE_NAME);
      updateInput.setOmitModifyDateUpdate(true);
      updateInput.setRecords(List.of(new QRecord().withValue("id", runId).withValue("startTime", startTime).withValue("modifyDate", modifyDate)));
      new UpdateAction().execute(updateInput);
   }



   /*******************************************************************************
    ** The run record with the given id, for lambdas and overrides that cannot
    ** throw a checked exception.
    *******************************************************************************/
   private QRecord queryRunQuietly(Integer runId)
   {
      try
      {
         return (queryRun(runId));
      }
      catch(QException e)
      {
         throw (new IllegalStateException(e));
      }
   }



   /*******************************************************************************
    ** The run record with the given id.
    *******************************************************************************/
   private QRecord queryRun(Integer runId) throws QException
   {
      return (queryAll(QuickSearchIndexRun.TABLE_NAME).stream()
         .filter(run -> runId.equals(run.getValueInteger("id")))
         .findFirst().orElseThrow());
   }



   /*******************************************************************************
    ** Set the status and lastBasepullTime on the testEntity quickSearchIndex row.
    *******************************************************************************/
   private void updateIndexRow(String status, Instant lastBasepullTime) throws QException
   {
      UpdateInput updateInput = new UpdateInput();
      updateInput.setTableName(QuickSearchIndex.TABLE_NAME);
      updateInput.setRecords(List.of(new QRecord().withValue("id", queryIndexRow().getValue("id"))
         .withValue("status", status)
         .withValue("lastBasepullTime", lastBasepullTime)));
      new UpdateAction().execute(updateInput);
   }



   /*******************************************************************************
    ** Test: a full reindex whose run record is fresh blocks a second one, which
    ** fails before it creates anything.
    *******************************************************************************/
   @Test
   void testLiveRun_blocksASecondRun() throws QException
   {
      insertTestEntities(1);
      insertIndexRow(true);
      insertRunningRun(queryIndexRow().getValueInteger("id"), 1, 1);

      assertThatThrownBy(() -> new FullReindexStep().run(new RunBackendStepInput(), new RunBackendStepOutput()))
         .isInstanceOf(QException.class)
         .hasMessageContaining("already running");

      verify(mockClient, never()).createPhysicalIndex(anyString());
      verify(mockClient, never()).indexDocuments(anyString(), anyList(), anyInt());
      assertThat(queryAll(QuickSearchIndexRun.TABLE_NAME)).singleElement()
         .satisfies(run -> assertThat(run.getValueString("status")).isEqualTo(AbstractIndexingStep.RUN_RUNNING));
   }



   /*******************************************************************************
    ** Test: a live full reindex also blocks a single-table full reindex.
    *******************************************************************************/
   @Test
   void testLiveRun_blocksASingleTableRun() throws QException
   {
      insertTestEntities(1);
      insertIndexRow(true);
      insertRunningRun(queryIndexRow().getValueInteger("id"), 1, 1);

      assertThatThrownBy(() -> new FullReindexStep().run(inputForTable(TEST_ENTITY_TABLE), new RunBackendStepOutput()))
         .isInstanceOf(QException.class)
         .hasMessageContaining("already running");

      verify(mockClient, never()).indexDocuments(anyList(), anyInt());
      assertThat(queryAll(QuickSearchIndexRun.TABLE_NAME)).hasSize(1);
   }



   /*******************************************************************************
    ** Test: a dead run (no heartbeat past the threshold) does not block a new
    ** one; it is recovered first, and the deletes it captured wait for the new
    ** run, which applies them after its swap.
    *******************************************************************************/
   @Test
   void testStaleRun_doesNotBlock_andIsRecovered() throws QException
   {
      insertTestEntities(2);
      insertIndexRow(true);
      updateIndexRowStatus(AbstractIndexingStep.STATUS_REBUILDING);
      Integer staleRunId = insertRunningRun(queryIndexRow().getValueInteger("id"), 120, 120).getValueInteger("id");
      insertCapturedDelete("9");
      when(mockClient.deleteDocuments(anyList(), anyInt())).thenReturn(new BulkIndexResult().withSuccessCount(1));

      RunBackendStepOutput output = new RunBackendStepOutput();
      new FullReindexStep().run(new RunBackendStepInput(), output);

      assertThat(output.getValueBoolean("aliasSwapped")).isTrue();
      assertThat(queryRun(staleRunId).getValueString("status")).isEqualTo(AbstractIndexingStep.RUN_FAILED);
      assertThat(queryRun(staleRunId).getValueString("errorMessage")).contains("stopped without finishing");
      assertThat(queryIndexRow().getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_ACTIVE);

      InOrder inOrder = inOrder(mockClient);
      inOrder.verify(mockClient).swapAliasTo(PHYSICAL_INDEX);
      inOrder.verify(mockClient).deleteDocuments(eq(List.of(TEST_ENTITY_TABLE + ":9")), anyInt());
      assertThat(queryAll(QuickSearchFailedEvent.TABLE_NAME)).isEmpty();
   }



   /*******************************************************************************
    ** Test: the run records stay RUNNING while the captured deletes are applied
    ** after the swap, so a basepull meanwhile leaves those rows to this run.
    *******************************************************************************/
   @Test
   void testAllTables_runStaysRunningUntilCapturedDeletesAreApplied() throws QException
   {
      insertTestEntities(1);
      insertCapturedDelete("9");

      List<String> runStatusesWhileApplying = new ArrayList<>();
      when(mockClient.deleteDocuments(anyList(), anyInt())).thenAnswer(invocation ->
      {
         queryAll(QuickSearchIndexRun.TABLE_NAME).forEach(run -> runStatusesWhileApplying.add(run.getValueString("status")));
         return (new BulkIndexResult().withSuccessCount(1));
      });

      new FullReindexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      assertThat(runStatusesWhileApplying).containsExactly(AbstractIndexingStep.RUN_RUNNING);
      assertThat(queryAll(QuickSearchIndexRun.TABLE_NAME)).singleElement()
         .satisfies(run -> assertThat(run.getValueString("status")).isEqualTo(AbstractIndexingStep.RUN_COMPLETED));
   }



   /*******************************************************************************
    ** Test: the run record's modifyDate is refreshed after every page, so a
    ** long rebuild keeps showing it is alive.
    *******************************************************************************/
   @Test
   void testHeartbeat_refreshesTheRunRecordAfterEachPage() throws QException
   {
      QuickSearchQBitContext.getConfig().withSourceBatchSize(2);
      insertTestEntities(5);
      insertIndexRow(true);
      Integer runId = insertRunningRun(queryIndexRow().getValueInteger("id"), 120, 120).getValueInteger("id");

      AtomicInteger pagesIndexed = new AtomicInteger(0);
      when(mockClient.indexDocuments(anyString(), anyList(), anyInt())).thenAnswer(invocation ->
      {
         pagesIndexed.incrementAndGet();
         return (new BulkIndexResult().withSuccessCount(((List<?>) invocation.getArgument(1)).size()));
      });

      List<Integer>   pagesIndexedAtEachHeartbeat = new ArrayList<>();
      List<Instant>   heartbeatsWritten           = new ArrayList<>();
      FullReindexStep step                        = new FullReindexStep()
      {
         @Override
         protected void heartbeat(QuickSearchIndexRun run)
         {
            super.heartbeat(run);
            pagesIndexedAtEachHeartbeat.add(pagesIndexed.get());
            heartbeatsWritten.add(queryRunQuietly(runId).getValueInstant("modifyDate"));
         }
      };

      Instant before = Instant.now();
      step.indexAllRecords(mockClient, QuickSearchQBitContext.getTableConfig(TEST_ENTITY_TABLE), List.of(), PHYSICAL_INDEX,
         new QuickSearchIndexRun().withId(runId).withRunType(FullReindexStep.RUN_TYPE));

      assertThat(pagesIndexedAtEachHeartbeat).containsExactly(1, 2, 3);
      assertThat(heartbeatsWritten).hasSize(3).allSatisfy(heartbeat -> assertThat(heartbeat).isAfterOrEqualTo(before));
      assertThat(heartbeatsWritten).isSorted();
   }



   /*******************************************************************************
    ** Test: only FULL_REINDEX runs have a heartbeat (nothing reads one for the
    ** other run types).
    *******************************************************************************/
   @Test
   void testHeartbeat_onlyForFullReindexRuns() throws QException
   {
      insertIndexRow(true);
      QRecord run = insertRunningRun(queryIndexRow().getValueInteger("id"), 120, 120);

      new FullReindexStep().heartbeat(new QuickSearchIndexRun().withId(run.getValueInteger("id")).withRunType(BasepullIndexStep.RUN_TYPE));
      assertThat(queryRun(run.getValueInteger("id")).getValueInstant("modifyDate")).isEqualTo(run.getValueInstant("modifyDate"));

      new FullReindexStep().heartbeat(new QuickSearchIndexRun().withId(run.getValueInteger("id")).withRunType(FullReindexStep.RUN_TYPE));
      assertThat(queryRun(run.getValueInteger("id")).getValueInstant("modifyDate")).isAfter(run.getValueInstant("modifyDate"));
   }



   /*******************************************************************************
    ** Test: a heartbeat that cannot be written is logged and the pages are
    ** still indexed.
    *******************************************************************************/
   @Test
   void testHeartbeat_failureDoesNotStopIndexing() throws QException
   {
      QuickSearchQBitContext.getConfig().withSourceBatchSize(2);
      insertTestEntities(3);

      AbstractIndexingStep.IndexCounts counts;
      try(MockedConstruction<UpdateAction> updateActions = mockConstruction(UpdateAction.class, (updateAction, context) ->
         when(updateAction.execute(any())).thenThrow(new QException("database unavailable"))))
      {
         counts = new FullReindexStep().indexAllRecords(mockClient, QuickSearchQBitContext.getTableConfig(TEST_ENTITY_TABLE), List.of(), PHYSICAL_INDEX,
            new QuickSearchIndexRun().withId(1).withRunType(FullReindexStep.RUN_TYPE));
         assertThat(updateActions.constructed()).hasSize(2);
      }

      assertThat(counts.processed()).isEqualTo(3);
      assertThat(counts.indexed()).isEqualTo(3);
   }



   /*******************************************************************************
    ** Test: a run that stalls past the threshold is recovered by another node
    ** while still going. It notices before its swap and stops without swapping
    ** and without touching the tables or the captured deletes, which the
    ** recovery already handed to basepull.
    *******************************************************************************/
   @Test
   void testRunJudgedDead_recoveredWhileGoing_doesNotSwap() throws QException
   {
      insertTestEntities(1);

      assertThatThrownBy(() -> new FullReindexStep()
      {
         @Override
         protected IndexCounts indexAllRecords(QuickSearchOpenSearchClient client, QuickSearchableTableConfig tableConfig, List<QFilterCriteria> extraCriteria, String targetIndex, QuickSearchIndexRun run) throws QException
         {
            IndexCounts counts = super.indexAllRecords(client, tableConfig, extraCriteria, targetIndex, run);
            insertCapturedDelete("7");
            backdateRun(run.getId());
            assertThat(new FullReindexStep().recoverDeadRun(true)).isEmpty();
            return (counts);
         }
      }.run(new RunBackendStepInput(), new RunBackendStepOutput()))
         .isInstanceOf(QException.class)
         .hasMessageContaining("recovered");

      verify(mockClient, never()).swapAliasTo(anyString());
      verify(mockClient).deletePhysicalIndex(PHYSICAL_INDEX);
      verify(mockClient, never()).deleteDocuments(anyList(), anyInt());
      assertThat(queryIndexRow().getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_ACTIVE);
      assertThat(queryAll(QuickSearchFailedEvent.TABLE_NAME)).singleElement()
         .satisfies(row -> assertThat(row.getValueString("status")).isEqualTo(QuickSearchFailedEvent.STATUS_PENDING));
   }



   /*******************************************************************************
    ** Test: a table that is no longer REBUILDING before the swap (a recovery
    ** that read the run records just before this run created its own put it
    ** back to ACTIVE) means a delete since may have reached only the old index,
    ** so the run fails without swapping and applies its captured deletes to the
    ** old index.
    *******************************************************************************/
   @Test
   void testTableNoLongerRebuildingBeforeTheSwap_doesNotSwap() throws QException
   {
      insertTestEntities(1);
      when(mockClient.deleteDocuments(anyList(), anyInt())).thenReturn(new BulkIndexResult().withSuccessCount(1));

      assertThatThrownBy(() -> new FullReindexStep()
      {
         @Override
         protected IndexCounts indexAllRecords(QuickSearchOpenSearchClient client, QuickSearchableTableConfig tableConfig, List<QFilterCriteria> extraCriteria, String targetIndex, QuickSearchIndexRun run) throws QException
         {
            IndexCounts counts = super.indexAllRecords(client, tableConfig, extraCriteria, targetIndex, run);
            insertCapturedDelete("7");
            updateIndexRowStatus(AbstractIndexingStep.STATUS_ACTIVE);
            return (counts);
         }
      }.run(new RunBackendStepInput(), new RunBackendStepOutput()))
         .isInstanceOf(QException.class)
         .hasMessageContaining("no longer REBUILDING");

      verify(mockClient, never()).swapAliasTo(anyString());
      verify(mockClient).deleteDocuments(eq(List.of(TEST_ENTITY_TABLE + ":7")), anyInt());
      assertThat(queryAll(QuickSearchFailedEvent.TABLE_NAME)).isEmpty();
      assertThat(queryAll(QuickSearchIndexRun.TABLE_NAME)).singleElement()
         .satisfies(run -> assertThat(run.getValueString("status")).isEqualTo(AbstractIndexingStep.RUN_FAILED));
   }



   /*******************************************************************************
    ** Test: when another node starts a full reindex at the same moment (after
    ** this one checked, before it created its run records), this one sees the
    ** other's fresh run record and stops before it touches anything.
    *******************************************************************************/
   @Test
   void testSimultaneousStart_secondRunStopsBeforeMarking() throws QException
   {
      insertTestEntities(1);
      AtomicBoolean otherStarted = new AtomicBoolean(false);

      assertThatThrownBy(() -> new FullReindexStep()
      {
         @Override
         protected QuickSearchIndexRun createRunRecord(Integer indexId, String runType) throws QException
         {
            if(otherStarted.compareAndSet(false, true))
            {
               insertRunningRun(indexId, 0, 0);
            }
            return (super.createRunRecord(indexId, runType));
         }
      }.run(new RunBackendStepInput(), new RunBackendStepOutput()))
         .isInstanceOf(QException.class)
         .hasMessageContaining("already running");

      verify(mockClient, never()).createPhysicalIndex(anyString());
      verify(mockClient, never()).swapAliasTo(anyString());
      assertThat(queryIndexRow().getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_ACTIVE);
      assertThat(queryAll(QuickSearchIndexRun.TABLE_NAME)).extracting(run -> run.getValueString("status"))
         .containsExactlyInAnyOrder(AbstractIndexingStep.RUN_RUNNING, AbstractIndexingStep.RUN_FAILED);
   }



   /*******************************************************************************
    ** Test: recovering a dead run marks its record FAILED, puts the table back
    ** to ACTIVE with the basepull watermark rewound to the run's start, and
    ** hands the captured deletes to basepull.
    *******************************************************************************/
   @Test
   void testRecovery_deadRun_restoresTableAndHandsCapturedDeletesToBasepull() throws QException
   {
      insertIndexRow(true);
      updateIndexRow(AbstractIndexingStep.STATUS_REBUILDING, Instant.now().minus(5, ChronoUnit.MINUTES));
      QRecord deadRun = insertRunningRun(queryIndexRow().getValueInteger("id"), 120, 90);
      insertCapturedDelete("4");
      insertCapturedDelete("5");

      assertThat(new FullReindexStep().recoverDeadRun(true)).isEmpty();

      QRecord row = queryIndexRow();
      assertThat(row.getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_ACTIVE);
      assertThat(row.getValueInstant("lastBasepullTime")).isEqualTo(deadRun.getValueInstant("startTime"));
      assertThat(row.getValueString("lastRunStatus")).isEqualTo("FULL_REINDEX FAILED");
      assertThat(row.getValueString("lastErrorMessage")).contains("stopped without finishing");

      QRecord run = queryRun(deadRun.getValueInteger("id"));
      assertThat(run.getValueString("status")).isEqualTo(AbstractIndexingStep.RUN_FAILED);
      assertThat(run.getValue("endTime")).isNotNull();

      assertThat(queryAll(QuickSearchFailedEvent.TABLE_NAME)).extracting(event -> event.getValueString("status"))
         .containsExactly(QuickSearchFailedEvent.STATUS_PENDING, QuickSearchFailedEvent.STATUS_PENDING);
   }



   /*******************************************************************************
    ** Test: a run started long ago but with a recent heartbeat is alive;
    ** recovery returns it and leaves its table, run record and captured
    ** deletes alone.
    *******************************************************************************/
   @Test
   void testRecovery_liveRun_leavesItsTableAndRowsAlone() throws QException
   {
      insertIndexRow(true);
      updateIndexRowStatus(AbstractIndexingStep.STATUS_REBUILDING);
      QRecord liveRun = insertRunningRun(queryIndexRow().getValueInteger("id"), 180, 1);
      insertCapturedDelete("4");

      assertThat(new FullReindexStep().recoverDeadRun(true)).extracting(run -> run.getValueInteger("id"))
         .containsExactly(liveRun.getValueInteger("id"));

      assertThat(queryIndexRow().getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_REBUILDING);
      assertThat(queryRun(liveRun.getValueInteger("id")).getValueString("status")).isEqualTo(AbstractIndexingStep.RUN_RUNNING);
      assertThat(queryAll(QuickSearchFailedEvent.TABLE_NAME)).singleElement()
         .satisfies(event -> assertThat(event.getValueString("status")).isEqualTo(QuickSearchFailedEvent.STATUS_AWAITING_REINDEX));
   }



   /*******************************************************************************
    ** Test: a REBUILDING table whose run record is gone (purged) is recovered
    ** too, without moving its basepull watermark.
    *******************************************************************************/
   @Test
   void testRecovery_withoutRunRecord_restoresTheTable() throws QException
   {
      Instant lastBasepullTime = Instant.now().minus(5, ChronoUnit.MINUTES);
      insertIndexRow(true);
      updateIndexRow(AbstractIndexingStep.STATUS_REBUILDING, lastBasepullTime);
      insertCapturedDelete("4");

      assertThat(new FullReindexStep().recoverDeadRun(true)).isEmpty();

      assertThat(queryIndexRow().getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_ACTIVE);
      assertThat(queryIndexRow().getValueInstant("lastBasepullTime")).isEqualTo(lastBasepullTime);
      assertThat(queryAll(QuickSearchFailedEvent.TABLE_NAME)).singleElement()
         .satisfies(event -> assertThat(event.getValueString("status")).isEqualTo(QuickSearchFailedEvent.STATUS_PENDING));
   }



   /*******************************************************************************
    ** Test: recovery hands over only the captured rows that existed when it
    ** read them; a row captured after (by a run that started meanwhile) stays
    ** AWAITING_REINDEX for that run.
    *******************************************************************************/
   @Test
   void testRecovery_handsOverOnlyRowsCapturedBeforeItRead() throws QException
   {
      insertIndexRow(true);
      updateIndexRowStatus(AbstractIndexingStep.STATUS_REBUILDING);
      insertRunningRun(queryIndexRow().getValueInteger("id"), 120, 120);
      insertCapturedDelete("4");

      AtomicBoolean capturedMeanwhile = new AtomicBoolean(false);
      new FullReindexStep()
      {
         @Override
         protected void updateIndexRow(Integer indexId, Map<String, ? extends Serializable> values) throws QException
         {
            super.updateIndexRow(indexId, values);
            if(capturedMeanwhile.compareAndSet(false, true))
            {
               insertCapturedDelete("5");
            }
         }
      }.recoverDeadRun(true);

      assertThat(queryAll(QuickSearchFailedEvent.TABLE_NAME)).extracting(event -> event.getValueString("recordId") + " " + event.getValueString("status"))
         .containsExactlyInAnyOrder("4 " + QuickSearchFailedEvent.STATUS_PENDING, "5 " + QuickSearchFailedEvent.STATUS_AWAITING_REINDEX);
   }



   /*******************************************************************************
    ** Test: a recovery that fails while putting a table back leaves the dead
    ** run's record RUNNING, so the next recovery still finds the run's start
    ** and rewinds the basepull watermark.
    *******************************************************************************/
   @Test
   void testRecovery_failsPartway_nextRecoveryStillRewinds() throws QException
   {
      insertIndexRow(true);
      updateIndexRow(AbstractIndexingStep.STATUS_REBUILDING, Instant.now().minus(5, ChronoUnit.MINUTES));
      QRecord deadRun = insertRunningRun(queryIndexRow().getValueInteger("id"), 120, 120);

      assertThatThrownBy(() -> new FullReindexStep()
      {
         @Override
         protected void updateIndexRow(Integer indexId, Map<String, ? extends Serializable> values) throws QException
         {
            throw (new QException("database unavailable"));
         }
      }.recoverDeadRun(true))
         .isInstanceOf(QException.class)
         .hasMessageContaining("database unavailable");

      assertThat(queryRun(deadRun.getValueInteger("id")).getValueString("status")).isEqualTo(AbstractIndexingStep.RUN_RUNNING);
      assertThat(queryIndexRow().getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_REBUILDING);

      assertThat(new FullReindexStep().recoverDeadRun(true)).isEmpty();

      assertThat(queryIndexRow().getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_ACTIVE);
      assertThat(queryIndexRow().getValueInstant("lastBasepullTime")).isEqualTo(deadRun.getValueInstant("startTime"));
      assertThat(queryRun(deadRun.getValueInteger("id")).getValueString("status")).isEqualTo(AbstractIndexingStep.RUN_FAILED);
   }



   /*******************************************************************************
    ** Test: a run whose drain of captured deletes is so slow that it is judged
    ** stopped, while a new full reindex starts, marks the table and captures a
    ** delete of its own: the drain stops at its next batch and leaves both its
    ** remaining row and the new run's row alone.
    *******************************************************************************/
   @Test
   void testDrainTakenOverMidway_leavesTheNewRunsRowsAlone() throws QException
   {
      QuickSearchQBitContext.getConfig().withSourceBatchSize(1);
      insertTestEntities(1);
      insertCapturedDelete("8");
      insertCapturedDelete("9");

      AtomicBoolean newRunStarted = new AtomicBoolean(false);
      when(mockClient.deleteDocuments(anyList(), anyInt())).thenAnswer(invocation ->
      {
         if(newRunStarted.compareAndSet(false, true))
         {
            for(QRecord run : queryAll(QuickSearchIndexRun.TABLE_NAME))
            {
               backdateRun(run.getValueInteger("id"));
            }
            assertThat(new FullReindexStep().recoverDeadRun(false)).isEmpty();
            insertRunningRun(queryIndexRow().getValueInteger("id"), 0, 0);
            updateIndexRowStatus(AbstractIndexingStep.STATUS_REBUILDING);
            insertCapturedDelete("7");
         }
         return (new BulkIndexResult().withSuccessCount(1));
      });

      new FullReindexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      verify(mockClient, times(1)).deleteDocuments(anyList(), anyInt());
      verify(mockClient).deleteDocuments(eq(List.of(TEST_ENTITY_TABLE + ":8")), anyInt());
      assertThat(queryAll(QuickSearchFailedEvent.TABLE_NAME)).extracting(event -> event.getValueString("recordId") + " " + event.getValueString("status"))
         .containsExactlyInAnyOrder("9 " + QuickSearchFailedEvent.STATUS_AWAITING_REINDEX, "7 " + QuickSearchFailedEvent.STATUS_AWAITING_REINDEX);
      assertThat(queryIndexRow().getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_REBUILDING);
   }



   /*******************************************************************************
    ** Test: the drain applies only rows up to the newest one at its start; a
    ** row captured while it runs is left alone.
    *******************************************************************************/
   @Test
   void testDrain_stopsAtTheNewestRowReadAtItsStart() throws QException
   {
      QuickSearchQBitContext.getConfig().withSourceBatchSize(1);
      insertTestEntities(1);
      insertCapturedDelete("8");
      insertCapturedDelete("9");

      AtomicBoolean capturedMeanwhile = new AtomicBoolean(false);
      when(mockClient.deleteDocuments(anyList(), anyInt())).thenAnswer(invocation ->
      {
         if(capturedMeanwhile.compareAndSet(false, true))
         {
            insertCapturedDelete("7");
         }
         return (new BulkIndexResult().withSuccessCount(1));
      });

      new FullReindexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      verify(mockClient, times(2)).deleteDocuments(anyList(), anyInt());
      assertThat(queryAll(QuickSearchFailedEvent.TABLE_NAME)).singleElement()
         .satisfies(event -> assertThat(event.getValueString("recordId")).isEqualTo("7"));
   }



   /*******************************************************************************
    ** Test: when the stale run records cannot be marked FAILED (every update
    ** comes back with a per-row error), recovery fails and the new full
    ** reindex does not start, since a live run misjudged as stopped would not
    ** learn it was taken over.
    *******************************************************************************/
   @Test
   void testRecovery_failedRunRecordsNotWritten_newRunDoesNotStart() throws QException
   {
      insertTestEntities(1);
      insertIndexRow(true);
      updateIndexRowStatus(AbstractIndexingStep.STATUS_REBUILDING);
      Integer staleRunId = insertRunningRun(queryIndexRow().getValueInteger("id"), 120, 120).getValueInteger("id");

      try(MockedConstruction<UpdateAction> updateActions = mockConstruction(UpdateAction.class, (updateAction, context) ->
         when(updateAction.execute(any())).thenAnswer(invocation ->
         {
            UpdateInput  updateInput  = invocation.getArgument(0);
            UpdateOutput updateOutput = new UpdateOutput();
            updateOutput.setRecords(updateInput.getRecords().stream().map(record -> new QRecord(record).withError(new SystemErrorStatusMessage("row is locked"))).toList());
            return (updateOutput);
         })))
      {
         assertThatThrownBy(() -> new FullReindexStep().run(new RunBackendStepInput(), new RunBackendStepOutput()))
            .isInstanceOf(QException.class)
            .hasMessageContaining("FAILED")
            .hasMessageContaining("row is locked");
      }

      verify(mockClient, never()).createPhysicalIndex(anyString());
      verify(mockClient, never()).indexDocuments(anyString(), anyList(), anyInt());
      assertThat(queryAll(QuickSearchIndexRun.TABLE_NAME)).singleElement()
         .satisfies(run ->
         {
            assertThat(run.getValueInteger("id")).isEqualTo(staleRunId);
            assertThat(run.getValueString("status")).isEqualTo(AbstractIndexingStep.RUN_RUNNING);
         });
   }



   /*******************************************************************************
    ** Test: the run keeps itself alive at every point where it could otherwise
    ** go quiet: after each page, right before its pre-swap ownership check,
    ** before each table's update after the swap, and before each batch of the
    ** captured-delete drain.
    *******************************************************************************/
   @Test
   void testAllTables_heartbeatsBeforeTheOwnershipCheckEachTableAndEachDrainBatch() throws QException
   {
      QuickSearchQBitContext.getConfig().withSourceBatchSize(1);
      insertTestEntities(1);
      insertCapturedDelete("8");
      insertCapturedDelete("9");

      List<String> events = new ArrayList<>();
      doAnswer(invocation ->
      {
         events.add("refresh");
         return (null);
      }).when(mockClient).refreshIndex(PHYSICAL_INDEX);
      doAnswer(invocation ->
      {
         events.add("swap");
         return (null);
      }).when(mockClient).swapAliasTo(PHYSICAL_INDEX);
      when(mockClient.deleteDocuments(anyList(), anyInt())).thenAnswer(invocation ->
      {
         events.add("drainBatch");
         return (new BulkIndexResult().withSuccessCount(1));
      });

      new FullReindexStep()
      {
         @Override
         protected void heartbeat(QuickSearchIndexRun run)
         {
            events.add("heartbeat");
            super.heartbeat(run);
         }



         @Override
         protected void updateIndexRow(Integer indexId, Map<String, ? extends Serializable> values) throws QException
         {
            if(values.containsKey("lastFullReindexTime"))
            {
               events.add("tableDone");
            }
            super.updateIndexRow(indexId, values);
         }
      }.run(new RunBackendStepInput(), new RunBackendStepOutput());

      assertThat(events).containsExactly(
         "heartbeat",
         "refresh", "heartbeat", "swap",
         "heartbeat", "tableDone",
         "heartbeat", "drainBatch", "heartbeat", "drainBatch", "heartbeat");
   }



   /*******************************************************************************
    ** Test: when a run fails and cannot read whether a recovery took it over,
    ** it leaves its run records RUNNING and its tables REBUILDING, so the next
    ** recovery (once the records are stale) puts everything back, watermark
    ** rewind included.
    *******************************************************************************/
   @Test
   void testFailure_takeoverUnreadable_leavesRunRecordsRunningForRecovery() throws QException
   {
      insertTestEntities(1);
      AtomicBoolean databaseDown = new AtomicBoolean(false);
      when(mockClient.indexDocuments(anyString(), anyList(), anyInt())).thenAnswer(invocation ->
      {
         databaseDown.set(true);
         throw (new QException("bulk failed"));
      });

      assertThatThrownBy(() -> new FullReindexStep()
      {
         @Override
         protected QuickSearchQBitConfig getConfig() throws QException
         {
            ///////////////////////////////////////////////////////////////////
            // only the first read after the failure (the takeover check) is //
            // lost; everything after it would work                          //
            ///////////////////////////////////////////////////////////////////
            if(databaseDown.getAndSet(false))
            {
               throw (new QException("database unavailable"));
            }
            return (super.getConfig());
         }
      }.run(new RunBackendStepInput(), new RunBackendStepOutput()))
         .isInstanceOf(QException.class)
         .hasMessageContaining("bulk failed");

      verify(mockClient).deletePhysicalIndex(PHYSICAL_INDEX);
      assertThat(queryIndexRow().getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_REBUILDING);
      QRecord run = queryAll(QuickSearchIndexRun.TABLE_NAME).get(0);
      assertThat(run.getValueString("status")).isEqualTo(AbstractIndexingStep.RUN_RUNNING);

      backdateRun(run.getValueInteger("id"));
      assertThat(new FullReindexStep().recoverDeadRun(true)).isEmpty();
      assertThat(queryIndexRow().getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_ACTIVE);
      assertThat(queryRun(run.getValueInteger("id")).getValueString("status")).isEqualTo(AbstractIndexingStep.RUN_FAILED);
   }



   /*******************************************************************************
    ** Test: with nothing left behind, recovery changes nothing.
    *******************************************************************************/
   @Test
   void testRecovery_nothingLeftBehind_changesNothing() throws QException
   {
      insertIndexRow(true);

      assertThat(new FullReindexStep().recoverDeadRun(true)).isEmpty();

      assertThat(queryIndexRow().getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_ACTIVE);
      assertThat(queryIndexRow().getValueString("lastRunStatus")).isNull();
      assertThat(queryAll(QuickSearchIndexRun.TABLE_NAME)).isEmpty();
   }

}

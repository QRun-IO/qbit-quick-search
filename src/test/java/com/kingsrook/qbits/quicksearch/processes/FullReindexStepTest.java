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


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.statusmessages.SystemErrorStatusMessage;
import com.kingsrook.qbits.quicksearch.BaseQuickSearchTest;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitContext;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;
import com.kingsrook.qbits.quicksearch.opensearch.BulkIndexResult;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
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
      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(QuickSearchIndex.TABLE_NAME);
      insertInput.setRecords(List.of(new QRecord()
         .withValue("tableName", TEST_ENTITY_TABLE)
         .withValue("enabled", enabled)
         .withValue("basepullIntervalMinutes", 5)
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
         protected IndexCounts indexAllRecords(QuickSearchOpenSearchClient client, QuickSearchableTableConfig tableConfig, List<QFilterCriteria> extraCriteria, String targetIndex) throws QException
         {
            IndexCounts counts = super.indexAllRecords(client, tableConfig, extraCriteria, targetIndex);
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

}

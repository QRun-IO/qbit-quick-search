/*
 * QBit Quick Search
 * Copyright (C) 2024-2025 QRun-IO, LLC
 * https://www.qrun.io | https://github.com/QRun-IO
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.kingsrook.qbits.quicksearch.processes;


import java.util.List;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryOutput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qbits.quicksearch.BaseQuickSearchTest;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitContext;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;
import com.kingsrook.qbits.quicksearch.opensearch.BulkIndexResult;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/*******************************************************************************
 ** Tests for FullReindexStep.
 **
 ** Verifies that:
 ** - All tables are reindexed when no tableName input is provided
 ** - Only the specified table is reindexed when tableName input is provided
 ** - An empty table results in a run record with 0 records processed
 ** - QuickSearchIndex rows are lazily created if they do not yet exist
 ** - A failed indexDocuments() call results in a FAILED run record
 ** - lastFullReindexTime is updated on the QuickSearchIndex row after reindex
 *******************************************************************************/
class FullReindexStepTest extends BaseQuickSearchTest
{

   /*******************************************************************************
    ** Helper: insert source records into the testEntity table.
    *******************************************************************************/
   private void insertTestEntities(int count) throws QException
   {
      List<QRecord> records = new java.util.ArrayList<>();
      for(int i = 1; i <= count; i++)
      {
         records.add(new QRecord()
            .withValue("name", "Entity " + i)
            .withValue("description", "Description " + i));
      }

      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(TEST_ENTITY_TABLE);
      insertInput.setRecords(records);

      new InsertAction().execute(insertInput);
   }



   /*******************************************************************************
    ** Helper: build a RunBackendStepInput with no special values.
    *******************************************************************************/
   private RunBackendStepInput buildEmptyInput()
   {
      return (new RunBackendStepInput());
   }



   /*******************************************************************************
    ** Helper: build a RunBackendStepInput with a tableName filter.
    *******************************************************************************/
   private RunBackendStepInput buildInputWithTableName(String tableName)
   {
      RunBackendStepInput input = new RunBackendStepInput();
      input.addValue("tableName", tableName);
      return (input);
   }



   /*******************************************************************************
    ** Helper: query all records from quickSearchIndexRun.
    *******************************************************************************/
   private List<QRecord> queryAllRunRecords() throws QException
   {
      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(QuickSearchIndexRun.TABLE_NAME);
      queryInput.setFilter(new QQueryFilter());

      QueryOutput queryOutput = new QueryAction().execute(queryInput);
      return (queryOutput.getRecords());
   }



   /*******************************************************************************
    ** Helper: query all records from quickSearchIndex.
    *******************************************************************************/
   private List<QRecord> queryAllIndexRecords() throws QException
   {
      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(QuickSearchIndex.TABLE_NAME);
      queryInput.setFilter(new QQueryFilter());

      QueryOutput queryOutput = new QueryAction().execute(queryInput);
      return (queryOutput.getRecords());
   }



   /*******************************************************************************
    ** Helper: query a quickSearchIndex record by tableName.
    *******************************************************************************/
   private QRecord queryIndexByTableName(String tableName) throws QException
   {
      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(QuickSearchIndex.TABLE_NAME);
      queryInput.setFilter(new QQueryFilter()
         .withCriteria(new QFilterCriteria("tableName", QCriteriaOperator.EQUALS, tableName)));

      QueryOutput queryOutput = new QueryAction().execute(queryInput);
      List<QRecord> records = queryOutput.getRecords();
      return (records.isEmpty() ? null : records.get(0));
   }



   /*******************************************************************************
    ** Test: reindex all tables when no tableName input is given.
    **
    ** With one discovered table (testEntity) and source records present,
    ** deleteDocumentsForTable() and indexDocuments() should each be called once.
    *******************************************************************************/
   @Test
   void testReindexAllTables_noTableNameInput_reindexesAllDiscoveredTables() throws QException
   {
      insertTestEntities(3);

      QuickSearchOpenSearchClient mockClient = mock(QuickSearchOpenSearchClient.class);
      when(mockClient.indexDocuments(any(), anyInt())).thenReturn(new BulkIndexResult().withSuccessCount(3));
      QuickSearchQBitContext.setClient(mockClient);

      FullReindexStep step = new FullReindexStep();
      step.run(buildEmptyInput(), new RunBackendStepOutput());

      verify(mockClient, times(1)).deleteDocumentsForTable(TEST_ENTITY_TABLE);
      verify(mockClient, times(1)).indexDocuments(any(), anyInt());
   }



   /*******************************************************************************
    ** Test: reindex only the specified table when tableName input is provided.
    **
    ** Registers two discovered tables, passes tableName=testEntity.
    ** Only testEntity should be reindexed; the other table should be skipped.
    *******************************************************************************/
   @Test
   void testReindexSingleTable_tableNameInputProvided_onlyThatTableReindexed() throws QException
   {
      insertTestEntities(2);

      QuickSearchOpenSearchClient mockClient = mock(QuickSearchOpenSearchClient.class);
      when(mockClient.indexDocuments(any(), anyInt())).thenReturn(new BulkIndexResult().withSuccessCount(2));
      QuickSearchQBitContext.setClient(mockClient);

      FullReindexStep step = new FullReindexStep();
      step.run(buildInputWithTableName(TEST_ENTITY_TABLE), new RunBackendStepOutput());

      verify(mockClient, times(1)).deleteDocumentsForTable(TEST_ENTITY_TABLE);
      verify(mockClient, never()).deleteDocumentsForTable("someOtherTable");
   }



   /*******************************************************************************
    ** Test: empty source table produces a run record with 0 records processed.
    *******************************************************************************/
   @Test
   void testEmptyTable_noSourceRecords_runRecordCreatedWithZeroProcessed() throws QException
   {
      QuickSearchOpenSearchClient mockClient = mock(QuickSearchOpenSearchClient.class);
      when(mockClient.indexDocuments(any(), anyInt())).thenReturn(new BulkIndexResult());
      QuickSearchQBitContext.setClient(mockClient);

      FullReindexStep step = new FullReindexStep();
      step.run(buildEmptyInput(), new RunBackendStepOutput());

      List<QRecord> runRecords = queryAllRunRecords();
      assertThat(runRecords).hasSize(1);

      QRecord runRecord = runRecords.get(0);
      assertThat(runRecord.getValueString("status")).isEqualTo("SUCCESS");
      assertThat(runRecord.getValueInteger("recordsProcessed")).isEqualTo(0);
      assertThat(runRecord.getValueInteger("recordsIndexed")).isEqualTo(0);

      verify(mockClient, times(1)).deleteDocumentsForTable(TEST_ENTITY_TABLE);
      verify(mockClient, never()).indexDocuments(any(), anyInt());
   }



   /*******************************************************************************
    ** Test: QuickSearchIndex row is lazily created if it does not exist yet.
    **
    ** No pre-existing index rows.  After run(), a quickSearchIndex row for
    ** testEntity should be present.
    *******************************************************************************/
   @Test
   void testLazyIndexRowCreation_noExistingIndexRow_createdBeforeReindex() throws QException
   {
      QuickSearchOpenSearchClient mockClient = mock(QuickSearchOpenSearchClient.class);
      when(mockClient.indexDocuments(any(), anyInt())).thenReturn(new BulkIndexResult());
      QuickSearchQBitContext.setClient(mockClient);

      ////////////////////////////////////////////////////
      // Verify no rows exist before run                //
      ////////////////////////////////////////////////////
      assertThat(queryAllIndexRecords()).isEmpty();

      FullReindexStep step = new FullReindexStep();
      step.run(buildEmptyInput(), new RunBackendStepOutput());

      ////////////////////////////////////////////////////
      // Verify index row was created                   //
      ////////////////////////////////////////////////////
      List<QRecord> indexRecords = queryAllIndexRecords();
      assertThat(indexRecords).hasSize(1);
      assertThat(indexRecords.get(0).getValueString("tableName")).isEqualTo(TEST_ENTITY_TABLE);
   }



   /*******************************************************************************
    ** Test: when indexDocuments() throws, the run record is marked FAILED.
    *******************************************************************************/
   @Test
   void testErrorDuringIndexing_mockClientThrows_failedRunRecord() throws QException
   {
      insertTestEntities(1);

      QuickSearchOpenSearchClient mockClient = mock(QuickSearchOpenSearchClient.class);
      when(mockClient.indexDocuments(any(), anyInt()))
         .thenThrow(new QException("OpenSearch connection refused"));
      QuickSearchQBitContext.setClient(mockClient);

      FullReindexStep step = new FullReindexStep();
      assertThatThrownBy(() -> step.run(buildEmptyInput(), new RunBackendStepOutput()))
         .isInstanceOf(QException.class)
         .hasMessageContaining("Full reindex failed");

      List<QRecord> runRecords = queryAllRunRecords();
      assertThat(runRecords).hasSize(1);
      assertThat(runRecords.get(0).getValueString("status")).isEqualTo("FAILED");
      assertThat(runRecords.get(0).getValueString("errorMessage")).contains("OpenSearch connection refused");
   }



   /*******************************************************************************
    ** Test: lastFullReindexTime is updated on the QuickSearchIndex row after reindex.
    *******************************************************************************/
   @Test
   void testUpdatesLastFullReindexTime_afterReindex_fieldIsSet() throws QException
   {
      insertTestEntities(2);

      QuickSearchOpenSearchClient mockClient = mock(QuickSearchOpenSearchClient.class);
      when(mockClient.indexDocuments(any(), anyInt())).thenReturn(new BulkIndexResult().withSuccessCount(2));
      QuickSearchQBitContext.setClient(mockClient);

      FullReindexStep step = new FullReindexStep();
      step.run(buildEmptyInput(), new RunBackendStepOutput());

      QRecord indexRecord = queryIndexByTableName(TEST_ENTITY_TABLE);
      assertThat(indexRecord).isNotNull();
      assertThat(indexRecord.getValue("lastFullReindexTime")).isNotNull();
      assertThat(indexRecord.getValueInteger("recordCount")).isEqualTo(2);
   }



   /*******************************************************************************
    ** Test: run record has correct runType = "FULL_REINDEX".
    *******************************************************************************/
   @Test
   void testRunRecord_runTypeIsFullReindex() throws QException
   {
      QuickSearchOpenSearchClient mockClient = mock(QuickSearchOpenSearchClient.class);
      when(mockClient.indexDocuments(any(), anyInt())).thenReturn(new BulkIndexResult());
      QuickSearchQBitContext.setClient(mockClient);

      FullReindexStep step = new FullReindexStep();
      step.run(buildEmptyInput(), new RunBackendStepOutput());

      List<QRecord> runRecords = queryAllRunRecords();
      assertThat(runRecords).hasSize(1);
      assertThat(runRecords.get(0).getValueString("runType")).isEqualTo("FULL_REINDEX");
   }



   /*******************************************************************************
    ** Test: documents are built and passed to indexDocuments with source records.
    **
    ** Verifies that the correct number of documents are submitted to the client.
    *******************************************************************************/
   @Test
   void testIndexDocuments_documentsBuiltFromSourceRecords() throws QException
   {
      insertTestEntities(5);

      QuickSearchOpenSearchClient mockClient = mock(QuickSearchOpenSearchClient.class);
      ArgumentCaptor<List<OpenSearchDocument>> docsCaptor = ArgumentCaptor.forClass(List.class);
      when(mockClient.indexDocuments(docsCaptor.capture(), anyInt())).thenReturn(new BulkIndexResult().withSuccessCount(5));
      QuickSearchQBitContext.setClient(mockClient);

      FullReindexStep step = new FullReindexStep();
      step.run(buildEmptyInput(), new RunBackendStepOutput());

      assertThat(docsCaptor.getValue()).hasSize(5);

      List<QRecord> runRecords = queryAllRunRecords();
      assertThat(runRecords).hasSize(1);
      assertThat(runRecords.get(0).getValueInteger("recordsProcessed")).isEqualTo(5);
   }

}

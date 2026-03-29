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


import java.time.Instant;
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryOutput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qbits.quicksearch.BaseQuickSearchTest;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitContext;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;
import com.kingsrook.qbits.quicksearch.opensearch.BulkIndexResult;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/*******************************************************************************
 ** Tests for BasepullIndexStep.
 **
 ** Uses the in-memory QInstance and mock OpenSearch client from BaseQuickSearchTest.
 *******************************************************************************/
class BasepullIndexStepTest extends BaseQuickSearchTest
{

   /*******************************************************************************
    ** Insert records into the testEntity source table.
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
    ** Insert a QuickSearchIndex row with the given parameters.
    *******************************************************************************/
   private QRecord insertIndexRow(String tableName, Boolean enabled, Instant lastBasepullTime, Integer intervalMinutes) throws QException
   {
      QRecord record = new QRecord()
         .withValue("tableName", tableName)
         .withValue("enabled", enabled)
         .withValue("basepullIntervalMinutes", intervalMinutes)
         .withValue("basepullTimestampField", "modifyDate")
         .withValue("lastBasepullTime", lastBasepullTime)
         .withValue("status", "ACTIVE");

      InsertInput input = new InsertInput();
      input.setTableName(QuickSearchIndex.TABLE_NAME);
      input.setRecords(List.of(record));

      return (new InsertAction().execute(input).getRecords().get(0));
   }



   /*******************************************************************************
    ** Query all quickSearchIndexRun rows.
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
    ** Query all quickSearchIndex rows.
    *******************************************************************************/
   private List<QRecord> queryAllIndexRows() throws QException
   {
      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(QuickSearchIndex.TABLE_NAME);
      queryInput.setFilter(new QQueryFilter());

      QueryOutput queryOutput = new QueryAction().execute(queryInput);
      return (queryOutput.getRecords());
   }



   /*******************************************************************************
    ** Build a BulkIndexResult representing all successes for N documents.
    *******************************************************************************/
   private BulkIndexResult allSuccessResult(int count)
   {
      BulkIndexResult result = new BulkIndexResult();
      for(int i = 0; i < count; i++)
      {
         result.addSuccess();
      }
      return (result);
   }



   /*******************************************************************************
    ** Test: no discovered tables, no quickSearchIndex rows - step completes
    ** without calling the OpenSearch client.
    *******************************************************************************/
   @Test
   void testNoEnabledIndexes_noClientCalls() throws QException
   {
      ////////////////////////////////////////////////////
      // Override: no discovered tables, so no index   //
      // rows will be lazy-created or processed.        //
      ////////////////////////////////////////////////////
      QuickSearchQBitContext.setDiscoveredTables(List.of());

      QuickSearchOpenSearchClient mockClient = (QuickSearchOpenSearchClient) QuickSearchQBitContext.getClient();

      BasepullIndexStep step = new BasepullIndexStep();
      step.run(new RunBackendStepInput(), new RunBackendStepOutput());

      verify(mockClient, never()).indexDocuments(any(), anyInt());
   }



   /*******************************************************************************
    ** Test: one enabled index with lastBasepullTime=null triggers a full basepull.
    ** Verifies indexDocuments is called and a COMPLETED run record is created.
    *******************************************************************************/
   @Test
   void testOneEnabledIndex_dueForBasepull_indexesAndCompletesRun() throws QException
   {
      ////////////////////////////////////////////////////
      // Insert some source records                     //
      ////////////////////////////////////////////////////
      insertTestEntities(3);

      ////////////////////////////////////////////////////
      // Insert a QuickSearchIndex row (never run).     //
      // Keep discoveredTables set so tableConfig is    //
      // available during document building.            //
      ////////////////////////////////////////////////////
      insertIndexRow(TEST_ENTITY_TABLE, true, null, 5);

      ////////////////////////////////////////////////////
      // Configure mock client to return success        //
      ////////////////////////////////////////////////////
      QuickSearchOpenSearchClient mockClient = (QuickSearchOpenSearchClient) QuickSearchQBitContext.getClient();
      when(mockClient.indexDocuments(any(), anyInt())).thenReturn(allSuccessResult(3));

      ////////////////////////////////////////////////////
      // Run the step - discoveredTables is still set   //
      // from baseSetUp so ensureIndexRowExists will    //
      // just find the existing row (no-op).            //
      ////////////////////////////////////////////////////
      BasepullIndexStep step = new BasepullIndexStep();
      step.run(new RunBackendStepInput(), new RunBackendStepOutput());

      ////////////////////////////////////////////////////
      // Verify client was called                       //
      ////////////////////////////////////////////////////
      verify(mockClient).indexDocuments(any(), anyInt());

      ////////////////////////////////////////////////////
      // Verify a run record was created with COMPLETED //
      ////////////////////////////////////////////////////
      List<QRecord> runs = queryAllRunRecords();
      assertThat(runs).hasSize(1);
      assertThat(runs.get(0).getValueString("status")).isEqualTo("COMPLETED");
      assertThat(runs.get(0).getValueString("runType")).isEqualTo("BASEPULL");
      assertThat(runs.get(0).getValueInteger("recordsProcessed")).isEqualTo(3);
   }



   /*******************************************************************************
    ** Test: index with a very recent lastBasepullTime and 60-minute interval is
    ** skipped (not yet due).
    *******************************************************************************/
   @Test
   void testIndexNotYetDue_skipped() throws QException
   {
      ////////////////////////////////////////////////////
      // Insert source records                          //
      ////////////////////////////////////////////////////
      insertTestEntities(2);

      ////////////////////////////////////////////////////
      // lastBasepullTime = now, interval = 60 min.     //
      // ensureIndexRowExists will find the existing    //
      // row so no duplicate is created.                //
      ////////////////////////////////////////////////////
      insertIndexRow(TEST_ENTITY_TABLE, true, Instant.now(), 60);

      QuickSearchOpenSearchClient mockClient = (QuickSearchOpenSearchClient) QuickSearchQBitContext.getClient();

      BasepullIndexStep step = new BasepullIndexStep();
      step.run(new RunBackendStepInput(), new RunBackendStepOutput());

      ////////////////////////////////////////////////////
      // Client should never be called                  //
      ////////////////////////////////////////////////////
      verify(mockClient, never()).indexDocuments(any(), anyInt());

      ////////////////////////////////////////////////////
      // No run records created                         //
      ////////////////////////////////////////////////////
      assertThat(queryAllRunRecords()).isEmpty();
   }



   /*******************************************************************************
    ** Test: index is due but no source records match the filter.
    ** Verifies a run record is created with 0 recordsProcessed.
    *******************************************************************************/
   @Test
   void testDueForBasepull_noMatchingRecords_zeroCountRun() throws QException
   {
      ////////////////////////////////////////////////////
      // No source records                              //
      ////////////////////////////////////////////////////

      ////////////////////////////////////////////////////
      // Index row is due (null lastBasepullTime).      //
      // discoveredTables stays set from baseSetUp.     //
      ////////////////////////////////////////////////////
      insertIndexRow(TEST_ENTITY_TABLE, true, null, 5);

      QuickSearchOpenSearchClient mockClient = (QuickSearchOpenSearchClient) QuickSearchQBitContext.getClient();

      BasepullIndexStep step = new BasepullIndexStep();
      step.run(new RunBackendStepInput(), new RunBackendStepOutput());

      ////////////////////////////////////////////////////
      // Run record should exist with 0 processed       //
      ////////////////////////////////////////////////////
      List<QRecord> runs = queryAllRunRecords();
      assertThat(runs).hasSize(1);
      assertThat(runs.get(0).getValueInteger("recordsProcessed")).isEqualTo(0);
      assertThat(runs.get(0).getValueString("status")).isEqualTo("COMPLETED");

      ////////////////////////////////////////////////////
      // Client never called (no documents to index)    //
      ////////////////////////////////////////////////////
      verify(mockClient, never()).indexDocuments(any(), anyInt());
   }



   /*******************************************************************************
    ** Test: discovered tables are present but no quickSearchIndex rows exist.
    ** Running the step should lazy-create the index rows.
    *******************************************************************************/
   @Test
   void testLazyIndexRowCreation_createsRowsForDiscoveredTables() throws QException
   {
      ////////////////////////////////////////////////////
      // Sanity: no index rows yet                      //
      ////////////////////////////////////////////////////
      assertThat(queryAllIndexRows()).isEmpty();

      ////////////////////////////////////////////////////
      // BaseQuickSearchTest sets up one discovered     //
      // table (testEntity); run step with no pre-      //
      // existing index rows.                           //
      ////////////////////////////////////////////////////
      QuickSearchOpenSearchClient mockClient = (QuickSearchOpenSearchClient) QuickSearchQBitContext.getClient();
      when(mockClient.indexDocuments(any(), anyInt())).thenReturn(allSuccessResult(0));

      BasepullIndexStep step = new BasepullIndexStep();
      step.run(new RunBackendStepInput(), new RunBackendStepOutput());

      ////////////////////////////////////////////////////
      // Verify one index row was created               //
      ////////////////////////////////////////////////////
      List<QRecord> indexRows = queryAllIndexRows();
      assertThat(indexRows).hasSize(1);
      assertThat(indexRows.get(0).getValueString("tableName")).isEqualTo(TEST_ENTITY_TABLE);
   }



   /*******************************************************************************
    ** Test: when the OpenSearch client throws an exception, the run record is
    ** updated with FAILED status.
    *******************************************************************************/
   @Test
   void testClientThrows_runRecordMarkedFailed() throws QException
   {
      ////////////////////////////////////////////////////
      // Insert source records so the step attempts to  //
      // index something                                //
      ////////////////////////////////////////////////////
      insertTestEntities(2);

      ////////////////////////////////////////////////////
      // Index row due for basepull                     //
      ////////////////////////////////////////////////////
      insertIndexRow(TEST_ENTITY_TABLE, true, null, 5);

      ////////////////////////////////////////////////////
      // Mock client throws on indexDocuments.          //
      // Keep discoveredTables so tableConfig is found  //
      // and documents are built - triggering the call. //
      ////////////////////////////////////////////////////
      QuickSearchOpenSearchClient mockClient = (QuickSearchOpenSearchClient) QuickSearchQBitContext.getClient();
      when(mockClient.indexDocuments(any(), anyInt()))
         .thenThrow(new QException("OpenSearch unavailable"));

      BasepullIndexStep step = new BasepullIndexStep();

      ////////////////////////////////////////////////////
      // Step should not throw (per-table errors are    //
      // caught and logged)                             //
      ////////////////////////////////////////////////////
      step.run(new RunBackendStepInput(), new RunBackendStepOutput());

      ////////////////////////////////////////////////////
      // Run record should be FAILED                    //
      ////////////////////////////////////////////////////
      List<QRecord> runs = queryAllRunRecords();
      assertThat(runs).hasSize(1);
      assertThat(runs.get(0).getValueString("status")).isEqualTo("FAILED");
   }



   /*******************************************************************************
    ** Test isDueForBasepull directly.
    *******************************************************************************/
   @Test
   void testIsDueForBasepull_nullLastTime_returnsTrue()
   {
      BasepullIndexStep step = new BasepullIndexStep();
      QuickSearchIndex index = new QuickSearchIndex()
         .withLastBasepullTime(null)
         .withBasepullIntervalMinutes(60);

      assertThat(step.isDueForBasepull(index)).isTrue();
   }



   /*******************************************************************************
    ** Test isDueForBasepull when last run was just now - not due.
    *******************************************************************************/
   @Test
   void testIsDueForBasepull_recentLastTime_returnsFalse()
   {
      BasepullIndexStep step = new BasepullIndexStep();
      QuickSearchIndex index = new QuickSearchIndex()
         .withLastBasepullTime(Instant.now())
         .withBasepullIntervalMinutes(60);

      assertThat(step.isDueForBasepull(index)).isFalse();
   }



   /*******************************************************************************
    ** Test isDueForBasepull when last run was more than the interval ago.
    *******************************************************************************/
   @Test
   void testIsDueForBasepull_pastDueLastTime_returnsTrue()
   {
      BasepullIndexStep step = new BasepullIndexStep();
      QuickSearchIndex index = new QuickSearchIndex()
         .withLastBasepullTime(Instant.now().minus(90, java.time.temporal.ChronoUnit.MINUTES))
         .withBasepullIntervalMinutes(60);

      assertThat(step.isDueForBasepull(index)).isTrue();
   }

}

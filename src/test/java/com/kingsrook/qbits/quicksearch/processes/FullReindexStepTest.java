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


import java.util.List;
import com.kingsrook.qbits.quicksearch.BaseQuickSearchTest;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


/*******************************************************************************
 ** Tests for FullReindexStep.
 *******************************************************************************/
class FullReindexStepTest extends BaseQuickSearchTest
{

   /***************************************************************************
    ** Test run throws exception when config is null.
    ***************************************************************************/
   @Test
   void testRun_nullConfig_throwsException()
   {
      FullReindexStep step = new FullReindexStep();
      RunBackendStepInput input = new RunBackendStepInput();
      RunBackendStepOutput output = new RunBackendStepOutput();

      assertThatThrownBy(() -> step.run(input, output))
         .isInstanceOf(QException.class)
         .hasMessageContaining("QuickSearchQBitConfig is required");
   }



   /***************************************************************************
    ** Test run with no indexes found completes without error.
    ***************************************************************************/
   @Test
   void testRun_noIndexesFound_completesSuccessfully() throws Exception
   {
      FullReindexStep step = new FullReindexStep();
      RunBackendStepInput input = new RunBackendStepInput();
      input.addValue(FullReindexStep.FIELD_CONFIG, config);
      RunBackendStepOutput output = new RunBackendStepOutput();

      try(MockedConstruction<QuickSearchOpenSearchClient> mocked = Mockito.mockConstruction(QuickSearchOpenSearchClient.class))
      {
         step.run(input, output);

         /////////////////////////////////////////////////////////////////
         // No client should be created if no indexes exist to reindex //
         /////////////////////////////////////////////////////////////////
         org.assertj.core.api.Assertions.assertThat(mocked.constructed()).isEmpty();
      }
   }



   /***************************************************************************
    ** Test run with existing index performs full reindex.
    ***************************************************************************/
   @Test
   void testRun_withIndex_performsFullReindex() throws Exception
   {
      //////////////////////////
      // Insert an index record //
      //////////////////////////
      insertIndexRecord(TEST_TABLE_NAME);

      //////////////////////////
      // Insert test data       //
      //////////////////////////
      insertTestEntityRecord(1, "Test Item", "Description", "ACTIVE");

      FullReindexStep step = new FullReindexStep();
      RunBackendStepInput input = new RunBackendStepInput();
      input.addValue(FullReindexStep.FIELD_CONFIG, config);
      RunBackendStepOutput output = new RunBackendStepOutput();

      try(MockedConstruction<QuickSearchOpenSearchClient> mocked = Mockito.mockConstruction(
         QuickSearchOpenSearchClient.class,
         (mock, context) ->
         {
            doNothing().when(mock).deleteDocumentsForTable(anyString());
            doNothing().when(mock).indexDocuments(anyList());
            doNothing().when(mock).close();
         }))
      {
         step.run(input, output);

         ////////////////////////////////////////////
         // Verify client was created and used    //
         ////////////////////////////////////////////
         org.assertj.core.api.Assertions.assertThat(mocked.constructed()).hasSize(1);
         QuickSearchOpenSearchClient client = mocked.constructed().get(0);
         verify(client).deleteDocumentsForTable(TEST_TABLE_NAME);
         verify(client).indexDocuments(anyList());
         verify(client).close();
      }
   }



   /***************************************************************************
    ** Test run with specific tableName filters to that table.
    ***************************************************************************/
   @Test
   void testRun_withTableNameFilter_filtersToTable() throws Exception
   {
      ///////////////////////////////////////
      // Insert index for testEntity table //
      ///////////////////////////////////////
      insertIndexRecord(TEST_TABLE_NAME);

      FullReindexStep step = new FullReindexStep();
      RunBackendStepInput input = new RunBackendStepInput();
      input.addValue(FullReindexStep.FIELD_CONFIG, config);
      input.addValue(FullReindexStep.FIELD_TABLE_NAME, "nonExistentTable");
      RunBackendStepOutput output = new RunBackendStepOutput();

      try(MockedConstruction<QuickSearchOpenSearchClient> mocked = Mockito.mockConstruction(QuickSearchOpenSearchClient.class))
      {
         step.run(input, output);

         //////////////////////////////////////////////////////
         // No client created since no matching index found //
         //////////////////////////////////////////////////////
         org.assertj.core.api.Assertions.assertThat(mocked.constructed()).isEmpty();
      }
   }



   /***************************************************************************
    ** Test run with matching table filter performs reindex.
    ***************************************************************************/
   @Test
   void testRun_withMatchingTableNameFilter_performsReindex() throws Exception
   {
      insertIndexRecord(TEST_TABLE_NAME);
      insertTestEntityRecord(1, "Item", "Desc", "ACTIVE");

      FullReindexStep step = new FullReindexStep();
      RunBackendStepInput input = new RunBackendStepInput();
      input.addValue(FullReindexStep.FIELD_CONFIG, config);
      input.addValue(FullReindexStep.FIELD_TABLE_NAME, TEST_TABLE_NAME);
      RunBackendStepOutput output = new RunBackendStepOutput();

      try(MockedConstruction<QuickSearchOpenSearchClient> mocked = Mockito.mockConstruction(
         QuickSearchOpenSearchClient.class,
         (mock, context) ->
         {
            doNothing().when(mock).deleteDocumentsForTable(anyString());
            doNothing().when(mock).indexDocuments(anyList());
            doNothing().when(mock).close();
         }))
      {
         step.run(input, output);

         org.assertj.core.api.Assertions.assertThat(mocked.constructed()).hasSize(1);
         verify(mocked.constructed().get(0)).deleteDocumentsForTable(TEST_TABLE_NAME);
      }
   }



   /***************************************************************************
    ** Test run when OpenSearch delete fails propagates exception.
    ***************************************************************************/
   @Test
   void testRun_deleteDocumentsFails_propagatesException() throws Exception
   {
      insertIndexRecord(TEST_TABLE_NAME);

      FullReindexStep step = new FullReindexStep();
      RunBackendStepInput input = new RunBackendStepInput();
      input.addValue(FullReindexStep.FIELD_CONFIG, config);
      RunBackendStepOutput output = new RunBackendStepOutput();

      try(MockedConstruction<QuickSearchOpenSearchClient> mocked = Mockito.mockConstruction(
         QuickSearchOpenSearchClient.class,
         (mock, context) ->
         {
            Mockito.doThrow(new QException("OpenSearch unavailable"))
               .when(mock).deleteDocumentsForTable(anyString());
            doNothing().when(mock).close();
         }))
      {
         assertThatThrownBy(() -> step.run(input, output))
            .isInstanceOf(QException.class)
            .hasMessageContaining("Full reindex failed");
      }
   }



   /***************************************************************************
    ** Test run with empty records still completes successfully.
    ***************************************************************************/
   @Test
   void testRun_noRecordsInTable_completesWithoutIndexing() throws Exception
   {
      insertIndexRecord(TEST_TABLE_NAME);

      FullReindexStep step = new FullReindexStep();
      RunBackendStepInput input = new RunBackendStepInput();
      input.addValue(FullReindexStep.FIELD_CONFIG, config);
      RunBackendStepOutput output = new RunBackendStepOutput();

      try(MockedConstruction<QuickSearchOpenSearchClient> mocked = Mockito.mockConstruction(
         QuickSearchOpenSearchClient.class,
         (mock, context) ->
         {
            doNothing().when(mock).deleteDocumentsForTable(anyString());
            doNothing().when(mock).indexDocuments(anyList());
            doNothing().when(mock).close();
         }))
      {
         step.run(input, output);

         QuickSearchOpenSearchClient client = mocked.constructed().get(0);
         verify(client).deleteDocumentsForTable(TEST_TABLE_NAME);
         ///////////////////////////////////////////////////////////
         // indexDocuments should not be called with empty list  //
         ///////////////////////////////////////////////////////////
         verify(client, never()).indexDocuments(anyList());
      }
   }



   /***************************************************************************
    ** Helper to insert a QuickSearchIndex record.
    ***************************************************************************/
   private void insertIndexRecord(String tableName) throws QException
   {
      QRecord indexRecord = new QRecord()
         .withValue("tableName", tableName)
         .withValue("isEnabled", true)
         .withValue("searchableFieldsJson", "[\"name\",\"description\",\"status\"]");

      InsertInput insertInput = new InsertInput()
         .withTableName(QuickSearchIndex.TABLE_NAME)
         .withRecords(List.of(indexRecord));

      new InsertAction().execute(insertInput);
   }



   /***************************************************************************
    ** Helper to insert a test entity record.
    ***************************************************************************/
   private void insertTestEntityRecord(Integer id, String name, String description, String status) throws QException
   {
      QRecord record = new QRecord()
         .withValue("id", id)
         .withValue("name", name)
         .withValue("description", description)
         .withValue("status", status);

      InsertInput insertInput = new InsertInput()
         .withTableName(TEST_TABLE_NAME)
         .withRecords(List.of(record));

      new InsertAction().execute(insertInput);
   }

}

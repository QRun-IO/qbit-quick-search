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
import java.time.temporal.ChronoUnit;
import java.util.List;
import com.kingsrook.qbits.quicksearch.BaseQuickSearchTest;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


/*******************************************************************************
 ** Tests for BasepullIndexStep.
 *******************************************************************************/
class BasepullIndexStepTest extends BaseQuickSearchTest
{

   /***************************************************************************
    ** Test run throws exception when config is null.
    ***************************************************************************/
   @Test
   void testRun_nullConfig_throwsException()
   {
      BasepullIndexStep step = new BasepullIndexStep();
      RunBackendStepInput input = new RunBackendStepInput();
      RunBackendStepOutput output = new RunBackendStepOutput();

      assertThatThrownBy(() -> step.run(input, output))
         .isInstanceOf(QException.class)
         .hasMessageContaining("QuickSearchQBitConfig is required");
   }



   /***************************************************************************
    ** Test run with no enabled indexes completes successfully.
    ***************************************************************************/
   @Test
   void testRun_noEnabledIndexes_completesSuccessfully() throws Exception
   {
      BasepullIndexStep step = new BasepullIndexStep();
      RunBackendStepInput input = new RunBackendStepInput();
      input.addValue(BasepullIndexStep.FIELD_CONFIG, config);
      RunBackendStepOutput output = new RunBackendStepOutput();

      try(MockedConstruction<QuickSearchOpenSearchClient> mocked = Mockito.mockConstruction(QuickSearchOpenSearchClient.class))
      {
         step.run(input, output);

         //////////////////////////////////////////////////////////////////
         // No client should be created if no enabled indexes due      //
         //////////////////////////////////////////////////////////////////
         assertThat(mocked.constructed()).isEmpty();
      }
   }



   /***************************************************************************
    ** Test run with disabled index does not process.
    ***************************************************************************/
   @Test
   void testRun_disabledIndex_doesNotProcess() throws Exception
   {
      insertIndexRecord(TEST_TABLE_NAME, false, null);

      BasepullIndexStep step = new BasepullIndexStep();
      RunBackendStepInput input = new RunBackendStepInput();
      input.addValue(BasepullIndexStep.FIELD_CONFIG, config);
      RunBackendStepOutput output = new RunBackendStepOutput();

      try(MockedConstruction<QuickSearchOpenSearchClient> mocked = Mockito.mockConstruction(QuickSearchOpenSearchClient.class))
      {
         step.run(input, output);
         assertThat(mocked.constructed()).isEmpty();
      }
   }



   /***************************************************************************
    ** Test run with enabled index that has never run triggers basepull.
    ***************************************************************************/
   @Test
   void testRun_enabledIndexNeverRun_triggersBasepull() throws Exception
   {
      insertIndexRecord(TEST_TABLE_NAME, true, null);
      insertTestEntityRecord(1, "Item 1", "Description", "ACTIVE");

      BasepullIndexStep step = new BasepullIndexStep();
      RunBackendStepInput input = new RunBackendStepInput();
      input.addValue(BasepullIndexStep.FIELD_CONFIG, config);
      RunBackendStepOutput output = new RunBackendStepOutput();

      try(MockedConstruction<QuickSearchOpenSearchClient> mocked = Mockito.mockConstruction(
         QuickSearchOpenSearchClient.class,
         (mock, context) ->
         {
            doNothing().when(mock).indexDocuments(anyList());
            doNothing().when(mock).close();
         }))
      {
         step.run(input, output);

         assertThat(mocked.constructed()).hasSize(1);
         verify(mocked.constructed().get(0)).indexDocuments(anyList());
      }
   }



   /***************************************************************************
    ** Test run with enabled index past due for basepull triggers indexing.
    ***************************************************************************/
   @Test
   void testRun_indexPastDue_triggersBasepull() throws Exception
   {
      ////////////////////////////////////////////////
      // Last basepull was 10 minutes ago, interval is 5 //
      ////////////////////////////////////////////////
      Instant lastBasepull = Instant.now().minus(10, ChronoUnit.MINUTES);
      insertIndexRecord(TEST_TABLE_NAME, true, lastBasepull);

      //////////////////////////////////////////////////////////
      // Insert record with modifyDate after last basepull  //
      //////////////////////////////////////////////////////////
      insertTestEntityRecordWithModifyDate(1, "Item 1", "Description", "ACTIVE",
         Instant.now().minus(5, ChronoUnit.MINUTES));

      BasepullIndexStep step = new BasepullIndexStep();
      RunBackendStepInput input = new RunBackendStepInput();
      input.addValue(BasepullIndexStep.FIELD_CONFIG, config);
      RunBackendStepOutput output = new RunBackendStepOutput();

      try(MockedConstruction<QuickSearchOpenSearchClient> mocked = Mockito.mockConstruction(
         QuickSearchOpenSearchClient.class,
         (mock, context) ->
         {
            doNothing().when(mock).indexDocuments(anyList());
            doNothing().when(mock).close();
         }))
      {
         step.run(input, output);

         assertThat(mocked.constructed()).hasSize(1);
      }
   }



   /***************************************************************************
    ** Test run with enabled index not yet due does not trigger basepull.
    ***************************************************************************/
   @Test
   void testRun_indexNotYetDue_doesNotTriggerBasepull() throws Exception
   {
      //////////////////////////////////////////////////
      // Last basepull was 1 minute ago, interval is 5 //
      //////////////////////////////////////////////////
      Instant lastBasepull = Instant.now().minus(1, ChronoUnit.MINUTES);
      insertIndexRecord(TEST_TABLE_NAME, true, lastBasepull);

      BasepullIndexStep step = new BasepullIndexStep();
      RunBackendStepInput input = new RunBackendStepInput();
      input.addValue(BasepullIndexStep.FIELD_CONFIG, config);
      RunBackendStepOutput output = new RunBackendStepOutput();

      try(MockedConstruction<QuickSearchOpenSearchClient> mocked = Mockito.mockConstruction(QuickSearchOpenSearchClient.class))
      {
         step.run(input, output);

         /////////////////////////////////////////
         // Index is not due, no client created //
         /////////////////////////////////////////
         assertThat(mocked.constructed()).isEmpty();
      }
   }



   /***************************************************************************
    ** Test run with no records to index completes without calling indexDocuments.
    ***************************************************************************/
   @Test
   void testRun_noRecordsToIndex_doesNotCallIndexDocuments() throws Exception
   {
      insertIndexRecord(TEST_TABLE_NAME, true, null);

      BasepullIndexStep step = new BasepullIndexStep();
      RunBackendStepInput input = new RunBackendStepInput();
      input.addValue(BasepullIndexStep.FIELD_CONFIG, config);
      RunBackendStepOutput output = new RunBackendStepOutput();

      try(MockedConstruction<QuickSearchOpenSearchClient> mocked = Mockito.mockConstruction(
         QuickSearchOpenSearchClient.class,
         (mock, context) ->
         {
            doNothing().when(mock).indexDocuments(anyList());
            doNothing().when(mock).close();
         }))
      {
         step.run(input, output);

         assertThat(mocked.constructed()).hasSize(1);
         //////////////////////////////////////////////////////
         // indexDocuments not called when no records found //
         //////////////////////////////////////////////////////
         verify(mocked.constructed().get(0), never()).indexDocuments(anyList());
      }
   }



   /***************************************************************************
    ** Test run when OpenSearch fails continues to next index.
    ***************************************************************************/
   @Test
   void testRun_opensearchFails_continuesWithoutThrowing() throws Exception
   {
      insertIndexRecord(TEST_TABLE_NAME, true, null);
      insertTestEntityRecord(1, "Item", "Desc", "ACTIVE");

      BasepullIndexStep step = new BasepullIndexStep();
      RunBackendStepInput input = new RunBackendStepInput();
      input.addValue(BasepullIndexStep.FIELD_CONFIG, config);
      RunBackendStepOutput output = new RunBackendStepOutput();

      try(MockedConstruction<QuickSearchOpenSearchClient> mocked = Mockito.mockConstruction(
         QuickSearchOpenSearchClient.class,
         (mock, context) ->
         {
            Mockito.doThrow(new QException("OpenSearch unavailable"))
               .when(mock).indexDocuments(anyList());
            doNothing().when(mock).close();
         }))
      {
         /////////////////////////////////////////////////////////////////
         // Should complete without throwing - logs warning and moves on //
         /////////////////////////////////////////////////////////////////
         step.run(input, output);

         assertThat(mocked.constructed()).hasSize(1);
      }
   }



   /***************************************************************************
    ** Helper to insert a QuickSearchIndex record.
    ***************************************************************************/
   private void insertIndexRecord(String tableName, boolean isEnabled, Instant lastBasepullTime) throws QException
   {
      QRecord indexRecord = new QRecord()
         .withValue("tableName", tableName)
         .withValue("isEnabled", isEnabled)
         .withValue("basepullIntervalMinutes", 5)
         .withValue("basepullTimestampField", "modifyDate")
         .withValue("lastBasepullTime", lastBasepullTime)
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
      insertTestEntityRecordWithModifyDate(id, name, description, status, Instant.now());
   }



   /***************************************************************************
    ** Helper to insert a test entity record with specific modifyDate.
    ***************************************************************************/
   private void insertTestEntityRecordWithModifyDate(Integer id, String name, String description, String status, Instant modifyDate) throws QException
   {
      QRecord record = new QRecord()
         .withValue("id", id)
         .withValue("name", name)
         .withValue("description", description)
         .withValue("status", status)
         .withValue("modifyDate", modifyDate);

      InsertInput insertInput = new InsertInput()
         .withTableName(TEST_TABLE_NAME)
         .withRecords(List.of(record));

      new InsertAction().execute(insertInput);
   }

}

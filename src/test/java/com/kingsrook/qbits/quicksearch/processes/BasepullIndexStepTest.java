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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/*******************************************************************************
 ** Tests for BasepullIndexStep.
 **
 ** Uses the in-memory QInstance from BaseQuickSearchTest with a mock OpenSearch
 ** client that records every batch of documents it is asked to index.
 *******************************************************************************/
class BasepullIndexStepTest extends BaseQuickSearchTest
{
   private static final String OTHER_TABLE = "otherEntity";
   private static final String UPDATED_AT  = "updatedAt";

   private QuickSearchOpenSearchClient mockClient;
   private List<List<String>>          indexedBatches;



   /*******************************************************************************
    ** Install a mock client that reports every document as indexed and keeps
    ** the record ids of each batch.
    *******************************************************************************/
   @BeforeEach
   void setUpClient() throws QException
   {
      indexedBatches = new ArrayList<>();
      mockClient     = mock(QuickSearchOpenSearchClient.class);
      doAnswer(invocation ->
      {
         List<OpenSearchDocument> docs = invocation.getArgument(0);
         indexedBatches.add(docs.stream().map(OpenSearchDocument::getRecordId).toList());
         return (new BulkIndexResult().withSuccessCount(docs.size()));
      }).when(mockClient).indexDocuments(anyList(), anyInt());
      when(mockClient.deleteDocuments(anyList(), anyInt())).thenAnswer(invocation ->
      {
         List<?> ids = invocation.getArgument(0);
         return (new BulkIndexResult().withSuccessCount(ids.size()));
      });
      when(mockClient.countDocumentsForTable(anyString())).thenReturn(0L);
      QuickSearchQBitContext.setClient(mockClient);
   }



   /*******************************************************************************
    ** Every record id indexed so far, in order.
    *******************************************************************************/
   private List<String> allIndexedIds()
   {
      return (indexedBatches.stream().flatMap(List::stream).toList());
   }



   /*******************************************************************************
    ** Insert records into the testEntity source table with the given modifyDate.
    *******************************************************************************/
   private void insertTestEntities(int count, Instant modifyDate) throws QException
   {
      List<QRecord> records = new ArrayList<>();
      for(int i = 1; i <= count; i++)
      {
         records.add(new QRecord()
            .withValue("name", "Entity " + i)
            .withValue("description", "Description " + i)
            .withValue("modifyDate", modifyDate));
      }

      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(TEST_ENTITY_TABLE);
      insertInput.setRecords(records);
      new InsertAction().execute(insertInput);
   }



   /*******************************************************************************
    ** Insert records into the testEntity source table.
    *******************************************************************************/
   private void insertTestEntities(int count) throws QException
   {
      insertTestEntities(count, null);
   }



   /*******************************************************************************
    ** Insert one testEntity record with the given value in a timestamp field.
    *******************************************************************************/
   private void insertTestEntity(String name, String timestampField, Instant timestamp) throws QException
   {
      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(TEST_ENTITY_TABLE);
      insertInput.setRecords(List.of(new QRecord().withValue("name", name).withValue(timestampField, timestamp)));
      new InsertAction().execute(insertInput);
   }



   /*******************************************************************************
    ** Add a DATE_TIME field to testEntity that QQQ does not fill automatically
    ** (unlike modifyDate), and make it the table's basepull timestamp field.
    *******************************************************************************/
   private void useExplicitTimestampField()
   {
      QContext.getQInstance().getTable(TEST_ENTITY_TABLE).addField(new QFieldMetaData(UPDATED_AT, QFieldType.DATE_TIME));
      QuickSearchQBitContext.setDiscoveredTables(List.of(new QuickSearchableTableConfig()
         .withTableName(TEST_ENTITY_TABLE)
         .withPrimaryKeyField("id")
         .withSearchableFields(List.of("name", "description"))
         .withBasepullTimestampField(UPDATED_AT)
         .withBasepullIntervalMinutes(5)
         .withEnabledByDefault(true)));
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
    ** Insert a PENDING failed real-time event.
    *******************************************************************************/
   private QRecord insertFailedEvent(String tableName, String recordId, String action, Integer attempts) throws QException
   {
      InsertInput input = new InsertInput();
      input.setTableName(QuickSearchFailedEvent.TABLE_NAME);
      input.setRecords(List.of(new QRecord()
         .withValue("tableName", tableName)
         .withValue("recordId", recordId)
         .withValue("action", action)
         .withValue("attempts", attempts)
         .withValue("status", QuickSearchFailedEvent.STATUS_PENDING)));
      return (new InsertAction().execute(input).getRecords().get(0));
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
    ** Add a second source table to the QInstance and register it as a
    ** discovered table after testEntity.
    *******************************************************************************/
   private void addOtherTable() throws QException
   {
      QContext.getQInstance().addTable(new QTableMetaData()
         .withName(OTHER_TABLE)
         .withBackendName(TEST_BACKEND_NAME)
         .withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("title", QFieldType.STRING))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME)));

      List<QuickSearchableTableConfig> tables = new ArrayList<>(QuickSearchQBitContext.getDiscoveredTables());
      tables.add(new QuickSearchableTableConfig()
         .withTableName(OTHER_TABLE)
         .withPrimaryKeyField("id")
         .withSearchableFields(List.of("title"))
         .withBasepullTimestampField("modifyDate")
         .withBasepullIntervalMinutes(5)
         .withEnabledByDefault(true));
      QuickSearchQBitContext.setDiscoveredTables(tables);

      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(OTHER_TABLE);
      insertInput.setRecords(List.of(new QRecord().withValue("title", "Other 1"), new QRecord().withValue("title", "Other 2")));
      new InsertAction().execute(insertInput);
   }



   /*******************************************************************************
    ** Test: no discovered tables, no quickSearchIndex rows - step completes
    ** without calling the OpenSearch client.
    *******************************************************************************/
   @Test
   void testNoEnabledIndexes_noClientCalls() throws QException
   {
      QuickSearchQBitContext.setDiscoveredTables(List.of());

      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      verify(mockClient, never()).indexDocuments(any(), anyInt());
   }



   /*******************************************************************************
    ** Test: one enabled index with lastBasepullTime=null triggers a full basepull.
    ** Verifies indexDocuments is called and a COMPLETED run record is created.
    *******************************************************************************/
   @Test
   void testOneEnabledIndex_dueForBasepull_indexesAndCompletesRun() throws QException
   {
      insertTestEntities(3);
      insertIndexRow(TEST_ENTITY_TABLE, true, null, 5);
      when(mockClient.countDocumentsForTable(TEST_ENTITY_TABLE)).thenReturn(3L);

      RunBackendStepOutput output = new RunBackendStepOutput();
      new BasepullIndexStep().run(new RunBackendStepInput(), output);

      verify(mockClient).indexDocuments(any(), anyInt());
      assertThat(allIndexedIds()).containsExactly("1", "2", "3");

      List<QRecord> runs = queryAll(QuickSearchIndexRun.TABLE_NAME);
      assertThat(runs).hasSize(1);
      assertThat(runs.get(0).getValueString("status")).isEqualTo("COMPLETED");
      assertThat(runs.get(0).getValueString("runType")).isEqualTo("BASEPULL");
      assertThat(runs.get(0).getValueInteger("recordsProcessed")).isEqualTo(3);
      assertThat(runs.get(0).getValueInteger("recordsIndexed")).isEqualTo(3);

      QRecord row = queryAll(QuickSearchIndex.TABLE_NAME).get(0);
      assertThat(row.getValue("lastBasepullTime")).isNotNull();
      assertThat(row.getValueInteger("documentCount")).isEqualTo(3);
      assertThat(row.getValueString("lastRunStatus")).isEqualTo("BASEPULL COMPLETED");

      assertThat(output.getValueInteger("tablesIndexed")).isEqualTo(1);
      assertThat(output.getValueInteger("failedEventsReplayed")).isEqualTo(0);
   }



   /*******************************************************************************
    ** Test: index with a very recent lastBasepullTime and 60-minute interval is
    ** skipped (not yet due).
    *******************************************************************************/
   @Test
   void testIndexNotYetDue_skipped() throws QException
   {
      insertTestEntities(2);
      insertIndexRow(TEST_ENTITY_TABLE, true, Instant.now(), 60);

      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      verify(mockClient, never()).indexDocuments(any(), anyInt());
      assertThat(queryAll(QuickSearchIndexRun.TABLE_NAME)).isEmpty();
   }



   /*******************************************************************************
    ** Test: a disabled index row is never basepulled.
    *******************************************************************************/
   @Test
   void testDisabledIndexRow_skipped() throws QException
   {
      insertTestEntities(2);
      insertIndexRow(TEST_ENTITY_TABLE, false, null, 5);

      RunBackendStepOutput output = new RunBackendStepOutput();
      new BasepullIndexStep().run(new RunBackendStepInput(), output);

      verify(mockClient, never()).indexDocuments(any(), anyInt());
      assertThat(queryAll(QuickSearchIndexRun.TABLE_NAME)).isEmpty();
      assertThat(output.getValue("tablesIndexed")).isNull();
   }



   /*******************************************************************************
    ** Test: index is due but no source records match the filter.
    ** Verifies a run record is created with 0 recordsProcessed.
    *******************************************************************************/
   @Test
   void testDueForBasepull_noMatchingRecords_zeroCountRun() throws QException
   {
      insertIndexRow(TEST_ENTITY_TABLE, true, null, 5);

      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      List<QRecord> runs = queryAll(QuickSearchIndexRun.TABLE_NAME);
      assertThat(runs).hasSize(1);
      assertThat(runs.get(0).getValueInteger("recordsProcessed")).isEqualTo(0);
      assertThat(runs.get(0).getValueString("status")).isEqualTo("COMPLETED");

      verify(mockClient, never()).indexDocuments(any(), anyInt());
   }



   /*******************************************************************************
    ** Test: discovered tables are present but no quickSearchIndex rows exist.
    ** Running the step should lazy-create the index rows with the field json.
    *******************************************************************************/
   @Test
   void testLazyIndexRowCreation_createsRowsForDiscoveredTables() throws QException
   {
      assertThat(queryAll(QuickSearchIndex.TABLE_NAME)).isEmpty();

      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      List<QRecord> indexRows = queryAll(QuickSearchIndex.TABLE_NAME);
      assertThat(indexRows).hasSize(1);
      assertThat(indexRows.get(0).getValueString("tableName")).isEqualTo(TEST_ENTITY_TABLE);
      assertThat(indexRows.get(0).getValueBoolean("enabled")).isTrue();
      assertThat(indexRows.get(0).getValueString("status")).isEqualTo("ACTIVE");
      assertThat(indexRows.get(0).getValueString("searchableFieldsJson"))
         .isEqualTo(AbstractIndexingStep.buildSearchableFieldsJson(QuickSearchQBitContext.getTableConfig(TEST_ENTITY_TABLE)));
   }



   /*******************************************************************************
    ** Test: when the OpenSearch client throws, the run record is FAILED with
    ** the error message, the index row mirrors it, and the step throws.
    *******************************************************************************/
   @Test
   void testClientThrows_runRecordMarkedFailed_andStepThrows() throws QException
   {
      insertTestEntities(2);
      insertIndexRow(TEST_ENTITY_TABLE, true, null, 5);
      when(mockClient.indexDocuments(any(), anyInt())).thenThrow(new QException("OpenSearch unavailable"));

      assertThatThrownBy(() -> new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput()))
         .isInstanceOf(QException.class)
         .hasMessageContaining("OpenSearch unavailable");

      List<QRecord> runs = queryAll(QuickSearchIndexRun.TABLE_NAME);
      assertThat(runs).hasSize(1);
      assertThat(runs.get(0).getValueString("status")).isEqualTo("FAILED");
      assertThat(runs.get(0).getValueInteger("errorCount")).isEqualTo(1);
      assertThat(runs.get(0).getValueString("errorMessage")).contains("OpenSearch unavailable");

      QRecord row = queryAll(QuickSearchIndex.TABLE_NAME).get(0);
      assertThat(row.getValue("lastBasepullTime")).as("watermark must not advance on failure").isNull();
      assertThat(row.getValueString("lastRunStatus")).isEqualTo("BASEPULL FAILED");
      assertThat(row.getValueString("lastErrorMessage")).contains("OpenSearch unavailable");
   }



   /*******************************************************************************
    ** Test: per-document bulk failures fail the run with the first error and
    ** leave the watermark alone.
    *******************************************************************************/
   @Test
   void testBulkItemFailures_runFailedWithFirstError() throws QException
   {
      insertTestEntities(2);
      insertIndexRow(TEST_ENTITY_TABLE, true, null, 5);
      BulkIndexResult partial = new BulkIndexResult();
      partial.addSuccess();
      partial.addFailure("testEntity:2: mapper_parsing_exception");
      when(mockClient.indexDocuments(any(), anyInt())).thenReturn(partial);

      assertThatThrownBy(() -> new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput()))
         .isInstanceOf(QException.class)
         .hasMessageContaining("mapper_parsing_exception");

      QRecord run = queryAll(QuickSearchIndexRun.TABLE_NAME).get(0);
      assertThat(run.getValueString("status")).isEqualTo("FAILED");
      assertThat(run.getValueInteger("recordsIndexed")).isEqualTo(1);
      assertThat(run.getValueString("errorMessage")).contains("mapper_parsing_exception");
      assertThat(queryAll(QuickSearchIndex.TABLE_NAME).get(0).getValue("lastBasepullTime")).isNull();
   }



   /*******************************************************************************
    ** Test: the stored watermark is the run's START time, so a record modified
    ** while the run is in progress is picked up by the next run. The record is
    ** inserted from inside the mocked indexDocuments call, after the first page
    ** was read; overlap is zero so the test is not masked by it.
    *******************************************************************************/
   @Test
   void testWatermark_isRunStart_recordModifiedDuringRunIndexedNextTime() throws QException
   {
      QuickSearchQBitContext.getConfig().withBasepullOverlapSeconds(0);
      insertTestEntities(2, Instant.now().minus(1, ChronoUnit.HOURS));
      insertIndexRow(TEST_ENTITY_TABLE, true, null, 0);

      AtomicBoolean            inserted      = new AtomicBoolean(false);
      AtomicReference<Instant> insertedAt    = new AtomicReference<>();
      doAnswer(invocation ->
      {
         List<OpenSearchDocument> docs = invocation.getArgument(0);
         indexedBatches.add(docs.stream().map(OpenSearchDocument::getRecordId).toList());
         if(inserted.compareAndSet(false, true))
         {
            insertedAt.set(Instant.now());
            insertTestEntity("Late arrival", "modifyDate", insertedAt.get());
         }
         return (new BulkIndexResult().withSuccessCount(docs.size()));
      }).when(mockClient).indexDocuments(anyList(), anyInt());

      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      Instant watermark = queryAll(QuickSearchIndex.TABLE_NAME).get(0).getValueInstant("lastBasepullTime");
      assertThat(watermark).as("watermark is captured before the first query").isBeforeOrEqualTo(insertedAt.get());

      indexedBatches.clear();
      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      assertThat(allIndexedIds()).as("only the record modified during the first run is re-indexed").containsExactly("3");
   }



   /*******************************************************************************
    ** Test: the basepull filter is timestamp >= lastBasepullTime - overlap:
    ** a record exactly at the lower bound is included, one just below is not.
    ** Uses a timestamp field QQQ does not overwrite on insert.
    *******************************************************************************/
   @Test
   void testBasepullFilter_greaterThanOrEquals_lastBasepullTimeMinusOverlap() throws QException
   {
      QuickSearchQBitContext.getConfig().withBasepullOverlapSeconds(600);
      useExplicitTimestampField();
      Instant lastBasepull = Instant.now().minus(1, ChronoUnit.HOURS);

      insertTestEntity("inside overlap", UPDATED_AT, lastBasepull.minusSeconds(300));
      insertTestEntity("just before bound", UPDATED_AT, lastBasepull.minusSeconds(601));
      insertTestEntity("exactly at bound", UPDATED_AT, lastBasepull.minusSeconds(600));
      insertTestEntity("after last run", UPDATED_AT, lastBasepull.plusSeconds(60));
      insertIndexRow(TEST_ENTITY_TABLE, true, lastBasepull, 5);

      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      assertThat(allIndexedIds()).containsExactly("1", "3", "4");
   }



   /*******************************************************************************
    ** Test: source records are paged by primary key, never by skip. A record
    ** from an already-read page is deleted during the run; offset paging would
    ** then skip the first record of the next page, keyset paging does not.
    *******************************************************************************/
   @Test
   void testKeysetPaging_everyRecordIndexedOnce_evenWhenEarlierRowDeletedMidRun() throws QException
   {
      QuickSearchQBitContext.getConfig().withSourceBatchSize(2);
      insertTestEntities(6);
      insertIndexRow(TEST_ENTITY_TABLE, true, null, 5);

      AtomicBoolean deleted = new AtomicBoolean(false);
      doAnswer(invocation ->
      {
         List<OpenSearchDocument> docs = invocation.getArgument(0);
         indexedBatches.add(docs.stream().map(OpenSearchDocument::getRecordId).toList());
         if(deleted.compareAndSet(false, true))
         {
            DeleteInput deleteInput = new DeleteInput();
            deleteInput.setTableName(TEST_ENTITY_TABLE);
            deleteInput.setPrimaryKeys(List.<Serializable>of(2));
            new DeleteAction().execute(deleteInput);
         }
         return (new BulkIndexResult().withSuccessCount(docs.size()));
      }).when(mockClient).indexDocuments(anyList(), anyInt());

      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      verify(mockClient, times(3)).indexDocuments(anyList(), anyInt());
      assertThat(indexedBatches).containsExactly(List.of("1", "2"), List.of("3", "4"), List.of("5", "6"));
   }



   /*******************************************************************************
    ** Test: when one table fails, the other tables are still processed, and
    ** the step then throws naming the failed table.
    *******************************************************************************/
   @Test
   void testOneTableFails_otherTablesComplete_thenStepThrows() throws QException
   {
      insertTestEntities(2);
      addOtherTable();

      doAnswer(invocation ->
      {
         List<OpenSearchDocument> docs = invocation.getArgument(0);
         if(TEST_ENTITY_TABLE.equals(docs.get(0).getSourceTable()))
         {
            throw (new QException("shard failure"));
         }
         indexedBatches.add(docs.stream().map(OpenSearchDocument::getRecordId).toList());
         return (new BulkIndexResult().withSuccessCount(docs.size()));
      }).when(mockClient).indexDocuments(anyList(), anyInt());

      RunBackendStepOutput output = new RunBackendStepOutput();
      assertThatThrownBy(() -> new BasepullIndexStep().run(new RunBackendStepInput(), output))
         .isInstanceOf(QException.class)
         .hasMessageContaining("Basepull failed for 1 item(s)")
         .hasMessageContaining(TEST_ENTITY_TABLE + ": ")
         .hasMessageContaining("shard failure");

      assertThat(allIndexedIds()).as("the other table was still indexed").containsExactly("1", "2");
      assertThat(output.getValueInteger("tablesIndexed")).isEqualTo(1);

      List<QRecord> runs = queryAll(QuickSearchIndexRun.TABLE_NAME);
      assertThat(runs).hasSize(2);
      assertThat(runs).extracting(r -> r.getValueString("status")).containsExactlyInAnyOrder("FAILED", "COMPLETED");
   }



   /*******************************************************************************
    ** Test: an enabled index row whose table is no longer configured gets a
    ** FAILED run with an explanation, no source scan, and fails the step.
    *******************************************************************************/
   @Test
   void testIndexRowWithoutTableConfig_runFailed_noScan() throws QException
   {
      insertIndexRow("ghostTable", true, null, 5);

      assertThatThrownBy(() -> new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput()))
         .isInstanceOf(QException.class)
         .hasMessageContaining("ghostTable")
         .hasMessageContaining("no longer configured");

      verify(mockClient, never()).indexDocuments(any(), anyInt());

      List<QRecord> runs = queryAll(QuickSearchIndexRun.TABLE_NAME);
      assertThat(runs).extracting(r -> r.getValueString("status")).contains("FAILED");
      assertThat(runs).extracting(r -> r.getValueString("errorMessage")).anyMatch(m -> m != null && m.contains("no longer configured"));
   }



   /*******************************************************************************
    ** Test: a table without a basepull timestamp field indexes everything on
    ** its first run only; later runs complete with nothing to do.
    *******************************************************************************/
   @Test
   void testNoTimestampField_onlyFirstRunIndexesEverything() throws QException
   {
      QuickSearchQBitContext.setDiscoveredTables(List.of(new QuickSearchableTableConfig()
         .withTableName(TEST_ENTITY_TABLE)
         .withPrimaryKeyField("id")
         .withSearchableFields(List.of("name", "description"))
         .withBasepullTimestampField(null)
         .withBasepullIntervalMinutes(0)
         .withEnabledByDefault(true)));
      insertTestEntities(3);

      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());
      assertThat(allIndexedIds()).containsExactly("1", "2", "3");

      indexedBatches.clear();
      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      assertThat(allIndexedIds()).isEmpty();
      List<QRecord> runs = queryAll(QuickSearchIndexRun.TABLE_NAME);
      assertThat(runs).hasSize(2);
      assertThat(runs).extracting(r -> r.getValueString("status")).containsOnly("COMPLETED");
      assertThat(runs.get(1).getValueInteger("recordsProcessed")).isEqualTo(0);
   }



   /*******************************************************************************
    ** Test: a row whose recorded searchable fields differ from the current
    ** configuration is marked NEEDS_REINDEX; a matching row stays ACTIVE.
    *******************************************************************************/
   @Test
   void testDriftDetection_changedFields_marksNeedsReindex() throws QException
   {
      QRecord row = insertIndexRow(TEST_ENTITY_TABLE, true, Instant.now(), 60);
      UpdateInput updateInput = new UpdateInput();
      updateInput.setTableName(QuickSearchIndex.TABLE_NAME);
      updateInput.setRecords(List.of(new QRecord().withValue("id", row.getValue("id"))
         .withValue("searchableFieldsJson", "[{\"fieldName\":\"name\",\"weight\":1,\"includeLabel\":false}]")));
      new UpdateAction().execute(updateInput);

      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      assertThat(queryAll(QuickSearchIndex.TABLE_NAME).get(0).getValueString("status")).isEqualTo("NEEDS_REINDEX");
   }



   /*******************************************************************************
    ** Test: a row created by the step itself records the current fields and
    ** is not flagged as drifted.
    *******************************************************************************/
   @Test
   void testDriftDetection_matchingFields_staysActive() throws QException
   {
      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());
      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      assertThat(queryAll(QuickSearchIndex.TABLE_NAME).get(0).getValueString("status")).isEqualTo("ACTIVE");
   }



   /*******************************************************************************
    ** Test: pending failed events are replayed first. An INDEX event re-reads
    ** and indexes the record, a DELETE event deletes the document, and rows
    ** that succeeded are removed.
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void testReplayFailedEvents_success_rowsDeleted() throws QException
   {
      insertTestEntities(1);
      insertIndexRow(TEST_ENTITY_TABLE, true, Instant.now(), 60);
      insertFailedEvent(TEST_ENTITY_TABLE, "1", "INDEX", 2);
      insertFailedEvent(TEST_ENTITY_TABLE, "7", "DELETE", 0);

      RunBackendStepOutput output = new RunBackendStepOutput();
      new BasepullIndexStep().run(new RunBackendStepInput(), output);

      assertThat(allIndexedIds()).containsExactly("1");

      ArgumentCaptor<List<String>> deletedIds = ArgumentCaptor.forClass(List.class);
      verify(mockClient).deleteDocuments(deletedIds.capture(), anyInt());
      assertThat(deletedIds.getValue()).containsExactly(OpenSearchDocument.buildDocumentId(TEST_ENTITY_TABLE, "7"));

      assertThat(queryAll(QuickSearchFailedEvent.TABLE_NAME)).isEmpty();
      assertThat(output.getValueInteger("failedEventsReplayed")).isEqualTo(2);
   }



   /*******************************************************************************
    ** Test: an INDEX event for a record that no longer exists removes any
    ** stale document instead, and still counts as replayed.
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void testReplayFailedEvents_recordGone_staleDocumentDeleted() throws QException
   {
      insertIndexRow(TEST_ENTITY_TABLE, true, Instant.now(), 60);
      insertFailedEvent(TEST_ENTITY_TABLE, "42", "INDEX", 0);

      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      ArgumentCaptor<List<String>> deletedIds = ArgumentCaptor.forClass(List.class);
      verify(mockClient).deleteDocuments(deletedIds.capture(), anyInt());
      assertThat(deletedIds.getValue()).containsExactly(OpenSearchDocument.buildDocumentId(TEST_ENTITY_TABLE, "42"));
      assertThat(queryAll(QuickSearchFailedEvent.TABLE_NAME)).isEmpty();
   }



   /*******************************************************************************
    ** Test: a replay that fails bumps attempts and records the error; the
    ** event becomes EXHAUSTED once attempts reach the limit. The step itself
    ** does not fail for replay errors alone when the tables are not due.
    *******************************************************************************/
   @Test
   void testReplayFailedEvents_failure_attemptsIncremented_exhaustedAtLimit() throws QException
   {
      insertTestEntities(2);
      insertIndexRow(TEST_ENTITY_TABLE, true, Instant.now(), 60);
      QRecord young = insertFailedEvent(TEST_ENTITY_TABLE, "1", "INDEX", 3);
      QRecord old   = insertFailedEvent(TEST_ENTITY_TABLE, "2", "INDEX", BasepullIndexStep.MAX_REPLAY_ATTEMPTS - 1);
      when(mockClient.indexDocuments(anyList(), anyInt())).thenThrow(new QException("still down"));

      RunBackendStepOutput output = new RunBackendStepOutput();
      new BasepullIndexStep().run(new RunBackendStepInput(), output);

      List<QRecord> events = queryAll(QuickSearchFailedEvent.TABLE_NAME);
      assertThat(events).hasSize(2);

      QRecord youngAfter = events.stream().filter(e -> e.getValueInteger("id").equals(young.getValueInteger("id"))).findFirst().orElseThrow();
      assertThat(youngAfter.getValueInteger("attempts")).isEqualTo(4);
      assertThat(youngAfter.getValueString("status")).isEqualTo(QuickSearchFailedEvent.STATUS_PENDING);
      assertThat(youngAfter.getValueString("errorMessage")).contains("still down");

      QRecord oldAfter = events.stream().filter(e -> e.getValueInteger("id").equals(old.getValueInteger("id"))).findFirst().orElseThrow();
      assertThat(oldAfter.getValueInteger("attempts")).isEqualTo(BasepullIndexStep.MAX_REPLAY_ATTEMPTS);
      assertThat(oldAfter.getValueString("status")).isEqualTo(QuickSearchFailedEvent.STATUS_EXHAUSTED);

      assertThat(output.getValueInteger("failedEventsReplayed")).isEqualTo(0);
   }



   /*******************************************************************************
    ** Test: an event for a table that is no longer configured fails its replay.
    *******************************************************************************/
   @Test
   void testReplayFailedEvents_unconfiguredTable_marksFailure() throws QException
   {
      QuickSearchQBitContext.setDiscoveredTables(List.of());
      insertFailedEvent("ghostTable", "1", "INDEX", 0);

      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      QRecord event = queryAll(QuickSearchFailedEvent.TABLE_NAME).get(0);
      assertThat(event.getValueInteger("attempts")).isEqualTo(1);
      assertThat(event.getValueString("errorMessage")).contains("no longer configured");
   }



   /*******************************************************************************
    ** Test: run records older than runHistoryRetentionDays are purged at the
    ** end of the step; newer ones are kept.
    *******************************************************************************/
   @Test
   void testPurgeOldRuns_deletesRunsOlderThanRetention() throws QException
   {
      QuickSearchQBitContext.getConfig().withRunHistoryRetentionDays(30);
      QuickSearchQBitContext.setDiscoveredTables(List.of());

      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(QuickSearchIndexRun.TABLE_NAME);
      insertInput.setRecords(List.of(
         new QRecord().withValue("runType", "BASEPULL").withValue("status", "COMPLETED").withValue("startTime", Instant.now().minus(40, ChronoUnit.DAYS)),
         new QRecord().withValue("runType", "BASEPULL").withValue("status", "COMPLETED").withValue("startTime", Instant.now().minus(1, ChronoUnit.DAYS))));
      new InsertAction().execute(insertInput);

      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      List<QRecord> runs = queryAll(QuickSearchIndexRun.TABLE_NAME);
      assertThat(runs).hasSize(1);
      assertThat(runs.get(0).getValueInstant("startTime")).isAfter(Instant.now().minus(2, ChronoUnit.DAYS));
   }



   /*******************************************************************************
    ** Test: a retention of zero disables the purge.
    *******************************************************************************/
   @Test
   void testPurgeOldRuns_zeroRetention_keepsEverything() throws QException
   {
      QuickSearchQBitContext.getConfig().withRunHistoryRetentionDays(0);
      QuickSearchQBitContext.setDiscoveredTables(List.of());

      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(QuickSearchIndexRun.TABLE_NAME);
      insertInput.setRecords(List.of(new QRecord().withValue("runType", "BASEPULL").withValue("status", "COMPLETED").withValue("startTime", Instant.now().minus(400, ChronoUnit.DAYS))));
      new InsertAction().execute(insertInput);

      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      assertThat(queryAll(QuickSearchIndexRun.TABLE_NAME)).hasSize(1);
   }



   /*******************************************************************************
    ** Test isDueForBasepull directly.
    *******************************************************************************/
   @Test
   void testIsDueForBasepull_nullLastTime_returnsTrue()
   {
      QuickSearchIndex index = new QuickSearchIndex().withLastBasepullTime(null).withBasepullIntervalMinutes(60);
      assertThat(new BasepullIndexStep().isDueForBasepull(index)).isTrue();
   }



   /*******************************************************************************
    ** Test isDueForBasepull when last run was just now - not due.
    *******************************************************************************/
   @Test
   void testIsDueForBasepull_recentLastTime_returnsFalse()
   {
      QuickSearchIndex index = new QuickSearchIndex().withLastBasepullTime(Instant.now()).withBasepullIntervalMinutes(60);
      assertThat(new BasepullIndexStep().isDueForBasepull(index)).isFalse();
   }



   /*******************************************************************************
    ** Test isDueForBasepull when last run was more than the interval ago.
    *******************************************************************************/
   @Test
   void testIsDueForBasepull_pastDueLastTime_returnsTrue()
   {
      QuickSearchIndex index = new QuickSearchIndex().withLastBasepullTime(Instant.now().minus(90, ChronoUnit.MINUTES)).withBasepullIntervalMinutes(60);
      assertThat(new BasepullIndexStep().isDueForBasepull(index)).isTrue();
   }



   /*******************************************************************************
    ** Test: a delete captured during a full reindex is left for that reindex
    ** to apply to the new index, not replayed against the old one.
    *******************************************************************************/
   @Test
   void testReplayFailedEvents_awaitingReindexRowsAreNotReplayed() throws QException
   {
      insertIndexRow(TEST_ENTITY_TABLE, true, Instant.now(), 60);
      QRecord captured = insertFailedEvent(TEST_ENTITY_TABLE, "7", "DELETE", 0);
      UpdateInput updateInput = new UpdateInput();
      updateInput.setTableName(QuickSearchFailedEvent.TABLE_NAME);
      updateInput.setRecords(List.of(new QRecord().withValue("id", captured.getValue("id")).withValue("status", QuickSearchFailedEvent.STATUS_AWAITING_REINDEX)));
      new UpdateAction().execute(updateInput);

      RunBackendStepOutput output = new RunBackendStepOutput();
      new BasepullIndexStep().run(new RunBackendStepInput(), output);

      verify(mockClient, never()).deleteDocuments(anyList(), anyInt());
      assertThat(queryAll(QuickSearchFailedEvent.TABLE_NAME)).hasSize(1);
      assertThat(output.getValueInteger("failedEventsReplayed")).isEqualTo(0);
   }



   /*******************************************************************************
    ** Test: drift found while a full reindex is running does not replace the
    ** REBUILDING status the listener relies on.
    *******************************************************************************/
   @Test
   void testDriftDetection_duringFullReindex_keepsRebuilding() throws QException
   {
      QRecord row = insertIndexRow(TEST_ENTITY_TABLE, true, Instant.now(), 60);
      UpdateInput updateInput = new UpdateInput();
      updateInput.setTableName(QuickSearchIndex.TABLE_NAME);
      updateInput.setRecords(List.of(new QRecord().withValue("id", row.getValue("id"))
         .withValue("status", AbstractIndexingStep.STATUS_REBUILDING)
         .withValue("searchableFieldsJson", "[{\"fieldName\":\"name\",\"weight\":1,\"includeLabel\":false}]")));
      new UpdateAction().execute(updateInput);

      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      assertThat(queryAll(QuickSearchIndex.TABLE_NAME).get(0).getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_REBUILDING);
   }

}

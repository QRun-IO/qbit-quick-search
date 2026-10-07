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

package com.kingsrook.qbits.quicksearch.listeners;


import java.util.List;
import com.kingsrook.qqq.backend.core.actions.QBackendTransaction;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.actions.tables.listeners.RecordChangeType;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;
import com.kingsrook.qbits.quicksearch.BaseQuickSearchTest;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitContext;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.processes.AbstractIndexingStep;
import com.kingsrook.qbits.quicksearch.publisher.IndexEvent;
import com.kingsrook.qbits.quicksearch.publisher.IndexEventAction;
import com.kingsrook.qbits.quicksearch.publisher.IndexEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


/*******************************************************************************
 ** Unit test for QuickSearchRecordChangeListener against the memory backend.
 *******************************************************************************/
class QuickSearchRecordChangeListenerTest extends BaseQuickSearchTest
{
   private IndexEventPublisher publisher;
   private QuickSearchRuntime  runtime;



   @BeforeEach
   void setUpListener()
   {
      QuickSearchQBitContext.clear();
      publisher = mock(IndexEventPublisher.class);

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName(TEST_BACKEND_NAME)
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test")
         .withIndexEventPublisher(publisher);

      runtime = new QuickSearchRuntime(config);
      runtime.setDiscoveredTables(List.of(new QuickSearchableTableConfig()
         .withTableName(TEST_ENTITY_TABLE)
         .withPrimaryKeyField("id")
         .withSearchableFields(List.of("name", "description"))));
      config.setRuntime(runtime);

      QInstance qInstance = QContext.getQInstance();
      qInstance.addQBit(new QBitMetaData().withGroupId("com.kingsrook.qbits").withArtifactId("quick-search").withVersion("test").withConfig(config));
      qInstance.withRecordChangeListener(new QCodeReference(QuickSearchRecordChangeListener.class));
   }



   private Integer insert(String name, QBackendTransaction transaction) throws QException
   {
      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(TEST_ENTITY_TABLE);
      insertInput.setTransaction(transaction);
      insertInput.setRecords(List.of(new QRecord().withValue("name", name).withValue("description", "d")));
      return (new InsertAction().execute(insertInput).getRecords().get(0).getValueInteger("id"));
   }



   @Test
   void testAppliesTo_onlyConfiguredTablesWhenEnabled()
   {
      QuickSearchRecordChangeListener listener = new QuickSearchRecordChangeListener();
      assertThat(listener.appliesTo(TEST_ENTITY_TABLE, RecordChangeType.INSERT)).isTrue();
      assertThat(listener.appliesTo(QuickSearchIndex.TABLE_NAME, RecordChangeType.INSERT)).isFalse();

      runtime.getConfig().setEnableRealTimeIndexing(false);
      assertThat(listener.appliesTo(TEST_ENTITY_TABLE, RecordChangeType.INSERT)).isFalse();
   }



   @Test
   void testInsert_withoutTransaction_publishesImmediately() throws QException
   {
      Integer id = insert("widget", null);

      ArgumentCaptor<List<IndexEvent>> captor = ArgumentCaptor.forClass(List.class);
      verify(publisher, times(1)).publishIndexEvents(captor.capture());
      assertThat(captor.getValue()).hasSize(1);
      assertThat(captor.getValue().get(0).getAction()).isEqualTo(IndexEventAction.INDEX);
      assertThat(captor.getValue().get(0).getRecordId()).isEqualTo(String.valueOf(id));
      assertThat(captor.getValue().get(0).getRecord().getValueString("name")).isEqualTo("widget");
   }



   @Test
   void testInsert_withTransaction_publishesOnlyAfterCommit() throws QException
   {
      QBackendTransaction transaction = new QBackendTransaction();
      insert("widget", transaction);

      verify(publisher, never()).publishIndexEvents(anyList());

      transaction.commit();
      verify(publisher, times(1)).publishIndexEvents(anyList());
   }



   @Test
   void testInsert_withTransaction_rollbackNeverPublishes() throws QException
   {
      QBackendTransaction transaction = new QBackendTransaction();
      insert("widget", transaction);
      transaction.rollback();

      verify(publisher, never()).publishIndexEvents(anyList());
   }



   @Test
   void testUpdate_reReadsFullRecord() throws QException
   {
      Integer id = insert("widget", null);

      UpdateInput updateInput = new UpdateInput();
      updateInput.setTableName(TEST_ENTITY_TABLE);
      updateInput.setRecords(List.of(new QRecord().withValue("id", id).withValue("description", "changed")));
      new UpdateAction().execute(updateInput);

      ArgumentCaptor<List<IndexEvent>> captor = ArgumentCaptor.forClass(List.class);
      verify(publisher, times(2)).publishIndexEvents(captor.capture());
      IndexEvent updateEvent = captor.getAllValues().get(1).get(0);
      assertThat(updateEvent.getRecord().getValueString("name")).isEqualTo("widget");
      assertThat(updateEvent.getRecord().getValueString("description")).isEqualTo("changed");
   }



   @Test
   void testDelete_publishesDeleteEvents() throws QException
   {
      Integer id = insert("widget", null);

      DeleteInput deleteInput = new DeleteInput();
      deleteInput.setTableName(TEST_ENTITY_TABLE);
      deleteInput.setPrimaryKeys(List.of(id));
      new DeleteAction().execute(deleteInput);

      ArgumentCaptor<List<IndexEvent>> captor = ArgumentCaptor.forClass(List.class);
      verify(publisher, times(1)).publishDeleteEvents(captor.capture());
      assertThat(captor.getValue()).hasSize(1);
      assertThat(captor.getValue().get(0).getAction()).isEqualTo(IndexEventAction.DELETE);
      assertThat(captor.getValue().get(0).getRecordId()).isEqualTo(String.valueOf(id));
   }



   @Test
   void testPublishFailure_writesFailedEventAndDoesNotFailWrite() throws QException
   {
      doThrow(new QException("cluster down")).when(publisher).publishIndexEvents(anyList());

      Integer id = insert("widget", null);
      assertThat(id).isNotNull();

      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(QuickSearchFailedEvent.TABLE_NAME);
      List<QRecord> failed = new QueryAction().execute(queryInput).getRecords();
      assertThat(failed).hasSize(1);
      assertThat(failed.get(0).getValueString("tableName")).isEqualTo(TEST_ENTITY_TABLE);
      assertThat(failed.get(0).getValueString("recordId")).isEqualTo(String.valueOf(id));
      assertThat(failed.get(0).getValueString("action")).isEqualTo("INDEX");
      assertThat(failed.get(0).getValueString("errorMessage")).contains("cluster down");
      assertThat(failed.get(0).getValueString("status")).isEqualTo(QuickSearchFailedEvent.STATUS_PENDING);
   }



   @Test
   void testDisabledTable_isSkipped() throws QException
   {
      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(QuickSearchIndex.TABLE_NAME);
      insertInput.setRecords(List.of(new QRecord().withValue("tableName", TEST_ENTITY_TABLE).withValue("enabled", false)));
      new InsertAction().execute(insertInput);
      runtime.invalidateEnabledCache();

      insert("widget", null);
      verify(publisher, never()).publishIndexEvents(anyList());
   }



   @Test
   void testDelete_duringFullReindex_isCapturedForTheNewIndex() throws QException
   {
      insertIndexRowWithStatus(AbstractIndexingStep.STATUS_REBUILDING);
      Integer id = insert("widget", null);
      delete(id);

      verify(publisher, times(1)).publishDeleteEvents(anyList());

      List<QRecord> captured = queryFailedEvents();
      assertThat(captured).hasSize(1);
      assertThat(captured.get(0).getValueString("tableName")).isEqualTo(TEST_ENTITY_TABLE);
      assertThat(captured.get(0).getValueString("recordId")).isEqualTo(String.valueOf(id));
      assertThat(captured.get(0).getValueString("action")).isEqualTo("DELETE");
      assertThat(captured.get(0).getValueString("status")).isEqualTo(QuickSearchFailedEvent.STATUS_AWAITING_REINDEX);
   }



   @Test
   void testDelete_publishFailureDuringFullReindex_recordsBothRows() throws QException
   {
      doThrow(new QException("cluster down")).when(publisher).publishDeleteEvents(anyList());
      insertIndexRowWithStatus(AbstractIndexingStep.STATUS_REBUILDING);
      delete(insert("widget", null));

      assertThat(queryFailedEvents()).extracting(r -> r.getValueString("status"))
         .containsExactlyInAnyOrder(QuickSearchFailedEvent.STATUS_PENDING, QuickSearchFailedEvent.STATUS_AWAITING_REINDEX);
   }



   @Test
   void testDelete_withoutFullReindex_isNotCaptured() throws QException
   {
      insertIndexRowWithStatus(AbstractIndexingStep.STATUS_ACTIVE);
      delete(insert("widget", null));

      verify(publisher, times(1)).publishDeleteEvents(anyList());
      assertThat(queryFailedEvents()).isEmpty();
   }



   @Test
   void testCapture_rebuildFinishedBeforeInsert_handsRowsToBasepull() throws QException
   {
      ////////////////////////////////////////////////////////////////////////
      // the second read sees the rebuild finished, as if it ended between //
      // the first read and the insert                                     //
      ////////////////////////////////////////////////////////////////////////
      insertIndexRowWithStatus(AbstractIndexingStep.STATUS_ACTIVE);
      List<IndexEvent> events = List.of(new IndexEvent().withAction(IndexEventAction.DELETE).withTableName(TEST_ENTITY_TABLE).withRecordId("7"));

      List<QRecord> inserted = QuickSearchRecordChangeListener.insertFailedEventRows(runtime, events, "captured", QuickSearchFailedEvent.STATUS_AWAITING_REINDEX);
      QuickSearchRecordChangeListener.handOverIfRebuildFinished(runtime, TEST_ENTITY_TABLE, inserted);

      List<QRecord> rows = queryFailedEvents();
      assertThat(rows).hasSize(1);
      assertThat(rows.get(0).getValueString("status")).isEqualTo(QuickSearchFailedEvent.STATUS_PENDING);
   }



   private void delete(Integer id) throws QException
   {
      DeleteInput deleteInput = new DeleteInput();
      deleteInput.setTableName(TEST_ENTITY_TABLE);
      deleteInput.setPrimaryKeys(List.of(id));
      new DeleteAction().execute(deleteInput);
   }



   private void insertIndexRowWithStatus(String status) throws QException
   {
      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(QuickSearchIndex.TABLE_NAME);
      insertInput.setRecords(List.of(new QRecord().withValue("tableName", TEST_ENTITY_TABLE).withValue("enabled", true).withValue("status", status)));
      new InsertAction().execute(insertInput);
      runtime.invalidateEnabledCache();
   }



   private List<QRecord> queryFailedEvents() throws QException
   {
      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(QuickSearchFailedEvent.TABLE_NAME);
      return (new QueryAction().execute(queryInput).getRecords());
   }



   @Test
   void testDelete_rebuildEndsDuringPublish_isStillCaptured() throws QException
   {
      ////////////////////////////////////////////////////////////////////////////
      // the reindex swaps the alias and clears REBUILDING while the delete is //
      // being published to the old index: the status read must come first    //
      ////////////////////////////////////////////////////////////////////////////
      insertIndexRowWithStatus(AbstractIndexingStep.STATUS_REBUILDING);
      Integer id = insert("widget", null);
      doAnswer(invocation ->
      {
         setIndexRowStatus(AbstractIndexingStep.STATUS_ACTIVE);
         return (null);
      }).when(publisher).publishDeleteEvents(anyList());

      delete(id);

      List<QRecord> rows = queryFailedEvents();
      assertThat(rows).hasSize(1);
      assertThat(rows.get(0).getValueString("recordId")).isEqualTo(String.valueOf(id));
      assertThat(rows.get(0).getValueString("action")).isEqualTo("DELETE");
      assertThat(rows.get(0).getValueString("status")).isEqualTo(QuickSearchFailedEvent.STATUS_PENDING);
   }



   private void setIndexRowStatus(String status) throws QException
   {
      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(QuickSearchIndex.TABLE_NAME);
      QRecord row = new QueryAction().execute(queryInput).getRecords().get(0);

      UpdateInput updateInput = new UpdateInput();
      updateInput.setTableName(QuickSearchIndex.TABLE_NAME);
      updateInput.setRecords(List.of(new QRecord().withValue("id", row.getValue("id")).withValue("status", status)));
      new UpdateAction().execute(updateInput);
   }

}

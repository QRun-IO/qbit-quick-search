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

package com.kingsrook.qbits.quicksearch.customizers;


import java.util.List;
import java.util.Optional;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizers;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.statusmessages.BadInputStatusMessage;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryRecordStore;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitContext;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.publisher.IndexEvent;
import com.kingsrook.qbits.quicksearch.publisher.IndexEventAction;
import com.kingsrook.qbits.quicksearch.publisher.IndexEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


/*******************************************************************************
 ** Tests for QuickSearchPostUpdateCustomizer.
 **
 ** Uses a memory backend, because the customizer re-reads updated records from
 ** the source table before indexing them.
 *******************************************************************************/
class QuickSearchPostUpdateCustomizerTest
{
   private static final String BACKEND_NAME = "memoryBackend";
   private static final String TABLE_NAME   = "orders";
   private static final String PK_FIELD     = "id";

   private IndexEventPublisher mockPublisher;



   /*******************************************************************************
    ** Set up mock publisher in QuickSearchQBitContext and a QContext with a
    ** memory-backed test QInstance before each test.
    *******************************************************************************/
   @BeforeEach
   void setUp()
   {
      MemoryRecordStore.getInstance().reset();

      mockPublisher = mock(IndexEventPublisher.class);
      QuickSearchQBitContext.setPublisher(mockPublisher);
      QuickSearchQBitContext.setDiscoveredTables(List.of(
         new QuickSearchableTableConfig().withTableName(TABLE_NAME).withPrimaryKeyField(PK_FIELD)
      ));

      QInstance qInstance = new QInstance();
      qInstance.addBackend(new QBackendMetaData()
         .withName(BACKEND_NAME)
         .withBackendType(MemoryBackendModule.class));
      qInstance.addTable(new QTableMetaData()
         .withName(TABLE_NAME)
         .withBackendName(BACKEND_NAME)
         .withPrimaryKeyField(PK_FIELD)
         .withField(new QFieldMetaData(PK_FIELD, QFieldType.INTEGER))
         .withField(new QFieldMetaData("name", QFieldType.STRING))
         .withField(new QFieldMetaData("description", QFieldType.STRING)));
      qInstance.withInstanceDefaultAuthentication(new QAuthenticationMetaData()
         .withName("anonymous")
         .withType(QAuthenticationType.FULLY_ANONYMOUS));
      QContext.init(qInstance, new QSession());
   }



   /*******************************************************************************
    ** Clear QuickSearchQBitContext and QContext after each test to prevent leakage.
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      QuickSearchQBitContext.clear();
      QContext.clear();
      MemoryRecordStore.getInstance().reset();
   }



   /*******************************************************************************
    ** Insert full records into the source table.
    *******************************************************************************/
   private void insertSourceRecords(QRecord... records) throws QException
   {
      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(TABLE_NAME);
      insertInput.setRecords(List.of(records));
      new InsertAction().execute(insertInput);
   }



   /*******************************************************************************
    ** Capture the events passed to the publisher's publishIndexEvents.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   private List<IndexEvent> captureIndexEvents() throws QException
   {
      ArgumentCaptor<List<IndexEvent>> captor = ArgumentCaptor.forClass(List.class);
      verify(mockPublisher).publishIndexEvents(captor.capture());
      return (captor.getValue());
   }



   /*******************************************************************************
    ** Test that no-arg constructor completes without throwing.
    *******************************************************************************/
   @Test
   void testNoArgConstructor_doesNotThrow()
   {
      assertThatNoException().isThrownBy(QuickSearchPostUpdateCustomizer::new);
   }



   /*******************************************************************************
    ** Test that post-update builds INDEX events from the stored source records,
    ** not from the (possibly sparse) records passed in by the update.
    *******************************************************************************/
   @Test
   void testPostUpdate_withRecords_indexesStoredSourceRecords() throws QException
   {
      insertSourceRecords(
         new QRecord().withValue(PK_FIELD, 10).withValue("name", "Alice Updated").withValue("description", "Engineer"),
         new QRecord().withValue(PK_FIELD, 20).withValue("name", "Bob Updated").withValue("description", "Designer"));

      QRecord sparse1 = new QRecord().withValue(PK_FIELD, 10).withValue("name", "Alice Updated");
      QRecord sparse2 = new QRecord().withValue(PK_FIELD, 20).withValue("name", "Bob Updated");

      UpdateInput updateInput = new UpdateInput();
      updateInput.setTableName(TABLE_NAME);

      QuickSearchPostUpdateCustomizer customizer = new QuickSearchPostUpdateCustomizer();
      List<QRecord> result = customizer.postUpdate(updateInput, List.of(sparse1, sparse2), Optional.empty());

      assertThat(result).containsExactly(sparse1, sparse2);

      List<IndexEvent> events = captureIndexEvents();
      assertThat(events).hasSize(2);
      assertThat(events).allSatisfy(e ->
      {
         assertThat(e.getAction()).isEqualTo(IndexEventAction.INDEX);
         assertThat(e.getTableName()).isEqualTo(TABLE_NAME);
      });
      assertThat(events).extracting(IndexEvent::getRecordId).containsExactlyInAnyOrder("10", "20");

      IndexEvent aliceEvent = events.stream().filter(e -> "10".equals(e.getRecordId())).findFirst().orElseThrow();
      assertThat(aliceEvent.getRecord().getValueString("name")).isEqualTo("Alice Updated");
      assertThat(aliceEvent.getRecord().getValueString("description")).isEqualTo("Engineer");
   }



   /*******************************************************************************
    ** End to end through UpdateAction: a partial update of one field must not drop
    ** the other indexed fields from the document.
    *******************************************************************************/
   @Test
   void testUpdateAction_partialUpdate_reindexesFullRecord() throws QException
   {
      QContext.getQInstance().getTable(TABLE_NAME)
         .withCustomizer(TableCustomizers.POST_UPDATE_RECORD, new QCodeReference(QuickSearchPostUpdateCustomizer.class));

      insertSourceRecords(new QRecord().withValue(PK_FIELD, 1).withValue("name", "Alice").withValue("description", "Engineer"));

      UpdateInput updateInput = new UpdateInput();
      updateInput.setTableName(TABLE_NAME);
      updateInput.setRecords(List.of(new QRecord().withValue(PK_FIELD, 1).withValue("name", "Alicia")));
      new UpdateAction().execute(updateInput);

      List<IndexEvent> events = captureIndexEvents();
      assertThat(events).hasSize(1);
      assertThat(events.get(0).getRecordId()).isEqualTo("1");
      assertThat(events.get(0).getRecord().getValueString("name")).isEqualTo("Alicia");
      assertThat(events.get(0).getRecord().getValueString("description")).isEqualTo("Engineer");
   }



   /*******************************************************************************
    ** Test that records the update rejected (with errors) are not re-indexed.
    *******************************************************************************/
   @Test
   void testPostUpdate_recordWithErrors_notIndexed() throws QException
   {
      insertSourceRecords(
         new QRecord().withValue(PK_FIELD, 1).withValue("name", "Good"),
         new QRecord().withValue(PK_FIELD, 2).withValue("name", "Rejected"));

      QRecord good     = new QRecord().withValue(PK_FIELD, 1).withValue("name", "Good");
      QRecord rejected = new QRecord().withValue(PK_FIELD, 2).withValue("name", "Rejected");
      rejected.addError(new BadInputStatusMessage("not allowed"));

      UpdateInput updateInput = new UpdateInput();
      updateInput.setTableName(TABLE_NAME);

      new QuickSearchPostUpdateCustomizer().postUpdate(updateInput, List.of(good, rejected), Optional.empty());

      List<IndexEvent> events = captureIndexEvents();
      assertThat(events).extracting(IndexEvent::getRecordId).containsExactly("1");
   }



   /*******************************************************************************
    ** Test that when no updated record can be read back from the source table,
    ** nothing is published.
    *******************************************************************************/
   @Test
   void testPostUpdate_recordNotInSource_nothingPublished() throws QException
   {
      UpdateInput updateInput = new UpdateInput();
      updateInput.setTableName(TABLE_NAME);

      new QuickSearchPostUpdateCustomizer().postUpdate(updateInput, List.of(new QRecord().withValue(PK_FIELD, 99)), Optional.empty());

      verify(mockPublisher, never()).publishIndexEvents(anyList());
   }



   /*******************************************************************************
    ** Test that post-update with empty records list returns input without calling
    ** publisher.
    *******************************************************************************/
   @Test
   void testPostUpdate_withEmptyRecords_returnsInputWithoutPublishing() throws QException
   {
      UpdateInput updateInput = new UpdateInput();
      updateInput.setTableName(TABLE_NAME);

      QuickSearchPostUpdateCustomizer customizer = new QuickSearchPostUpdateCustomizer();
      List<QRecord> result = customizer.postUpdate(updateInput, List.of(), Optional.empty());

      assertThat(result).isEmpty();
      verify(mockPublisher, never()).publishIndexEvents(anyList());
   }



   /*******************************************************************************
    ** Test that post-update with null records returns null without calling publisher.
    *******************************************************************************/
   @Test
   void testPostUpdate_withNullRecords_returnsNullWithoutPublishing() throws QException
   {
      UpdateInput updateInput = new UpdateInput();
      updateInput.setTableName(TABLE_NAME);

      QuickSearchPostUpdateCustomizer customizer = new QuickSearchPostUpdateCustomizer();
      List<QRecord> result = customizer.postUpdate(updateInput, null, Optional.empty());

      assertThat(result).isNull();
      verify(mockPublisher, never()).publishIndexEvents(anyList());
   }



   /*******************************************************************************
    ** Test that when publisher is null, records are still returned (no exception).
    *******************************************************************************/
   @Test
   void testPostUpdate_publisherNull_returnsRecordsWithoutThrowing() throws QException
   {
      QuickSearchQBitContext.setPublisher(null);

      QRecord record = new QRecord().withValue(PK_FIELD, 1);
      UpdateInput updateInput = new UpdateInput();
      updateInput.setTableName(TABLE_NAME);

      QuickSearchPostUpdateCustomizer customizer = new QuickSearchPostUpdateCustomizer();
      List<QRecord> result = customizer.postUpdate(updateInput, List.of(record), Optional.empty());

      assertThat(result).containsExactly(record);
   }



   /*******************************************************************************
    ** Test that when publisher throws, a warning is logged and records are returned
    ** (no exception propagated to caller).
    *******************************************************************************/
   @Test
   void testPostUpdate_publisherThrows_returnsRecordsWithoutThrowing() throws QException
   {
      insertSourceRecords(new QRecord().withValue(PK_FIELD, 1).withValue("name", "Alice"));
      doThrow(new QException("publish failed"))
         .when(mockPublisher).publishIndexEvents(anyList());

      QRecord record = new QRecord().withValue(PK_FIELD, 1);
      UpdateInput updateInput = new UpdateInput();
      updateInput.setTableName(TABLE_NAME);

      QuickSearchPostUpdateCustomizer customizer = new QuickSearchPostUpdateCustomizer();
      assertThatNoException().isThrownBy(() -> customizer.postUpdate(updateInput, List.of(record), Optional.empty()));
      verify(mockPublisher).publishIndexEvents(anyList());
   }

}

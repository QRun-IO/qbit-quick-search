/*
 * Copyright 2024 Kingsrook, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qbits.quicksearch.customizers;


import java.util.List;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
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
 ** Tests for QuickSearchPostInsertCustomizer.
 *******************************************************************************/
class QuickSearchPostInsertCustomizerTest
{
   private static final String TABLE_NAME = "orders";
   private static final String PK_FIELD   = "id";

   private IndexEventPublisher mockPublisher;



   /*******************************************************************************
    ** Set up mock publisher in QuickSearchQBitContext and a QContext with a test
    ** QInstance before each test.
    *******************************************************************************/
   @BeforeEach
   void setUp()
   {
      mockPublisher = mock(IndexEventPublisher.class);
      QuickSearchQBitContext.setPublisher(mockPublisher);
      QuickSearchQBitContext.setDiscoveredTables(List.of(
         new QuickSearchableTableConfig().withTableName(TABLE_NAME).withPrimaryKeyField(PK_FIELD)
      ));

      QInstance qInstance = new QInstance();
      qInstance.addTable(new QTableMetaData()
         .withName(TABLE_NAME)
         .withPrimaryKeyField(PK_FIELD)
         .withField(new QFieldMetaData(PK_FIELD, QFieldType.INTEGER))
         .withField(new QFieldMetaData("name", QFieldType.STRING)));
      qInstance.addBackend(new com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData().withName("testBackend"));
      qInstance.setAuthentication(new QAuthenticationMetaData().withName("anonymous").withType(com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType.FULLY_ANONYMOUS));
      qInstance.getTable(TABLE_NAME).withBackendName("testBackend");
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
   }



   /*******************************************************************************
    ** Test that no-arg constructor completes without throwing.
    *******************************************************************************/
   @Test
   void testNoArgConstructor_doesNotThrow()
   {
      assertThatNoException().isThrownBy(QuickSearchPostInsertCustomizer::new);
   }



   /*******************************************************************************
    ** Test that post-insert with valid records builds correct INDEX events and
    ** calls publisher.publishIndexEvents.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   @Test
   void testPostInsert_withRecords_publishesIndexEvents() throws QException
   {
      QRecord record1 = new QRecord().withValue(PK_FIELD, 1).withValue("name", "Alice");
      QRecord record2 = new QRecord().withValue(PK_FIELD, 2).withValue("name", "Bob");

      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(TABLE_NAME);

      QuickSearchPostInsertCustomizer customizer = new QuickSearchPostInsertCustomizer();
      List<QRecord> result = customizer.postInsert(insertInput, List.of(record1, record2));

      assertThat(result).containsExactly(record1, record2);

      ArgumentCaptor<List<IndexEvent>> captor = ArgumentCaptor.forClass(List.class);
      verify(mockPublisher).publishIndexEvents(captor.capture());

      List<IndexEvent> events = captor.getValue();
      assertThat(events).hasSize(2);
      assertThat(events.get(0).getAction()).isEqualTo(IndexEventAction.INDEX);
      assertThat(events.get(0).getTableName()).isEqualTo(TABLE_NAME);
      assertThat(events.get(0).getRecordId()).isEqualTo("1");
      assertThat(events.get(0).getRecord()).isSameAs(record1);
      assertThat(events.get(1).getAction()).isEqualTo(IndexEventAction.INDEX);
      assertThat(events.get(1).getRecordId()).isEqualTo("2");
      assertThat(events.get(1).getRecord()).isSameAs(record2);
   }



   /*******************************************************************************
    ** Test that post-insert with empty records list returns input without calling
    ** publisher.
    *******************************************************************************/
   @Test
   void testPostInsert_withEmptyRecords_returnsInputWithoutPublishing() throws QException
   {
      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(TABLE_NAME);

      QuickSearchPostInsertCustomizer customizer = new QuickSearchPostInsertCustomizer();
      List<QRecord> result = customizer.postInsert(insertInput, List.of());

      assertThat(result).isEmpty();
      verify(mockPublisher, never()).publishIndexEvents(anyList());
   }



   /*******************************************************************************
    ** Test that post-insert with null records returns null without calling publisher.
    *******************************************************************************/
   @Test
   void testPostInsert_withNullRecords_returnsNullWithoutPublishing() throws QException
   {
      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(TABLE_NAME);

      QuickSearchPostInsertCustomizer customizer = new QuickSearchPostInsertCustomizer();
      List<QRecord> result = customizer.postInsert(insertInput, null);

      assertThat(result).isNull();
      verify(mockPublisher, never()).publishIndexEvents(anyList());
   }



   /*******************************************************************************
    ** Test that when publisher is null, records are still returned (no exception).
    *******************************************************************************/
   @Test
   void testPostInsert_publisherNull_returnsRecordsWithoutThrowing() throws QException
   {
      QuickSearchQBitContext.setPublisher(null);

      QRecord record = new QRecord().withValue(PK_FIELD, 1);
      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(TABLE_NAME);

      QuickSearchPostInsertCustomizer customizer = new QuickSearchPostInsertCustomizer();
      List<QRecord> result = customizer.postInsert(insertInput, List.of(record));

      assertThat(result).containsExactly(record);
   }



   /*******************************************************************************
    ** Test that when publisher throws, a warning is logged and records are returned
    ** (no exception propagated to caller).
    *******************************************************************************/
   @Test
   void testPostInsert_publisherThrows_returnsRecordsWithoutThrowing() throws QException
   {
      doThrow(new QException("publish failed"))
         .when(mockPublisher).publishIndexEvents(anyList());

      QRecord record = new QRecord().withValue(PK_FIELD, 1);
      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(TABLE_NAME);

      QuickSearchPostInsertCustomizer customizer = new QuickSearchPostInsertCustomizer();
      assertThatNoException().isThrownBy(() -> customizer.postInsert(insertInput, List.of(record)));
   }

}

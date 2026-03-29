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

package com.kingsrook.qbits.quicksearch.publisher;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.opensearch.BulkIndexResult;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;


/*******************************************************************************
 ** Tests for SynchronousIndexEventPublisher.
 *******************************************************************************/
class SynchronousIndexEventPublisherTest
{
   private QuickSearchOpenSearchClient    mockClient;
   private SynchronousIndexEventPublisher publisher;

   private static final Integer BATCH_SIZE = 100;



   /*******************************************************************************
    ** Set up a publisher with a mocked client and a single table config.
    *******************************************************************************/
   @BeforeEach
   void setUp()
   {
      mockClient = mock(QuickSearchOpenSearchClient.class);

      QuickSearchableTableConfig ordersConfig = new QuickSearchableTableConfig()
         .withTableName("orders")
         .withPrimaryKeyField("id")
         .withSearchableFields(List.of("orderNumber", "customerName"))
         .withFieldWeights(Map.of("orderNumber", 2, "customerName", 1))
         .withFieldIncludeLabels(Map.of("orderNumber", false, "customerName", false));

      publisher = new SynchronousIndexEventPublisher(mockClient, List.of(ordersConfig), BATCH_SIZE);
   }



   /*******************************************************************************
    ** Test publishIndexEvents with INDEX events calls client.indexDocuments with
    ** correctly built documents.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   @Test
   void testPublishIndexEvents_withIndexEvents_callsClientWithDocuments() throws QException
   {
      QRecord record1 = new QRecord()
         .withValue("id", 1)
         .withValue("orderNumber", "ORD-001")
         .withValue("customerName", "Alice");

      QRecord record2 = new QRecord()
         .withValue("id", 2)
         .withValue("orderNumber", "ORD-002")
         .withValue("customerName", "Bob");

      List<IndexEvent> events = List.of(
         new IndexEvent()
            .withTableName("orders")
            .withRecordId("1")
            .withAction(IndexEventAction.INDEX)
            .withRecord(record1),
         new IndexEvent()
            .withTableName("orders")
            .withRecordId("2")
            .withAction(IndexEventAction.INDEX)
            .withRecord(record2));

      when(mockClient.indexDocuments(anyList(), anyInt())).thenReturn(new BulkIndexResult());

      publisher.publishIndexEvents(events);

      ArgumentCaptor<List<OpenSearchDocument>> docsCaptor = ArgumentCaptor.forClass(List.class);
      ArgumentCaptor<Integer> batchCaptor = ArgumentCaptor.forClass(Integer.class);
      verify(mockClient).indexDocuments(docsCaptor.capture(), batchCaptor.capture());

      List<OpenSearchDocument> capturedDocs = docsCaptor.getValue();
      assertThat(capturedDocs).hasSize(2);
      assertThat(capturedDocs.get(0).getSourceTable()).isEqualTo("orders");
      assertThat(capturedDocs.get(0).getRecordId()).isEqualTo("1");
      assertThat(capturedDocs.get(0).getSearchableText()).contains("ORD-001");
      assertThat(capturedDocs.get(1).getRecordId()).isEqualTo("2");
      assertThat(batchCaptor.getValue()).isEqualTo(BATCH_SIZE);
   }



   /*******************************************************************************
    ** Test publishIndexEvents with empty list makes no client calls.
    *******************************************************************************/
   @Test
   void testPublishIndexEvents_emptyList_noClientCalls() throws QException
   {
      publisher.publishIndexEvents(Collections.emptyList());
      verifyNoInteractions(mockClient);
   }



   /*******************************************************************************
    ** Test publishIndexEvents with null list makes no client calls.
    *******************************************************************************/
   @Test
   void testPublishIndexEvents_nullList_noClientCalls() throws QException
   {
      publisher.publishIndexEvents(null);
      verifyNoInteractions(mockClient);
   }



   /*******************************************************************************
    ** Test publishIndexEvents skips events for unknown tables.
    *******************************************************************************/
   @Test
   void testPublishIndexEvents_unknownTable_skipped() throws QException
   {
      IndexEvent event = new IndexEvent()
         .withTableName("unknownTable")
         .withRecordId("1")
         .withAction(IndexEventAction.INDEX)
         .withRecord(new QRecord().withValue("id", 1));

      publisher.publishIndexEvents(List.of(event));

      verify(mockClient, never()).indexDocuments(anyList(), anyInt());
   }



   /*******************************************************************************
    ** Test publishDeleteEvents calls client.deleteDocument for each event.
    *******************************************************************************/
   @Test
   void testPublishDeleteEvents_callsClientForEachEvent() throws QException
   {
      List<IndexEvent> events = List.of(
         new IndexEvent()
            .withTableName("orders")
            .withRecordId("10")
            .withAction(IndexEventAction.DELETE),
         new IndexEvent()
            .withTableName("orders")
            .withRecordId("20")
            .withAction(IndexEventAction.DELETE));

      publisher.publishDeleteEvents(events);

      verify(mockClient).deleteDocument("orders", "10");
      verify(mockClient).deleteDocument("orders", "20");
   }



   /*******************************************************************************
    ** Test publishDeleteEvents with empty list makes no client calls.
    *******************************************************************************/
   @Test
   void testPublishDeleteEvents_emptyList_noClientCalls() throws QException
   {
      publisher.publishDeleteEvents(Collections.emptyList());
      verifyNoInteractions(mockClient);
   }



   /*******************************************************************************
    ** Test publishDeleteEvents when client throws propagates QException.
    *******************************************************************************/
   @Test
   void testPublishDeleteEvents_clientThrows_propagatesException() throws QException
   {
      doThrow(new QException("delete failed"))
         .when(mockClient).deleteDocument("orders", "1");

      IndexEvent event = new IndexEvent()
         .withTableName("orders")
         .withRecordId("1")
         .withAction(IndexEventAction.DELETE);

      assertThatThrownBy(() -> publisher.publishDeleteEvents(List.of(event)))
         .isInstanceOf(QException.class)
         .hasMessageContaining("delete failed");
   }



   /*******************************************************************************
    ** Test close delegates to client.close().
    *******************************************************************************/
   @Test
   void testClose_delegatesToClient() throws QException
   {
      publisher.close();
      verify(mockClient).close();
   }



   /*******************************************************************************
    ** Test publishIndexEvents uses default primaryKeyField "id" when config
    ** does not specify one.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   @Test
   void testPublishIndexEvents_defaultPrimaryKeyField() throws QException
   {
      QuickSearchableTableConfig configWithoutPk = new QuickSearchableTableConfig()
         .withTableName("products")
         .withSearchableFields(List.of("name"))
         .withFieldWeights(Map.of("name", 1))
         .withFieldIncludeLabels(Map.of("name", false));

      SynchronousIndexEventPublisher pub = new SynchronousIndexEventPublisher(
         mockClient, List.of(configWithoutPk), BATCH_SIZE);

      QRecord record = new QRecord()
         .withValue("id", 42)
         .withValue("name", "Widget");

      IndexEvent event = new IndexEvent()
         .withTableName("products")
         .withRecordId("42")
         .withAction(IndexEventAction.INDEX)
         .withRecord(record);

      when(mockClient.indexDocuments(anyList(), anyInt())).thenReturn(new BulkIndexResult());

      pub.publishIndexEvents(List.of(event));

      ArgumentCaptor<List<OpenSearchDocument>> docsCaptor = ArgumentCaptor.forClass(List.class);
      verify(mockClient).indexDocuments(docsCaptor.capture(), anyInt());

      List<OpenSearchDocument> docs = docsCaptor.getValue();
      assertThat(docs).hasSize(1);
      assertThat(docs.get(0).getRecordId()).isEqualTo("42");
      assertThat(docs.get(0).getSourceTable()).isEqualTo("products");
   }



   /*******************************************************************************
    ** Test publishDeleteEvents with null list makes no client calls.
    *******************************************************************************/
   @Test
   void testPublishDeleteEvents_nullList_noClientCalls() throws QException
   {
      publisher.publishDeleteEvents(null);
      verifyNoInteractions(mockClient);
   }

}

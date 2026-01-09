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
package com.kingsrook.qbits.quicksearch.opensearch;


import java.time.Instant;
import java.util.List;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Tests for QuickSearchOpenSearchClient.
 *******************************************************************************/
class QuickSearchOpenSearchClientTest
{

   /***************************************************************************
    ** Test constructor with valid config creates client.
    ***************************************************************************/
   @Test
   void testConstructor_validConfig_createsClient() throws Exception
   {
      QuickSearchQBitConfig config = createTestConfig();
      QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(config);

      assertThat(client).isNotNull();
      client.close();
   }



   /***************************************************************************
    ** Test constructor with SSL enabled.
    ***************************************************************************/
   @Test
   void testConstructor_withSsl_createsClient() throws Exception
   {
      QuickSearchQBitConfig config = createTestConfig()
         .withUseSsl(true)
         .withOpensearchPort(443);

      QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(config);

      assertThat(client).isNotNull();
      client.close();
   }



   /***************************************************************************
    ** Test constructor with credentials.
    ***************************************************************************/
   @Test
   void testConstructor_withCredentials_createsClient() throws Exception
   {
      QuickSearchQBitConfig config = createTestConfig()
         .withOpensearchUsername("testuser")
         .withOpensearchPassword("testpass");

      QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(config);

      assertThat(client).isNotNull();
      client.close();
   }



   /***************************************************************************
    ** Test ensureIndexExists throws when server unavailable.
    ***************************************************************************/
   @Test
   void testEnsureIndexExists_serverUnavailable_throws() throws Exception
   {
      QuickSearchQBitConfig config = createTestConfig();
      QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(config);

      assertThatThrownBy(() -> client.ensureIndexExists())
         .isInstanceOf(QException.class)
         .hasMessageContaining("Failed to ensure index exists");

      client.close();
   }



   /***************************************************************************
    ** Test indexDocument throws when server unavailable.
    ***************************************************************************/
   @Test
   void testIndexDocument_serverUnavailable_throws() throws Exception
   {
      QuickSearchQBitConfig config = createTestConfig();
      QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(config);

      OpenSearchDocument document = new OpenSearchDocument()
         .withSourceTable("test")
         .withRecordId("1")
         .withSearchableText("test content")
         .withIndexedAt(Instant.now());

      assertThatThrownBy(() -> client.indexDocument(document))
         .isInstanceOf(Exception.class);

      client.close();
   }



   /***************************************************************************
    ** Test indexDocuments with empty list does nothing.
    ***************************************************************************/
   @Test
   void testIndexDocuments_emptyList_doesNothing() throws Exception
   {
      QuickSearchQBitConfig config = createTestConfig();
      QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(config);

      // Should not throw even though server is unavailable
      client.indexDocuments(List.of());

      client.close();
   }



   /***************************************************************************
    ** Test indexDocuments throws when server unavailable.
    ***************************************************************************/
   @Test
   void testIndexDocuments_serverUnavailable_throws() throws Exception
   {
      QuickSearchQBitConfig config = createTestConfig();
      QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(config);

      List<OpenSearchDocument> documents = List.of(
         new OpenSearchDocument()
            .withSourceTable("test")
            .withRecordId("1")
            .withSearchableText("test")
            .withIndexedAt(Instant.now())
      );

      assertThatThrownBy(() -> client.indexDocuments(documents))
         .isInstanceOf(Exception.class);

      client.close();
   }



   /***************************************************************************
    ** Test deleteDocument throws when server unavailable.
    ***************************************************************************/
   @Test
   void testDeleteDocument_serverUnavailable_throws() throws Exception
   {
      QuickSearchQBitConfig config = createTestConfig();
      QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(config);

      assertThatThrownBy(() -> client.deleteDocument("test", "1"))
         .isInstanceOf(QException.class)
         .hasMessageContaining("Failed to delete document");

      client.close();
   }



   /***************************************************************************
    ** Test deleteDocumentsForTable throws when server unavailable.
    ***************************************************************************/
   @Test
   void testDeleteDocumentsForTable_serverUnavailable_throws() throws Exception
   {
      QuickSearchQBitConfig config = createTestConfig();
      QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(config);

      assertThatThrownBy(() -> client.deleteDocumentsForTable("test"))
         .isInstanceOf(QException.class)
         .hasMessageContaining("Failed to delete documents for table");

      client.close();
   }



   /***************************************************************************
    ** Test search with no table filter throws when server unavailable.
    ***************************************************************************/
   @Test
   void testSearch_noTableFilter_serverUnavailable_throws() throws Exception
   {
      QuickSearchQBitConfig config = createTestConfig();
      QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(config);

      assertThatThrownBy(() -> client.search("test query", 10))
         .isInstanceOf(QException.class)
         .hasMessageContaining("Failed to search documents");

      client.close();
   }



   /***************************************************************************
    ** Test search with table filter throws when server unavailable.
    ***************************************************************************/
   @Test
   void testSearch_withTableFilter_serverUnavailable_throws() throws Exception
   {
      QuickSearchQBitConfig config = createTestConfig();
      QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(config);

      assertThatThrownBy(() -> client.search("test query", "orders", 10))
         .isInstanceOf(QException.class)
         .hasMessageContaining("Failed to search documents");

      client.close();
   }



   /***************************************************************************
    ** Test search with null limit uses default.
    ***************************************************************************/
   @Test
   void testSearch_nullLimit_serverUnavailable_throws() throws Exception
   {
      QuickSearchQBitConfig config = createTestConfig();
      QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(config);

      assertThatThrownBy(() -> client.search("test query", null, null))
         .isInstanceOf(QException.class)
         .hasMessageContaining("Failed to search documents");

      client.close();
   }



   /***************************************************************************
    ** Helper to create test configuration.
    ***************************************************************************/
   private QuickSearchQBitConfig createTestConfig()
   {
      return new QuickSearchQBitConfig()
         .withBackendName("memory")
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-quick-search")
         .withUseSsl(false);
   }

}

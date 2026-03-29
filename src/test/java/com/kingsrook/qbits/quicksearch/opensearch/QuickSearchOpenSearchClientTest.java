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


import java.util.List;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Tests for QuickSearchOpenSearchClient.
 **
 ** All tests use localhost:19876 where nothing is listening, so they exercise
 ** constructor success, error-handling paths, and lifecycle behaviour without
 ** requiring a live OpenSearch instance.
 *******************************************************************************/
class QuickSearchOpenSearchClientTest
{
   private static final String TEST_HOST  = "localhost";
   private static final int    TEST_PORT  = 19876;
   private static final String TEST_INDEX = "test-index";



   /*******************************************************************************
    ** Helper to build a minimal plain-HTTP config.
    *******************************************************************************/
   private QuickSearchQBitConfig plainConfig()
   {
      return (new QuickSearchQBitConfig()
         .withOpensearchHost(TEST_HOST)
         .withOpensearchPort(TEST_PORT)
         .withOpensearchIndexName(TEST_INDEX)
         .withUseSsl(false));
   }



   /*******************************************************************************
    ** Constructor succeeds for plain HTTP (no credentials).
    *******************************************************************************/
   @Test
   void testConstructor_plainHttp_succeeds() throws QException
   {
      QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(plainConfig());
      client.close();
   }



   /*******************************************************************************
    ** Constructor succeeds when useSsl is true.
    *******************************************************************************/
   @Test
   void testConstructor_withSsl_succeeds() throws QException
   {
      QuickSearchQBitConfig config = plainConfig().withUseSsl(true);
      QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(config);
      client.close();
   }



   /*******************************************************************************
    ** Constructor succeeds when both username and password are provided.
    *******************************************************************************/
   @Test
   void testConstructor_withCredentials_succeeds() throws QException
   {
      QuickSearchQBitConfig config = plainConfig()
         .withOpensearchUsername("admin")
         .withOpensearchPassword("secret");
      QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(config);
      client.close();
   }



   /*******************************************************************************
    ** close() does not throw any exception.
    *******************************************************************************/
   @Test
   void testClose_doesNotThrow() throws QException
   {
      QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(plainConfig());
      client.close();
   }



   /*******************************************************************************
    ** close() is idempotent: calling it twice must not throw.
    *******************************************************************************/
   @Test
   void testClose_idempotent() throws QException
   {
      QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(plainConfig());
      client.close();
      client.close();
   }



   /*******************************************************************************
    ** indexDocuments with a null list returns an empty, fully-successful result.
    *******************************************************************************/
   @Test
   void testIndexDocuments_nullList_returnsEmptyResult() throws QException
   {
      QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(plainConfig());

      try
      {
         BulkIndexResult result = client.indexDocuments(null, 100);

         assertThat(result).isNotNull();
         assertThat(result.getSuccessCount()).isEqualTo(0);
         assertThat(result.getFailureCount()).isEqualTo(0);
         assertThat(result.isFullySuccessful()).isTrue();
      }
      finally
      {
         client.close();
      }
   }



   /*******************************************************************************
    ** indexDocuments with an empty list returns an empty, fully-successful result.
    *******************************************************************************/
   @Test
   void testIndexDocuments_emptyList_returnsEmptyResult() throws QException
   {
      QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(plainConfig());

      try
      {
         BulkIndexResult result = client.indexDocuments(List.of(), 100);

         assertThat(result).isNotNull();
         assertThat(result.getSuccessCount()).isEqualTo(0);
         assertThat(result.getFailureCount()).isEqualTo(0);
         assertThat(result.isFullySuccessful()).isTrue();
      }
      finally
      {
         client.close();
      }
   }



   /*******************************************************************************
    ** ensureIndexExists throws QException when the server is unavailable.
    *******************************************************************************/
   @Test
   void testEnsureIndexExists_serverUnavailable_throwsQException() throws QException
   {
      QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(plainConfig());

      try
      {
         assertThatThrownBy(client::ensureIndexExists)
            .isInstanceOf(QException.class)
            .hasMessageContaining(TEST_INDEX);
      }
      finally
      {
         client.close();
      }
   }



   /*******************************************************************************
    ** indexDocument throws QException when the server is unavailable.
    *******************************************************************************/
   @Test
   void testIndexDocument_serverUnavailable_throwsQException() throws QException
   {
      QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(plainConfig());

      OpenSearchDocument doc = new OpenSearchDocument()
         .withSourceTable("orders")
         .withRecordId("1")
         .withRecordLabel("Order #1")
         .withSearchableText("test order");

      try
      {
         assertThatThrownBy(() -> client.indexDocument(doc))
            .isInstanceOf(QException.class)
            .hasMessageContaining("orders:1");
      }
      finally
      {
         client.close();
      }
   }



   /*******************************************************************************
    ** search throws QException when the server is unavailable.
    *******************************************************************************/
   @Test
   void testSearch_serverUnavailable_throwsQException() throws QException
   {
      QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(plainConfig());

      try
      {
         assertThatThrownBy(() -> client.search("hello", null, 10, 0, List.of()))
            .isInstanceOf(QException.class)
            .hasMessageContaining("hello");
      }
      finally
      {
         client.close();
      }
   }



   /*******************************************************************************
    ** deleteDocument throws QException when the server is unavailable.
    *******************************************************************************/
   @Test
   void testDeleteDocument_serverUnavailable_throwsQException() throws QException
   {
      QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(plainConfig());

      try
      {
         assertThatThrownBy(() -> client.deleteDocument("orders", "1"))
            .isInstanceOf(QException.class)
            .hasMessageContaining("orders:1");
      }
      finally
      {
         client.close();
      }
   }

}

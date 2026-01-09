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
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Tests for OpenSearchDocument.
 *******************************************************************************/
class OpenSearchDocumentTest
{

   /***************************************************************************
    ** Test document ID generation.
    ***************************************************************************/
   @Test
   void testGetDocumentId_returnsCompositeKey()
   {
      OpenSearchDocument doc = new OpenSearchDocument()
         .withSourceTable("orders")
         .withRecordId("12345");

      assertThat(doc.getDocumentId()).isEqualTo("orders:12345");
   }



   /***************************************************************************
    ** Test document ID with null source table.
    ***************************************************************************/
   @Test
   void testGetDocumentId_nullSourceTable_handlesGracefully()
   {
      OpenSearchDocument doc = new OpenSearchDocument()
         .withSourceTable(null)
         .withRecordId("12345");

      assertThat(doc.getDocumentId()).isEqualTo("null:12345");
   }



   /***************************************************************************
    ** Test all getters and setters.
    ***************************************************************************/
   @Test
   void testGettersAndSetters()
   {
      Instant now = Instant.now();

      OpenSearchDocument doc = new OpenSearchDocument()
         .withSourceTable("customers")
         .withRecordId("999")
         .withRecordLabel("Customer #999")
         .withSearchableText("john doe acme corp")
         .withIndexedAt(now);

      assertThat(doc.getSourceTable()).isEqualTo("customers");
      assertThat(doc.getRecordId()).isEqualTo("999");
      assertThat(doc.getRecordLabel()).isEqualTo("Customer #999");
      assertThat(doc.getSearchableText()).isEqualTo("john doe acme corp");
      assertThat(doc.getIndexedAt()).isEqualTo(now);
   }



   /***************************************************************************
    ** Test fluent setters return this.
    ***************************************************************************/
   @Test
   void testFluentSetters_returnThis()
   {
      OpenSearchDocument doc = new OpenSearchDocument();

      OpenSearchDocument result = doc
         .withSourceTable("test")
         .withRecordId("1")
         .withRecordLabel("label")
         .withSearchableText("text")
         .withIndexedAt(Instant.now());

      assertThat(result).isSameAs(doc);
   }

}

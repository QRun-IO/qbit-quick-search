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
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Tests for OpenSearchDocument.
 *******************************************************************************/
class OpenSearchDocumentTest
{

   /*******************************************************************************
    ** Test that fluent setters return this and getters return values.
    *******************************************************************************/
   @Test
   void testFluentSetters()
   {
      Instant now = Instant.now();
      Map<String, Object> fieldValues = new HashMap<>();
      fieldValues.put("name", "Alice");

      OpenSearchDocument doc = new OpenSearchDocument()
         .withSourceTable("users")
         .withRecordId("7")
         .withRecordLabel("Alice")
         .withSearchableText("Alice Smith admin user")
         .withIndexedAt(now)
         .withFieldValues(fieldValues);

      assertThat(doc.getSourceTable()).isEqualTo("users");
      assertThat(doc.getRecordId()).isEqualTo("7");
      assertThat(doc.getRecordLabel()).isEqualTo("Alice");
      assertThat(doc.getSearchableText()).isEqualTo("Alice Smith admin user");
      assertThat(doc.getIndexedAt()).isEqualTo(now);
      assertThat(doc.getFieldValues()).containsEntry("name", "Alice");
   }

   /*******************************************************************************
    ** Test that withX methods return this.
    *******************************************************************************/
   @Test
   void testWithReturnsThis()
   {
      OpenSearchDocument doc = new OpenSearchDocument();
      assertThat(doc.withSourceTable("t")).isSameAs(doc);
      assertThat(doc.withRecordId("1")).isSameAs(doc);
      assertThat(doc.withRecordLabel("l")).isSameAs(doc);
      assertThat(doc.withSearchableText("text")).isSameAs(doc);
      assertThat(doc.withIndexedAt(Instant.now())).isSameAs(doc);
      assertThat(doc.withFieldValues(Map.of())).isSameAs(doc);
   }

   /*******************************************************************************
    ** Test getDocumentId() returns "sourceTable:recordId".
    *******************************************************************************/
   @Test
   void testGetDocumentId()
   {
      OpenSearchDocument doc = new OpenSearchDocument()
         .withSourceTable("orders")
         .withRecordId("42");

      assertThat(doc.getDocumentId()).isEqualTo("orders:42");
   }

   /*******************************************************************************
    ** Test getDocumentId() when sourceTable is null returns concatenation with null.
    *******************************************************************************/
   @Test
   void testGetDocumentIdWithNullSourceTable()
   {
      OpenSearchDocument doc = new OpenSearchDocument()
         .withRecordId("10");

      assertThat(doc.getDocumentId()).isEqualTo("null:10");
   }

}

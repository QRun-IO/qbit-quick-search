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

package com.kingsrook.qbits.quicksearch.opensearch;


import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


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
    ** Test getDocumentId() when sourceTable is null throws IllegalStateException.
    *******************************************************************************/
   @Test
   void testGetDocumentIdWithNullSourceTable()
   {
      OpenSearchDocument doc = new OpenSearchDocument()
         .withRecordId("10");

      assertThatThrownBy(doc::getDocumentId)
         .isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("sourceTable=null");
   }

}

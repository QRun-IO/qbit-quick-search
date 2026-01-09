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


import java.util.List;
import com.kingsrook.qbits.quicksearch.BaseQuickSearchTest;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Tests for IndexingUtils.
 *******************************************************************************/
class IndexingUtilsTest extends BaseQuickSearchTest
{

   /***************************************************************************
    ** Test normalizeSearchText with normal input.
    ***************************************************************************/
   @Test
   void testNormalizeSearchText_normalInput_returnsLowercase()
   {
      String result = IndexingUtils.normalizeSearchText("Hello World");
      assertThat(result).isEqualTo("hello world");
   }



   /***************************************************************************
    ** Test normalizeSearchText with null input returns empty string.
    ***************************************************************************/
   @Test
   void testNormalizeSearchText_nullInput_returnsEmptyString()
   {
      String result = IndexingUtils.normalizeSearchText(null);
      assertThat(result).isEqualTo("");
   }



   /***************************************************************************
    ** Test normalizeSearchText with blank input returns empty string.
    ***************************************************************************/
   @Test
   void testNormalizeSearchText_blankInput_returnsEmptyString()
   {
      String result = IndexingUtils.normalizeSearchText("   ");
      assertThat(result).isEqualTo("");
   }



   /***************************************************************************
    ** Test normalizeSearchText trims leading/trailing whitespace.
    ***************************************************************************/
   @Test
   void testNormalizeSearchText_extraWhitespace_trims()
   {
      String result = IndexingUtils.normalizeSearchText("  Hello   World  ");
      assertThat(result).isEqualTo("hello   world");
   }



   /***************************************************************************
    ** Test buildDocument with valid record.
    ***************************************************************************/
   @Test
   void testBuildDocument_validRecord_buildsDocument()
   {
      QTableMetaData table = createTestEntityTable();
      QRecord record = new QRecord()
         .withValue("id", 123)
         .withValue("name", "Test Item")
         .withValue("description", "A test description")
         .withValue("status", "ACTIVE");

      List<String> searchableFields = List.of("name", "description", "status");

      OpenSearchDocument doc = IndexingUtils.buildDocument(record, table, searchableFields);

      assertThat(doc.getSourceTable()).isEqualTo(TEST_TABLE_NAME);
      assertThat(doc.getRecordId()).isEqualTo("123");
      assertThat(doc.getDocumentId()).isEqualTo(TEST_TABLE_NAME + ":123");
      assertThat(doc.getSearchableText()).contains("Test Item");
      assertThat(doc.getSearchableText()).contains("A test description");
      assertThat(doc.getSearchableText()).contains("ACTIVE");
   }



   /***************************************************************************
    ** Test buildDocument with null field values skips those fields.
    ***************************************************************************/
   @Test
   void testBuildDocument_nullFieldValues_skipsNulls()
   {
      QTableMetaData table = createTestEntityTable();
      QRecord record = new QRecord()
         .withValue("id", 456)
         .withValue("name", "Test")
         .withValue("description", null)
         .withValue("status", null);

      List<String> searchableFields = List.of("name", "description", "status");

      OpenSearchDocument doc = IndexingUtils.buildDocument(record, table, searchableFields);

      assertThat(doc.getSearchableText()).isEqualTo("Test");
   }



   /***************************************************************************
    ** Test buildDocument with empty searchable fields list returns empty text.
    ***************************************************************************/
   @Test
   void testBuildDocument_emptySearchableFields_returnsEmptyText()
   {
      QTableMetaData table = createTestEntityTable();
      QRecord record = new QRecord()
         .withValue("id", 789)
         .withValue("name", "All Fields")
         .withValue("description", "Should include all")
         .withValue("status", "PENDING");

      List<String> searchableFields = List.of();

      OpenSearchDocument doc = IndexingUtils.buildDocument(record, table, searchableFields);

      assertThat(doc.getSearchableText()).isEmpty();
   }

}

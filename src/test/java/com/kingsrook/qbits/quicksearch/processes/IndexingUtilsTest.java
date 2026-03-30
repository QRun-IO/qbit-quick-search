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


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Tests for IndexingUtils text normalization and document building.
 *******************************************************************************/
class IndexingUtilsTest
{

   /*******************************************************************************
    ** Test that normal input is lowercased and trimmed.
    *******************************************************************************/
   @Test
   void testNormalizeSearchText_normalInput_lowercaseTrimmed()
   {
      assertThat(IndexingUtils.normalizeSearchText("Hello World")).isEqualTo("hello world");
   }


   /*******************************************************************************
    ** Test that null input returns empty string.
    *******************************************************************************/
   @Test
   void testNormalizeSearchText_null_returnsEmpty()
   {
      assertThat(IndexingUtils.normalizeSearchText(null)).isEqualTo("");
   }


   /*******************************************************************************
    ** Test that empty string returns empty string.
    *******************************************************************************/
   @Test
   void testNormalizeSearchText_empty_returnsEmpty()
   {
      assertThat(IndexingUtils.normalizeSearchText("")).isEqualTo("");
   }


   /*******************************************************************************
    ** Test that blank string (whitespace only) returns empty string.
    *******************************************************************************/
   @Test
   void testNormalizeSearchText_blank_returnsEmpty()
   {
      assertThat(IndexingUtils.normalizeSearchText("  ")).isEqualTo("");
   }


   /*******************************************************************************
    ** Test that multiple internal whitespace is collapsed to single space.
    *******************************************************************************/
   @Test
   void testNormalizeSearchText_multipleSpaces_collapsed()
   {
      assertThat(IndexingUtils.normalizeSearchText("  hello   world  ")).isEqualTo("hello world");
   }


   /*******************************************************************************
    ** Test that mixed case input is fully lowercased.
    *******************************************************************************/
   @Test
   void testNormalizeSearchText_mixedCase_lowercased()
   {
      assertThat(IndexingUtils.normalizeSearchText("HeLLo WoRLd")).isEqualTo("hello world");
   }


   /*******************************************************************************
    ** Test that field values are concatenated with space separator.
    *******************************************************************************/
   @Test
   void testBuildSearchableText_normalFields_concatenated()
   {
      QRecord record = new QRecord();
      record.setValue("name", "John");
      record.setValue("description", "Developer");

      String result = IndexingUtils.buildSearchableText(record, List.of("name", "description"), Map.of());

      assertThat(result).isEqualTo("John Developer");
   }


   /*******************************************************************************
    ** Test that null field values are skipped.
    *******************************************************************************/
   @Test
   void testBuildSearchableText_nullValue_skipped()
   {
      QRecord record = new QRecord();

      String result = IndexingUtils.buildSearchableText(record, List.of("name"), Map.of());

      assertThat(result).isEqualTo("");
   }


   /*******************************************************************************
    ** Test that fields with includeLabel=true are prefixed with fieldName.
    *******************************************************************************/
   @Test
   void testBuildSearchableText_includeLabel_prefixed()
   {
      QRecord record = new QRecord();
      record.setValue("name", "John");

      String result = IndexingUtils.buildSearchableText(record, List.of("name"), Map.of("name", Boolean.TRUE));

      assertThat(result).isEqualTo("name: John");
   }


   /*******************************************************************************
    ** Test that empty fields list returns empty string.
    *******************************************************************************/
   @Test
   void testBuildSearchableText_emptyFields_returnsEmpty()
   {
      QRecord record = new QRecord();
      record.setValue("name", "John");

      String result = IndexingUtils.buildSearchableText(record, List.of(), Map.of());

      assertThat(result).isEqualTo("");
   }


   /*******************************************************************************
    ** Test that null fields list returns empty string.
    *******************************************************************************/
   @Test
   void testBuildSearchableText_nullFields_returnsEmpty()
   {
      QRecord record = new QRecord();
      record.setValue("name", "John");

      String result = IndexingUtils.buildSearchableText(record, null, Map.of());

      assertThat(result).isEqualTo("");
   }


   /*******************************************************************************
    ** Test that buildDocument populates all fields correctly.
    *******************************************************************************/
   @Test
   void testBuildDocument_validRecord_allFieldsPopulated()
   {
      QRecord record = new QRecord();
      record.setValue("id", 42);
      record.setValue("name", "John");
      record.setValue("description", "Developer");
      record.setRecordLabel("John Doe");

      OpenSearchDocument doc = IndexingUtils.buildDocument(
         record,
         "customers",
         "id",
         List.of("name", "description"),
         Map.of(),
         Map.of()
      );

      assertThat(doc.getSourceTable()).isEqualTo("customers");
      assertThat(doc.getRecordId()).isEqualTo("42");
      assertThat(doc.getRecordLabel()).isEqualTo("John Doe");
      assertThat(doc.getSearchableText()).isEqualTo("John Developer");
      assertThat(doc.getFieldValues()).containsKey("name");
      assertThat(doc.getFieldValues()).containsKey("description");
      assertThat(doc.getIndexedAt()).isNotNull();
   }


   /*******************************************************************************
    ** Test that buildDocument with null primary key value yields null recordId.
    *******************************************************************************/
   @Test
   void testBuildDocument_nullPrimaryKey_returnsNull()
   {
      QRecord record = new QRecord();
      record.setValue("name", "John");

      OpenSearchDocument doc = IndexingUtils.buildDocument(
         record,
         "customers",
         "id",
         List.of("name"),
         Map.of(),
         Map.of()
      );

      assertThat(doc).isNull();
   }


   /*******************************************************************************
    ** Test that null field values are not included in fieldValues map.
    *******************************************************************************/
   @Test
   void testBuildDocument_nullFieldValue_notInFieldValues()
   {
      QRecord record = new QRecord();
      record.setValue("id", 1);
      record.setValue("name", "John");

      OpenSearchDocument doc = IndexingUtils.buildDocument(
         record,
         "customers",
         "id",
         List.of("name", "description"),
         Map.of(),
         Map.of()
      );

      assertThat(doc.getFieldValues()).containsKey("name");
      assertThat(doc.getFieldValues()).doesNotContainKey("description");
   }


   /*******************************************************************************
    ** Test that buildDocument with empty searchable fields yields empty searchableText.
    *******************************************************************************/
   @Test
   void testBuildDocument_emptySearchableFields_emptySearchableText()
   {
      QRecord record = new QRecord();
      record.setValue("id", 1);

      OpenSearchDocument doc = IndexingUtils.buildDocument(
         record,
         "customers",
         "id",
         List.of(),
         Map.of(),
         Map.of()
      );

      assertThat(doc.getSearchableText()).isEqualTo("");
   }



   /*******************************************************************************
    ** Test the new buildDocument overload with QuickSearchableTableConfig that
    ** has a recordLabelFormat -- verifies the formatted label is applied.
    *******************************************************************************/
   @Test
   void testBuildDocument_tableConfig_withRecordLabelFormat_usesFormattedLabel()
   {
      QRecord record = new QRecord();
      record.setValue("id", 1);
      record.setValue("firstName", "John");
      record.setValue("lastName", "Doe");
      record.setRecordLabel("QQQ Default Label");

      QuickSearchableTableConfig tableConfig = new QuickSearchableTableConfig()
         .withTableName("person")
         .withPrimaryKeyField("id")
         .withSearchableFields(List.of("firstName", "lastName"))
         .withFieldWeights(Map.of("firstName", 2, "lastName", 1))
         .withFieldIncludeLabels(Map.of("firstName", false, "lastName", false))
         .withRecordLabelFormat("%s %s")
         .withRecordLabelFields(List.of("firstName", "lastName"));

      OpenSearchDocument doc = IndexingUtils.buildDocument(record, tableConfig);

      assertThat(doc).isNotNull();
      assertThat(doc.getRecordLabel()).isEqualTo("John Doe");
      assertThat(doc.getSourceTable()).isEqualTo("person");
      assertThat(doc.getSearchableText()).isEqualTo("John Doe");
   }



   /*******************************************************************************
    ** Test the new buildDocument overload with QuickSearchableTableConfig that
    ** does NOT have a recordLabelFormat -- falls back to QRecord.getRecordLabel().
    *******************************************************************************/
   @Test
   void testBuildDocument_tableConfig_withoutRecordLabelFormat_usesQRecordLabel()
   {
      QRecord record = new QRecord();
      record.setValue("id", 42);
      record.setValue("name", "Widget");
      record.setRecordLabel("Widget Record");

      QuickSearchableTableConfig tableConfig = new QuickSearchableTableConfig()
         .withTableName("product")
         .withPrimaryKeyField("id")
         .withSearchableFields(List.of("name"))
         .withFieldWeights(Map.of("name", 1))
         .withFieldIncludeLabels(Map.of("name", false));

      OpenSearchDocument doc = IndexingUtils.buildDocument(record, tableConfig);

      assertThat(doc).isNotNull();
      assertThat(doc.getRecordLabel()).isEqualTo("Widget Record");
   }



   /*******************************************************************************
    ** Test the new buildDocument overload maps all fields into fieldValues.
    *******************************************************************************/
   @Test
   void testBuildDocument_tableConfig_allFieldsMapped()
   {
      QRecord record = new QRecord();
      record.setValue("id", 1);
      record.setValue("firstName", "Jane");
      record.setValue("lastName", "Smith");
      record.setValue("email", "jane@example.com");

      QuickSearchableTableConfig tableConfig = new QuickSearchableTableConfig()
         .withTableName("person")
         .withPrimaryKeyField("id")
         .withSearchableFields(List.of("firstName", "lastName", "email"))
         .withFieldWeights(Map.of("firstName", 3, "lastName", 2, "email", 1))
         .withFieldIncludeLabels(Map.of("firstName", false, "lastName", false, "email", false));

      OpenSearchDocument doc = IndexingUtils.buildDocument(record, tableConfig);

      assertThat(doc).isNotNull();
      assertThat(doc.getFieldValues()).containsKey("firstName");
      assertThat(doc.getFieldValues()).containsKey("lastName");
      assertThat(doc.getFieldValues()).containsKey("email");
      assertThat(doc.getFieldValues().get("firstName")).isEqualTo("Jane");
   }



   /*******************************************************************************
    ** Test the new buildDocument overload returns null when primary key is null.
    *******************************************************************************/
   @Test
   void testBuildDocument_tableConfig_nullPrimaryKey_returnsNull()
   {
      QRecord record = new QRecord();
      record.setValue("name", "NoId");

      QuickSearchableTableConfig tableConfig = new QuickSearchableTableConfig()
         .withTableName("person")
         .withPrimaryKeyField("id")
         .withSearchableFields(List.of("name"))
         .withFieldWeights(Map.of("name", 1))
         .withFieldIncludeLabels(Map.of("name", false));

      OpenSearchDocument doc = IndexingUtils.buildDocument(record, tableConfig);

      assertThat(doc).isNull();
   }

}

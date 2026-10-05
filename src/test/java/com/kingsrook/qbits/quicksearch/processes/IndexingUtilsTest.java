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


import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Tests for IndexingUtils text normalization and document building.
 *******************************************************************************/
class IndexingUtilsTest
{

   /*******************************************************************************
    ** Test that normal input is trimmed and its case is preserved (the analyzer
    ** lower-cases at index and search time).
    *******************************************************************************/
   @Test
   void testNormalizeSearchText_normalInput_trimmedCasePreserved()
   {
      assertThat(IndexingUtils.normalizeSearchText("  Hello World  ")).isEqualTo("Hello World");
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
    ** Test that mixed case input is not lower-cased.
    *******************************************************************************/
   @Test
   void testNormalizeSearchText_mixedCase_preserved()
   {
      assertThat(IndexingUtils.normalizeSearchText("HeLLo WoRLd")).isEqualTo("HeLLo WoRLd");
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
    ** has a recordLabelFormat; verifies the formatted label is applied.
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
    ** does NOT have a recordLabelFormat; falls back to QRecord.getRecordLabel().
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



   /*******************************************************************************
    ** Clear any QContext a test installed.
    *******************************************************************************/
   @AfterEach
   void clearContext()
   {
      QContext.clear();
   }



   /*******************************************************************************
    ** Install a QInstance holding one "person" table with a record label format
    ** and a labelled "sku" field.
    *******************************************************************************/
   private void initContextWithPersonTable()
   {
      QInstance qInstance = new QInstance();
      qInstance.addBackend(new QBackendMetaData().withName("memory").withBackendType(MemoryBackendModule.class));
      qInstance.withInstanceDefaultAuthentication(new QAuthenticationMetaData().withName("anonymous").withType(QAuthenticationType.FULLY_ANONYMOUS));
      qInstance.addTable(new QTableMetaData()
         .withName("person")
         .withBackendName("memory")
         .withPrimaryKeyField("id")
         .withRecordLabelFormat("%s %s")
         .withRecordLabelFields(List.of("firstName", "lastName"))
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("firstName", QFieldType.STRING))
         .withField(new QFieldMetaData("lastName", QFieldType.STRING))
         .withField(new QFieldMetaData("sku", QFieldType.STRING).withLabel("SKU Code"))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME)));
      QContext.init(qInstance, new QSession());
   }



   /*******************************************************************************
    ** Test: a display value (possible-value label, formatted date) wins over
    ** the raw value, both in fieldValues and in the searchable text.
    *******************************************************************************/
   @Test
   void testBuildDocument_displayValuePresent_usedInsteadOfRawValue()
   {
      QRecord record = new QRecord();
      record.setValue("id", 1);
      record.setValue("statusId", 3);
      record.setDisplayValue("statusId", "Shipped");

      QuickSearchableTableConfig tableConfig = new QuickSearchableTableConfig()
         .withTableName("order")
         .withPrimaryKeyField("id")
         .withSearchableFields(List.of("statusId"));

      OpenSearchDocument doc = IndexingUtils.buildDocument(record, tableConfig);

      assertThat(doc.getFieldValues().get("statusId")).isEqualTo("Shipped");
      assertThat(doc.getSearchableText()).isEqualTo("Shipped");
   }



   /*******************************************************************************
    ** Test: non-string values are stored as strings in fieldValues, since the
    ** mapping types every fieldValues.* entry as text.
    *******************************************************************************/
   @Test
   void testBuildDocument_integerValue_storedAsString()
   {
      QRecord record = new QRecord();
      record.setValue("id", 1);
      record.setValue("quantity", 42);

      QuickSearchableTableConfig tableConfig = new QuickSearchableTableConfig()
         .withTableName("order")
         .withPrimaryKeyField("id")
         .withSearchableFields(List.of("quantity"));

      OpenSearchDocument doc = IndexingUtils.buildDocument(record, tableConfig);

      assertThat(doc.getFieldValues().get("quantity")).isEqualTo("42");
      assertThat(doc.getSearchableText()).isEqualTo("42");
   }



   /*******************************************************************************
    ** Test: values longer than maxFieldLength are truncated.
    *******************************************************************************/
   @Test
   void testBuildDocument_valueLongerThanMaxFieldLength_truncated()
   {
      QRecord record = new QRecord();
      record.setValue("id", 1);
      record.setValue("notes", "abcdefghijklmnop");

      QuickSearchableTableConfig tableConfig = new QuickSearchableTableConfig()
         .withTableName("order")
         .withPrimaryKeyField("id")
         .withSearchableFields(List.of("notes"))
         .withMaxFieldLength(5);

      OpenSearchDocument doc = IndexingUtils.buildDocument(record, tableConfig);

      assertThat(doc.getFieldValues().get("notes")).isEqualTo("abcde");
      assertThat(doc.getSearchableText()).isEqualTo("abcde");
   }



   /*******************************************************************************
    ** Test: a record without a label gets one from the table's record label
    ** format when the table is known to the QInstance in QContext.
    *******************************************************************************/
   @Test
   void testBuildDocument_noRecordLabel_fallsBackToTableRecordLabelFormat()
   {
      initContextWithPersonTable();

      QRecord record = new QRecord();
      record.setValue("id", 1);
      record.setValue("firstName", "Ada");
      record.setValue("lastName", "Lovelace");

      QuickSearchableTableConfig tableConfig = new QuickSearchableTableConfig()
         .withTableName("person")
         .withPrimaryKeyField("id")
         .withSearchableFields(List.of("firstName"));

      OpenSearchDocument doc = IndexingUtils.buildDocument(record, tableConfig);

      assertThat(doc.getRecordLabel()).isEqualTo("Ada Lovelace");
   }



   /*******************************************************************************
    ** Test: a recordLabelFormat that cannot be applied to the values never
    ** throws; the default label is kept.
    *******************************************************************************/
   @Test
   void testBuildDocument_badRecordLabelFormat_fallsBackToDefaultLabel()
   {
      QRecord record = new QRecord();
      record.setValue("id", 1);
      record.setValue("firstName", "Ada");
      record.setRecordLabel("Default Label");

      QuickSearchableTableConfig tableConfig = new QuickSearchableTableConfig()
         .withTableName("person")
         .withPrimaryKeyField("id")
         .withSearchableFields(List.of("firstName"))
         .withRecordLabelFormat("%d-%s")
         .withRecordLabelFields(List.of("firstName", "lastName"));

      OpenSearchDocument doc = IndexingUtils.buildDocument(record, tableConfig);

      assertThat(doc).isNotNull();
      assertThat(doc.getRecordLabel()).isEqualTo("Default Label");
   }



   /*******************************************************************************
    ** Test: the document version is the basepull timestamp in epoch millis,
    ** and null when the record has no timestamp or the table has no field.
    *******************************************************************************/
   @Test
   void testBuildDocument_version_fromBasepullTimestampField()
   {
      Instant modifyDate = Instant.parse("2026-10-04T12:34:56.789Z");

      QRecord record = new QRecord();
      record.setValue("id", 1);
      record.setValue("name", "Widget");
      record.setValue("modifyDate", modifyDate);

      QuickSearchableTableConfig withTimestamp = new QuickSearchableTableConfig()
         .withTableName("product")
         .withPrimaryKeyField("id")
         .withSearchableFields(List.of("name"))
         .withBasepullTimestampField("modifyDate");

      assertThat(IndexingUtils.buildDocument(record, withTimestamp).getVersion()).isEqualTo(modifyDate.toEpochMilli());

      QRecord withoutValue = new QRecord().withValue("id", 2).withValue("name", "Gadget");
      assertThat(IndexingUtils.buildDocument(withoutValue, withTimestamp).getVersion()).isNull();

      QuickSearchableTableConfig withoutTimestamp = new QuickSearchableTableConfig()
         .withTableName("product")
         .withPrimaryKeyField("id")
         .withSearchableFields(List.of("name"));
      assertThat(IndexingUtils.buildDocument(record, withoutTimestamp).getVersion()).isNull();
   }



   /*******************************************************************************
    ** Test: the label prefix uses the QQQ field label when the table is known,
    ** and the field name otherwise.
    *******************************************************************************/
   @Test
   void testBuildSearchableText_includeLabel_usesFieldLabelWhenTableKnown()
   {
      QRecord record = new QRecord();
      record.setValue("id", 1);
      record.setValue("sku", "AB-1");

      QuickSearchableTableConfig tableConfig = new QuickSearchableTableConfig()
         .withTableName("person")
         .withPrimaryKeyField("id")
         .withSearchableFields(List.of("sku"))
         .withFieldIncludeLabels(Map.of("sku", true));

      assertThat(IndexingUtils.buildDocument(record, tableConfig).getSearchableText()).isEqualTo("sku: AB-1");

      initContextWithPersonTable();
      assertThat(IndexingUtils.buildDocument(record, tableConfig).getSearchableText()).isEqualTo("SKU Code: AB-1");
   }



   /*******************************************************************************
    ** Test: a null includeLabels map is tolerated.
    *******************************************************************************/
   @Test
   void testBuildSearchableText_nullIncludeLabels_doesNotThrow()
   {
      QRecord record = new QRecord();
      record.setValue("name", "Widget");

      assertThat(IndexingUtils.buildSearchableText(record, List.of("name"), null)).isEqualTo("Widget");
   }

}

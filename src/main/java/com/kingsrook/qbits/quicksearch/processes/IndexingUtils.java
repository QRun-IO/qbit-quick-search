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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.values.QValueFormatter;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.config.QuickSearchFieldRules;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFieldRole;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Text normalization and OpenSearch document building.
 **
 ** Indexed text uses a record's display value when the query generated one
 ** (possible-value labels, formatted dates) and the raw value otherwise. Every
 ** value is stored as a string (fieldValues are mapped as text), truncated to
 ** the table's maxFieldLength when set.
 *******************************************************************************/
public class IndexingUtils
{
   private static final QLogger LOG = QLogger.getLogger(IndexingUtils.class);

   static final int MAX_ROLE_VALUE_LENGTH = 500;



   /*******************************************************************************
    ** Private constructor to prevent instantiation.
    *******************************************************************************/
   private IndexingUtils()
   {
   }



   /*******************************************************************************
    ** Trim and collapse whitespace. Lower-casing is left to the analyzers.
    *******************************************************************************/
   public static String normalizeSearchText(String text)
   {
      if(text == null || text.isBlank())
      {
         return ("");
      }
      return (text.trim().replaceAll("\\s+", " "));
   }



   /*******************************************************************************
    ** Concatenate the display values of the given fields. A field whose
    ** includeLabels entry is true is prefixed with the field's label (or name).
    *******************************************************************************/
   public static String buildSearchableText(QRecord record, List<String> fields, Map<String, Boolean> includeLabels)
   {
      return (buildSearchableText(record, fields, includeLabels, null, null));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static String buildSearchableText(QRecord record, List<String> fields, Map<String, Boolean> includeLabels, QTableMetaData table, Integer maxFieldLength)
   {
      if(fields == null || fields.isEmpty())
      {
         return ("");
      }

      Map<String, Boolean> labels = includeLabels == null ? Collections.emptyMap() : includeLabels;
      StringBuilder        sb     = new StringBuilder();

      for(String fieldName : fields)
      {
         if(table != null && !QuickSearchFieldRules.isIndexable(table, fieldName))
         {
            continue;
         }
         String value = displayValue(record, fieldName, maxFieldLength);
         if(value == null)
         {
            continue;
         }

         if(sb.length() > 0)
         {
            sb.append(" ");
         }

         if(Boolean.TRUE.equals(labels.get(fieldName)))
         {
            String label = (table != null && table.getFields().containsKey(fieldName) && StringUtils.hasContent(table.getField(fieldName).getLabel()))
               ? table.getField(fieldName).getLabel() : fieldName;
            sb.append(label).append(": ");
         }

         sb.append(value);
      }

      return (sb.toString().trim());
   }



   /*******************************************************************************
    ** Display value when present, else the raw value as a string; blank is null.
    *******************************************************************************/
   static String displayValue(QRecord record, String fieldName, Integer maxFieldLength)
   {
      String value = record.getDisplayValue(fieldName);
      if(!StringUtils.hasContent(value))
      {
         value = record.getValueString(fieldName);
      }
      if(!StringUtils.hasContent(value))
      {
         return (null);
      }
      if(maxFieldLength != null && value.length() > maxFieldLength)
      {
         value = value.substring(0, maxFieldLength);
      }
      return (value);
   }



   /*******************************************************************************
    ** Build a document from explicit field lists (no table config).
    *******************************************************************************/
   public static OpenSearchDocument buildDocument(QRecord record, String tableName, String primaryKeyField, List<String> fields, Map<String, Integer> fieldWeights, Map<String, Boolean> includeLabels)
   {
      return (buildDocument(record, tableName, primaryKeyField, fields, includeLabels, lookupTable(tableName), null, null, null));
   }



   /*******************************************************************************
    ** Build a document for a configured table: display values, label fallback,
    ** truncation, and an external version from the basepull timestamp field.
    ** Returns null when metadata is unavailable or the primary key is missing
    ** or excluded by the field policy.
    *******************************************************************************/
   public static OpenSearchDocument buildDocument(QRecord record, QuickSearchableTableConfig tableConfig)
   {
      QTableMetaData     table = lookupTable(tableConfig.getTableName());
      OpenSearchDocument doc   = buildDocument(record, tableConfig.getTableName(), tableConfig.getPrimaryKeyField(), tableConfig.getSearchableFields(),
         tableConfig.getFieldIncludeLabels(), table, tableConfig.getMaxFieldLength(), tableConfig.getBasepullTimestampField(), tableConfig.getRecordLabelFields());

      if(doc != null && tableConfig.getRecordLabelFormat() != null && tableConfig.getRecordLabelFields() != null)
      {
         doc.withRecordLabel(formatLabel(record, tableConfig, doc.getRecordLabel()));
      }

      if(doc != null)
      {
         applyRolesAndAnalyzers(doc, record, tableConfig, table);
      }

      return (doc);
   }



   /*******************************************************************************
    ** Apply the fields' roles and analyzers (from quick_search_index_field):
    ** only snippet fields go into searchableText; WORD, ENGLISH and EXACT
    ** fields are also written to fieldValuesWord, fieldValuesEnglish and
    ** fieldValuesExact (lower-cased) for their analyzers; TITLE fields make the
    ** record label, SUBTITLE fields the subtitle and DISPLAY fields the
    ** displayValues (by field label). A table whose fields are all plain BODY
    ** fields is left exactly as before.
    *******************************************************************************/
   static void applyRolesAndAnalyzers(OpenSearchDocument doc, QRecord record, QuickSearchableTableConfig tableConfig, QTableMetaData table)
   {
      List<String> fields = table == null ? List.of() : QuickSearchFieldRules.indexableFields(table, tableConfig.getSearchableFields());
      if(!tableConfig.isAllPlainFields())
      {
         List<String> snippetFields = new ArrayList<>();
         for(String fieldName : fields)
         {
            if(tableConfig.isIncludedInSnippet(fieldName))
            {
               snippetFields.add(fieldName);
            }
         }
         doc.withSearchableText(buildSearchableText(record, snippetFields, tableConfig.getFieldIncludeLabels(), table, tableConfig.getMaxFieldLength()));

         Map<String, Object> word    = new LinkedHashMap<>();
         Map<String, Object> english = new LinkedHashMap<>();
         Map<String, Object> exact   = new LinkedHashMap<>();
         for(String fieldName : fields)
         {
            String value = displayValue(record, fieldName, tableConfig.getMaxFieldLength());
            if(value == null)
            {
               continue;
            }
            switch(tableConfig.getAnalyzer(fieldName))
            {
               case WORD -> word.put(fieldName, value);
               case ENGLISH -> english.put(fieldName, value);
               case EXACT -> exact.put(fieldName, normalizeExact(value));
               default ->
               {
                  // PREFIX: fieldValues only
               }
            }
         }
         doc.withFieldValuesWord(word.isEmpty() ? null : word);
         doc.withFieldValuesEnglish(english.isEmpty() ? null : english);
         doc.withFieldValuesExact(exact.isEmpty() ? null : exact);
      }

      List<String>        titles    = new ArrayList<>();
      List<String>        subtitles = new ArrayList<>();
      Map<String, String> display   = new LinkedHashMap<>();
      for(String fieldName : fields)
      {
         QuickSearchFieldRole role = tableConfig.getRole(fieldName);
         if(role != QuickSearchFieldRole.TITLE && role != QuickSearchFieldRole.SUBTITLE && role != QuickSearchFieldRole.DISPLAY)
         {
            continue;
         }
         String value = displayValue(record, fieldName, MAX_ROLE_VALUE_LENGTH);
         if(value == null)
         {
            continue;
         }
         switch(role)
         {
            case TITLE -> titles.add(value);
            case SUBTITLE -> subtitles.add(value);
            default -> display.put(fieldLabel(table, fieldName), value);
         }
      }
      if(!titles.isEmpty())
      {
         doc.withRecordLabel(String.join(" ", titles));
      }
      doc.withSubtitle(subtitles.isEmpty() ? null : String.join(" - ", subtitles));
      doc.withDisplayValues(display.isEmpty() ? null : display);
   }



   /*******************************************************************************
    ** The form an EXACT value is stored and matched in: trimmed, whitespace
    ** collapsed, lower-cased.
    *******************************************************************************/
   public static String normalizeExact(String value)
   {
      return (normalizeSearchText(value).toLowerCase(Locale.ROOT));
   }



   /*******************************************************************************
    ** A field's label, or its name.
    *******************************************************************************/
   private static String fieldLabel(QTableMetaData table, String fieldName)
   {
      if(table != null && table.getFields().containsKey(fieldName) && StringUtils.hasContent(table.getField(fieldName).getLabel()))
      {
         return (table.getField(fieldName).getLabel());
      }
      return (fieldName);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static OpenSearchDocument buildDocument(QRecord record, String tableName, String primaryKeyField, List<String> fields, Map<String, Boolean> includeLabels,
      QTableMetaData table, Integer maxFieldLength, String timestampField, List<String> recordLabelFields)
   {
      if(table == null || primaryKeyField == null || table.getField(primaryKeyField) == null || QuickSearchFieldRules.isProtected(table, primaryKeyField))
      {
         QuickSearchFieldRules.warnExcludedFields(table);
         return (null);
      }
      fields = QuickSearchFieldRules.indexableFields(table, fields);
      String recordId = record.getValueString(primaryKeyField);
      if(recordId == null)
      {
         return (null);
      }

      Map<String, Object> fieldValues = new HashMap<>();
      for(String fieldName : fields == null ? List.<String>of() : fields)
      {
         String value = displayValue(record, fieldName, maxFieldLength);
         if(value != null)
         {
            fieldValues.put(fieldName, value);
         }
      }

      String recordLabel = record.getRecordLabel();
      boolean protectedLabel = table.getRecordLabelFields() != null
         && table.getRecordLabelFields().stream().anyMatch(f -> QuickSearchFieldRules.isProtected(table, f));
      if(protectedLabel || (recordLabelFields != null && recordLabelFields.stream().anyMatch(f -> QuickSearchFieldRules.isProtected(table, f))))
      {
         recordLabel = null;
         record = new QRecord(record);
         record.setRecordLabel(null);
      }
      if(!StringUtils.hasContent(recordLabel) && !protectedLabel && table.getRecordLabelFields() != null)
      {
         try
         {
            recordLabel = QValueFormatter.formatRecordLabel(table, record);
         }
         catch(Exception e)
         {
            LOG.debug("Could not format record label", e, logPair("tableName", tableName));
         }
      }

      Long version = null;
      if(timestampField != null && !QuickSearchFieldRules.isProtected(table, timestampField))
      {
         try
         {
            Instant timestamp = record.getValueInstant(timestampField);
            version = timestamp == null ? null : timestamp.toEpochMilli();
         }
         catch(Exception e)
         {
            version = null;
         }
      }

      return (new OpenSearchDocument()
         .withSourceTable(tableName)
         .withRecordId(recordId)
         .withRecordLabel(recordLabel)
         .withSearchableText(buildSearchableText(record, fields, includeLabels, table, maxFieldLength))
         .withIndexedAt(Instant.now())
         .withFieldValues(fieldValues)
         .withVersion(version));
   }



   /*******************************************************************************
    ** Apply recordLabelFormat; a bad format or null value never breaks indexing.
    *******************************************************************************/
   private static String formatLabel(QRecord record, QuickSearchableTableConfig tableConfig, String fallback)
   {
      QTableMetaData table = lookupTable(tableConfig.getTableName());
      if(tableConfig.getRecordLabelFields().stream().anyMatch(f -> QuickSearchFieldRules.isProtected(table, f)))
      {
         QuickSearchFieldRules.warnExcludedFields(table);
         return (fallback);
      }
      try
      {
         Object[] labelValues = new Object[tableConfig.getRecordLabelFields().size()];
         for(int i = 0; i < labelValues.length; i++)
         {
            String value = displayValue(record, tableConfig.getRecordLabelFields().get(i), null);
            labelValues[i] = value == null ? "" : value;
         }
         return (String.format(tableConfig.getRecordLabelFormat(), labelValues));
      }
      catch(Exception e)
      {
         LOG.warn("recordLabelFormat could not be applied; using default label", logPair("tableName", tableConfig.getTableName()));
         return (fallback);
      }
   }



   /*******************************************************************************
    ** Rebuild result presentation from allowed field values, discarding old
    ** aggregate labels, snippets and display values that may contain secrets.
    *******************************************************************************/
   public static OpenSearchDocument filterResult(OpenSearchDocument source, QuickSearchableTableConfig tableConfig)
   {
      if(!QuickSearchFieldRules.requiresFieldFiltering(source.getSourceTable()))
      {
         return (source);
      }
      QTableMetaData table = lookupTable(source.getSourceTable());
      if(tableConfig == null || QuickSearchFieldRules.isProtected(table, table.getPrimaryKeyField()))
      {
         return (null);
      }
      QRecord record = new QRecord().withValue(table.getPrimaryKeyField(), source.getRecordId());
      for(String field : QuickSearchFieldRules.indexableFields(table, tableConfig.getSearchableFields()))
      {
         Object value = source.getFieldValues() == null ? null : source.getFieldValues().get(field);
         if(value instanceof String text)
         {
            record.setValue(field, text);
         }
      }
      return (buildDocument(record, tableConfig));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static QTableMetaData lookupTable(String tableName)
   {
      QInstance qInstance = QContext.getQInstance();
      return (qInstance == null || tableName == null ? null : qInstance.getTable(tableName));
   }

}

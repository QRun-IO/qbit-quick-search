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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.values.QValueFormatter;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
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
      return (buildDocument(record, tableName, primaryKeyField, fields, includeLabels, lookupTable(tableName), null, null));
   }



   /*******************************************************************************
    ** Build a document for a configured table: display values, label fallback,
    ** truncation, and an external version from the basepull timestamp field.
    ** Returns null when the record has no primary key.
    *******************************************************************************/
   public static OpenSearchDocument buildDocument(QRecord record, QuickSearchableTableConfig tableConfig)
   {
      QTableMetaData     table = lookupTable(tableConfig.getTableName());
      OpenSearchDocument doc   = buildDocument(record, tableConfig.getTableName(), tableConfig.getPrimaryKeyField(), tableConfig.getSearchableFields(),
         tableConfig.getFieldIncludeLabels(), table, tableConfig.getMaxFieldLength(), tableConfig.getBasepullTimestampField());

      if(doc != null && tableConfig.getRecordLabelFormat() != null && tableConfig.getRecordLabelFields() != null)
      {
         doc.withRecordLabel(formatLabel(record, tableConfig, doc.getRecordLabel()));
      }

      return (doc);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static OpenSearchDocument buildDocument(QRecord record, String tableName, String primaryKeyField, List<String> fields, Map<String, Boolean> includeLabels,
      QTableMetaData table, Integer maxFieldLength, String timestampField)
   {
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
      if(!StringUtils.hasContent(recordLabel) && table != null)
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
      if(timestampField != null)
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
         LOG.warn("recordLabelFormat could not be applied; using default label", e, logPair("tableName", tableConfig.getTableName()));
         return (fallback);
      }
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

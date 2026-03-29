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
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;


/*******************************************************************************
 ** Static utility class for text normalization and OpenSearch document building.
 **
 ** Provides helpers used during the indexing pipeline to normalize field values
 ** into searchable text and to assemble OpenSearchDocument instances from
 ** QRecord data.
 *******************************************************************************/
public class IndexingUtils
{

   /*******************************************************************************
    ** Private constructor to prevent instantiation of this utility class.
    *******************************************************************************/
   private IndexingUtils()
   {
   }



   /*******************************************************************************
    ** Normalize text for search indexing.
    **
    ** Converts to lowercase, trims leading/trailing whitespace, and collapses
    ** runs of internal whitespace to a single space. Returns empty string for
    ** null or blank input.
    **
    ** @param text the raw text to normalize
    ** @return the normalized text, never null
    *******************************************************************************/
   public static String normalizeSearchText(String text)
   {
      if(text == null || text.isBlank())
      {
         return ("");
      }

      return (text.toLowerCase().trim().replaceAll("\\s+", " "));
   }



   /*******************************************************************************
    ** Build a concatenated searchable text string from selected record fields.
    **
    ** Iterates over each field name in {@code fields}, retrieves the string value
    ** from the record, and appends it to the result. Fields with a null or blank
    ** value are skipped. When {@code includeLabels} maps a field name to
    ** {@code true}, the value is prefixed with {@code "fieldName: "}.
    **
    ** @param record        the QRecord to read field values from
    ** @param fields        ordered list of field names to include
    ** @param includeLabels map of field name to whether its label should be prefixed
    ** @return the concatenated text, trimmed; empty string if fields is null or empty
    *******************************************************************************/
   public static String buildSearchableText(QRecord record, List<String> fields, Map<String, Boolean> includeLabels)
   {
      if(fields == null || fields.isEmpty())
      {
         return ("");
      }

      StringBuilder sb = new StringBuilder();

      for(String fieldName : fields)
      {
         String value = record.getValueString(fieldName);

         if(value == null || value.isBlank())
         {
            continue;
         }

         if(sb.length() > 0)
         {
            sb.append(" ");
         }

         if(Boolean.TRUE.equals(includeLabels.get(fieldName)))
         {
            sb.append(fieldName).append(": ");
         }

         sb.append(value);
      }

      return (sb.toString().trim());
   }



   /*******************************************************************************
    ** Build an OpenSearchDocument from a QRecord.
    **
    ** Delegates to {@link #buildSearchableText} for the searchable text, reads the
    ** record ID from {@code primaryKeyField}, and populates {@code fieldValues} with
    ** only those entries where the record's value is non-null.
    **
    ** @param record          the source QRecord
    ** @param tableName       the QQQ table name used as the document's sourceTable
    ** @param primaryKeyField the field name whose value becomes the document's recordId
    ** @param fields          ordered list of field names for searchable text
    ** @param fieldWeights    map of field name to search weight (reserved for future use)
    ** @param includeLabels   map of field name to whether its label should be prefixed
    ** @return a fully populated OpenSearchDocument with indexedAt set to now
    *******************************************************************************/
   public static OpenSearchDocument buildDocument(QRecord record, String tableName, String primaryKeyField, List<String> fields, Map<String, Integer> fieldWeights, Map<String, Boolean> includeLabels)
   {
      String searchableText = buildSearchableText(record, fields, includeLabels);
      String recordId = record.getValueString(primaryKeyField);
      if(recordId == null)
      {
         return (null);
      }
      String recordLabel = record.getRecordLabel();

      Map<String, Object> fieldValues = new HashMap<>();

      if(fields != null)
      {
         for(String fieldName : fields)
         {
            Object value = record.getValue(fieldName);

            if(value != null)
            {
               fieldValues.put(fieldName, value);
            }
         }
      }

      return (new OpenSearchDocument()
         .withSourceTable(tableName)
         .withRecordId(recordId)
         .withRecordLabel(recordLabel)
         .withSearchableText(searchableText)
         .withIndexedAt(Instant.now())
         .withFieldValues(fieldValues));
   }

}

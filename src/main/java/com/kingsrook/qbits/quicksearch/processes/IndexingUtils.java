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
import java.util.List;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;


/*******************************************************************************
 ** Utilities for building searchable content from QQQ records.
 *******************************************************************************/
public class IndexingUtils
{

   /***************************************************************************
    ** Build searchable text blob from a record's fields.
    ***************************************************************************/
   public static String buildSearchableText(QRecord record, List<String> fieldNames)
   {
      StringBuilder sb = new StringBuilder();

      for(String fieldName : fieldNames)
      {
         Object value = record.getValue(fieldName);
         if(value != null)
         {
            String textValue = String.valueOf(value);
            if(!textValue.isBlank())
            {
               if(sb.length() > 0)
               {
                  sb.append(" ");
               }
               sb.append(textValue);
            }
         }
      }

      return sb.toString();
   }



   /***************************************************************************
    ** Normalize search query text for matching.
    ***************************************************************************/
   public static String normalizeSearchText(String text)
   {
      if(text == null)
      {
         return "";
      }
      return text.toLowerCase().trim();
   }



   /***************************************************************************
    ** Build an OpenSearchDocument from a QRecord.
    ***************************************************************************/
   public static OpenSearchDocument buildDocument(QRecord record, QTableMetaData table, List<String> searchableFields)
   {
      String primaryKeyField = table.getPrimaryKeyField();
      Object primaryKeyValue = record.getValue(primaryKeyField);
      String recordId = primaryKeyValue != null ? String.valueOf(primaryKeyValue) : null;

      String recordLabel = record.getRecordLabel();
      if(recordLabel == null || recordLabel.isBlank())
      {
         recordLabel = table.getLabel() + " " + recordId;
      }

      String searchableText = buildSearchableText(record, searchableFields);

      return new OpenSearchDocument()
         .withSourceTable(table.getName())
         .withRecordId(recordId)
         .withRecordLabel(recordLabel)
         .withSearchableText(searchableText)
         .withIndexedAt(Instant.now());
   }

}

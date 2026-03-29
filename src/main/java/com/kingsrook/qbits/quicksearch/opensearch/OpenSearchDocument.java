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
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnore;


/*******************************************************************************
 ** Represents a document stored in OpenSearch for quick-search indexing.
 **
 ** Each document maps to a single QQQ record and contains the denormalized
 ** searchable text used for full-text queries.
 *******************************************************************************/
public class OpenSearchDocument
{

   private String sourceTable;
   private String recordId;
   private String recordLabel;
   private String searchableText;
   private Instant indexedAt;
   private Map<String, Object> fieldValues;



   /*******************************************************************************
    ** Getter for sourceTable
    *******************************************************************************/
   public String getSourceTable()
   {
      return (sourceTable);
   }



   /*******************************************************************************
    ** Fluent setter for sourceTable
    *******************************************************************************/
   public OpenSearchDocument withSourceTable(String sourceTable)
   {
      this.sourceTable = sourceTable;
      return (this);
   }



   /*******************************************************************************
    ** Getter for recordId
    *******************************************************************************/
   public String getRecordId()
   {
      return (recordId);
   }



   /*******************************************************************************
    ** Fluent setter for recordId
    *******************************************************************************/
   public OpenSearchDocument withRecordId(String recordId)
   {
      this.recordId = recordId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for recordLabel
    *******************************************************************************/
   public String getRecordLabel()
   {
      return (recordLabel);
   }



   /*******************************************************************************
    ** Fluent setter for recordLabel
    *******************************************************************************/
   public OpenSearchDocument withRecordLabel(String recordLabel)
   {
      this.recordLabel = recordLabel;
      return (this);
   }



   /*******************************************************************************
    ** Getter for searchableText
    *******************************************************************************/
   public String getSearchableText()
   {
      return (searchableText);
   }



   /*******************************************************************************
    ** Fluent setter for searchableText
    *******************************************************************************/
   public OpenSearchDocument withSearchableText(String searchableText)
   {
      this.searchableText = searchableText;
      return (this);
   }



   /*******************************************************************************
    ** Getter for indexedAt
    *******************************************************************************/
   public Instant getIndexedAt()
   {
      return (indexedAt);
   }



   /*******************************************************************************
    ** Fluent setter for indexedAt
    *******************************************************************************/
   public OpenSearchDocument withIndexedAt(Instant indexedAt)
   {
      this.indexedAt = indexedAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for fieldValues
    *******************************************************************************/
   public Map<String, Object> getFieldValues()
   {
      return (fieldValues);
   }



   /*******************************************************************************
    ** Fluent setter for fieldValues
    *******************************************************************************/
   public OpenSearchDocument withFieldValues(Map<String, Object> fieldValues)
   {
      this.fieldValues = fieldValues;
      return (this);
   }



   /*******************************************************************************
    ** Returns the composite document ID used as the OpenSearch _id field.
    ** Excluded from JSON serialization to avoid including it in the document body.
    *******************************************************************************/
   @JsonIgnore
   public String getDocumentId()
   {
      if(sourceTable == null || recordId == null)
      {
         throw new IllegalStateException("Cannot generate document ID: sourceTable=" + sourceTable + ", recordId=" + recordId);
      }
      return (sourceTable + ":" + recordId);
   }

}

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

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
import com.fasterxml.jackson.annotation.JsonIgnore;


/*******************************************************************************
 ** Document structure for Quick Search entries in OpenSearch.
 *******************************************************************************/
public class OpenSearchDocument
{
   private String  sourceTable;
   private String  recordId;
   private String  recordLabel;
   private String  searchableText;
   private Instant indexedAt;



   /***************************************************************************
    ** Generate a unique document ID for OpenSearch.
    ** This is not stored in the document body - it's used as the document _id.
    ***************************************************************************/
   @JsonIgnore
   public String getDocumentId()
   {
      return sourceTable + ":" + recordId;
   }



   /***************************************************************************
    ** Getter for sourceTable
    ***************************************************************************/
   public String getSourceTable()
   {
      return (this.sourceTable);
   }



   /***************************************************************************
    ** Setter for sourceTable
    ***************************************************************************/
   public void setSourceTable(String sourceTable)
   {
      this.sourceTable = sourceTable;
   }



   /***************************************************************************
    ** Fluent setter for sourceTable
    ***************************************************************************/
   public OpenSearchDocument withSourceTable(String sourceTable)
   {
      this.sourceTable = sourceTable;
      return (this);
   }



   /***************************************************************************
    ** Getter for recordId
    ***************************************************************************/
   public String getRecordId()
   {
      return (this.recordId);
   }



   /***************************************************************************
    ** Setter for recordId
    ***************************************************************************/
   public void setRecordId(String recordId)
   {
      this.recordId = recordId;
   }



   /***************************************************************************
    ** Fluent setter for recordId
    ***************************************************************************/
   public OpenSearchDocument withRecordId(String recordId)
   {
      this.recordId = recordId;
      return (this);
   }



   /***************************************************************************
    ** Getter for recordLabel
    ***************************************************************************/
   public String getRecordLabel()
   {
      return (this.recordLabel);
   }



   /***************************************************************************
    ** Setter for recordLabel
    ***************************************************************************/
   public void setRecordLabel(String recordLabel)
   {
      this.recordLabel = recordLabel;
   }



   /***************************************************************************
    ** Fluent setter for recordLabel
    ***************************************************************************/
   public OpenSearchDocument withRecordLabel(String recordLabel)
   {
      this.recordLabel = recordLabel;
      return (this);
   }



   /***************************************************************************
    ** Getter for searchableText
    ***************************************************************************/
   public String getSearchableText()
   {
      return (this.searchableText);
   }



   /***************************************************************************
    ** Setter for searchableText
    ***************************************************************************/
   public void setSearchableText(String searchableText)
   {
      this.searchableText = searchableText;
   }



   /***************************************************************************
    ** Fluent setter for searchableText
    ***************************************************************************/
   public OpenSearchDocument withSearchableText(String searchableText)
   {
      this.searchableText = searchableText;
      return (this);
   }



   /***************************************************************************
    ** Getter for indexedAt
    ***************************************************************************/
   public Instant getIndexedAt()
   {
      return (this.indexedAt);
   }



   /***************************************************************************
    ** Setter for indexedAt
    ***************************************************************************/
   public void setIndexedAt(Instant indexedAt)
   {
      this.indexedAt = indexedAt;
   }



   /***************************************************************************
    ** Fluent setter for indexedAt
    ***************************************************************************/
   public OpenSearchDocument withIndexedAt(Instant indexedAt)
   {
      this.indexedAt = indexedAt;
      return (this);
   }

}

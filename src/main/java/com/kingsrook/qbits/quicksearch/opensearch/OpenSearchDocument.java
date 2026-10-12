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
import com.fasterxml.jackson.annotation.JsonInclude;


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

   ////////////////////////////////////////////////////////////////////////
   // set only for tables whose fields have roles or non-default         //
   // analyzers, so other documents are written exactly as before        //
   ////////////////////////////////////////////////////////////////////////
   @JsonInclude(JsonInclude.Include.NON_NULL)
   private String subtitle;
   @JsonInclude(JsonInclude.Include.NON_NULL)
   private Map<String, String> displayValues;
   @JsonInclude(JsonInclude.Include.NON_NULL)
   private Map<String, Object> fieldValuesWord;
   @JsonInclude(JsonInclude.Include.NON_NULL)
   private Map<String, Object> fieldValuesEnglish;
   @JsonInclude(JsonInclude.Include.NON_NULL)
   private Map<String, Object> fieldValuesExact;

   private transient Long version;



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
   /*******************************************************************************
    ** External version for optimistic indexing (epoch millis of the source
    ** row's timestamp), or null to index unconditionally. Not stored in the
    ** document body.
    *******************************************************************************/
   @JsonIgnore
   public Long getVersion()
   {
      return (version);
   }



   /*******************************************************************************
    ** Fluent setter for version
    *******************************************************************************/
   public OpenSearchDocument withVersion(Long version)
   {
      this.version = version;
      return (this);
   }



   @JsonIgnore
   public String getDocumentId()
   {
      return (buildDocumentId(sourceTable, recordId));
   }



   /*******************************************************************************
    ** Build the composite OpenSearch _id for a source table and record ID.
    *******************************************************************************/
   public static String buildDocumentId(String sourceTable, String recordId)
   {
      return (sourceTable + ":" + recordId);
   }



   /*******************************************************************************
    ** Getter for subtitle
    *******************************************************************************/
   public String getSubtitle()
   {
      return (this.subtitle);
   }



   /*******************************************************************************
    ** Setter for subtitle
    *******************************************************************************/
   public void setSubtitle(String subtitle)
   {
      this.subtitle = subtitle;
   }



   /*******************************************************************************
    ** Fluent setter for subtitle
    *******************************************************************************/
   public OpenSearchDocument withSubtitle(String subtitle)
   {
      this.subtitle = subtitle;
      return (this);
   }


   /*******************************************************************************
    ** Getter for displayValues
    *******************************************************************************/
   public Map<String, String> getDisplayValues()
   {
      return (this.displayValues);
   }



   /*******************************************************************************
    ** Setter for displayValues
    *******************************************************************************/
   public void setDisplayValues(Map<String, String> displayValues)
   {
      this.displayValues = displayValues;
   }



   /*******************************************************************************
    ** Fluent setter for displayValues
    *******************************************************************************/
   public OpenSearchDocument withDisplayValues(Map<String, String> displayValues)
   {
      this.displayValues = displayValues;
      return (this);
   }


   /*******************************************************************************
    ** Getter for fieldValuesWord
    *******************************************************************************/
   public Map<String, Object> getFieldValuesWord()
   {
      return (this.fieldValuesWord);
   }



   /*******************************************************************************
    ** Setter for fieldValuesWord
    *******************************************************************************/
   public void setFieldValuesWord(Map<String, Object> fieldValuesWord)
   {
      this.fieldValuesWord = fieldValuesWord;
   }



   /*******************************************************************************
    ** Fluent setter for fieldValuesWord
    *******************************************************************************/
   public OpenSearchDocument withFieldValuesWord(Map<String, Object> fieldValuesWord)
   {
      this.fieldValuesWord = fieldValuesWord;
      return (this);
   }


   /*******************************************************************************
    ** Getter for fieldValuesEnglish
    *******************************************************************************/
   public Map<String, Object> getFieldValuesEnglish()
   {
      return (this.fieldValuesEnglish);
   }



   /*******************************************************************************
    ** Setter for fieldValuesEnglish
    *******************************************************************************/
   public void setFieldValuesEnglish(Map<String, Object> fieldValuesEnglish)
   {
      this.fieldValuesEnglish = fieldValuesEnglish;
   }



   /*******************************************************************************
    ** Fluent setter for fieldValuesEnglish
    *******************************************************************************/
   public OpenSearchDocument withFieldValuesEnglish(Map<String, Object> fieldValuesEnglish)
   {
      this.fieldValuesEnglish = fieldValuesEnglish;
      return (this);
   }


   /*******************************************************************************
    ** Getter for fieldValuesExact
    *******************************************************************************/
   public Map<String, Object> getFieldValuesExact()
   {
      return (this.fieldValuesExact);
   }



   /*******************************************************************************
    ** Setter for fieldValuesExact
    *******************************************************************************/
   public void setFieldValuesExact(Map<String, Object> fieldValuesExact)
   {
      this.fieldValuesExact = fieldValuesExact;
   }



   /*******************************************************************************
    ** Fluent setter for fieldValuesExact
    *******************************************************************************/
   public OpenSearchDocument withFieldValuesExact(Map<String, Object> fieldValuesExact)
   {
      this.fieldValuesExact = fieldValuesExact;
      return (this);
   }

}

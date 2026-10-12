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

package com.kingsrook.qbits.quicksearch.model;


import java.time.Instant;
import com.kingsrook.qqq.backend.core.model.data.QField;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;


/*******************************************************************************
 ** Entity bean for the quick_search_index table.
 **
 ** Tracks configuration for each table that participates in the quick search
 ** index, including basepull scheduling, searchable field definitions, and
 ** current indexing status.
 *******************************************************************************/
public class QuickSearchIndex extends QRecordEntity
{
   public static final String TABLE_NAME = "quick_search_index";

   /** Table name before 1.1.0-RC.3 (camelCase). Kept only for upgrade scripts; see docs/MIGRATION-1.1.md. */
   @Deprecated(since = "1.1.0-RC.3")
   public static final String LEGACY_TABLE_NAME = "quickSearchIndex";

   ///////////////////////////////////////////////////////////////////////
   // source: who created the row. CODE rows were seeded from code      //
   // defaults; OWNER rows were added by an admin. ownerEdited marks a  //
   // row whose config an admin changed: priming never overwrites it.   //
   ///////////////////////////////////////////////////////////////////////
   public static final String SOURCE_CODE  = "CODE";
   public static final String SOURCE_OWNER = "OWNER";

   @QField()
   private Integer id;

   @QField()
   private String tableName;

   @QField()
   private Boolean enabled;

   @QField()
   private Integer basepullIntervalMinutes;

   @QField()
   private String basepullTimestampField;

   @QField()
   private String searchableFieldsJson;

   @QField()
   private Instant lastBasepullTime;

   @QField()
   private Instant lastFullReindexTime;

   @QField()
   private Integer recordCount;

   @QField()
   private String status;

   @QField()
   private Instant createDate;

   @QField()
   private Instant modifyDate;

   @QField()
   private String source;

   @QField()
   private Boolean ownerEdited;



   /*******************************************************************************
    ** Getter for id
    *******************************************************************************/
   public Integer getId()
   {
      return (this.id);
   }



   /*******************************************************************************
    ** Fluent setter for id
    *******************************************************************************/
   public QuickSearchIndex withId(Integer id)
   {
      this.id = id;
      return (this);
   }



   /*******************************************************************************
    ** Getter for tableName
    *******************************************************************************/
   public String getTableName()
   {
      return (this.tableName);
   }



   /*******************************************************************************
    ** Fluent setter for tableName
    *******************************************************************************/
   public QuickSearchIndex withTableName(String tableName)
   {
      this.tableName = tableName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for enabled
    *******************************************************************************/
   public Boolean getEnabled()
   {
      return (this.enabled);
   }



   /*******************************************************************************
    ** Fluent setter for enabled
    *******************************************************************************/
   public QuickSearchIndex withEnabled(Boolean enabled)
   {
      this.enabled = enabled;
      return (this);
   }



   /*******************************************************************************
    ** Getter for basepullIntervalMinutes
    *******************************************************************************/
   public Integer getBasepullIntervalMinutes()
   {
      return (this.basepullIntervalMinutes);
   }



   /*******************************************************************************
    ** Fluent setter for basepullIntervalMinutes
    *******************************************************************************/
   public QuickSearchIndex withBasepullIntervalMinutes(Integer basepullIntervalMinutes)
   {
      this.basepullIntervalMinutes = basepullIntervalMinutes;
      return (this);
   }



   /*******************************************************************************
    ** Getter for basepullTimestampField
    *******************************************************************************/
   public String getBasepullTimestampField()
   {
      return (this.basepullTimestampField);
   }



   /*******************************************************************************
    ** Fluent setter for basepullTimestampField
    *******************************************************************************/
   public QuickSearchIndex withBasepullTimestampField(String basepullTimestampField)
   {
      this.basepullTimestampField = basepullTimestampField;
      return (this);
   }



   /*******************************************************************************
    ** Getter for searchableFieldsJson
    *******************************************************************************/
   public String getSearchableFieldsJson()
   {
      return (this.searchableFieldsJson);
   }



   /*******************************************************************************
    ** Fluent setter for searchableFieldsJson
    *******************************************************************************/
   public QuickSearchIndex withSearchableFieldsJson(String searchableFieldsJson)
   {
      this.searchableFieldsJson = searchableFieldsJson;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastBasepullTime
    *******************************************************************************/
   public Instant getLastBasepullTime()
   {
      return (this.lastBasepullTime);
   }



   /*******************************************************************************
    ** Fluent setter for lastBasepullTime
    *******************************************************************************/
   public QuickSearchIndex withLastBasepullTime(Instant lastBasepullTime)
   {
      this.lastBasepullTime = lastBasepullTime;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastFullReindexTime
    *******************************************************************************/
   public Instant getLastFullReindexTime()
   {
      return (this.lastFullReindexTime);
   }



   /*******************************************************************************
    ** Fluent setter for lastFullReindexTime
    *******************************************************************************/
   public QuickSearchIndex withLastFullReindexTime(Instant lastFullReindexTime)
   {
      this.lastFullReindexTime = lastFullReindexTime;
      return (this);
   }



   /*******************************************************************************
    ** Getter for recordCount
    *******************************************************************************/
   public Integer getRecordCount()
   {
      return (this.recordCount);
   }



   /*******************************************************************************
    ** Fluent setter for recordCount
    *******************************************************************************/
   public QuickSearchIndex withRecordCount(Integer recordCount)
   {
      this.recordCount = recordCount;
      return (this);
   }



   /*******************************************************************************
    ** Getter for status
    *******************************************************************************/
   public String getStatus()
   {
      return (this.status);
   }



   /*******************************************************************************
    ** Fluent setter for status
    *******************************************************************************/
   public QuickSearchIndex withStatus(String status)
   {
      this.status = status;
      return (this);
   }



   /*******************************************************************************
    ** Getter for createDate
    *******************************************************************************/
   public Instant getCreateDate()
   {
      return (this.createDate);
   }



   /*******************************************************************************
    ** Fluent setter for createDate
    *******************************************************************************/
   public QuickSearchIndex withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }



   /*******************************************************************************
    ** Getter for modifyDate
    *******************************************************************************/
   public Instant getModifyDate()
   {
      return (this.modifyDate);
   }



   /*******************************************************************************
    ** Fluent setter for modifyDate
    *******************************************************************************/
   public QuickSearchIndex withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }



   /*******************************************************************************
    ** Getter for source
    *******************************************************************************/
   public String getSource()
   {
      return (this.source);
   }



   /*******************************************************************************
    ** Setter for source
    *******************************************************************************/
   public void setSource(String source)
   {
      this.source = source;
   }



   /*******************************************************************************
    ** Fluent setter for source
    *******************************************************************************/
   public QuickSearchIndex withSource(String source)
   {
      this.source = source;
      return (this);
   }


   /*******************************************************************************
    ** Getter for ownerEdited
    *******************************************************************************/
   public Boolean getOwnerEdited()
   {
      return (this.ownerEdited);
   }



   /*******************************************************************************
    ** Setter for ownerEdited
    *******************************************************************************/
   public void setOwnerEdited(Boolean ownerEdited)
   {
      this.ownerEdited = ownerEdited;
   }



   /*******************************************************************************
    ** Fluent setter for ownerEdited
    *******************************************************************************/
   public QuickSearchIndex withOwnerEdited(Boolean ownerEdited)
   {
      this.ownerEdited = ownerEdited;
      return (this);
   }

}

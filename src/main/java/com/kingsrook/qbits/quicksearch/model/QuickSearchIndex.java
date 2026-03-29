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
 ** Entity bean for the quickSearchIndex table.
 **
 ** Tracks configuration for each table that participates in the quick search
 ** index, including basepull scheduling, searchable field definitions, and
 ** current indexing status.
 *******************************************************************************/
public class QuickSearchIndex extends QRecordEntity
{
   public static final String TABLE_NAME = "quickSearchIndex";

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

}

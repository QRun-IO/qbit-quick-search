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
 ** Entity for tracking Quick Search index configuration and status per table.
 *******************************************************************************/
public class QuickSearchIndex extends QRecordEntity
{
   public static final String TABLE_NAME = "quickSearchIndex";

   @QField(isPrimaryKey = true)
   private Integer id;

   @QField(label = "Table Name", isRequired = true, isEditable = false)
   private String tableName;

   @QField(label = "Enabled")
   private Boolean isEnabled;

   @QField(label = "Basepull Interval (min)")
   private Integer basepullIntervalMinutes;

   @QField(label = "Timestamp Field")
   private String basepullTimestampField;

   @QField(label = "Searchable Fields")
   private String searchableFieldsJson;

   @QField(label = "Last Full Index Time", isEditable = false)
   private Instant lastFullIndexTime;

   @QField(label = "Last Basepull Time", isEditable = false)
   private Instant lastBasepullTime;

   @QField(label = "Indexed Record Count", isEditable = false)
   private Integer indexedRecordCount;

   @QField(label = "Create Date", isEditable = false)
   private Instant createDate;

   @QField(label = "Modify Date", isEditable = false)
   private Instant modifyDate;



   /***************************************************************************
    ** Getter for id
    ***************************************************************************/
   public Integer getId()
   {
      return (this.id);
   }



   /***************************************************************************
    ** Setter for id
    ***************************************************************************/
   public void setId(Integer id)
   {
      this.id = id;
   }



   /***************************************************************************
    ** Fluent setter for id
    ***************************************************************************/
   public QuickSearchIndex withId(Integer id)
   {
      this.id = id;
      return (this);
   }



   /***************************************************************************
    ** Getter for tableName
    ***************************************************************************/
   public String getTableName()
   {
      return (this.tableName);
   }



   /***************************************************************************
    ** Setter for tableName
    ***************************************************************************/
   public void setTableName(String tableName)
   {
      this.tableName = tableName;
   }



   /***************************************************************************
    ** Fluent setter for tableName
    ***************************************************************************/
   public QuickSearchIndex withTableName(String tableName)
   {
      this.tableName = tableName;
      return (this);
   }



   /***************************************************************************
    ** Getter for isEnabled
    ***************************************************************************/
   public Boolean getIsEnabled()
   {
      return (this.isEnabled);
   }



   /***************************************************************************
    ** Setter for isEnabled
    ***************************************************************************/
   public void setIsEnabled(Boolean isEnabled)
   {
      this.isEnabled = isEnabled;
   }



   /***************************************************************************
    ** Fluent setter for isEnabled
    ***************************************************************************/
   public QuickSearchIndex withIsEnabled(Boolean isEnabled)
   {
      this.isEnabled = isEnabled;
      return (this);
   }



   /***************************************************************************
    ** Getter for basepullIntervalMinutes
    ***************************************************************************/
   public Integer getBasepullIntervalMinutes()
   {
      return (this.basepullIntervalMinutes);
   }



   /***************************************************************************
    ** Setter for basepullIntervalMinutes
    ***************************************************************************/
   public void setBasepullIntervalMinutes(Integer basepullIntervalMinutes)
   {
      this.basepullIntervalMinutes = basepullIntervalMinutes;
   }



   /***************************************************************************
    ** Fluent setter for basepullIntervalMinutes
    ***************************************************************************/
   public QuickSearchIndex withBasepullIntervalMinutes(Integer basepullIntervalMinutes)
   {
      this.basepullIntervalMinutes = basepullIntervalMinutes;
      return (this);
   }



   /***************************************************************************
    ** Getter for basepullTimestampField
    ***************************************************************************/
   public String getBasepullTimestampField()
   {
      return (this.basepullTimestampField);
   }



   /***************************************************************************
    ** Setter for basepullTimestampField
    ***************************************************************************/
   public void setBasepullTimestampField(String basepullTimestampField)
   {
      this.basepullTimestampField = basepullTimestampField;
   }



   /***************************************************************************
    ** Fluent setter for basepullTimestampField
    ***************************************************************************/
   public QuickSearchIndex withBasepullTimestampField(String basepullTimestampField)
   {
      this.basepullTimestampField = basepullTimestampField;
      return (this);
   }



   /***************************************************************************
    ** Getter for searchableFieldsJson
    ***************************************************************************/
   public String getSearchableFieldsJson()
   {
      return (this.searchableFieldsJson);
   }



   /***************************************************************************
    ** Setter for searchableFieldsJson
    ***************************************************************************/
   public void setSearchableFieldsJson(String searchableFieldsJson)
   {
      this.searchableFieldsJson = searchableFieldsJson;
   }



   /***************************************************************************
    ** Fluent setter for searchableFieldsJson
    ***************************************************************************/
   public QuickSearchIndex withSearchableFieldsJson(String searchableFieldsJson)
   {
      this.searchableFieldsJson = searchableFieldsJson;
      return (this);
   }



   /***************************************************************************
    ** Getter for lastFullIndexTime
    ***************************************************************************/
   public Instant getLastFullIndexTime()
   {
      return (this.lastFullIndexTime);
   }



   /***************************************************************************
    ** Setter for lastFullIndexTime
    ***************************************************************************/
   public void setLastFullIndexTime(Instant lastFullIndexTime)
   {
      this.lastFullIndexTime = lastFullIndexTime;
   }



   /***************************************************************************
    ** Fluent setter for lastFullIndexTime
    ***************************************************************************/
   public QuickSearchIndex withLastFullIndexTime(Instant lastFullIndexTime)
   {
      this.lastFullIndexTime = lastFullIndexTime;
      return (this);
   }



   /***************************************************************************
    ** Getter for lastBasepullTime
    ***************************************************************************/
   public Instant getLastBasepullTime()
   {
      return (this.lastBasepullTime);
   }



   /***************************************************************************
    ** Setter for lastBasepullTime
    ***************************************************************************/
   public void setLastBasepullTime(Instant lastBasepullTime)
   {
      this.lastBasepullTime = lastBasepullTime;
   }



   /***************************************************************************
    ** Fluent setter for lastBasepullTime
    ***************************************************************************/
   public QuickSearchIndex withLastBasepullTime(Instant lastBasepullTime)
   {
      this.lastBasepullTime = lastBasepullTime;
      return (this);
   }



   /***************************************************************************
    ** Getter for indexedRecordCount
    ***************************************************************************/
   public Integer getIndexedRecordCount()
   {
      return (this.indexedRecordCount);
   }



   /***************************************************************************
    ** Setter for indexedRecordCount
    ***************************************************************************/
   public void setIndexedRecordCount(Integer indexedRecordCount)
   {
      this.indexedRecordCount = indexedRecordCount;
   }



   /***************************************************************************
    ** Fluent setter for indexedRecordCount
    ***************************************************************************/
   public QuickSearchIndex withIndexedRecordCount(Integer indexedRecordCount)
   {
      this.indexedRecordCount = indexedRecordCount;
      return (this);
   }



   /***************************************************************************
    ** Getter for createDate
    ***************************************************************************/
   public Instant getCreateDate()
   {
      return (this.createDate);
   }



   /***************************************************************************
    ** Setter for createDate
    ***************************************************************************/
   public void setCreateDate(Instant createDate)
   {
      this.createDate = createDate;
   }



   /***************************************************************************
    ** Fluent setter for createDate
    ***************************************************************************/
   public QuickSearchIndex withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }



   /***************************************************************************
    ** Getter for modifyDate
    ***************************************************************************/
   public Instant getModifyDate()
   {
      return (this.modifyDate);
   }



   /***************************************************************************
    ** Setter for modifyDate
    ***************************************************************************/
   public void setModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
   }



   /***************************************************************************
    ** Fluent setter for modifyDate
    ***************************************************************************/
   public QuickSearchIndex withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }

}

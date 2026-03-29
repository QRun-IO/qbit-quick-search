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

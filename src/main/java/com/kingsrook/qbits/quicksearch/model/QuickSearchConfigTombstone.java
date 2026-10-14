/*
 * Copyright 2026 Kingsrook, LLC
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
 ** Entity bean for the quick_search_config_tombstone table: a table (fieldName
 ** null) or one field the owner removed from Quick Search. Startup priming
 ** never re-adds what a tombstone names, even when code still declares it;
 ** deleting the tombstone lets code defaults seed it again.
 *******************************************************************************/
public class QuickSearchConfigTombstone extends QRecordEntity
{
   public static final String TABLE_NAME = "quick_search_config_tombstone";

   @QField()
   private Integer id;
   @QField()
   private String tableName;
   @QField()
   private String fieldName;
   @QField()
   private String removedBy;
   @QField()
   private String reason;
   @QField()
   private Instant createDate;


   /*******************************************************************************
    ** Getter for id
    *******************************************************************************/
   public Integer getId()
   {
      return (this.id);
   }



   /*******************************************************************************
    ** Setter for id
    *******************************************************************************/
   public void setId(Integer id)
   {
      this.id = id;
   }



   /*******************************************************************************
    ** Fluent setter for id
    *******************************************************************************/
   public QuickSearchConfigTombstone withId(Integer id)
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
    ** Setter for tableName
    *******************************************************************************/
   public void setTableName(String tableName)
   {
      this.tableName = tableName;
   }



   /*******************************************************************************
    ** Fluent setter for tableName
    *******************************************************************************/
   public QuickSearchConfigTombstone withTableName(String tableName)
   {
      this.tableName = tableName;
      return (this);
   }


   /*******************************************************************************
    ** Getter for fieldName
    *******************************************************************************/
   public String getFieldName()
   {
      return (this.fieldName);
   }



   /*******************************************************************************
    ** Setter for fieldName
    *******************************************************************************/
   public void setFieldName(String fieldName)
   {
      this.fieldName = fieldName;
   }



   /*******************************************************************************
    ** Fluent setter for fieldName
    *******************************************************************************/
   public QuickSearchConfigTombstone withFieldName(String fieldName)
   {
      this.fieldName = fieldName;
      return (this);
   }


   /*******************************************************************************
    ** Getter for removedBy
    *******************************************************************************/
   public String getRemovedBy()
   {
      return (this.removedBy);
   }



   /*******************************************************************************
    ** Setter for removedBy
    *******************************************************************************/
   public void setRemovedBy(String removedBy)
   {
      this.removedBy = removedBy;
   }



   /*******************************************************************************
    ** Fluent setter for removedBy
    *******************************************************************************/
   public QuickSearchConfigTombstone withRemovedBy(String removedBy)
   {
      this.removedBy = removedBy;
      return (this);
   }


   /*******************************************************************************
    ** Getter for reason
    *******************************************************************************/
   public String getReason()
   {
      return (this.reason);
   }



   /*******************************************************************************
    ** Setter for reason
    *******************************************************************************/
   public void setReason(String reason)
   {
      this.reason = reason;
   }



   /*******************************************************************************
    ** Fluent setter for reason
    *******************************************************************************/
   public QuickSearchConfigTombstone withReason(String reason)
   {
      this.reason = reason;
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
    ** Setter for createDate
    *******************************************************************************/
   public void setCreateDate(Instant createDate)
   {
      this.createDate = createDate;
   }



   /*******************************************************************************
    ** Fluent setter for createDate
    *******************************************************************************/
   public QuickSearchConfigTombstone withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }
}

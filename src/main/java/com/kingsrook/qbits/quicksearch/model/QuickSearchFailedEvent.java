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
 ** A real-time index or delete event that could not be applied to OpenSearch.
 ** Replayed by the scheduled basepull; status PENDING until it succeeds, or
 ** EXHAUSTED after the retry limit.
 *******************************************************************************/
public class QuickSearchFailedEvent extends QRecordEntity
{
   public static final String TABLE_NAME = "quickSearchFailedEvent";

   public static final String STATUS_PENDING   = "PENDING";
   public static final String STATUS_EXHAUSTED = "EXHAUSTED";

   @QField()
   private Integer id;

   @QField()
   private String tableName;

   @QField()
   private String recordId;

   @QField()
   private String action;

   @QField()
   private String errorMessage;

   @QField()
   private Integer attempts;

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
    ** Getter for tableName
    *******************************************************************************/
   public String getTableName()
   {
      return (this.tableName);
   }



   /*******************************************************************************
    ** Getter for recordId
    *******************************************************************************/
   public String getRecordId()
   {
      return (this.recordId);
   }



   /*******************************************************************************
    ** Getter for action
    *******************************************************************************/
   public String getAction()
   {
      return (this.action);
   }



   /*******************************************************************************
    ** Getter for errorMessage
    *******************************************************************************/
   public String getErrorMessage()
   {
      return (this.errorMessage);
   }



   /*******************************************************************************
    ** Getter for attempts
    *******************************************************************************/
   public Integer getAttempts()
   {
      return (this.attempts);
   }



   /*******************************************************************************
    ** Getter for status
    *******************************************************************************/
   public String getStatus()
   {
      return (this.status);
   }



   /*******************************************************************************
    ** Getter for createDate
    *******************************************************************************/
   public Instant getCreateDate()
   {
      return (this.createDate);
   }



   /*******************************************************************************
    ** Getter for modifyDate
    *******************************************************************************/
   public Instant getModifyDate()
   {
      return (this.modifyDate);
   }

}

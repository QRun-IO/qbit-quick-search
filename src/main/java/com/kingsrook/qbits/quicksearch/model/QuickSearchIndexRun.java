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
 ** Entity for tracking Quick Search indexing job history.
 *******************************************************************************/
public class QuickSearchIndexRun extends QRecordEntity
{
   public static final String TABLE_NAME = "quickSearchIndexRun";

   @QField(isPrimaryKey = true)
   private Integer id;

   @QField(label = "Index", isRequired = true)
   private Integer quickSearchIndexId;

   @QField(label = "Run Type", isRequired = true)
   private String runType;

   @QField(label = "Status", isRequired = true)
   private String status;

   @QField(label = "Start Time")
   private Instant startTime;

   @QField(label = "End Time")
   private Instant endTime;

   @QField(label = "Records Processed")
   private Integer recordsProcessed;

   @QField(label = "Records Indexed")
   private Integer recordsIndexed;

   @QField(label = "Error Count")
   private Integer errorCount;

   @QField(label = "Error Message")
   private String errorMessage;



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
   public QuickSearchIndexRun withId(Integer id)
   {
      this.id = id;
      return (this);
   }



   /***************************************************************************
    ** Getter for quickSearchIndexId
    ***************************************************************************/
   public Integer getQuickSearchIndexId()
   {
      return (this.quickSearchIndexId);
   }



   /***************************************************************************
    ** Setter for quickSearchIndexId
    ***************************************************************************/
   public void setQuickSearchIndexId(Integer quickSearchIndexId)
   {
      this.quickSearchIndexId = quickSearchIndexId;
   }



   /***************************************************************************
    ** Fluent setter for quickSearchIndexId
    ***************************************************************************/
   public QuickSearchIndexRun withQuickSearchIndexId(Integer quickSearchIndexId)
   {
      this.quickSearchIndexId = quickSearchIndexId;
      return (this);
   }



   /***************************************************************************
    ** Getter for runType
    ***************************************************************************/
   public String getRunType()
   {
      return (this.runType);
   }



   /***************************************************************************
    ** Setter for runType
    ***************************************************************************/
   public void setRunType(String runType)
   {
      this.runType = runType;
   }



   /***************************************************************************
    ** Fluent setter for runType
    ***************************************************************************/
   public QuickSearchIndexRun withRunType(String runType)
   {
      this.runType = runType;
      return (this);
   }



   /***************************************************************************
    ** Getter for status
    ***************************************************************************/
   public String getStatus()
   {
      return (this.status);
   }



   /***************************************************************************
    ** Setter for status
    ***************************************************************************/
   public void setStatus(String status)
   {
      this.status = status;
   }



   /***************************************************************************
    ** Fluent setter for status
    ***************************************************************************/
   public QuickSearchIndexRun withStatus(String status)
   {
      this.status = status;
      return (this);
   }



   /***************************************************************************
    ** Getter for startTime
    ***************************************************************************/
   public Instant getStartTime()
   {
      return (this.startTime);
   }



   /***************************************************************************
    ** Setter for startTime
    ***************************************************************************/
   public void setStartTime(Instant startTime)
   {
      this.startTime = startTime;
   }



   /***************************************************************************
    ** Fluent setter for startTime
    ***************************************************************************/
   public QuickSearchIndexRun withStartTime(Instant startTime)
   {
      this.startTime = startTime;
      return (this);
   }



   /***************************************************************************
    ** Getter for endTime
    ***************************************************************************/
   public Instant getEndTime()
   {
      return (this.endTime);
   }



   /***************************************************************************
    ** Setter for endTime
    ***************************************************************************/
   public void setEndTime(Instant endTime)
   {
      this.endTime = endTime;
   }



   /***************************************************************************
    ** Fluent setter for endTime
    ***************************************************************************/
   public QuickSearchIndexRun withEndTime(Instant endTime)
   {
      this.endTime = endTime;
      return (this);
   }



   /***************************************************************************
    ** Getter for recordsProcessed
    ***************************************************************************/
   public Integer getRecordsProcessed()
   {
      return (this.recordsProcessed);
   }



   /***************************************************************************
    ** Setter for recordsProcessed
    ***************************************************************************/
   public void setRecordsProcessed(Integer recordsProcessed)
   {
      this.recordsProcessed = recordsProcessed;
   }



   /***************************************************************************
    ** Fluent setter for recordsProcessed
    ***************************************************************************/
   public QuickSearchIndexRun withRecordsProcessed(Integer recordsProcessed)
   {
      this.recordsProcessed = recordsProcessed;
      return (this);
   }



   /***************************************************************************
    ** Getter for recordsIndexed
    ***************************************************************************/
   public Integer getRecordsIndexed()
   {
      return (this.recordsIndexed);
   }



   /***************************************************************************
    ** Setter for recordsIndexed
    ***************************************************************************/
   public void setRecordsIndexed(Integer recordsIndexed)
   {
      this.recordsIndexed = recordsIndexed;
   }



   /***************************************************************************
    ** Fluent setter for recordsIndexed
    ***************************************************************************/
   public QuickSearchIndexRun withRecordsIndexed(Integer recordsIndexed)
   {
      this.recordsIndexed = recordsIndexed;
      return (this);
   }



   /***************************************************************************
    ** Getter for errorCount
    ***************************************************************************/
   public Integer getErrorCount()
   {
      return (this.errorCount);
   }



   /***************************************************************************
    ** Setter for errorCount
    ***************************************************************************/
   public void setErrorCount(Integer errorCount)
   {
      this.errorCount = errorCount;
   }



   /***************************************************************************
    ** Fluent setter for errorCount
    ***************************************************************************/
   public QuickSearchIndexRun withErrorCount(Integer errorCount)
   {
      this.errorCount = errorCount;
      return (this);
   }



   /***************************************************************************
    ** Getter for errorMessage
    ***************************************************************************/
   public String getErrorMessage()
   {
      return (this.errorMessage);
   }



   /***************************************************************************
    ** Setter for errorMessage
    ***************************************************************************/
   public void setErrorMessage(String errorMessage)
   {
      this.errorMessage = errorMessage;
   }



   /***************************************************************************
    ** Fluent setter for errorMessage
    ***************************************************************************/
   public QuickSearchIndexRun withErrorMessage(String errorMessage)
   {
      this.errorMessage = errorMessage;
      return (this);
   }

}

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
 ** Entity bean for the quickSearchIndexRun table.
 **
 ** Records a single execution of the indexing process for a given
 ** QuickSearchIndex, capturing run type, timing, record counts, and any errors
 ** encountered during the run.
 *******************************************************************************/
public class QuickSearchIndexRun extends QRecordEntity
{
   public static final String TABLE_NAME = "quickSearchIndexRun";

   @QField()
   private Integer id;

   @QField()
   private Integer quickSearchIndexId;

   @QField()
   private String runType;

   @QField()
   private String status;

   @QField()
   private Instant startTime;

   @QField()
   private Instant endTime;

   @QField()
   private Integer recordsProcessed;

   @QField()
   private Integer recordsIndexed;

   @QField()
   private Integer errorCount;

   @QField()
   private String errorMessage;

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
   public QuickSearchIndexRun withId(Integer id)
   {
      this.id = id;
      return (this);
   }



   /*******************************************************************************
    ** Getter for quickSearchIndexId
    *******************************************************************************/
   public Integer getQuickSearchIndexId()
   {
      return (this.quickSearchIndexId);
   }



   /*******************************************************************************
    ** Fluent setter for quickSearchIndexId
    *******************************************************************************/
   public QuickSearchIndexRun withQuickSearchIndexId(Integer quickSearchIndexId)
   {
      this.quickSearchIndexId = quickSearchIndexId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for runType
    *******************************************************************************/
   public String getRunType()
   {
      return (this.runType);
   }



   /*******************************************************************************
    ** Fluent setter for runType
    *******************************************************************************/
   public QuickSearchIndexRun withRunType(String runType)
   {
      this.runType = runType;
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
   public QuickSearchIndexRun withStatus(String status)
   {
      this.status = status;
      return (this);
   }



   /*******************************************************************************
    ** Getter for startTime
    *******************************************************************************/
   public Instant getStartTime()
   {
      return (this.startTime);
   }



   /*******************************************************************************
    ** Fluent setter for startTime
    *******************************************************************************/
   public QuickSearchIndexRun withStartTime(Instant startTime)
   {
      this.startTime = startTime;
      return (this);
   }



   /*******************************************************************************
    ** Getter for endTime
    *******************************************************************************/
   public Instant getEndTime()
   {
      return (this.endTime);
   }



   /*******************************************************************************
    ** Fluent setter for endTime
    *******************************************************************************/
   public QuickSearchIndexRun withEndTime(Instant endTime)
   {
      this.endTime = endTime;
      return (this);
   }



   /*******************************************************************************
    ** Getter for recordsProcessed
    *******************************************************************************/
   public Integer getRecordsProcessed()
   {
      return (this.recordsProcessed);
   }



   /*******************************************************************************
    ** Fluent setter for recordsProcessed
    *******************************************************************************/
   public QuickSearchIndexRun withRecordsProcessed(Integer recordsProcessed)
   {
      this.recordsProcessed = recordsProcessed;
      return (this);
   }



   /*******************************************************************************
    ** Getter for recordsIndexed
    *******************************************************************************/
   public Integer getRecordsIndexed()
   {
      return (this.recordsIndexed);
   }



   /*******************************************************************************
    ** Fluent setter for recordsIndexed
    *******************************************************************************/
   public QuickSearchIndexRun withRecordsIndexed(Integer recordsIndexed)
   {
      this.recordsIndexed = recordsIndexed;
      return (this);
   }



   /*******************************************************************************
    ** Getter for errorCount
    *******************************************************************************/
   public Integer getErrorCount()
   {
      return (this.errorCount);
   }



   /*******************************************************************************
    ** Fluent setter for errorCount
    *******************************************************************************/
   public QuickSearchIndexRun withErrorCount(Integer errorCount)
   {
      this.errorCount = errorCount;
      return (this);
   }



   /*******************************************************************************
    ** Getter for errorMessage
    *******************************************************************************/
   public String getErrorMessage()
   {
      return (this.errorMessage);
   }



   /*******************************************************************************
    ** Fluent setter for errorMessage
    *******************************************************************************/
   public QuickSearchIndexRun withErrorMessage(String errorMessage)
   {
      this.errorMessage = errorMessage;
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
   public QuickSearchIndexRun withCreateDate(Instant createDate)
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
   public QuickSearchIndexRun withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }

}

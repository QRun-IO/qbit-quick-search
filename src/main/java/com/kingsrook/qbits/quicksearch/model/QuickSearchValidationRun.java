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
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;
import com.kingsrook.qqq.backend.core.exceptions.QException;


/*******************************************************************************
 ** One run of the index validation: overall status, counts of failed and
 ** warning checks, and the full report (HTML for the record view, JSON for
 ** tools). Kept as history; purged after runHistoryRetentionDays.
 *******************************************************************************/
public class QuickSearchValidationRun extends QRecordEntity
{
   public static final String TABLE_NAME = "quick_search_validation_run";

   /** Table name before 1.1.0-RC.3 (camelCase). Kept only for upgrade scripts; see docs/MIGRATION-1.1.md. */
   @Deprecated(since = "1.1.0-RC.3")
   public static final String LEGACY_TABLE_NAME = "quickSearchValidationRun";

   public static final String STATUS_PASS = "PASS";
   public static final String STATUS_WARN = "WARN";
   public static final String STATUS_FAIL = "FAIL";
   public static final String STATUS_ERROR = "ERROR";
   public static final String TRIGGER_MANUAL = "MANUAL";
   public static final String TRIGGER_SCHEDULED = "SCHEDULED";

   @QField()
   private Integer id;

   @QField()
   private String status;

   @QField()
   private String triggerType;

   @QField()
   private String userName;

   @QField()
   private String tableNames;

   @QField()
   private Integer sampleSize;

   @QField()
   private Integer tablesChecked;

   @QField()
   private Integer failCount;

   @QField()
   private Integer warnCount;

   @QField()
   private String summary;

   @QField()
   private String reportHtml;

   @QField()
   private String reportJson;

   @QField()
   private Instant startTime;

   @QField()
   private Instant endTime;

   @QField()
   private Instant createDate;



   /*******************************************************************************
    ** Default constructor
    *******************************************************************************/
   public QuickSearchValidationRun()
   {
   }



   /*******************************************************************************
    ** Constructor from a QRecord
    *******************************************************************************/
   public QuickSearchValidationRun(QRecord record) throws QException
   {
      populateFromQRecord(record);
   }



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
   public QuickSearchValidationRun withId(Integer id)
   {
      this.id = id;
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
    ** Setter for status
    *******************************************************************************/
   public void setStatus(String status)
   {
      this.status = status;
   }



   /*******************************************************************************
    ** Fluent setter for status
    *******************************************************************************/
   public QuickSearchValidationRun withStatus(String status)
   {
      this.status = status;
      return (this);
   }



   /*******************************************************************************
    ** Getter for triggerType
    *******************************************************************************/
   public String getTriggerType()
   {
      return (this.triggerType);
   }



   /*******************************************************************************
    ** Setter for triggerType
    *******************************************************************************/
   public void setTriggerType(String triggerType)
   {
      this.triggerType = triggerType;
   }



   /*******************************************************************************
    ** Fluent setter for triggerType
    *******************************************************************************/
   public QuickSearchValidationRun withTriggerType(String triggerType)
   {
      this.triggerType = triggerType;
      return (this);
   }



   /*******************************************************************************
    ** Getter for userName
    *******************************************************************************/
   public String getUserName()
   {
      return (this.userName);
   }



   /*******************************************************************************
    ** Setter for userName
    *******************************************************************************/
   public void setUserName(String userName)
   {
      this.userName = userName;
   }



   /*******************************************************************************
    ** Fluent setter for userName
    *******************************************************************************/
   public QuickSearchValidationRun withUserName(String userName)
   {
      this.userName = userName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for tableNames
    *******************************************************************************/
   public String getTableNames()
   {
      return (this.tableNames);
   }



   /*******************************************************************************
    ** Setter for tableNames
    *******************************************************************************/
   public void setTableNames(String tableNames)
   {
      this.tableNames = tableNames;
   }



   /*******************************************************************************
    ** Fluent setter for tableNames
    *******************************************************************************/
   public QuickSearchValidationRun withTableNames(String tableNames)
   {
      this.tableNames = tableNames;
      return (this);
   }



   /*******************************************************************************
    ** Getter for sampleSize
    *******************************************************************************/
   public Integer getSampleSize()
   {
      return (this.sampleSize);
   }



   /*******************************************************************************
    ** Setter for sampleSize
    *******************************************************************************/
   public void setSampleSize(Integer sampleSize)
   {
      this.sampleSize = sampleSize;
   }



   /*******************************************************************************
    ** Fluent setter for sampleSize
    *******************************************************************************/
   public QuickSearchValidationRun withSampleSize(Integer sampleSize)
   {
      this.sampleSize = sampleSize;
      return (this);
   }



   /*******************************************************************************
    ** Getter for tablesChecked
    *******************************************************************************/
   public Integer getTablesChecked()
   {
      return (this.tablesChecked);
   }



   /*******************************************************************************
    ** Setter for tablesChecked
    *******************************************************************************/
   public void setTablesChecked(Integer tablesChecked)
   {
      this.tablesChecked = tablesChecked;
   }



   /*******************************************************************************
    ** Fluent setter for tablesChecked
    *******************************************************************************/
   public QuickSearchValidationRun withTablesChecked(Integer tablesChecked)
   {
      this.tablesChecked = tablesChecked;
      return (this);
   }



   /*******************************************************************************
    ** Getter for failCount
    *******************************************************************************/
   public Integer getFailCount()
   {
      return (this.failCount);
   }



   /*******************************************************************************
    ** Setter for failCount
    *******************************************************************************/
   public void setFailCount(Integer failCount)
   {
      this.failCount = failCount;
   }



   /*******************************************************************************
    ** Fluent setter for failCount
    *******************************************************************************/
   public QuickSearchValidationRun withFailCount(Integer failCount)
   {
      this.failCount = failCount;
      return (this);
   }



   /*******************************************************************************
    ** Getter for warnCount
    *******************************************************************************/
   public Integer getWarnCount()
   {
      return (this.warnCount);
   }



   /*******************************************************************************
    ** Setter for warnCount
    *******************************************************************************/
   public void setWarnCount(Integer warnCount)
   {
      this.warnCount = warnCount;
   }



   /*******************************************************************************
    ** Fluent setter for warnCount
    *******************************************************************************/
   public QuickSearchValidationRun withWarnCount(Integer warnCount)
   {
      this.warnCount = warnCount;
      return (this);
   }



   /*******************************************************************************
    ** Getter for summary
    *******************************************************************************/
   public String getSummary()
   {
      return (this.summary);
   }



   /*******************************************************************************
    ** Setter for summary
    *******************************************************************************/
   public void setSummary(String summary)
   {
      this.summary = summary;
   }



   /*******************************************************************************
    ** Fluent setter for summary
    *******************************************************************************/
   public QuickSearchValidationRun withSummary(String summary)
   {
      this.summary = summary;
      return (this);
   }



   /*******************************************************************************
    ** Getter for reportHtml
    *******************************************************************************/
   public String getReportHtml()
   {
      return (this.reportHtml);
   }



   /*******************************************************************************
    ** Setter for reportHtml
    *******************************************************************************/
   public void setReportHtml(String reportHtml)
   {
      this.reportHtml = reportHtml;
   }



   /*******************************************************************************
    ** Fluent setter for reportHtml
    *******************************************************************************/
   public QuickSearchValidationRun withReportHtml(String reportHtml)
   {
      this.reportHtml = reportHtml;
      return (this);
   }



   /*******************************************************************************
    ** Getter for reportJson
    *******************************************************************************/
   public String getReportJson()
   {
      return (this.reportJson);
   }



   /*******************************************************************************
    ** Setter for reportJson
    *******************************************************************************/
   public void setReportJson(String reportJson)
   {
      this.reportJson = reportJson;
   }



   /*******************************************************************************
    ** Fluent setter for reportJson
    *******************************************************************************/
   public QuickSearchValidationRun withReportJson(String reportJson)
   {
      this.reportJson = reportJson;
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
    ** Setter for startTime
    *******************************************************************************/
   public void setStartTime(Instant startTime)
   {
      this.startTime = startTime;
   }



   /*******************************************************************************
    ** Fluent setter for startTime
    *******************************************************************************/
   public QuickSearchValidationRun withStartTime(Instant startTime)
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
    ** Setter for endTime
    *******************************************************************************/
   public void setEndTime(Instant endTime)
   {
      this.endTime = endTime;
   }



   /*******************************************************************************
    ** Fluent setter for endTime
    *******************************************************************************/
   public QuickSearchValidationRun withEndTime(Instant endTime)
   {
      this.endTime = endTime;
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
   public QuickSearchValidationRun withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }

}

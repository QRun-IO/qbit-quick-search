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
 ** A Quick Search diagnostic event: a failed search (OpenSearch error or timeout,
 ** after which core record search falls back to its own search), a diagnostic
 ** tool run (the audit trail of the admin tools), or a refused run (rate limit).
 ** Written by DiagnosticEventRecorder; read-only for operators.
 *******************************************************************************/
public class QuickSearchDiagnosticEvent extends QRecordEntity
{
   public static final String TABLE_NAME = "quick_search_diagnostic_event";

   /** Table name before 1.1.0-RC.3 (camelCase). Kept only for upgrade scripts; see docs/MIGRATION-1.1.md. */
   @Deprecated(since = "1.1.0-RC.3")
   public static final String LEGACY_TABLE_NAME = "quickSearchDiagnosticEvent";

   public static final String TYPE_SEARCH_FAILED = "SEARCH_FAILED";
   public static final String TYPE_TOOL_RUN = "TOOL_RUN";
   public static final String TYPE_REINDEX = "REINDEX";
   public static final String TYPE_RATE_LIMITED = "RATE_LIMITED";
   public static final String SOURCE_CORE_RECORD_SEARCH = "CORE_RECORD_SEARCH";
   public static final String SOURCE_QUICK_SEARCH_ACTION = "QUICK_SEARCH_ACTION";
   public static final String OUTCOME_OK = "OK";
   public static final String OUTCOME_ERROR = "ERROR";
   public static final String OUTCOME_REFUSED = "REFUSED";

   @QField()
   private Integer id;

   @QField()
   private String eventType;

   @QField()
   private String source;

   @QField()
   private String outcome;

   @QField()
   private String userName;

   @QField()
   private String tableNames;

   @QField()
   private String searchTerm;

   @QField()
   private Integer durationMillis;

   @QField()
   private String errorClass;

   @QField()
   private String errorMessage;

   @QField()
   private String detail;

   @QField()
   private String hostName;

   @QField()
   private Instant createDate;



   /*******************************************************************************
    ** Default constructor
    *******************************************************************************/
   public QuickSearchDiagnosticEvent()
   {
   }



   /*******************************************************************************
    ** Constructor from a QRecord
    *******************************************************************************/
   public QuickSearchDiagnosticEvent(QRecord record) throws QException
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
   public QuickSearchDiagnosticEvent withId(Integer id)
   {
      this.id = id;
      return (this);
   }



   /*******************************************************************************
    ** Getter for eventType
    *******************************************************************************/
   public String getEventType()
   {
      return (this.eventType);
   }



   /*******************************************************************************
    ** Setter for eventType
    *******************************************************************************/
   public void setEventType(String eventType)
   {
      this.eventType = eventType;
   }



   /*******************************************************************************
    ** Fluent setter for eventType
    *******************************************************************************/
   public QuickSearchDiagnosticEvent withEventType(String eventType)
   {
      this.eventType = eventType;
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
   public QuickSearchDiagnosticEvent withSource(String source)
   {
      this.source = source;
      return (this);
   }



   /*******************************************************************************
    ** Getter for outcome
    *******************************************************************************/
   public String getOutcome()
   {
      return (this.outcome);
   }



   /*******************************************************************************
    ** Setter for outcome
    *******************************************************************************/
   public void setOutcome(String outcome)
   {
      this.outcome = outcome;
   }



   /*******************************************************************************
    ** Fluent setter for outcome
    *******************************************************************************/
   public QuickSearchDiagnosticEvent withOutcome(String outcome)
   {
      this.outcome = outcome;
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
   public QuickSearchDiagnosticEvent withUserName(String userName)
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
   public QuickSearchDiagnosticEvent withTableNames(String tableNames)
   {
      this.tableNames = tableNames;
      return (this);
   }



   /*******************************************************************************
    ** Getter for searchTerm
    *******************************************************************************/
   public String getSearchTerm()
   {
      return (this.searchTerm);
   }



   /*******************************************************************************
    ** Setter for searchTerm
    *******************************************************************************/
   public void setSearchTerm(String searchTerm)
   {
      this.searchTerm = searchTerm;
   }



   /*******************************************************************************
    ** Fluent setter for searchTerm
    *******************************************************************************/
   public QuickSearchDiagnosticEvent withSearchTerm(String searchTerm)
   {
      this.searchTerm = searchTerm;
      return (this);
   }



   /*******************************************************************************
    ** Getter for durationMillis
    *******************************************************************************/
   public Integer getDurationMillis()
   {
      return (this.durationMillis);
   }



   /*******************************************************************************
    ** Setter for durationMillis
    *******************************************************************************/
   public void setDurationMillis(Integer durationMillis)
   {
      this.durationMillis = durationMillis;
   }



   /*******************************************************************************
    ** Fluent setter for durationMillis
    *******************************************************************************/
   public QuickSearchDiagnosticEvent withDurationMillis(Integer durationMillis)
   {
      this.durationMillis = durationMillis;
      return (this);
   }



   /*******************************************************************************
    ** Getter for errorClass
    *******************************************************************************/
   public String getErrorClass()
   {
      return (this.errorClass);
   }



   /*******************************************************************************
    ** Setter for errorClass
    *******************************************************************************/
   public void setErrorClass(String errorClass)
   {
      this.errorClass = errorClass;
   }



   /*******************************************************************************
    ** Fluent setter for errorClass
    *******************************************************************************/
   public QuickSearchDiagnosticEvent withErrorClass(String errorClass)
   {
      this.errorClass = errorClass;
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
    ** Setter for errorMessage
    *******************************************************************************/
   public void setErrorMessage(String errorMessage)
   {
      this.errorMessage = errorMessage;
   }



   /*******************************************************************************
    ** Fluent setter for errorMessage
    *******************************************************************************/
   public QuickSearchDiagnosticEvent withErrorMessage(String errorMessage)
   {
      this.errorMessage = errorMessage;
      return (this);
   }



   /*******************************************************************************
    ** Getter for detail
    *******************************************************************************/
   public String getDetail()
   {
      return (this.detail);
   }



   /*******************************************************************************
    ** Setter for detail
    *******************************************************************************/
   public void setDetail(String detail)
   {
      this.detail = detail;
   }



   /*******************************************************************************
    ** Fluent setter for detail
    *******************************************************************************/
   public QuickSearchDiagnosticEvent withDetail(String detail)
   {
      this.detail = detail;
      return (this);
   }



   /*******************************************************************************
    ** Getter for hostName
    *******************************************************************************/
   public String getHostName()
   {
      return (this.hostName);
   }



   /*******************************************************************************
    ** Setter for hostName
    *******************************************************************************/
   public void setHostName(String hostName)
   {
      this.hostName = hostName;
   }



   /*******************************************************************************
    ** Fluent setter for hostName
    *******************************************************************************/
   public QuickSearchDiagnosticEvent withHostName(String hostName)
   {
      this.hostName = hostName;
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
   public QuickSearchDiagnosticEvent withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }

}

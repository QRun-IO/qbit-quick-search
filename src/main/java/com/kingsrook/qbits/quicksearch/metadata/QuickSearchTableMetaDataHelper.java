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

package com.kingsrook.qbits.quicksearch.metadata;


import java.util.List;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QFieldSection;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Tier;
import com.kingsrook.qqq.backend.core.model.metadata.tables.UniqueKey;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.model.QuickSearchDiagnosticEvent;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;
import com.kingsrook.qbits.quicksearch.model.QuickSearchValidationRun;


/*******************************************************************************
 ** Builds the QTableMetaData for the three operational tables. Shared by the
 ** component producers and by tests that need the same shapes.
 *******************************************************************************/
public class QuickSearchTableMetaDataHelper
{

   /*******************************************************************************
    ** quickSearchIndex: one row per indexed table.
    *******************************************************************************/
   public static QTableMetaData buildIndexTable(QuickSearchQBitConfig config)
   {
      return (new QTableMetaData()
         .withName(config.applyPrefix(QuickSearchIndex.TABLE_NAME))
         .withLabel("Quick Search Index")
         .withIcon(new QIcon("manage_search"))
         .withBackendName(config.getBackendName())
         .withPrimaryKeyField("id")
         .withRecordLabelFields("tableName")
         .withUniqueKey(new UniqueKey("tableName"))
         .withPermissionRules(config.getTablePermissionRules() == null ? null : config.getTablePermissionRules().clone())
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("tableName", QFieldType.STRING).withIsRequired(true).withIsEditable(false))
         .withField(new QFieldMetaData("enabled", QFieldType.BOOLEAN))
         .withField(new QFieldMetaData("basepullIntervalMinutes", QFieldType.INTEGER))
         .withField(new QFieldMetaData("basepullTimestampField", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("searchableFieldsJson", QFieldType.TEXT).withIsEditable(false))
         .withField(new QFieldMetaData("lastBasepullTime", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("lastFullReindexTime", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("lastReconcileTime", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("lastRunStatus", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("lastErrorMessage", QFieldType.TEXT).withIsEditable(false))
         .withField(new QFieldMetaData("recordCount", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("documentCount", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("realTimeErrorCount", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("status", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("createDate", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME).withIsEditable(false)));
   }



   /*******************************************************************************
    ** quickSearchIndexRun: history of basepull, reindex and reconcile runs.
    *******************************************************************************/
   public static QTableMetaData buildIndexRunTable(QuickSearchQBitConfig config)
   {
      return (new QTableMetaData()
         .withName(config.applyPrefix(QuickSearchIndexRun.TABLE_NAME))
         .withLabel("Quick Search Index Run")
         .withIcon(new QIcon("history"))
         .withBackendName(config.getBackendName())
         .withPrimaryKeyField("id")
         .withPermissionRules(config.getTablePermissionRules() == null ? null : config.getTablePermissionRules().clone())
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("quickSearchIndexId", QFieldType.INTEGER).withIsEditable(false)
            .withPossibleValueSourceName(config.applyPrefix(QuickSearchIndex.TABLE_NAME)))
         .withField(new QFieldMetaData("runType", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("status", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("startTime", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("endTime", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("recordsProcessed", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("recordsIndexed", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("errorCount", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("errorMessage", QFieldType.TEXT).withIsEditable(false))
         .withField(new QFieldMetaData("createDate", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME).withIsEditable(false)));
   }



   /*******************************************************************************
    ** quickSearchFailedEvent: real-time index or delete events that could not
    ** reach OpenSearch, replayed by the scheduled basepull.
    *******************************************************************************/
   public static QTableMetaData buildFailedEventTable(QuickSearchQBitConfig config)
   {
      return (new QTableMetaData()
         .withName(config.applyPrefix(QuickSearchFailedEvent.TABLE_NAME))
         .withLabel("Quick Search Failed Event")
         .withIcon(new QIcon("error_outline"))
         .withBackendName(config.getBackendName())
         .withPrimaryKeyField("id")
         .withPermissionRules(config.getTablePermissionRules() == null ? null : config.getTablePermissionRules().clone())
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("tableName", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("recordId", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("action", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("errorMessage", QFieldType.TEXT).withIsEditable(false))
         .withField(new QFieldMetaData("attempts", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("status", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("createDate", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME).withIsEditable(false)));
   }



   /*******************************************************************************
    ** quickSearchDiagnosticEvent: failed searches, diagnostic tool runs (the
    ** audit trail) and refused runs. Written by the QBit only.
    *******************************************************************************/
   public static QTableMetaData buildDiagnosticEventTable(QuickSearchQBitConfig config)
   {
      return (new QTableMetaData()
         .withName(config.applyPrefix(QuickSearchDiagnosticEvent.TABLE_NAME))
         .withLabel("Quick Search Diagnostic Event")
         .withIcon(new QIcon("bug_report"))
         .withBackendName(config.getBackendName())
         .withPrimaryKeyField("id")
         .withRecordLabelFormat("%s %s")
         .withRecordLabelFields("eventType", "source")
         .withPermissionRules(config.getTablePermissionRules() == null ? null : config.getTablePermissionRules().clone())
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("eventType", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("source", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("outcome", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("userName", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("tableNames", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("searchTerm", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("durationMillis", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("errorClass", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("errorMessage", QFieldType.TEXT).withIsEditable(false))
         .withField(new QFieldMetaData("detail", QFieldType.TEXT).withIsEditable(false))
         .withField(new QFieldMetaData("hostName", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("createDate", QFieldType.DATE_TIME).withIsEditable(false))
         .withSection(new QFieldSection("identity", "Event", new QIcon("bug_report"), Tier.T1, List.of("id", "createDate", "eventType", "source", "outcome")))
         .withSection(new QFieldSection("request", "Request", new QIcon("search"), Tier.T2, List.of("userName", "tableNames", "searchTerm", "durationMillis", "hostName")))
         .withSection(new QFieldSection("error", "Error and detail", new QIcon("error_outline"), Tier.T2, List.of("errorClass", "errorMessage", "detail"))));
   }



   /*******************************************************************************
    ** quickSearchValidationRun: index validation reports (history). The
    ** report is an HTML field, shown in full on the record view.
    *******************************************************************************/
   public static QTableMetaData buildValidationRunTable(QuickSearchQBitConfig config)
   {
      return (new QTableMetaData()
         .withName(config.applyPrefix(QuickSearchValidationRun.TABLE_NAME))
         .withLabel("Quick Search Validation Run")
         .withIcon(new QIcon("fact_check"))
         .withBackendName(config.getBackendName())
         .withPrimaryKeyField("id")
         .withRecordLabelFormat("%s %s")
         .withRecordLabelFields("status", "startTime")
         .withPermissionRules(config.getTablePermissionRules() == null ? null : config.getTablePermissionRules().clone())
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("status", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("triggerType", QFieldType.STRING).withLabel("Trigger").withIsEditable(false))
         .withField(new QFieldMetaData("userName", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("tableNames", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("sampleSize", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("tablesChecked", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("failCount", QFieldType.INTEGER).withLabel("Failed Checks").withIsEditable(false))
         .withField(new QFieldMetaData("warnCount", QFieldType.INTEGER).withLabel("Warnings").withIsEditable(false))
         .withField(new QFieldMetaData("summary", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("reportHtml", QFieldType.HTML).withLabel("Report").withIsEditable(false))
         .withField(new QFieldMetaData("reportJson", QFieldType.TEXT).withLabel("Report JSON").withIsEditable(false))
         .withField(new QFieldMetaData("startTime", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("endTime", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("createDate", QFieldType.DATE_TIME).withIsEditable(false))
         .withSection(new QFieldSection("identity", "Run", new QIcon("fact_check"), Tier.T1, List.of("id", "status", "summary", "triggerType", "startTime", "endTime")))
         .withSection(new QFieldSection("scope", "Scope", new QIcon("table_chart"), Tier.T2, List.of("userName", "tableNames", "sampleSize", "tablesChecked", "failCount", "warnCount", "createDate")))
         .withSection(new QFieldSection("report", "Report", new QIcon("description"), Tier.T2, List.of("reportHtml")))
         .withSection(new QFieldSection("data", "Report data (JSON)", new QIcon("data_object"), Tier.T3, List.of("reportJson"))));
   }



   /*******************************************************************************
    ** Possible value source over quickSearchIndex, so run rows show the table
    ** name instead of an id.
    *******************************************************************************/
   public static QPossibleValueSource buildIndexPossibleValueSource(QuickSearchQBitConfig config)
   {
      return (QPossibleValueSource.newForTable(config.applyPrefix(QuickSearchIndex.TABLE_NAME)));
   }

}

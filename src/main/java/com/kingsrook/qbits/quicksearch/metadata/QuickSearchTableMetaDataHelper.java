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
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizers;
import com.kingsrook.qqq.backend.core.model.metadata.audits.AuditLevel;
import com.kingsrook.qqq.backend.core.model.metadata.audits.QAuditRules;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.AdornmentType;
import com.kingsrook.qqq.backend.core.model.metadata.fields.FieldAdornment;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.help.QHelpContent;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Association;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Capability;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QFieldSection;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Tier;
import com.kingsrook.qqq.backend.core.model.metadata.tables.UniqueKey;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.config.QuickSearchIndexTableCustomizer;
import com.kingsrook.qbits.quicksearch.model.QuickSearchDiagnosticEvent;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexField;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;
import com.kingsrook.qbits.quicksearch.model.QuickSearchValidationRun;


/*******************************************************************************
 ** Builds the QTableMetaData for the three operational tables. Shared by the
 ** component producers and by tests that need the same shapes.
 *******************************************************************************/
public class QuickSearchTableMetaDataHelper
{

   /*******************************************************************************
    ** quick_search_index: one row per indexed table.
    *******************************************************************************/
   public static QTableMetaData buildIndexTable(QuickSearchQBitConfig config)
   {
      ////////////////////////////////////////////////////////////////////////////
      // rows are added by Add Search Index (and by self-priming) and removed  //
      // by Remove Search Index, which validate, preview and build or clean up //
      // the index; the generic create and delete screens are turned off      //
      ////////////////////////////////////////////////////////////////////////////
      return (new QTableMetaData()
         .withName(config.applyTablePrefix(QuickSearchIndex.TABLE_NAME))
         .withLabel("Quick Search Index")
         .withIcon(new QIcon("manage_search"))
         .withBackendName(config.getBackendName())
         .withPrimaryKeyField("id")
         .withRecordLabelFields("tableName")
         .withUniqueKey(new UniqueKey("tableName"))
         .withoutCapabilities(Capability.TABLE_INSERT, Capability.TABLE_DELETE)
         .withPermissionRules(config.getTablePermissionRules() == null ? null : config.getTablePermissionRules().clone())
         .withAuditRules(new QAuditRules().withAuditLevel(AuditLevel.FIELD))
         .withCustomizer(TableCustomizers.PRE_INSERT_RECORD, new QCodeReference(QuickSearchIndexTableCustomizer.class))
         .withCustomizer(TableCustomizers.PRE_UPDATE_RECORD, new QCodeReference(QuickSearchIndexTableCustomizer.class))
         .withCustomizer(TableCustomizers.POST_INSERT_RECORD, new QCodeReference(QuickSearchIndexTableCustomizer.class))
         .withCustomizer(TableCustomizers.POST_UPDATE_RECORD, new QCodeReference(QuickSearchIndexTableCustomizer.class))
         .withCustomizer(TableCustomizers.POST_DELETE_RECORD, new QCodeReference(QuickSearchIndexTableCustomizer.class))
         .withAssociation(new Association()
            .withName(QuickSearchConfigMetaDataHelper.FIELDS_ASSOCIATION)
            .withAssociatedTableName(config.applyTablePrefix(QuickSearchIndexField.TABLE_NAME))
            .withJoinName(config.applyTablePrefix(QuickSearchConfigMetaDataHelper.INDEX_FIELDS_JOIN)))
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("tableName", QFieldType.STRING).withLabel("Table").withIsRequired(true).withIsEditable(false)
            .withPossibleValueSourceName(config.applyTablePrefix(QuickSearchSearchableTablePossibleValueSourceProducer.NAME)))
         .withField(new QFieldMetaData("enabled", QFieldType.BOOLEAN))
         .withField(new QFieldMetaData("basepullIntervalMinutes", QFieldType.INTEGER).withLabel("Update Interval (Minutes)"))
         .withField(new QFieldMetaData("basepullTimestampField", QFieldType.STRING).withLabel("Timestamp Field")
            .withPossibleValueSourceName(config.applyTablePrefix(QuickSearchConfigMetaDataHelper.TIMESTAMP_FIELD_PVS)))
         .withField(new QFieldMetaData("source", QFieldType.STRING).withIsEditable(false)
            .withPossibleValueSourceName(config.applyTablePrefix(QuickSearchConfigMetaDataHelper.CONFIG_SOURCE_PVS)))
         .withField(new QFieldMetaData("ownerEdited", QFieldType.BOOLEAN).withLabel("Edited by Owner").withIsEditable(false))
         .withField(new QFieldMetaData("searchableFieldsJson", QFieldType.TEXT).withLabel("Fields as Last Built").withIsEditable(false))
         .withField(new QFieldMetaData("lastBasepullTime", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("lastFullReindexTime", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("lastReconcileTime", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("lastRunStatus", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("lastErrorMessage", QFieldType.TEXT).withIsEditable(false))
         .withField(new QFieldMetaData("recordCount", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("documentCount", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("realTimeErrorCount", QFieldType.INTEGER).withIsEditable(false)
            .withHelpContent(new QHelpContent("Real-time indexing failures since the last full reindex or basepull")))
         .withField(new QFieldMetaData("status", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("createDate", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME).withIsEditable(false))
         .withSection(new QFieldSection("identity", "Search Index", new QIcon("manage_search"), Tier.T1,
            List.of("id", "tableName", "enabled", "status", "basepullIntervalMinutes", "basepullTimestampField")))
         .withSection(new QFieldSection("fields", "Searchable Fields", new QIcon("text_fields"), Tier.T2, null)
            .withWidgetName(config.applyPrefix(QuickSearchConfigMetaDataHelper.INDEX_FIELDS_WIDGET)))
         .withSection(new QFieldSection("runs", "Recent Runs", new QIcon("history"), Tier.T2, null)
            .withWidgetName(config.applyPrefix(QuickSearchConfigMetaDataHelper.INDEX_RUNS_WIDGET)))
         .withSection(new QFieldSection("activity", "Activity", new QIcon("insights"), Tier.T2,
            List.of("lastBasepullTime", "lastFullReindexTime", "lastReconcileTime", "lastRunStatus", "lastErrorMessage", "recordCount", "documentCount", "realTimeErrorCount")))
         .withSection(new QFieldSection("origin", "Origin", new QIcon("info"), Tier.T3,
            List.of("source", "ownerEdited", "searchableFieldsJson", "createDate", "modifyDate"))));
   }



   /*******************************************************************************
    ** quick_search_index_run: history of basepull, reindex and reconcile runs.
    *******************************************************************************/
   public static QTableMetaData buildIndexRunTable(QuickSearchQBitConfig config)
   {
      /////////////////////////////////////////////////////////////////////////
      // run history is written by the QBit only: no create or edit screens //
      // (Delete stays, for clearing); Cancel Index Run sets the cancel flag //
      /////////////////////////////////////////////////////////////////////////
      return (new QTableMetaData()
         .withName(config.applyTablePrefix(QuickSearchIndexRun.TABLE_NAME))
         .withLabel("Quick Search Index Run")
         .withIcon(new QIcon("history"))
         .withBackendName(config.getBackendName())
         .withPrimaryKeyField("id")
         .withoutCapabilities(Capability.TABLE_INSERT, Capability.TABLE_UPDATE)
         .withRecordLabelFormat("%s %s")
         .withRecordLabelFields("runType", "startTime")
         .withPermissionRules(config.getTablePermissionRules() == null ? null : config.getTablePermissionRules().clone())
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("quickSearchIndexId", QFieldType.INTEGER).withLabel("Search Index").withIsEditable(false)
            .withPossibleValueSourceName(config.applyTablePrefix(QuickSearchIndex.TABLE_NAME)))
         .withField(new QFieldMetaData("runType", QFieldType.STRING).withIsEditable(false)
            .withPossibleValueSourceName(config.applyTablePrefix(QuickSearchConfigMetaDataHelper.RUN_TYPE_PVS)))
         .withField(new QFieldMetaData("status", QFieldType.STRING).withIsEditable(false)
            .withPossibleValueSourceName(config.applyTablePrefix(QuickSearchConfigMetaDataHelper.RUN_STATUS_PVS)))
         .withField(new QFieldMetaData("startTime", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("endTime", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("recordsProcessed", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("recordsIndexed", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("errorCount", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("errorMessage", QFieldType.TEXT).withIsEditable(false))
         .withField(new QFieldMetaData("cancelRequested", QFieldType.BOOLEAN).withIsEditable(false))
         .withField(new QFieldMetaData("cancelRequestedBy", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("createDate", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME).withLabel("Last Heartbeat").withIsEditable(false))
         .withSection(new QFieldSection("run", "Run", new QIcon("history"), Tier.T1, List.of("id", "quickSearchIndexId", "runType", "status", "startTime", "endTime")))
         .withSection(new QFieldSection("counts", "Counts", new QIcon("functions"), Tier.T2, List.of("recordsProcessed", "recordsIndexed", "errorCount", "errorMessage")))
         .withSection(new QFieldSection("cancel", "Cancel", new QIcon("cancel"), Tier.T2, List.of("cancelRequested", "cancelRequestedBy")))
         .withSection(new QFieldSection("dates", "Dates", new QIcon("calendar_today"), Tier.T3, List.of("createDate", "modifyDate"))));
   }



   /*******************************************************************************
    ** quick_search_failed_event: real-time index or delete events that could not
    ** reach OpenSearch, replayed by the scheduled basepull.
    *******************************************************************************/
   public static QTableMetaData buildFailedEventTable(QuickSearchQBitConfig config)
   {
      /////////////////////////////////////////////////////////////////////////
      // system events, written by the QBit only: no create or edit screens //
      // (Delete stays, for clearing)                                        //
      /////////////////////////////////////////////////////////////////////////
      return (new QTableMetaData()
         .withName(config.applyTablePrefix(QuickSearchFailedEvent.TABLE_NAME))
         .withLabel("Quick Search Failed Event")
         .withIcon(new QIcon("error_outline"))
         .withBackendName(config.getBackendName())
         .withPrimaryKeyField("id")
         .withoutCapabilities(Capability.TABLE_INSERT, Capability.TABLE_UPDATE)
         .withRecordLabelFormat("%s %s")
         .withRecordLabelFields("tableName", "recordId")
         .withPermissionRules(config.getTablePermissionRules() == null ? null : config.getTablePermissionRules().clone())
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("tableName", QFieldType.STRING).withLabel("Table").withIsEditable(false)
            .withPossibleValueSourceName(config.applyTablePrefix(QuickSearchSearchableTablePossibleValueSourceProducer.NAME)))
         .withField(new QFieldMetaData("recordId", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("action", QFieldType.STRING).withIsEditable(false)
            .withPossibleValueSourceName(config.applyTablePrefix(QuickSearchConfigMetaDataHelper.EVENT_ACTION_PVS)))
         .withField(new QFieldMetaData("errorMessage", QFieldType.TEXT).withIsEditable(false))
         .withField(new QFieldMetaData("attempts", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("status", QFieldType.STRING).withIsEditable(false)
            .withPossibleValueSourceName(config.applyTablePrefix(QuickSearchConfigMetaDataHelper.EVENT_STATUS_PVS)))
         .withField(new QFieldMetaData("createDate", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME).withIsEditable(false))
         .withSection(new QFieldSection("event", "Event", new QIcon("error_outline"), Tier.T1, List.of("id", "tableName", "recordId", "action", "status")))
         .withSection(new QFieldSection("error", "Error and retries", new QIcon("replay"), Tier.T2, List.of("errorMessage", "attempts")))
         .withSection(new QFieldSection("dates", "Dates", new QIcon("calendar_today"), Tier.T3, List.of("createDate", "modifyDate"))));
   }



   /*******************************************************************************
    ** quick_search_diagnostic_event: failed searches, diagnostic tool runs (the
    ** audit trail) and refused runs. Written by the QBit only: insert, update
    ** and delete are disabled for users and the API (core actions, which the
    ** QBit uses, do not check capabilities).
    *******************************************************************************/
   public static QTableMetaData buildDiagnosticEventTable(QuickSearchQBitConfig config)
   {
      return (new QTableMetaData()
         .withName(config.applyTablePrefix(QuickSearchDiagnosticEvent.TABLE_NAME))
         .withLabel("Quick Search Diagnostic Event")
         .withoutCapabilities(Capability.TABLE_INSERT, Capability.TABLE_UPDATE, Capability.TABLE_DELETE)
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
    ** quick_search_validation_run: index validation reports (history). The
    ** report is an HTML field, shown in full on the record view; only the
    ** QBit writes rows (insert, update and delete are disabled for users and
    ** the API), so the HTML is always the QBit's escaped output.
    *******************************************************************************/
   public static QTableMetaData buildValidationRunTable(QuickSearchQBitConfig config)
   {
      return (new QTableMetaData()
         .withName(config.applyTablePrefix(QuickSearchValidationRun.TABLE_NAME))
         .withLabel("Quick Search Validation Run")
         .withoutCapabilities(Capability.TABLE_INSERT, Capability.TABLE_UPDATE, Capability.TABLE_DELETE)
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
         .withField(new QFieldMetaData("reportJson", QFieldType.TEXT).withLabel("Report JSON").withIsEditable(false)
            .withFieldAdornment(new FieldAdornment(AdornmentType.CODE_EDITOR).withValue(AdornmentType.CodeEditorValues.languageMode("json"))))
         .withField(new QFieldMetaData("startTime", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("endTime", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("createDate", QFieldType.DATE_TIME).withIsEditable(false))
         .withSection(new QFieldSection("identity", "Run", new QIcon("fact_check"), Tier.T1, List.of("id", "status", "summary", "triggerType", "startTime", "endTime")))
         .withSection(new QFieldSection("scope", "Scope", new QIcon("table_chart"), Tier.T2, List.of("userName", "tableNames", "sampleSize", "tablesChecked", "failCount", "warnCount", "createDate")))
         .withSection(new QFieldSection("report", "Report", new QIcon("description"), Tier.T2, List.of("reportHtml")))
         .withSection(new QFieldSection("data", "Report data (JSON)", new QIcon("data_object"), Tier.T3, List.of("reportJson"))));
   }



   /*******************************************************************************
    ** Possible value source over quick_search_index, so run rows show the table
    ** name instead of an id.
    *******************************************************************************/
   public static QPossibleValueSource buildIndexPossibleValueSource(QuickSearchQBitConfig config)
   {
      return (QPossibleValueSource.newForTable(config.applyTablePrefix(QuickSearchIndex.TABLE_NAME)));
   }

}

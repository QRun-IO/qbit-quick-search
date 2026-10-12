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

package com.kingsrook.qbits.quicksearch.metadata;


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.tables.RecordSearchAction;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QBackendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QComponentType;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFrontendComponentMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFrontendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFunctionInputMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QRecordListMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.scheduleing.QScheduleMetaData;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;
import com.kingsrook.qbits.quicksearch.processes.diagnostics.AbstractDiagnosticToolStep;
import com.kingsrook.qbits.quicksearch.processes.diagnostics.AnalyzeTextStep;
import com.kingsrook.qbits.quicksearch.processes.diagnostics.InspectRecordStep;
import com.kingsrook.qbits.quicksearch.processes.diagnostics.ReindexRecordsStep;
import com.kingsrook.qbits.quicksearch.processes.diagnostics.SearchPlaygroundStep;
import com.kingsrook.qbits.quicksearch.processes.diagnostics.ValidateIndexStep;


/*******************************************************************************
 ** Builds the diagnostic tool processes. Every one is a table or record
 ** action (it has a tableName and is never added to an app's nav):
 ** - on Quick Search Index: Search Playground, Validate Search Index (also
 **   schedulable), Test Search Analyzer;
 ** - on every indexed table and on Quick Search Failed Event: Inspect Search
 **   Document and Reindex in Search.
 ** Read-only tools use debugPermissionRules; Reindex in Search uses
 ** adminPermissionRules.
 *******************************************************************************/
public class QuickSearchDiagnosticsProcessMetaDataHelper
{
   public static final String PLAYGROUND_PROCESS_NAME     = "quickSearchPlayground";
   public static final String VALIDATE_PROCESS_NAME       = "quickSearchValidateIndex";
   public static final String ANALYZE_PROCESS_NAME        = "quickSearchAnalyzeText";
   public static final String INSPECT_PROCESS_PREFIX      = "quickSearchInspect";
   public static final String REINDEX_RECORDS_PROCESS_PREFIX = "quickSearchReindexRecords";



   /*******************************************************************************
    ** Name of the Inspect Search Document process of a table.
    *******************************************************************************/
   public static String inspectProcessName(QuickSearchQBitConfig config, String tableName)
   {
      return (config.applyPrefix(INSPECT_PROCESS_PREFIX) + "_" + tableName);
   }



   /*******************************************************************************
    ** Name of the Reindex in Search process of a table.
    *******************************************************************************/
   public static String reindexRecordsProcessName(QuickSearchQBitConfig config, String tableName)
   {
      return (config.applyPrefix(REINDEX_RECORDS_PROCESS_PREFIX) + "_" + tableName);
   }



   /*******************************************************************************
    ** Search Playground, a table action on Quick Search Index.
    *******************************************************************************/
   public static QProcessMetaData buildPlaygroundProcess(QuickSearchQBitConfig config)
   {
      return (new QProcessMetaData()
         .withName(config.applyPrefix(PLAYGROUND_PROCESS_NAME))
         .withLabel("Search Playground")
         .withIcon(new QIcon("travel_explore"))
         .withTableName(config.getQuickSearchIndexTableName())
         .withMinInputRecords(0)
         .withPermissionRules(clone(config.getDebugPermissionRules()))
         .withStep(backend(SearchPlaygroundStep.class, AbstractDiagnosticToolStep.STEP_PREPARE, null))
         .withStep(form("Search", List.of(
            new QFieldMetaData(SearchPlaygroundStep.FIELD_TERM, QFieldType.STRING).withLabel("Search term").withIsRequired(true).withMaxLength(RecordSearchAction.MAX_SEARCH_TERM_LENGTH),
            new QFieldMetaData(SearchPlaygroundStep.FIELD_TABLES, QFieldType.STRING).withLabel("Tables (optional; names separated by commas)"),
            new QFieldMetaData(SearchPlaygroundStep.FIELD_LIMIT_PER_TABLE, QFieldType.INTEGER).withLabel("Results per table").withDefaultValue(RecordSearchAction.DEFAULT_LIMIT_PER_TABLE),
            new QFieldMetaData(SearchPlaygroundStep.FIELD_EXPLAIN_HIT, QFieldType.STRING).withLabel("Explain hit (optional document id, e.g. blogPost:42)"))))
         .withStep(backend(SearchPlaygroundStep.class, AbstractDiagnosticToolStep.STEP_RUN, null)));
   }



   /*******************************************************************************
    ** Test Search Analyzer, a table action on Quick Search Index.
    *******************************************************************************/
   public static QProcessMetaData buildAnalyzeProcess(QuickSearchQBitConfig config)
   {
      return (new QProcessMetaData()
         .withName(config.applyPrefix(ANALYZE_PROCESS_NAME))
         .withLabel("Test Search Analyzer")
         .withIcon(new QIcon("text_fields"))
         .withTableName(config.getQuickSearchIndexTableName())
         .withMinInputRecords(0)
         .withPermissionRules(clone(config.getDebugPermissionRules()))
         .withStep(backend(AnalyzeTextStep.class, AbstractDiagnosticToolStep.STEP_PREPARE, null))
         .withStep(form("Analyze", List.of(
            new QFieldMetaData(AnalyzeTextStep.FIELD_TEXT, QFieldType.TEXT).withLabel("Text (as a field value)").withIsRequired(true),
            new QFieldMetaData(AnalyzeTextStep.FIELD_QUERY, QFieldType.STRING).withLabel("Search term (optional)").withMaxLength(RecordSearchAction.MAX_SEARCH_TERM_LENGTH))))
         .withStep(backend(AnalyzeTextStep.class, AbstractDiagnosticToolStep.STEP_RUN, null)));
   }



   /*******************************************************************************
    ** Validate Search Index, a table action on Quick Search Index; scheduled
    ** by cron when validationCronExpression and a schedulerName are set.
    *******************************************************************************/
   public static QProcessMetaData buildValidateProcess(QuickSearchQBitConfig config)
   {
      QProcessMetaData process = new QProcessMetaData()
         .withName(config.applyPrefix(VALIDATE_PROCESS_NAME))
         .withLabel("Validate Search Index")
         .withIcon(new QIcon("fact_check"))
         .withTableName(config.getQuickSearchIndexTableName())
         .withMinInputRecords(0)
         .withPermissionRules(clone(config.getDebugPermissionRules()))
         .withStep(backend(ValidateIndexStep.class, AbstractDiagnosticToolStep.STEP_PREPARE, null))
         .withStep(form("Validate", List.of(
            new QFieldMetaData(ValidateIndexStep.FIELD_SAMPLE_SIZE, QFieldType.INTEGER).withLabel("Records and documents to sample per table")
               .withDefaultValue(config.getValidationSampleSize()))))
         .withStep(backend(ValidateIndexStep.class, AbstractDiagnosticToolStep.STEP_RUN, null))
         .withStep(result(AbstractDiagnosticToolStep.STEP_RESULT, "Validation report"));

      if(StringUtils.hasContent(config.getSchedulerName()) && StringUtils.hasContent(config.getValidationCronExpression()))
      {
         process.withSchedule(new QScheduleMetaData()
            .withSchedulerName(config.getSchedulerName())
            .withCronExpression(config.getValidationCronExpression())
            .withCronTimeZoneId(config.getValidationCronTimeZoneId())
            .withDescription("Quick Search index validation"));
      }
      return (process);
   }



   /*******************************************************************************
    ** Inspect Search Document, a record action on a table (an indexed table,
    ** or Quick Search Failed Event for the event's record).
    *******************************************************************************/
   public static QProcessMetaData buildInspectProcess(QuickSearchQBitConfig config, String tableName)
   {
      return (new QProcessMetaData()
         .withName(inspectProcessName(config, tableName))
         .withLabel("Inspect Search Document")
         .withIcon(new QIcon("manage_search"))
         .withTableName(tableName)
         .withMinInputRecords(1)
         .withMaxInputRecords(InspectRecordStep.MAX_RECORDS)
         .withPermissionRules(clone(config.getDebugPermissionRules()))
         .withStep(backend(InspectRecordStep.class, AbstractDiagnosticToolStep.STEP_PREPARE, tableName))
         .withStep(form("Inspect", List.of(
            new QFieldMetaData(InspectRecordStep.FIELD_TERM, QFieldType.STRING).withLabel("Why doesn't it match... (optional search term)")
               .withMaxLength(RecordSearchAction.MAX_SEARCH_TERM_LENGTH))))
         .withStep(backend(InspectRecordStep.class, AbstractDiagnosticToolStep.STEP_RUN, null)));
   }



   /*******************************************************************************
    ** Reindex in Search, a record action on a table (an indexed table, or
    ** Quick Search Failed Event for the event's record). Writes to the index,
    ** so it uses the admin permission rules.
    *******************************************************************************/
   public static QProcessMetaData buildReindexRecordsProcess(QuickSearchQBitConfig config, String tableName)
   {
      return (new QProcessMetaData()
         .withName(reindexRecordsProcessName(config, tableName))
         .withLabel("Reindex in Search")
         .withIcon(new QIcon("sync"))
         .withTableName(tableName)
         .withMinInputRecords(1)
         .withMaxInputRecords(ReindexRecordsStep.MAX_RECORDS)
         .withPermissionRules(clone(config.getAdminPermissionRules()))
         .withStep(backend(ReindexRecordsStep.class, AbstractDiagnosticToolStep.STEP_PREPARE, tableName))
         .withStep(result(AbstractDiagnosticToolStep.STEP_CONFIRM, "Confirm"))
         .withStep(backend(ReindexRecordsStep.class, AbstractDiagnosticToolStep.STEP_RUN, null))
         .withStep(result(AbstractDiagnosticToolStep.STEP_RESULT, "Reindexed")));
   }



   /*******************************************************************************
    ** The fixed processes (on Quick Search Index and Quick Search Failed
    ** Event).
    *******************************************************************************/
   public static List<QProcessMetaData> buildFixedProcesses(QuickSearchQBitConfig config)
   {
      List<QProcessMetaData> processes = new ArrayList<>();
      processes.add(buildPlaygroundProcess(config));
      processes.add(buildValidateProcess(config));
      processes.add(buildAnalyzeProcess(config));
      processes.add(buildInspectProcess(config, config.applyPrefix(QuickSearchFailedEvent.TABLE_NAME)));
      processes.add(buildReindexRecordsProcess(config, config.applyPrefix(QuickSearchFailedEvent.TABLE_NAME)));
      return (processes);
   }



   /*******************************************************************************
    ** A backend step of a tool; with recordsTableName, the step receives the
    ** selected records of that table.
    *******************************************************************************/
   private static QBackendStepMetaData backend(Class<?> stepClass, String name, String recordsTableName)
   {
      QBackendStepMetaData step = new QBackendStepMetaData().withName(name).withCode(new QCodeReference(stepClass));
      if(recordsTableName != null)
      {
         step.withInputData(new QFunctionInputMetaData().withRecordListMetaData(new QRecordListMetaData().withTableName(recordsTableName)));
      }
      return (step);
   }



   /*******************************************************************************
    ** The FORM step: fields above, the tool's HTML below.
    *******************************************************************************/
   private static QFrontendStepMetaData form(String label, List<QFieldMetaData> fields)
   {
      QFrontendStepMetaData step = new QFrontendStepMetaData()
         .withName(AbstractDiagnosticToolStep.STEP_FORM)
         .withLabel(label)
         .withComponent(new QFrontendComponentMetaData().withType(QComponentType.EDIT_FORM))
         .withComponent(new QFrontendComponentMetaData().withType(QComponentType.HTML));
      fields.forEach(step::withFormField);
      return (step);
   }



   /*******************************************************************************
    ** An HTML-only frontend step.
    *******************************************************************************/
   private static QFrontendStepMetaData result(String name, String label)
   {
      return (new QFrontendStepMetaData()
         .withName(name)
         .withLabel(label)
         .withComponent(new QFrontendComponentMetaData().withType(QComponentType.HTML)));
   }



   /*******************************************************************************
    *******************************************************************************/
   private static QPermissionRules clone(QPermissionRules rules)
   {
      return (rules == null ? null : rules.clone());
   }

}

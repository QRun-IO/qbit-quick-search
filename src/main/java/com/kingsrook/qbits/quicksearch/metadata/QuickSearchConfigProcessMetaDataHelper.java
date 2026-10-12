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


import java.util.List;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFrontendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractExtractStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractLoadStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractTransformStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.ExtractViaQueryStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.StreamedETLWithFrontendProcess;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.model.QuickSearchDocumentsChoice;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;
import com.kingsrook.qbits.quicksearch.model.QuickSearchSettings;
import com.kingsrook.qbits.quicksearch.processes.config.AddSearchIndexSteps;
import com.kingsrook.qbits.quicksearch.processes.config.ApplyIndexChangesSteps;
import com.kingsrook.qbits.quicksearch.processes.config.CancelRunSteps;
import com.kingsrook.qbits.quicksearch.processes.config.SetIndexingPausedSteps;
import com.kingsrook.qbits.quicksearch.processes.config.StopIndexingSteps;


/*******************************************************************************
 ** The config processes. Each is a standard streamed-ETL process with a
 ** review step (the transform's summary: what will happen, counts, problems)
 ** and a result step, run from its table's Actions (never from the nav):
 ** - Add Search Index, on quick_search_index (no selection needed);
 ** - Disable / Remove Search Index and Apply Search Index Changes, on the
 **   selected quick_search_index rows;
 ** - Cancel Index Run, on the selected quick_search_index_run rows;
 ** - Pause / Resume Indexing, on the quick_search_settings row.
 ** All need the admin permission (quick_search_admin by default).
 *******************************************************************************/
public class QuickSearchConfigProcessMetaDataHelper
{
   public static final String ADD_INDEX_PROCESS_NAME     = "quickSearchAddIndex";
   public static final String DISABLE_INDEX_PROCESS_NAME = "quickSearchDisableIndex";
   public static final String REMOVE_INDEX_PROCESS_NAME  = "quickSearchRemoveIndex";
   public static final String APPLY_CHANGES_PROCESS_NAME = "quickSearchApplyIndexChanges";
   public static final String CANCEL_RUN_PROCESS_NAME    = "quickSearchCancelRun";
   public static final String PAUSE_PROCESS_NAME         = "quickSearchPauseIndexing";
   public static final String RESUME_PROCESS_NAME        = "quickSearchResumeIndexing";



   /*******************************************************************************
    ** Every config process.
    *******************************************************************************/
   public static List<QProcessMetaData> buildProcesses(QuickSearchQBitConfig config)
   {
      return (List.of(
         buildAddIndexProcess(config),
         buildStopIndexingProcess(config, DISABLE_INDEX_PROCESS_NAME, "Disable Search Index", "toggle_off", StopIndexingSteps.MODE_DISABLE, QuickSearchDocumentsChoice.KEEP),
         buildStopIndexingProcess(config, REMOVE_INDEX_PROCESS_NAME, "Remove Search Index", "delete", StopIndexingSteps.MODE_REMOVE, QuickSearchDocumentsChoice.DELETE),
         buildApplyChangesProcess(config),
         buildCancelRunProcess(config),
         buildSetPausedProcess(config, PAUSE_PROCESS_NAME, "Pause Indexing", "pause_circle", true),
         buildSetPausedProcess(config, RESUME_PROCESS_NAME, "Resume Indexing", "play_circle", false)));
   }



   /*******************************************************************************
    ** Add Search Index: the valid way to add a table.
    *******************************************************************************/
   public static QProcessMetaData buildAddIndexProcess(QuickSearchQBitConfig config)
   {
      String indexTable = config.applyTablePrefix(QuickSearchIndex.TABLE_NAME);
      QProcessMetaData process = base(config, ADD_INDEX_PROCESS_NAME, "Add Search Index", "add_circle", indexTable,
         AddSearchIndexSteps.Extract.class, AddSearchIndexSteps.Transform.class, AddSearchIndexSteps.Load.class, 0, List.of(
            new QFieldMetaData(AddSearchIndexSteps.FIELD_TABLE_NAME, QFieldType.STRING).withLabel("Table")
               .withPossibleValueSourceName(config.applyTablePrefix(QuickSearchSearchableTablePossibleValueSourceProducer.NAME)),
            new QFieldMetaData("estimatedRecords", QFieldType.INTEGER).withLabel("Records to Index"),
            new QFieldMetaData("fieldCount", QFieldType.INTEGER).withLabel("Fields"),
            new QFieldMetaData(AddSearchIndexSteps.FIELD_INTERVAL, QFieldType.INTEGER).withLabel("Update Interval (Minutes)"),
            new QFieldMetaData(AddSearchIndexSteps.FIELD_TIMESTAMP, QFieldType.STRING).withLabel("Timestamp Field"),
            new QFieldMetaData(AddSearchIndexSteps.FIELD_ENABLED, QFieldType.BOOLEAN)));

      process.withStep(0, new QFrontendStepMetaData()
         .withName("input")
         .withLabel("Choose a table")
         .withFormField(new QFieldMetaData(AddSearchIndexSteps.FIELD_TABLE_NAME, QFieldType.STRING).withLabel("Table").withIsRequired(true)
            .withPossibleValueSourceName(config.applyTablePrefix(QuickSearchConfigMetaDataHelper.ELIGIBLE_TABLE_PVS)))
         .withFormField(new QFieldMetaData(AddSearchIndexSteps.FIELD_INTERVAL, QFieldType.INTEGER).withLabel("Update Interval (Minutes)")
            .withDefaultValue(config.getDefaultBasepullIntervalMinutes()))
         .withFormField(new QFieldMetaData(AddSearchIndexSteps.FIELD_TIMESTAMP, QFieldType.STRING).withLabel("Timestamp Field (blank: detect)")
            .withPossibleValueSourceName(config.applyTablePrefix(QuickSearchConfigMetaDataHelper.TIMESTAMP_FIELD_PVS)))
         .withFormField(new QFieldMetaData(AddSearchIndexSteps.FIELD_ENABLED, QFieldType.BOOLEAN).withLabel("Enabled").withDefaultValue(true))
         .withViewField(new QFieldMetaData("help", QFieldType.STRING).withLabel(" ").withDefaultValue(
            "Only tables you can read, that are not indexed yet and are not internal or secret are offered. The review step shows the fields the index starts with "
               + "and how many records its first build indexes; change the fields afterwards under Searchable Fields on the index.")));
      return (process);
   }



   /*******************************************************************************
    ** Disable or Remove Search Index, with the documents choice.
    *******************************************************************************/
   static QProcessMetaData buildStopIndexingProcess(QuickSearchQBitConfig config, String name, String label, String icon, String mode, QuickSearchDocumentsChoice defaultChoice)
   {
      String indexTable = config.applyTablePrefix(QuickSearchIndex.TABLE_NAME);
      QProcessMetaData process = StreamedETLWithFrontendProcess.processMetaDataBuilder()
         .withName(config.applyPrefix(name))
         .withLabel(label)
         .withIcon(new QIcon(icon))
         .withTableName(indexTable)
         .withSourceTable(indexTable)
         .withDestinationTable(indexTable)
         .withExtractStepClass(ExtractViaQueryStep.class)
         .withTransformStepClass(StopIndexingSteps.Transform.class)
         .withLoadStepClass(StopIndexingSteps.Load.class)
         .withSupportsFullValidation(true)
         .withDoFullValidation(true)
         .withTransactionLevelAutoCommit()
         .withMinInputRecords(1)
         .withFields(List.of(new QFieldMetaData(StopIndexingSteps.FIELD_MODE, QFieldType.STRING).withDefaultValue(mode)))
         .withReviewStepRecordFields(indexReviewFields(config))
         .getProcessMetaData();
      process.withPermissionRules(config.getAdminPermissionRules() == null ? null : config.getAdminPermissionRules().clone());

      process.withStep(0, new QFrontendStepMetaData()
         .withName("input")
         .withLabel("Search documents")
         .withFormField(new QFieldMetaData(StopIndexingSteps.FIELD_DOCUMENTS, QFieldType.STRING).withLabel("Search Documents").withIsRequired(true)
            .withDefaultValue(defaultChoice.name())
            .withPossibleValueSourceName(config.applyTablePrefix(QuickSearchConfigMetaDataHelper.DOCUMENTS_CHOICE_PVS)))
         .withViewField(new QFieldMetaData("help", QFieldType.STRING).withLabel(" ").withDefaultValue(
            StopIndexingSteps.MODE_REMOVE.equals(mode)
               ? "Removing stops indexing the table and deletes its index and field rows. Code defaults never add it back (see Quick Search Removed Config)."
               : "Disabling stops indexing the table and hides it from search; enabling it again rebuilds it.")));
      return (process);
   }



   /*******************************************************************************
    ** Apply Search Index Changes: preview field changes, then rebuild.
    *******************************************************************************/
   static QProcessMetaData buildApplyChangesProcess(QuickSearchQBitConfig config)
   {
      String indexTable = config.applyTablePrefix(QuickSearchIndex.TABLE_NAME);
      return (base(config, APPLY_CHANGES_PROCESS_NAME, "Apply Search Index Changes", "published_with_changes", indexTable,
         ExtractViaQueryStep.class, ApplyIndexChangesSteps.Transform.class, ApplyIndexChangesSteps.Load.class, 1, indexReviewFields(config)));
   }



   /*******************************************************************************
    ** Cancel Index Run: the cooperative cancel flag.
    *******************************************************************************/
   static QProcessMetaData buildCancelRunProcess(QuickSearchQBitConfig config)
   {
      String runTable = config.applyTablePrefix(QuickSearchIndexRun.TABLE_NAME);
      return (base(config, CANCEL_RUN_PROCESS_NAME, "Cancel Index Run", "cancel", runTable,
         ExtractViaQueryStep.class, CancelRunSteps.Transform.class, CancelRunSteps.Load.class, 1, List.of(
            new QFieldMetaData("id", QFieldType.INTEGER),
            new QFieldMetaData("quickSearchIndexId", QFieldType.INTEGER).withLabel("Search Index").withPossibleValueSourceName(config.applyTablePrefix(QuickSearchIndex.TABLE_NAME)),
            new QFieldMetaData("runType", QFieldType.STRING),
            new QFieldMetaData("status", QFieldType.STRING),
            new QFieldMetaData("recordsProcessed", QFieldType.INTEGER))));
   }



   /*******************************************************************************
    ** Pause or Resume Indexing.
    *******************************************************************************/
   static QProcessMetaData buildSetPausedProcess(QuickSearchQBitConfig config, String name, String label, String icon, boolean pause)
   {
      String settingsTable = config.applyTablePrefix(QuickSearchSettings.TABLE_NAME);
      QProcessMetaData process = StreamedETLWithFrontendProcess.processMetaDataBuilder()
         .withName(config.applyPrefix(name))
         .withLabel(label)
         .withIcon(new QIcon(icon))
         .withTableName(settingsTable)
         .withSourceTable(settingsTable)
         .withDestinationTable(settingsTable)
         .withExtractStepClass(ExtractViaQueryStep.class)
         .withTransformStepClass(SetIndexingPausedSteps.Transform.class)
         .withLoadStepClass(SetIndexingPausedSteps.Load.class)
         .withSupportsFullValidation(true)
         .withDoFullValidation(true)
         .withTransactionLevelAutoCommit()
         .withMinInputRecords(1)
         .withFields(List.of(new QFieldMetaData(SetIndexingPausedSteps.FIELD_PAUSE, QFieldType.BOOLEAN).withDefaultValue(pause)))
         .withReviewStepRecordFields(List.of(
            new QFieldMetaData("indexingPaused", QFieldType.BOOLEAN).withLabel("Indexing Paused"),
            new QFieldMetaData("pausedAt", QFieldType.DATE_TIME).withLabel("Paused Since"),
            new QFieldMetaData("pausedBy", QFieldType.STRING).withLabel("Paused By")))
         .getProcessMetaData();
      process.withPermissionRules(config.getAdminPermissionRules() == null ? null : config.getAdminPermissionRules().clone());

      if(pause)
      {
         process.withStep(0, new QFrontendStepMetaData()
            .withName("input")
            .withLabel("Pause indexing")
            .withFormField(new QFieldMetaData(SetIndexingPausedSteps.FIELD_REASON, QFieldType.STRING).withLabel("Reason (optional)")));
      }
      return (process);
   }



   /*******************************************************************************
    ** The index fields shown on the review step.
    *******************************************************************************/
   private static List<QFieldMetaData> indexReviewFields(QuickSearchQBitConfig config)
   {
      return (List.of(
         new QFieldMetaData("id", QFieldType.INTEGER),
         new QFieldMetaData("tableName", QFieldType.STRING).withLabel("Table")
            .withPossibleValueSourceName(config.applyTablePrefix(QuickSearchSearchableTablePossibleValueSourceProducer.NAME)),
         new QFieldMetaData("enabled", QFieldType.BOOLEAN),
         new QFieldMetaData("status", QFieldType.STRING),
         new QFieldMetaData("documentCount", QFieldType.INTEGER)));
   }



   /*******************************************************************************
    ** A streamed-ETL process with a validated review step, gated by the admin
    ** permission.
    *******************************************************************************/
   private static QProcessMetaData base(QuickSearchQBitConfig config, String name, String label, String icon, String tableName, Class<? extends AbstractExtractStep> extract,
      Class<? extends AbstractTransformStep> transform, Class<? extends AbstractLoadStep> load, Integer minInputRecords, List<QFieldMetaData> reviewFields)
   {
      QProcessMetaData process = StreamedETLWithFrontendProcess.processMetaDataBuilder()
         .withName(config.applyPrefix(name))
         .withLabel(label)
         .withIcon(new QIcon(icon))
         .withTableName(tableName)
         .withSourceTable(tableName)
         .withDestinationTable(tableName)
         .withExtractStepClass(extract)
         .withTransformStepClass(transform)
         .withLoadStepClass(load)
         .withSupportsFullValidation(true)
         .withDoFullValidation(true)
         .withTransactionLevelAutoCommit()
         .withMinInputRecords(minInputRecords)
         .withReviewStepRecordFields(reviewFields)
         .getProcessMetaData();
      process.withPermissionRules(config.getAdminPermissionRules() == null ? null : config.getAdminPermissionRules().clone());
      return (process);
   }

}

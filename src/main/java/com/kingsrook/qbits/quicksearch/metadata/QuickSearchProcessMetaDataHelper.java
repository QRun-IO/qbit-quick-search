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


import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QBackendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QComponentType;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFrontendComponentMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFrontendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.scheduleing.QScheduleMetaData;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.processes.BasepullIndexStep;
import com.kingsrook.qbits.quicksearch.processes.FullReindexStep;
import com.kingsrook.qbits.quicksearch.processes.ReconcileIndexStep;


/*******************************************************************************
 ** Builds the three process definitions, with permission rules from config and
 ** schedules when a scheduler is configured.
 *******************************************************************************/
public class QuickSearchProcessMetaDataHelper
{
   public static final String BASEPULL_PROCESS_NAME     = "quickSearchBasepullIndex";
   public static final String FULL_REINDEX_PROCESS_NAME = "quickSearchFullReindex";
   public static final String RECONCILE_PROCESS_NAME    = "quickSearchReconcileIndex";



   /*******************************************************************************
    ** Basepull catch-up; scheduled every basepullRepeatSeconds when a
    ** schedulerName is set.
    *******************************************************************************/
   public static QProcessMetaData buildBasepullProcess(QuickSearchQBitConfig config)
   {
      QProcessMetaData process = buildProcess(config, BASEPULL_PROCESS_NAME, "Quick Search Basepull Index", "basepull", BasepullIndexStep.class, "update");
      process.withStep(resultStep("Basepull complete", "tablesIndexed", "tablesBuilt", "failedEventsReplayed", "paused"));

      if(StringUtils.hasContent(config.getSchedulerName()))
      {
         process.withSchedule(new QScheduleMetaData()
            .withSchedulerName(config.getSchedulerName())
            .withRepeatSeconds(config.getBasepullRepeatSeconds() == null ? 300 : config.getBasepullRepeatSeconds())
            .withDescription("Quick Search basepull catch-up"));
      }

      return (process);
   }



   /*******************************************************************************
    ** Full reindex; on demand only.
    *******************************************************************************/
   public static QProcessMetaData buildFullReindexProcess(QuickSearchQBitConfig config)
   {
      QProcessMetaData process = buildProcess(config, FULL_REINDEX_PROCESS_NAME, "Quick Search Full Reindex", "fullReindex", FullReindexStep.class, "refresh");
      process.withStep(0, inputStep(config, "Leave the table blank to rebuild every table into a fresh index and swap the alias; pick a table to rebuild just that table in place."));
      process.withStep(resultStep("Full reindex complete", "recordsIndexed", "aliasSwapped", "message"));
      return (process);
   }



   /*******************************************************************************
    ** Reconcile; scheduled by cron when reconcileCronExpression and a
    ** schedulerName are set.
    *******************************************************************************/
   public static QProcessMetaData buildReconcileProcess(QuickSearchQBitConfig config)
   {
      QProcessMetaData process = buildProcess(config, RECONCILE_PROCESS_NAME, "Quick Search Reconcile Index", "reconcile", ReconcileIndexStep.class, "sync");
      process.withStep(0, inputStep(config, "Leave the table blank to reconcile every table and remove documents of unconfigured tables; pick a table to reconcile just that one."));
      process.withStep(resultStep("Reconcile complete", "recordsIndexed", "documentsRemoved", "orphanDocumentsRemoved", "tablesSkipped", "message"));

      if(StringUtils.hasContent(config.getSchedulerName()) && StringUtils.hasContent(config.getReconcileCronExpression()))
      {
         process.withSchedule(new QScheduleMetaData()
            .withSchedulerName(config.getSchedulerName())
            .withCronExpression(config.getReconcileCronExpression())
            .withCronTimeZoneId(config.getReconcileCronTimeZoneId())
            .withDescription("Quick Search reconcile"));
      }

      return (process);
   }



   /*******************************************************************************
    ** Optional table picker shown before the backend step. Its field is
    ** searchTableName, not tableName, which a process run from a table gets
    ** from the frontend (the table it runs from).
    *******************************************************************************/
   private static QFrontendStepMetaData inputStep(QuickSearchQBitConfig config, String help)
   {
      return (new QFrontendStepMetaData()
         .withName("input")
         .withComponent(new QFrontendComponentMetaData().withType(QComponentType.HELP_TEXT).withValue("text", help))
         .withComponent(new QFrontendComponentMetaData().withType(QComponentType.EDIT_FORM))
         .withLabel("Choose table")
         .withFormField(new QFieldMetaData("searchTableName", QFieldType.STRING)
            .withLabel("Table (optional)")
            .withPossibleValueSourceName(config.applyTablePrefix(QuickSearchSearchableTablePossibleValueSourceProducer.NAME))
            .withIsRequired(false)));
   }



   /*******************************************************************************
    ** Summary screen showing the step's output values.
    *******************************************************************************/
   private static QFrontendStepMetaData resultStep(String label, String... outputFields)
   {
      QFrontendStepMetaData step = new QFrontendStepMetaData().withName("result").withLabel(label);
      for(String field : outputFields)
      {
         step.withViewField(new QFieldMetaData(field, QFieldType.STRING));
      }
      return (step);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static QProcessMetaData buildProcess(QuickSearchQBitConfig config, String baseName, String label, String stepName, Class<?> stepClass, String icon)
   {
      ///////////////////////////////////////////////////////////////////////
      // run from Quick Search Index's Actions (processes are never in the //
      // nav); the input screen's table picker is searchTableName          //
      ///////////////////////////////////////////////////////////////////////
      return (new QProcessMetaData()
         .withName(config.applyPrefix(baseName))
         .withLabel(label)
         .withIcon(new QIcon(icon))
         .withTableName(config.getQuickSearchIndexTableName())
         .withPermissionRules(config.getAdminPermissionRules() == null ? null : config.getAdminPermissionRules().clone())
         .withStep(new QBackendStepMetaData()
            .withName(stepName)
            .withCode(new QCodeReference(stepClass))));
   }

}

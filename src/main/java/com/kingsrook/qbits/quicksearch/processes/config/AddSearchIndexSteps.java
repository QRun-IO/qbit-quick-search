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

package com.kingsrook.qbits.quicksearch.processes.config;


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.Status;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractExtractStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractLoadStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractTransformStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.ProcessSummaryProviderInterface;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.config.QuickSearchConfigPrimer;
import com.kingsrook.qbits.quicksearch.config.QuickSearchFieldRules;
import com.kingsrook.qbits.quicksearch.config.QuickSearchTombstones;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexField;
import com.kingsrook.qbits.quicksearch.processes.AbstractIndexingStep;


/*******************************************************************************
 ** The steps of Add Search Index, the valid "create" for quick_search_index:
 ** the admin picks an eligible table (one they can read, not yet indexed,
 ** not internal or secret), the update interval, the timestamp field and the
 ** enabled flag; the review step shows the estimated record count, the
 ** fields the index starts with (record-label fields as titles, then the
 ** text fields) and what is left out and why; saving inserts the index row
 ** and its field rows and queues the first build. Fields are adjusted after
 ** on the index record's Searchable Fields.
 *******************************************************************************/
public final class AddSearchIndexSteps
{
   ////////////////////////////////////////////////////////////////////////
   // the input is not "tableName": a process on a table gets that value  //
   // (the table it runs from) from the frontend                          //
   ////////////////////////////////////////////////////////////////////////
   public static final String FIELD_TABLE_NAME = "searchTableName";
   public static final String RECORD_TABLE     = "tableName";
   public static final String FIELD_INTERVAL   = "basepullIntervalMinutes";
   public static final String FIELD_TIMESTAMP  = "basepullTimestampField";
   public static final String FIELD_ENABLED    = "enabled";



   /*******************************************************************************
    ** Utility holder - no instances.
    *******************************************************************************/
   private AddSearchIndexSteps()
   {
   }



   /*******************************************************************************
    ** Extract: the one record the input step describes.
    *******************************************************************************/
   public static class Extract extends AbstractExtractStep
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         getRecordPipe().addRecord(new QRecord()
            .withValue(RECORD_TABLE, input.getValueString(FIELD_TABLE_NAME))
            .withValue(FIELD_INTERVAL, input.getValueInteger(FIELD_INTERVAL))
            .withValue(FIELD_TIMESTAMP, input.getValueString(FIELD_TIMESTAMP))
            .withValue(FIELD_ENABLED, input.getValue(FIELD_ENABLED) == null || input.getValueBoolean(FIELD_ENABLED)));
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public Integer doCount(RunBackendStepInput input)
      {
         return (1);
      }
   }



   /*******************************************************************************
    ** Transform: validate and describe the new index.
    *******************************************************************************/
   public static class Transform extends AbstractTransformStep
   {
      private final List<ProcessSummaryLine> summary = new ArrayList<>();



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         QuickSearchQBitConfig config = ConfigProcessSupport.config();
         for(QRecord record : input.getRecords())
         {
            String tableName = record.getValueString(RECORD_TABLE);
            String problem   = validate(config, record);
            if(problem != null)
            {
               summary.add(ConfigProcessSupport.line(Status.ERROR, problem));
               continue;
            }

            QTableMetaData                          table    = QContext.getQInstance().getTable(tableName);
            List<QuickSearchFieldRules.DefaultField> defaults = QuickSearchFieldRules.defaultFields(table);
            List<String>                            bullets  = new ArrayList<>();
            for(QuickSearchFieldRules.DefaultField field : defaults)
            {
               bullets.add(ConfigProcessSupport.fieldLabel(tableName, field.fieldName()) + ": " + field.role().getPossibleValueLabel() + ", weight " + field.weight());
            }

            Integer estimated = ConfigProcessSupport.countRecords(tableName);
            summary.add(ConfigProcessSupport.line(Status.OK, ConfigProcessSupport.tableLabel(tableName) + " will be added to Quick Search; its first build indexes "
               + ConfigProcessSupport.aboutRecords(estimated) + "."));
            summary.add(ConfigProcessSupport.line(Status.INFO, "It starts with " + defaults.size() + (defaults.size() == 1 ? " field" : " fields")
               + " (change them afterwards under Searchable Fields on the index):", bullets));

            List<String> leftOut = new ArrayList<>();
            for(QFieldMetaData field : table.getFields().values())
            {
               String why = QuickSearchFieldRules.whyNotIndexable(table, field.getName());
               if(why != null && (why.contains("sensitive") || why.contains("hidden")))
               {
                  leftOut.add(why);
               }
            }
            if(!leftOut.isEmpty())
            {
               summary.add(ConfigProcessSupport.line(Status.WARNING, "Never indexed:", leftOut));
            }

            String timestamp = record.getValueString(FIELD_TIMESTAMP);
            summary.add(ConfigProcessSupport.line(Status.INFO, StringUtils.hasContent(timestamp)
               ? "Changes are picked up every " + record.getValueInteger(FIELD_INTERVAL) + " minutes by " + ConfigProcessSupport.fieldLabel(tableName, timestamp) + ", and at once by real-time indexing."
               : "The table has no timestamp field: real-time indexing and the nightly reconcile keep it current."));
            if(Boolean.FALSE.equals(record.getValueBoolean(FIELD_ENABLED)))
            {
               summary.add(ConfigProcessSupport.line(Status.WARNING, "It is added disabled: nothing is indexed until it is enabled."));
            }
            if(QuickSearchTombstones.exists(tableName, null))
            {
               summary.add(ConfigProcessSupport.line(Status.INFO, "It was removed from Quick Search before; adding it back clears that."));
            }

            record.setValue("estimatedRecords", estimated);
            record.setValue("fieldCount", defaults.size());
            output.addRecord(record);
         }
      }



      /*******************************************************************************
       ** Fill defaults and return why the record cannot be added, or null.
       *******************************************************************************/
      static String validate(QuickSearchQBitConfig config, QRecord record) throws QException
      {
         String tableName = record.getValueString(RECORD_TABLE);
         if(!StringUtils.hasContent(tableName))
         {
            return ("Choose a table to add.");
         }
         String problem = QuickSearchFieldRules.whyNotEligible(QContext.getQInstance(), config, tableName);
         if(problem != null)
         {
            return ("Cannot add " + tableName + ": " + problem + ".");
         }
         if(!QuickSearchFieldRules.canRead(tableName))
         {
            return ("Cannot add " + tableName + ": you do not have permission to read it.");
         }
         for(QRecord row : QuickSearchConfigPrimer.queryAll(config.getQuickSearchIndexTableName()))
         {
            if(tableName.equals(row.getValueString("tableName")))
            {
               return (ConfigProcessSupport.tableLabel(tableName) + " is already in Quick Search.");
            }
         }

         QTableMetaData table = QContext.getQInstance().getTable(tableName);
         if(QuickSearchFieldRules.defaultFields(table).isEmpty())
         {
            return ("Cannot add " + ConfigProcessSupport.tableLabel(tableName) + ": it has no text field to start the index with.");
         }

         String timestamp = record.getValueString(FIELD_TIMESTAMP);
         if(!StringUtils.hasContent(timestamp))
         {
            record.setValue(FIELD_TIMESTAMP, QuickSearchFieldRules.defaultTimestampField(table));
         }
         else if(!QuickSearchFieldRules.isTimestampField(table, timestamp))
         {
            return ("Timestamp field [" + timestamp + "] is not a date-time field of " + ConfigProcessSupport.tableLabel(tableName) + ".");
         }

         Integer interval = record.getValueInteger(FIELD_INTERVAL);
         if(interval == null)
         {
            record.setValue(FIELD_INTERVAL, config.getDefaultBasepullIntervalMinutes());
         }
         else if(interval < 0)
         {
            return ("The update interval cannot be negative (0 means every update).");
         }
         return (null);
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public ArrayList<ProcessSummaryLineInterface> getProcessSummary(RunBackendStepOutput output, boolean isForResultScreen)
      {
         return (ConfigProcessSupport.lines(summary));
      }
   }



   /*******************************************************************************
    ** Load: insert the index row and its fields, then queue (or run) the build.
    *******************************************************************************/
   public static class Load extends AbstractLoadStep implements ProcessSummaryProviderInterface
   {
      private final List<ProcessSummaryLine> summary = new ArrayList<>();



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         QuickSearchQBitConfig config = ConfigProcessSupport.config();
         for(QRecord record : input.getRecords())
         {
            String  tableName = record.getValueString(RECORD_TABLE);
            boolean enabled   = !Boolean.FALSE.equals(record.getValueBoolean(FIELD_ENABLED));
            QRecord inserted  = QuickSearchConfigPrimer.insertOne(config.getQuickSearchIndexTableName(), new QRecord()
               .withValue("tableName", tableName)
               .withValue("enabled", enabled)
               .withValue("basepullIntervalMinutes", record.getValueInteger(FIELD_INTERVAL))
               .withValue("basepullTimestampField", record.getValueString(FIELD_TIMESTAMP))
               .withValue("source", QuickSearchIndex.SOURCE_OWNER)
               .withValue("ownerEdited", true)
               .withValue("realTimeErrorCount", 0)
               .withValue("status", enabled ? AbstractIndexingStep.STATUS_PENDING_BUILD : AbstractIndexingStep.STATUS_ACTIVE));
            Integer indexId = inserted.getValueInteger("id");

            List<QRecord> fields = new ArrayList<>();
            int           order  = 10;
            for(QuickSearchFieldRules.DefaultField field : QuickSearchFieldRules.defaultFields(QContext.getQInstance().getTable(tableName)))
            {
               fields.add(new QRecord()
                  .withValue("quickSearchIndexId", indexId)
                  .withValue("fieldName", field.fieldName())
                  .withValue("weight", field.weight())
                  .withValue("role", field.role().name())
                  .withValue("sortOrder", order));
               order += 10;
            }
            QuickSearchConfigPrimer.insertAll(config.applyTablePrefix(QuickSearchIndexField.TABLE_NAME), fields);
            QuickSearchTombstones.remove(tableName, null);

            String build = !enabled
               ? "it is disabled, so nothing is indexed until it is enabled"
               : ConfigProcessSupport.queueOrBuild(indexId);
            summary.add(ConfigProcessSupport.line(Status.OK, "Added " + ConfigProcessSupport.tableLabel(tableName) + " to Quick Search with " + fields.size()
               + (fields.size() == 1 ? " field" : " fields") + "; " + build + "."));
            output.addRecord(inserted);
         }
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public ArrayList<ProcessSummaryLineInterface> getProcessSummary(RunBackendStepOutput output, boolean isForResultScreen)
      {
         return (ConfigProcessSupport.lines(CollectionUtils.nonNullList(summary)));
      }
   }

}

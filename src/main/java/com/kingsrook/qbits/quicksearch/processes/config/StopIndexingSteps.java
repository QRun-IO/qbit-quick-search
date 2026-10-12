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


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.Status;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractLoadStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractTransformStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.ProcessSummaryProviderInterface;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.config.QuickSearchConfigPrimer;
import com.kingsrook.qbits.quicksearch.config.QuickSearchConfigWrites;
import com.kingsrook.qbits.quicksearch.config.QuickSearchTombstones;
import com.kingsrook.qbits.quicksearch.model.QuickSearchDocumentsChoice;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexField;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** The steps of Disable Search Index and Remove Search Index (record actions
 ** on quick_search_index). Both stop indexing the selected tables at once;
 ** the input step's "documents" choice, restated on the review step, says
 ** whether their search documents are deleted now or kept.
 **
 ** Disable keeps the row (enabled off, marked ownerEdited); enabling it again
 ** queues a build. Remove deletes the row and its field rows and pending
 ** failed events, and leaves a tombstone so code defaults never add the table
 ** back (delete the tombstone to allow that).
 *******************************************************************************/
public final class StopIndexingSteps
{
   private static final QLogger LOG = QLogger.getLogger(StopIndexingSteps.class);

   public static final String FIELD_MODE      = "mode";
   public static final String FIELD_DOCUMENTS = "documents";
   public static final String MODE_DISABLE    = "DISABLE";
   public static final String MODE_REMOVE     = "REMOVE";



   /*******************************************************************************
    ** Utility holder - no instances.
    *******************************************************************************/
   private StopIndexingSteps()
   {
   }



   /*******************************************************************************
    ** The documents choice from the input step (DELETE when not given).
    *******************************************************************************/
   static QuickSearchDocumentsChoice documentsChoice(RunBackendStepInput input)
   {
      return (QuickSearchDocumentsChoice.fromName(input.getValueString(FIELD_DOCUMENTS), QuickSearchDocumentsChoice.DELETE));
   }



   /*******************************************************************************
    ** Whether this run removes (else disables).
    *******************************************************************************/
   static boolean isRemove(RunBackendStepInput input)
   {
      return (MODE_REMOVE.equals(input.getValueString(FIELD_MODE)));
   }



   /*******************************************************************************
    ** Transform: say what will happen to each selected index.
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
         boolean                    remove    = isRemove(input);
         QuickSearchDocumentsChoice documents = documentsChoice(input);
         for(QRecord record : input.getRecords())
         {
            String tableName = record.getValueString("tableName");
            String label     = ConfigProcessSupport.tableLabel(tableName);
            if(!remove && Boolean.FALSE.equals(record.getValueBoolean("enabled")))
            {
               summary.add(ConfigProcessSupport.line(Status.INFO, label + " is already disabled; it is skipped."));
               continue;
            }

            Long   count = ConfigProcessSupport.countDocuments(tableName);
            String docs  = count == null ? "its search documents" : "its " + String.format("%,d", count) + (count == 1 ? " search document" : " search documents");
            summary.add(ConfigProcessSupport.line(Status.OK, label + (remove ? " will be removed from Quick Search" : " will be disabled") + " and stop being indexed now; "
               + docs + (documents == QuickSearchDocumentsChoice.DELETE ? " will be deleted from the search index." : " will be kept"
               + (remove ? " until the next all-tables reconcile sweeps them." : " (hidden from search) and rebuilt when it is enabled again."))));
            if(remove && QuickSearchIndex.SOURCE_CODE.equals(record.getValueString("source")))
            {
               summary.add(ConfigProcessSupport.line(Status.INFO, label + " is declared searchable in code; Quick Search remembers the removal (Quick Search Removed Config) so code does not add it back."));
            }
            output.addRecord(record);
         }
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
    ** Load: disable or remove, and delete or keep the documents.
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
         QuickSearchQBitConfig      config    = ConfigProcessSupport.config();
         boolean                    remove    = isRemove(input);
         QuickSearchDocumentsChoice documents = documentsChoice(input);
         for(QRecord record : input.getRecords())
         {
            Integer id        = record.getValueInteger("id");
            String  tableName = record.getValueString("tableName");
            String  label     = ConfigProcessSupport.tableLabel(tableName);

            if(remove)
            {
               removeIndex(config, id, tableName);
            }
            else
            {
               QuickSearchConfigPrimer.updateAll(config.getQuickSearchIndexTableName(), List.of(new QRecord().withValue("id", id).withValue("enabled", false)));
            }

            String outcome = "kept";
            if(documents == QuickSearchDocumentsChoice.DELETE)
            {
               outcome = deleteDocuments(tableName, remove ? null : id);
            }
            summary.add(ConfigProcessSupport.line(Status.OK, label + (remove ? " was removed from Quick Search" : " was disabled") + "; its search documents were " + outcome + "."));
            output.addRecord(record);
         }

         QuickSearchRuntime runtime = QuickSearchRuntime.get();
         if(runtime != null)
         {
            runtime.invalidateConfig();
         }
      }



      /*******************************************************************************
       ** Delete the index row (its field rows first) and the table's pending
       ** failed events, and leave a tombstone.
       *******************************************************************************/
      private static void removeIndex(QuickSearchQBitConfig config, Integer id, String tableName) throws QException
      {
         QuickSearchTombstones.add(tableName, null, "removed by " + QuickSearchConfigWrites.currentUserName());
         QuickSearchConfigWrites.asSystem(() ->
         {
            deleteWhere(config.applyTablePrefix(QuickSearchIndexField.TABLE_NAME), new QFilterCriteria("quickSearchIndexId", QCriteriaOperator.EQUALS, id));
            if(QContext.getQInstance().getTable(config.applyTablePrefix(QuickSearchFailedEvent.TABLE_NAME)) != null)
            {
               deleteWhere(config.applyTablePrefix(QuickSearchFailedEvent.TABLE_NAME), new QFilterCriteria("tableName", QCriteriaOperator.EQUALS, tableName));
            }
            DeleteInput deleteInput = new DeleteInput();
            deleteInput.setTableName(config.getQuickSearchIndexTableName());
            deleteInput.setPrimaryKeys(List.of(id));
            return (new DeleteAction().execute(deleteInput));
         });
      }



      /*******************************************************************************
       ** Delete the rows of a table matching a criteria.
       *******************************************************************************/
      private static void deleteWhere(String tableName, QFilterCriteria criteria) throws QException
      {
         QueryInput queryInput = new QueryInput();
         queryInput.setTableName(tableName);
         queryInput.setFilter(new QQueryFilter(criteria));
         List<Serializable> ids = new ArrayList<>();
         CollectionUtils.nonNullList(new QueryAction().execute(queryInput).getRecords()).forEach(r -> ids.add(r.getValue("id")));
         if(!ids.isEmpty())
         {
            DeleteInput deleteInput = new DeleteInput();
            deleteInput.setTableName(tableName);
            deleteInput.setPrimaryKeys(ids);
            new DeleteAction().execute(deleteInput);
         }
      }



      /*******************************************************************************
       ** Delete a table's documents; returns what happened. A failure is
       ** reported, not thrown: indexing has already stopped, and the next
       ** reconcile removes the documents of removed tables.
       *******************************************************************************/
      private static String deleteDocuments(String tableName, Integer keptIndexId)
      {
         try
         {
            QuickSearchRuntime runtime = QuickSearchRuntime.get();
            Long               deleted = runtime == null ? 0L : runtime.getClient().deleteDocumentsForTable(tableName);
            if(keptIndexId != null)
            {
               ConfigProcessSupport.updateIndexRowAsSystem(keptIndexId, Map.of("documentCount", 0));
            }
            return ("deleted (" + String.format("%,d", deleted == null ? 0L : deleted) + ")");
         }
         catch(Exception e)
         {
            LOG.warn("Could not delete a table's search documents", e, logPair("tableName", tableName));
            return ("not deleted (" + e.getMessage() + "; the next reconcile removes them)");
         }
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

}

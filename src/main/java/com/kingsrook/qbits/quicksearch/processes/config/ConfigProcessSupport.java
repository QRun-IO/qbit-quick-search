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
import com.kingsrook.qqq.backend.core.actions.tables.CountAction;
import com.kingsrook.qqq.backend.core.actions.tables.GetAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.Status;
import com.kingsrook.qqq.backend.core.model.actions.tables.count.CountInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.get.GetInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitContext;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.config.QuickSearchConfigPrimer;
import com.kingsrook.qbits.quicksearch.config.QuickSearchConfigWrites;
import com.kingsrook.qbits.quicksearch.config.QuickSearchFieldRules;
import com.kingsrook.qbits.quicksearch.processes.AbstractIndexingStep;
import com.kingsrook.qbits.quicksearch.processes.BasepullIndexStep;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Shared pieces of the config processes (Add, Disable and Remove Search
 ** Index, Apply Search Index Changes, Cancel Index Run, Pause and Resume
 ** Indexing): the QBit's config and runtime, record counts for the review
 ** step, summary lines, and queueing (or, with no scheduler, running) a build.
 *******************************************************************************/
public final class ConfigProcessSupport
{
   private static final QLogger LOG = QLogger.getLogger(ConfigProcessSupport.class);



   /*******************************************************************************
    ** Utility class - no instances.
    *******************************************************************************/
   private ConfigProcessSupport()
   {
   }



   /*******************************************************************************
    ** The QBit's config (runtime first, then the static facade).
    *******************************************************************************/
   static QuickSearchQBitConfig config() throws QException
   {
      QuickSearchRuntime    runtime = QuickSearchRuntime.get();
      QuickSearchQBitConfig config  = runtime != null ? runtime.getConfig() : QuickSearchQBitContext.getConfig();
      if(config == null)
      {
         throw (new QException("Quick Search is not configured in this application"));
      }
      return (config);
   }



   /*******************************************************************************
    ** The number of records in a table, or null when it cannot be counted.
    *******************************************************************************/
   static Integer countRecords(String tableName)
   {
      try
      {
         return (new CountAction().execute(new CountInput(tableName)).getCount());
      }
      catch(Exception e)
      {
         LOG.debug("Could not count records for the review step", e, logPair("tableName", tableName));
         return (null);
      }
   }



   /*******************************************************************************
    ** The number of search documents of a table, or null when OpenSearch
    ** cannot be asked.
    *******************************************************************************/
   static Long countDocuments(String tableName)
   {
      try
      {
         QuickSearchRuntime runtime = QuickSearchRuntime.get();
         return (runtime == null ? null : runtime.getClient().countDocumentsForTable(tableName));
      }
      catch(Exception e)
      {
         LOG.debug("Could not count search documents for the review step", e, logPair("tableName", tableName));
         return (null);
      }
   }



   /*******************************************************************************
    ** A table's label (or name).
    *******************************************************************************/
   static String tableLabel(String tableName)
   {
      QTableMetaData table = tableName == null || QContext.getQInstance() == null ? null : QContext.getQInstance().getTable(tableName);
      return (table == null ? String.valueOf(tableName) : QuickSearchFieldRules.label(table));
   }



   /*******************************************************************************
    ** A field's label and name: "Title (title)".
    *******************************************************************************/
   static String fieldLabel(String tableName, String fieldName)
   {
      QTableMetaData table = tableName == null || QContext.getQInstance() == null ? null : QContext.getQInstance().getTable(tableName);
      if(table != null && table.getFields().containsKey(fieldName) && StringUtils.hasContent(table.getField(fieldName).getLabel()))
      {
         return (table.getField(fieldName).getLabel() + " (" + fieldName + ")");
      }
      return (fieldName);
   }



   /*******************************************************************************
    ** "about 1,234 records" (or "an unknown number of records").
    *******************************************************************************/
   static String aboutRecords(Integer count)
   {
      return (count == null ? "an unknown number of records" : "about " + String.format("%,d", count) + (count == 1 ? " record" : " records"));
   }



   /*******************************************************************************
    ** A summary line with a message only.
    *******************************************************************************/
   static ProcessSummaryLine line(Status status, String message)
   {
      return (new ProcessSummaryLine(status, null, message));
   }



   /*******************************************************************************
    ** A summary line with bullets of text.
    *******************************************************************************/
   static ProcessSummaryLine line(Status status, String message, List<String> bullets)
   {
      return (new ProcessSummaryLine(status, null, message).withBulletsOfText(new ArrayList<>(bullets)));
   }



   /*******************************************************************************
    ** Copy lines into a fresh list (the summary providers' return type).
    *******************************************************************************/
   static ArrayList<ProcessSummaryLineInterface> lines(List<? extends ProcessSummaryLineInterface> lines)
   {
      return (new ArrayList<>(lines));
   }



   /*******************************************************************************
    ** The quick_search_index row, or null.
    *******************************************************************************/
   static QRecord indexRow(Integer id) throws QException
   {
      GetInput getInput = new GetInput(config().getQuickSearchIndexTableName());
      getInput.setPrimaryKey(id);
      return (new GetAction().execute(getInput).getRecord());
   }



   /*******************************************************************************
    ** Update an index row as a system write.
    *******************************************************************************/
   static void updateIndexRowAsSystem(Integer id, Map<String, ? extends Serializable> values) throws QException
   {
      QRecord record = new QRecord().withValue("id", id);
      values.forEach(record::setValue);
      QuickSearchConfigWrites.asSystem(() ->
      {
         QuickSearchConfigPrimer.updateAll(config().getQuickSearchIndexTableName(), List.of(record));
         return (null);
      });
   }



   /*******************************************************************************
    ** Queue a table's build (PENDING_BUILD, run by the next scheduled basepull),
    ** or, when no scheduler runs the basepull, build it now. Returns what
    ** happened, for the result screen.
    *******************************************************************************/
   static String queueOrBuild(Integer indexId) throws QException
   {
      QuickSearchQBitConfig config = config();
      updateIndexRowAsSystem(indexId, Map.of("status", AbstractIndexingStep.STATUS_PENDING_BUILD));

      QuickSearchRuntime runtime = QuickSearchRuntime.get();
      if(runtime != null)
      {
         runtime.invalidateConfig();
      }

      if(StringUtils.hasContent(config.getSchedulerName()) && Boolean.TRUE.equals(config.getEnableBasepullProcess()))
      {
         Integer seconds = config.getBasepullRepeatSeconds() == null ? 300 : config.getBasepullRepeatSeconds();
         return ("its build is queued and starts with the next update (within " + seconds + " seconds)");
      }

      if(runtime != null && runtime.isIndexingPaused())
      {
         return ("its build is queued and starts when indexing is resumed");
      }

      QRecord row = indexRow(indexId);
      try
      {
         boolean built = new BasepullIndexStep().buildTable(row, runtime);
         QRecord after = indexRow(indexId);
         return (built ? "it was built now (" + aboutRecords(after == null ? null : after.getValueInteger("recordCount")) + " indexed)" : "its build is queued");
      }
      catch(Exception e)
      {
         return ("its build failed and is queued to retry: " + e.getMessage());
      }
   }

}

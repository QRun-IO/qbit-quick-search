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

package com.kingsrook.qbits.quicksearch.processes;


import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Rebuild the index from source.
 **
 ** Without a "tableName" input, every enabled table is indexed into a fresh
 ** physical index with the current mapping; when all of them succeed the alias
 ** is swapped to it and the old index is deleted, so search keeps serving the
 ** old documents until the new set is complete. This is also how an index
 ** created by an older release (outdated mapping) is upgraded. On failure the
 ** new physical index is deleted and nothing changes.
 **
 ** With a "tableName" input, that table is rebuilt in place with the reconcile
 ** algorithm (index everything, then delete what was not re-indexed).
 *******************************************************************************/
public class FullReindexStep extends AbstractIndexingStep
{
   private static final QLogger LOG = QLogger.getLogger(FullReindexStep.class);

   public static final String RUN_TYPE = "FULL_REINDEX";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
   {
      String tableNameFilter = input.getValueString("tableName");

      List<QuickSearchableTableConfig> tables = new ArrayList<>();
      for(QuickSearchableTableConfig tableConfig : getDiscoveredTables())
      {
         if(tableNameFilter != null && !tableNameFilter.isBlank() && !tableNameFilter.equals(tableConfig.getTableName()))
         {
            continue;
         }
         ensureIndexRowExists(tableConfig.getTableName(), tableConfig);
         if(isRowEnabled(queryIndexRow(tableConfig.getTableName())))
         {
            tables.add(tableConfig);
         }
         else
         {
            LOG.info("Table is disabled; skipping full reindex", logPair("tableName", tableConfig.getTableName()));
         }
      }

      if(tableNameFilter != null && !tableNameFilter.isBlank())
      {
         Integer recordsIndexed = 0;
         for(QuickSearchableTableConfig tableConfig : tables)
         {
            recordsIndexed = recordsIndexed + new ReconcileIndexStep().reconcileTable(tableConfig, RUN_TYPE).recordsIndexed();
         }
         output.addValue("recordsIndexed", recordsIndexed);
         output.addValue("aliasSwapped", false);
         return;
      }

      output.addValue("recordsIndexed", rebuildAllTables(tables));
      output.addValue("aliasSwapped", true);
   }



   /*******************************************************************************
    ** Index every table into a new physical index, then swap the alias.
    *******************************************************************************/
   Integer rebuildAllTables(List<QuickSearchableTableConfig> tables) throws QException
   {
      QuickSearchOpenSearchClient client        = getClient();
      String                      physicalIndex = client.newPhysicalIndexName();

      client.createPhysicalIndex(physicalIndex);
      LOG.info("Rebuilding Quick Search index", logPair("physicalIndex", physicalIndex), logPair("tables", tables.size()));

      Map<QuickSearchableTableConfig, QuickSearchIndexRun> runs       = new LinkedHashMap<>();
      Map<String, IndexCounts>                              counts     = new LinkedHashMap<>();
      Map<String, Instant>                                  startTimes = new LinkedHashMap<>();
      Integer                                               indexed    = 0;

      boolean swapped = false;

      try
      {
         for(QuickSearchableTableConfig tableConfig : tables)
         {
            String              tableName = tableConfig.getTableName();
            QuickSearchIndexRun run       = createRunRecord(queryIndexId(tableName), RUN_TYPE);
            runs.put(tableConfig, run);
            startTimes.put(tableName, Instant.now());

            IndexCounts tableCounts = indexAllRecords(client, tableConfig, List.of(), physicalIndex);
            counts.put(tableName, tableCounts);
            indexed = indexed + tableCounts.indexed();

            if(tableCounts.errors() > 0)
            {
               throw (new QException("Full reindex indexed " + tableCounts.indexed() + " of " + tableCounts.processed() + " records for table [" + tableName + "]; first error: " + tableCounts.firstError()));
            }
         }

         client.refreshIndex(physicalIndex);
         client.swapAliasTo(physicalIndex);
         swapped = true;

         for(QuickSearchableTableConfig tableConfig : tables)
         {
            String      tableName   = tableConfig.getTableName();
            IndexCounts tableCounts = counts.get(tableName);

            Map<String, Serializable> values = new HashMap<>();
            values.put("lastFullReindexTime", Instant.now());
            values.put("lastBasepullTime", startTimes.get(tableName));
            values.put("recordCount", tableCounts.processed());
            Integer documentCount = ReconcileIndexStep.safeCount(client, tableName);
            if(documentCount != null)
            {
               values.put("documentCount", documentCount);
            }
            values.put("searchableFieldsJson", buildSearchableFieldsJson(tableConfig));
            values.put("status", STATUS_ACTIVE);
            updateIndexRow(runs.get(tableConfig).getQuickSearchIndexId(), values);

            completeRunRecord(runs.get(tableConfig), RUN_COMPLETED, tableCounts.processed(), tableCounts.indexed(), 0, null);
         }

         LOG.info("Full reindex complete", logPair("physicalIndex", physicalIndex), logPair("recordsIndexed", indexed));
         return (indexed);
      }
      catch(Exception e)
      {
         LOG.warn(swapped ? "Full reindex failed after the alias swap; the new index stays in service" : "Full reindex failed; the previous index stays in service", e, logPair("physicalIndex", physicalIndex));

         for(Map.Entry<QuickSearchableTableConfig, QuickSearchIndexRun> entry : runs.entrySet())
         {
            IndexCounts tableCounts = counts.get(entry.getKey().getTableName());
            completeRunRecord(entry.getValue(), RUN_FAILED,
               tableCounts == null ? 0 : tableCounts.processed(),
               tableCounts == null ? 0 : tableCounts.indexed(),
               tableCounts == null ? 1 : Math.max(1, tableCounts.errors()),
               e.getMessage());
         }

         if(!swapped)
         {
            try
            {
               client.deletePhysicalIndex(physicalIndex);
            }
            catch(Exception cleanup)
            {
               LOG.warn("Could not delete the incomplete physical index", cleanup, logPair("physicalIndex", physicalIndex));
            }
         }

         if(e instanceof QException qException)
         {
            throw qException;
         }
         throw new QException("Full reindex failed: " + e.getMessage(), e);
      }
   }



   /*******************************************************************************
    ** Rebuild one table in place (kept for callers that used this directly).
    *******************************************************************************/
   void reindexTable(String tableName, QuickSearchableTableConfig tableConfig) throws QException
   {
      QRecord row = queryIndexRow(tableName);
      if(row == null)
      {
         ensureIndexRowExists(tableName, tableConfig);
      }
      new ReconcileIndexStep().reconcileTable(tableConfig, RUN_TYPE);
   }

}

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
 ** Reconcile the index with its source tables without a search blackout.
 **
 ** For each enabled table (or the one named by the optional "tableName"
 ** input): re-index every source record in primary-key order, then, if every
 ** document indexed, refresh and delete the table's documents this run did not
 ** touch (indexedAt before the run started). When no table filter is given,
 ** documents of tables that are no longer configured are removed too.
 **
 ** Used by the full reindex of a single table as well, with its own run type.
 *******************************************************************************/
public class ReconcileIndexStep extends AbstractIndexingStep
{
   private static final QLogger LOG = QLogger.getLogger(ReconcileIndexStep.class);

   public static final String RUN_TYPE = "RECONCILE";



   /*******************************************************************************
    ** Totals from reconciling one table.
    *******************************************************************************/
   public record ReconcileCounts(Integer recordsIndexed, Long documentsRemoved)
   {
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
   {
      String tableNameFilter = input.getValueString("tableName");
      boolean allTables      = tableNameFilter == null || tableNameFilter.isBlank();

      Integer recordsIndexed   = 0;
      Long    documentsRemoved = 0L;
      Integer tablesSkipped    = 0;

      for(QuickSearchableTableConfig tableConfig : getDiscoveredTables())
      {
         if(!allTables && !tableNameFilter.equals(tableConfig.getTableName()))
         {
            continue;
         }

         ensureIndexRowExists(tableConfig.getTableName(), tableConfig);
         QRecord row = queryIndexRow(tableConfig.getTableName());
         if(!isRowEnabled(row))
         {
            LOG.info("Table is disabled; skipping reconcile", logPair("tableName", tableConfig.getTableName()));
            tablesSkipped++;
            continue;
         }

         ReconcileCounts counts = reconcileTable(tableConfig, RUN_TYPE);
         recordsIndexed   = recordsIndexed + counts.recordsIndexed();
         documentsRemoved = documentsRemoved + counts.documentsRemoved();
      }

      Long orphanDocumentsRemoved = 0L;
      if(allTables)
      {
         List<String> configured = new ArrayList<>();
         getDiscoveredTables().forEach(tc -> configured.add(tc.getTableName()));
         orphanDocumentsRemoved = getClient().deleteDocumentsForTablesNotIn(configured);
         if(orphanDocumentsRemoved > 0)
         {
            LOG.info("Removed documents of tables that are no longer configured", logPair("count", orphanDocumentsRemoved));
         }
      }

      purgeOldRuns();

      output.addValue("recordsIndexed", recordsIndexed);
      output.addValue("documentsRemoved", documentsRemoved);
      output.addValue("orphanDocumentsRemoved", orphanDocumentsRemoved);
      output.addValue("tablesSkipped", tablesSkipped);
   }



   /*******************************************************************************
    ** Reconcile one table, recording the run with the given run type.
    *******************************************************************************/
   ReconcileCounts reconcileTable(QuickSearchableTableConfig tableConfig, String runType) throws QException
   {
      String                      tableName = tableConfig.getTableName();
      QuickSearchOpenSearchClient client    = getClient();

      Integer             indexId = queryIndexId(tableName);
      QuickSearchIndexRun run     = createRunRecord(indexId, runType);

      try
      {
         Instant     reconcileStart = Instant.now();
         IndexCounts counts         = indexAllRecords(client, tableConfig, List.of(), null);

         Long documentsRemoved = 0L;
         if(counts.errors() == 0)
         {
            client.refreshIndex();
            documentsRemoved = client.deleteDocumentsIndexedBefore(tableName, reconcileStart);

            Map<String, Serializable> values = new HashMap<>();
            values.put("lastReconcileTime", Instant.now());
            values.put("lastBasepullTime", reconcileStart);
            values.put("recordCount", counts.processed());
            values.put("documentCount", safeCount(client, tableName));
            values.put("searchableFieldsJson", buildSearchableFieldsJson(tableConfig));
            values.put("status", STATUS_ACTIVE);
            if(ReconcileIndexStep.RUN_TYPE.equals(runType) == false)
            {
               values.put("lastFullReindexTime", Instant.now());
            }
            updateIndexRow(indexId, values);
         }
         else
         {
            LOG.warn("Reconcile had indexing errors; stale documents were not removed", logPair("tableName", tableName), logPair("errorCount", counts.errors()));
         }

         String status = counts.errors() == 0 ? RUN_COMPLETED : RUN_FAILED;
         completeRunRecord(run, status, counts.processed(), counts.indexed(), counts.errors(), counts.firstError());

         LOG.info("Reconcile complete", logPair("tableName", tableName), logPair("processed", counts.processed()), logPair("indexed", counts.indexed()),
            logPair("skippedStale", counts.skipped()), logPair("errors", counts.errors()), logPair("documentsRemoved", documentsRemoved));

         if(counts.errors() > 0)
         {
            throw (new QException("Reconcile indexed " + counts.indexed() + " of " + counts.processed() + " records for table [" + tableName + "]; first error: " + counts.firstError()));
         }

         return (new ReconcileCounts(counts.indexed(), documentsRemoved));
      }
      catch(QException e)
      {
         if(!RUN_FAILED.equals(run.getStatus()))
         {
            completeRunRecord(run, RUN_FAILED, 0, 0, 1, e.getMessage());
            run.withStatus(RUN_FAILED);
         }
         throw e;
      }
      catch(Exception e)
      {
         LOG.warn("Reconcile failed", e, logPair("tableName", tableName));
         completeRunRecord(run, RUN_FAILED, 0, 0, 1, e.getMessage());
         throw new QException("Reconcile failed for table [" + tableName + "]: " + e.getMessage(), e);
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static Integer safeCount(QuickSearchOpenSearchClient client, String tableName)
   {
      try
      {
         Long count = client.countDocumentsForTable(tableName);
         return (count == null ? null : count.intValue());
      }
      catch(Exception e)
      {
         return (null);
      }
   }

}

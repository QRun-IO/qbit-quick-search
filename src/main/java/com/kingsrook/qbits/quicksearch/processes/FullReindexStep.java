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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;
import com.kingsrook.qbits.quicksearch.opensearch.BulkIndexResult;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
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
 ** While the new index is built, real-time writes still go through the alias
 ** to the old one. Updates are caught by the next basepull (lastBasepullTime
 ** is set to each table's start). Deletes are caught here: each table's
 ** quickSearchIndex row is REBUILDING for the whole rebuild, which makes the
 ** record change listener (on any node) also record each delete as an
 ** AWAITING_REINDEX failed event. Once the rebuild's own writes are done and
 ** the alias is swapped, those deletes are applied through the alias.
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

      Map<QuickSearchableTableConfig, QuickSearchIndexRun> runs        = new LinkedHashMap<>();
      Map<String, IndexCounts>                              counts      = new LinkedHashMap<>();
      Map<String, Instant>                                  startTimes  = new LinkedHashMap<>();
      Map<String, String>                                   priorStatus = new LinkedHashMap<>();
      Integer                                               indexed     = 0;

      boolean swapped = false;

      try
      {
         markRebuilding(tables, priorStatus);

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
            priorStatus.remove(tableName);

            completeRunRecord(runs.get(tableConfig), RUN_COMPLETED, tableCounts.processed(), tableCounts.indexed(), 0, null);
         }

         applyCapturedDeletes(client, tables);

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

         restoreStatus(priorStatus);
         applyCapturedDeletes(client, tables);

         if(e instanceof QException qException)
         {
            throw qException;
         }
         throw new QException("Full reindex failed: " + e.getMessage(), e);
      }
   }



   /*******************************************************************************
    ** Mark each table's index row REBUILDING before any of them is read,
    ** remembering the status it had. A stale REBUILDING left by a crashed run
    ** is remembered as ACTIVE.
    *******************************************************************************/
   private void markRebuilding(List<QuickSearchableTableConfig> tables, Map<String, String> priorStatus) throws QException
   {
      for(QuickSearchableTableConfig tableConfig : tables)
      {
         QRecord row    = queryIndexRow(tableConfig.getTableName());
         String  status = row.getValueString("status");
         priorStatus.put(tableConfig.getTableName(), status == null || STATUS_REBUILDING.equals(status) ? STATUS_ACTIVE : status);
         updateIndexRow(row.getValueInteger("id"), Map.of("status", STATUS_REBUILDING));
      }
   }



   /*******************************************************************************
    ** After a failed rebuild, put back the status of each table still marked
    ** REBUILDING. Never throws.
    *******************************************************************************/
   private void restoreStatus(Map<String, String> priorStatus)
   {
      for(Map.Entry<String, String> entry : priorStatus.entrySet())
      {
         try
         {
            updateIndexRow(queryIndexId(entry.getKey()), Map.of("status", entry.getValue()));
         }
         catch(Exception e)
         {
            LOG.warn("Could not restore the index row status after a failed full reindex", e, logPair("tableName", entry.getKey()));
         }
      }
   }



   /*******************************************************************************
    ** Apply the deletes the listener captured during the rebuild (failed-event
    ** rows with status AWAITING_REINDEX) through the alias, after the rebuild's
    ** own writes, so an in-flight page cannot bring a deleted record back.
    ** Called once the tables are no longer REBUILDING, so the listener stops
    ** adding rows (one that races the status change hands its own rows to
    ** basepull).
    **
    ** Rows are read in id order, past the previous batch, so a batch whose rows
    ** could not be removed or updated (QQQ reports those as per-row errors, not
    ** exceptions) is not read again; such rows stay AWAITING_REINDEX for the next
    ** full reindex. Never throws.
    *******************************************************************************/
   void applyCapturedDeletes(QuickSearchOpenSearchClient client, List<QuickSearchableTableConfig> tables)
   {
      List<Serializable> tableNames = new ArrayList<>();
      tables.forEach(tableConfig -> tableNames.add(tableConfig.getTableName()));
      if(tableNames.isEmpty())
      {
         return;
      }

      Integer applied = 0;
      try
      {
         QuickSearchQBitConfig config           = getConfig();
         String                failedEventTable = config.applyPrefix(QuickSearchFailedEvent.TABLE_NAME);
         Serializable          afterId          = null;

         while(true)
         {
            QQueryFilter filter = new QQueryFilter(
               new QFilterCriteria("status", QCriteriaOperator.EQUALS, QuickSearchFailedEvent.STATUS_AWAITING_REINDEX),
               new QFilterCriteria("tableName", QCriteriaOperator.IN, tableNames))
               .withOrderBy(new QFilterOrderBy("id", true))
               .withLimit(config.getSourceBatchSize());
            if(afterId != null)
            {
               filter.addCriteria(new QFilterCriteria("id", QCriteriaOperator.GREATER_THAN, afterId));
            }

            QueryInput queryInput = new QueryInput();
            queryInput.setTableName(failedEventTable);
            queryInput.setFilter(filter);
            List<QRecord> rows = new QueryAction().execute(queryInput).getRecords();
            if(CollectionUtils.nullSafeIsEmpty(rows))
            {
               break;
            }

            afterId = rows.get(rows.size() - 1).getValue("id");
            try
            {
               applied = applied + applyCapturedDeleteBatch(client, config, failedEventTable, rows);
            }
            catch(Exception e)
            {
               LOG.warn("Could not apply a batch of deletes captured during the full reindex; they stay AWAITING_REINDEX for the next full reindex", e,
                  logPair("tableNames", rows.stream().map(row -> row.getValueString("tableName")).distinct().toList()), logPair("count", rows.size()));
            }
         }
      }
      catch(Exception e)
      {
         LOG.warn("Could not apply deletes captured during the full reindex; they stay AWAITING_REINDEX for the next full reindex", e);
      }

      LOG.info("Applied deletes captured during the full reindex", logPair("count", applied));
   }



   /*******************************************************************************
    ** Apply one batch of captured deletes; returns how many were applied.
    ** A record that exists again (re-created with the same key since it was
    ** deleted) keeps its document, and its row is just removed. Deleting a
    ** missing document is harmless. Applied rows are removed; rows the cluster
    ** rejects become PENDING for basepull to retry.
    *******************************************************************************/
   private Integer applyCapturedDeleteBatch(QuickSearchOpenSearchClient client, QuickSearchQBitConfig config, String failedEventTable, List<QRecord> rows) throws QException
   {
      Map<String, List<QRecord>> rowsByTable = new LinkedHashMap<>();
      for(QRecord row : rows)
      {
         rowsByTable.computeIfAbsent(row.getValueString("tableName"), k -> new ArrayList<>()).add(row);
      }

      List<QRecord> finished = new ArrayList<>();
      List<QRecord> toApply  = new ArrayList<>();
      for(Map.Entry<String, List<QRecord>> entry : rowsByTable.entrySet())
      {
         Set<String> existingIds = queryExistingRecordIds(entry.getKey(), entry.getValue());
         for(QRecord row : entry.getValue())
         {
            if(existingIds.contains(row.getValueString("recordId")))
            {
               finished.add(row);
            }
            else
            {
               toApply.add(row);
            }
         }
      }

      String error = null;
      if(!toApply.isEmpty())
      {
         List<String> documentIds = new ArrayList<>();
         for(QRecord row : toApply)
         {
            documentIds.add(OpenSearchDocument.buildDocumentId(row.getValueString("tableName"), row.getValueString("recordId")));
         }

         try
         {
            BulkIndexResult result = client.deleteDocuments(documentIds, config.getBulkBatchSize());
            error = result.getFailureCount() == 0 ? null : String.join("; ", result.getErrors());
         }
         catch(Exception e)
         {
            error = e.getMessage() == null ? e.toString() : e.getMessage();
         }

         if(error == null)
         {
            finished.addAll(toApply);
         }
      }

      if(!finished.isEmpty())
      {
         DeleteInput deleteInput = new DeleteInput();
         deleteInput.setTableName(failedEventTable);
         deleteInput.setPrimaryKeys(finished.stream().map(row -> row.getValue("id")).toList());
         DeleteOutput deleteOutput = new DeleteAction().execute(deleteInput);
         if(CollectionUtils.nullSafeHasContents(deleteOutput.getRecordsWithErrors()))
         {
            LOG.warn("Could not remove applied captured-delete rows; they stay AWAITING_REINDEX and are applied again by the next full reindex",
               logPair("count", deleteOutput.getRecordsWithErrors().size()), logPair("firstError", deleteOutput.getRecordsWithErrors().get(0).getErrors()));
         }
      }

      if(error != null)
      {
         List<QRecord> updates = new ArrayList<>();
         for(QRecord row : toApply)
         {
            updates.add(new QRecord()
               .withValue("id", row.getValue("id"))
               .withValue("errorMessage", truncate(error, 4000))
               .withValue("status", QuickSearchFailedEvent.STATUS_PENDING));
         }

         UpdateInput updateInput = new UpdateInput();
         updateInput.setTableName(failedEventTable);
         updateInput.setRecords(updates);
         List<QRecord> notUpdated = CollectionUtils.nonNullList(new UpdateAction().execute(updateInput).getRecords()).stream()
            .filter(record -> CollectionUtils.nullSafeHasContents(record.getErrors()))
            .toList();
         LOG.warn("Could not apply deletes captured during the full reindex; basepull will retry them", logPair("count", toApply.size()), logPair("error", error));
         if(!notUpdated.isEmpty())
         {
            LOG.warn("Could not hand captured deletes to basepull; they stay AWAITING_REINDEX for the next full reindex",
               logPair("count", notUpdated.size()), logPair("firstError", notUpdated.get(0).getErrors()));
         }
         return (0);
      }

      return (toApply.size());
   }



   /*******************************************************************************
    ** The record IDs, among the rows' recordIds, that exist in the source table.
    *******************************************************************************/
   Set<String> queryExistingRecordIds(String tableName, List<QRecord> rows) throws QException
   {
      QuickSearchableTableConfig tableConfig = getTableConfig(tableName);
      if(tableConfig == null)
      {
         return (Set.of());
      }

      String             primaryKeyField = tableConfig.getPrimaryKeyField() == null ? "id" : tableConfig.getPrimaryKeyField();
      List<Serializable> recordIds       = new ArrayList<>();
      rows.forEach(row -> recordIds.add(row.getValueString("recordId")));

      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(tableName);
      queryInput.setFilter(new QQueryFilter(new QFilterCriteria(primaryKeyField, QCriteriaOperator.IN, recordIds)));

      Set<String> existingIds = new HashSet<>();
      for(QRecord record : CollectionUtils.nonNullList(new QueryAction().execute(queryInput).getRecords()))
      {
         existingIds.add(record.getValueString(primaryKeyField));
      }
      return (existingIds);
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

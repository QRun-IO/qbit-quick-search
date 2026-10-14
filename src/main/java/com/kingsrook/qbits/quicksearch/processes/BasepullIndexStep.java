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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
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
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.config.QuickSearchConfigWrites;
import com.kingsrook.qbits.quicksearch.config.QuickSearchFieldRules;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;
import com.kingsrook.qbits.quicksearch.opensearch.BulkIndexResult;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Scheduled catch-up. For each enabled table that is due, re-indexes records
 ** whose basepull timestamp is at or after the previous run's start minus the
 ** configured overlap, paging by primary key. First recovers what a full
 ** reindex that stopped without finishing left behind (so its captured deletes
 ** are replayed in the same run) and replays failed real-time events, and
 ** afterwards purges old run history.
 **
 ** The watermark stored on the quick_search_index row is the run's start time,
 ** captured before the first query, so rows modified during the run are read
 ** again next time (re-indexing is idempotent). A table without a timestamp
 ** field is left to the reconcile process.
 **
 ** One table's failure does not stop the others, but the step fails at the end
 ** if any table failed, so the scheduler and run history show it.
 *******************************************************************************/
public class BasepullIndexStep extends AbstractIndexingStep
{
   private static final QLogger LOG = QLogger.getLogger(BasepullIndexStep.class);

   public static final String RUN_TYPE          = "BASEPULL";
   public static final int    MAX_REPLAY_ATTEMPTS = 10;



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
   {
      QuickSearchRuntime runtime = QuickSearchRuntime.get();
      if(runtime != null)
      {
         runtime.getConfigStore().tryPrime();
         if(runtime.isIndexingPaused())
         {
            LOG.info("Quick Search indexing is paused; basepull skipped");
            output.addValue("tablesIndexed", 0);
            output.addValue("paused", true);
            return;
         }
      }

      List<String>  failures = new ArrayList<>();
      List<QRecord> liveRuns = List.of();

      try
      {
         liveRuns = new FullReindexStep().recoverDeadRun(true);
      }
      catch(Exception e)
      {
         LOG.warn("Recovering from a stopped full reindex did not complete", e);
         failures.add("full reindex recovery: " + e.getMessage());
      }

      try
      {
         queueBuildsIfIndexMissingOrEmpty();
      }
      catch(Exception e)
      {
         LOG.warn("Could not check whether the search index is missing or empty", e);
         failures.add("index check: " + e.getMessage());
      }

      try
      {
         replayFailedEvents(output);
      }
      catch(Exception e)
      {
         LOG.warn("Replaying failed events did not complete", e);
         failures.add("failed-event replay: " + e.getMessage());
      }

      for(QuickSearchableTableConfig tableConfig : getDiscoveredTables())
      {
         ensureIndexRowExists(tableConfig.getTableName(), tableConfig);
      }

      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(getConfig().getQuickSearchIndexTableName());
      queryInput.setFilter(new QQueryFilter(new QFilterCriteria("enabled", QCriteriaOperator.EQUALS, true)));
      List<QRecord> indexRecords = new QueryAction().execute(queryInput).getRecords();

      if(CollectionUtils.nullSafeIsEmpty(indexRecords))
      {
         LOG.info("No enabled quick_search_index rows found; nothing to basepull");
         purgeOldRuns();
         return;
      }

      Integer tablesIndexed = 0;
      Integer tablesBuilt   = 0;
      for(QRecord indexRecord : indexRecords)
      {
         ////////////////////////////////////////////////////////////////////////
         // self-priming: a table queued for a build (new, changed by code, or //
         // the index was missing) is built in place instead of basepulled; a  //
         // BUILDING row with no live run was left by a stopped server         //
         ////////////////////////////////////////////////////////////////////////
         String status = indexRecord.getValueString("status");
         if(STATUS_PENDING_BUILD.equals(status) || (STATUS_BUILDING.equals(status) && CollectionUtils.nullSafeIsEmpty(liveRuns)))
         {
            try
            {
               if(buildTable(indexRecord, runtime))
               {
                  tablesBuilt++;
               }
            }
            catch(Exception e)
            {
               LOG.warn("Building a queued search index failed; it is retried after its update interval", e, logPair("tableName", indexRecord.getValueString("tableName")));
               failures.add(indexRecord.getValueString("tableName") + " build: " + e.getMessage());
            }
            continue;
         }

         QuickSearchIndex index = new QuickSearchIndex()
            .withId(indexRecord.getValueInteger("id"))
            .withTableName(indexRecord.getValueString("tableName"))
            .withEnabled(indexRecord.getValueBoolean("enabled"))
            .withBasepullIntervalMinutes(indexRecord.getValueInteger("basepullIntervalMinutes"))
            .withBasepullTimestampField(indexRecord.getValueString("basepullTimestampField"))
            .withLastBasepullTime(indexRecord.getValueInstant("lastBasepullTime"));

         QuickSearchableTableConfig tableConfig = getTableConfig(index.getTableName());
         detectDrift(indexRecord, tableConfig);

         if(!isDueForBasepull(index))
         {
            LOG.debug("Index not due for basepull; skipping", logPair("tableName", index.getTableName()), logPair("lastBasepullTime", index.getLastBasepullTime()));
            continue;
         }

         try
         {
            basepullIndex(index);
            tablesIndexed++;
         }
         catch(Exception e)
         {
            LOG.warn("Error during basepull for table; continuing with next table", e, logPair("tableName", index.getTableName()));
            failures.add(index.getTableName() + ": " + e.getMessage());
         }
      }

      purgeOldRuns();
      output.addValue("tablesIndexed", tablesIndexed);
      output.addValue("tablesBuilt", tablesBuilt);

      if(!failures.isEmpty())
      {
         throw (new QException("Basepull failed for " + failures.size() + " item(s): " + String.join("; ", failures)));
      }
   }



   /*******************************************************************************
    ** Build one queued table in place (the reconcile algorithm, recorded as a
    ** FULL_REINDEX run): BUILDING while it runs, ACTIVE after. A failure
    ** queues it again, retried once its update interval has passed since the
    ** failure; a cancel leaves it NEEDS_REINDEX (Apply Search Index Changes
    ** starts it again); a pause leaves it queued for when indexing resumes.
    ** Returns false when it was skipped (backing off after a failure).
    *******************************************************************************/
   public boolean buildTable(QRecord row, QuickSearchRuntime runtime) throws QException
   {
      String                     tableName   = row.getValueString("tableName");
      Integer                    indexId     = row.getValueInteger("id");
      QuickSearchableTableConfig tableConfig = getTableConfig(tableName);
      if(tableConfig == null)
      {
         throw (new QException("Table is no longer configured for Quick Search"));
      }

      Integer intervalMinutes = row.getValueInteger("basepullIntervalMinutes");
      long    backoffMillis   = Math.max(1, intervalMinutes == null ? 5 : intervalMinutes) * 60_000L;
      if(runtime != null && System.currentTimeMillis() - runtime.getLastBuildFailureMillis(tableName) < backoffMillis)
      {
         return (false);
      }

      updateIndexRow(indexId, Map.of("status", STATUS_BUILDING));
      try
      {
         new ReconcileIndexStep().reconcileTable(tableConfig, FullReindexStep.RUN_TYPE);
         if(runtime != null)
         {
            runtime.clearBuildFailure(tableName);
         }
         LOG.info("Built queued search index", logPair("tableName", tableName));
         return (true);
      }
      catch(QuickSearchRunCancelledException e)
      {
         boolean paused = runtime != null && runtime.isIndexingPaused();
         updateIndexRow(indexId, Map.of("status", paused ? STATUS_PENDING_BUILD : STATUS_NEEDS_REINDEX));
         LOG.info("Queued search index build stopped: " + e.getMessage(), logPair("tableName", tableName));
         return (false);
      }
      catch(Exception e)
      {
         updateIndexRow(indexId, Map.of("status", STATUS_PENDING_BUILD));
         if(runtime != null)
         {
            runtime.recordBuildFailure(tableName);
         }
         throw (e instanceof QException qe ? qe : new QException("Build failed: " + e.getMessage(), e));
      }
   }



   /*******************************************************************************
    ** Self-priming of the index itself: when the alias is gone (a new cluster,
    ** or the index was deleted) it is created again. When the index was just
    ** created, every enabled table that had documents in the lost index (and
    ** has not been fully rebuilt since) is queued for a build; when it is
    ** empty while a table last counted documents in it, every enabled table is.
    *******************************************************************************/
   void queueBuildsIfIndexMissingOrEmpty() throws QException
   {
      QuickSearchOpenSearchClient client = getClient();
      String                      reason = null;
      if(!client.indexExists())
      {
         client.ensureIndexExists();
      }

      List<QRecord> rows = queryEnabledRows();
      if(client.wasIndexCreated())
      {
         ///////////////////////////////////////////////////////////////////////
         // only tables that had documents in the lost index and have not been //
         // fully rebuilt since: a table never basepulled reads everything on  //
         // its first basepull anyway                                          //
         ///////////////////////////////////////////////////////////////////////
         Instant createdAt = client.getIndexCreatedAt() == null ? Instant.EPOCH : client.getIndexCreatedAt();
         rows = rows.stream().filter(r -> r.getValue("lastBasepullTime") != null
            && !isAfter(r.getValueInstant("lastFullReindexTime"), createdAt) && !isAfter(r.getValueInstant("lastReconcileTime"), createdAt)).toList();
         reason = "the search index was missing and has been created";
      }
      else
      {
         Long documents = client.countAllDocuments();
         if(documents != null && documents == 0 && rows.stream().anyMatch(r -> r.getValueInteger("documentCount") != null && r.getValueInteger("documentCount") > 0))
         {
            reason = "the search index is empty";
         }
      }

      if(reason == null)
      {
         return;
      }

      int queued = 0;
      for(QRecord row : rows)
      {
         String status = row.getValueString("status");
         if(!STATUS_PENDING_BUILD.equals(status) && !STATUS_BUILDING.equals(status) && !STATUS_REBUILDING.equals(status))
         {
            QuickSearchConfigWrites.asSystem(() ->
            {
               updateIndexRow(row.getValueInteger("id"), Map.of("status", STATUS_PENDING_BUILD));
               return (null);
            });
            queued++;
         }
      }
      client.clearIndexCreated();
      LOG.info("Queued a build of every enabled table: " + reason, logPair("tablesQueued", queued));
   }



   /*******************************************************************************
    ** Whether a time is set and after another.
    *******************************************************************************/
   private static boolean isAfter(Instant time, Instant other)
   {
      return (time != null && time.isAfter(other));
   }



   /*******************************************************************************
    ** The enabled quick_search_index rows.
    *******************************************************************************/
   private List<QRecord> queryEnabledRows() throws QException
   {
      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(getConfig().getQuickSearchIndexTableName());
      queryInput.setFilter(new QQueryFilter(new QFilterCriteria("enabled", QCriteriaOperator.EQUALS, true)));
      return (CollectionUtils.nonNullList(new QueryAction().execute(queryInput).getRecords()));
   }



   /*******************************************************************************
    ** Due when never run, or when the interval has elapsed since the last run.
    *******************************************************************************/
   boolean isDueForBasepull(QuickSearchIndex index)
   {
      Instant lastBasepullTime = index.getLastBasepullTime();
      Integer intervalMinutes  = index.getBasepullIntervalMinutes();
      if(lastBasepullTime == null || intervalMinutes == null)
      {
         return (true);
      }
      return (lastBasepullTime.plus(intervalMinutes, ChronoUnit.MINUTES).isBefore(Instant.now()));
   }



   /*******************************************************************************
    ** One table's basepull run.
    *******************************************************************************/
   void basepullIndex(QuickSearchIndex index) throws QException
   {
      String                      tableName   = index.getTableName();
      QuickSearchableTableConfig  tableConfig = getTableConfig(tableName);
      QuickSearchOpenSearchClient client      = getClient();
      QuickSearchQBitConfig       config      = getConfig();

      QuickSearchIndexRun run = createRunRecord(index.getId(), RUN_TYPE);

      if(tableConfig == null)
      {
         String message = "Table is no longer configured for Quick Search; disable or remove its quick_search_index row";
         completeRunRecord(run, RUN_FAILED, 0, 0, 1, message);
         throw (new QException(tableName + ": " + message));
      }

      Instant runStart = Instant.now();

      try
      {
         String timestampField = tableConfig.getBasepullTimestampField();
         if(timestampField == null && index.getLastBasepullTime() != null)
         {
            ////////////////////////////////////////////////////////////////////////
            // no change-detection field: only the first run indexes everything;  //
            // later changes are caught by reconcile                              //
            ////////////////////////////////////////////////////////////////////////
            completeRunRecord(run, RUN_COMPLETED, 0, 0, 0, null);
            return;
         }

         List<QFilterCriteria> criteria = new ArrayList<>();
         if(timestampField != null && index.getLastBasepullTime() != null)
         {
            int     overlap = config.getBasepullOverlapSeconds() == null ? 0 : config.getBasepullOverlapSeconds();
            Instant since   = index.getLastBasepullTime().minusSeconds(overlap);
            criteria.add(new QFilterCriteria(timestampField, QCriteriaOperator.GREATER_THAN_OR_EQUALS, since));
         }

         IndexCounts counts = indexAllRecords(client, tableConfig, criteria, null, run);

         if(counts.errors() == 0)
         {
            Map<String, Serializable> values = new HashMap<>();
            values.put("lastBasepullTime", runStart);
            Integer documentCount = ReconcileIndexStep.safeCount(client, tableName);
            if(documentCount != null)
            {
               values.put("documentCount", documentCount);
            }
            updateIndexRow(index.getId(), values);
         }

         String status = counts.errors() > 0 ? RUN_FAILED : RUN_COMPLETED;
         completeRunRecord(run, status, counts.processed(), counts.indexed(), counts.errors(), counts.firstError());

         LOG.info("Basepull complete", logPair("tableName", tableName), logPair("processed", counts.processed()), logPair("indexed", counts.indexed()),
            logPair("skippedStale", counts.skipped()), logPair("errors", counts.errors()));

         if(counts.errors() > 0)
         {
            throw (new QException("Basepull indexed " + counts.indexed() + " of " + counts.processed() + " records for table [" + tableName + "]; first error: " + counts.firstError()));
         }
      }
      catch(QuickSearchRunCancelledException e)
      {
         completeCancelledRun(run, e);
         throw e;
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
         completeRunRecord(run, RUN_FAILED, 0, 0, 1, e.getMessage());
         throw (new QException("Basepull failed for table [" + tableName + "]: " + e.getMessage(), e));
      }
   }



   /*******************************************************************************
    ** Replay PENDING failed real-time events: re-read and index records for
    ** INDEX events, delete documents for DELETE events. A DELETE whose record
    ** exists again (re-created with the same key since) keeps its document and
    ** counts as replayed. Successes are removed; failures bump attempts and
    ** become EXHAUSTED at the limit.
    *******************************************************************************/
   void replayFailedEvents(RunBackendStepOutput output) throws QException
   {
      QuickSearchQBitConfig config          = getConfig();
      String                failedEventTable = config.applyTablePrefix(QuickSearchFailedEvent.TABLE_NAME);

      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(failedEventTable);
      queryInput.setFilter(new QQueryFilter(new QFilterCriteria("status", QCriteriaOperator.EQUALS, QuickSearchFailedEvent.STATUS_PENDING))
         .withOrderBy(new QFilterOrderBy("id", true))
         .withLimit(config.getSourceBatchSize()));
      List<QRecord> events = new QueryAction().execute(queryInput).getRecords();

      if(CollectionUtils.nullSafeIsEmpty(events))
      {
         output.addValue("failedEventsReplayed", 0);
         return;
      }

      QuickSearchOpenSearchClient client = getClient();

      Map<String, List<QRecord>> indexByTable  = new LinkedHashMap<>();
      Map<String, List<QRecord>> deleteByTable = new LinkedHashMap<>();
      for(QRecord event : events)
      {
         Map<String, List<QRecord>> target = "DELETE".equals(event.getValueString("action")) ? deleteByTable : indexByTable;
         target.computeIfAbsent(event.getValueString("tableName"), k -> new ArrayList<>()).add(event);
      }

      List<Serializable> succeeded = new ArrayList<>();
      List<QRecord>      failed    = new ArrayList<>();

      for(Map.Entry<String, List<QRecord>> entry : deleteByTable.entrySet())
      {
         if(QuickSearchFieldRules.hasProtectedPrimaryKey(entry.getKey()))
         {
            markReplayOutcome(entry.getValue(), false, List.of("The primary key cannot be indexed"), succeeded, failed);
            continue;
         }
         List<QRecord> toDelete = entry.getValue();
         try
         {
            Set<String> existingIds = queryExistingRecordIds(entry.getKey(), entry.getValue());
            toDelete = new ArrayList<>();
            for(QRecord event : entry.getValue())
            {
               if(existingIds.contains(event.getValueString("recordId")))
               {
                  succeeded.add(event.getValue("id"));
               }
               else
               {
                  toDelete.add(event);
               }
            }

            if(!toDelete.isEmpty())
            {
               List<String> documentIds = new ArrayList<>();
               for(QRecord event : toDelete)
               {
                  documentIds.add(OpenSearchDocument.buildDocumentId(entry.getKey(), event.getValueString("recordId")));
               }
               BulkIndexResult result = client.deleteDocuments(documentIds, config.getBulkBatchSize());
               markReplayOutcome(toDelete, result.getFailureCount() == 0, result.getErrors(), succeeded, failed);
            }
         }
         catch(Exception e)
         {
            markReplayOutcome(toDelete, false, List.of(e.getMessage() == null ? e.toString() : e.getMessage()), succeeded, failed);
         }
      }

      for(Map.Entry<String, List<QRecord>> entry : indexByTable.entrySet())
      {
         String                     tableName   = entry.getKey();
         QuickSearchableTableConfig tableConfig = getTableConfig(tableName);
         if(tableConfig == null)
         {
            markReplayOutcome(entry.getValue(), false, List.of("table is no longer configured"), succeeded, failed);
            continue;
         }

         try
         {
            String             primaryKeyField = tableConfig.getPrimaryKeyField() == null ? "id" : tableConfig.getPrimaryKeyField();
            List<Serializable> ids             = new ArrayList<>();
            for(QRecord event : entry.getValue())
            {
               ids.add(event.getValueString("recordId"));
            }

            QueryInput sourceQuery = new QueryInput();
            sourceQuery.setTableName(tableName);
            sourceQuery.setFilter(new QQueryFilter(new QFilterCriteria(primaryKeyField, QCriteriaOperator.IN, ids)));
            sourceQuery.setShouldGenerateDisplayValues(true);
            sourceQuery.setShouldTranslatePossibleValues(true);
            List<QRecord> records = CollectionUtils.nonNullList(new QueryAction().execute(sourceQuery).getRecords());

            List<OpenSearchDocument> documents = new ArrayList<>();
            List<String>             foundIds  = new ArrayList<>();
            for(QRecord record : records)
            {
               OpenSearchDocument document = IndexingUtils.buildDocument(record, tableConfig);
               if(document != null)
               {
                  documents.add(document);
                  foundIds.add(document.getRecordId());
               }
            }

            ///////////////////////////////////////////////////////////////////////
            // a record deleted since the event was recorded has no document to //
            // index; remove any stale document for it instead                  //
            ///////////////////////////////////////////////////////////////////////
            List<String> goneDocumentIds = new ArrayList<>();
            for(Serializable id : ids)
            {
               if(!foundIds.contains(String.valueOf(id)))
               {
                  goneDocumentIds.add(OpenSearchDocument.buildDocumentId(tableName, String.valueOf(id)));
               }
            }

            BulkIndexResult result = client.indexDocuments(documents, config.getBulkBatchSize());
            if(!goneDocumentIds.isEmpty())
            {
               BulkIndexResult deleteResult = client.deleteDocuments(goneDocumentIds, config.getBulkBatchSize());
               deleteResult.getErrors().forEach(result::addFailure);
            }
            markReplayOutcome(entry.getValue(), result.getFailureCount() == 0, result.getErrors(), succeeded, failed);
         }
         catch(Exception e)
         {
            markReplayOutcome(entry.getValue(), false, List.of(e.getMessage() == null ? e.toString() : e.getMessage()), succeeded, failed);
         }
      }

      if(!succeeded.isEmpty())
      {
         DeleteInput deleteInput = new DeleteInput();
         deleteInput.setTableName(failedEventTable);
         deleteInput.setPrimaryKeys(succeeded);
         new DeleteAction().execute(deleteInput);
      }

      if(!failed.isEmpty())
      {
         UpdateInput updateInput = new UpdateInput();
         updateInput.setTableName(failedEventTable);
         updateInput.setRecords(failed);
         new UpdateAction().execute(updateInput);
      }

      LOG.info("Replayed failed real-time events", logPair("succeeded", succeeded.size()), logPair("stillFailing", failed.size()));
      output.addValue("failedEventsReplayed", succeeded.size());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void markReplayOutcome(List<QRecord> events, boolean success, List<String> errors, List<Serializable> succeeded, List<QRecord> failed)
   {
      for(QRecord event : events)
      {
         if(success)
         {
            succeeded.add(event.getValue("id"));
            continue;
         }

         int attempts = (event.getValueInteger("attempts") == null ? 0 : event.getValueInteger("attempts")) + 1;
         failed.add(new QRecord()
            .withValue("id", event.getValue("id"))
            .withValue("attempts", attempts)
            .withValue("errorMessage", truncate(errors == null || errors.isEmpty() ? "replay failed" : errors.get(0), 4000))
            .withValue("status", attempts >= MAX_REPLAY_ATTEMPTS ? QuickSearchFailedEvent.STATUS_EXHAUSTED : QuickSearchFailedEvent.STATUS_PENDING));
      }
   }

}

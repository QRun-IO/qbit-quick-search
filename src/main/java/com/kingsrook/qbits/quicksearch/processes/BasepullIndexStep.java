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
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
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
 ** configured overlap, paging by primary key. First replays failed real-time
 ** events, and afterwards purges old run history.
 **
 ** The watermark stored on the quickSearchIndex row is the run's start time,
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
      List<String> failures = new ArrayList<>();

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
         LOG.info("No enabled quickSearchIndex rows found; nothing to basepull");
         purgeOldRuns();
         return;
      }

      Integer tablesIndexed = 0;
      for(QRecord indexRecord : indexRecords)
      {
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

      if(!failures.isEmpty())
      {
         throw (new QException("Basepull failed for " + failures.size() + " item(s): " + String.join("; ", failures)));
      }
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
         String message = "Table is no longer configured for Quick Search; disable or remove its quickSearchIndex row";
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

         IndexCounts counts = indexAllRecords(client, tableConfig, criteria, null);

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
    ** INDEX events, delete documents for DELETE events. Successes are removed;
    ** failures bump attempts and become EXHAUSTED at the limit.
    *******************************************************************************/
   void replayFailedEvents(RunBackendStepOutput output) throws QException
   {
      QuickSearchQBitConfig config          = getConfig();
      String                failedEventTable = config.applyPrefix(QuickSearchFailedEvent.TABLE_NAME);

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
         List<String> documentIds = new ArrayList<>();
         for(QRecord event : entry.getValue())
         {
            documentIds.add(OpenSearchDocument.buildDocumentId(entry.getKey(), event.getValueString("recordId")));
         }
         try
         {
            BulkIndexResult result = client.deleteDocuments(documentIds, config.getBulkBatchSize());
            markReplayOutcome(entry.getValue(), result.getFailureCount() == 0, result.getErrors(), succeeded, failed);
         }
         catch(Exception e)
         {
            markReplayOutcome(entry.getValue(), false, List.of(e.getMessage() == null ? e.toString() : e.getMessage()), succeeded, failed);
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

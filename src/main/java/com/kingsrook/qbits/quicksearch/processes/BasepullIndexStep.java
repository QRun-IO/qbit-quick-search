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


import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;
import com.kingsrook.qbits.quicksearch.opensearch.BulkIndexResult;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;


/*******************************************************************************
 ** Incremental basepull indexing process step.
 **
 ** Queries all enabled QuickSearchIndex rows, checks whether each is due for a
 ** basepull run, and for those that are, queries the source table for records
 ** modified since the last basepull, builds OpenSearch documents, and bulk-indexes
 ** them.
 **
 ** Run records are created at the start of each per-table run and updated with
 ** final status and counts on completion.  Per-table failures are caught and
 ** logged so that one bad table does not prevent others from being processed.
 *******************************************************************************/
public class BasepullIndexStep extends AbstractIndexingStep
{
   private static final QLogger LOG = QLogger.getLogger(BasepullIndexStep.class);



   /*******************************************************************************
    ** Main entry point called by the QQQ process engine.
    **
    ** 1. For each discovered table, ensure a quickSearchIndex row exists.
    ** 2. Query all enabled quickSearchIndex rows.
    ** 3. For each enabled row, check isDueForBasepull and run basepullIndex.
    **
    ** @param input  process step input (not directly used)
    ** @param output process step output (not directly used)
    ** @throws QException if an unrecoverable error occurs (per-table errors are caught)
    *******************************************************************************/
   @Override
   public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
   {
      ////////////////////////////////////////////////////
      // 1. Ensure index rows exist for all tables      //
      ////////////////////////////////////////////////////
      List<QuickSearchableTableConfig> discoveredTables = getDiscoveredTables();
      for(QuickSearchableTableConfig tableConfig : discoveredTables)
      {
         ensureIndexRowExists(tableConfig.getTableName(), tableConfig);
      }

      ////////////////////////////////////////////////////
      // 2. Query all enabled quickSearchIndex rows     //
      ////////////////////////////////////////////////////
      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(getConfig().getQuickSearchIndexTableName());
      queryInput.setFilter(new QQueryFilter()
         .withCriteria(new QFilterCriteria("enabled", QCriteriaOperator.EQUALS, true)));

      QueryOutput queryOutput = new QueryAction().execute(queryInput);
      List<QRecord> indexRecords = queryOutput.getRecords();

      if(indexRecords == null || indexRecords.isEmpty())
      {
         LOG.info("No enabled quickSearchIndex rows found; nothing to basepull");
         return;
      }

      ////////////////////////////////////////////////////
      // 3. Process each enabled index row              //
      ////////////////////////////////////////////////////
      for(QRecord indexRecord : indexRecords)
      {
         QuickSearchIndex index = new QuickSearchIndex()
            .withId(indexRecord.getValueInteger("id"))
            .withTableName(indexRecord.getValueString("tableName"))
            .withEnabled(indexRecord.getValueBoolean("enabled"))
            .withBasepullIntervalMinutes(indexRecord.getValueInteger("basepullIntervalMinutes"))
            .withBasepullTimestampField(indexRecord.getValueString("basepullTimestampField"))
            .withLastBasepullTime(indexRecord.getValueInstant("lastBasepullTime"));

         if(!isDueForBasepull(index))
         {
            LOG.info("Index not due for basepull; skipping",
               "tableName", index.getTableName(),
               "lastBasepullTime", index.getLastBasepullTime());
            continue;
         }

         try
         {
            basepullIndex(index);
         }
         catch(Exception e)
         {
            LOG.warn("Error during basepull for table; continuing with next table",
               e, "tableName", index.getTableName());
         }
      }
   }



   /*******************************************************************************
    ** Determine whether this index is due for a basepull run.
    **
    ** Returns true when lastBasepullTime is null (never run), or when the elapsed
    ** time since the last run has exceeded the configured interval.
    **
    ** @param index the QuickSearchIndex row to evaluate
    ** @return true if a basepull should be executed for this index
    *******************************************************************************/
   boolean isDueForBasepull(QuickSearchIndex index)
   {
      Instant lastBasepullTime = index.getLastBasepullTime();

      if(lastBasepullTime == null)
      {
         return (true);
      }

      Integer intervalMinutes = index.getBasepullIntervalMinutes();
      if(intervalMinutes == null)
      {
         return (true);
      }

      return (lastBasepullTime.plus(intervalMinutes, ChronoUnit.MINUTES).isBefore(Instant.now()));
   }



   /*******************************************************************************
    ** Execute a basepull indexing run for one QuickSearchIndex row.
    **
    ** Steps:
    ** 1. Resolve table config and client.
    ** 2. Create a run record (status=RUNNING).
    ** 3. Build an optional QQueryFilter using lastBasepullTime.
    ** 4. Paginate through the source table, building and indexing documents.
    ** 5. Update lastBasepullTime on the QuickSearchIndex row.
    ** 6. Complete the run record with final status and counts.
    **
    ** @param index the QuickSearchIndex row driving this run
    ** @throws QException if an error occurs that cannot be handled locally
    *******************************************************************************/
   void basepullIndex(QuickSearchIndex index) throws QException
   {
      String                   tableName   = index.getTableName();
      QuickSearchableTableConfig tableConfig = getTableConfig(tableName);
      QuickSearchOpenSearchClient client    = getClient();
      QuickSearchQBitConfig    config      = getConfig();

      QuickSearchIndexRun run = createRunRecord(index.getId(), "BASEPULL");

      Integer totalProcessed = 0;
      Integer totalIndexed   = 0;
      Integer totalErrors    = 0;
      String  errorMessage   = null;

      try
      {
         ///////////////////////////////////////////////////////////////////
         // Build filter: timestamp field > lastBasepullTime (if set)     //
         ///////////////////////////////////////////////////////////////////
         QQueryFilter filter = null;

         if(index.getLastBasepullTime() != null && tableConfig != null
            && tableConfig.getBasepullTimestampField() != null)
         {
            filter = new QQueryFilter()
               .withCriteria(new QFilterCriteria(
                  tableConfig.getBasepullTimestampField(),
                  QCriteriaOperator.GREATER_THAN,
                  index.getLastBasepullTime()));
         }

         Integer sourceBatchSize = config.getSourceBatchSize();
         Integer bulkBatchSize   = config.getBulkBatchSize();

         ///////////////////////////////////////////////////////////////////
         // Paginate through the source table                             //
         ///////////////////////////////////////////////////////////////////
         int offset = 0;

         while(true)
         {
            List<QRecord> batch = querySourceTableBatch(tableName, filter, sourceBatchSize, offset);

            if(batch.isEmpty())
            {
               break;
            }

            ////////////////////////////////////////////////////
            // Build documents for this batch                 //
            ////////////////////////////////////////////////////
            List<OpenSearchDocument> documents = new ArrayList<>();

            for(QRecord record : batch)
            {
               if(tableConfig != null)
               {
                  OpenSearchDocument doc = IndexingUtils.buildDocument(record, tableConfig);
                  documents.add(doc);
               }
            }

            documents.removeIf(doc -> doc == null);

            ////////////////////////////////////////////////////
            // Bulk-index the batch                           //
            ////////////////////////////////////////////////////
            if(!documents.isEmpty())
            {
               BulkIndexResult result = client.indexDocuments(documents, bulkBatchSize);
               totalIndexed   = totalIndexed + result.getSuccessCount();
               totalErrors    = totalErrors + result.getFailureCount();

               if(!result.getErrors().isEmpty() && errorMessage == null)
               {
                  errorMessage = result.getErrors().get(0);
               }
            }

            totalProcessed = totalProcessed + batch.size();
            offset         = offset + batch.size();
         }

         ////////////////////////////////////////////////////
         // Update lastBasepullTime on the index row       //
         // only when no indexing errors occurred           //
         ////////////////////////////////////////////////////
         if(totalErrors.equals(0))
         {
            QRecord updateRecord = new QRecord()
               .withValue("id", index.getId())
               .withValue("lastBasepullTime", Instant.now());

            UpdateInput updateInput = new UpdateInput();
            updateInput.setTableName(getConfig().getQuickSearchIndexTableName());
            updateInput.setRecords(List.of(updateRecord));

            new UpdateAction().execute(updateInput);
         }

         ////////////////////////////////////////////////////
         // Complete the run record                        //
         ////////////////////////////////////////////////////
         String finalStatus = (totalErrors > 0) ? "FAILED" : "COMPLETED";
         completeRunRecord(run, finalStatus, totalProcessed, totalIndexed, totalErrors, errorMessage);

         LOG.info("Basepull complete",
            "tableName", tableName,
            "totalProcessed", totalProcessed,
            "totalIndexed", totalIndexed,
            "totalErrors", totalErrors);
      }
      catch(Exception e)
      {
         LOG.warn("Basepull failed for table", e, "tableName", tableName);
         completeRunRecord(run, "FAILED", totalProcessed, totalIndexed, totalErrors, e.getMessage());
         throw e;
      }
   }

}

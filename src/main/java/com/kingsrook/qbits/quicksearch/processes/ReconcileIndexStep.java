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
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
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
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;
import com.kingsrook.qbits.quicksearch.opensearch.BulkIndexResult;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Process step that reconciles the search index with its source tables.
 **
 ** For each table (or only the one named by the optional "tableName" input):
 ** 1. Re-index every source record, paging in primary-key order so each record
 **    is read exactly once, even while other rows are inserted or deleted.
 ** 2. If every document indexed, refresh the index and delete the table's
 **    documents that this run did not re-index (indexedAt before the run
 **    started). Those have no source record: their deletes were missed.
 **
 ** Unlike FullReindexStep, the table's documents are never wiped first, so
 ** search keeps working during the run. When any document fails to index, the
 ** stale-document delete is skipped (it would remove that record's old
 ** document), and the run is marked FAILED.
 **
 ** The cutoff comes from this JVM's clock, as do real-time indexedAt values on
 ** this node; application nodes are assumed to keep their clocks in sync.
 *******************************************************************************/
public class ReconcileIndexStep extends AbstractIndexingStep
{
   private static final QLogger LOG = QLogger.getLogger(ReconcileIndexStep.class);

   public static final String RUN_TYPE = "RECONCILE";



   /*******************************************************************************
    ** Totals from reconciling one table.
    *******************************************************************************/
   private record ReconcileCounts(Integer recordsIndexed, Long documentsRemoved)
   {
   }



   /*******************************************************************************
    ** Reconcile every discovered table, or only the one named by "tableName".
    **
    ** Output values: recordsIndexed (Integer) and documentsRemoved (Long),
    ** summed across tables.
    **
    ** @throws QException if reconciling a table fails (its run is marked FAILED)
    *******************************************************************************/
   @Override
   public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
   {
      String tableNameFilter = input.getValueString("tableName");

      Integer recordsIndexed   = 0;
      Long    documentsRemoved = 0L;

      for(QuickSearchableTableConfig tableConfig : getDiscoveredTables())
      {
         if(tableNameFilter != null && !tableNameFilter.isBlank() && !tableNameFilter.equals(tableConfig.getTableName()))
         {
            continue;
         }

         ensureIndexRowExists(tableConfig.getTableName(), tableConfig);
         ReconcileCounts counts = reconcileTable(tableConfig);

         recordsIndexed   = recordsIndexed + counts.recordsIndexed();
         documentsRemoved = documentsRemoved + counts.documentsRemoved();
      }

      output.addValue("recordsIndexed", recordsIndexed);
      output.addValue("documentsRemoved", documentsRemoved);
   }



   /*******************************************************************************
    ** Reconcile one table, recording the run in quickSearchIndexRun.
    *******************************************************************************/
   private ReconcileCounts reconcileTable(QuickSearchableTableConfig tableConfig) throws QException
   {
      String                      tableName       = tableConfig.getTableName();
      String                      primaryKeyField = tableConfig.getPrimaryKeyField();
      QuickSearchOpenSearchClient client          = getClient();
      QuickSearchQBitConfig       config          = getConfig();

      Integer             indexId = queryIndexId(tableName);
      QuickSearchIndexRun run     = createRunRecord(indexId, RUN_TYPE);

      Integer totalProcessed = 0;
      Integer totalIndexed   = 0;
      Integer totalErrors    = 0;
      String  firstError     = null;

      try
      {
         ///////////////////////////////////////////////////////////////////////
         // every document this run writes has indexedAt at or after this     //
         // instant, so afterwards any older document has no source record    //
         ///////////////////////////////////////////////////////////////////////
         Instant reconcileStart = Instant.now();

         Serializable lastPrimaryKey = null;
         while(true)
         {
            List<QRecord> batch = querySourceBatchAfter(tableName, primaryKeyField, lastPrimaryKey, config.getSourceBatchSize());
            if(batch.isEmpty())
            {
               break;
            }

            List<OpenSearchDocument> documents = new ArrayList<>();
            for(QRecord record : batch)
            {
               OpenSearchDocument document = IndexingUtils.buildDocument(record, tableConfig);
               if(document != null)
               {
                  documents.add(document);
               }
            }

            if(!documents.isEmpty())
            {
               BulkIndexResult result = client.indexDocuments(documents, config.getBulkBatchSize());
               totalIndexed = totalIndexed + result.getSuccessCount();
               totalErrors  = totalErrors + result.getFailureCount();

               if(firstError == null && !result.getErrors().isEmpty())
               {
                  firstError = result.getErrors().get(0);
               }
            }

            totalProcessed = totalProcessed + batch.size();
            lastPrimaryKey = batch.get(batch.size() - 1).getValue(primaryKeyField);

            if(lastPrimaryKey == null)
            {
               //////////////////////////////////////////////////////////////
               // paging needs a key to continue after; without one, the    //
               // next query would start over and never finish              //
               //////////////////////////////////////////////////////////////
               throw new QException("Source record has no value in primary key field [" + primaryKeyField + "]");
            }
         }

         Long documentsRemoved = 0L;
         if(totalErrors.equals(0))
         {
            client.refreshIndex();
            documentsRemoved = client.deleteDocumentsIndexedBefore(tableName, reconcileStart);
            updateIndexRow(indexId, reconcileStart, totalProcessed);
         }
         else
         {
            LOG.warn("Reconcile had indexing errors; stale documents were not removed", logPair("tableName", tableName), logPair("errorCount", totalErrors));
         }

         String finalStatus = totalErrors.equals(0) ? "COMPLETED" : "FAILED";
         completeRunRecord(run, finalStatus, totalProcessed, totalIndexed, totalErrors, firstError);

         LOG.info("Reconcile complete", logPair("tableName", tableName), logPair("totalProcessed", totalProcessed),
            logPair("totalIndexed", totalIndexed), logPair("totalErrors", totalErrors), logPair("documentsRemoved", documentsRemoved));

         return (new ReconcileCounts(totalIndexed, documentsRemoved));
      }
      catch(Exception e)
      {
         LOG.warn("Reconcile failed", e, logPair("tableName", tableName));
         completeRunRecord(run, "FAILED", totalProcessed, totalIndexed, totalErrors, e.getMessage());
         throw new QException("Reconcile failed for table [" + tableName + "]: " + e.getMessage(), e);
      }
   }



   /*******************************************************************************
    ** Query the next batch of source records, in primary-key order, after the
    ** given key (or from the start when it is null).
    *******************************************************************************/
   private List<QRecord> querySourceBatchAfter(String tableName, String primaryKeyField, Serializable afterPrimaryKey, Integer limit) throws QException
   {
      QQueryFilter filter = new QQueryFilter()
         .withOrderBy(new QFilterOrderBy(primaryKeyField, true))
         .withLimit(limit);

      if(afterPrimaryKey != null)
      {
         filter.withCriteria(new QFilterCriteria(primaryKeyField, QCriteriaOperator.GREATER_THAN, afterPrimaryKey));
      }

      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(tableName);
      queryInput.setFilter(filter);

      return (CollectionUtils.nonNullList(new QueryAction().execute(queryInput).getRecords()));
   }



   /*******************************************************************************
    ** Look up the ID of the quickSearchIndex row for a table.
    *******************************************************************************/
   private Integer queryIndexId(String tableName) throws QException
   {
      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(getConfig().getQuickSearchIndexTableName());
      queryInput.setFilter(new QQueryFilter()
         .withCriteria(new QFilterCriteria("tableName", QCriteriaOperator.EQUALS, tableName)));

      List<QRecord> rows = new QueryAction().execute(queryInput).getRecords();
      if(CollectionUtils.nullSafeIsEmpty(rows))
      {
         throw new QException("No QuickSearchIndex row found for table: " + tableName);
      }

      return (rows.get(0).getValueInteger("id"));
   }



   /*******************************************************************************
    ** Record a successful reconcile on the quickSearchIndex row. Records changed
    ** after reconcileStart may not have been read, so basepull resumes from there.
    *******************************************************************************/
   private void updateIndexRow(Integer indexId, Instant reconcileStart, Integer recordCount) throws QException
   {
      UpdateInput updateInput = new UpdateInput();
      updateInput.setTableName(getConfig().getQuickSearchIndexTableName());
      updateInput.setRecords(List.of(new QRecord()
         .withValue("id", indexId)
         .withValue("lastFullReindexTime", Instant.now())
         .withValue("lastBasepullTime", reconcileStart)
         .withValue("recordCount", recordCount)));

      new UpdateAction().execute(updateInput);
   }

}

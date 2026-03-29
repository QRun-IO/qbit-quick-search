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
 ** Process step that performs a full reindex of one or all tables.
 **
 ** Deletes all existing OpenSearch documents for each target table, then
 ** re-fetches and re-indexes every record in batches. Updates the
 ** QuickSearchIndex row with lastFullReindexTime and recordCount when done.
 **
 ** An optional "tableName" input field narrows the run to a single table.
 ** When absent, all discovered tables are reindexed.
 *******************************************************************************/
public class FullReindexStep extends AbstractIndexingStep
{
   private static final QLogger LOG = QLogger.getLogger(FullReindexStep.class);



   /*******************************************************************************
    ** Run the full reindex step.
    **
    ** Reads the optional "tableName" field from the input.  When present, only
    ** that table is reindexed; otherwise every discovered table is processed.
    **
    ** @param input  the backend step input (may contain "tableName")
    ** @param output the backend step output (unused but required by interface)
    ** @throws QException if a fatal error occurs preventing any reindex
    *******************************************************************************/
   @Override
   public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
   {
      String tableNameFilter = input.getValueString("tableName");

      List<QuickSearchableTableConfig> tables = getDiscoveredTables();

      if(tableNameFilter != null && !tableNameFilter.isBlank())
      {
         List<QuickSearchableTableConfig> filtered = new ArrayList<>();
         for(QuickSearchableTableConfig tableConfig : tables)
         {
            if(tableNameFilter.equals(tableConfig.getTableName()))
            {
               filtered.add(tableConfig);
            }
         }
         tables = filtered;
      }

      for(QuickSearchableTableConfig tableConfig : tables)
      {
         String tableName = tableConfig.getTableName();

         ensureIndexRowExists(tableName, tableConfig);
         reindexTable(tableName, tableConfig);
      }
   }



   /*******************************************************************************
    ** Reindex all records for a single table.
    **
    ** Flow:
    ** 1. Obtain the QuickSearchIndex row for this table (to get its ID).
    ** 2. Create a FULL_REINDEX run record.
    ** 3. Delete all existing OpenSearch documents for the table.
    ** 4. Paginate through all source records and index them in batches.
    ** 5. Update lastFullReindexTime and recordCount on the QuickSearchIndex row.
    ** 6. Complete the run record with final stats.
    **
    ** On any exception, the run record is marked FAILED before re-throwing.
    **
    ** @param tableName   the name of the source table
    ** @param tableConfig the configuration for that table
    ** @throws QException if a fatal indexing error occurs
    *******************************************************************************/
   void reindexTable(String tableName, QuickSearchableTableConfig tableConfig) throws QException
   {
      QuickSearchOpenSearchClient client = getClient();
      QuickSearchQBitConfig config = getConfig();

      ////////////////////////////////////////////////////
      // Query for the index row to get its ID          //
      ////////////////////////////////////////////////////
      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(QuickSearchIndex.TABLE_NAME);
      queryInput.setFilter(new QQueryFilter()
         .withCriteria(new QFilterCriteria("tableName", QCriteriaOperator.EQUALS, tableName)));

      QueryOutput queryOutput = new QueryAction().execute(queryInput);

      if(queryOutput.getRecords().isEmpty())
      {
         throw new QException("No QuickSearchIndex row found for table: " + tableName);
      }

      QRecord indexRecord = queryOutput.getRecords().get(0);
      Integer indexId = indexRecord.getValueInteger("id");

      QuickSearchIndexRun run = createRunRecord(indexId, "FULL_REINDEX");

      Integer totalProcessed = 0;
      Integer totalIndexed = 0;
      Integer totalErrors = 0;

      try
      {
         ////////////////////////////////////////////////////
         // Delete existing documents for this table       //
         ////////////////////////////////////////////////////
         client.deleteDocumentsForTable(tableName);

         ////////////////////////////////////////////////////
         // Paginate through all records and index them    //
         ////////////////////////////////////////////////////
         Integer sourceBatchSize = config.getSourceBatchSize();
         Integer offset = 0;

         while(true)
         {
            List<QRecord> batch = querySourceTableBatch(tableName, new QQueryFilter(), sourceBatchSize, offset);

            if(batch.isEmpty())
            {
               break;
            }

            List<OpenSearchDocument> documents = new ArrayList<>();
            for(QRecord record : batch)
            {
               OpenSearchDocument doc = IndexingUtils.buildDocument(
                  record,
                  tableName,
                  tableConfig.getPrimaryKeyField(),
                  tableConfig.getSearchableFields(),
                  tableConfig.getFieldWeights(),
                  tableConfig.getFieldIncludeLabels() != null ? tableConfig.getFieldIncludeLabels() : java.util.Collections.emptyMap());
               documents.add(doc);
            }

            BulkIndexResult result = client.indexDocuments(documents, config.getBulkBatchSize());

            totalProcessed += batch.size();
            totalIndexed += result.getSuccessCount();
            totalErrors += result.getFailureCount();

            offset += batch.size();
         }

         ////////////////////////////////////////////////////
         // Update the QuickSearchIndex row               //
         ////////////////////////////////////////////////////
         QRecord updateRecord = new QRecord()
            .withValue("id", indexId)
            .withValue("lastFullReindexTime", Instant.now())
            .withValue("lastBasepullTime", Instant.now())
            .withValue("recordCount", totalProcessed);

         UpdateInput updateInput = new UpdateInput();
         updateInput.setTableName(QuickSearchIndex.TABLE_NAME);
         updateInput.setRecords(List.of(updateRecord));

         new UpdateAction().execute(updateInput);

         ////////////////////////////////////////////////////
         // Complete the run record as success             //
         ////////////////////////////////////////////////////
         completeRunRecord(run, "SUCCESS", totalProcessed, totalIndexed, totalErrors, null);

         LOG.info("Full reindex complete", "tableName", tableName, "totalProcessed", totalProcessed,
            "totalIndexed", totalIndexed, "totalErrors", totalErrors);
      }
      catch(Exception e)
      {
         LOG.warn("Full reindex failed", e, "tableName", tableName);
         completeRunRecord(run, "FAILED", totalProcessed, totalIndexed, totalErrors, e.getMessage());
         throw new QException("Full reindex failed for table [" + tableName + "]: " + e.getMessage(), e);
      }
   }

}

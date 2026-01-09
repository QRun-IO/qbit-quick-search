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
import java.util.Map;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Step that performs full reindexing of Quick Search indexes.
 *******************************************************************************/
public class FullReindexStep implements BackendStep
{
   private static final QLogger LOG = QLogger.getLogger(FullReindexStep.class);

   public static final String FIELD_TABLE_NAME = "tableName";
   public static final String FIELD_CONFIG     = "quickSearchConfig";



   /***************************************************************************
    ** Execute the full reindex.
    ***************************************************************************/
   @Override
   public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
   {
      String               targetTableName = input.getValueString(FIELD_TABLE_NAME);
      QuickSearchQBitConfig config          = (QuickSearchQBitConfig) input.getValue(FIELD_CONFIG);

      if(config == null)
      {
         throw new QException("QuickSearchQBitConfig is required");
      }

      List<QuickSearchIndex> indexes = loadIndexes(config, targetTableName);

      if(indexes.isEmpty())
      {
         LOG.info("No indexes found to reindex", logPair("targetTableName", targetTableName));
         return;
      }

      QuickSearchOpenSearchClient searchClient = new QuickSearchOpenSearchClient(config);

      try
      {
         for(QuickSearchIndex index : indexes)
         {
            reindexTable(config, searchClient, index);
         }
      }
      finally
      {
         searchClient.close();
      }
   }



   /***************************************************************************
    ** Load indexes to process.
    ***************************************************************************/
   private List<QuickSearchIndex> loadIndexes(QuickSearchQBitConfig config, String targetTableName) throws QException
   {
      String indexTableName = config.applyPrefix(QuickSearchIndex.TABLE_NAME);

      QQueryFilter filter = new QQueryFilter();
      if(StringUtils.hasContent(targetTableName))
      {
         filter.withCriteria(new QFilterCriteria("tableName", QCriteriaOperator.EQUALS, targetTableName));
      }

      QueryInput queryInput = new QueryInput()
         .withTableName(indexTableName)
         .withFilter(filter);

      QueryOutput queryOutput = new QueryAction().execute(queryInput);

      List<QuickSearchIndex> indexes = new ArrayList<>();
      for(QRecord record : queryOutput.getRecords())
      {
         indexes.add(mapRecordToIndex(record));
      }

      return indexes;
   }



   /***************************************************************************
    ** Reindex a single table.
    ***************************************************************************/
   private void reindexTable(QuickSearchQBitConfig config, QuickSearchOpenSearchClient searchClient, QuickSearchIndex index) throws QException
   {
      String tableName = index.getTableName();
      LOG.info("Starting full reindex", logPair("tableName", tableName));

      Instant startTime = Instant.now();
      QuickSearchIndexRun run = createRunRecord(config, index, "FULL", "RUNNING", startTime);

      int recordsProcessed = 0;
      int recordsIndexed = 0;
      String errorMessage = null;

      try
      {
         ///////////////////////////////////////
         // Delete existing documents for table //
         ///////////////////////////////////////
         searchClient.deleteDocumentsForTable(tableName);

         ///////////////////////////////////
         // Query all records from source //
         ///////////////////////////////////
         QTableMetaData table = QContext.getQInstance().getTable(tableName);
         if(table == null)
         {
            throw new QException("Table not found: " + tableName);
         }

         List<String> searchableFields = parseFieldsJson(index.getSearchableFieldsJson());

         QueryInput queryInput = new QueryInput().withTableName(tableName);
         QueryOutput queryOutput = new QueryAction().execute(queryInput);

         recordsProcessed = queryOutput.getRecords().size();

         /////////////////////////////////////
         // Build and index documents //
         /////////////////////////////////////
         List<OpenSearchDocument> documents = new ArrayList<>();
         for(QRecord record : queryOutput.getRecords())
         {
            OpenSearchDocument doc = IndexingUtils.buildDocument(record, table, searchableFields);
            if(StringUtils.hasContent(doc.getSearchableText()))
            {
               documents.add(doc);
            }
         }

         if(!documents.isEmpty())
         {
            searchClient.indexDocuments(documents);
            recordsIndexed = documents.size();
         }

         /////////////////////////////////////
         // Update index status //
         /////////////////////////////////////
         updateIndexStatus(config, index, startTime, recordsIndexed);

         LOG.info("Full reindex complete", logPair("tableName", tableName), logPair("recordsIndexed", recordsIndexed));
      }
      catch(Exception e)
      {
         errorMessage = e.getMessage();
         LOG.error("Full reindex failed", logPair("tableName", tableName), e);
         throw new QException("Full reindex failed for table: " + tableName, e);
      }
      finally
      {
         completeRunRecord(config, run, recordsProcessed, recordsIndexed, errorMessage);
      }
   }



   /***************************************************************************
    ** Parse searchable fields from JSON.
    ***************************************************************************/
   @SuppressWarnings("unchecked")
   private List<String> parseFieldsJson(String json)
   {
      if(!StringUtils.hasContent(json))
      {
         return new ArrayList<>();
      }

      try
      {
         return JsonUtils.toObject(json, List.class);
      }
      catch(Exception e)
      {
         LOG.warn("Failed to parse searchable fields JSON", logPair("json", json), e);
         return new ArrayList<>();
      }
   }



   /***************************************************************************
    ** Create a run record for tracking.
    ***************************************************************************/
   private QuickSearchIndexRun createRunRecord(QuickSearchQBitConfig config, QuickSearchIndex index, String runType, String status, Instant startTime) throws QException
   {
      String runTableName = config.applyPrefix(QuickSearchIndexRun.TABLE_NAME);

      QRecord runRecord = new QRecord()
         .withValue("quickSearchIndexId", index.getId())
         .withValue("runType", runType)
         .withValue("status", status)
         .withValue("startTime", startTime);

      InsertInput insertInput = new InsertInput()
         .withTableName(runTableName)
         .withRecords(List.of(runRecord));

      new InsertAction().execute(insertInput);

      QuickSearchIndexRun run = new QuickSearchIndexRun()
         .withQuickSearchIndexId(index.getId())
         .withRunType(runType)
         .withStatus(status)
         .withStartTime(startTime);
      return run;
   }



   /***************************************************************************
    ** Map a QRecord to a QuickSearchIndex entity.
    ***************************************************************************/
   private QuickSearchIndex mapRecordToIndex(QRecord record)
   {
      return new QuickSearchIndex()
         .withId(record.getValueInteger("id"))
         .withTableName(record.getValueString("tableName"))
         .withIsEnabled(record.getValueBoolean("isEnabled"))
         .withBasepullIntervalMinutes(record.getValueInteger("basepullIntervalMinutes"))
         .withBasepullTimestampField(record.getValueString("basepullTimestampField"))
         .withSearchableFieldsJson(record.getValueString("searchableFieldsJson"))
         .withLastFullIndexTime(record.getValueInstant("lastFullIndexTime"))
         .withLastBasepullTime(record.getValueInstant("lastBasepullTime"))
         .withIndexedRecordCount(record.getValueInteger("indexedRecordCount"));
   }



   /***************************************************************************
    ** Complete a run record with final status.
    ***************************************************************************/
   private void completeRunRecord(QuickSearchQBitConfig config, QuickSearchIndexRun run, int recordsProcessed, int recordsIndexed, String errorMessage) throws QException
   {
      String runTableName = config.applyPrefix(QuickSearchIndexRun.TABLE_NAME);

      String status = errorMessage == null ? "COMPLETE" : "ERROR";

      QRecord updateRecord = new QRecord()
         .withValue("id", run.getId())
         .withValue("status", status)
         .withValue("endTime", Instant.now())
         .withValue("recordsProcessed", recordsProcessed)
         .withValue("recordsIndexed", recordsIndexed)
         .withValue("errorMessage", errorMessage);

      UpdateInput updateInput = new UpdateInput()
         .withTableName(runTableName)
         .withRecords(List.of(updateRecord));

      new UpdateAction().execute(updateInput);
   }



   /***************************************************************************
    ** Update the index status after successful reindex.
    ***************************************************************************/
   private void updateIndexStatus(QuickSearchQBitConfig config, QuickSearchIndex index, Instant fullIndexTime, int recordCount) throws QException
   {
      String indexTableName = config.applyPrefix(QuickSearchIndex.TABLE_NAME);

      QRecord updateRecord = new QRecord()
         .withValue("id", index.getId())
         .withValue("lastFullIndexTime", fullIndexTime)
         .withValue("indexedRecordCount", recordCount)
         .withValue("modifyDate", Instant.now());

      UpdateInput updateInput = new UpdateInput()
         .withTableName(indexTableName)
         .withRecords(List.of(updateRecord));

      new UpdateAction().execute(updateInput);
   }

}

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
 ** Step that performs incremental basepull indexing of Quick Search indexes.
 *******************************************************************************/
public class BasepullIndexStep implements BackendStep
{
   private static final QLogger LOG = QLogger.getLogger(BasepullIndexStep.class);

   public static final String FIELD_CONFIG = "quickSearchConfig";



   /***************************************************************************
    ** Execute the basepull indexing.
    ***************************************************************************/
   @Override
   public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
   {
      QuickSearchQBitConfig config = (QuickSearchQBitConfig) input.getValue(FIELD_CONFIG);

      if(config == null)
      {
         throw new QException("QuickSearchQBitConfig is required");
      }

      List<QuickSearchIndex> indexes = loadEnabledIndexesDueForBasepull(config);

      if(indexes.isEmpty())
      {
         LOG.debug("No indexes due for basepull");
         return;
      }

      QuickSearchOpenSearchClient searchClient = new QuickSearchOpenSearchClient(config);

      try
      {
         for(QuickSearchIndex index : indexes)
         {
            try
            {
               basepullIndex(config, searchClient, index);
            }
            catch(Exception e)
            {
               LOG.warn("Basepull failed for index", logPair("tableName", index.getTableName()), e);
            }
         }
      }
      finally
      {
         searchClient.close();
      }
   }



   /***************************************************************************
    ** Load enabled indexes that are due for basepull.
    ***************************************************************************/
   private List<QuickSearchIndex> loadEnabledIndexesDueForBasepull(QuickSearchQBitConfig config) throws QException
   {
      String indexTableName = config.applyPrefix(QuickSearchIndex.TABLE_NAME);

      QueryInput queryInput = new QueryInput()
         .withTableName(indexTableName)
         .withFilter(new QQueryFilter()
            .withCriteria(new QFilterCriteria("isEnabled", QCriteriaOperator.EQUALS, true)));

      QueryOutput queryOutput = new QueryAction().execute(queryInput);

      Instant now = Instant.now();
      List<QuickSearchIndex> dueIndexes = new ArrayList<>();

      for(QRecord record : queryOutput.getRecords())
      {
         QuickSearchIndex index = mapRecordToIndex(record);
         if(isDueForBasepull(index, now))
         {
            dueIndexes.add(index);
         }
      }

      return dueIndexes;
   }



   /***************************************************************************
    ** Check if an index is due for basepull based on its interval.
    ***************************************************************************/
   private boolean isDueForBasepull(QuickSearchIndex index, Instant now)
   {
      if(index.getLastBasepullTime() == null)
      {
         return true;
      }

      Integer intervalMinutes = index.getBasepullIntervalMinutes();
      if(intervalMinutes == null || intervalMinutes <= 0)
      {
         intervalMinutes = 5;
      }

      Instant nextDue = index.getLastBasepullTime().plus(intervalMinutes, ChronoUnit.MINUTES);
      return now.isAfter(nextDue);
   }



   /***************************************************************************
    ** Perform basepull indexing for a single index.
    ***************************************************************************/
   private void basepullIndex(QuickSearchQBitConfig config, QuickSearchOpenSearchClient searchClient, QuickSearchIndex index) throws QException
   {
      String tableName = index.getTableName();
      LOG.debug("Starting basepull", logPair("tableName", tableName));

      Instant startTime = Instant.now();
      QuickSearchIndexRun run = createRunRecord(config, index, "BASEPULL", "RUNNING", startTime);

      int recordsProcessed = 0;
      int recordsIndexed = 0;
      String errorMessage = null;

      try
      {
         QTableMetaData table = QContext.getQInstance().getTable(tableName);
         if(table == null)
         {
            throw new QException("Table not found: " + tableName);
         }

         List<String> searchableFields = parseFieldsJson(index.getSearchableFieldsJson());
         String timestampField = index.getBasepullTimestampField();

         if(!StringUtils.hasContent(timestampField))
         {
            timestampField = "modifyDate";
         }

         /////////////////////////////////////////////
         // Build query for records since last run //
         /////////////////////////////////////////////
         QQueryFilter filter = new QQueryFilter();
         Instant lastRun = index.getLastBasepullTime();

         if(lastRun != null)
         {
            filter.withCriteria(new QFilterCriteria(timestampField, QCriteriaOperator.GREATER_THAN, lastRun));
         }
         filter.withCriteria(new QFilterCriteria(timestampField, QCriteriaOperator.LESS_THAN_OR_EQUALS, startTime));

         QueryInput queryInput = new QueryInput()
            .withTableName(tableName)
            .withFilter(filter);

         QueryOutput queryOutput = new QueryAction().execute(queryInput);
         recordsProcessed = queryOutput.getRecords().size();

         if(recordsProcessed > 0)
         {
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

            LOG.info("Basepull complete", logPair("tableName", tableName), logPair("recordsIndexed", recordsIndexed));
         }

         /////////////////////////////////////
         // Update index status //
         /////////////////////////////////////
         updateLastBasepullTime(config, index, startTime);
      }
      catch(Exception e)
      {
         errorMessage = e.getMessage();
         LOG.error("Basepull failed", logPair("tableName", tableName), e);
         throw new QException("Basepull failed for table: " + tableName, e);
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
    ** Update the last basepull time on the index.
    ***************************************************************************/
   private void updateLastBasepullTime(QuickSearchQBitConfig config, QuickSearchIndex index, Instant basepullTime) throws QException
   {
      String indexTableName = config.applyPrefix(QuickSearchIndex.TABLE_NAME);

      QRecord updateRecord = new QRecord()
         .withValue("id", index.getId())
         .withValue("lastBasepullTime", basepullTime)
         .withValue("modifyDate", Instant.now());

      UpdateInput updateInput = new UpdateInput()
         .withTableName(indexTableName)
         .withRecords(List.of(updateRecord));

      new UpdateAction().execute(updateInput);
   }

}

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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitContext;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;
import com.kingsrook.qbits.quicksearch.opensearch.BulkIndexResult;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Shared machinery for the indexing process steps: resolving the QBit's live
 ** state, operational-table bookkeeping (index rows, run records, drift
 ** detection, run-history purge), and primary-key-ordered paging of source
 ** tables with display values.
 *******************************************************************************/
public abstract class AbstractIndexingStep implements BackendStep
{
   private static final QLogger LOG = QLogger.getLogger(AbstractIndexingStep.class);

   public static final String STATUS_ACTIVE        = "ACTIVE";
   public static final String STATUS_NEEDS_REINDEX = "NEEDS_REINDEX";
   public static final String STATUS_REBUILDING    = "REBUILDING";
   public static final String RUN_COMPLETED        = "COMPLETED";
   public static final String RUN_FAILED           = "FAILED";
   public static final String RUN_RUNNING          = "RUNNING";



   /*******************************************************************************
    ** Counts from indexing one table.
    *******************************************************************************/
   public record IndexCounts(Integer processed, Integer indexed, Integer errors, Integer skipped, String firstError)
   {
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   protected QuickSearchQBitConfig getConfig() throws QException
   {
      QuickSearchQBitConfig config = QuickSearchQBitContext.getConfig();
      if(config == null)
      {
         throw new QException("Quick Search QBit is not initialized (no config found in the QInstance)");
      }
      return (config);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   protected QuickSearchOpenSearchClient getClient() throws QException
   {
      QuickSearchOpenSearchClient client = QuickSearchQBitContext.getClient();
      if(client == null)
      {
         throw new QException("Quick Search OpenSearch client is not available");
      }
      return (client);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   protected List<QuickSearchableTableConfig> getDiscoveredTables()
   {
      List<QuickSearchableTableConfig> tables = QuickSearchQBitContext.getDiscoveredTables();
      return (tables == null ? Collections.emptyList() : tables);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   protected QuickSearchableTableConfig getTableConfig(String tableName)
   {
      return (QuickSearchQBitContext.getTableConfig(tableName));
   }



   /*******************************************************************************
    ** Make sure a quickSearchIndex row exists for the table. The table's unique
    ** key on tableName makes a concurrent first insert fail, which is handled by
    ** re-querying.
    *******************************************************************************/
   protected void ensureIndexRowExists(String tableName, QuickSearchableTableConfig tableConfig) throws QException
   {
      if(queryIndexRow(tableName) != null)
      {
         return;
      }

      try
      {
         QRecord record = new QRecord()
            .withValue("tableName", tableName)
            .withValue("enabled", tableConfig.getEnabledByDefault())
            .withValue("basepullIntervalMinutes", tableConfig.getBasepullIntervalMinutes())
            .withValue("basepullTimestampField", tableConfig.getBasepullTimestampField())
            .withValue("searchableFieldsJson", buildSearchableFieldsJson(tableConfig))
            .withValue("realTimeErrorCount", 0)
            .withValue("status", STATUS_ACTIVE);

         InsertInput insertInput = new InsertInput();
         insertInput.setTableName(getConfig().getQuickSearchIndexTableName());
         insertInput.setRecords(List.of(record));
         InsertOutput insertOutput = new InsertAction().execute(insertInput);

         if(insertOutput.getRecords().isEmpty() || CollectionUtils.nullSafeHasContents(insertOutput.getRecords().get(0).getErrors()))
         {
            throw (new QException("Insert returned errors"));
         }
      }
      catch(Exception e)
      {
         if(queryIndexRow(tableName) == null)
         {
            throw new QException("Failed to create QuickSearchIndex row for table [" + tableName + "]", e);
         }
         LOG.debug("QuickSearchIndex row already exists (concurrent insert)", logPair("tableName", tableName));
      }
   }



   /*******************************************************************************
    ** The quickSearchIndex row for a table, or null.
    *******************************************************************************/
   protected QRecord queryIndexRow(String tableName) throws QException
   {
      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(getConfig().getQuickSearchIndexTableName());
      queryInput.setFilter(new QQueryFilter(new QFilterCriteria("tableName", QCriteriaOperator.EQUALS, tableName)));
      List<QRecord> rows = new QueryAction().execute(queryInput).getRecords();
      return (CollectionUtils.nullSafeIsEmpty(rows) ? null : rows.get(0));
   }



   /*******************************************************************************
    ** The id of the quickSearchIndex row for a table.
    *******************************************************************************/
   protected Integer queryIndexId(String tableName) throws QException
   {
      QRecord row = queryIndexRow(tableName);
      if(row == null)
      {
         throw new QException("No QuickSearchIndex row found for table: " + tableName);
      }
      return (row.getValueInteger("id"));
   }



   /*******************************************************************************
    ** Whether an index row is enabled (null counts as enabled).
    *******************************************************************************/
   protected static boolean isRowEnabled(QRecord row)
   {
      Boolean enabled = row == null ? null : row.getValueBoolean("enabled");
      return (enabled == null || enabled);
   }



   /*******************************************************************************
    ** Compare the configured fields with what the row was created with; mark
    ** the row NEEDS_REINDEX when they differ. Returns true when drift was found.
    ** A REBUILDING row is left alone: the full reindex in progress records the
    ** current fields, and the status tells the listener to capture deletes.
    *******************************************************************************/
   protected boolean detectDrift(QRecord row, QuickSearchableTableConfig tableConfig) throws QException
   {
      if(row == null || tableConfig == null)
      {
         return (false);
      }

      String current = buildSearchableFieldsJson(tableConfig);
      if(!isUnmarkedDrift(row, current))
      {
         return (hasDrift(row, current));
      }

      ///////////////////////////////////////////////////////////////////////////
      // the row may be a snapshot taken before a full reindex started or     //
      // finished; decide on a fresh read, so REBUILDING is kept and fields a //
      // reindex has since recorded are not flagged                           //
      ///////////////////////////////////////////////////////////////////////////
      QRecord latest = queryIndexRow(tableConfig.getTableName());
      if(latest == null || !isUnmarkedDrift(latest, current))
      {
         return (latest != null && hasDrift(latest, current));
      }

      // a status change in the moment between this read and the update below is not guarded
      LOG.warn("Searchable field configuration changed since the index row was created; marking NEEDS_REINDEX", logPair("tableName", tableConfig.getTableName()));
      updateIndexRow(latest.getValueInteger("id"), Map.of("status", STATUS_NEEDS_REINDEX));
      return (true);
   }



   /*******************************************************************************
    ** Whether the row records fields that differ from the configured ones.
    *******************************************************************************/
   private static boolean hasDrift(QRecord row, String current)
   {
      String recorded = row.getValueString("searchableFieldsJson");
      return (recorded != null && !recorded.equals(current));
   }



   /*******************************************************************************
    ** Whether the row has drifted and is not yet NEEDS_REINDEX or REBUILDING.
    *******************************************************************************/
   private static boolean isUnmarkedDrift(QRecord row, String current)
   {
      String status = row.getValueString("status");
      return (hasDrift(row, current) && !STATUS_NEEDS_REINDEX.equals(status) && !STATUS_REBUILDING.equals(status));
   }



   /*******************************************************************************
    ** JSON array describing a table's searchable fields, weights and labels.
    *******************************************************************************/
   protected static String buildSearchableFieldsJson(QuickSearchableTableConfig tableConfig)
   {
      if(tableConfig.getSearchableFields() == null || tableConfig.getSearchableFields().isEmpty())
      {
         return ("[]");
      }

      StringBuilder sb    = new StringBuilder("[");
      boolean       first = true;
      for(String fieldName : tableConfig.getSearchableFields())
      {
         if(!first)
         {
            sb.append(",");
         }
         first = false;

         Integer weight       = tableConfig.getFieldWeights() != null && tableConfig.getFieldWeights().containsKey(fieldName) ? tableConfig.getFieldWeights().get(fieldName) : 1;
         Boolean includeLabel = tableConfig.getFieldIncludeLabels() != null && tableConfig.getFieldIncludeLabels().containsKey(fieldName) ? tableConfig.getFieldIncludeLabels().get(fieldName) : false;
         String  escapedName  = fieldName.replace("\\", "\\\\").replace("\"", "\\\"");

         sb.append("{\"fieldName\":\"").append(escapedName).append("\",\"weight\":").append(weight).append(",\"includeLabel\":").append(includeLabel).append("}");
      }
      return (sb.append("]").toString());
   }



   /*******************************************************************************
    ** Insert a RUNNING run record and return it with its id.
    *******************************************************************************/
   protected QuickSearchIndexRun createRunRecord(Integer indexId, String runType) throws QException
   {
      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(getConfig().getQuickSearchIndexRunTableName());
      insertInput.setRecords(List.of(new QRecord()
         .withValue("quickSearchIndexId", indexId)
         .withValue("runType", runType)
         .withValue("status", RUN_RUNNING)
         .withValue("startTime", Instant.now())));

      InsertOutput insertOutput = new InsertAction().execute(insertInput);
      if(insertOutput.getRecords().isEmpty())
      {
         throw new QException("Failed to create quickSearchIndexRun record: no record returned from insert");
      }

      QRecord inserted = insertOutput.getRecords().get(0);
      return (new QuickSearchIndexRun()
         .withId(inserted.getValueInteger("id"))
         .withQuickSearchIndexId(inserted.getValueInteger("quickSearchIndexId"))
         .withRunType(inserted.getValueString("runType"))
         .withStatus(inserted.getValueString("status"))
         .withStartTime(inserted.getValueInstant("startTime")));
   }



   /*******************************************************************************
    ** Finish a run record and mirror its status onto the index row. Never
    ** throws, so it is safe from finally and catch blocks.
    *******************************************************************************/
   protected void completeRunRecord(QuickSearchIndexRun run, String status, Integer recordsProcessed, Integer recordsIndexed, Integer errorCount, String errorMessage)
   {
      run.withStatus(status);

      try
      {
         UpdateInput updateInput = new UpdateInput();
         updateInput.setTableName(getConfig().getQuickSearchIndexRunTableName());
         updateInput.setRecords(List.of(new QRecord()
            .withValue("id", run.getId())
            .withValue("status", status)
            .withValue("endTime", Instant.now())
            .withValue("recordsProcessed", recordsProcessed)
            .withValue("recordsIndexed", recordsIndexed)
            .withValue("errorCount", errorCount)
            .withValue("errorMessage", truncate(errorMessage, 4000))));
         new UpdateAction().execute(updateInput);

         if(run.getQuickSearchIndexId() != null)
         {
            Map<String, Serializable> values = new HashMap<>();
            values.put("lastRunStatus", run.getRunType() + " " + status);
            values.put("lastErrorMessage", truncate(errorMessage, 4000));
            updateIndexRow(run.getQuickSearchIndexId(), values);
         }
      }
      catch(Exception e)
      {
         LOG.warn("Failed to complete run record", e, logPair("runId", run.getId()), logPair("status", status));
      }
   }



   /*******************************************************************************
    ** Refresh a FULL_REINDEX run record's modifyDate: the heartbeat by which
    ** other nodes tell a full reindex that is still going from one that
    ** stopped (see FullReindexStep.recoverDeadRun). Nothing reads it for other
    ** run types. Best effort: a failure is logged and never stops the run (one
    ** judged stopped as a result fails safely before its swap).
    *******************************************************************************/
   protected void heartbeat(QuickSearchIndexRun run)
   {
      if(run == null || run.getId() == null || !FullReindexStep.RUN_TYPE.equals(run.getRunType()))
      {
         return;
      }

      try
      {
         UpdateInput updateInput = new UpdateInput();
         updateInput.setTableName(getConfig().getQuickSearchIndexRunTableName());
         updateInput.setRecords(List.of(new QRecord().withValue("id", run.getId()).withValue("modifyDate", Instant.now())));
         new UpdateAction().execute(updateInput);
      }
      catch(Exception e)
      {
         LOG.warn("Could not refresh the full reindex heartbeat", e, logPair("runId", run.getId()));
      }
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
    ** Update fields on a quickSearchIndex row.
    *******************************************************************************/
   protected void updateIndexRow(Integer indexId, Map<String, ? extends Serializable> values) throws QException
   {
      QRecord record = new QRecord().withValue("id", indexId);
      for(Map.Entry<String, ? extends Serializable> entry : values.entrySet())
      {
         record.setValue(entry.getKey(), entry.getValue());
      }

      UpdateInput updateInput = new UpdateInput();
      updateInput.setTableName(getConfig().getQuickSearchIndexTableName());
      updateInput.setRecords(List.of(record));
      new UpdateAction().execute(updateInput);
   }



   /*******************************************************************************
    ** Next page of source records in primary-key order after afterPrimaryKey
    ** (null starts at the beginning), with the extra criteria applied and
    ** display values generated.
    *******************************************************************************/
   protected List<QRecord> querySourceBatchAfter(String tableName, String primaryKeyField, Serializable afterPrimaryKey, List<QFilterCriteria> extraCriteria, Integer limit) throws QException
   {
      QQueryFilter filter = new QQueryFilter()
         .withOrderBy(new QFilterOrderBy(primaryKeyField, true))
         .withLimit(limit);

      if(afterPrimaryKey != null)
      {
         filter.withCriteria(new QFilterCriteria(primaryKeyField, QCriteriaOperator.GREATER_THAN, afterPrimaryKey));
      }
      for(QFilterCriteria criteria : CollectionUtils.nonNullList(extraCriteria))
      {
         filter.withCriteria(criteria);
      }

      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(tableName);
      queryInput.setFilter(filter);
      queryInput.setShouldGenerateDisplayValues(true);
      queryInput.setShouldTranslatePossibleValues(true);

      return (CollectionUtils.nonNullList(new QueryAction().execute(queryInput).getRecords()));
   }



   /*******************************************************************************
    ** Index every source record matching the criteria into targetIndex (null
    ** for the alias), paging by primary key so each record is read once even
    ** while rows are inserted or deleted. The run's heartbeat is refreshed
    ** after every page.
    *******************************************************************************/
   protected IndexCounts indexAllRecords(QuickSearchOpenSearchClient client, QuickSearchableTableConfig tableConfig, List<QFilterCriteria> extraCriteria, String targetIndex, QuickSearchIndexRun run) throws QException
   {
      QuickSearchQBitConfig config          = getConfig();
      String                tableName       = tableConfig.getTableName();
      String                primaryKeyField = tableConfig.getPrimaryKeyField() == null ? "id" : tableConfig.getPrimaryKeyField();

      int    processed  = 0;
      int    indexed    = 0;
      int    errors     = 0;
      int    skipped    = 0;
      String firstError = null;

      Serializable lastPrimaryKey = null;
      while(true)
      {
         List<QRecord> batch = querySourceBatchAfter(tableName, primaryKeyField, lastPrimaryKey, extraCriteria, config.getSourceBatchSize());
         if(batch.isEmpty())
         {
            break;
         }

         List<OpenSearchDocument> documents = new ArrayList<>();
         for(QRecord record : batch)
         {
            try
            {
               OpenSearchDocument document = IndexingUtils.buildDocument(record, tableConfig);
               if(document != null)
               {
                  documents.add(document);
               }
            }
            catch(Exception e)
            {
               errors++;
               if(firstError == null)
               {
                  firstError = "Could not build document for " + tableName + ":" + record.getValueString(primaryKeyField) + ": " + e.getMessage();
               }
            }
         }

         if(!documents.isEmpty())
         {
            BulkIndexResult result = targetIndex == null ? client.indexDocuments(documents, config.getBulkBatchSize()) : client.indexDocuments(targetIndex, documents, config.getBulkBatchSize());
            indexed += result.getSuccessCount();
            errors += result.getFailureCount();
            skipped += result.getSkippedCount();
            if(firstError == null && !result.getErrors().isEmpty())
            {
               firstError = result.getErrors().get(0);
            }
         }

         processed += batch.size();
         lastPrimaryKey = batch.get(batch.size() - 1).getValue(primaryKeyField);
         if(lastPrimaryKey == null)
         {
            throw new QException("Source record has no value in primary key field [" + primaryKeyField + "]");
         }

         heartbeat(run);
      }

      return (new IndexCounts(processed, indexed, errors, skipped, firstError));
   }



   /*******************************************************************************
    ** Delete run records older than runHistoryRetentionDays. Never throws.
    *******************************************************************************/
   protected void purgeOldRuns()
   {
      try
      {
         Integer retentionDays = getConfig().getRunHistoryRetentionDays();
         if(retentionDays == null || retentionDays <= 0)
         {
            return;
         }

         Instant    cutoff     = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
         QueryInput queryInput = new QueryInput();
         queryInput.setTableName(getConfig().getQuickSearchIndexRunTableName());
         queryInput.setFilter(new QQueryFilter(new QFilterCriteria("startTime", QCriteriaOperator.LESS_THAN, cutoff)).withLimit(5000));
         List<QRecord> old = new QueryAction().execute(queryInput).getRecords();
         if(CollectionUtils.nullSafeIsEmpty(old))
         {
            return;
         }

         List<Serializable> ids = new ArrayList<>();
         for(QRecord record : old)
         {
            ids.add(record.getValue("id"));
         }

         DeleteInput deleteInput = new DeleteInput();
         deleteInput.setTableName(getConfig().getQuickSearchIndexRunTableName());
         deleteInput.setPrimaryKeys(ids);
         new DeleteAction().execute(deleteInput);
         LOG.info("Purged old Quick Search run records", logPair("count", ids.size()), logPair("olderThanDays", retentionDays));
      }
      catch(Exception e)
      {
         LOG.warn("Failed to purge old run records", e);
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   protected static String truncate(String s, int max)
   {
      return (s == null || s.length() <= max ? s : s.substring(0, max));
   }

}

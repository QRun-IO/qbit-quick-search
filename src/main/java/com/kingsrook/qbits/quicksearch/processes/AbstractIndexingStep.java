/*
 * QBit Quick Search
 * Copyright (C) 2024-2025 QRun-IO, LLC
 * https://www.qrun.io | https://github.com/QRun-IO
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.kingsrook.qbits.quicksearch.processes;


import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitContext;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;


/*******************************************************************************
 ** Abstract base class for Quick Search indexing process steps.
 **
 ** Implements {@link BackendStep} but does NOT provide a run() implementation;
 ** that is left to concrete subclasses (BasepullIndexStep, FullReindexStep).
 **
 ** Provides protected helper methods for:
 ** - Accessing QBit config, OpenSearch client, and discovered tables
 ** - Ensuring quickSearchIndex rows exist for each table
 ** - Creating and completing quickSearchIndexRun records
 ** - Querying batches of records from source tables
 *******************************************************************************/
public abstract class AbstractIndexingStep implements BackendStep
{
   private static final QLogger LOG = QLogger.getLogger(AbstractIndexingStep.class);



   /*******************************************************************************
    ** Return the active QuickSearchQBitConfig from context.
    **
    ** @throws QException if config is null (QBit not initialized)
    *******************************************************************************/
   protected QuickSearchQBitConfig getConfig() throws QException
   {
      QuickSearchQBitConfig config = QuickSearchQBitContext.getConfig();
      if(config == null)
      {
         throw new QException("QuickSearchQBitConfig is not initialized in QuickSearchQBitContext");
      }
      return (config);
   }



   /*******************************************************************************
    ** Return the active OpenSearch client cast from context.
    **
    ** @throws QException if client is null (not initialized)
    *******************************************************************************/
   protected QuickSearchOpenSearchClient getClient() throws QException
   {
      QuickSearchOpenSearchClient client = QuickSearchQBitContext.getClient();
      if(client == null)
      {
         throw new QException("QuickSearchOpenSearchClient is not initialized in QuickSearchQBitContext");
      }
      return (client);
   }



   /*******************************************************************************
    ** Return the list of discovered table configs from context.
    **
    ** Returns an empty list if discoveredTables is null.
    *******************************************************************************/
   protected List<QuickSearchableTableConfig> getDiscoveredTables()
   {
      List<QuickSearchableTableConfig> tables = QuickSearchQBitContext.getDiscoveredTables();
      if(tables == null)
      {
         return (Collections.emptyList());
      }
      return (tables);
   }



   /*******************************************************************************
    ** Return the table config for the given table name from context.
    **
    ** Delegates to QuickSearchQBitContext.getTableConfig().
    ** Returns null if not found.
    *******************************************************************************/
   protected QuickSearchableTableConfig getTableConfig(String tableName)
   {
      return (QuickSearchQBitContext.getTableConfig(tableName));
   }



   /*******************************************************************************
    ** Ensure a quickSearchIndex row exists for the given table.
    **
    ** Queries the quickSearchIndex table for a row matching tableName.
    ** If no row is found, inserts one with:
    ** - tableName
    ** - enabled = tableConfig.getEnabledByDefault()
    ** - basepullIntervalMinutes from tableConfig
    ** - basepullTimestampField from tableConfig
    ** - searchableFieldsJson built from tableConfig's fields/weights/labels
    ** - status = "ACTIVE"
    **
    ** @param tableName   the name of the source table
    ** @param tableConfig the configuration for that table
    ** @throws QException if the query or insert fails
    *******************************************************************************/
   protected void ensureIndexRowExists(String tableName, QuickSearchableTableConfig tableConfig) throws QException
   {
      //////////////////////////////////////////////////////////////////////////////
      // Query for an existing index row                                          //
      //////////////////////////////////////////////////////////////////////////////
      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(QuickSearchIndex.TABLE_NAME);
      queryInput.setFilter(new QQueryFilter()
         .withCriteria(new QFilterCriteria("tableName", QCriteriaOperator.EQUALS, tableName)));

      QueryOutput queryOutput = new QueryAction().execute(queryInput);

      if(!queryOutput.getRecords().isEmpty())
      {
         return;
      }

      //////////////////////////////////////////////////////////////////////////////
      // Build and insert a new index row. Wrapped in try/catch to handle the   //
      // race condition where another thread inserts between our check and our  //
      // insert -- a duplicate key exception in that case is harmless.          //
      //////////////////////////////////////////////////////////////////////////////
      try
      {
         String searchableFieldsJson = buildSearchableFieldsJson(tableConfig);

         QRecord record = new QRecord()
            .withValue("tableName", tableName)
            .withValue("enabled", tableConfig.getEnabledByDefault())
            .withValue("basepullIntervalMinutes", tableConfig.getBasepullIntervalMinutes())
            .withValue("basepullTimestampField", tableConfig.getBasepullTimestampField())
            .withValue("searchableFieldsJson", searchableFieldsJson)
            .withValue("status", "ACTIVE");

         InsertInput insertInput = new InsertInput();
         insertInput.setTableName(QuickSearchIndex.TABLE_NAME);
         insertInput.setRecords(List.of(record));

         new InsertAction().execute(insertInput);
      }
      catch(Exception e)
      {
         LOG.info("Insert of index row may have hit a duplicate key (another thread likely created it); continuing",
            "tableName", tableName, "message", e.getMessage());
      }
   }



   /*******************************************************************************
    ** Build a JSON array string representing the searchable fields configuration
    ** for a table.
    **
    ** Each element looks like:
    ** {"fieldName":"name","weight":1,"includeLabel":false}
    **
    ** @param tableConfig the table configuration containing field lists/maps
    ** @return JSON string, e.g. [{"fieldName":"name","weight":1,"includeLabel":false}]
    *******************************************************************************/
   private String buildSearchableFieldsJson(QuickSearchableTableConfig tableConfig)
   {
      if(tableConfig.getSearchableFields() == null || tableConfig.getSearchableFields().isEmpty())
      {
         return ("[]");
      }

      StringBuilder sb = new StringBuilder("[");
      boolean first = true;

      for(String fieldName : tableConfig.getSearchableFields())
      {
         if(!first)
         {
            sb.append(",");
         }
         first = false;

         Integer weight = 1;
         if(tableConfig.getFieldWeights() != null && tableConfig.getFieldWeights().containsKey(fieldName))
         {
            weight = tableConfig.getFieldWeights().get(fieldName);
         }

         Boolean includeLabel = false;
         if(tableConfig.getFieldIncludeLabels() != null && tableConfig.getFieldIncludeLabels().containsKey(fieldName))
         {
            includeLabel = tableConfig.getFieldIncludeLabels().get(fieldName);
         }

         String escapedName = fieldName.replace("\\", "\\\\").replace("\"", "\\\"");

         sb.append("{\"fieldName\":\"").append(escapedName).append("\"")
           .append(",\"weight\":").append(weight)
           .append(",\"includeLabel\":").append(includeLabel)
           .append("}");
      }

      sb.append("]");
      return (sb.toString());
   }



   /*******************************************************************************
    ** Create a quickSearchIndexRun record and return it with its generated ID.
    **
    ** Inserts a new record with status=RUNNING and startTime=now, then reads
    ** back the inserted record to capture the auto-generated primary key.
    **
    ** @param indexId  the ID of the parent quickSearchIndex row
    ** @param runType  the type label for this run (e.g. "BASEPULL", "FULL_REINDEX")
    ** @return a QuickSearchIndexRun entity with id populated
    ** @throws QException if insert fails or no record is returned
    *******************************************************************************/
   protected QuickSearchIndexRun createRunRecord(Integer indexId, String runType) throws QException
   {
      QRecord record = new QRecord()
         .withValue("quickSearchIndexId", indexId)
         .withValue("runType", runType)
         .withValue("status", "RUNNING")
         .withValue("startTime", Instant.now());

      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(QuickSearchIndexRun.TABLE_NAME);
      insertInput.setRecords(List.of(record));

      InsertOutput insertOutput = new InsertAction().execute(insertInput);

      if(insertOutput.getRecords().isEmpty())
      {
         throw new QException("Failed to create quickSearchIndexRun record: no record returned from insert");
      }

      QRecord insertedRecord = insertOutput.getRecords().get(0);

      QuickSearchIndexRun run = new QuickSearchIndexRun()
         .withId(insertedRecord.getValueInteger("id"))
         .withQuickSearchIndexId(insertedRecord.getValueInteger("quickSearchIndexId"))
         .withRunType(insertedRecord.getValueString("runType"))
         .withStatus(insertedRecord.getValueString("status"))
         .withStartTime(insertedRecord.getValueInstant("startTime"));
      return (run);
   }



   /*******************************************************************************
    ** Mark a quickSearchIndexRun as complete by updating its status, endTime,
    ** record counts, and error information.
    **
    ** Wrapped in try/catch so that failures to update the run record do not
    ** mask original exceptions when called from finally blocks.
    **
    ** @param run              the run record to update
    ** @param status           the final status (e.g. "SUCCESS", "ERROR")
    ** @param recordsProcessed number of records examined
    ** @param recordsIndexed   number of records successfully indexed
    ** @param errorCount       number of errors encountered
    ** @param errorMessage     summary error message, or null
    *******************************************************************************/
   protected void completeRunRecord(QuickSearchIndexRun run, String status, Integer recordsProcessed,
      Integer recordsIndexed, Integer errorCount, String errorMessage)
   {
      try
      {
         QRecord record = new QRecord()
            .withValue("id", run.getId())
            .withValue("status", status)
            .withValue("endTime", Instant.now())
            .withValue("recordsProcessed", recordsProcessed)
            .withValue("recordsIndexed", recordsIndexed)
            .withValue("errorCount", errorCount)
            .withValue("errorMessage", errorMessage);

         UpdateInput updateInput = new UpdateInput();
         updateInput.setTableName(QuickSearchIndexRun.TABLE_NAME);
         updateInput.setRecords(List.of(record));

         new UpdateAction().execute(updateInput);
      }
      catch(Exception e)
      {
         LOG.warn("Failed to complete run record", e, "runId", run.getId(), "status", status);
      }
   }



   /*******************************************************************************
    ** Query a batch of records from the given source table.
    **
    ** @param tableName the name of the source table to query
    ** @param filter    the filter to apply (may be null for no filtering)
    ** @param limit     maximum number of records to return
    ** @param offset    number of records to skip (for pagination)
    ** @return list of QRecord from the query
    ** @throws QException if the query fails
    *******************************************************************************/
   protected List<QRecord> querySourceTableBatch(String tableName, QQueryFilter filter, Integer limit, Integer offset) throws QException
   {
      QQueryFilter effectiveFilter = new QQueryFilter();
      if(filter != null && filter.getCriteria() != null)
      {
         for(QFilterCriteria criteria : filter.getCriteria())
         {
            effectiveFilter.withCriteria(criteria);
         }
      }
      effectiveFilter.setLimit(limit);
      effectiveFilter.setSkip(offset);

      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(tableName);
      queryInput.setFilter(effectiveFilter);

      QueryOutput queryOutput = new QueryAction().execute(queryInput);

      List<QRecord> records = queryOutput.getRecords();
      if(records == null)
      {
         return (new ArrayList<>());
      }

      return (records);
   }

}

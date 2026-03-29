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

package com.kingsrook.qbits.quicksearch.publisher;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import com.kingsrook.qbits.quicksearch.processes.IndexingUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Synchronous implementation of IndexEventPublisher that processes events
 ** immediately against the OpenSearch client.
 **
 ** Index events are grouped by table, converted to OpenSearchDocument instances
 ** using IndexingUtils, and bulk-indexed. Delete events are processed
 ** individually.
 *******************************************************************************/
public class SynchronousIndexEventPublisher implements IndexEventPublisher
{
   private static final QLogger LOG = QLogger.getLogger(SynchronousIndexEventPublisher.class);

   private final QuickSearchOpenSearchClient        client;
   private final Map<String, QuickSearchableTableConfig> tableConfigsByName;
   private final Integer                             bulkBatchSize;



   /*******************************************************************************
    ** Constructor.
    **
    ** @param client       the OpenSearch client to delegate operations to
    ** @param tableConfigs the list of searchable table configurations
    ** @param bulkBatchSize number of documents per bulk request
    *******************************************************************************/
   public SynchronousIndexEventPublisher(QuickSearchOpenSearchClient client, List<QuickSearchableTableConfig> tableConfigs, Integer bulkBatchSize)
   {
      this.client = client;
      this.bulkBatchSize = bulkBatchSize;
      this.tableConfigsByName = new HashMap<>();

      if(tableConfigs != null)
      {
         for(QuickSearchableTableConfig config : tableConfigs)
         {
            tableConfigsByName.put(config.getTableName(), config);
         }
      }
   }



   /*******************************************************************************
    ** Publish index events synchronously by building documents and bulk-indexing
    ** them via the OpenSearch client.
    *******************************************************************************/
   @Override
   public void publishIndexEvents(List<IndexEvent> events) throws QException
   {
      if(events == null || events.isEmpty())
      {
         return;
      }

      /////////////////////////////////////////////////////////////////////////
      // group events by table name so we can look up configs per table once //
      /////////////////////////////////////////////////////////////////////////
      Map<String, List<IndexEvent>> eventsByTable = new HashMap<>();
      for(IndexEvent event : events)
      {
         eventsByTable.computeIfAbsent(event.getTableName(), k -> new ArrayList<>()).add(event);
      }

      List<OpenSearchDocument> allDocuments = new ArrayList<>();

      for(Map.Entry<String, List<IndexEvent>> entry : eventsByTable.entrySet())
      {
         String tableName = entry.getKey();
         List<IndexEvent> tableEvents = entry.getValue();

         QuickSearchableTableConfig tableConfig = tableConfigsByName.get(tableName);
         if(tableConfig == null)
         {
            LOG.warn("No QuickSearchableTableConfig found for table, skipping", logPair("tableName", tableName));
            continue;
         }

         String primaryKeyField = tableConfig.getPrimaryKeyField();
         if(primaryKeyField == null)
         {
            primaryKeyField = "id";
         }

         for(IndexEvent event : tableEvents)
         {
            OpenSearchDocument document = IndexingUtils.buildDocument(
               event.getRecord(),
               event.getTableName(),
               primaryKeyField,
               tableConfig.getSearchableFields(),
               tableConfig.getFieldWeights(),
               tableConfig.getFieldIncludeLabels());

            allDocuments.add(document);
         }
      }

      if(!allDocuments.isEmpty())
      {
         client.indexDocuments(allDocuments, bulkBatchSize);
      }
   }



   /*******************************************************************************
    ** Publish delete events synchronously by calling deleteDocument for each event.
    *******************************************************************************/
   @Override
   public void publishDeleteEvents(List<IndexEvent> events) throws QException
   {
      if(events == null || events.isEmpty())
      {
         return;
      }

      for(IndexEvent event : events)
      {
         client.deleteDocument(event.getTableName(), event.getRecordId());
      }
   }



   /*******************************************************************************
    ** Close by delegating to the underlying client.
    *******************************************************************************/
   @Override
   public void close() throws QException
   {
      client.close();
   }

}

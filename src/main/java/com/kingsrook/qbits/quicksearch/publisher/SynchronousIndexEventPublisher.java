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

package com.kingsrook.qbits.quicksearch.publisher;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.opensearch.BulkIndexResult;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import com.kingsrook.qbits.quicksearch.processes.IndexingUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Synchronous implementation of IndexEventPublisher that processes events
 ** immediately against the OpenSearch client.
 **
 ** Index events are grouped by table, converted to OpenSearchDocument instances
 ** using IndexingUtils, and bulk-indexed. Delete events are bulk-deleted.
 ** Either way, per-document failures are raised as a QException.
 *******************************************************************************/
public class SynchronousIndexEventPublisher implements IndexEventPublisher
{
   private static final QLogger LOG = QLogger.getLogger(SynchronousIndexEventPublisher.class);

   private static final Integer MAX_ERRORS_IN_MESSAGE = 10;

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

         for(IndexEvent event : tableEvents)
         {
            OpenSearchDocument document = IndexingUtils.buildDocument(event.getRecord(), tableConfig);
            if(document != null)
            {
               allDocuments.add(document);
            }
         }
      }

      if(!allDocuments.isEmpty())
      {
         BulkIndexResult result = client.indexDocuments(allDocuments, bulkBatchSize);
         throwIfAnyFailed(result, "index", allDocuments.size());
      }
   }



   /*******************************************************************************
    ** Publish delete events synchronously as one bulk delete, so every document
    ** is attempted even when some fail. Throws if any document failed.
    *******************************************************************************/
   @Override
   public void publishDeleteEvents(List<IndexEvent> events) throws QException
   {
      if(events == null || events.isEmpty())
      {
         return;
      }

      List<String> documentIds = new ArrayList<>();
      for(IndexEvent event : events)
      {
         documentIds.add(OpenSearchDocument.buildDocumentId(event.getTableName(), event.getRecordId()));
      }

      BulkIndexResult result = client.deleteDocuments(documentIds, bulkBatchSize);
      throwIfAnyFailed(result, "delete", documentIds.size());
   }



   /*******************************************************************************
    ** Throw a QException naming the failed documents, if any bulk item failed,
    ** so failures reach the caller instead of leaving the index silently stale.
    *******************************************************************************/
   private void throwIfAnyFailed(BulkIndexResult result, String operationName, Integer attemptedCount) throws QException
   {
      if(result == null || Boolean.TRUE.equals(result.isFullySuccessful()))
      {
         return;
      }

      List<String> errors      = result.getErrors();
      List<String> errorsShown = errors.subList(0, Math.min(errors.size(), MAX_ERRORS_IN_MESSAGE));
      throw (new QException("Failed to " + operationName + " " + result.getFailureCount() + " of " + attemptedCount
         + " search documents: " + String.join("; ", errorsShown)));
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

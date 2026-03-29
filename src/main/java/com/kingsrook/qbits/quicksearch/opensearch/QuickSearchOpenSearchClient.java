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

package com.kingsrook.qbits.quicksearch.opensearch;


import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.MultiMatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.opensearch.client.opensearch.core.search.Highlight;
import org.opensearch.client.opensearch.core.search.HighlightField;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5Transport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;


/*******************************************************************************
 ** OpenSearch client wrapper for the Quick Search QBit.
 **
 ** Manages the lifecycle of an OpenSearchClient with optional SSL and basic-auth
 ** support. Provides helpers for index creation with edge-ngram mappings, single
 ** and bulk document indexing, deletion, and boosted multi-match search.
 *******************************************************************************/
public class QuickSearchOpenSearchClient implements Closeable
{
   private static final QLogger LOG = QLogger.getLogger(QuickSearchOpenSearchClient.class);

   private final OpenSearchClient          client;
   private final ApacheHttpClient5Transport transport;
   private final String                    indexName;
   private       Boolean                   closed = false;



   /*******************************************************************************
    ** Construct a new client from the provided config.
    **
    ** Creates an ApacheHttpClient5Transport pointed at the configured host/port.
    ** When both username and password are non-null, a BasicCredentialsProvider
    ** is configured for HTTP basic authentication.  A JavaTimeModule-aware
    ** ObjectMapper is used for JSON serialization.
    **
    ** @param config the QBit configuration supplying connection details
    ** @throws QException if transport construction fails
    *******************************************************************************/
   public QuickSearchOpenSearchClient(QuickSearchQBitConfig config) throws QException
   {
      try
      {
         String   scheme   = Boolean.TRUE.equals(config.getUseSsl()) ? "https" : "http";
         HttpHost httpHost = new HttpHost(scheme, config.getOpensearchHost(), config.getOpensearchPort());

         ObjectMapper objectMapper = new ObjectMapper();
         objectMapper.registerModule(new JavaTimeModule());
         objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

         ApacheHttpClient5TransportBuilder builder = ApacheHttpClient5TransportBuilder
            .builder(httpHost)
            .setMapper(new JacksonJsonpMapper(objectMapper));

         String username = config.getOpensearchUsername();
         String password = config.getOpensearchPassword();

         if(username != null && password != null)
         {
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
               new AuthScope(httpHost),
               new UsernamePasswordCredentials(username, password.toCharArray()));

            builder.setHttpClientConfigCallback(httpClientBuilder ->
               httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
         }

         transport = builder.build();
         client    = new OpenSearchClient(transport);
         indexName = config.getOpensearchIndexName();
      }
      catch(Exception e)
      {
         throw new QException("Failed to create OpenSearch transport: " + e.getMessage(), e);
      }
   }



   /*******************************************************************************
    ** Ensure the index exists, creating it with explicit mappings if needed.
    **
    ** Uses a custom edge-ngram analyzer (quick_search_analyzer) with min=2 max=20.
    ** Field mappings: sourceTable and recordId as keyword; recordLabel as text;
    ** searchableText as text with the custom analyzer; indexedAt as date;
    ** fieldValues as a dynamic object.
    **
    ** @throws QException if the OpenSearch call fails
    *******************************************************************************/
   public void ensureIndexExists() throws QException
   {
      try
      {
         boolean exists = client.indices().exists(ExistsRequest.of(r -> r.index(indexName))).value();

         if(exists)
         {
            LOG.info("OpenSearch index already exists", "indexName", indexName);
            return;
         }

         client.indices().create(CreateIndexRequest.of(r -> r
            .index(indexName)
            .settings(s -> s
               .analysis(a -> a
                  .analyzer("quick_search_analyzer", an -> an
                     .custom(c -> c
                        .tokenizer("standard")
                        .filter("lowercase", "edge_ngram_filter")))
                  .filter("edge_ngram_filter", f -> f
                     .definition(d -> d
                        .edgeNgram(en -> en
                           .minGram(2)
                           .maxGram(20))))))
            .mappings(m -> m
               .properties("sourceTable", p -> p.keyword(k -> k))
               .properties("recordId", p -> p.keyword(k -> k))
               .properties("recordLabel", p -> p.text(t -> t))
               .properties("searchableText", p -> p.text(t -> t.analyzer("quick_search_analyzer")))
               .properties("indexedAt", p -> p.date(d -> d))
               .properties("fieldValues", p -> p.object(o -> o)))));


         LOG.info("Created OpenSearch index", "indexName", indexName);
      }
      catch(IOException e)
      {
         throw new QException("Failed to ensure index exists [" + indexName + "]: " + e.getMessage(), e);
      }
   }



   /*******************************************************************************
    ** Index a single document using its composite document ID as the _id.
    **
    ** @param doc the document to index
    ** @throws QException if the OpenSearch call fails
    *******************************************************************************/
   public void indexDocument(OpenSearchDocument doc) throws QException
   {
      try
      {
         client.index(IndexRequest.of(r -> r
            .index(indexName)
            .id(doc.getDocumentId())
            .document(doc)));
      }
      catch(IOException e)
      {
         throw new QException("Failed to index document [" + doc.getDocumentId() + "]: " + e.getMessage(), e);
      }
   }



   /*******************************************************************************
    ** Bulk-index a list of documents in batches of at most batchSize.
    **
    ** An empty or null list is a no-op that returns an empty BulkIndexResult.
    ** Documents within each batch are collected into a BulkRequest. Per-item
    ** errors are recorded via BulkIndexResult.addFailure(); successes via
    ** addSuccess().
    **
    ** @param docs      the documents to index; null is treated as empty
    ** @param batchSize maximum number of documents per bulk request
    ** @return accumulated result across all batches
    ** @throws QException if any batch request fails at the transport level
    *******************************************************************************/
   public BulkIndexResult indexDocuments(List<OpenSearchDocument> docs, int batchSize) throws QException
   {
      BulkIndexResult result = new BulkIndexResult();

      if(docs == null || docs.isEmpty())
      {
         return (result);
      }

      int total = docs.size();

      for(int start = 0; start < total; start += batchSize)
      {
         int                      end   = Math.min(start + batchSize, total);
         List<OpenSearchDocument> batch = docs.subList(start, end);

         List<BulkOperation> operations = new ArrayList<>();

         for(OpenSearchDocument doc : batch)
         {
            String docId = doc.getDocumentId();

            operations.add(BulkOperation.of(o -> o
               .index(IndexOperation.of(i -> i
                  .index(indexName)
                  .id(docId)
                  .document(doc)))));
         }

         BulkRequest bulkRequest = BulkRequest.of(r -> r.operations(operations));

         try
         {
            BulkResponse response = client.bulk(bulkRequest);

            for(BulkResponseItem item : response.items())
            {
               if(item.error() != null)
               {
                  result.addFailure(item.id() + ": " + item.error().reason());
               }
               else
               {
                  result.addSuccess();
               }
            }
         }
         catch(IOException e)
         {
            throw new QException("Bulk index request failed: " + e.getMessage(), e);
         }
      }

      return (result);
   }



   /*******************************************************************************
    ** Delete a single document identified by sourceTable and recordId.
    **
    ** @param sourceTable the source table component of the document ID
    ** @param recordId    the record ID component of the document ID
    ** @throws QException if the OpenSearch call fails
    *******************************************************************************/
   public void deleteDocument(String sourceTable, String recordId) throws QException
   {
      String documentId = sourceTable + ":" + recordId;

      try
      {
         client.delete(DeleteRequest.of(r -> r
            .index(indexName)
            .id(documentId)));
      }
      catch(IOException e)
      {
         throw new QException("Failed to delete document [" + documentId + "]: " + e.getMessage(), e);
      }
   }



   /*******************************************************************************
    ** Delete all documents whose sourceTable field matches the given table name.
    **
    ** @param sourceTable the table name to delete all documents for
    ** @throws QException if the OpenSearch call fails
    *******************************************************************************/
   public void deleteDocumentsForTable(String sourceTable) throws QException
   {
      try
      {
         client.deleteByQuery(DeleteByQueryRequest.of(r -> r
            .index(indexName)
            .query(Query.of(q -> q
               .term(TermQuery.of(t -> t
                  .field("sourceTable")
                  .value(FieldValue.of(sourceTable))))))));
      }
      catch(IOException e)
      {
         throw new QException("Failed to delete documents for table [" + sourceTable + "]: " + e.getMessage(), e);
      }
   }



   /*******************************************************************************
    ** Execute a boosted multi-match search across the index.
    **
    ** Builds a bool query with:
    ** - a must multi_match on searchableText
    ** - per-table should clauses on fieldValues.{fieldName} using configured weights
    ** An optional term filter on sourceTable is applied when tableName is non-null.
    ** Pagination via from/size. Highlights on searchableText.
    **
    ** @param searchTerm   the user-supplied search string
    ** @param tableName    when non-null, restricts results to this source table
    ** @param limit        maximum number of hits to return
    ** @param offset       number of hits to skip (for pagination)
    ** @param tableConfigs per-table config providing searchable fields and weights
    ** @return raw SearchResponse from OpenSearch
    ** @throws QException if the OpenSearch call fails
    *******************************************************************************/
   public SearchResponse<OpenSearchDocument> search(String searchTerm, String tableName, int limit, int offset, List<QuickSearchableTableConfig> tableConfigs) throws QException
   {
      try
      {
         List<Query> shouldClauses = buildFieldBoostQueries(searchTerm, tableConfigs);

         Query mustQuery = Query.of(q -> q
            .multiMatch(MultiMatchQuery.of(mm -> mm
               .query(searchTerm)
               .fields("searchableText"))));

         BoolQuery.Builder boolBuilder = new BoolQuery.Builder()
            .must(mustQuery)
            .should(shouldClauses);

         if(tableName != null)
         {
            boolBuilder.filter(Query.of(q -> q
               .term(TermQuery.of(t -> t
                  .field("sourceTable")
                  .value(FieldValue.of(tableName))))));
         }

         Query finalQuery = Query.of(q -> q.bool(boolBuilder.build()));

         Highlight highlight = Highlight.of(h -> h
            .fields("searchableText", HighlightField.of(hf -> hf)));

         int capturedOffset = offset;
         int capturedLimit  = limit;

         SearchRequest request = SearchRequest.of(r -> r
            .index(indexName)
            .query(finalQuery)
            .highlight(highlight)
            .from(capturedOffset)
            .size(capturedLimit));

         return (client.search(request, OpenSearchDocument.class));
      }
      catch(IOException e)
      {
         throw new QException("Search failed for term [" + searchTerm + "]: " + e.getMessage(), e);
      }
   }



   /*******************************************************************************
    ** Build should-clause boost queries from tableConfigs field weights.
    **
    ** For each table config, emits a multi-match query for each field in
    ** fieldWeights (if present) or searchableFields otherwise, targeting
    ** fieldValues.{fieldName} with the configured boost.
    **
    ** @param searchTerm   the search string to match
    ** @param tableConfigs the per-table configuration
    ** @return list of boost queries, may be empty
    *******************************************************************************/
   private List<Query> buildFieldBoostQueries(String searchTerm, List<QuickSearchableTableConfig> tableConfigs)
   {
      List<Query> queries = new ArrayList<>();

      if(tableConfigs == null)
      {
         return (queries);
      }

      for(QuickSearchableTableConfig tableConfig : tableConfigs)
      {
         if(tableConfig.getFieldWeights() != null)
         {
            for(Map.Entry<String, Integer> entry : tableConfig.getFieldWeights().entrySet())
            {
               String fieldPath  = "fieldValues." + entry.getKey();
               double boostValue = entry.getValue().doubleValue();

               queries.add(Query.of(q -> q
                  .multiMatch(MultiMatchQuery.of(mm -> mm
                     .query(searchTerm)
                     .fields(fieldPath)
                     .boost((float) boostValue)))));
            }
         }
         else if(tableConfig.getSearchableFields() != null)
         {
            for(String field : tableConfig.getSearchableFields())
            {
               String fieldPath = "fieldValues." + field;

               queries.add(Query.of(q -> q
                  .multiMatch(MultiMatchQuery.of(mm -> mm
                     .query(searchTerm)
                     .fields(fieldPath)))));
            }
         }
      }

      return (queries);
   }



   /*******************************************************************************
    ** Force a refresh of the OpenSearch index, making all indexed documents
    ** searchable immediately. Primarily used in tests.
    **
    ** @throws QException if the refresh call fails
    *******************************************************************************/
   public void refreshIndex() throws QException
   {
      try
      {
         client.indices().refresh(r -> r.index(indexName));
      }
      catch(IOException e)
      {
         throw new QException("Failed to refresh index [" + indexName + "]: " + e.getMessage(), e);
      }
   }



   /*******************************************************************************
    ** Close the underlying transport.
    **
    ** Safe to call multiple times; subsequent calls after the first are no-ops.
    ** Exceptions during close are logged but not re-thrown.
    *******************************************************************************/
   public void close()
   {
      if(Boolean.TRUE.equals(closed))
      {
         return;
      }

      closed = true;

      try
      {
         transport.close();
      }
      catch(Exception e)
      {
         LOG.warn("Error closing OpenSearch transport", e);
      }
   }

}

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


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** OpenSearch client wrapper for Quick Search operations.
 *******************************************************************************/
public class QuickSearchOpenSearchClient
{
   private static final QLogger LOG = QLogger.getLogger(QuickSearchOpenSearchClient.class);

   private final OpenSearchClient       client;
   private final QuickSearchQBitConfig  config;



   /***************************************************************************
    ** Constructor - creates OpenSearch client from QBit configuration.
    ***************************************************************************/
   public QuickSearchOpenSearchClient(QuickSearchQBitConfig config) throws QException
   {
      this.config = config;

      try
      {
         String scheme = Boolean.TRUE.equals(config.getUseSsl()) ? "https" : "http";
         HttpHost host = new HttpHost(scheme, config.getOpensearchHost(), config.getOpensearchPort());

         ApacheHttpClient5TransportBuilder builder = ApacheHttpClient5TransportBuilder.builder(host);

         if(config.getOpensearchUsername() != null && config.getOpensearchPassword() != null)
         {
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
               new AuthScope(host),
               new UsernamePasswordCredentials(
                  config.getOpensearchUsername(),
                  config.getOpensearchPassword().toCharArray()
               )
            );
            builder.setHttpClientConfigCallback(httpClientBuilder ->
               httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
            );
         }

         ObjectMapper objectMapper = new ObjectMapper();
         objectMapper.registerModule(new JavaTimeModule());
         builder.setMapper(new JacksonJsonpMapper(objectMapper));

         this.client = new OpenSearchClient(builder.build());
      }
      catch(Exception e)
      {
         throw new QException("Failed to create OpenSearch client", e);
      }
   }



   /***************************************************************************
    ** Ensure the index exists, creating it if necessary.
    ***************************************************************************/
   public void ensureIndexExists() throws QException
   {
      try
      {
         String indexName = config.getOpensearchIndexName();
         ExistsRequest existsRequest = new ExistsRequest.Builder().index(indexName).build();

         if(!client.indices().exists(existsRequest).value())
         {
            LOG.info("Creating OpenSearch index", logPair("indexName", indexName));

            CreateIndexRequest createRequest = new CreateIndexRequest.Builder()
               .index(indexName)
               .build();

            client.indices().create(createRequest);
         }
      }
      catch(IOException e)
      {
         throw new QException("Failed to ensure index exists", e);
      }
   }



   /***************************************************************************
    ** Index a single document.
    ***************************************************************************/
   public void indexDocument(OpenSearchDocument document) throws QException
   {
      try
      {
         IndexRequest<OpenSearchDocument> request = new IndexRequest.Builder<OpenSearchDocument>()
            .index(config.getOpensearchIndexName())
            .id(document.getDocumentId())
            .document(document)
            .build();

         client.index(request);
      }
      catch(IOException e)
      {
         throw new QException("Failed to index document: " + document.getDocumentId(), e);
      }
   }



   /***************************************************************************
    ** Index multiple documents in bulk.
    ***************************************************************************/
   public void indexDocuments(List<OpenSearchDocument> documents) throws QException
   {
      if(documents.isEmpty())
      {
         return;
      }

      try
      {
         BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

         for(OpenSearchDocument document : documents)
         {
            bulkBuilder.operations(op -> op
               .index(idx -> idx
                  .index(config.getOpensearchIndexName())
                  .id(document.getDocumentId())
                  .document(document)
               )
            );
         }

         BulkResponse response = client.bulk(bulkBuilder.build());

         if(response.errors())
         {
            LOG.warn("Bulk indexing completed with errors", logPair("errorCount", response.items().stream().filter(i -> i.error() != null).count()));
         }
      }
      catch(IOException e)
      {
         throw new QException("Failed to bulk index documents", e);
      }
   }



   /***************************************************************************
    ** Delete a single document by its ID.
    ***************************************************************************/
   public void deleteDocument(String sourceTable, String recordId) throws QException
   {
      try
      {
         String documentId = sourceTable + ":" + recordId;

         DeleteRequest request = new DeleteRequest.Builder()
            .index(config.getOpensearchIndexName())
            .id(documentId)
            .build();

         client.delete(request);
      }
      catch(IOException e)
      {
         throw new QException("Failed to delete document: " + sourceTable + ":" + recordId, e);
      }
   }



   /***************************************************************************
    ** Delete all documents for a specific source table.
    ***************************************************************************/
   public void deleteDocumentsForTable(String sourceTable) throws QException
   {
      try
      {
         Query query = new Query.Builder()
            .term(t -> t.field("sourceTable").value(FieldValue.of(sourceTable)))
            .build();

         DeleteByQueryRequest request = new DeleteByQueryRequest.Builder()
            .index(config.getOpensearchIndexName())
            .query(query)
            .build();

         client.deleteByQuery(request);
      }
      catch(IOException e)
      {
         throw new QException("Failed to delete documents for table: " + sourceTable, e);
      }
   }



   /***************************************************************************
    ** Search for documents matching the given query text.
    ***************************************************************************/
   public List<OpenSearchDocument> search(String queryText, Integer limit) throws QException
   {
      return search(queryText, null, limit);
   }



   /***************************************************************************
    ** Search for documents matching the given query text, optionally filtered by table.
    ***************************************************************************/
   public List<OpenSearchDocument> search(String queryText, String sourceTable, Integer limit) throws QException
   {
      try
      {
         Query matchQuery = new Query.Builder()
            .match(new MatchQuery.Builder()
               .field("searchableText")
               .query(FieldValue.of(queryText))
               .build())
            .build();

         Query finalQuery;
         if(sourceTable != null)
         {
            Query tableFilter = new Query.Builder()
               .term(t -> t.field("sourceTable").value(FieldValue.of(sourceTable)))
               .build();

            finalQuery = new Query.Builder()
               .bool(b -> b
                  .must(matchQuery)
                  .filter(tableFilter))
               .build();
         }
         else
         {
            finalQuery = matchQuery;
         }

         SearchRequest request = new SearchRequest.Builder()
            .index(config.getOpensearchIndexName())
            .query(finalQuery)
            .size(limit != null ? limit : 50)
            .build();

         SearchResponse<OpenSearchDocument> response = client.search(request, OpenSearchDocument.class);

         List<OpenSearchDocument> results = new ArrayList<>();
         for(Hit<OpenSearchDocument> hit : response.hits().hits())
         {
            if(hit.source() != null)
            {
               results.add(hit.source());
            }
         }

         return results;
      }
      catch(IOException e)
      {
         throw new QException("Failed to search documents", e);
      }
   }



   /***************************************************************************
    ** Close the client connection.
    ***************************************************************************/
   public void close()
   {
      // The ApacheHttpClient5Transport handles cleanup
   }

}

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Conflicts;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.VersionType;
import org.opensearch.client.opensearch._types.mapping.DynamicTemplate;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.MultiMatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.Operator;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.bulk.DeleteOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.opensearch.client.opensearch.core.search.Highlight;
import org.opensearch.client.opensearch.core.search.HighlightField;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.opensearch.client.opensearch.indices.GetMappingResponse;
import org.opensearch.client.transport.OpenSearchTransport;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** All OpenSearch operations for the Quick Search QBit.
 **
 ** The configured opensearchIndexName is an alias over a physical index named
 ** "{alias}-v{mappingVersion}-{epochSeconds}", so a full rebuild can index into a
 ** fresh physical index and swap the alias without a search blackout. A
 ** pre-1.0 concrete index with the alias's name is used as-is and reported as
 ** needing a rebuild.
 **
 ** Mapping (version 2): searchableText is indexed with an edge-ngram analyzer
 ** and searched with a plain lowercase analyzer, so query terms are not
 ** n-grammed; fieldValues.* are mapped through a dynamic template as text with
 ** the same analyzers (never numbers or dates), so tables cannot conflict.
 **
 ** Every OpenSearch failure surfaces as a QException.
 *******************************************************************************/
public class QuickSearchOpenSearchClient implements Closeable
{
   private static final QLogger LOG = QLogger.getLogger(QuickSearchOpenSearchClient.class);

   public static final int    MAPPING_VERSION      = 2;
   public static final String META_MAPPING_VERSION = "quickSearchMappingVersion";
   public static final String INDEX_ANALYZER       = "quick_search_analyzer";
   public static final String SEARCH_ANALYZER      = "quick_search_search_analyzer";
   public static final int    MAX_RESULT_WINDOW    = 10_000;

   private final OpenSearchClient    client;
   private final OpenSearchTransport transport;
   private final String              indexName;
   private final int                 maxBulkRequestBytes;
   private final ObjectMapper        sizingMapper;

   private volatile Boolean mappingOutdated = false;
   private          Boolean closed          = false;



   /*******************************************************************************
    ** Build a client for the config. Does not contact the cluster.
    *******************************************************************************/
   public QuickSearchOpenSearchClient(QuickSearchQBitConfig config) throws QException
   {
      this.transport           = OpenSearchTransportFactory.build(config);
      this.client              = new OpenSearchClient(transport);
      this.indexName           = config.getOpensearchIndexName();
      this.maxBulkRequestBytes = config.getMaxBulkRequestBytes() == null ? 5 * 1024 * 1024 : config.getMaxBulkRequestBytes();
      this.sizingMapper        = new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
   }



   /*******************************************************************************
    ** Make sure the alias (or a legacy concrete index) exists. Creates a new
    ** physical index with the current mapping and the alias when nothing exists.
    ** When an index exists with an older mapping, remembers that a rebuild is
    ** needed (see {@link #isMappingOutdated()}).
    *******************************************************************************/
   public void ensureIndexExists() throws QException
   {
      try
      {
         if(client.indices().exists(ExistsRequest.of(r -> r.index(indexName))).value())
         {
            mappingOutdated = readMappingVersion() < MAPPING_VERSION;
            if(mappingOutdated)
            {
               LOG.warn("OpenSearch index has an older mapping; run a full reindex to rebuild it", logPair("indexName", indexName), logPair("currentMappingVersion", MAPPING_VERSION));
            }
            else
            {
               LOG.info("OpenSearch index already exists", logPair("indexName", indexName));
            }
            return;
         }

         String physicalIndex = newPhysicalIndexName();
         createPhysicalIndex(physicalIndex);
         client.indices().updateAliases(u -> u.actions(a -> a.add(ad -> ad.index(physicalIndex).alias(indexName))));
         mappingOutdated = false;
         LOG.info("Created OpenSearch index", logPair("indexName", indexName), logPair("physicalIndex", physicalIndex));
      }
      catch(OpenSearchException | java.io.IOException e)
      {
         throw wrap("Failed to ensure index exists [" + indexName + "]", e);
      }
   }



   /*******************************************************************************
    ** Whether the live index was created with an older mapping than this
    ** client writes. A full reindex of all tables rebuilds it.
    *******************************************************************************/
   public boolean isMappingOutdated()
   {
      return (Boolean.TRUE.equals(mappingOutdated));
   }



   /*******************************************************************************
    ** Name for a fresh physical index behind the alias.
    *******************************************************************************/
   public String newPhysicalIndexName()
   {
      return (indexName + "-v" + MAPPING_VERSION + "-" + Instant.now().toEpochMilli());
   }



   /*******************************************************************************
    ** Create a physical index with the current settings and mapping (no alias).
    *******************************************************************************/
   public void createPhysicalIndex(String physicalIndex) throws QException
   {
      try
      {
         client.indices().create(CreateIndexRequest.of(r -> r
            .index(physicalIndex)
            .settings(s -> s
               .analysis(a -> a
                  .analyzer(INDEX_ANALYZER, an -> an.custom(c -> c.tokenizer("standard").filter("lowercase", "edge_ngram_filter")))
                  .analyzer(SEARCH_ANALYZER, an -> an.custom(c -> c.tokenizer("standard").filter("lowercase")))
                  .filter("edge_ngram_filter", f -> f.definition(d -> d.edgeNgram(en -> en.minGram(2).maxGram(20))))))
            .mappings(buildMapping())));
      }
      catch(OpenSearchException | java.io.IOException e)
      {
         throw wrap("Failed to create index [" + physicalIndex + "]", e);
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private TypeMapping buildMapping()
   {
      DynamicTemplate fieldValuesAsText = DynamicTemplate.of(t -> t
         .pathMatch("fieldValues.*")
         .mapping(p -> p.text(tx -> tx.analyzer(INDEX_ANALYZER).searchAnalyzer(SEARCH_ANALYZER))));

      return (TypeMapping.of(m -> m
         .meta(META_MAPPING_VERSION, JsonData.of(MAPPING_VERSION))
         .dateDetection(false)
         .numericDetection(false)
         .dynamicTemplates(Map.of("fieldValuesAsText", fieldValuesAsText))
         .properties("sourceTable", p -> p.keyword(k -> k))
         .properties("recordId", p -> p.keyword(k -> k))
         .properties("recordLabel", p -> p.text(t -> t.analyzer(INDEX_ANALYZER).searchAnalyzer(SEARCH_ANALYZER)))
         .properties("searchableText", p -> p.text(t -> t.analyzer(INDEX_ANALYZER).searchAnalyzer(SEARCH_ANALYZER)))
         .properties("indexedAt", p -> p.date(d -> d))
         .properties("fieldValues", p -> p.object(o -> o))));
   }



   /*******************************************************************************
    ** Read quickSearchMappingVersion from the live mapping's _meta; 1 when
    ** absent (a pre-1.0 index).
    *******************************************************************************/
   int readMappingVersion() throws java.io.IOException
   {
      GetMappingResponse response = client.indices().getMapping(g -> g.index(indexName));
      int                version  = 1;
      for(var record : response.result().values())
      {
         if(record.mappings() != null && record.mappings().meta() != null && record.mappings().meta().containsKey(META_MAPPING_VERSION))
         {
            version = Math.max(version, record.mappings().meta().get(META_MAPPING_VERSION).to(Integer.class));
         }
      }
      return (version);
   }



   /*******************************************************************************
    ** Physical indexes currently behind the alias (empty for a legacy concrete
    ** index).
    *******************************************************************************/
   public Set<String> getPhysicalIndexes() throws QException
   {
      try
      {
         if(!client.indices().existsAlias(e -> e.name(indexName)).value())
         {
            return (Set.of());
         }
         return (client.indices().getAlias(g -> g.name(indexName)).result().keySet());
      }
      catch(OpenSearchException | java.io.IOException e)
      {
         throw wrap("Failed to read alias [" + indexName + "]", e);
      }
   }



   /*******************************************************************************
    ** Atomically point the alias at newPhysicalIndex, then delete the indexes it
    ** pointed at before. A legacy concrete index with the alias's name is
    ** deleted first, since an alias cannot share its name.
    *******************************************************************************/
   public void swapAliasTo(String newPhysicalIndex) throws QException
   {
      try
      {
         Set<String> previous = getPhysicalIndexes();

         if(previous.isEmpty() && client.indices().exists(ExistsRequest.of(r -> r.index(indexName))).value())
         {
            client.indices().delete(d -> d.index(indexName));
            client.indices().updateAliases(u -> u.actions(a -> a.add(ad -> ad.index(newPhysicalIndex).alias(indexName))));
         }
         else
         {
            client.indices().updateAliases(u ->
            {
               u.actions(a -> a.add(ad -> ad.index(newPhysicalIndex).alias(indexName)));
               for(String old : previous)
               {
                  u.actions(a -> a.remove(rm -> rm.index(old).alias(indexName)));
               }
               return (u);
            });
            for(String old : previous)
            {
               if(!old.equals(newPhysicalIndex))
               {
                  client.indices().delete(d -> d.index(old));
               }
            }
         }

         mappingOutdated = false;
         LOG.info("Swapped OpenSearch alias", logPair("alias", indexName), logPair("newPhysicalIndex", newPhysicalIndex), logPair("removed", previous));
      }
      catch(OpenSearchException | java.io.IOException e)
      {
         throw wrap("Failed to swap alias [" + indexName + "] to [" + newPhysicalIndex + "]", e);
      }
   }



   /*******************************************************************************
    ** Delete a physical index (cleanup after a failed rebuild).
    *******************************************************************************/
   public void deletePhysicalIndex(String physicalIndex) throws QException
   {
      try
      {
         if(client.indices().exists(ExistsRequest.of(r -> r.index(physicalIndex))).value())
         {
            client.indices().delete(d -> d.index(physicalIndex));
         }
      }
      catch(OpenSearchException | java.io.IOException e)
      {
         throw wrap("Failed to delete index [" + physicalIndex + "]", e);
      }
   }



   /*******************************************************************************
    ** Index a single document into the alias.
    *******************************************************************************/
   public void indexDocument(OpenSearchDocument doc) throws QException
   {
      try
      {
         client.index(IndexRequest.of(r -> r.index(indexName).id(doc.getDocumentId()).document(doc)));
      }
      catch(OpenSearchException | java.io.IOException e)
      {
         throw wrap("Failed to index document [" + doc.getDocumentId() + "]", e);
      }
   }



   /*******************************************************************************
    ** Bulk-index into the alias.
    *******************************************************************************/
   public BulkIndexResult indexDocuments(List<OpenSearchDocument> docs, int batchSize) throws QException
   {
      return (indexDocuments(indexName, docs, batchSize));
   }



   /*******************************************************************************
    ** Bulk-index into a named index (alias or physical). Batches are cut at
    ** batchSize documents or maxBulkRequestBytes, whichever comes first.
    ** Documents with a version are written with version_type=external_gte so a
    ** stale write (older source timestamp) is rejected, not applied; rejected
    ** items are counted as skipped, not failures.
    *******************************************************************************/
   public BulkIndexResult indexDocuments(String targetIndex, List<OpenSearchDocument> docs, int batchSize) throws QException
   {
      BulkIndexResult result = new BulkIndexResult();
      if(docs == null || docs.isEmpty())
      {
         return (result);
      }

      List<BulkOperation> operations = new ArrayList<>();
      long                bytes      = 0;

      for(OpenSearchDocument doc : docs)
      {
         String docId = doc.getDocumentId();
         Long   version = doc.getVersion();

         operations.add(BulkOperation.of(o -> o.index(IndexOperation.of(i ->
         {
            i.index(targetIndex).id(docId).document(doc);
            if(version != null)
            {
               i.version(version).versionType(VersionType.ExternalGte);
            }
            return (i);
         }))));

         bytes += estimateBytes(doc);

         if(operations.size() >= batchSize || bytes >= maxBulkRequestBytes)
         {
            executeBulk(operations, result, "index");
            operations = new ArrayList<>();
            bytes      = 0;
         }
      }

      if(!operations.isEmpty())
      {
         executeBulk(operations, result, "index");
      }

      return (result);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private long estimateBytes(OpenSearchDocument doc)
   {
      try
      {
         return (sizingMapper.writeValueAsBytes(doc).length + 128);
      }
      catch(Exception e)
      {
         return (1024);
      }
   }



   /*******************************************************************************
    ** Bulk-delete documents by id from the alias. A missing document counts as
    ** success.
    *******************************************************************************/
   public BulkIndexResult deleteDocuments(List<String> documentIds, int batchSize) throws QException
   {
      BulkIndexResult result = new BulkIndexResult();
      if(documentIds == null || documentIds.isEmpty())
      {
         return (result);
      }

      int total = documentIds.size();
      for(int start = 0; start < total; start += batchSize)
      {
         List<BulkOperation> operations = new ArrayList<>();
         for(String documentId : documentIds.subList(start, Math.min(start + batchSize, total)))
         {
            operations.add(BulkOperation.of(o -> o.delete(DeleteOperation.of(d -> d.index(indexName).id(documentId)))));
         }
         executeBulk(operations, result, "delete");
      }

      return (result);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private void executeBulk(List<BulkOperation> operations, BulkIndexResult result, String operationName) throws QException
   {
      try
      {
         BulkResponse response = client.bulk(BulkRequest.of(r -> r.operations(operations)));
         for(BulkResponseItem item : response.items())
         {
            if(item.error() == null)
            {
               result.addSuccess();
            }
            else if(item.status() == 409)
            {
               ////////////////////////////////////////////////////////////////////
               // a newer version of this document is already indexed; keep it  //
               ////////////////////////////////////////////////////////////////////
               result.addSkipped();
            }
            else
            {
               result.addFailure(item.id() + ": " + item.error().reason());
            }
         }
      }
      catch(OpenSearchException | java.io.IOException e)
      {
         throw wrap("Bulk " + operationName + " request failed", e);
      }
   }



   /*******************************************************************************
    ** Delete one document.
    *******************************************************************************/
   public void deleteDocument(String sourceTable, String recordId) throws QException
   {
      String documentId = OpenSearchDocument.buildDocumentId(sourceTable, recordId);
      try
      {
         client.delete(DeleteRequest.of(r -> r.index(indexName).id(documentId)));
      }
      catch(OpenSearchException | java.io.IOException e)
      {
         throw wrap("Failed to delete document [" + documentId + "]", e);
      }
   }



   /*******************************************************************************
    ** Delete every document of a table. Version conflicts (a concurrent write)
    ** proceed rather than failing the request.
    *******************************************************************************/
   public Long deleteDocumentsForTable(String sourceTable) throws QException
   {
      try
      {
         DeleteByQueryResponse response = client.deleteByQuery(DeleteByQueryRequest.of(r -> r
            .index(indexName)
            .conflicts(Conflicts.Proceed)
            .refresh(Refresh.True)
            .query(Query.of(q -> q.term(TermQuery.of(t -> t.field("sourceTable").value(FieldValue.of(sourceTable))))))));
         return (response.deleted() == null ? 0L : response.deleted());
      }
      catch(OpenSearchException | java.io.IOException e)
      {
         throw wrap("Failed to delete documents for table [" + sourceTable + "]", e);
      }
   }



   /*******************************************************************************
    ** Delete every document whose sourceTable is not in the given set (tables
    ** removed from the configuration).
    *******************************************************************************/
   public Long deleteDocumentsForTablesNotIn(Collection<String> keepTables) throws QException
   {
      try
      {
         List<FieldValue> values = keepTables.stream().map(FieldValue::of).toList();
         DeleteByQueryResponse response = client.deleteByQuery(DeleteByQueryRequest.of(r -> r
            .index(indexName)
            .conflicts(Conflicts.Proceed)
            .refresh(Refresh.True)
            .query(Query.of(q -> q.bool(b -> b.mustNot(mn -> mn.terms(t -> t.field("sourceTable").terms(tv -> tv.value(values)))))))));
         return (response.deleted() == null ? 0L : response.deleted());
      }
      catch(OpenSearchException | java.io.IOException e)
      {
         throw wrap("Failed to delete documents of removed tables", e);
      }
   }



   /*******************************************************************************
    ** Delete a table's documents not (re-)indexed at or after cutoff.
    *******************************************************************************/
   public Long deleteDocumentsIndexedBefore(String sourceTable, Instant cutoff) throws QException
   {
      try
      {
         DeleteByQueryResponse response = client.deleteByQuery(DeleteByQueryRequest.of(r -> r
            .index(indexName)
            .conflicts(Conflicts.Proceed)
            .refresh(Refresh.True)
            .query(Query.of(q -> q.bool(b -> b
               .filter(f -> f.term(t -> t.field("sourceTable").value(FieldValue.of(sourceTable))))
               .mustNot(mn -> mn.range(rg -> rg.field("indexedAt").gte(JsonData.of(cutoff.toString())))))))));
         return (response.deleted() == null ? 0L : response.deleted());
      }
      catch(OpenSearchException | java.io.IOException e)
      {
         throw wrap("Failed to delete stale documents for table [" + sourceTable + "]", e);
      }
   }



   /*******************************************************************************
    ** Count a table's documents.
    *******************************************************************************/
   public Long countDocumentsForTable(String sourceTable) throws QException
   {
      try
      {
         return (client.count(c -> c.index(indexName).query(q -> q.term(t -> t.field("sourceTable").value(FieldValue.of(sourceTable))))).count());
      }
      catch(OpenSearchException | java.io.IOException e)
      {
         throw wrap("Failed to count documents for table [" + sourceTable + "]", e);
      }
   }



   /*******************************************************************************
    ** Whether the cluster answers and the alias (or index) exists.
    *******************************************************************************/
   public boolean isHealthy()
   {
      try
      {
         return (client.indices().exists(ExistsRequest.of(r -> r.index(indexName))).value());
      }
      catch(Exception e)
      {
         LOG.debug("Quick Search health check failed", e);
         return (false);
      }
   }



   /*******************************************************************************
    ** Boosted multi-match search over the allowed tables only.
    **
    ** The must clause matches every term of the query (operator AND) in
    ** searchableText; per-field should clauses boost by configured weight and
    ** are lenient, so a non-text value can never fail the request. Only tables
    ** in allowedTables are searched; an empty set returns no hits.
    *******************************************************************************/
   public SearchResponse<OpenSearchDocument> search(String searchTerm, Collection<String> allowedTables, int limit, int offset, List<QuickSearchableTableConfig> tableConfigs) throws QException
   {
      try
      {
         List<FieldValue> allowed = allowedTables.stream().map(FieldValue::of).toList();

         BoolQuery.Builder boolBuilder = new BoolQuery.Builder()
            .must(Query.of(q -> q.multiMatch(MultiMatchQuery.of(mm -> mm.query(searchTerm).fields("searchableText").operator(Operator.And)))))
            .filter(Query.of(q -> q.terms(t -> t.field("sourceTable").terms(tv -> tv.value(allowed)))))
            .should(buildFieldBoostQueries(searchTerm, tableConfigs, allowedTables));

         Query     finalQuery = Query.of(q -> q.bool(boolBuilder.build()));
         Highlight highlight  = Highlight.of(h -> h.fields("searchableText", HighlightField.of(hf -> hf)));

         SearchRequest request = SearchRequest.of(r -> r
            .index(indexName)
            .query(finalQuery)
            .highlight(highlight)
            .trackTotalHits(t -> t.count(MAX_RESULT_WINDOW))
            .from(offset)
            .size(limit));

         return (client.search(request, OpenSearchDocument.class));
      }
      catch(OpenSearchException | java.io.IOException e)
      {
         throw wrap("Search failed for term [" + searchTerm + "]", e);
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private List<Query> buildFieldBoostQueries(String searchTerm, List<QuickSearchableTableConfig> tableConfigs, Collection<String> allowedTables)
   {
      List<Query> queries = new ArrayList<>();
      if(tableConfigs == null)
      {
         return (queries);
      }

      for(QuickSearchableTableConfig tableConfig : tableConfigs)
      {
         if(!allowedTables.contains(tableConfig.getTableName()))
         {
            continue;
         }

         Map<String, Integer> weights = tableConfig.getFieldWeights();
         List<String>         fields  = tableConfig.getSearchableFields() == null ? List.of() : tableConfig.getSearchableFields();
         for(String field : fields)
         {
            float boost = (weights == null || weights.get(field) == null) ? 1f : weights.get(field).floatValue();
            queries.add(Query.of(q -> q.multiMatch(MultiMatchQuery.of(mm -> mm
               .query(searchTerm)
               .fields("fieldValues." + field)
               .operator(Operator.And)
               .lenient(true)
               .boost(boost)))));
         }
      }

      return (queries);
   }



   /*******************************************************************************
    ** Force a refresh so indexed documents are searchable now.
    *******************************************************************************/
   public void refreshIndex() throws QException
   {
      try
      {
         client.indices().refresh(r -> r.index(indexName));
      }
      catch(OpenSearchException | java.io.IOException e)
      {
         throw wrap("Failed to refresh index [" + indexName + "]", e);
      }
   }



   /*******************************************************************************
    ** Refresh a named physical index.
    *******************************************************************************/
   public void refreshIndex(String physicalIndex) throws QException
   {
      try
      {
         client.indices().refresh(r -> r.index(physicalIndex));
      }
      catch(OpenSearchException | java.io.IOException e)
      {
         throw wrap("Failed to refresh index [" + physicalIndex + "]", e);
      }
   }



   /*******************************************************************************
    ** Getter for the configured alias name.
    *******************************************************************************/
   public String getIndexName()
   {
      return (indexName);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static QException wrap(String message, Exception e)
   {
      if(e instanceof OpenSearchException openSearchException)
      {
         String reason = openSearchException.error() == null ? openSearchException.getMessage() : openSearchException.error().reason();
         return (new QException(message + ": OpenSearch returned status " + openSearchException.status() + ": " + reason, e));
      }
      return (new QException(message + ": " + e.getMessage(), e));
   }



   /*******************************************************************************
    ** Close the transport. Safe to call more than once.
    *******************************************************************************/
   @Override
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

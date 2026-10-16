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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.kingsrook.qbits.quicksearch.QuickSearchAuthMode;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.config.QuickSearchFieldRules;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFieldAnalyzer;
import com.kingsrook.qbits.quicksearch.processes.IndexingUtils;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
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
import org.opensearch.client.opensearch._types.query_dsl.TextQueryType;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.MsearchRequest;
import org.opensearch.client.opensearch.core.MsearchResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.bulk.DeleteOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.opensearch.client.opensearch.core.msearch.MultiSearchResponseItem;
import org.opensearch.client.opensearch.core.msearch.RequestItem;
import org.opensearch.client.opensearch.core.search.Hit;
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
   public static final String MATCH_CLAUSE_NAME    = "searchableText";
   public static final String FIELD_CLAUSE_PREFIX  = "field:";
   public static final String PATH_PREFIX          = "fieldValues";
   public static final String PATH_WORD            = "fieldValuesWord";
   public static final String PATH_ENGLISH         = "fieldValuesEnglish";
   public static final String PATH_EXACT           = "fieldValuesExact";

   ////////////////////////////////////////////////////////////////////////////
   // record search highlight markers: private-use characters, which do not //
   // occur in indexed text, so fragments convert to plain text and ranges  //
   ////////////////////////////////////////////////////////////////////////////
   public static final String HIGHLIGHT_PRE_TAG       = "\uE000"; // private use area
   public static final String HIGHLIGHT_POST_TAG      = "\uE001"; // private use area
   public static final int    HIGHLIGHT_FRAGMENT_SIZE = 160;

   private final OpenSearchClient      client;
   private final OpenSearchAsyncClient asyncClient;
   private final OpenSearchTransport   transport;
   private final String                indexName;
   private final int                   maxBulkRequestBytes;
   private final ObjectMapper          sizingMapper;
   private final Integer               indexReplicas;
   private final Integer               maxAutoReplicas;
   private final Boolean               replicasManaged;

   private volatile Boolean mappingOutdated = false;
   private volatile Boolean indexCreated    = false;
   private volatile Instant indexCreatedAt;
   private          Boolean closed          = false;
   private volatile ReplicaStatus replicaStatus;



   /*******************************************************************************
    ** Outcome of the latest replica sizing: the target number_of_replicas, the
    ** data nodes it was sized for (null when explicitly configured), how many
    ** physical indexes were changed, and whether applying it failed.
    *******************************************************************************/
   public record ReplicaStatus(Integer targetReplicas, Integer dataNodes, Integer updatedIndexes, Boolean failed)
   {
   }



   /*******************************************************************************
    ** Build a client for the config. Does not contact the cluster.
    *******************************************************************************/
   public QuickSearchOpenSearchClient(QuickSearchQBitConfig config) throws QException
   {
      this.transport           = OpenSearchTransportFactory.build(config);
      this.client              = new OpenSearchClient(transport);
      this.asyncClient         = new OpenSearchAsyncClient(transport);
      this.indexName           = config.getOpensearchIndexName();
      this.maxBulkRequestBytes = config.getMaxBulkRequestBytes() == null ? 5 * 1024 * 1024 : config.getMaxBulkRequestBytes();
      this.sizingMapper        = new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
      this.indexReplicas       = config.getIndexReplicas();
      this.maxAutoReplicas     = config.getMaxAutoReplicas() == null ? QuickSearchQBitConfig.DEFAULT_MAX_AUTO_REPLICAS : config.getMaxAutoReplicas();
      this.replicasManaged     = !(config.getEffectiveAuthMode() == QuickSearchAuthMode.AWS_SIGV4 && "aoss".equals(config.getEffectiveAwsServiceName()));
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
               extendMapping();
            }
            reconcileReplicas();
            return;
         }

         String physicalIndex = newPhysicalIndexName();
         createPhysicalIndex(physicalIndex);
         client.indices().updateAliases(u -> u.actions(a -> a.add(ad -> ad.index(physicalIndex).alias(indexName))));
         mappingOutdated = false;
         indexCreated    = true;
         indexCreatedAt  = Instant.now();
         LOG.info("Created OpenSearch index", logPair("indexName", indexName), logPair("physicalIndex", physicalIndex));
      }
      catch(OpenSearchException | java.io.IOException e)
      {
         throw wrap("Failed to ensure index exists [" + indexName + "]", e);
      }
   }



   /*******************************************************************************
    ** Whether ensureIndexExists found no index and created an empty one (a new
    ** install, or the index was deleted). Self-priming then queues a build of
    ** every enabled table. Cleared by clearIndexCreated().
    *******************************************************************************/
   public boolean wasIndexCreated()
   {
      return (Boolean.TRUE.equals(indexCreated));
   }



   /*******************************************************************************
    ** When ensureIndexExists last created the index (null when it has not).
    *******************************************************************************/
   public Instant getIndexCreatedAt()
   {
      return (indexCreatedAt);
   }



   /*******************************************************************************
    ** Forget that the index was created (its builds have been queued).
    *******************************************************************************/
   public void clearIndexCreated()
   {
      indexCreated = false;
   }



   /*******************************************************************************
    ** Whether the alias (or a legacy concrete index) exists. Throws when the
    ** cluster cannot be asked.
    *******************************************************************************/
   public boolean indexExists() throws QException
   {
      try
      {
         return (client.indices().exists(ExistsRequest.of(r -> r.index(indexName))).value());
      }
      catch(OpenSearchException | java.io.IOException e)
      {
         throw wrap("Failed to check whether index [" + indexName + "] exists", e);
      }
   }



   /*******************************************************************************
    ** Number of documents in the index, all tables.
    *******************************************************************************/
   public Long countAllDocuments() throws QException
   {
      try
      {
         return (client.count(c -> c.index(indexName)).count());
      }
      catch(OpenSearchException | java.io.IOException e)
      {
         throw wrap("Failed to count documents in index [" + indexName + "]", e);
      }
   }



   /*******************************************************************************
    ** Add the field-analyzer templates and the role properties (added in
    ** 1.1.0-RC.3) to an index created before them. Additive, so the mapping
    ** version stays 2 and no rebuild is needed; a failure is logged and only
    ** means those fields fall back to dynamic mapping.
    *******************************************************************************/
   void extendMapping()
   {
      try
      {
         client.indices().putMapping(p -> p
            .index(indexName)
            .dynamicTemplates(dynamicTemplates())
            .properties("subtitle", pr -> pr.text(t -> t.index(false)))
            .properties("displayValues", pr -> pr.object(o -> o.enabled(false)))
            .properties(PATH_WORD, pr -> pr.object(o -> o))
            .properties(PATH_ENGLISH, pr -> pr.object(o -> o))
            .properties(PATH_EXACT, pr -> pr.object(o -> o)));
      }
      catch(Exception e)
      {
         LOG.warn("Could not add the field-analyzer mapping to the existing index; WORD, ENGLISH and EXACT fields use dynamic mapping until a full reindex",
            e, logPair("indexName", indexName));
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
         Integer replicas = replicasForNewIndex();
         client.indices().create(CreateIndexRequest.of(r -> r
            .index(physicalIndex)
            .settings(s ->
            {
               s.analysis(a -> a
                  .analyzer(INDEX_ANALYZER, an -> an.custom(c -> c.tokenizer("standard").filter("lowercase", "edge_ngram_filter")))
                  .analyzer(SEARCH_ANALYZER, an -> an.custom(c -> c.tokenizer("standard").filter("lowercase")))
                  .filter("edge_ngram_filter", f -> f.definition(d -> d.edgeNgram(en -> en.minGram(2).maxGram(20)))));
               if(replicas != null)
               {
                  s.numberOfReplicas(replicas);
               }
               return (s);
            })
            .mappings(buildMapping())));
      }
      catch(OpenSearchException | java.io.IOException e)
      {
         throw wrap("Failed to create index [" + physicalIndex + "]", e);
      }
   }



   /*******************************************************************************
    ** Replicas a new physical index should ask for; null leaves the server
    ** default (Serverless, or the cluster could not be sized; logged).
    *******************************************************************************/
   private Integer replicasForNewIndex()
   {
      if(!Boolean.TRUE.equals(replicasManaged))
      {
         return (null);
      }
      try
      {
         ReplicaStatus target = targetReplicas();
         replicaStatus = new ReplicaStatus(target.targetReplicas(), target.dataNodes(), 0, false);
         return (target.targetReplicas());
      }
      catch(Exception e)
      {
         replicaStatus = new ReplicaStatus(null, null, 0, true);
         LOG.warn("Could not size Quick Search index replicas; the new index uses the server default", e, logPair("indexName", indexName));
         return (null);
      }
   }



   /*******************************************************************************
    ** The configured indexReplicas, else min(maxAutoReplicas, data nodes - 1)
    ** from cluster health, so a single-node cluster gets 0 and stays green.
    *******************************************************************************/
   ReplicaStatus targetReplicas() throws QException
   {
      if(indexReplicas != null)
      {
         return (new ReplicaStatus(indexReplicas, null, 0, false));
      }
      QuickSearchDiagnosticsOperations.RawResponse health = new QuickSearchDiagnosticsOperations(this).raw("GET", "/_cluster/health", null);
      QuickSearchDiagnosticsOperations.requireOk(health, "Cluster health unavailable");
      JsonNode dataNodes = health.body() == null ? null : health.body().path("number_of_data_nodes");
      if(dataNodes == null || !dataNodes.isNumber())
      {
         throw (new QException("Cluster health did not report number_of_data_nodes"));
      }
      return (new ReplicaStatus(autoReplicas(dataNodes.intValue(), maxAutoReplicas), dataNodes.intValue(), 0, false));
   }



   /*******************************************************************************
    ** Automatic replica count: a replica can only be assigned to a different
    ** data node than its primary, so never more than data nodes - 1.
    *******************************************************************************/
   static int autoReplicas(int dataNodes, int maxAutoReplicas)
   {
      return (Math.max(0, Math.min(maxAutoReplicas, dataNodes - 1)));
   }



   /*******************************************************************************
    ** Bring number_of_replicas of the physical index(es) behind the alias to
    ** the target. Idempotent (only differing indexes are updated) and failure
    ** tolerant: a failure is logged and kept in {@link #getReplicaStatus()},
    ** never thrown, so it cannot block startup. Indexes with
    ** auto_expand_replicas set are left to OpenSearch. Null on Serverless.
    *******************************************************************************/
   public ReplicaStatus reconcileReplicas()
   {
      if(!Boolean.TRUE.equals(replicasManaged))
      {
         return (null);
      }
      Integer target = null;
      Integer nodes  = null;
      try
      {
         ReplicaStatus sized = targetReplicas();
         target = sized.targetReplicas();
         nodes  = sized.dataNodes();
         QuickSearchDiagnosticsOperations             operations = new QuickSearchDiagnosticsOperations(this);
         QuickSearchDiagnosticsOperations.RawResponse settings   = operations.raw("GET", "/" + QuickSearchDiagnosticsOperations.encode(indexName) + "/_settings", null);
         QuickSearchDiagnosticsOperations.requireOk(settings, "Index settings unavailable");
         int updated = 0;
         for(Map.Entry<String, JsonNode> entry : settings.body().properties())
         {
            JsonNode index      = entry.getValue().path("settings").path("index");
            String   current    = index.path("number_of_replicas").asText(null);
            String   autoExpand = index.path("auto_expand_replicas").asText("false");
            if(!"false".equalsIgnoreCase(autoExpand) || String.valueOf(target).equals(current))
            {
               continue;
            }
            QuickSearchDiagnosticsOperations.RawResponse put = operations.raw("PUT", "/" + QuickSearchDiagnosticsOperations.encode(entry.getKey()) + "/_settings",
               "{\"index\":{\"number_of_replicas\":" + target + "}}");
            QuickSearchDiagnosticsOperations.requireOk(put, "Updating number_of_replicas failed");
            updated++;
            LOG.info("Updated Quick Search index replicas", logPair("physicalIndex", entry.getKey()), logPair("from", current),
               logPair("to", target), logPair("dataNodes", nodes));
         }
         replicaStatus = new ReplicaStatus(target, nodes, updated, false);
      }
      catch(Exception e)
      {
         LOG.warn("Could not reconcile Quick Search index replicas; continuing", e, logPair("indexName", indexName), logPair("targetReplicas", target));
         replicaStatus = new ReplicaStatus(target, nodes, 0, true);
      }
      return (replicaStatus);
   }



   /*******************************************************************************
    ** The latest replica sizing outcome; null before any (or on Serverless).
    *******************************************************************************/
   public ReplicaStatus getReplicaStatus()
   {
      return (replicaStatus);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private TypeMapping buildMapping()
   {
      return (TypeMapping.of(m -> m
         .meta(META_MAPPING_VERSION, JsonData.of(MAPPING_VERSION))
         .dateDetection(false)
         .numericDetection(false)
         .dynamicTemplates(dynamicTemplates())
         .properties("subtitle", p -> p.text(t -> t.index(false)))
         .properties("displayValues", p -> p.object(o -> o.enabled(false)))
         .properties(PATH_WORD, p -> p.object(o -> o))
         .properties(PATH_ENGLISH, p -> p.object(o -> o))
         .properties(PATH_EXACT, p -> p.object(o -> o))
         .properties("sourceTable", p -> p.keyword(k -> k))
         .properties("recordId", p -> p.keyword(k -> k))
         .properties("recordLabel", p -> p.text(t -> t.analyzer(INDEX_ANALYZER).searchAnalyzer(SEARCH_ANALYZER)))
         .properties("searchableText", p -> p.text(t -> t.analyzer(INDEX_ANALYZER).searchAnalyzer(SEARCH_ANALYZER)))
         .properties("indexedAt", p -> p.date(d -> d))
         .properties("fieldValues", p -> p.object(o -> o))));
   }



   /*******************************************************************************
    ** The dynamic templates: fieldValues.* with the prefix analyzers (as since
    ** mapping version 2), and the field-analyzer copies: fieldValuesWord.*
    ** (standard), fieldValuesEnglish.* (english) and fieldValuesExact.*
    ** (keyword; values are lower-cased before indexing).
    *******************************************************************************/
   static List<Map<String, DynamicTemplate>> dynamicTemplates()
   {
      return (List.of(
         Map.of("fieldValuesAsText", DynamicTemplate.of(t -> t.pathMatch(PATH_PREFIX + ".*")
            .mapping(p -> p.text(tx -> tx.analyzer(INDEX_ANALYZER).searchAnalyzer(SEARCH_ANALYZER))))),
         Map.of("fieldValuesWordAsText", DynamicTemplate.of(t -> t.pathMatch(PATH_WORD + ".*")
            .mapping(p -> p.text(tx -> tx.analyzer("standard"))))),
         Map.of("fieldValuesEnglishAsText", DynamicTemplate.of(t -> t.pathMatch(PATH_ENGLISH + ".*")
            .mapping(p -> p.text(tx -> tx.analyzer("english"))))),
         Map.of("fieldValuesExactAsKeyword", DynamicTemplate.of(t -> t.pathMatch(PATH_EXACT + ".*")
            .mapping(p -> p.keyword(k -> k.ignoreAbove(1024)))))));
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
            ///////////////////////////////////////////////////////////////////////////
            // one request: an alias cannot share the legacy index's name, and two //
            // calls would leave a window with neither index nor alias            //
            ///////////////////////////////////////////////////////////////////////////
            client.indices().updateAliases(u -> u
               .actions(a -> a.removeIndex(ri -> ri.index(indexName)))
               .actions(a -> a.add(ad -> ad.index(newPhysicalIndex).alias(indexName))));
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
                  try
                  {
                     client.indices().delete(d -> d.index(old));
                  }
                  catch(OpenSearchException | java.io.IOException cleanup)
                  {
                     LOG.warn("Alias swapped but the previous physical index could not be deleted", cleanup, logPair("physicalIndex", old));
                  }
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
      return (search(searchTerm, allowedTables, limit, offset, tableConfigs, null));
   }



   /*******************************************************************************
    ** As search above; a non-null preference routes every request that shares
    ** it to the same shard copies, so batches of one scan see one ordering.
    *******************************************************************************/
   public SearchResponse<OpenSearchDocument> search(String searchTerm, Collection<String> allowedTables, int limit, int offset, List<QuickSearchableTableConfig> tableConfigs, String preference) throws QException
   {
      try
      {
         Query     finalQuery = buildSearchQuery(searchTerm, allowedTables, tableConfigs);
         Highlight highlight  = allowedTables.stream().anyMatch(QuickSearchFieldRules::requiresFieldFiltering) ? null
            : Highlight.of(h -> h.fields("searchableText", HighlightField.of(hf -> hf)));

         SearchRequest request = SearchRequest.of(r -> r
            .index(indexName)
            .query(finalQuery)
            .highlight(highlight)
            .trackTotalHits(t -> t.count(MAX_RESULT_WINDOW))
            .from(offset)
            .size(limit)
            .preference(preference));

         return (client.search(request, OpenSearchDocument.class));
      }
      catch(OpenSearchException | java.io.IOException e)
      {
         throw wrap("Search failed for term [" + searchTerm + "]", e);
      }
   }



   /*******************************************************************************
    ** One hit of a record search: the record id, its relevance score, and its
    ** highlight fragments by index field (e.g., "fieldValues.firstName"), with
    ** matches between {@link #HIGHLIGHT_PRE_TAG} and {@link #HIGHLIGHT_POST_TAG}.
    *******************************************************************************/
   public record RecordSearchHit(String recordId, Double score, Map<String, List<String>> highlights)
   {
   }



   /*******************************************************************************
    ** The hits of a record search in one table, best first, and the table's
    ** total number of matches (counted up to {@link #MAX_RESULT_WINDOW}; null
    ** if unknown).
    *******************************************************************************/
   public record RecordSearchTableHits(List<RecordSearchHit> hits, Long totalHits)
   {
   }



   /*******************************************************************************
    ** Record ids of the best matches in each table, best first - see
    ** {@link #searchRecordsPerTable}, which also returns scores, highlights and
    ** totals.
    *******************************************************************************/
   public Map<String, List<String>> searchRecordIdsPerTable(String searchTerm, List<String> tableNames, int idsPerTable, List<QuickSearchableTableConfig> tableConfigs, int timeoutMillis) throws QException
   {
      Map<String, List<String>> idsByTable = new LinkedHashMap<>();
      for(Map.Entry<String, RecordSearchTableHits> entry : searchRecordsPerTable(searchTerm, tableNames, idsPerTable, 0, false, tableConfigs, timeoutMillis).entrySet())
      {
         idsByTable.put(entry.getKey(), entry.getValue().hits().stream().map(RecordSearchHit::recordId).toList());
      }
      return (idsByTable);
   }



   /*******************************************************************************
    ** The best matches in each table, best first, for core record search. One
    ** _msearch request with one search per table (the same query and ranking as
    ** {@link #search}, restricted to that table), so every table gets its own
    ** top hitsPerTable hits, after skipping offset. Only sourceTable and
    ** recordId are fetched, plus each hit's score, the table's total hits
    ** (counted up to MAX_RESULT_WINDOW) and - when highlight is set - one
    ** highlight fragment per matching fieldValues.* field.
    **
    ** The whole request is bounded by timeoutMillis of wall-clock time (and the
    ** same server-side search timeout): when it runs out, the request is
    ** cancelled and a QException is thrown. A failed search for any table fails
    ** the call. Returns every requested table as a key, in the given order.
    *******************************************************************************/
   public Map<String, RecordSearchTableHits> searchRecordsPerTable(String searchTerm, List<String> tableNames, int hitsPerTable, int offset, boolean highlight, List<QuickSearchableTableConfig> tableConfigs, int timeoutMillis) throws QException
   {
      Map<String, RecordSearchTableHits> hitsByTable = new LinkedHashMap<>();
      if(tableNames == null || tableNames.isEmpty())
      {
         return (hitsByTable);
      }

      int from = Math.max(0, Math.min(offset, MAX_RESULT_WINDOW - hitsPerTable));

      List<RequestItem> searches = new ArrayList<>();
      for(String tableName : tableNames)
      {
         Query query = buildSearchQuery(searchTerm, List.of(tableName), tableConfigs);
         searches.add(RequestItem.of(ri -> ri
            .header(h -> h.index(indexName))
            .body(b ->
            {
               b.query(query)
                  .from(from)
                  .size(hitsPerTable)
                  .trackTotalHits(t -> t.count(MAX_RESULT_WINDOW))
                  .source(sc -> sc.filter(f -> f.includes("sourceTable", "recordId")))
                  .timeout(timeoutMillis + "ms");
               if(highlight)
               {
                  b.highlight(h -> h
                     .preTags(HIGHLIGHT_PRE_TAG)
                     .postTags(HIGHLIGHT_POST_TAG)
                     .requireFieldMatch(false)
                     .numberOfFragments(1)
                     .fragmentSize(HIGHLIGHT_FRAGMENT_SIZE)
                     .fields("fieldValues.*", HighlightField.of(hf -> hf)));
               }
               return (b);
            })));
      }

      CompletableFuture<MsearchResponse<OpenSearchDocument>> future = null;
      try
      {
         future = asyncClient.msearch(MsearchRequest.of(r -> r.searches(searches)), OpenSearchDocument.class);
         MsearchResponse<OpenSearchDocument> response = future.get(timeoutMillis, TimeUnit.MILLISECONDS);

         List<MultiSearchResponseItem<OpenSearchDocument>> items = response.responses();
         if(items == null || items.size() != tableNames.size())
         {
            throw (new QException("Record search failed: OpenSearch returned " + (items == null ? 0 : items.size()) + " responses for " + tableNames.size() + " searches"));
         }

         for(int i = 0; i < tableNames.size(); i++)
         {
            MultiSearchResponseItem<OpenSearchDocument> item = items.get(i);
            if(item.isFailure())
            {
               String reason = item.failure() != null && item.failure().error() != null ? item.failure().error().reason() : "unknown error";
               throw (new QException("Record search failed for table [" + tableNames.get(i) + "]: " + reason));
            }

            List<RecordSearchHit> hits = new ArrayList<>();
            for(Hit<OpenSearchDocument> hit : item.result().hits().hits())
            {
               if(hit.source() != null && hit.source().getRecordId() != null)
               {
                  hits.add(new RecordSearchHit(hit.source().getRecordId(), hit.score(), hit.highlight()));
               }
            }

            Long totalHits = item.result().hits().total() == null ? null : item.result().hits().total().value();
            hitsByTable.put(tableNames.get(i), new RecordSearchTableHits(hits, totalHits));
         }
         return (hitsByTable);
      }
      catch(TimeoutException e)
      {
         future.cancel(true);
         throw (new QException("Record search timed out after " + timeoutMillis + " ms", e));
      }
      catch(InterruptedException e)
      {
         if(future != null)
         {
            future.cancel(true);
         }
         Thread.currentThread().interrupt();
         throw (new QException("Record search was interrupted", e));
      }
      catch(ExecutionException e)
      {
         throw wrap("Record search failed", e.getCause() instanceof Exception cause ? cause : e);
      }
      catch(OpenSearchException | java.io.IOException e)
      {
         throw wrap("Record search failed", e);
      }
   }



   /*******************************************************************************
    ** The search query: every term of the query (operator AND) must match
    ** searchableText, only the allowed tables match (an empty set matches
    ** nothing), and per-field should clauses boost by configured weight.
    *******************************************************************************/
   private Query buildSearchQuery(String searchTerm, Collection<String> allowedTables, List<QuickSearchableTableConfig> tableConfigs)
   {
      return (buildSearchQuery(searchTerm, allowedTables, tableConfigs, false));
   }



   /*******************************************************************************
    ** The search query; with named true each clause carries a query name
    ** (MATCH_CLAUSE_NAME for the required searchableText match, the field name
    ** for each boost clause) so a hit reports which clauses matched. Names do
    ** not change matching or scoring.
    *******************************************************************************/
   Query buildSearchQuery(String searchTerm, Collection<String> allowedTables, List<QuickSearchableTableConfig> tableConfigs, boolean named)
   {
      List<FieldValue> allowed = allowedTables.stream().map(FieldValue::of).toList();

      Query snippetMatch = Query.of(q -> q.multiMatch(MultiMatchQuery.of(mm ->
      {
         mm.query(searchTerm).fields("searchableText").operator(Operator.And);
         return (named ? mm.queryName(MATCH_CLAUSE_NAME) : mm);
      })));

      ////////////////////////////////////////////////////////////////////////////
      // fields kept out of the snippet (KEYWORD, EXACT, or includeInSnippet   //
      // off) match on their own; with none, the query is exactly as before    //
      ////////////////////////////////////////////////////////////////////////////
      List<String> aggregateTables = allowedTables.stream().filter(t -> !QuickSearchFieldRules.requiresFieldFiltering(t)).toList();
      if(aggregateTables.isEmpty())
      {
         snippetMatch = Query.of(q -> q.matchNone(m -> m));
      }
      else if(aggregateTables.size() != allowedTables.size())
      {
         Query aggregateMatch = snippetMatch;
         snippetMatch = Query.of(q -> q.bool(b -> b.must(aggregateMatch).filter(f -> f.terms(t -> t.field("sourceTable")
            .terms(v -> v.value(aggregateTables.stream().map(FieldValue::of).toList()))))));
      }
      List<Query> ownMatches = buildOwnFieldMatchQueries(searchTerm, tableConfigs, allowedTables);
      ownMatches.addAll(buildFilteredSnippetQueries(searchTerm, tableConfigs, allowedTables));
      Query safeSnippetMatch = snippetMatch;
      Query       match      = ownMatches.isEmpty() ? safeSnippetMatch
         : Query.of(q -> q.bool(b -> b.should(safeSnippetMatch).should(ownMatches).minimumShouldMatch("1")));

      BoolQuery.Builder boolBuilder = new BoolQuery.Builder()
         .must(match)
         .filter(Query.of(q -> q.terms(t -> t.field("sourceTable").terms(tv -> tv.value(allowed)))))
         .should(buildFieldBoostQueries(searchTerm, tableConfigs, allowedTables, named));

      return (Query.of(q -> q.bool(boolBuilder.build())));
   }



   /*******************************************************************************
    ** For tables with excluded fields, match only attributable field values.
    ** Old combined text may still contain fields that became classified later.
    *******************************************************************************/
   private List<Query> buildFilteredSnippetQueries(String term, List<QuickSearchableTableConfig> configs, Collection<String> allowedTables)
   {
      List<Query> queries = new ArrayList<>();
      for(QuickSearchableTableConfig config : configs == null ? List.<QuickSearchableTableConfig>of() : configs)
      {
         if(!allowedTables.contains(config.getTableName()) || !QuickSearchFieldRules.requiresFieldFiltering(config.getTableName()))
         {
            continue;
         }
         var table = QuickSearchFieldRules.lookupTable(config.getTableName());
         List<String> fields = QuickSearchFieldRules.indexableFields(table, config.getSearchableFields()).stream()
            .filter(config::isIncludedInSnippet).map(f -> PATH_PREFIX + "." + f).toList();
         if(fields.isEmpty() || QuickSearchFieldRules.isProtected(table, table.getPrimaryKeyField()))
         {
            continue;
         }
         queries.add(Query.of(q -> q.bool(b -> b
            .filter(f -> f.term(t -> t.field("sourceTable").value(FieldValue.of(config.getTableName()))))
            .must(m -> m.multiMatch(mm -> mm.query(term).fields(fields).type(TextQueryType.CrossFields)
               .operator(Operator.And).lenient(true))))));
      }
      return (queries);
   }



   /*******************************************************************************
    ** Matches for the fields that are not in searchableText, each limited to
    ** its own table: an EXACT field matches the whole (lower-cased) term, any
    ** other matches every term with its analyzer.
    *******************************************************************************/
   private List<Query> buildOwnFieldMatchQueries(String searchTerm, List<QuickSearchableTableConfig> tableConfigs, Collection<String> allowedTables)
   {
      List<Query> queries = new ArrayList<>();
      for(QuickSearchableTableConfig tableConfig : tableConfigs == null ? List.<QuickSearchableTableConfig>of() : tableConfigs)
      {
         if(QuickSearchFieldRules.hasProtectedPrimaryKey(tableConfig.getTableName()) || !allowedTables.contains(tableConfig.getTableName()) || tableConfig.isAllPlainFields())
         {
            continue;
         }

         Query inTable = Query.of(q -> q.term(t -> t.field("sourceTable").value(FieldValue.of(tableConfig.getTableName()))));
         for(String field : tableConfig.getSearchableFields() == null ? List.<String>of() : tableConfig.getSearchableFields())
         {
            if(tableConfig.isIncludedInSnippet(field)
               || (QuickSearchFieldRules.lookupTable(tableConfig.getTableName()) != null && !QuickSearchFieldRules.isIndexable(QuickSearchFieldRules.lookupTable(tableConfig.getTableName()), field)))
            {
               continue;
            }

            QuickSearchFieldAnalyzer analyzer = tableConfig.getAnalyzer(field);
            Query fieldMatch = analyzer == QuickSearchFieldAnalyzer.EXACT
               ? Query.of(q -> q.term(t -> t.field(PATH_EXACT + "." + field).value(FieldValue.of(IndexingUtils.normalizeExact(searchTerm)))))
               : Query.of(q -> q.multiMatch(mm -> mm.query(searchTerm).fields(fieldPath(analyzer) + "." + field).operator(Operator.And).lenient(true)));
            queries.add(Query.of(q -> q.bool(b -> b.filter(inTable).must(fieldMatch))));
         }
      }
      return (queries);
   }



   /*******************************************************************************
    ** The document object a field's analyzer writes it to.
    *******************************************************************************/
   static String fieldPath(QuickSearchFieldAnalyzer analyzer)
   {
      return (switch(analyzer == null ? QuickSearchFieldAnalyzer.PREFIX : analyzer)
      {
         case WORD -> PATH_WORD;
         case ENGLISH -> PATH_ENGLISH;
         case EXACT -> PATH_EXACT;
         default -> PATH_PREFIX;
      });
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private List<Query> buildFieldBoostQueries(String searchTerm, List<QuickSearchableTableConfig> tableConfigs, Collection<String> allowedTables, boolean named)
   {
      List<Query> queries = new ArrayList<>();
      if(tableConfigs == null)
      {
         return (queries);
      }

      for(QuickSearchableTableConfig tableConfig : tableConfigs)
      {
         if(QuickSearchFieldRules.hasProtectedPrimaryKey(tableConfig.getTableName()) || !allowedTables.contains(tableConfig.getTableName()))
         {
            continue;
         }

         Map<String, Integer> weights = tableConfig.getFieldWeights();
         List<String>         fields  = tableConfig.getSearchableFields() == null ? List.of() : tableConfig.getSearchableFields();
         for(String field : fields)
         {
            if(QuickSearchFieldRules.lookupTable(tableConfig.getTableName()) != null && !QuickSearchFieldRules.isIndexable(QuickSearchFieldRules.lookupTable(tableConfig.getTableName()), field))
            {
               continue;
            }
            float                    boost    = (weights == null || weights.get(field) == null) ? 1f : weights.get(field).floatValue();
            QuickSearchFieldAnalyzer analyzer = tableConfig.getAnalyzer(field);
            if(analyzer == QuickSearchFieldAnalyzer.EXACT)
            {
               String exact = IndexingUtils.normalizeExact(searchTerm);
               queries.add(Query.of(q -> q.term(t ->
               {
                  t.field(PATH_EXACT + "." + field).value(FieldValue.of(exact)).boost(boost);
                  return (named ? t.queryName(FIELD_CLAUSE_PREFIX + field) : t);
               })));
               continue;
            }

            String path = fieldPath(analyzer) + "." + field;
            queries.add(Query.of(q -> q.multiMatch(MultiMatchQuery.of(mm ->
            {
               mm.query(searchTerm)
                  .fields(path)
                  .operator(Operator.And)
                  .lenient(true)
                  .boost(boost);
               return (named ? mm.queryName(FIELD_CLAUSE_PREFIX + field) : mm);
            }))));
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
    ** The underlying OpenSearch client, for the read-only diagnostics
    ** (QuickSearchDiagnosticsOperations).
    *******************************************************************************/
   OpenSearchClient getOpenSearchClient()
   {
      return (client);
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

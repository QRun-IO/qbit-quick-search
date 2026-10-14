/*
 * Copyright 2026 Kingsrook, LLC
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
import java.io.StringWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.config.QuickSearchFieldRules;
import com.kingsrook.qbits.quicksearch.processes.IndexingUtils;
import jakarta.json.stream.JsonGenerator;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.ExplainResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Highlight;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HighlightField;
import org.opensearch.client.opensearch.core.search.MatchedQueries;
import org.opensearch.client.opensearch.core.search.TotalHitsRelation;
import org.opensearch.client.opensearch.generic.Body;
import org.opensearch.client.opensearch.generic.Requests;
import org.opensearch.client.opensearch.generic.Response;


/*******************************************************************************
 ** Read-only OpenSearch operations behind the Quick Search admin diagnostics:
 ** a probe search that reports scores, highlights and matched clauses with
 ** its request and response JSON, explain for one document, document reads,
 ** _analyze, random document sampling, and raw cluster / index / mapping /
 ** settings reads. Nothing here writes to the cluster.
 ** The probe uses exactly the query production search builds (same clauses,
 ** operator, boosts and table filter); it only adds query names and
 ** highlighting on the field values, which change neither matching nor
 ** scoring.
 *******************************************************************************/
public class QuickSearchDiagnosticsOperations
{
   public static final int MAX_ANALYZE_TEXT_LENGTH = 10_000;
   public static final int MAX_PROBE_SIZE          = 100;
   public static final int MAX_SAMPLE_SIZE         = 1_000;

   private static final ObjectMapper MAPPER = new ObjectMapper();

   private final QuickSearchOpenSearchClient quickSearchClient;
   private final OpenSearchClient            client;
   private final String                      indexName;



   /*******************************************************************************
    ** One hit of a probe search.
    *******************************************************************************/
   public record ProbeHit(String recordId, String recordLabel, Double score, List<String> matchedClauses, Map<String, List<String>> highlights)
   {
   }



   /*******************************************************************************
    ** A probe search of one table: request and response JSON, server and
    ** wall-clock time, total hits and the hits.
    *******************************************************************************/
   public record SearchProbe(String tableName, String requestJson, String responseJson, Long tookMillis, Long wallMillis, Long totalHits, Boolean totalIsLowerBound, List<ProbeHit> hits)
   {
   }



   /*******************************************************************************
    ** The result of explain for one document and the search query.
    *******************************************************************************/
   public record ExplainResult(Boolean documentFound, Boolean matched, Double score, String explanationText, String json)
   {
   }



   /*******************************************************************************
    ** One indexed document as stored: whether it exists, its version, the
    ** physical index holding it and its source.
    *******************************************************************************/
   public record DocumentView(Boolean found, Long version, String physicalIndex, JsonNode source)
   {
   }



   /*******************************************************************************
    ** One token produced by _analyze.
    *******************************************************************************/
   public record AnalyzedToken(String token, Integer position, Integer startOffset, Integer endOffset, String type)
   {
   }



   /*******************************************************************************
    ** A raw JSON response: HTTP status and parsed body (null when empty).
    *******************************************************************************/
   public record RawResponse(Integer status, JsonNode body)
   {
      /*******************************************************************************
       ** Whether the status is 2xx.
       *******************************************************************************/
      public boolean isOk()
      {
         return (status != null && status >= 200 && status < 300);
      }
   }



   /*******************************************************************************
    ** Constructor
    *******************************************************************************/
   public QuickSearchDiagnosticsOperations(QuickSearchOpenSearchClient quickSearchClient)
   {
      this.quickSearchClient = quickSearchClient;
      this.client            = quickSearchClient.getOpenSearchClient();
      this.indexName         = quickSearchClient.getIndexName();
   }



   /*******************************************************************************
    ** Getter for the alias name the operations read.
    *******************************************************************************/
   public String getIndexName()
   {
      return (indexName);
   }



   /*******************************************************************************
    ** Run the production search query against one table with query names and
    ** field highlighting, returning up to size hits (capped at MAX_PROBE_SIZE).
    *******************************************************************************/
   public SearchProbe probeSearch(String term, String tableName, int size, List<QuickSearchableTableConfig> tableConfigs) throws QException
   {
      int           boundedSize = Math.max(1, Math.min(size, MAX_PROBE_SIZE));
      Query         query       = quickSearchClient.buildSearchQuery(term, List.of(tableName), tableConfigs, true);
      Highlight highlight = QuickSearchFieldRules.requiresFieldFiltering(tableName) ? null : Highlight.of(h -> h
         .requireFieldMatch(true)
         .fields("searchableText", HighlightField.of(hf -> hf))
         .fields("fieldValues.*", HighlightField.of(hf -> hf)));
      SearchRequest request     = SearchRequest.of(r -> r
         .index(indexName)
         .query(query)
         .highlight(highlight)
         .trackTotalHits(t -> t.count(QuickSearchOpenSearchClient.MAX_RESULT_WINDOW))
         .size(boundedSize));

      long start = System.nanoTime();
      try
      {
         SearchResponse<OpenSearchDocument> response = client.search(request, OpenSearchDocument.class);
         long                               wall     = (System.nanoTime() - start) / 1_000_000;

         List<ProbeHit> hits = new ArrayList<>();
         for(Hit<OpenSearchDocument> hit : response.hits().hits())
         {
            OpenSearchDocument source = hit.source();
            boolean filterFields = QuickSearchFieldRules.requiresFieldFiltering(tableName);
            if(source != null)
            {
               source = IndexingUtils.filterResult(source, tableConfigs.stream().filter(c -> tableName.equals(c.getTableName())).findFirst().orElse(null));
            }
            if(filterFields && source == null)
            {
               continue;
            }
            hits.add(new ProbeHit(
               source == null ? idFromDocumentId(hit.id()) : source.getRecordId(),
               source == null ? null : source.getRecordLabel(),
               hit.score(),
               matchedClauses(hit.matchedQueries()),
               filterFields || hit.highlight() == null ? Map.of() : new LinkedHashMap<>(hit.highlight())));
         }

         Long    total      = response.hits().total() == null ? (long) hits.size() : response.hits().total().value();
         Boolean lowerBound = response.hits().total() != null && response.hits().total().relation() == TotalHitsRelation.Gte;
         return (new SearchProbe(tableName, toJson(request), QuickSearchFieldRules.requiresFieldFiltering(tableName) ? "{}" : toJson(response), response.took(), wall, total, lowerBound, hits));
      }
      catch(OpenSearchException | IOException e)
      {
         throw (wrap("Probe search failed for table [" + tableName + "]", e));
      }
   }



   /*******************************************************************************
    ** Names of the clauses a hit matched (query names or, in newer servers,
    ** the keys of the named-query scores).
    *******************************************************************************/
   static List<String> matchedClauses(MatchedQueries matchedQueries)
   {
      if(matchedQueries == null)
      {
         return (List.of());
      }
      if(matchedQueries.isScores())
      {
         return (new ArrayList<>(matchedQueries.scores().keySet()));
      }
      return (new ArrayList<>(matchedQueries.names()));
   }



   /*******************************************************************************
    ** Explain how the production query scores one document of a table. A
    ** missing document gives documentFound false (not an error).
    *******************************************************************************/
   public ExplainResult explain(String term, String tableName, String recordId, List<QuickSearchableTableConfig> tableConfigs) throws QException
   {
      Query  query      = quickSearchClient.buildSearchQuery(term, List.of(tableName), tableConfigs, false);
      String documentId = OpenSearchDocument.buildDocumentId(tableName, recordId);
      try
      {
         ExplainResponse<OpenSearchDocument> response = client.explain(e -> e.index(indexName).id(documentId).query(query), OpenSearchDocument.class);
         String   json        = toJson(response);
         JsonNode tree        = MAPPER.readTree(json);
         JsonNode explanation = tree.path("explanation");
         Double   score       = explanation.isMissingNode() ? null : explanation.path("value").asDouble();
         return (new ExplainResult(true, response.matched(), score, formatExplanation(explanation), json));
      }
      catch(OpenSearchException e)
      {
         if(e.status() == 404)
         {
            return (new ExplainResult(false, false, null, null, null));
         }
         throw (wrap("Explain failed for document [" + documentId + "]", e));
      }
      catch(IOException e)
      {
         throw (wrap("Explain failed for document [" + documentId + "]", e));
      }
   }



   /*******************************************************************************
    ** An explanation tree as indented text ("value  description"), bounded to
    ** 400 lines.
    *******************************************************************************/
   public static String formatExplanation(JsonNode explanation)
   {
      if(explanation == null || explanation.isMissingNode() || explanation.isNull())
      {
         return ("");
      }
      List<String> lines = new ArrayList<>();
      appendExplanation(explanation, 0, lines);
      return (String.join("\n", lines));
   }



   /*******************************************************************************
    *******************************************************************************/
   private static void appendExplanation(JsonNode node, int depth, List<String> lines)
   {
      if(lines.size() >= 400)
      {
         return;
      }
      lines.add("  ".repeat(depth) + String.format("%.4f", node.path("value").asDouble()) + "  " + node.path("description").asText(""));
      for(JsonNode detail : node.path("details"))
      {
         appendExplanation(detail, depth + 1, lines);
      }
   }



   /*******************************************************************************
    ** Read one document of a table by record id.
    *******************************************************************************/
   public DocumentView getDocument(String tableName, String recordId) throws QException
   {
      RawResponse response = raw("GET", "/" + encode(indexName) + "/_doc/" + encode(OpenSearchDocument.buildDocumentId(tableName, recordId)), null);
      if(response.status() == 404)
      {
         return (new DocumentView(false, null, null, null));
      }
      requireOk(response, "Document read failed");
      JsonNode body = response.body();
      return (new DocumentView(body.path("found").asBoolean(false), body.has("_version") ? body.get("_version").asLong() : null,
         body.path("_index").asText(null), filterDocumentSource(tableName, body.get("_source"))));
   }



   /*******************************************************************************
    ** Read many documents of a table by record id: record id to source, for
    ** the documents that exist.
    *******************************************************************************/
   public Map<String, JsonNode> getDocuments(String tableName, Collection<String> recordIds) throws QException
   {
      Map<String, JsonNode> sources = new LinkedHashMap<>();
      if(recordIds == null || recordIds.isEmpty())
      {
         return (sources);
      }

      ObjectNode body = JsonNodeFactory.instance.objectNode();
      ArrayNode  ids  = body.putArray("ids");
      for(String recordId : recordIds)
      {
         ids.add(OpenSearchDocument.buildDocumentId(tableName, recordId));
      }

      RawResponse response = raw("POST", "/" + encode(indexName) + "/_mget", body.toString());
      requireOk(response, "Document read failed");
      for(JsonNode doc : response.body().path("docs"))
      {
         if(doc.path("found").asBoolean(false) && doc.has("_source"))
         {
            sources.put(idFromDocumentId(doc.path("_id").asText()), filterDocumentSource(tableName, doc.get("_source")));
         }
      }
      return (sources);
   }



   /*******************************************************************************
    ** Old documents may retain protected values. Diagnostic reads expose only
    ** attributable, indexable fields, never the old combined presentation.
    *******************************************************************************/
   private static JsonNode filterDocumentSource(String tableName, JsonNode source)
   {
      if(source == null || !QuickSearchFieldRules.requiresFieldFiltering(tableName))
      {
         return (source);
      }
      ObjectNode filtered = JsonNodeFactory.instance.objectNode();
      filtered.put("sourceTable", tableName);
      var table = QuickSearchFieldRules.lookupTable(tableName);
      if(!QuickSearchFieldRules.isProtected(table, table.getPrimaryKeyField()))
      {
         filtered.set("recordId", source.get("recordId"));
      }
      for(String container : List.of("fieldValues", "fieldValuesWord", "fieldValuesEnglish", "fieldValuesExact"))
      {
         ObjectNode values = filtered.putObject(container);
         source.path(container).fields().forEachRemaining(entry ->
         {
            if(QuickSearchFieldRules.isIndexable(table, entry.getKey()))
            {
               values.set(entry.getKey(), entry.getValue());
            }
         });
      }
      return (filtered);
   }



   /*******************************************************************************
    ** Run _analyze on the index with a named analyzer (an index analyzer or a
    ** built-in one). Text is truncated to MAX_ANALYZE_TEXT_LENGTH.
    *******************************************************************************/
   public List<AnalyzedToken> analyze(String analyzer, String text) throws QException
   {
      String     bounded = text == null ? "" : (text.length() > MAX_ANALYZE_TEXT_LENGTH ? text.substring(0, MAX_ANALYZE_TEXT_LENGTH) : text);
      ObjectNode body    = JsonNodeFactory.instance.objectNode().put("analyzer", analyzer).put("text", bounded);

      RawResponse response = raw("POST", "/" + encode(indexName) + "/_analyze", body.toString());
      requireOk(response, "Analyze failed for analyzer [" + analyzer + "]");

      List<AnalyzedToken> tokens = new ArrayList<>();
      for(JsonNode token : response.body().path("tokens"))
      {
         tokens.add(new AnalyzedToken(token.path("token").asText(), token.path("position").asInt(), token.path("start_offset").asInt(),
            token.path("end_offset").asInt(), token.path("type").asText(null)));
      }
      return (tokens);
   }



   /*******************************************************************************
    ** Up to n record ids of a table's documents, chosen at random.
    *******************************************************************************/
   public List<String> sampleRecordIds(String tableName, int n) throws QException
   {
      int        size  = Math.max(1, Math.min(n, MAX_SAMPLE_SIZE));
      ObjectNode body  = JsonNodeFactory.instance.objectNode();
      body.put("size", size);
      body.putArray("_source").add("recordId");
      ObjectNode functionScore = body.putObject("query").putObject("function_score");
      functionScore.putObject("query").putObject("term").put("sourceTable", tableName);
      functionScore.putObject("random_score");

      RawResponse response = raw("POST", "/" + encode(indexName) + "/_search", body.toString());
      requireOk(response, "Sampling documents failed for table [" + tableName + "]");

      List<String> recordIds = new ArrayList<>();
      for(JsonNode hit : response.body().path("hits").path("hits"))
      {
         String recordId = hit.path("_source").path("recordId").asText(null);
         recordIds.add(recordId != null ? recordId : idFromDocumentId(hit.path("_id").asText()));
      }
      return (recordIds);
   }



   /*******************************************************************************
    ** Cluster health, with the alias's indexes (level=indices).
    *******************************************************************************/
   public RawResponse clusterHealth() throws QException
   {
      return (raw("GET", "/_cluster/health/" + encode(indexName) + "?level=indices", null));
   }



   /*******************************************************************************
    ** The live mapping of the alias's index(es).
    *******************************************************************************/
   public RawResponse mapping() throws QException
   {
      return (raw("GET", "/" + encode(indexName) + "/_mapping", null));
   }



   /*******************************************************************************
    ** The live settings of the alias's index(es).
    *******************************************************************************/
   public RawResponse settings() throws QException
   {
      return (raw("GET", "/" + encode(indexName) + "/_settings", null));
   }



   /*******************************************************************************
    ** The physical indexes named like this alias's ({alias}-v*), with health,
    ** status, document counts and size, from _cat/indices.
    *******************************************************************************/
   public RawResponse physicalIndexes() throws QException
   {
      return (raw("GET", "/_cat/indices/" + encode(indexName) + "-v*?format=json&h=index,health,status,docs.count,docs.deleted,store.size&expand_wildcards=all", null));
   }



   /*******************************************************************************
    ** The record id part of a document id ("table:id").
    *******************************************************************************/
   static String idFromDocumentId(String documentId)
   {
      if(documentId == null)
      {
         return (null);
      }
      int colon = documentId.indexOf(':');
      return (colon < 0 ? documentId : documentId.substring(colon + 1));
   }



   /*******************************************************************************
    ** Send a raw request and parse the JSON body. HTTP errors are returned
    ** (status and body), not thrown; transport errors throw.
    *******************************************************************************/
   public RawResponse raw(String method, String endpoint, String jsonBody) throws QException
   {
      Requests.JsonBodyBuilder builder = Requests.builder().method(method).endpoint(endpoint);
      if(jsonBody != null)
      {
         builder.json(jsonBody);
      }

      try(Response response = client.generic().execute(builder.build()))
      {
         JsonNode body = null;
         if(response.getBody().isPresent())
         {
            try(Body responseBody = response.getBody().get())
            {
               String text = responseBody.bodyAsString();
               body = text == null || text.isBlank() ? null : MAPPER.readTree(text);
            }
         }
         return (new RawResponse(response.getStatus(), body));
      }
      catch(OpenSearchException | IOException e)
      {
         throw (wrap(method + " " + endpoint.replaceAll("\\?.*", "") + " failed", e));
      }
   }



   /*******************************************************************************
    ** Throw a QException with OpenSearch's reason when a raw response is not
    ** 2xx.
    *******************************************************************************/
   public static void requireOk(RawResponse response, String message) throws QException
   {
      if(!response.isOk())
      {
         throw (new QException(message + ": OpenSearch returned status " + response.status() + ": " + errorReason(response.body())));
      }
   }



   /*******************************************************************************
    ** OpenSearch's error reason from an error body, or a short form of it.
    *******************************************************************************/
   public static String errorReason(JsonNode body)
   {
      if(body == null)
      {
         return ("no response body");
      }
      JsonNode error = body.path("error");
      if(error.isTextual())
      {
         return (error.asText());
      }
      String reason = error.path("reason").asText(null);
      if(reason != null)
      {
         return (reason);
      }
      String text = body.toString();
      return (text.length() > 300 ? text.substring(0, 300) + "..." : text);
   }



   /*******************************************************************************
    ** Serialize a request or response with the transport's mapper (which knows
    ** OpenSearchDocument; the default mapper of toJsonString does not).
    *******************************************************************************/
   private String toJson(JsonpSerializable value)
   {
      JsonpMapper  mapper = client._transport().jsonpMapper();
      StringWriter writer = new StringWriter();
      try(JsonGenerator generator = mapper.jsonProvider().createGenerator(writer))
      {
         value.serialize(generator, mapper);
      }
      return (writer.toString());
   }



   /*******************************************************************************
    ** URL-encode one path segment.
    *******************************************************************************/
   static String encode(String segment)
   {
      return (URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
   }



   /*******************************************************************************
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

}

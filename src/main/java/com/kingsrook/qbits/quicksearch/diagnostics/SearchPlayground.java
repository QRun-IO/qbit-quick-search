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

package com.kingsrook.qbits.quicksearch.diagnostics;


import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kingsrook.qqq.backend.core.actions.tables.RecordSearchAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.search.RecordSearchInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.search.RecordSearchOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.search.RecordSearchResult;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchAction;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchRecordSearchProvider;
import com.kingsrook.qbits.quicksearch.diagnostics.DiagnosticsHtml.Level;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchDiagnosticsOperations;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchDiagnosticsOperations.AnalyzedToken;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchDiagnosticsOperations.ExplainResult;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchDiagnosticsOperations.ProbeHit;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchDiagnosticsOperations.SearchProbe;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import com.kingsrook.qbits.quicksearch.processes.IndexingUtils;


/*******************************************************************************
 ** The search playground: runs a term the way the global search does, as the
 ** current user, and shows everything behind it.
 ** 1. Which path serves each table and why (SearchPathDiagnosis).
 ** 2. The provider's own OpenSearch request (_msearch of record ids), timed,
 **    without starting a backoff when it fails.
 ** 3. The real global search (core RecordSearchAction) as the current user:
 **    what users see, per table, timed. Like any user search, a provider
 **    failure here starts the provider's normal backoff.
 ** 4. Per indexed table, the production query with scores, highlights and
 **    matched clauses, which hits users can read (record locks), which ones
 **    the global search shows, the query DSL and the raw response (hits the
 **    user cannot read are redacted to their id).
 ** 5. Optionally, explain for one hit, and the term's tokens.
 ** Read-only: no index or record is changed.
 *******************************************************************************/
public class SearchPlayground
{
   private static final ObjectMapper MAPPER = new ObjectMapper();



   /*******************************************************************************
    ** What to run: term, tables (empty for every searchable table), results per
    ** table as in the global search, and an optional hit to explain
    ** ("table:recordId").
    *******************************************************************************/
   public record Input(String term, List<String> tableNames, Integer limitPerTable, String explainDocumentId)
   {
   }



   /*******************************************************************************
    ** One table's probe: the search, which hit ids the user can read, which
    ** the global search showed, and an error when the probe failed.
    *******************************************************************************/
   public record TableProbe(String tableName, SearchProbe probe, Set<String> readableIds, Set<String> shownIds, Integer hiddenCount, String error)
   {
   }



   /*******************************************************************************
    ** Everything the playground found.
    *******************************************************************************/
   public record Report(String term, List<String> notices, List<AnalyzedToken> termTokens, String termTokensError,
                        SearchPathDiagnosis.ProviderState providerState, List<SearchPathDiagnosis.TablePath> paths,
                        Map<String, List<String>> providerIds, Map<String, Integer> providerHiddenCounts, Long providerMillis, String providerError,
                        Map<String, List<RecordSearchResult>> coreResults, Long coreMillis, String coreError,
                        List<TableProbe> probes, String explainDocumentId, ExplainResult explain, String explainError,
                        Integer limitPerTable, Integer idsPerTable)
   {
   }



   /*******************************************************************************
    ** Run the playground for the input as the current session.
    *******************************************************************************/
   public Report run(QuickSearchRuntime runtime, Input input, AbstractActionInput permissionInput)
   {
      QInstance             qInstance = QContext.getQInstance();
      QuickSearchQBitConfig config    = runtime.getConfig();
      List<String>          notices   = new ArrayList<>();

      String term          = IndexingUtils.normalizeSearchText(input.term());
      int    limitPerTable = input.limitPerTable() == null || input.limitPerTable() <= 0 ? RecordSearchAction.DEFAULT_LIMIT_PER_TABLE : Math.min(input.limitPerTable(), RecordSearchAction.MAX_LIMIT_PER_TABLE);
      int    idsPerTable   = Math.min(RecordSearchAction.MAX_PROVIDER_IDS_PER_TABLE, limitPerTable * QuickSearchRecordSearchProvider.IDS_PER_LIMIT_MULTIPLIER);

      List<String> tables = resolveTables(qInstance, runtime, input.tableNames(), notices);
      List<SearchPathDiagnosis.TablePath> paths = SearchPathDiagnosis.diagnose(qInstance, runtime, tables, permissionInput);
      SearchPathDiagnosis.ProviderState   state = SearchPathDiagnosis.providerState(qInstance, runtime);

      if(term.length() < QuickSearchAction.MIN_TERM_LENGTH)
      {
         notices.add("The term is shorter than " + QuickSearchAction.MIN_TERM_LENGTH + " characters: the Quick Search provider returns nothing for it, so only core's own search can match.");
      }
      if(term.length() > RecordSearchAction.MAX_SEARCH_TERM_LENGTH)
      {
         notices.add("The term is longer than " + RecordSearchAction.MAX_SEARCH_TERM_LENGTH + " characters: the global search rejects it.");
      }

      QuickSearchDiagnosticsOperations ops = null;
      String                           opsError = null;
      try
      {
         ops = new QuickSearchDiagnosticsOperations(runtime.getClient());
      }
      catch(QException e)
      {
         opsError = e.getMessage();
         notices.add("The OpenSearch client could not be built: " + e.getMessage());
      }

      ////////////////////////
      // term tokens        //
      ////////////////////////
      List<AnalyzedToken> termTokens      = null;
      String              termTokensError = opsError;
      if(ops != null && StringUtils.hasContent(term))
      {
         try
         {
            termTokens = ops.analyze(QuickSearchOpenSearchClient.SEARCH_ANALYZER, term);
         }
         catch(QException e)
         {
            termTokensError = e.getMessage();
         }
      }

      ///////////////////////////////////////////////////////////
      // the provider's own request, for the tables it claims  //
      ///////////////////////////////////////////////////////////
      List<String> claimed = paths.stream().filter(p -> SearchPathDiagnosis.PATH_OPENSEARCH.equals(p.path())).map(SearchPathDiagnosis.TablePath::tableName).toList();
      Map<String, List<String>> providerIds    = null;
      Map<String, Integer>      providerHidden = new LinkedHashMap<>();
      Long                      providerMillis = null;
      String                    providerError  = null;
      if(!claimed.isEmpty() && ops != null && term.length() >= QuickSearchAction.MIN_TERM_LENGTH)
      {
         int  timeout = state.timeoutMillis();
         long start   = System.currentTimeMillis();
         try
         {
            providerIds = runtime.getClient().searchRecordIdsPerTable(term, claimed, idsPerTable, runtime.getDiscoveredTables(), timeout);
         }
         catch(QException | RuntimeException e)
         {
            providerError = e.getClass().getSimpleName() + ": " + e.getMessage();
         }
         providerMillis = System.currentTimeMillis() - start;
         providerIds    = providerIds == null ? null : readableOnly(providerIds, providerHidden);
      }

      ///////////////////////////////////////////////////////
      // the real global search, as the current user       //
      ///////////////////////////////////////////////////////
      Map<String, List<RecordSearchResult>> coreResults = new LinkedHashMap<>();
      Long                                  coreMillis  = null;
      String                                coreError   = null;
      List<String> searched = paths.stream().filter(p -> !SearchPathDiagnosis.PATH_NONE.equals(p.path())).map(SearchPathDiagnosis.TablePath::tableName).toList();
      if(!searched.isEmpty() && StringUtils.hasContent(term))
      {
         long start = System.currentTimeMillis();
         try
         {
            RecordSearchOutput output = new RecordSearchAction().execute(new RecordSearchInput().withSearchTerm(term).withTableNames(searched).withLimitPerTable(limitPerTable));
            for(RecordSearchResult result : CollectionUtils.nonNullList(output.getResults()))
            {
               coreResults.computeIfAbsent(result.getTableName(), k -> new ArrayList<>()).add(result);
            }
         }
         catch(Exception e)
         {
            coreError = e.getClass().getSimpleName() + ": " + e.getMessage();
         }
         coreMillis = System.currentTimeMillis() - start;
      }

      //////////////////////////////////////////
      // per indexed table, the probe search  //
      //////////////////////////////////////////
      List<TableProbe> probes = new ArrayList<>();
      for(SearchPathDiagnosis.TablePath path : paths)
      {
         if(!Boolean.TRUE.equals(path.indexed()) || !Boolean.TRUE.equals(path.readable()) || term.length() < QuickSearchAction.MIN_TERM_LENGTH)
         {
            continue;
         }
         if(ops == null)
         {
            probes.add(new TableProbe(path.tableName(), null, Set.of(), Set.of(), 0, opsError));
            continue;
         }
         probes.add(probeTable(ops, runtime, term, path.tableName(), idsPerTable, coreResults.get(path.tableName())));
      }

      //////////////////////
      // explain one hit  //
      //////////////////////
      ExplainResult explain      = null;
      String        explainError = null;
      String        explainId    = StringUtils.hasContent(input.explainDocumentId()) ? input.explainDocumentId().trim() : null;
      if(explainId != null && ops != null)
      {
         try
         {
            explain = explainHit(ops, runtime, term, explainId);
         }
         catch(QException e)
         {
            explainError = e.getMessage();
         }
      }

      return (new Report(term, notices, termTokens, termTokensError, state, paths, providerIds, providerHidden, providerMillis, providerError,
         coreResults, coreMillis, coreError, probes, explainId, explain, explainError, limitPerTable, idsPerTable));
   }



   /*******************************************************************************
    ** The requested tables that exist (else every table with core searchFields
    ** or a Quick Search index), in instance order.
    *******************************************************************************/
   static List<String> resolveTables(QInstance qInstance, QuickSearchRuntime runtime, List<String> requested, List<String> notices)
   {
      List<String> tables = new ArrayList<>();
      if(!CollectionUtils.nullSafeIsEmpty(requested))
      {
         for(String tableName : new LinkedHashSet<>(requested))
         {
            if(qInstance.getTable(tableName) == null)
            {
               notices.add("Ignored unknown table [" + tableName + "].");
            }
            else
            {
               tables.add(tableName);
            }
         }
         return (tables);
      }

      for(QTableMetaData table : CollectionUtils.nonNullMap(qInstance.getTables()).values())
      {
         if(!CollectionUtils.nullSafeIsEmpty(table.getSearchFields()) || runtime.getTableConfig(table.getName()) != null)
         {
            tables.add(table.getName());
         }
      }
      return (tables);
   }



   /*******************************************************************************
    ** Probe one table and mark which hits the user can read and which ones the
    ** global search showed.
    *******************************************************************************/
   private TableProbe probeTable(QuickSearchDiagnosticsOperations ops, QuickSearchRuntime runtime, String term, String tableName, int size, List<RecordSearchResult> shown)
   {
      try
      {
         SearchProbe  probe = ops.probeSearch(term, tableName, size, runtime.getDiscoveredTables());
         List<String> ids   = probe.hits().stream().map(ProbeHit::recordId).toList();

         Set<String> readable = new HashSet<>(DiagnosticRecordReads.existingIds(tableName, ids));
         Set<String> shownIds = new HashSet<>();
         for(RecordSearchResult result : CollectionUtils.nonNullList(shown))
         {
            shownIds.add(String.valueOf(result.getRecordId()));
         }

         List<ProbeHit> visible = probe.hits().stream().filter(h -> readable.contains(h.recordId())).toList();
         int            hidden  = probe.hits().size() - visible.size();
         SearchProbe redacted = new SearchProbe(probe.tableName(), probe.requestJson(), redactResponse(probe.responseJson(), readable),
            probe.tookMillis(), probe.wallMillis(), hidden == 0 ? probe.totalHits() : null, probe.totalIsLowerBound(), visible);
         return (new TableProbe(tableName, redacted, readable, shownIds, hidden, null));
      }
      catch(QException e)
      {
         return (new TableProbe(tableName, null, Set.of(), Set.of(), 0, e.getMessage()));
      }
   }



   /*******************************************************************************
    ** The provider's ids per table, keeping only records the user can read;
    ** how many were dropped goes in hiddenCounts. Ids of records the user
    ** cannot read are never shown (they would reveal what a locked record
    ** contains).
    *******************************************************************************/
   static Map<String, List<String>> readableOnly(Map<String, List<String>> idsByTable, Map<String, Integer> hiddenCounts)
   {
      Map<String, List<String>> readable = new LinkedHashMap<>();
      for(Map.Entry<String, List<String>> entry : idsByTable.entrySet())
      {
         List<String> ids = entry.getValue() == null ? List.of() : entry.getValue();
         Set<String>  ok;
         try
         {
            ok = new HashSet<>(DiagnosticRecordReads.existingIds(entry.getKey(), ids));
         }
         catch(QException e)
         {
            ok = Set.of();
         }
         Set<String> finalOk = ok;
         readable.put(entry.getKey(), ids.stream().filter(finalOk::contains).toList());
         hiddenCounts.put(entry.getKey(), (int) ids.stream().filter(id -> !finalOk.contains(id)).count());
      }
      return (readable);
   }



   /*******************************************************************************
    ** The raw response without the hits the user cannot read: they are
    ** removed whole (id, score, source, highlights, matched queries), and
    ** when any was removed the total and max score are redacted too, so the
    ** response reveals nothing about records the user cannot read.
    *******************************************************************************/
   static String redactResponse(String responseJson, Set<String> readableIds)
   {
      try
      {
         JsonNode root    = MAPPER.readTree(responseJson);
         JsonNode hitList = root.path("hits").path("hits");
         int      removed = 0;
         if(hitList.isArray())
         {
            ArrayNode array = (ArrayNode) hitList;
            for(int i = array.size() - 1; i >= 0; i--)
            {
               String recordId = array.get(i).path("_source").path("recordId").asText(null);
               if(recordId == null || !readableIds.contains(recordId))
               {
                  array.remove(i);
                  removed++;
               }
            }
         }
         if(removed > 0 && root.path("hits") instanceof ObjectNode hits)
         {
            hits.put("total", "redacted: " + removed + " hit(s) you cannot read were removed");
            hits.remove("max_score");
         }
         return (root.toString());
      }
      catch(Exception e)
      {
         return ("{\"redacted\":\"response could not be parsed for redaction\"}");
      }
   }



   /*******************************************************************************
    ** Explain one hit ("table:recordId") when the table is indexed and the user
    ** can read the record.
    *******************************************************************************/
   private ExplainResult explainHit(QuickSearchDiagnosticsOperations ops, QuickSearchRuntime runtime, String term, String documentId) throws QException
   {
      int colon = documentId.indexOf(':');
      if(colon <= 0 || colon == documentId.length() - 1)
      {
         throw (new QException("Give the hit as table:recordId (its document id), for example blogPost:42"));
      }
      String tableName = documentId.substring(0, colon);
      String recordId  = documentId.substring(colon + 1);

      QuickSearchableTableConfig tableConfig = runtime.getTableConfig(tableName);
      if(tableConfig == null)
      {
         throw (new QException("Table [" + tableName + "] is not indexed by Quick Search"));
      }
      if(DiagnosticRecordReads.existingIds(tableName, List.of(recordId)).isEmpty())
      {
         throw (new QException("Record " + recordId + " of [" + tableName + "] does not exist or you cannot read it"));
      }
      return (ops.explain(term, tableName, recordId, runtime.getDiscoveredTables()));
   }



   /*******************************************************************************
    ** Render a report as HTML.
    *******************************************************************************/
   public static String render(Report report)
   {
      DiagnosticsHtml html = new DiagnosticsHtml().open();
      html.heading("Search playground: \"" + report.term() + "\"");
      html.paragraph("Run as you, at " + Instant.now() + ". The global search shows up to " + report.limitPerTable() + " results per table; the Quick Search provider asks OpenSearch for the top "
         + report.idsPerTable() + " ids per table and core keeps the first " + report.limitPerTable() + " you can read.");

      for(String notice : report.notices())
      {
         html.status(Level.WARN, notice);
      }

      renderProviderState(html, report.providerState());

      html.subheading("Path per table");
      List<String> pathItems = new ArrayList<>();
      for(SearchPathDiagnosis.TablePath path : report.paths())
      {
         Level level = SearchPathDiagnosis.PATH_OPENSEARCH.equals(path.path()) ? Level.OK : (SearchPathDiagnosis.PATH_CORE.equals(path.path()) ? Level.INFO : Level.WARN);
         pathItems.add(DiagnosticsHtml.badge(level) + " <strong>" + DiagnosticsHtml.escape(path.tableLabel()) + "</strong> (" + DiagnosticsHtml.escape(path.tableName()) + "): "
            + DiagnosticsHtml.escape(path.path()) + ". " + DiagnosticsHtml.escape(path.reason()));
      }
      if(pathItems.isEmpty())
      {
         html.paragraph("No table is searchable by the global search.");
      }
      html.rawList(pathItems);

      html.subheading("Term tokens (search analyzer)");
      if(report.termTokensError() != null)
      {
         html.status(Level.FAIL, "Could not analyze the term: " + report.termTokensError());
      }
      else if(report.termTokens() != null)
      {
         html.paragraph(tokenSummary(report.termTokens()));
         for(AnalyzedToken token : report.termTokens())
         {
            if(token.token().length() < 2)
            {
               html.status(Level.WARN, "\"" + token.token() + "\" is one character: the index holds edge n-grams of 2 to 20 characters, so it can never match, and every term must match.");
            }
            else if(token.token().length() > 20)
            {
               html.status(Level.WARN, "\"" + token.token() + "\" is longer than 20 characters: the index holds edge n-grams of at most 20 characters, so it can never match.");
            }
         }
      }

      html.subheading("Quick Search provider request");
      if(report.providerMillis() == null)
      {
         html.paragraph("Not run: the provider claims none of these tables (see the paths above), or the term is too short.");
      }
      else if(report.providerError() != null)
      {
         html.status(Level.FAIL, "Failed after " + report.providerMillis() + " ms: " + report.providerError() + ". A user search hitting this falls back to core's database search for these tables.");
      }
      else
      {
         html.status(Level.OK, "Answered in " + report.providerMillis() + " ms (wall clock, limit " + report.providerState().timeoutMillis() + " ms).");
         List<String> items = new ArrayList<>();
         for(Map.Entry<String, List<String>> entry : report.providerIds().entrySet())
         {
            Integer hidden = report.providerHiddenCounts().getOrDefault(entry.getKey(), 0);
            items.add("<strong>" + DiagnosticsHtml.escape(entry.getKey()) + "</strong>: " + entry.getValue().size() + " id(s) you can read"
               + (hidden > 0 ? " (" + hidden + " more you cannot read, not shown)" : "")
               + (entry.getValue().isEmpty() ? "" : ": " + DiagnosticsHtml.escape(String.join(", ", entry.getValue().subList(0, Math.min(20, entry.getValue().size()))))
               + (entry.getValue().size() > 20 ? ", ..." : "")));
         }
         html.rawList(items);
      }

      html.subheading("Global search results (what you see)");
      if(report.coreMillis() == null)
      {
         html.paragraph("Not run: no table is searchable, or the term is empty.");
      }
      else if(report.coreError() != null)
      {
         html.status(Level.FAIL, "The global search failed after " + report.coreMillis() + " ms: " + report.coreError());
      }
      else
      {
         int total = report.coreResults().values().stream().mapToInt(List::size).sum();
         html.status(total == 0 ? Level.INFO : Level.OK, total + " result(s) in " + report.coreMillis() + " ms.");
         List<String> items = new ArrayList<>();
         for(Map.Entry<String, List<RecordSearchResult>> entry : report.coreResults().entrySet())
         {
            List<String> labels = new ArrayList<>();
            for(RecordSearchResult result : entry.getValue())
            {
               labels.add(DiagnosticsHtml.escape(result.getRecordLabel() == null ? String.valueOf(result.getRecordId()) : result.getRecordLabel()) + " (#" + DiagnosticsHtml.escape(result.getRecordId()) + ")");
            }
            items.add("<strong>" + DiagnosticsHtml.escape(entry.getKey()) + "</strong>: " + String.join("; ", labels));
         }
         html.rawList(items);
      }

      html.subheading("OpenSearch detail per indexed table");
      if(report.probes().isEmpty())
      {
         html.paragraph("No indexed table you can read was searched, or the term is too short.");
      }
      for(TableProbe tableProbe : report.probes())
      {
         renderProbe(html, tableProbe);
      }

      if(report.explainDocumentId() != null)
      {
         html.subheading("Explain " + report.explainDocumentId());
         if(report.explainError() != null)
         {
            html.status(Level.FAIL, report.explainError());
         }
         else if(report.explain() != null && !Boolean.TRUE.equals(report.explain().documentFound()))
         {
            html.status(Level.FAIL, "There is no document " + report.explainDocumentId() + " in the index.");
         }
         else if(report.explain() != null)
         {
            html.status(Boolean.TRUE.equals(report.explain().matched()) ? Level.OK : Level.WARN,
               Boolean.TRUE.equals(report.explain().matched()) ? "Matches with score " + report.explain().score() + "." : "Does not match this term.");
            html.textDetails("Score explanation", report.explain().explanationText());
            html.jsonDetails("Explain response (JSON)", report.explain().json());
         }
      }

      return (html.close().toString());
   }



   /*******************************************************************************
    *******************************************************************************/
   private static void renderProviderState(DiagnosticsHtml html, SearchPathDiagnosis.ProviderState state)
   {
      html.subheading("Record search provider (this server)");
      List<String[]> facts = new ArrayList<>();
      facts.add(new String[] { "Registered provider", state.registeredProvider() == null ? null : state.registeredProvider() + (Boolean.TRUE.equals(state.quickSearchProvider()) ? " (Quick Search)" : " (not Quick Search)") });
      facts.add(new String[] { "serveCoreRecordSearch", String.valueOf(state.serveCoreRecordSearch()) });
      facts.add(new String[] { "Timeout", state.timeoutMillis() + " ms" });
      facts.add(new String[] { "Backing off", state.backoffRemainingMillis() > 0 ? "yes, " + (state.backoffRemainingMillis() / 1000) + " s left" : "no" });
      facts.add(new String[] { "Failures since " + state.runtimeStartedAt(), String.valueOf(state.failureCount()) });
      facts.add(new String[] { "Table searches core ran itself during backoffs", String.valueOf(state.backoffDeclinedClaims()) });
      facts.add(new String[] { "Last failure", state.lastFailureTime() == null ? null : state.lastFailureTime() + ": " + state.lastFailureMessage() });
      html.facts(facts);
   }



   /*******************************************************************************
    *******************************************************************************/
   private static void renderProbe(DiagnosticsHtml html, TableProbe tableProbe)
   {
      html.raw("<h4>" + DiagnosticsHtml.escape(tableProbe.tableName()) + "</h4>");
      if(tableProbe.error() != null)
      {
         html.status(Level.FAIL, "OpenSearch search failed: " + tableProbe.error());
         return;
      }

      SearchProbe probe = tableProbe.probe();
      html.paragraph((probe.totalHits() == null ? "Matching documents: not shown (some are records you cannot read)"
         : probe.totalHits() + (Boolean.TRUE.equals(probe.totalIsLowerBound()) ? "+" : "") + " matching document(s)")
         + "; OpenSearch took " + probe.tookMillis() + " ms (" + probe.wallMillis() + " ms wall clock). Showing the " + probe.hits().size() + " top hit(s) you can read.");
      if(tableProbe.hiddenCount() != null && tableProbe.hiddenCount() > 0)
      {
         html.status(Level.INFO, tableProbe.hiddenCount() + " of the top hits are records you cannot read (deleted, or hidden by a record security lock); they are not shown.");
      }

      List<String> items    = new ArrayList<>();
      int          position = 0;
      for(ProbeHit hit : probe.hits())
      {
         position++;
         StringBuilder item = new StringBuilder();
         item.append("#").append(position).append(" <strong>")
            .append(DiagnosticsHtml.escape(hit.recordLabel() == null ? "(no label)" : hit.recordLabel()))
            .append("</strong> &middot; document <code>").append(DiagnosticsHtml.escape(OpenSearchDocument.buildDocumentId(tableProbe.tableName(), hit.recordId()))).append("</code>")
            .append(" &middot; score ").append(hit.score() == null ? "n/a" : String.format("%.3f", hit.score()))
            .append(" &middot; ").append(tableProbe.shownIds().contains(hit.recordId()) ? "shown in global search" : "not shown (beyond the per-table limit)");
         if(!hit.matchedClauses().isEmpty())
         {
            List<String> fields = hit.matchedClauses().stream().map(c -> c.startsWith(QuickSearchOpenSearchClient.FIELD_CLAUSE_PREFIX) ? c.substring(QuickSearchOpenSearchClient.FIELD_CLAUSE_PREFIX.length()) : c).toList();
            item.append("<br>Matched: ").append(DiagnosticsHtml.escape(String.join(", ", fields)));
         }
         for(Map.Entry<String, List<String>> highlight : hit.highlights().entrySet())
         {
            item.append("<br><em>").append(DiagnosticsHtml.escape(highlight.getKey())).append(":</em> ")
               .append(DiagnosticsHtml.highlight(String.join(" ... ", highlight.getValue())));
         }
         items.add(item.toString());
      }
      if(items.isEmpty())
      {
         html.paragraph("No document of this table that you can read matches every term.");
      }
      html.rawList(items);
      html.jsonDetails("Query DSL", probe.requestJson());
      html.jsonDetails("Raw response (hits you cannot read removed)", probe.responseJson());
   }



   /*******************************************************************************
    ** "a, b, c" of token strings.
    *******************************************************************************/
   static String tokenSummary(List<AnalyzedToken> tokens)
   {
      if(tokens.isEmpty())
      {
         return ("No tokens: the term has no searchable characters.");
      }
      List<String> parts = new ArrayList<>();
      for(AnalyzedToken token : tokens)
      {
         parts.add("\"" + token.token() + "\"");
      }
      return (tokens.size() + " token(s), all of which must match: " + String.join(", ", parts));
   }

}

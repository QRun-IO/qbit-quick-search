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


import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper;
import com.kingsrook.qqq.backend.core.actions.permissions.TablePermissionSubType;
import com.kingsrook.qqq.backend.core.actions.tables.RecordSearchAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchAction;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchRecordSearchProvider;
import com.kingsrook.qbits.quicksearch.diagnostics.DiagnosticsHtml.Check;
import com.kingsrook.qbits.quicksearch.diagnostics.DiagnosticsHtml.Level;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchDiagnosticsOperations;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchDiagnosticsOperations.AnalyzedToken;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchDiagnosticsOperations.DocumentView;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchDiagnosticsOperations.ExplainResult;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchDiagnosticsOperations.ProbeHit;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchDiagnosticsOperations.SearchProbe;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import com.kingsrook.qbits.quicksearch.processes.IndexingUtils;


/*******************************************************************************
 ** Inspect one record's search document: the document as indexed next to the
 ** document indexing would write from the database now (field by field),
 ** and, given a term, why the record does or does not match it.
 ** The match analysis walks the same gates as the global search: term
 ** length, table indexed and enabled, read permission, record readable,
 ** document present and current, every query token present among the
 ** document's indexed tokens (edge n-grams of 2 to 20 characters, AND
 ** semantics), explain, and the record's rank against the provider's
 ** per-table id window. Read-only.
 *******************************************************************************/
public class RecordInspector
{
   public static final String VERDICT_CURRENT     = "CURRENT";
   public static final String VERDICT_STALE       = "STALE";
   public static final String VERDICT_MISSING     = "MISSING";
   public static final String VERDICT_NOT_INDEXED = "NOT_INDEXED";
   public static final String VERDICT_NO_RECORD   = "NO_RECORD";

   private static final int RANK_WINDOW = RecordSearchAction.MAX_PROVIDER_IDS_PER_TABLE;



   /*******************************************************************************
    ** One compared value: name, value from the database, value in the index.
    *******************************************************************************/
   public record FieldDiff(String name, String databaseValue, String indexValue)
   {
      /*******************************************************************************
       ** Whether the two values are equal.
       *******************************************************************************/
      public boolean isSame()
      {
         return (Objects.equals(databaseValue, indexValue));
      }
   }



   /*******************************************************************************
    ** The inspection of one record.
    *******************************************************************************/
   public record Inspection(String tableName, String recordId, String verdict, List<FieldDiff> diffs, DocumentView indexed, OpenSearchDocument expected,
                            List<Check> matchChecks, ExplainResult explain, String error)
   {
   }



   /*******************************************************************************
    ** Inspect one record, with an optional term to analyze.
    *******************************************************************************/
   public Inspection inspect(QuickSearchRuntime runtime, String tableName, String recordId, String term, AbstractActionInput permissionInput) throws QException
   {
      QuickSearchableTableConfig tableConfig = runtime.getTableConfig(tableName);
      if(tableConfig == null)
      {
         return (new Inspection(tableName, recordId, VERDICT_NOT_INDEXED, List.of(), null, null, List.of(), null, "Quick Search does not index table [" + tableName + "]."));
      }

      Map<String, QRecord> records = DiagnosticRecordReads.loadForIndexing(tableName, List.of(recordId));
      QRecord              record  = records.get(recordId);
      if(record == null && !records.isEmpty())
      {
         record = records.values().iterator().next();
      }
      if(record == null)
      {
         return (new Inspection(tableName, recordId, VERDICT_NO_RECORD, List.of(), null, null, List.of(), null,
            "Record " + recordId + " does not exist, or you cannot read it."));
      }

      QuickSearchDiagnosticsOperations ops      = new QuickSearchDiagnosticsOperations(runtime.getClient());
      OpenSearchDocument               expected = IndexingUtils.buildDocument(record, tableConfig);
      DocumentView                     indexed  = ops.getDocument(tableName, recordId);
      List<FieldDiff>                  diffs    = diff(tableConfig, expected, indexed);

      String verdict = !Boolean.TRUE.equals(indexed.found()) ? VERDICT_MISSING : (diffs.stream().allMatch(FieldDiff::isSame) ? VERDICT_CURRENT : VERDICT_STALE);

      List<Check>   checks  = new ArrayList<>();
      ExplainResult explain = null;
      String        cleaned = IndexingUtils.normalizeSearchText(term);
      if(StringUtils.hasContent(cleaned))
      {
         explain = analyzeMatch(runtime, ops, tableConfig, recordId, cleaned, verdict, expected, indexed, checks, permissionInput);
      }

      return (new Inspection(tableName, recordId, verdict, diffs, indexed, expected, checks, explain, null));
   }



   /*******************************************************************************
    ** Compare the expected document with the indexed one: label, searchable
    ** text, then each configured field (in configuration order) and any extra
    ** field only the index holds.
    *******************************************************************************/
   static List<FieldDiff> diff(QuickSearchableTableConfig tableConfig, OpenSearchDocument expected, DocumentView indexed)
   {
      JsonNode source = indexed == null || indexed.source() == null ? null : indexed.source();

      List<FieldDiff> diffs = new ArrayList<>();
      diffs.add(new FieldDiff("recordLabel", expected == null ? null : blankToNull(expected.getRecordLabel()), text(source, "recordLabel")));
      diffs.add(new FieldDiff("searchableText", expected == null ? null : blankToNull(expected.getSearchableText()), text(source, "searchableText")));

      Set<String> fieldNames = new LinkedHashSet<>(CollectionUtils.nonNullList(tableConfig.getSearchableFields()));
      if(source != null && source.has("fieldValues"))
      {
         source.get("fieldValues").fieldNames().forEachRemaining(fieldNames::add);
      }
      for(String field : fieldNames)
      {
         Object expectedValue = expected == null || expected.getFieldValues() == null ? null : expected.getFieldValues().get(field);
         diffs.add(new FieldDiff("fieldValues." + field, expectedValue == null ? null : String.valueOf(expectedValue),
            source == null ? null : text(source.path("fieldValues"), field)));
      }
      return (diffs);
   }



   /*******************************************************************************
    ** Walk the global search's gates for this record and term, adding a check
    ** per gate; returns the explain result when the document exists.
    *******************************************************************************/
   private ExplainResult analyzeMatch(QuickSearchRuntime runtime, QuickSearchDiagnosticsOperations ops, QuickSearchableTableConfig tableConfig, String recordId, String term, String verdict,
      OpenSearchDocument expected, DocumentView indexed, List<Check> checks, AbstractActionInput permissionInput) throws QException
   {
      String tableName = tableConfig.getTableName();

      checks.add(term.length() < QuickSearchAction.MIN_TERM_LENGTH
         ? new Check("Term length", Level.FAIL, "Terms shorter than " + QuickSearchAction.MIN_TERM_LENGTH + " characters never reach OpenSearch.")
         : new Check("Term length", Level.OK, term.length() + " characters."));

      boolean enabled = runtime.isTableEnabled(tableName);
      checks.add(enabled
         ? new Check("Table enabled", Level.OK, "The table is enabled in Quick Search Index.")
         : new Check("Table enabled", Level.FAIL, "The table is disabled in Quick Search Index, so Quick Search does not search it (core searches it with its own database search)."));

      boolean readable = PermissionsHelper.hasTablePermission(permissionInput, tableName, TablePermissionSubType.READ);
      checks.add(readable
         ? new Check("Table permission", Level.OK, "You can read this table.")
         : new Check("Table permission", Level.FAIL, "You cannot read this table, so the global search skips it for you."));

      checks.add(new Check("Record access", Level.OK, "You can read this record (record security locks allow it)."));

      switch(verdict)
      {
         case VERDICT_MISSING -> checks.add(new Check("Indexed document", Level.FAIL,
            "The record has no document in the index, so OpenSearch cannot return it. Use Reindex in Search on the record (or wait for the next basepull / reconcile)."));
         case VERDICT_STALE -> checks.add(new Check("Indexed document", Level.WARN,
            "The indexed document differs from the database (see the comparison); the search matches the indexed values. Reindex the record to refresh it."));
         default -> checks.add(new Check("Indexed document", Level.OK, "The indexed document matches the database."));
      }

      List<AnalyzedToken> queryTokens = ops.analyze(QuickSearchOpenSearchClient.SEARCH_ANALYZER, term);
      Set<String>         indexedGrams = Boolean.TRUE.equals(indexed.found()) ? tokenSet(ops.analyze(QuickSearchOpenSearchClient.INDEX_ANALYZER, text(indexed.source(), "searchableText")))
         : Set.of();
      Set<String>         expectedGrams = expected == null ? Set.of() : tokenSet(ops.analyze(QuickSearchOpenSearchClient.INDEX_ANALYZER, expected.getSearchableText()));

      if(queryTokens.isEmpty())
      {
         checks.add(new Check("Term tokens", Level.FAIL, "The term has no searchable characters after analysis."));
      }
      for(AnalyzedToken token : queryTokens)
      {
         String value = token.token();
         if(value.length() < 2)
         {
            checks.add(new Check("Token \"" + value + "\"", Level.FAIL, "One character: the index holds edge n-grams of 2 to 20 characters, so a single character never matches, and every token must match."));
         }
         else if(value.length() > 20)
         {
            checks.add(new Check("Token \"" + value + "\"", Level.FAIL, "Longer than 20 characters: the index holds edge n-grams of at most 20 characters, so this token never matches. Search a shorter prefix."));
         }
         else if(indexedGrams.contains(value))
         {
            checks.add(new Check("Token \"" + value + "\"", Level.OK, "Found in the indexed text (as a word or a word prefix)."));
         }
         else if(expectedGrams.contains(value))
         {
            checks.add(new Check("Token \"" + value + "\"", Level.WARN, "Not in the indexed text, but in the database's current values: it will match once the record is reindexed."));
         }
         else
         {
            checks.add(new Check("Token \"" + value + "\"", Level.FAIL, "Not in this record's searchable text: no searchable field holds a word starting with it. Every token must match (AND)."));
         }
      }

      ExplainResult explain = null;
      if(Boolean.TRUE.equals(indexed.found()))
      {
         explain = ops.explain(term, tableName, recordId, runtime.getDiscoveredTables());
         checks.add(Boolean.TRUE.equals(explain.matched())
            ? new Check("OpenSearch explain", Level.OK, "The search query matches this document with score " + String.format("%.4f", explain.score()) + ".")
            : new Check("OpenSearch explain", Level.FAIL, "The search query does not match this document."));

         if(Boolean.TRUE.equals(explain.matched()))
         {
            checks.add(rankCheck(runtime, ops, tableName, recordId, term));
         }
      }
      return (explain);
   }



   /*******************************************************************************
    ** Where the record ranks among the table's matches, against the number of
    ** ids the provider asks for with the default per-table limit.
    *******************************************************************************/
   private Check rankCheck(QuickSearchRuntime runtime, QuickSearchDiagnosticsOperations ops, String tableName, String recordId, String term) throws QException
   {
      int         window = Math.min(RecordSearchAction.MAX_PROVIDER_IDS_PER_TABLE, RecordSearchAction.DEFAULT_LIMIT_PER_TABLE * QuickSearchRecordSearchProvider.IDS_PER_LIMIT_MULTIPLIER);
      SearchProbe probe  = ops.probeSearch(term, tableName, RANK_WINDOW, runtime.getDiscoveredTables());
      int         rank   = 0;
      for(ProbeHit hit : probe.hits())
      {
         rank++;
         if(recordId.equals(hit.recordId()))
         {
            if(rank <= RecordSearchAction.DEFAULT_LIMIT_PER_TABLE)
            {
               return (new Check("Rank", Level.OK, "#" + rank + " of " + probe.totalHits() + " matches in this table: within the global search's default " + RecordSearchAction.DEFAULT_LIMIT_PER_TABLE + " results."));
            }
            if(rank <= window)
            {
               return (new Check("Rank", Level.WARN, "#" + rank + " of " + probe.totalHits() + " matches: Quick Search returns it, but the global search shows only the first "
                  + RecordSearchAction.DEFAULT_LIMIT_PER_TABLE + " readable results per table by default. A more specific term ranks it higher."));
            }
            return (new Check("Rank", Level.WARN, "#" + rank + " of " + probe.totalHits() + " matches: beyond the top " + window + " ids the provider requests per table, so the global search does not show it."));
         }
      }
      return (new Check("Rank", Level.WARN, "Not in the top " + RANK_WINDOW + " of " + probe.totalHits() + " matches in this table, so the global search does not show it."));
   }



   /*******************************************************************************
    ** Render inspections as HTML.
    *******************************************************************************/
   public static String render(List<Inspection> inspections, String term)
   {
      DiagnosticsHtml html = new DiagnosticsHtml().open();
      for(Inspection inspection : inspections)
      {
         html.heading("Search document for " + inspection.tableName() + " #" + inspection.recordId());
         if(inspection.error() != null)
         {
            html.status(Level.FAIL, inspection.error());
            continue;
         }

         switch(inspection.verdict())
         {
            case VERDICT_CURRENT -> html.status(Level.OK, "Indexed and current: the document matches the database.");
            case VERDICT_STALE -> html.status(Level.WARN, "Indexed but stale: some values differ from the database. Reindex in Search refreshes it.");
            default -> html.status(Level.FAIL, "Not in the index. Reindex in Search adds it.");
         }

         DocumentView indexed = inspection.indexed();
         List<String[]> facts = new ArrayList<>();
         facts.add(new String[] { "Document id", OpenSearchDocument.buildDocumentId(inspection.tableName(), inspection.recordId()) });
         facts.add(new String[] { "Physical index", indexed == null ? null : indexed.physicalIndex() });
         facts.add(new String[] { "Indexed version", indexed == null || indexed.version() == null ? null : indexed.version() + versionNote(indexed.version()) });
         facts.add(new String[] { "Database version", inspection.expected() == null || inspection.expected().getVersion() == null ? "not versioned (no timestamp field)"
            : inspection.expected().getVersion() + versionNote(inspection.expected().getVersion()) });
         facts.add(new String[] { "Indexed at", indexed == null || indexed.source() == null ? null : indexed.source().path("indexedAt").asText(null) });
         html.facts(facts);

         html.subheading("Database vs index");
         List<String> items = new ArrayList<>();
         for(FieldDiff diff : inspection.diffs())
         {
            items.add(DiagnosticsHtml.badge(diff.isSame() ? Level.OK : Level.WARN) + " <strong>" + DiagnosticsHtml.escape(diff.name()) + "</strong>"
               + (diff.isSame() ? ": " + valueHtml(diff.databaseValue())
               : "<br>database: " + valueHtml(diff.databaseValue()) + "<br>index: " + valueHtml(diff.indexValue())));
         }
         html.rawList(items);

         if(!inspection.matchChecks().isEmpty())
         {
            html.subheading("Why does (or doesn't) this record match \"" + term + "\"?");
            html.checks(inspection.matchChecks());
            if(inspection.explain() != null && inspection.explain().explanationText() != null)
            {
               html.textDetails("Score explanation", inspection.explain().explanationText());
            }
         }

         if(indexed != null && indexed.source() != null)
         {
            html.jsonDetails("Indexed document (JSON)", indexed.source().toString());
         }
      }
      return (html.close().toString());
   }



   /*******************************************************************************
    *******************************************************************************/
   private static String valueHtml(String value)
   {
      if(value == null)
      {
         return ("<em>none</em>");
      }
      String shown = value.length() > 500 ? value.substring(0, 500) + "... (" + value.length() + " characters)" : value;
      return ("<code>" + DiagnosticsHtml.escape(shown) + "</code>");
   }



   /*******************************************************************************
    ** " (time)" for a version that looks like epoch millis.
    *******************************************************************************/
   private static String versionNote(Long version)
   {
      return (version != null && version > 946_684_800_000L ? " (" + java.time.Instant.ofEpochMilli(version) + ")" : "");
   }



   /*******************************************************************************
    *******************************************************************************/
   private static Set<String> tokenSet(List<AnalyzedToken> tokens)
   {
      Set<String> set = new LinkedHashSet<>();
      for(AnalyzedToken token : tokens)
      {
         set.add(token.token());
      }
      return (set);
   }



   /*******************************************************************************
    *******************************************************************************/
   private static String text(JsonNode node, String field)
   {
      if(node == null || !node.has(field) || node.get(field).isNull())
      {
         return (null);
      }
      return (blankToNull(node.get(field).isTextual() ? node.get(field).asText() : node.get(field).toString()));
   }



   /*******************************************************************************
    *******************************************************************************/
   private static String blankToNull(String value)
   {
      return (value == null || value.isEmpty() ? null : value);
   }

}

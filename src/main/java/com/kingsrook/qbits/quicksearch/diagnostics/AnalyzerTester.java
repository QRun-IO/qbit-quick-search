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
import java.util.Set;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.diagnostics.DiagnosticsHtml.Level;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchDiagnosticsOperations;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchDiagnosticsOperations.AnalyzedToken;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;


/*******************************************************************************
 ** The analyzer tester: run _analyze on the live index with its two
 ** analyzers. The text is analyzed as it would be indexed (standard
 ** tokenizer, lowercase, edge n-grams of 2 to 20 characters) and as a query
 ** (standard tokenizer, lowercase); with a query, each query token is marked
 ** found or not among the text's indexed tokens, which is how matching works
 ** (every query token must be found). Read-only.
 *******************************************************************************/
public class AnalyzerTester
{

   /*******************************************************************************
    ** The tokens of the text (both analyzers) and of the query.
    *******************************************************************************/
   public record Result(String text, String query, List<AnalyzedToken> indexTokens, List<AnalyzedToken> textAsQueryTokens, List<AnalyzedToken> queryTokens)
   {
   }



   /*******************************************************************************
    ** Analyze text (and an optional query) with the index's analyzers.
    *******************************************************************************/
   public Result analyze(QuickSearchRuntime runtime, String text, String query) throws QException
   {
      QuickSearchDiagnosticsOperations ops = new QuickSearchDiagnosticsOperations(runtime.getClient());
      String                           safeText = text == null ? "" : text;
      return (new Result(safeText, query,
         ops.analyze(QuickSearchOpenSearchClient.INDEX_ANALYZER, safeText),
         ops.analyze(QuickSearchOpenSearchClient.SEARCH_ANALYZER, safeText),
         StringUtils.hasContent(query) ? ops.analyze(QuickSearchOpenSearchClient.SEARCH_ANALYZER, query) : null));
   }



   /*******************************************************************************
    ** Render a result as HTML.
    *******************************************************************************/
   public static String render(Result result)
   {
      DiagnosticsHtml html = new DiagnosticsHtml().open();
      html.heading("Analyzer tokens");
      if(result.text().length() > QuickSearchDiagnosticsOperations.MAX_ANALYZE_TEXT_LENGTH)
      {
         html.status(Level.WARN, "Only the first " + QuickSearchDiagnosticsOperations.MAX_ANALYZE_TEXT_LENGTH + " characters were analyzed.");
      }

      Set<String> indexed = new LinkedHashSet<>();
      result.indexTokens().forEach(t -> indexed.add(t.token()));

      if(result.queryTokens() != null)
      {
         html.subheading("Does the query match this text?");
         List<DiagnosticsHtml.Check> checks = new ArrayList<>();
         boolean                     all    = !result.queryTokens().isEmpty();
         for(AnalyzedToken token : result.queryTokens())
         {
            boolean found = indexed.contains(token.token());
            all &= found;
            String why = found ? "found among the indexed tokens"
               : token.token().length() < 2 ? "one character: never indexed (n-grams start at 2)"
               : token.token().length() > 20 ? "longer than 20 characters: never indexed (n-grams stop at 20)"
               : "no word in the text starts with it";
            checks.add(new DiagnosticsHtml.Check("\"" + token.token() + "\"", found ? Level.OK : Level.FAIL, why));
         }
         html.status(all ? Level.OK : Level.FAIL, all ? "Every query token is found: the searchable-text clause matches."
            : "At least one query token is missing: with AND semantics, a document with only this text does not match.");
         html.checks(checks);
      }

      html.subheading("As indexed (" + QuickSearchOpenSearchClient.INDEX_ANALYZER + "): " + result.indexTokens().size() + " token(s)");
      html.paragraph(join(result.indexTokens()));
      html.subheading("As a query (" + QuickSearchOpenSearchClient.SEARCH_ANALYZER + "): " + result.textAsQueryTokens().size() + " token(s)");
      html.paragraph(join(result.textAsQueryTokens()));
      if(result.queryTokens() != null)
      {
         html.subheading("Query tokens: " + result.queryTokens().size());
         html.paragraph(join(result.queryTokens()));
      }
      return (html.close().toString());
   }



   /*******************************************************************************
    ** Tokens as "token (position)" separated by commas.
    *******************************************************************************/
   static String join(List<AnalyzedToken> tokens)
   {
      if(tokens == null || tokens.isEmpty())
      {
         return ("(none)");
      }
      List<String> parts = new ArrayList<>();
      for(AnalyzedToken token : tokens)
      {
         parts.add(token.token() + " @" + token.position());
      }
      return (String.join(", ", parts));
   }

}

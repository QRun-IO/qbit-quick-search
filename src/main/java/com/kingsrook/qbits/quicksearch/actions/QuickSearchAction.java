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

package com.kingsrook.qbits.quicksearch.actions;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitContext;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import com.kingsrook.qbits.quicksearch.processes.IndexingUtils;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;


/*******************************************************************************
 ** Action that executes a quick-search query against OpenSearch and returns
 ** normalized, paginated results.
 **
 ** Validates and normalizes the input, delegates to the
 ** QuickSearchOpenSearchClient, maps each hit to a QuickSearchResult, and
 ** computes pagination metadata (totalHits, hasMore).
 *******************************************************************************/
public class QuickSearchAction
{
   private static final QLogger LOG             = QLogger.getLogger(QuickSearchAction.class);
   private static final int     DEFAULT_LIMIT   = 25;
   private static final int     DEFAULT_OFFSET  = 0;



   /*******************************************************************************
    ** Execute a quick-search for the given input.
    **
    ** Returns an empty output (totalHits=0, hasMore=false, empty results list)
    ** when the search term is null or blank.  Otherwise normalizes the term,
    ** applies default pagination values, calls the OpenSearch client, and maps
    ** hits to QuickSearchResult objects.
    **
    ** @param input the search parameters; must not be null
    ** @return populated QuickSearchOutput
    ** @throws QException if the OpenSearch call fails
    *******************************************************************************/
   public QuickSearchOutput execute(QuickSearchInput input) throws QException
   {
      String searchTerm = input.getSearchTerm();

      if(searchTerm == null || searchTerm.isBlank())
      {
         LOG.debug("QuickSearch called with blank/null search term - returning empty output");
         return (new QuickSearchOutput()
            .withResults(Collections.emptyList())
            .withTotalHits(0L)
            .withHasMore(false));
      }

      String normalizedTerm = IndexingUtils.normalizeSearchText(searchTerm);

      int limit  = (input.getLimit() == null || input.getLimit() <= 0) ? DEFAULT_LIMIT : input.getLimit();
      int offset = (input.getOffset() == null || input.getOffset() < 0) ? DEFAULT_OFFSET : input.getOffset();

      QuickSearchOpenSearchClient       client       = QuickSearchQBitContext.getClient();
      List<QuickSearchableTableConfig>  tableConfigs = QuickSearchQBitContext.getDiscoveredTables();

      if(client == null)
      {
         throw new QException("QuickSearch client is not initialized. The QBit may not have connected to OpenSearch during startup.");
      }

      LOG.debug("Executing quick search", "term", normalizedTerm, "tableName", input.getTableName(), "limit", limit, "offset", offset);

      SearchResponse<OpenSearchDocument> response = client.search(normalizedTerm, input.getTableName(), limit, offset, tableConfigs);

      List<QuickSearchResult> results = new ArrayList<>();

      for(Hit<OpenSearchDocument> hit : response.hits().hits())
      {
         OpenSearchDocument source = hit.source();

         String highlightSnippet = null;
         Map<String, List<String>> highlight = hit.highlight();

         if(highlight != null)
         {
            List<String> fragments = highlight.get("searchableText");

            if(fragments != null && !fragments.isEmpty())
            {
               highlightSnippet = String.join("...", fragments);
            }
         }

         Double rawScore = hit.score();
         Float  score    = rawScore != null ? rawScore.floatValue() : null;

         results.add(new QuickSearchResult()
            .withTableName(source.getSourceTable())
            .withRecordId(source.getRecordId())
            .withRecordLabel(source.getRecordLabel())
            .withScore(score)
            .withHighlightSnippet(highlightSnippet));
      }

      long    totalHits = response.hits().total() != null ? response.hits().total().value() : results.size();
      boolean hasMore   = (offset + results.size()) < totalHits;

      return (new QuickSearchOutput()
         .withResults(results)
         .withTotalHits(totalHits)
         .withHasMore(hasMore));
   }

}

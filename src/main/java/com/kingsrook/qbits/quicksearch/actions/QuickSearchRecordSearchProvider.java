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

package com.kingsrook.qbits.quicksearch.actions;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.tables.RecordSearchAction;
import com.kingsrook.qqq.backend.core.actions.tables.RecordSearchProviderInterface;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.search.RecordSearchHighlight;
import com.kingsrook.qqq.backend.core.model.actions.tables.search.RecordSearchHighlightMatch;
import com.kingsrook.qqq.backend.core.model.actions.tables.search.RecordSearchInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.search.RecordSearchOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.search.RecordSearchResult;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient.RecordSearchHit;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient.RecordSearchTableHits;
import com.kingsrook.qbits.quicksearch.processes.IndexingUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Serves QQQ core record search (RecordSearchAction, POST /qqq/v1/search,
 ** the Next UI global search) from the Quick Search index, for the tables
 ** this QBit indexes. Registered on the QInstance by QuickSearchQBitProducer
 ** when serveCoreRecordSearch is on (the default).
 **
 ** Core creates a new instance for every search, so this class holds no
 ** state: it resolves the QBit's QuickSearchRuntime (client, tables, config)
 ** from QContext.
 **
 ** A table is claimed when it is indexed by this QBit and enabled, and core
 ** record search is not backing off after a recent failure. search() sends
 ** one _msearch request with a top-N search per table (after skipping the
 ** input's offset) and returns record ids, best first, each with its score
 ** and highlights (plain text plus match ranges, from the indexed field
 ** values), and each table's total hits; core re-reads the ids by primary key
 ** through QueryAction, so table permissions, record security locks and
 ** record labels are core's job, and ids of deleted records drop out (core
 ** also keeps only highlights of fields the session can see). The index has
 ** no record modify dates, so a RECENT sort gets relevance order. Each table gets
 ** IDS_PER_LIMIT_MULTIPLIER x limitPerTable ids (at most
 ** RecordSearchAction.MAX_PROVIDER_IDS_PER_TABLE) so dropouts do not leave
 ** the table short.
 **
 ** The request is bounded by recordSearchTimeoutMillis. Any failure throws,
 ** and core then searches the claimed tables with its own LIKE search; the
 ** failure also starts a short backoff (QuickSearchRuntime
 ** RECORD_SEARCH_BACKOFF_MILLIS) during which no tables are claimed.
 *******************************************************************************/
public class QuickSearchRecordSearchProvider implements RecordSearchProviderInterface
{
   private static final QLogger LOG = QLogger.getLogger(QuickSearchRecordSearchProvider.class);

   public static final int IDS_PER_LIMIT_MULTIPLIER = 4;

   private static final String FIELD_VALUES_PREFIX = "fieldValues.";



   /*******************************************************************************
    ** Claim tables this QBit indexes and that are enabled, unless core record
    ** search is off or backing off. Cheap: an in-memory lookup plus the
    ** runtime's cached enabled flag.
    *******************************************************************************/
   @Override
   public boolean claimsTable(QTableMetaData table)
   {
      QuickSearchRuntime runtime = QuickSearchRuntime.get();
      if(runtime == null || table == null || !Boolean.TRUE.equals(runtime.getConfig().getServeCoreRecordSearch()))
      {
         return (false);
      }

      if(runtime.getTableConfig(table.getName()) == null || runtime.isRecordSearchBackingOff())
      {
         return (false);
      }

      return (runtime.isTableEnabled(table.getName()));
   }



   /*******************************************************************************
    ** Ids of the best matches in each requested table, in relevance order.
    *******************************************************************************/
   @Override
   public RecordSearchOutput search(RecordSearchInput input) throws QException
   {
      QuickSearchRuntime    runtime = QuickSearchRuntime.getOrThrow();
      QuickSearchQBitConfig config  = runtime.getConfig();

      List<RecordSearchResult> results = new ArrayList<>();
      String                   term    = IndexingUtils.normalizeSearchText(input.getSearchTerm());
      if(term.length() < QuickSearchAction.MIN_TERM_LENGTH)
      {
         return (new RecordSearchOutput().withResults(results));
      }

      List<String> tableNames = new ArrayList<>();
      for(String tableName : new LinkedHashSet<>(CollectionUtils.nonNullList(input.getTableNames())))
      {
         if(runtime.getTableConfig(tableName) != null)
         {
            tableNames.add(tableName);
         }
      }
      if(tableNames.isEmpty())
      {
         return (new RecordSearchOutput().withResults(results));
      }

      int limitPerTable = input.getLimitPerTable() == null || input.getLimitPerTable() <= 0 ? RecordSearchAction.DEFAULT_LIMIT_PER_TABLE : input.getLimitPerTable();
      int idsPerTable   = Math.min(RecordSearchAction.MAX_PROVIDER_IDS_PER_TABLE, limitPerTable * IDS_PER_LIMIT_MULTIPLIER);
      int timeoutMillis = config.getRecordSearchTimeoutMillis() == null ? QuickSearchQBitConfig.DEFAULT_RECORD_SEARCH_TIMEOUT_MILLIS : config.getRecordSearchTimeoutMillis();

      int offset = input.getOffset() == null ? 0 : Math.max(0, input.getOffset());

      Map<String, RecordSearchTableHits> hitsByTable;
      try
      {
         hitsByTable = runtime.getClient().searchRecordsPerTable(term, tableNames, idsPerTable, offset, true, runtime.getDiscoveredTables(), timeoutMillis);
      }
      catch(QException | RuntimeException e)
      {
         runtime.markRecordSearchFailed();
         LOG.info("Quick Search record search failed; core searches these tables itself, and Quick Search claims none for a while", logPair("tables", tableNames),
            logPair("backoffMillis", QuickSearchRuntime.RECORD_SEARCH_BACKOFF_MILLIS), logPair("error", e.getMessage()));
         throw (e instanceof QException qe ? qe : new QException("Quick Search record search failed: " + e.getMessage(), e));
      }

      Map<String, Integer> totalCountByTable = new HashMap<>();
      for(String tableName : tableNames)
      {
         RecordSearchTableHits tableHits = hitsByTable.get(tableName);
         if(tableHits == null)
         {
            continue;
         }

         if(tableHits.totalHits() != null)
         {
            totalCountByTable.put(tableName, (int) Math.min(Integer.MAX_VALUE, tableHits.totalHits()));
         }

         for(RecordSearchHit hit : CollectionUtils.nonNullList(tableHits.hits()))
         {
            List<RecordSearchHighlight> highlights = highlights(hit.highlights());
            results.add(new RecordSearchResult()
               .withTableName(tableName)
               .withRecordId(hit.recordId())
               .withScore(hit.score())
               .withHighlights(highlights.isEmpty() ? null : highlights));
         }
      }

      return (new RecordSearchOutput().withResults(results).withTotalCountByTable(totalCountByTable));
   }



   /*******************************************************************************
    ** A hit's highlight fragments (by index field) as core record search
    ** highlights: one per fieldValues.{fieldName} field, as plain text with
    ** match ranges.  Other index fields, and fragments without a match, are
    ** skipped.
    *******************************************************************************/
   public static List<RecordSearchHighlight> highlights(Map<String, List<String>> fragmentsByIndexField)
   {
      List<RecordSearchHighlight> highlights = new ArrayList<>();
      for(Map.Entry<String, List<String>> entry : CollectionUtils.nonNullMap(fragmentsByIndexField).entrySet())
      {
         String indexField = entry.getKey();
         if(indexField == null || !indexField.startsWith(FIELD_VALUES_PREFIX) || indexField.length() == FIELD_VALUES_PREFIX.length() || CollectionUtils.nullSafeIsEmpty(entry.getValue()))
         {
            continue;
         }

         RecordSearchHighlight highlight = highlight(indexField.substring(FIELD_VALUES_PREFIX.length()), entry.getValue().get(0));
         if(highlight != null)
         {
            highlights.add(highlight);
         }
      }
      return (highlights);
   }



   /*******************************************************************************
    ** A highlight from a fragment whose matches are wrapped in the client's
    ** pre/post tags: the fragment's text without the tags, and the ranges they
    ** wrapped (adjacent ranges merged).  Null if the fragment is blank or has
    ** no match.
    *******************************************************************************/
   public static RecordSearchHighlight highlight(String fieldName, String fragment)
   {
      if(fragment == null)
      {
         return (null);
      }

      StringBuilder                    text    = new StringBuilder();
      List<RecordSearchHighlightMatch> matches = new ArrayList<>();
      int                              open    = -1;
      for(int i = 0; i < fragment.length(); i++)
      {
         char c = fragment.charAt(i);
         if(c == QuickSearchOpenSearchClient.HIGHLIGHT_PRE_TAG.charAt(0))
         {
            open = text.length();
         }
         else if(c == QuickSearchOpenSearchClient.HIGHLIGHT_POST_TAG.charAt(0))
         {
            if(open >= 0 && text.length() > open)
            {
               RecordSearchHighlightMatch last = matches.isEmpty() ? null : matches.get(matches.size() - 1);
               if(last != null && last.getEnd() == open)
               {
                  last.setEnd(text.length());
               }
               else
               {
                  matches.add(new RecordSearchHighlightMatch().withStart(open).withEnd(text.length()));
               }
            }
            open = -1;
         }
         else
         {
            text.append(c);
         }
      }

      if(matches.isEmpty() || text.toString().isBlank())
      {
         return (null);
      }

      return (new RecordSearchHighlight().withFieldName(fieldName).withText(text.toString()).withMatches(matches));
   }

}

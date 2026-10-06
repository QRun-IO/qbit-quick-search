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


import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper;
import com.kingsrook.qqq.backend.core.actions.permissions.TablePermissionSubType;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitContext;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import com.kingsrook.qbits.quicksearch.processes.IndexingUtils;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.TotalHitsRelation;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Permission-aware, bounded search over the Quick Search index.
 **
 ** Only tables the session may read (table READ permission), that are
 ** configured and enabled, are searched. With applyRecordSecurityLocks (the
 ** default) hits are re-read through QueryAction by primary key, so record
 ** security locks apply and no label or snippet is returned for a record the
 ** session cannot query.
 **
 ** limit is capped at maxSearchLimit and offset + limit at OpenSearch's result
 ** window. tableNames restricts the search; limitPerTable searches each table
 ** separately and returns up to that many hits per table.
 **
 ** offset counts results the caller can see. Without locks it is passed
 ** straight to OpenSearch. With locks, hits are read from position 0 in
 ** batches that double in size, filtered, the first offset accessible results
 ** are skipped and limit are kept, so consecutive pages neither overlap nor
 ** skip an accessible hit. No request reads past MAX_RESULT_WINDOW (10,000
 ** raw hits), so accessible results beyond it are not reachable.
 **
 ** With locks, each scan (the whole search, or one table in per-table mode)
 ** has a hard budget: at most MAX_RESULT_WINDOW raw hits, in batches of at
 ** most MAX_LOCKED_BATCH_SIZE, over at most MAX_LOCKED_ROUND_TRIPS OpenSearch
 ** requests, each followed by one QueryAction of at most
 ** MAX_LOCKED_BATCH_SIZE ids per table in the batch. One request therefore
 ** reads at most (searched tables) x MAX_RESULT_WINDOW raw hits however
 ** large offset or limit is, and a fully locked table cannot starve another.
 ** When a budget runs out the scan stops and reports hasMore and a
 ** lower-bound total. Hits are deduplicated by table and record id within a
 ** call; batches are separate from/size requests without a tiebreak sort, so
 ** a hit that moves between batches during a refresh can still be missed.
 *******************************************************************************/
public class QuickSearchAction
{
   private static final QLogger LOG = QLogger.getLogger(QuickSearchAction.class);

   public static final int DEFAULT_LIMIT       = 25;
   public static final int MIN_TERM_LENGTH     = 2;
   public static final int MAX_TERM_LENGTH     = 100;
   public static final int MAX_LIMIT_PER_TABLE = 100;

   public static final int MAX_LOCKED_ROUND_TRIPS = 32;
   public static final int MAX_LOCKED_BATCH_SIZE  = 1_000;



   /*******************************************************************************
    **
    *******************************************************************************/
   public QuickSearchOutput execute(QuickSearchInput input) throws QException
   {
      String searchTerm = input.getSearchTerm();
      if(searchTerm == null || searchTerm.isBlank())
      {
         return (emptyOutput());
      }

      String normalizedTerm = IndexingUtils.normalizeSearchText(searchTerm);
      if(normalizedTerm.length() < MIN_TERM_LENGTH)
      {
         return (emptyOutput());
      }
      if(normalizedTerm.length() > MAX_TERM_LENGTH)
      {
         throw (new QException("Search term is too long (maximum " + MAX_TERM_LENGTH + " characters)"));
      }

      QuickSearchRuntime               runtime      = QuickSearchRuntime.get();
      QuickSearchQBitConfig            config       = runtime != null ? runtime.getConfig() : QuickSearchQBitContext.getConfig();
      QuickSearchOpenSearchClient      client       = runtime != null ? runtime.getClient() : QuickSearchQBitContext.getClient();
      List<QuickSearchableTableConfig> tableConfigs = runtime != null ? runtime.getDiscoveredTables() : QuickSearchQBitContext.getDiscoveredTables();

      if(client == null)
      {
         throw new QException("QuickSearch client is not initialized. The QBit may not have connected to OpenSearch during startup.");
      }
      if(tableConfigs == null)
      {
         tableConfigs = Collections.emptyList();
      }

      int maxLimit = (config == null || config.getMaxSearchLimit() == null) ? MAX_LIMIT_PER_TABLE : config.getMaxSearchLimit();
      int limit    = (input.getLimit() == null || input.getLimit() <= 0) ? DEFAULT_LIMIT : Math.min(input.getLimit(), maxLimit);
      int offset   = (input.getOffset() == null || input.getOffset() < 0) ? 0 : input.getOffset();

      if(offset >= QuickSearchOpenSearchClient.MAX_RESULT_WINDOW)
      {
         return (emptyOutput());
      }

      List<String> allowedTables = resolveAllowedTables(input, runtime, tableConfigs);
      if(allowedTables.isEmpty())
      {
         return (emptyOutput());
      }

      boolean applyLocks = config == null || Boolean.TRUE.equals(config.getApplyRecordSecurityLocks());

      LOG.debug("Executing quick search", logPair("termLength", normalizedTerm.length()), logPair("tables", allowedTables.size()), logPair("limit", limit), logPair("offset", offset));

      if(input.getLimitPerTable() != null && input.getLimitPerTable() > 0)
      {
         return (searchPerTable(client, normalizedTerm, allowedTables, offset, Math.min(input.getLimitPerTable(), maxLimit), tableConfigs, applyLocks));
      }

      Page page = searchPage(client, normalizedTerm, allowedTables, offset, limit, tableConfigs, applyLocks);
      return (page.toOutput());
   }



   /*******************************************************************************
    ** Search each allowed table separately, skipping offset results and
    ** returning up to limitPerTable hits in each table.
    *******************************************************************************/
   private QuickSearchOutput searchPerTable(QuickSearchOpenSearchClient client, String term, List<String> allowedTables, int offset, int limitPerTable, List<QuickSearchableTableConfig> tableConfigs, boolean applyLocks) throws QException
   {
      List<QuickSearchResult> results    = new ArrayList<>();
      long                    totalHits  = 0;
      boolean                 hasMore    = false;
      boolean                 lowerBound = false;

      for(String tableName : allowedTables)
      {
         Page page = searchPage(client, term, List.of(tableName), offset, limitPerTable, tableConfigs, applyLocks);
         results.addAll(page.results());
         totalHits += page.totalHits();
         hasMore |= page.hasMore();
         lowerBound |= page.lowerBound();
      }

      return (new QuickSearchOutput().withResults(results).withTotalHits(totalHits).withTotalHitsIsLowerBound(lowerBound).withHasMore(hasMore));
   }



   /*******************************************************************************
    ** One page over the given tables: skip offset results, keep up to limit.
    ** Without locks offset goes straight to OpenSearch; with locks offset and
    ** limit count accessible results within one scan budget (see the class
    ** comment). Every request keeps from + size within MAX_RESULT_WINDOW.
    *******************************************************************************/
   private Page searchPage(QuickSearchOpenSearchClient client, String term, List<String> tables, int offset, int limit, List<QuickSearchableTableConfig> tableConfigs, boolean applyLocks) throws QException
   {
      int window   = QuickSearchOpenSearchClient.MAX_RESULT_WINDOW;
      int pageSize = Math.min(limit, window - offset);
      if(pageSize <= 0)
      {
         return (new Page(Collections.emptyList(), 0L, false, false));
      }

      if(!applyLocks)
      {
         SearchResponse<OpenSearchDocument> response = runSearch(client, term, tables, pageSize, offset, tableConfigs, null);
         List<QuickSearchResult>            results  = toResults(response);

         long    totalHits  = response.hits().total() != null ? response.hits().total().value() : results.size();
         boolean lowerBound = response.hits().total() != null && response.hits().total().relation() == TotalHitsRelation.Gte;
         return (new Page(results, totalHits, lowerBound, (offset + results.size()) < totalHits));
      }

      ////////////////////////////////////////////////////////////////////////////
      // read candidates from position 0 until offset + pageSize accessible     //
      // results are known, the hits run out, the result window is reached, or  //
      // the scan budget is spent. each batch over-reads 2x what is still       //
      // needed and at least doubles the last one, up to MAX_LOCKED_BATCH_SIZE, //
      // so a fully locked table reaches the window in 19 round trips.          //
      ////////////////////////////////////////////////////////////////////////////
      int                     target     = offset + pageSize;
      List<QuickSearchResult> accessible = new ArrayList<>();
      int                     position   = 0;
      int                     batchSize  = 0;
      boolean                 exhausted  = false;
      boolean                 rawFloor   = false;
      ScanBudget              budget     = new ScanBudget();
      Set<String>             seen       = new HashSet<>();
      String                  preference = "quick-search-" + UUID.randomUUID();

      while(accessible.size() < target && !exhausted && position < window && budget.hasRemaining())
      {
         batchSize = Math.min(Math.min(window - position, budget.remainingHits), Math.min(MAX_LOCKED_BATCH_SIZE, Math.max(2 * (target - accessible.size()), 2 * batchSize)));

         SearchResponse<OpenSearchDocument> response = runSearch(client, term, tables, batchSize, position, tableConfigs, preference);
         int                                rawHits  = response.hits().hits().size();
         long                               rawTotal = response.hits().total() != null ? response.hits().total().value() : position + rawHits;
         rawFloor = response.hits().total() != null && response.hits().total().relation() == TotalHitsRelation.Gte;

         List<QuickSearchResult> candidates = new ArrayList<>();
         for(QuickSearchResult result : toResults(response))
         {
            if(seen.add(result.getTableName() + ":" + result.getRecordId()))
            {
               candidates.add(result);
            }
         }
         accessible.addAll(filterByRecordAccess(candidates, tableConfigs));
         position += rawHits;
         budget.spend(rawHits);
         exhausted = rawHits < batchSize || position >= rawTotal;
      }

      ////////////////////////////////////////////////////////////////////////////
      // the raw total would reveal how many matching rows the session may not  //
      // see; report only what was verified accessible. hasMore says only that  //
      // more candidates exist, so a caller can page on to accessible rows.     //
      ////////////////////////////////////////////////////////////////////////////
      boolean truncated = accessible.size() > target;
      boolean hasMore   = truncated || (!exhausted && position < window);

      List<QuickSearchResult> results = accessible.size() > offset
         ? new ArrayList<>(accessible.subList(offset, Math.min(target, accessible.size())))
         : new ArrayList<>();

      return (new Page(results, (long) accessible.size(), !exhausted || rawFloor, hasMore));
   }



   /*******************************************************************************
    ** Requested tables (tableNames, else tableName, else all configured) that
    ** are configured, enabled, and readable by the session.
    *******************************************************************************/
   private List<String> resolveAllowedTables(QuickSearchInput input, QuickSearchRuntime runtime, List<QuickSearchableTableConfig> tableConfigs)
   {
      Set<String> configured = new HashSet<>();
      for(QuickSearchableTableConfig tableConfig : tableConfigs)
      {
         configured.add(tableConfig.getTableName());
      }

      List<String> requested = new ArrayList<>();
      if(!CollectionUtils.nullSafeIsEmpty(input.getTableNames()))
      {
         requested.addAll(input.getTableNames());
      }
      else if(StringUtils.hasContent(input.getTableName()))
      {
         requested.add(input.getTableName());
      }
      else
      {
         requested.addAll(configured);
      }

      boolean      checkPermissions = QContext.getQInstance() != null && QContext.getQSession() != null;
      List<String> allowed          = new ArrayList<>();
      for(String tableName : new LinkedHashSet<>(requested))
      {
         if(!configured.contains(tableName))
         {
            continue;
         }
         if(runtime != null && !runtime.isTableEnabled(tableName))
         {
            continue;
         }
         if(checkPermissions && !PermissionsHelper.hasTablePermission(input, tableName, TablePermissionSubType.READ))
         {
            continue;
         }
         allowed.add(tableName);
      }

      return (allowed);
   }



   /*******************************************************************************
    ** Keep only hits whose record the session can query (record security
    ** locks). Tables not in the QInstance are left as they are.
    *******************************************************************************/
   private List<QuickSearchResult> filterByRecordAccess(List<QuickSearchResult> results, List<QuickSearchableTableConfig> tableConfigs) throws QException
   {
      QInstance qInstance = QContext.getQInstance();
      if(qInstance == null || QContext.getQSession() == null || results.isEmpty())
      {
         return (results);
      }

      Map<String, List<QuickSearchResult>> byTable = new LinkedHashMap<>();
      for(QuickSearchResult result : results)
      {
         byTable.computeIfAbsent(result.getTableName(), k -> new ArrayList<>()).add(result);
      }

      Set<QuickSearchResult> accessible = new HashSet<>();
      for(Map.Entry<String, List<QuickSearchResult>> entry : byTable.entrySet())
      {
         QTableMetaData table = qInstance.getTable(entry.getKey());
         if(table == null)
         {
            accessible.addAll(entry.getValue());
            continue;
         }

         List<Serializable> ids = new ArrayList<>();
         for(QuickSearchResult result : entry.getValue())
         {
            ids.add(result.getRecordId());
         }

         Set<String> found = new HashSet<>();
         for(int start = 0; start < ids.size(); start += MAX_LOCKED_BATCH_SIZE)
         {
            QueryInput queryInput = new QueryInput();
            queryInput.setTableName(table.getName());
            queryInput.setFilter(new QQueryFilter(new QFilterCriteria(table.getPrimaryKeyField(), QCriteriaOperator.IN, ids.subList(start, Math.min(ids.size(), start + MAX_LOCKED_BATCH_SIZE)))));

            for(QRecord record : CollectionUtils.nonNullList(new QueryAction().execute(queryInput).getRecords()))
            {
               found.add(record.getValueString(table.getPrimaryKeyField()));
            }
         }

         for(QuickSearchResult result : entry.getValue())
         {
            if(found.contains(result.getRecordId()))
            {
               accessible.add(result);
            }
         }
      }

      List<QuickSearchResult> filtered = new ArrayList<>();
      for(QuickSearchResult result : results)
      {
         if(accessible.contains(result))
         {
            filtered.add(result);
         }
      }
      return (filtered);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static List<QuickSearchResult> toResults(SearchResponse<OpenSearchDocument> response)
   {
      QInstance               qInstance = QContext.getQInstance();
      List<QuickSearchResult> results   = new ArrayList<>();

      for(Hit<OpenSearchDocument> hit : response.hits().hits())
      {
         OpenSearchDocument source = hit.source();
         if(source == null)
         {
            continue;
         }

         String                    highlightSnippet = null;
         Map<String, List<String>> highlight        = hit.highlight();
         if(highlight != null)
         {
            List<String> fragments = highlight.get("searchableText");
            if(fragments != null && !fragments.isEmpty())
            {
               highlightSnippet = String.join("...", fragments);
            }
         }

         QTableMetaData table      = qInstance == null ? null : qInstance.getTable(source.getSourceTable());
         String         tableLabel = table != null && StringUtils.hasContent(table.getLabel()) ? table.getLabel() : source.getSourceTable();
         Double         rawScore   = hit.score();

         results.add(new QuickSearchResult()
            .withTableName(source.getSourceTable())
            .withTableLabel(tableLabel)
            .withRecordId(source.getRecordId())
            .withRecordLabel(source.getRecordLabel())
            .withScore(rawScore != null ? rawScore.floatValue() : null)
            .withHighlightSnippet(highlightSnippet));
      }

      return (results);
   }



   /*******************************************************************************
    ** Run one search, surfacing an OpenSearch client error as a QException so
    ** callers never see an unchecked OpenSearchException.
    *******************************************************************************/
   private static SearchResponse<OpenSearchDocument> runSearch(QuickSearchOpenSearchClient client, String term, List<String> tables, int fetchSize, int offset, List<QuickSearchableTableConfig> tableConfigs, String preference) throws QException
   {
      try
      {
         return (client.search(term, tables, fetchSize, offset, tableConfigs, preference));
      }
      catch(OpenSearchException e)
      {
         throw (new QException("Quick search failed: " + (e.error() != null && e.error().reason() != null ? e.error().reason() : e.getMessage()), e));
      }
   }



   /*******************************************************************************
    ** Work budget for one locked scan: raw hits fetched and OpenSearch round
    ** trips.
    *******************************************************************************/
   private static final class ScanBudget
   {
      private Integer remainingHits       = QuickSearchOpenSearchClient.MAX_RESULT_WINDOW;
      private Integer remainingRoundTrips = MAX_LOCKED_ROUND_TRIPS;



      /*******************************************************************************
       **
       *******************************************************************************/
      boolean hasRemaining()
      {
         return (remainingHits > 0 && remainingRoundTrips > 0);
      }



      /*******************************************************************************
       ** Record one round trip that fetched the given number of raw hits.
       *******************************************************************************/
      void spend(int rawHits)
      {
         remainingHits -= rawHits;
         remainingRoundTrips--;
      }
   }



   /*******************************************************************************
    ** One page of results with its total and paging flags.
    *******************************************************************************/
   private record Page(List<QuickSearchResult> results, Long totalHits, Boolean lowerBound, Boolean hasMore)
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      QuickSearchOutput toOutput()
      {
         return (new QuickSearchOutput().withResults(results).withTotalHits(totalHits).withTotalHitsIsLowerBound(lowerBound).withHasMore(hasMore));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static QuickSearchOutput emptyOutput()
   {
      return (new QuickSearchOutput().withResults(Collections.emptyList()).withTotalHits(0L).withTotalHitsIsLowerBound(false).withHasMore(false));
   }

}

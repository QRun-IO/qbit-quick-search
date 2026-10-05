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
 *******************************************************************************/
public class QuickSearchAction
{
   private static final QLogger LOG = QLogger.getLogger(QuickSearchAction.class);

   public static final int DEFAULT_LIMIT       = 25;
   public static final int MIN_TERM_LENGTH     = 2;
   public static final int MAX_TERM_LENGTH     = 100;
   public static final int MAX_LIMIT_PER_TABLE = 100;



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

      if(offset + limit > QuickSearchOpenSearchClient.MAX_RESULT_WINDOW)
      {
         limit = Math.max(0, QuickSearchOpenSearchClient.MAX_RESULT_WINDOW - offset);
      }
      if(limit == 0)
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
         return (searchPerTable(client, normalizedTerm, allowedTables, Math.min(input.getLimitPerTable(), maxLimit), tableConfigs, applyLocks));
      }

      int fetchSize = applyLocks ? Math.min(limit * 2, Math.max(limit, QuickSearchOpenSearchClient.MAX_RESULT_WINDOW - offset)) : limit;

      SearchResponse<OpenSearchDocument> response = runSearch(client, normalizedTerm, allowedTables, fetchSize, offset, tableConfigs);
      List<QuickSearchResult>            results  = toResults(response);
      int                                rawHits  = results.size();

      if(applyLocks)
      {
         results = filterByRecordAccess(results, tableConfigs);
      }

      boolean truncated = results.size() > limit;
      if(truncated)
      {
         results = new ArrayList<>(results.subList(0, limit));
      }

      long    totalHits  = response.hits().total() != null ? response.hits().total().value() : results.size();
      boolean lowerBound = response.hits().total() != null && response.hits().total().relation() == TotalHitsRelation.Gte;
      boolean hasMore    = truncated || (offset + rawHits) < totalHits;

      if(applyLocks)
      {
         ////////////////////////////////////////////////////////////////////////////
         // the raw total would reveal how many matching rows the session may not //
         // see; report only what was verified accessible. hasMore still follows  //
         // the raw window: it says only that more candidates exist past it, so a //
         // caller can page on to accessible rows instead of stopping short.      //
         ////////////////////////////////////////////////////////////////////////////
         totalHits  = offset + results.size() + (truncated ? 1 : 0);
         lowerBound = truncated || hasMore;
         hasMore    = truncated || hasMore;
      }

      return (new QuickSearchOutput()
         .withResults(results)
         .withTotalHits(totalHits)
         .withTotalHitsIsLowerBound(lowerBound)
         .withHasMore(hasMore));
   }



   /*******************************************************************************
    ** Search each allowed table separately, up to limitPerTable hits each.
    *******************************************************************************/
   private QuickSearchOutput searchPerTable(QuickSearchOpenSearchClient client, String term, List<String> allowedTables, int limitPerTable, List<QuickSearchableTableConfig> tableConfigs, boolean applyLocks) throws QException
   {
      List<QuickSearchResult> results    = new ArrayList<>();
      long                    totalHits  = 0;
      boolean                 hasMore    = false;
      boolean                 lowerBound = false;

      for(String tableName : allowedTables)
      {
         int                                fetchSize = applyLocks ? limitPerTable * 2 : limitPerTable;
         SearchResponse<OpenSearchDocument> response  = runSearch(client, term, List.of(tableName), fetchSize, 0, tableConfigs);
         List<QuickSearchResult>            tableHits = toResults(response);

         if(applyLocks)
         {
            tableHits = filterByRecordAccess(tableHits, tableConfigs);
            lowerBound = true;
         }
         if(tableHits.size() > limitPerTable)
         {
            tableHits = new ArrayList<>(tableHits.subList(0, limitPerTable));
            hasMore   = true;
         }

         long tableTotal = response.hits().total() != null ? response.hits().total().value() : tableHits.size();
         if(applyLocks)
         {
            tableTotal = tableHits.size();
         }
         totalHits += tableTotal;
         hasMore |= tableHits.size() < tableTotal;
         lowerBound |= response.hits().total() != null && response.hits().total().relation() == TotalHitsRelation.Gte;

         results.addAll(tableHits);
      }

      return (new QuickSearchOutput().withResults(results).withTotalHits(totalHits).withTotalHitsIsLowerBound(lowerBound).withHasMore(hasMore));
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

         QueryInput queryInput = new QueryInput();
         queryInput.setTableName(table.getName());
         queryInput.setFilter(new QQueryFilter(new QFilterCriteria(table.getPrimaryKeyField(), QCriteriaOperator.IN, ids)));

         Set<String> found = new HashSet<>();
         for(QRecord record : CollectionUtils.nonNullList(new QueryAction().execute(queryInput).getRecords()))
         {
            found.add(record.getValueString(table.getPrimaryKeyField()));
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
   private static SearchResponse<OpenSearchDocument> runSearch(QuickSearchOpenSearchClient client, String term, List<String> tables, int fetchSize, int offset, List<QuickSearchableTableConfig> tableConfigs) throws QException
   {
      try
      {
         return (client.search(term, tables, fetchSize, offset, tableConfigs));
      }
      catch(OpenSearchException e)
      {
         throw (new QException("Quick search failed: " + (e.error() != null && e.error().reason() != null ? e.error().reason() : e.getMessage()), e));
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

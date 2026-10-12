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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.tables.RecordSearchAction;
import com.kingsrook.qqq.backend.core.actions.tables.RecordSearchProviderInterface;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.search.RecordSearchInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.search.RecordSearchOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.search.RecordSearchResult;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
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
 ** one _msearch request with a top-N search per table and returns record ids
 ** only, best first; core re-reads them by primary key through QueryAction,
 ** so table permissions, record security locks and record labels are core's
 ** job, and ids of deleted records drop out. Each table gets
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

      Map<String, List<String>> idsByTable;
      try
      {
         idsByTable = runtime.getClient().searchRecordIdsPerTable(term, tableNames, idsPerTable, runtime.getDiscoveredTables(), timeoutMillis);
      }
      catch(QException | RuntimeException e)
      {
         runtime.markRecordSearchFailed();
         LOG.info("Quick Search record search failed; core searches these tables itself, and Quick Search claims none for a while", logPair("tables", tableNames),
            logPair("backoffMillis", QuickSearchRuntime.RECORD_SEARCH_BACKOFF_MILLIS), logPair("error", e.getMessage()));
         throw (e instanceof QException qe ? qe : new QException("Quick Search record search failed: " + e.getMessage(), e));
      }

      for(String tableName : tableNames)
      {
         for(String recordId : CollectionUtils.nonNullList(idsByTable.get(tableName)))
         {
            results.add(new RecordSearchResult().withTableName(tableName).withRecordId(recordId));
         }
      }

      return (new RecordSearchOutput().withResults(results));
   }

}

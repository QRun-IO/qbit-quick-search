/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2021-2026.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qbits.quicksearch.api;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchAction;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchInput;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchOutput;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchResult;
import com.kingsrook.qbits.quicksearch.processes.IndexingUtils;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.logging.QLogger;


/*******************************************************************************
 ** Session-only typeahead over the existing permission-aware search action.
 *******************************************************************************/
public final class QuickSearchSuggestService
{
   private static final QLogger LOG = QLogger.getLogger(QuickSearchSuggestService.class);



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private QuickSearchSuggestService()
   {
   }



   /*******************************************************************************
    ** One candidate batch per table, with one extra visible hit for hasMore.
    ** A short term or unavailable search returns an empty response. hasMore only
    ** confirms an extra accessible hit in this bounded batch, never hidden rows.
    *******************************************************************************/
   public static QuickSearchSuggest suggest(String query, Integer limitPerTable, Integer maxTables, String tables) throws QException
   {
      if(QContext.getQSession() == null)
      {
         throw (new QPermissionDeniedException("A session is required for Quick Search suggestions."));
      }
      if(query == null)
      {
         throw (new QBadRequestException("q is required."));
      }
      String normalized = IndexingUtils.normalizeSearchText(query);
      if(normalized.length() > QuickSearchAction.MAX_TERM_LENGTH)
      {
         throw (new QBadRequestException("Search term is too long (maximum 100 characters)."));
      }
      Integer itemLimit = bounded(limitPerTable, 5, 10, "limitPerTable");
      Integer groupLimit = bounded(maxTables, 8, 20, "maxTables");
      QuickSearchSuggest empty = new QuickSearchSuggest(normalized, List.of(), false);
      QuickSearchRuntime runtime = QuickSearchRuntime.get();
      if(normalized.length() < QuickSearchAction.MIN_TERM_LENGTH || runtime == null)
      {
         return (empty);
      }

      List<String> tableNames = tables == null ? null : Arrays.stream(tables.split(",")).map(String::trim).filter(name -> !name.isEmpty()).distinct().toList();
      if(tableNames != null && tableNames.isEmpty())
      {
         return (empty);
      }

      QuickSearchOutput output;
      try
      {
         if(runtime.getClient() == null)
         {
            return (empty);
         }
         output = new QuickSearchAction().execute(new QuickSearchInput().withSearchTerm(normalized)
            .withTableNames(tableNames).withLimitPerTable(itemLimit + 1).withSinglePass(true));
      }
      catch(QPermissionDeniedException e)
      {
         throw (e);
      }
      catch(QException | RuntimeException e)
      {
         LOG.debug("Quick Search suggestions unavailable", e);
         return (empty);
      }

      Map<String, List<QuickSearchResult>> byTable = new LinkedHashMap<>();
      for(QuickSearchResult result : output.getResults())
      {
         byTable.computeIfAbsent(result.getTableName(), key -> new ArrayList<>()).add(result);
      }
      List<List<QuickSearchResult>> ranked = new ArrayList<>(byTable.values());
      ranked.sort(Comparator.<List<QuickSearchResult>, Float>comparing(
         hits -> hits.stream().map(QuickSearchResult::getScore).filter(score -> score != null).max(Float::compare).orElse(Float.NEGATIVE_INFINITY))
         .reversed().thenComparing(hits -> hits.get(0).getTableName()));

      List<QuickSearchSuggest.Group> groups = new ArrayList<>();
      for(List<QuickSearchResult> hits : ranked.subList(0, Math.min(groupLimit, ranked.size())))
      {
         QuickSearchResult first = hits.get(0);
         List<QuickSearchSuggest.Item> items = hits.stream().limit(itemLimit).map(hit ->
         {
            String label = hit.getRecordLabel();
            if(label == null || label.isBlank())
            {
               label = hit.getTableLabel() + " #" + hit.getRecordId();
            }
            return (new QuickSearchSuggest.Item(hit.getRecordId(), label, hit.getSubtitle()));
         }).toList();
         groups.add(new QuickSearchSuggest.Group(first.getTableName(), first.getTableLabel(), hits.size() > itemLimit, items));
      }
      return (new QuickSearchSuggest(normalized, groups, ranked.size() > groupLimit));
   }



   /*******************************************************************************
    ** Reject invalid ranges instead of silently changing a caller's intent.
    *******************************************************************************/
   private static Integer bounded(Integer value, Integer defaultValue, Integer maximum, String name) throws QBadRequestException
   {
      if(value == null)
      {
         return (defaultValue);
      }
      if(value < 1 || value > maximum)
      {
         throw (new QBadRequestException(name + " must be between 1 and " + maximum + "."));
      }
      return (value);
   }
}

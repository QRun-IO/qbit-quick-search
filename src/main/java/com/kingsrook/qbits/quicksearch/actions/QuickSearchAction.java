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
import java.util.Collections;
import java.util.List;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitContext;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import com.kingsrook.qbits.quicksearch.processes.IndexingUtils;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Executes quick search queries against OpenSearch.
 *******************************************************************************/
public class QuickSearchAction
{
   private static final QLogger LOG = QLogger.getLogger(QuickSearchAction.class);



   /***************************************************************************
    ** Execute a quick search.
    ***************************************************************************/
   public QuickSearchOutput execute(QuickSearchInput input) throws QException
   {
      String searchTerm = IndexingUtils.normalizeSearchText(input.getSearchTerm());

      if(!StringUtils.hasContent(searchTerm))
      {
         return new QuickSearchOutput()
            .withResults(Collections.emptyList())
            .withTotalCount(0);
      }

      Integer limit = input.getLimit();
      if(limit == null || limit <= 0)
      {
         limit = 50;
      }

      LOG.debug("Executing quick search", logPair("term", searchTerm), logPair("limit", limit), logPair("table", input.getTableName()));

      QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(QuickSearchQBitContext.getConfig());

      try
      {
         List<OpenSearchDocument> documents = client.search(searchTerm, input.getTableName(), limit);

         List<QuickSearchResult> results = new ArrayList<>();
         for(OpenSearchDocument doc : documents)
         {
            results.add(new QuickSearchResult()
               .withTableName(doc.getSourceTable())
               .withRecordId(doc.getRecordId())
               .withRecordLabel(doc.getRecordLabel())
               .withMatchedText(doc.getSearchableText()));
         }

         return new QuickSearchOutput()
            .withResults(results)
            .withTotalCount(results.size());
      }
      finally
      {
         client.close();
      }
   }

}

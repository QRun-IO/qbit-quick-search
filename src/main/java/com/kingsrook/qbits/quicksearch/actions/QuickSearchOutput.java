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


import java.util.List;


/*******************************************************************************
 ** Output DTO for the QuickSearch action.
 **
 ** Carries the list of matching results, the total hit count from OpenSearch,
 ** and a convenience flag indicating whether additional pages are available.
 *******************************************************************************/
public class QuickSearchOutput
{

   private List<QuickSearchResult> results;
   private Long                    totalHits;
   private Boolean                 hasMore;


   /*******************************************************************************
    ** Getter for results.
    *******************************************************************************/
   public List<QuickSearchResult> getResults()
   {
      return results;
   }


   /*******************************************************************************
    ** Setter for results.
    *******************************************************************************/
   public void setResults(List<QuickSearchResult> results)
   {
      this.results = results;
   }


   /*******************************************************************************
    ** Fluent setter for results.
    *******************************************************************************/
   public QuickSearchOutput withResults(List<QuickSearchResult> results)
   {
      this.results = results;
      return this;
   }


   /*******************************************************************************
    ** Getter for totalHits.
    *******************************************************************************/
   public Long getTotalHits()
   {
      return totalHits;
   }


   /*******************************************************************************
    ** Setter for totalHits.
    *******************************************************************************/
   public void setTotalHits(Long totalHits)
   {
      this.totalHits = totalHits;
   }


   /*******************************************************************************
    ** Fluent setter for totalHits.
    *******************************************************************************/
   public QuickSearchOutput withTotalHits(Long totalHits)
   {
      this.totalHits = totalHits;
      return this;
   }


   /*******************************************************************************
    ** Getter for hasMore.
    *******************************************************************************/
   public Boolean getHasMore()
   {
      return hasMore;
   }


   /*******************************************************************************
    ** Setter for hasMore.
    *******************************************************************************/
   public void setHasMore(Boolean hasMore)
   {
      this.hasMore = hasMore;
   }


   /*******************************************************************************
    ** Fluent setter for hasMore.
    *******************************************************************************/
   public QuickSearchOutput withHasMore(Boolean hasMore)
   {
      this.hasMore = hasMore;
      return this;
   }

}

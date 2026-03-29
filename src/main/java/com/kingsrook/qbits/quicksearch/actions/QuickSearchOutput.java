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

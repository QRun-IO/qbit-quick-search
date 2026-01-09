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
 ** Output of a quick search request.
 *******************************************************************************/
public class QuickSearchOutput
{
   private List<QuickSearchResult> results;
   private Integer                 totalCount;



   /***************************************************************************
    ** Getter for results
    ***************************************************************************/
   public List<QuickSearchResult> getResults()
   {
      return (this.results);
   }



   /***************************************************************************
    ** Setter for results
    ***************************************************************************/
   public void setResults(List<QuickSearchResult> results)
   {
      this.results = results;
   }



   /***************************************************************************
    ** Fluent setter for results
    ***************************************************************************/
   public QuickSearchOutput withResults(List<QuickSearchResult> results)
   {
      this.results = results;
      return (this);
   }



   /***************************************************************************
    ** Getter for totalCount
    ***************************************************************************/
   public Integer getTotalCount()
   {
      return (this.totalCount);
   }



   /***************************************************************************
    ** Setter for totalCount
    ***************************************************************************/
   public void setTotalCount(Integer totalCount)
   {
      this.totalCount = totalCount;
   }



   /***************************************************************************
    ** Fluent setter for totalCount
    ***************************************************************************/
   public QuickSearchOutput withTotalCount(Integer totalCount)
   {
      this.totalCount = totalCount;
      return (this);
   }

}

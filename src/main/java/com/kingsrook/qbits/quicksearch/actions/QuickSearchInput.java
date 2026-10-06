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
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;


/*******************************************************************************
 ** Input DTO for the QuickSearch action.
 **
 ** Carries the search term plus optional filtering and pagination parameters.
 *******************************************************************************/
public class QuickSearchInput extends AbstractActionInput
{

   private String  searchTerm;
   private String  tableName;
   private Integer limit;
   private Integer offset;
   private List<String> tableNames;
   private Integer limitPerTable;


   /*******************************************************************************
    ** Getter for searchTerm.
    *******************************************************************************/
   public String getSearchTerm()
   {
      return searchTerm;
   }


   /*******************************************************************************
    ** Setter for searchTerm.
    *******************************************************************************/
   public void setSearchTerm(String searchTerm)
   {
      this.searchTerm = searchTerm;
   }


   /*******************************************************************************
    ** Fluent setter for searchTerm.
    *******************************************************************************/
   public QuickSearchInput withSearchTerm(String searchTerm)
   {
      this.searchTerm = searchTerm;
      return this;
   }


   /*******************************************************************************
    ** Getter for tableName.
    *******************************************************************************/
   public String getTableName()
   {
      return tableName;
   }


   /*******************************************************************************
    ** Setter for tableName.
    *******************************************************************************/
   public void setTableName(String tableName)
   {
      this.tableName = tableName;
   }


   /*******************************************************************************
    ** Fluent setter for tableName.
    *******************************************************************************/
   public QuickSearchInput withTableName(String tableName)
   {
      this.tableName = tableName;
      return this;
   }


   /*******************************************************************************
    ** Getter for limit.
    *******************************************************************************/
   public Integer getLimit()
   {
      return limit;
   }


   /*******************************************************************************
    ** Setter for limit.
    *******************************************************************************/
   public void setLimit(Integer limit)
   {
      this.limit = limit;
   }


   /*******************************************************************************
    ** Fluent setter for limit.
    *******************************************************************************/
   public QuickSearchInput withLimit(Integer limit)
   {
      this.limit = limit;
      return this;
   }


   /*******************************************************************************
    ** Getter for offset: the number of results to skip, counting only results
    ** the session can see (with record security locks on, hits filtered out
    ** by a lock are not counted). With limitPerTable it applies within each
    ** table. Results are reachable only within the first 10,000 raw hits; in
    ** per-table mode with locks each table gets an equal share of that scan
    ** budget, so deep pages of a heavily locked table may be unreachable
    ** (hasMore and totalHitsIsLowerBound stay true).
    *******************************************************************************/
   public Integer getOffset()
   {
      return offset;
   }


   /*******************************************************************************
    ** Setter for offset.
    *******************************************************************************/
   public void setOffset(Integer offset)
   {
      this.offset = offset;
   }


   /*******************************************************************************
    ** Fluent setter for offset.
    *******************************************************************************/
   public QuickSearchInput withOffset(Integer offset)
   {
      this.offset = offset;
      return this;
   }


   /*******************************************************************************
    ** Getter for tableNames (restrict the search to these tables)
    *******************************************************************************/
   public List<String> getTableNames()
   {
      return tableNames;
   }



   /*******************************************************************************
    ** Setter for tableNames
    *******************************************************************************/
   public void setTableNames(List<String> tableNames)
   {
      this.tableNames = tableNames;
   }



   /*******************************************************************************
    ** Fluent setter for tableNames
    *******************************************************************************/
   public QuickSearchInput withTableNames(List<String> tableNames)
   {
      this.tableNames = tableNames;
      return this;
   }



   /*******************************************************************************
    ** Getter for limitPerTable (search each table separately, up to this many)
    *******************************************************************************/
   public Integer getLimitPerTable()
   {
      return limitPerTable;
   }



   /*******************************************************************************
    ** Setter for limitPerTable
    *******************************************************************************/
   public void setLimitPerTable(Integer limitPerTable)
   {
      this.limitPerTable = limitPerTable;
   }



   /*******************************************************************************
    ** Fluent setter for limitPerTable
    *******************************************************************************/
   public QuickSearchInput withLimitPerTable(Integer limitPerTable)
   {
      this.limitPerTable = limitPerTable;
      return this;
   }

}

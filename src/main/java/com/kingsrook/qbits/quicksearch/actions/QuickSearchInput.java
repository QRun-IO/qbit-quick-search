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


/*******************************************************************************
 ** Input DTO for the QuickSearch action.
 **
 ** Carries the search term plus optional filtering and pagination parameters.
 *******************************************************************************/
public class QuickSearchInput
{

   private String  searchTerm;
   private String  tableName;
   private Integer limit;
   private Integer offset;


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
    ** Getter for offset.
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

}

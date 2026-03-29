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

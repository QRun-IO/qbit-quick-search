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
 ** A single search hit returned by a QuickSearch query, representing one
 ** matching record with its relevance score and optional highlight snippet.
 *******************************************************************************/
public class QuickSearchResult
{

   private String tableName;
   private String recordId;
   private String recordLabel;
   private Float  score;
   private String highlightSnippet;


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
   public QuickSearchResult withTableName(String tableName)
   {
      this.tableName = tableName;
      return this;
   }


   /*******************************************************************************
    ** Getter for recordId.
    *******************************************************************************/
   public String getRecordId()
   {
      return recordId;
   }


   /*******************************************************************************
    ** Setter for recordId.
    *******************************************************************************/
   public void setRecordId(String recordId)
   {
      this.recordId = recordId;
   }


   /*******************************************************************************
    ** Fluent setter for recordId.
    *******************************************************************************/
   public QuickSearchResult withRecordId(String recordId)
   {
      this.recordId = recordId;
      return this;
   }


   /*******************************************************************************
    ** Getter for recordLabel.
    *******************************************************************************/
   public String getRecordLabel()
   {
      return recordLabel;
   }


   /*******************************************************************************
    ** Setter for recordLabel.
    *******************************************************************************/
   public void setRecordLabel(String recordLabel)
   {
      this.recordLabel = recordLabel;
   }


   /*******************************************************************************
    ** Fluent setter for recordLabel.
    *******************************************************************************/
   public QuickSearchResult withRecordLabel(String recordLabel)
   {
      this.recordLabel = recordLabel;
      return this;
   }


   /*******************************************************************************
    ** Getter for score.
    *******************************************************************************/
   public Float getScore()
   {
      return score;
   }


   /*******************************************************************************
    ** Setter for score.
    *******************************************************************************/
   public void setScore(Float score)
   {
      this.score = score;
   }


   /*******************************************************************************
    ** Fluent setter for score.
    *******************************************************************************/
   public QuickSearchResult withScore(Float score)
   {
      this.score = score;
      return this;
   }


   /*******************************************************************************
    ** Getter for highlightSnippet.
    *******************************************************************************/
   public String getHighlightSnippet()
   {
      return highlightSnippet;
   }


   /*******************************************************************************
    ** Setter for highlightSnippet.
    *******************************************************************************/
   public void setHighlightSnippet(String highlightSnippet)
   {
      this.highlightSnippet = highlightSnippet;
   }


   /*******************************************************************************
    ** Fluent setter for highlightSnippet.
    *******************************************************************************/
   public QuickSearchResult withHighlightSnippet(String highlightSnippet)
   {
      this.highlightSnippet = highlightSnippet;
      return this;
   }

}

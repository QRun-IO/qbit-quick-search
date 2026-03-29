/*
 * Copyright 2024 Kingsrook, LLC
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

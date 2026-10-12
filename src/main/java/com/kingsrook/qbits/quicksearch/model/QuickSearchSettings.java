/*
 * Copyright 2026 Kingsrook, LLC
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

package com.kingsrook.qbits.quicksearch.model;


import java.time.Instant;
import com.kingsrook.qqq.backend.core.model.data.QField;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;


/*******************************************************************************
 ** Entity bean for the quick_search_settings table: the one row (id 1) of
 ** system-level Quick Search settings. indexingPaused stops all indexing
 ** (real-time and scheduled) while search keeps serving the existing index;
 ** the priming fields report what startup self-priming last added.
 *******************************************************************************/
public class QuickSearchSettings extends QRecordEntity
{
   public static final String  TABLE_NAME  = "quick_search_settings";
   public static final Integer SETTINGS_ID = 1;

   @QField()
   private Integer id;
   @QField()
   private Boolean indexingPaused;
   @QField()
   private Instant pausedAt;
   @QField()
   private String pausedBy;
   @QField()
   private String pauseReason;
   @QField()
   private Instant lastPrimedAt;
   @QField()
   private String lastPrimingSummary;
   @QField()
   private Integer lastPrimingNewTables;
   @QField()
   private Instant createDate;
   @QField()
   private Instant modifyDate;


   /*******************************************************************************
    ** Getter for id
    *******************************************************************************/
   public Integer getId()
   {
      return (this.id);
   }



   /*******************************************************************************
    ** Setter for id
    *******************************************************************************/
   public void setId(Integer id)
   {
      this.id = id;
   }



   /*******************************************************************************
    ** Fluent setter for id
    *******************************************************************************/
   public QuickSearchSettings withId(Integer id)
   {
      this.id = id;
      return (this);
   }


   /*******************************************************************************
    ** Getter for indexingPaused
    *******************************************************************************/
   public Boolean getIndexingPaused()
   {
      return (this.indexingPaused);
   }



   /*******************************************************************************
    ** Setter for indexingPaused
    *******************************************************************************/
   public void setIndexingPaused(Boolean indexingPaused)
   {
      this.indexingPaused = indexingPaused;
   }



   /*******************************************************************************
    ** Fluent setter for indexingPaused
    *******************************************************************************/
   public QuickSearchSettings withIndexingPaused(Boolean indexingPaused)
   {
      this.indexingPaused = indexingPaused;
      return (this);
   }


   /*******************************************************************************
    ** Getter for pausedAt
    *******************************************************************************/
   public Instant getPausedAt()
   {
      return (this.pausedAt);
   }



   /*******************************************************************************
    ** Setter for pausedAt
    *******************************************************************************/
   public void setPausedAt(Instant pausedAt)
   {
      this.pausedAt = pausedAt;
   }



   /*******************************************************************************
    ** Fluent setter for pausedAt
    *******************************************************************************/
   public QuickSearchSettings withPausedAt(Instant pausedAt)
   {
      this.pausedAt = pausedAt;
      return (this);
   }


   /*******************************************************************************
    ** Getter for pausedBy
    *******************************************************************************/
   public String getPausedBy()
   {
      return (this.pausedBy);
   }



   /*******************************************************************************
    ** Setter for pausedBy
    *******************************************************************************/
   public void setPausedBy(String pausedBy)
   {
      this.pausedBy = pausedBy;
   }



   /*******************************************************************************
    ** Fluent setter for pausedBy
    *******************************************************************************/
   public QuickSearchSettings withPausedBy(String pausedBy)
   {
      this.pausedBy = pausedBy;
      return (this);
   }


   /*******************************************************************************
    ** Getter for pauseReason
    *******************************************************************************/
   public String getPauseReason()
   {
      return (this.pauseReason);
   }



   /*******************************************************************************
    ** Setter for pauseReason
    *******************************************************************************/
   public void setPauseReason(String pauseReason)
   {
      this.pauseReason = pauseReason;
   }



   /*******************************************************************************
    ** Fluent setter for pauseReason
    *******************************************************************************/
   public QuickSearchSettings withPauseReason(String pauseReason)
   {
      this.pauseReason = pauseReason;
      return (this);
   }


   /*******************************************************************************
    ** Getter for lastPrimedAt
    *******************************************************************************/
   public Instant getLastPrimedAt()
   {
      return (this.lastPrimedAt);
   }



   /*******************************************************************************
    ** Setter for lastPrimedAt
    *******************************************************************************/
   public void setLastPrimedAt(Instant lastPrimedAt)
   {
      this.lastPrimedAt = lastPrimedAt;
   }



   /*******************************************************************************
    ** Fluent setter for lastPrimedAt
    *******************************************************************************/
   public QuickSearchSettings withLastPrimedAt(Instant lastPrimedAt)
   {
      this.lastPrimedAt = lastPrimedAt;
      return (this);
   }


   /*******************************************************************************
    ** Getter for lastPrimingSummary
    *******************************************************************************/
   public String getLastPrimingSummary()
   {
      return (this.lastPrimingSummary);
   }



   /*******************************************************************************
    ** Setter for lastPrimingSummary
    *******************************************************************************/
   public void setLastPrimingSummary(String lastPrimingSummary)
   {
      this.lastPrimingSummary = lastPrimingSummary;
   }



   /*******************************************************************************
    ** Fluent setter for lastPrimingSummary
    *******************************************************************************/
   public QuickSearchSettings withLastPrimingSummary(String lastPrimingSummary)
   {
      this.lastPrimingSummary = lastPrimingSummary;
      return (this);
   }


   /*******************************************************************************
    ** Getter for lastPrimingNewTables
    *******************************************************************************/
   public Integer getLastPrimingNewTables()
   {
      return (this.lastPrimingNewTables);
   }



   /*******************************************************************************
    ** Setter for lastPrimingNewTables
    *******************************************************************************/
   public void setLastPrimingNewTables(Integer lastPrimingNewTables)
   {
      this.lastPrimingNewTables = lastPrimingNewTables;
   }



   /*******************************************************************************
    ** Fluent setter for lastPrimingNewTables
    *******************************************************************************/
   public QuickSearchSettings withLastPrimingNewTables(Integer lastPrimingNewTables)
   {
      this.lastPrimingNewTables = lastPrimingNewTables;
      return (this);
   }


   /*******************************************************************************
    ** Getter for createDate
    *******************************************************************************/
   public Instant getCreateDate()
   {
      return (this.createDate);
   }



   /*******************************************************************************
    ** Setter for createDate
    *******************************************************************************/
   public void setCreateDate(Instant createDate)
   {
      this.createDate = createDate;
   }



   /*******************************************************************************
    ** Fluent setter for createDate
    *******************************************************************************/
   public QuickSearchSettings withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }


   /*******************************************************************************
    ** Getter for modifyDate
    *******************************************************************************/
   public Instant getModifyDate()
   {
      return (this.modifyDate);
   }



   /*******************************************************************************
    ** Setter for modifyDate
    *******************************************************************************/
   public void setModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
   }



   /*******************************************************************************
    ** Fluent setter for modifyDate
    *******************************************************************************/
   public QuickSearchSettings withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }
}

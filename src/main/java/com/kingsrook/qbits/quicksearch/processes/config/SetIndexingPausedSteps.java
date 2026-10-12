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

package com.kingsrook.qbits.quicksearch.processes.config;


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.tables.CountAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.Status;
import com.kingsrook.qqq.backend.core.model.actions.tables.count.CountInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractLoadStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractTransformStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.ProcessSummaryProviderInterface;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.config.QuickSearchConfigPrimer;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;
import com.kingsrook.qbits.quicksearch.model.QuickSearchSettings;


/*******************************************************************************
 ** The steps of Pause Indexing and Resume Indexing (record actions on
 ** quick_search_settings; the process's "pause" value says which). Pausing
 ** stops all indexing, real-time and scheduled, on every server (within the
 ** settings cache time); changes made meanwhile are kept and replayed on
 ** resume, and search keeps serving the current index. The settings table's
 ** customizer stamps who paused it and when.
 *******************************************************************************/
public final class SetIndexingPausedSteps
{
   public static final String FIELD_PAUSE  = "pause";
   public static final String FIELD_REASON = "pauseReason";



   /*******************************************************************************
    ** Utility holder - no instances.
    *******************************************************************************/
   private SetIndexingPausedSteps()
   {
   }



   /*******************************************************************************
    ** Transform: say what pausing or resuming does.
    *******************************************************************************/
   public static class Transform extends AbstractTransformStep
   {
      private final List<ProcessSummaryLine> summary = new ArrayList<>();



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         boolean pause = Boolean.TRUE.equals(input.getValueBoolean(FIELD_PAUSE));
         for(QRecord record : input.getRecords())
         {
            boolean paused = Boolean.TRUE.equals(record.getValueBoolean("indexingPaused"));
            if(pause == paused)
            {
               summary.add(ConfigProcessSupport.line(Status.INFO, pause
                  ? "Indexing is already paused (since " + record.getValueString("pausedAt") + " by " + record.getValueString("pausedBy") + ")."
                  : "Indexing is not paused."));
               continue;
            }
            if(pause)
            {
               summary.add(ConfigProcessSupport.line(Status.OK, "All indexing will pause on every server (real-time and scheduled; a running rebuild stops after its current batch)."));
               summary.add(ConfigProcessSupport.line(Status.INFO, "Search keeps serving the current index. Changes made while paused are kept and indexed when you resume."));
            }
            else
            {
               summary.add(ConfigProcessSupport.line(Status.OK, "Indexing will resume; " + deferredCount() + " changes made while paused will be indexed by the next update."));
            }
            output.addRecord(record);
         }
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public ArrayList<ProcessSummaryLineInterface> getProcessSummary(RunBackendStepOutput output, boolean isForResultScreen)
      {
         return (ConfigProcessSupport.lines(summary));
      }
   }



   /*******************************************************************************
    ** Load: set the flag (the settings customizer does the rest).
    *******************************************************************************/
   public static class Load extends AbstractLoadStep implements ProcessSummaryProviderInterface
   {
      private final List<ProcessSummaryLine> summary = new ArrayList<>();



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         QuickSearchQBitConfig config = ConfigProcessSupport.config();
         boolean               pause  = Boolean.TRUE.equals(input.getValueBoolean(FIELD_PAUSE));
         for(QRecord record : input.getRecords())
         {
            QRecord update = new QRecord().withValue("id", record.getValue("id")).withValue("indexingPaused", pause);
            if(pause)
            {
               update.setValue("pauseReason", input.getValueString(FIELD_REASON));
            }
            QuickSearchConfigPrimer.updateAll(config.applyTablePrefix(QuickSearchSettings.TABLE_NAME), List.of(update));
            summary.add(ConfigProcessSupport.line(Status.OK, pause ? "Indexing is paused. Search keeps serving the current index." : "Indexing resumed."));
            output.addRecord(record);
         }
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public ArrayList<ProcessSummaryLineInterface> getProcessSummary(RunBackendStepOutput output, boolean isForResultScreen)
      {
         return (ConfigProcessSupport.lines(summary));
      }
   }



   /*******************************************************************************
    ** Changes deferred while paused (0 when they cannot be counted).
    *******************************************************************************/
   static Integer deferredCount()
   {
      try
      {
         String table = ConfigProcessSupport.config().applyTablePrefix(QuickSearchFailedEvent.TABLE_NAME);
         if(QContext.getQInstance().getTable(table) == null)
         {
            return (0);
         }
         CountInput countInput = new CountInput(table);
         countInput.setFilter(new QQueryFilter(new QFilterCriteria("status", QCriteriaOperator.EQUALS, QuickSearchFailedEvent.STATUS_DEFERRED)));
         Integer count = new CountAction().execute(countInput).getCount();
         return (count == null ? 0 : count);
      }
      catch(Exception e)
      {
         return (0);
      }
   }

}

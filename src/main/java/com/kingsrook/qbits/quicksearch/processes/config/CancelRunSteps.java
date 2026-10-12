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
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.Status;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractLoadStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractTransformStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.ProcessSummaryProviderInterface;
import com.kingsrook.qbits.quicksearch.config.QuickSearchConfigPrimer;
import com.kingsrook.qbits.quicksearch.config.QuickSearchConfigWrites;
import com.kingsrook.qbits.quicksearch.processes.AbstractIndexingStep;


/*******************************************************************************
 ** The steps of Cancel Index Run (record action on quick_search_index_run):
 ** sets the run's cooperative cancel flag. The run checks it between batches
 ** and ends CANCELLED with its counts so far; a cancelled all-tables rebuild
 ** keeps the previous index in service, and a cancelled queued build waits
 ** for Apply Search Index Changes. Runs that are not RUNNING are skipped.
 *******************************************************************************/
public final class CancelRunSteps
{

   /*******************************************************************************
    ** Utility holder - no instances.
    *******************************************************************************/
   private CancelRunSteps()
   {
   }



   /*******************************************************************************
    ** Transform: which runs can be cancelled.
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
         for(QRecord record : input.getRecords())
         {
            String name = describe(record);
            if(!AbstractIndexingStep.RUN_RUNNING.equals(record.getValueString("status")))
            {
               summary.add(ConfigProcessSupport.line(Status.INFO, name + " is not running (" + record.getValueString("status") + "); it is skipped."));
               continue;
            }
            if(Boolean.TRUE.equals(record.getValueBoolean("cancelRequested")))
            {
               summary.add(ConfigProcessSupport.line(Status.INFO, name + " was already asked to stop; it is skipped."));
               continue;
            }
            summary.add(ConfigProcessSupport.line(Status.OK, name + " (" + record.getValueString("recordsProcessed") + " records so far) will stop after its current batch and end CANCELLED."));
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
    ** Load: set the cancel flags.
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
         String         table   = ConfigProcessSupport.config().getQuickSearchIndexRunTableName();
         String         user    = QuickSearchConfigWrites.currentUserName();
         List<QRecord>  updates = new ArrayList<>();
         for(QRecord record : input.getRecords())
         {
            updates.add(new QRecord().withValue("id", record.getValue("id")).withValue("cancelRequested", true).withValue("cancelRequestedBy", user));
            summary.add(ConfigProcessSupport.line(Status.OK, describe(record) + " was asked to stop; it ends CANCELLED after its current batch."));
            output.addRecord(record);
         }
         QuickSearchConfigPrimer.updateAll(table, updates);
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
    ** "Run 12 (FULL_REINDEX)".
    *******************************************************************************/
   static String describe(QRecord run)
   {
      return ("Run " + run.getValueString("id") + " (" + run.getValueString("runType") + ")");
   }

}

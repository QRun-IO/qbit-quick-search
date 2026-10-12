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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.processes.AbstractIndexingStep;


/*******************************************************************************
 ** The steps of Apply Search Index Changes (record action on
 ** quick_search_index): the review step previews, per selected index, how its
 ** searchable fields differ from what its documents were last built with
 ** (fields added, removed or changed) and how many records the rebuild
 ** reads; executing queues the rebuild (or runs it now when no scheduler runs
 ** the basepull). Field edits apply to searches and new writes at once and
 ** mark the index NEEDS_REINDEX until this rebuilds the older documents.
 *******************************************************************************/
public final class ApplyIndexChangesSteps
{
   private static final Pattern FIELD_JSON = Pattern.compile("\\{\"fieldName\":\"((?:[^\"\\\\]|\\\\.)*)\"([^}]*)}");



   /*******************************************************************************
    ** Utility holder - no instances.
    *******************************************************************************/
   private ApplyIndexChangesSteps()
   {
   }



   /*******************************************************************************
    ** Field name to its JSON attributes (weight, label, role...), from a
    ** searchableFieldsJson string.
    *******************************************************************************/
   static Map<String, String> parseFields(String json)
   {
      Map<String, String> fields = new LinkedHashMap<>();
      if(json != null)
      {
         Matcher matcher = FIELD_JSON.matcher(json);
         while(matcher.find())
         {
            fields.put(matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\"), matcher.group(2));
         }
      }
      return (fields);
   }



   /*******************************************************************************
    ** The differences between the fields last built and the current ones.
    *******************************************************************************/
   static List<String> describeChanges(String tableName, String builtJson, String currentJson)
   {
      Map<String, String> built   = parseFields(builtJson);
      Map<String, String> current = parseFields(currentJson);
      List<String>        changes = new ArrayList<>();
      for(Map.Entry<String, String> entry : current.entrySet())
      {
         if(!built.containsKey(entry.getKey()))
         {
            changes.add("Added: " + ConfigProcessSupport.fieldLabel(tableName, entry.getKey()));
         }
         else if(!Objects.equals(built.get(entry.getKey()), entry.getValue()))
         {
            changes.add("Changed: " + ConfigProcessSupport.fieldLabel(tableName, entry.getKey()) + " (" + entry.getValue().replaceFirst("^,", "").replace("\"", "") + ")");
         }
      }
      for(String fieldName : built.keySet())
      {
         if(!current.containsKey(fieldName))
         {
            changes.add("Removed: " + ConfigProcessSupport.fieldLabel(tableName, fieldName));
         }
      }
      return (changes);
   }



   /*******************************************************************************
    ** Transform: preview each selected index's changes.
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
         QuickSearchRuntime runtime = QuickSearchRuntime.get();
         for(QRecord record : input.getRecords())
         {
            String tableName = record.getValueString("tableName");
            String label     = ConfigProcessSupport.tableLabel(tableName);
            String status    = record.getValueString("status");
            if(Boolean.FALSE.equals(record.getValueBoolean("enabled")))
            {
               summary.add(ConfigProcessSupport.line(Status.WARNING, label + " is disabled; enable it first (enabling queues its build)."));
               continue;
            }
            if(AbstractIndexingStep.STATUS_PENDING_BUILD.equals(status) || AbstractIndexingStep.STATUS_BUILDING.equals(status))
            {
               summary.add(ConfigProcessSupport.line(Status.INFO, label + " is already queued or building; it is skipped."));
               continue;
            }

            QuickSearchableTableConfig tableConfig = runtime == null ? null : runtime.getTableConfig(tableName);
            if(tableConfig == null)
            {
               summary.add(ConfigProcessSupport.line(Status.ERROR, label + " is not in this application any more; remove it instead."));
               continue;
            }

            List<String> changes = describeChanges(tableName, record.getValueString("searchableFieldsJson"), AbstractIndexingStep.buildSearchableFieldsJson(tableConfig));
            String       reads   = ConfigProcessSupport.aboutRecords(ConfigProcessSupport.countRecords(tableName));
            if(changes.isEmpty())
            {
               summary.add(ConfigProcessSupport.line(Status.OK, label + ": no field changes since its last build; it will be rebuilt anyway (" + reads + ")."));
            }
            else
            {
               summary.add(ConfigProcessSupport.line(Status.OK, label + " will be rebuilt with these field changes (" + reads + "):", changes));
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
    ** Load: queue (or run) each rebuild.
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
         for(QRecord record : input.getRecords())
         {
            String outcome = ConfigProcessSupport.queueOrBuild(record.getValueInteger("id"));
            summary.add(ConfigProcessSupport.line(Status.OK, ConfigProcessSupport.tableLabel(record.getValueString("tableName")) + ": " + outcome + "."));
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

}

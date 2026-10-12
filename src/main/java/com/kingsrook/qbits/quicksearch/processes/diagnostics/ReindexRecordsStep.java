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

package com.kingsrook.qbits.quicksearch.processes.diagnostics;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QUserFacingException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.diagnostics.DiagnosticsHtml;
import com.kingsrook.qbits.quicksearch.diagnostics.DiagnosticsSupport;
import com.kingsrook.qbits.quicksearch.diagnostics.RecordReindexer;
import com.kingsrook.qbits.quicksearch.model.QuickSearchDiagnosticEvent;


/*******************************************************************************
 ** Steps of Reindex in Search, a record action on every indexed table (and
 ** on Quick Search Failed Event, for the event's record): PREPARE asks to
 ** confirm, RUN rebuilds the selected records' documents (RecordReindexer)
 ** and shows the outcome. The only diagnostic action that writes to the
 ** index; it needs the Quick Search admin permission.
 *******************************************************************************/
public class ReindexRecordsStep extends AbstractDiagnosticToolStep
{
   public static final int MAX_RECORDS = 500;



   /*******************************************************************************
    *******************************************************************************/
   @Override
   public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
   {
      QuickSearchRuntime runtime = DiagnosticsSupport.requireRuntime();
      if(STEP_PREPARE.equals(input.getStepName()))
      {
         List<Target> targets = targetsFromInput(input, runtime, MAX_RECORDS);
         Map<String, List<String>> byTable = groupByTable(targets);
         List<String>              lines   = new ArrayList<>();
         byTable.forEach((table, ids) -> lines.add(ids.size() + " record(s) of " + table));
         setHtml(output, STEP_CONFIRM, new DiagnosticsHtml().open()
            .paragraph("Rebuild the search documents of " + String.join(", ", lines) + " from the database now? Records that no longer exist have their documents removed. "
               + "Records are not changed. The run is recorded in Quick Search Diagnostic Event.")
            .close().toString());
         return;
      }

      long start = System.currentTimeMillis();
      DiagnosticsSupport.checkRateLimit(runtime, DiagnosticsSupport.TOOL_REINDEX_RECORDS);

      StringBuilder html = new StringBuilder();
      for(Map.Entry<String, List<String>> entry : groupByTable(targetsFromInput(input, runtime, MAX_RECORDS)).entrySet())
      {
         try
         {
            List<RecordReindexer.Outcome> outcomes = new RecordReindexer().reindex(runtime, entry.getKey(), entry.getValue());
            html.append(RecordReindexer.render(entry.getKey(), outcomes));
            DiagnosticsSupport.recordToolRun(runtime, DiagnosticsSupport.TOOL_REINDEX_RECORDS, QuickSearchDiagnosticEvent.TYPE_REINDEX, List.of(entry.getKey()), null, start, null,
               "records: " + String.join(", ", entry.getValue()) + "; outcomes: " + String.join(", ", outcomes.stream().map(o -> o.recordId() + "=" + o.outcome()).toList()));
         }
         catch(Exception e)
         {
            html.append(failureHtml("Reindexing " + entry.getKey() + " failed", e));
            DiagnosticsSupport.recordToolRun(runtime, DiagnosticsSupport.TOOL_REINDEX_RECORDS, QuickSearchDiagnosticEvent.TYPE_REINDEX, List.of(entry.getKey()), null, start, e,
               "records: " + String.join(", ", entry.getValue()));
         }
      }
      setHtml(output, STEP_RESULT, html.toString());
   }



   /*******************************************************************************
    ** Record ids grouped by table, in selection order.
    *******************************************************************************/
   static Map<String, List<String>> groupByTable(List<Target> targets) throws QUserFacingException
   {
      if(targets.isEmpty())
      {
         throw (new QUserFacingException("No records to reindex."));
      }
      Map<String, List<String>> byTable = new LinkedHashMap<>();
      for(Target target : targets)
      {
         byTable.computeIfAbsent(target.tableName(), k -> new ArrayList<>()).add(target.recordId());
      }
      return (byTable);
   }

}

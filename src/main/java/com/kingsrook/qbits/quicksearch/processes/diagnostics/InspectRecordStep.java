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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QUserFacingException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.diagnostics.DiagnosticsSupport;
import com.kingsrook.qbits.quicksearch.diagnostics.RecordInspector;
import com.kingsrook.qbits.quicksearch.model.QuickSearchDiagnosticEvent;


/*******************************************************************************
 ** Steps of Inspect Search Document, a record action on every indexed table
 ** (and on Quick Search Failed Event, for the event's record). PREPARE shows
 ** each selected record's indexed document next to the database; RUN adds
 ** the match analysis for the entered term and loops back to the form.
 ** Read-only.
 *******************************************************************************/
public class InspectRecordStep extends AbstractDiagnosticToolStep
{
   public static final String FIELD_TERM  = "searchTerm";
   public static final int    MAX_RECORDS = 10;



   /*******************************************************************************
    *******************************************************************************/
   @Override
   public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
   {
      QuickSearchRuntime runtime = DiagnosticsSupport.requireRuntime();
      if(!STEP_PREPARE.equals(input.getStepName()))
      {
         loopBackToForm(output);
      }
      List<Target> targets = targetsFromInput(input, runtime, MAX_RECORDS);

      long   start = System.currentTimeMillis();
      String term  = STEP_PREPARE.equals(input.getStepName()) ? null : input.getValueString(FIELD_TERM);
      try
      {
         DiagnosticsSupport.checkRateLimit(runtime, DiagnosticsSupport.TOOL_INSPECT_RECORD);
      }
      catch(QUserFacingException e)
      {
         setHtml(output, STEP_FORM, failureHtml("Not run", e));
         return;
      }

      Set<String> tables = new LinkedHashSet<>();
      try
      {
         List<RecordInspector.Inspection> inspections = new ArrayList<>();
         for(Target target : targets)
         {
            tables.add(target.tableName());
            inspections.add(new RecordInspector().inspect(runtime, target.tableName(), target.recordId(), term, input));
         }
         setHtml(output, STEP_FORM, RecordInspector.render(inspections, term));
         DiagnosticsSupport.recordToolRun(runtime, DiagnosticsSupport.TOOL_INSPECT_RECORD, QuickSearchDiagnosticEvent.TYPE_TOOL_RUN, tables, term, start, null,
            "records: " + String.join(", ", targets.stream().map(Target::toString).toList()));
      }
      catch(Exception e)
      {
         setHtml(output, STEP_FORM, failureHtml("Inspecting failed (is OpenSearch reachable?)", e));
         DiagnosticsSupport.recordToolRun(runtime, DiagnosticsSupport.TOOL_INSPECT_RECORD, QuickSearchDiagnosticEvent.TYPE_TOOL_RUN, tables, term, start, e, null);
      }
   }

}

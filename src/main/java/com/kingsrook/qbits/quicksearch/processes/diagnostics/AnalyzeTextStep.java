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


import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QUserFacingException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.diagnostics.AnalyzerTester;
import com.kingsrook.qbits.quicksearch.diagnostics.DiagnosticsHtml;
import com.kingsrook.qbits.quicksearch.diagnostics.DiagnosticsSupport;
import com.kingsrook.qbits.quicksearch.model.QuickSearchDiagnosticEvent;


/*******************************************************************************
 ** Steps of the Test Search Analyzer process (a table action on Quick Search
 ** Index): run the index's analyzers on text, and check a query against it.
 ** Loops back to the form.
 *******************************************************************************/
public class AnalyzeTextStep extends AbstractDiagnosticToolStep
{
   public static final String FIELD_TEXT  = "analyzeText";
   public static final String FIELD_QUERY = "analyzeQuery";



   /*******************************************************************************
    *******************************************************************************/
   @Override
   public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
   {
      QuickSearchRuntime runtime = DiagnosticsSupport.requireRuntime();
      if(STEP_PREPARE.equals(input.getStepName()))
      {
         setHtml(output, STEP_FORM, new DiagnosticsHtml().open()
            .paragraph("Enter text as it would be indexed (a field value) and, optionally, a search term. You get the tokens each of the index's analyzers produces "
               + "and, with a term, whether every term token is among the text's indexed tokens: that is how a document's searchable text matches. Nothing is changed.")
            .close().toString());
         return;
      }

      loopBackToForm(output);
      long start = System.currentTimeMillis();
      try
      {
         DiagnosticsSupport.checkRateLimit(runtime, DiagnosticsSupport.TOOL_ANALYZE_TEXT);
      }
      catch(QUserFacingException e)
      {
         setHtml(output, STEP_FORM, failureHtml("Not run", e));
         return;
      }

      String query = input.getValueString(FIELD_QUERY);
      try
      {
         setHtml(output, STEP_FORM, AnalyzerTester.render(new AnalyzerTester().analyze(runtime, input.getValueString(FIELD_TEXT), query)));
         DiagnosticsSupport.recordToolRun(runtime, DiagnosticsSupport.TOOL_ANALYZE_TEXT, QuickSearchDiagnosticEvent.TYPE_TOOL_RUN, null, query, start, null, null);
      }
      catch(Exception e)
      {
         setHtml(output, STEP_FORM, failureHtml("Analyzing failed (is OpenSearch reachable and the index created?)", e));
         DiagnosticsSupport.recordToolRun(runtime, DiagnosticsSupport.TOOL_ANALYZE_TEXT, QuickSearchDiagnosticEvent.TYPE_TOOL_RUN, null, query, start, e, null);
      }
   }

}

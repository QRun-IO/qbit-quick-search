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


import java.util.List;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QUserFacingException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.diagnostics.DiagnosticsHtml;
import com.kingsrook.qbits.quicksearch.diagnostics.DiagnosticsSupport;
import com.kingsrook.qbits.quicksearch.diagnostics.IndexValidator;
import com.kingsrook.qbits.quicksearch.model.QuickSearchDiagnosticEvent;
import com.kingsrook.qbits.quicksearch.model.QuickSearchValidationRun;


/*******************************************************************************
 ** Steps of Validate Search Index (a table action on Quick Search Index, and
 ** schedulable with validationCronExpression). PREPARE picks the tables (the
 ** selected Quick Search Index rows, else all); RUN validates them
 ** (IndexValidator), saves the report as a Quick Search Validation Run row
 ** and shows it. One validation runs at a time per server. A scheduled run
 ** (frontend steps skipped) validates every table with the configured sample
 ** size and is not rate-limited.
 *******************************************************************************/
public class ValidateIndexStep extends AbstractDiagnosticToolStep
{
   public static final String FIELD_SAMPLE_SIZE = "sampleSize";
   public static final String FIELD_TABLES      = "validateTableNames";
   public static final String FIELD_RUN_ID      = "validationRunId";



   /*******************************************************************************
    *******************************************************************************/
   @Override
   public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
   {
      QuickSearchRuntime    runtime = DiagnosticsSupport.requireRuntime();
      QuickSearchQBitConfig config  = runtime.getConfig();
      if(STEP_PREPARE.equals(input.getStepName()))
      {
         List<String> tables = SearchPlaygroundStep.selectedIndexTables(input, runtime);
         output.addValue(FIELD_TABLES, String.join(",", tables));
         if(input.getValue(FIELD_SAMPLE_SIZE) == null)
         {
            output.addValue(FIELD_SAMPLE_SIZE, config.getValidationSampleSize());
         }
         setHtml(output, STEP_FORM, new DiagnosticsHtml().open()
            .paragraph("Validate " + (tables.isEmpty() ? "every indexed table" : String.join(", ", tables)) + ": cluster and index health, the mapping and analyzers, "
               + "database rows vs indexed documents, and a sample of records and documents checked for missing, stale and orphaned documents. "
               + "Read-only; the report is saved as a Quick Search Validation Run.")
            .close().toString());
         return;
      }

      boolean scheduled = input.getFrontendStepBehavior() == RunProcessInput.FrontendStepBehavior.SKIP;
      long    start     = System.currentTimeMillis();
      if(!scheduled)
      {
         DiagnosticsSupport.checkRateLimit(runtime, DiagnosticsSupport.TOOL_VALIDATE_INDEX);
      }
      if(!runtime.beginValidation())
      {
         throw (new QUserFacingException("A validation is already running on this server. Try again when it finishes."));
      }

      List<String> tables = SearchPlaygroundStep.parseTables(input.getValueString(FIELD_TABLES));
      Integer      sample = input.getValueInteger(FIELD_SAMPLE_SIZE);
      try
      {
         IndexValidator         validator = new IndexValidator();
         IndexValidator.Report  report    = validator.validate(runtime, tables, sample == null ? config.getValidationSampleSize() : sample);
         Integer                runId     = validator.persist(runtime, report, scheduled ? QuickSearchValidationRun.TRIGGER_SCHEDULED : QuickSearchValidationRun.TRIGGER_MANUAL,
            DiagnosticsSupport.currentUserName());

         String link = runId == null
            ? "<p><em>The report was not saved (the validation run table is not configured or the insert failed; see the log).</em></p>"
            : "<p>Saved as <a href=\"/app/" + DiagnosticsHtml.escape(config.applyPrefix(QuickSearchValidationRun.TABLE_NAME)) + "/" + runId + "\">validation run #" + runId + "</a>.</p>";
         setHtml(output, STEP_RESULT, IndexValidator.render(report) + link);
         output.addValue(FIELD_RUN_ID, runId);
         output.addValue("status", report.status());
         DiagnosticsSupport.recordToolRun(runtime, DiagnosticsSupport.TOOL_VALIDATE_INDEX, QuickSearchDiagnosticEvent.TYPE_TOOL_RUN, report.tableNames(), null, start, null,
            IndexValidator.summary(report) + (runId == null ? "" : " run #" + runId));
      }
      catch(QException e)
      {
         DiagnosticsSupport.recordToolRun(runtime, DiagnosticsSupport.TOOL_VALIDATE_INDEX, QuickSearchDiagnosticEvent.TYPE_TOOL_RUN, tables, null, start, e, null);
         throw (e);
      }
      finally
      {
         runtime.endValidation();
      }
   }

}

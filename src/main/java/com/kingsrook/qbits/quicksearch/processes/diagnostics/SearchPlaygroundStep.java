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
import java.util.Arrays;
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QUserFacingException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.diagnostics.DiagnosticsHtml;
import com.kingsrook.qbits.quicksearch.diagnostics.DiagnosticsSupport;
import com.kingsrook.qbits.quicksearch.diagnostics.SearchPathDiagnosis;
import com.kingsrook.qbits.quicksearch.diagnostics.SearchPlayground;
import com.kingsrook.qbits.quicksearch.model.QuickSearchDiagnosticEvent;


/*******************************************************************************
 ** Steps of the Search Playground process (a table action on Quick Search
 ** Index). PREPARE shows which tables are indexed and the provider's state,
 ** and pre-fills the tables from the selected Quick Search Index rows; RUN
 ** runs SearchPlayground as the current user and loops back to the form.
 *******************************************************************************/
public class SearchPlaygroundStep extends AbstractDiagnosticToolStep
{
   public static final String FIELD_TERM            = "searchTerm";
   public static final String FIELD_TABLES          = "tableNames";
   public static final String FIELD_LIMIT_PER_TABLE = "limitPerTable";
   public static final String FIELD_EXPLAIN_HIT     = "explainHit";



   /*******************************************************************************
    *******************************************************************************/
   @Override
   public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
   {
      QuickSearchRuntime runtime = DiagnosticsSupport.requireRuntime();
      if(STEP_PREPARE.equals(input.getStepName()))
      {
         prepare(input, output, runtime);
         return;
      }

      loopBackToForm(output);
      long start = System.currentTimeMillis();
      try
      {
         DiagnosticsSupport.checkRateLimit(runtime, DiagnosticsSupport.TOOL_PLAYGROUND);
      }
      catch(QUserFacingException e)
      {
         setHtml(output, STEP_FORM, failureHtml("Not run", e));
         return;
      }

      String       term   = input.getValueString(FIELD_TERM);
      List<String> tables = parseTables(input.getValueString(FIELD_TABLES));
      try
      {
         SearchPlayground.Report report = new SearchPlayground().run(runtime,
            new SearchPlayground.Input(term, tables, input.getValueInteger(FIELD_LIMIT_PER_TABLE), input.getValueString(FIELD_EXPLAIN_HIT)), input);
         setHtml(output, STEP_FORM, SearchPlayground.render(report));
         DiagnosticsSupport.recordToolRun(runtime, DiagnosticsSupport.TOOL_PLAYGROUND, QuickSearchDiagnosticEvent.TYPE_TOOL_RUN, tables, term, start, null,
            report.coreError() != null ? "global search failed: " + report.coreError() : (report.providerError() != null ? "provider failed: " + report.providerError() : null));
      }
      catch(Exception e)
      {
         setHtml(output, STEP_FORM, failureHtml("The playground failed", e));
         DiagnosticsSupport.recordToolRun(runtime, DiagnosticsSupport.TOOL_PLAYGROUND, QuickSearchDiagnosticEvent.TYPE_TOOL_RUN, tables, term, start, e, null);
      }
   }



   /*******************************************************************************
    ** Intro HTML and table pre-fill.
    *******************************************************************************/
   private void prepare(RunBackendStepInput input, RunBackendStepOutput output, QuickSearchRuntime runtime) throws QException
   {
      List<String> selected = selectedIndexTables(input, runtime);
      if(!selected.isEmpty())
      {
         output.addValue(FIELD_TABLES, String.join(", ", selected));
      }

      List<String> indexed = new ArrayList<>();
      runtime.getDiscoveredTables().forEach(t -> indexed.add(t.getTableName()));

      SearchPathDiagnosis.ProviderState state = SearchPathDiagnosis.providerState(QContext.getQInstance(), runtime);
      DiagnosticsHtml html = new DiagnosticsHtml().open()
         .paragraph("Type a term and run it. The playground runs it the way the global search does, as you, and shows the path each table takes, what you would see, "
            + "and the OpenSearch query, scores, matched fields and raw response behind it. Leave the tables blank to search every searchable table. "
            + "To explain a hit, copy its document id (table:id) into Explain hit. Nothing is changed.")
         .facts(List.of(
            new String[] { "Indexed tables", indexed.isEmpty() ? null : String.join(", ", indexed) },
            new String[] { "Provider", Boolean.TRUE.equals(state.quickSearchProvider()) ? "Quick Search serves the global search" : "Quick Search does not serve the global search" },
            new String[] { "Backing off", state.backoffRemainingMillis() > 0 ? (state.backoffRemainingMillis() / 1000) + " s left after a failure" : "no" }))
         .close();
      setHtml(output, STEP_FORM, html.toString());
   }



   /*******************************************************************************
    ** Table names of the Quick Search Index rows the process was started on
    ** (the recordIds value), if any.
    *******************************************************************************/
   static List<String> selectedIndexTables(RunBackendStepInput input, QuickSearchRuntime runtime) throws QException
   {
      String recordIds = input.getValueString("recordIds");
      String table     = runtime.getConfig().getQuickSearchIndexTableName();
      if(!StringUtils.hasContent(recordIds) || QContext.getQInstance().getTable(table) == null)
      {
         return (List.of());
      }

      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(table);
      queryInput.setFilter(new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.IN, Arrays.stream(recordIds.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList())));
      List<String> tables = new ArrayList<>();
      for(QRecord row : CollectionUtils.nonNullList(new QueryAction().execute(queryInput).getRecords()))
      {
         tables.add(row.getValueString("tableName"));
      }
      return (tables);
   }



   /*******************************************************************************
    ** Table names from a comma- or space-separated list.
    *******************************************************************************/
   public static List<String> parseTables(String value)
   {
      if(!StringUtils.hasContent(value))
      {
         return (List.of());
      }
      return (Arrays.stream(value.split("[,\\s]+")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList());
   }

}

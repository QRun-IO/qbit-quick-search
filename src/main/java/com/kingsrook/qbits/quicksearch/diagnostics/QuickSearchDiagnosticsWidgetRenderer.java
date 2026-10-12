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

package com.kingsrook.qbits.quicksearch.diagnostics;


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.dashboard.widgets.AbstractWidgetRenderer;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetInput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetOutput;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.RawHTML;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.diagnostics.DiagnosticsHtml.Level;
import com.kingsrook.qbits.quicksearch.model.QuickSearchDiagnosticEvent;
import com.kingsrook.qbits.quicksearch.model.QuickSearchValidationRun;


/*******************************************************************************
 ** The Search Diagnostics widget: the record-search provider's state on the
 ** server that renders it, the most recent search failures (from every
 ** server, via Quick Search Diagnostic Event), the latest validation run,
 ** and where the tools are. Every part says plainly when it has nothing to
 ** show or cannot be read.
 *******************************************************************************/
public class QuickSearchDiagnosticsWidgetRenderer extends AbstractWidgetRenderer
{
   private static final QLogger LOG = QLogger.getLogger(QuickSearchDiagnosticsWidgetRenderer.class);

   public static final int RECENT_FAILURES = 10;



   /*******************************************************************************
    *******************************************************************************/
   @Override
   public RenderWidgetOutput render(RenderWidgetInput input) throws QException
   {
      QuickSearchRuntime runtime = QuickSearchRuntime.get();
      if(runtime == null)
      {
         return (new RenderWidgetOutput(new RawHTML("Search Diagnostics", "<p>Quick Search is not configured in this application.</p>")));
      }
      return (new RenderWidgetOutput(new RawHTML("Search Diagnostics", renderHtml(runtime))));
   }



   /*******************************************************************************
    ** The widget's HTML.
    *******************************************************************************/
   static String renderHtml(QuickSearchRuntime runtime)
   {
      QuickSearchQBitConfig config = runtime.getConfig();
      DiagnosticsHtml       html   = new DiagnosticsHtml().open();

      SearchPathDiagnosis.ProviderState state = SearchPathDiagnosis.providerState(QContext.getQInstance(), runtime);
      html.subheading("Global search on this server");
      if(!Boolean.TRUE.equals(state.quickSearchProvider()) || !Boolean.TRUE.equals(state.serveCoreRecordSearch()))
      {
         html.status(Level.INFO, "Quick Search does not serve the global search here (" + (state.registeredProvider() == null ? "no provider registered" : "provider " + state.registeredProvider())
            + "); core searches every table itself.");
      }
      else if(state.backoffRemainingMillis() > 0)
      {
         html.status(Level.FAIL, "Backing off for " + (state.backoffRemainingMillis() / 1000) + " s after an OpenSearch failure: the global search uses core's database search meanwhile. Last error: "
            + state.lastFailureMessage());
      }
      else
      {
         html.status(Level.OK, "Served by OpenSearch through Quick Search (timeout " + state.timeoutMillis() + " ms).");
      }
      html.facts(List.of(
         new String[] { "Failed OpenSearch searches since " + state.runtimeStartedAt(), String.valueOf(state.failureCount()) },
         new String[] { "Table searches core ran itself during backoffs", String.valueOf(state.backoffDeclinedClaims()) },
         new String[] { "Last failure", state.lastFailureTime() == null ? null : state.lastFailureTime() + ": " + state.lastFailureMessage() }));

      renderRecentFailures(html, config);
      renderLastValidation(html, config);

      html.subheading("Tools");
      html.paragraph("On Quick Search Index (Actions): Search Playground, Validate Search Index, Test Search Analyzer. "
         + "On any indexed record and on Quick Search Failed Event rows: Inspect Search Document and Reindex in Search. "
         + "Every run is recorded in Quick Search Diagnostic Event.");
      return (html.close().toString());
   }



   /*******************************************************************************
    *******************************************************************************/
   private static void renderRecentFailures(DiagnosticsHtml html, QuickSearchQBitConfig config)
   {
      String tableName = config.applyPrefix(QuickSearchDiagnosticEvent.TABLE_NAME);
      html.subheading("Recent search failures (all servers)");
      if(QContext.getQInstance().getTable(tableName) == null)
      {
         html.paragraph("Not recorded: the diagnostic event table is not configured.");
         return;
      }

      try
      {
         QueryInput queryInput = new QueryInput();
         queryInput.setTableName(tableName);
         queryInput.setFilter(new QQueryFilter(new QFilterCriteria("eventType", QCriteriaOperator.EQUALS, QuickSearchDiagnosticEvent.TYPE_SEARCH_FAILED))
            .withOrderBy(new QFilterOrderBy("id", false))
            .withLimit(RECENT_FAILURES));
         List<QRecord> rows = CollectionUtils.nonNullList(new QueryAction().execute(queryInput).getRecords());
         if(rows.isEmpty())
         {
            html.status(Level.OK, "None recorded (failures are kept for " + config.getRunHistoryRetentionDays() + " days).");
            return;
         }

         List<String> items = new ArrayList<>();
         for(QRecord row : rows)
         {
            items.add("<a href=\"/app/" + DiagnosticsHtml.escape(tableName) + "/" + DiagnosticsHtml.escape(row.getValueString("id")) + "\">"
               + DiagnosticsHtml.escape(row.getValueString("createDate")) + "</a> on " + DiagnosticsHtml.escape(row.getValueString("hostName"))
               + ": " + DiagnosticsHtml.escape(row.getValueString("source")) + ", " + DiagnosticsHtml.escape(row.getValueString("durationMillis")) + " ms, "
               + DiagnosticsHtml.escape(shortClass(row.getValueString("errorClass"))) + ": " + DiagnosticsHtml.escape(abbreviate(row.getValueString("errorMessage"), 240)));
         }
         html.rawList(items);
      }
      catch(Exception e)
      {
         LOG.info("Could not read recent search failures for the diagnostics widget", e);
         html.status(Level.WARN, "Could not read the diagnostic events: " + e.getMessage());
      }
   }



   /*******************************************************************************
    *******************************************************************************/
   private static void renderLastValidation(DiagnosticsHtml html, QuickSearchQBitConfig config)
   {
      String tableName = config.applyPrefix(QuickSearchValidationRun.TABLE_NAME);
      html.subheading("Latest index validation");
      if(QContext.getQInstance().getTable(tableName) == null)
      {
         html.paragraph("Not kept: the validation run table is not configured.");
         return;
      }

      try
      {
         QueryInput queryInput = new QueryInput();
         queryInput.setTableName(tableName);
         queryInput.setFilter(new QQueryFilter().withOrderBy(new QFilterOrderBy("id", false)).withLimit(1));
         List<QRecord> rows = CollectionUtils.nonNullList(new QueryAction().execute(queryInput).getRecords());
         if(rows.isEmpty())
         {
            html.paragraph("None yet. Run Validate Search Index from Quick Search Index (Actions)"
               + (config.getValidationCronExpression() == null ? "." : "; it is also scheduled (" + config.getValidationCronExpression() + ")."));
            return;
         }

         QRecord row    = rows.get(0);
         String  status = row.getValueString("status");
         Level   level  = QuickSearchValidationRun.STATUS_PASS.equals(status) ? Level.OK : (QuickSearchValidationRun.STATUS_WARN.equals(status) ? Level.WARN : Level.FAIL);
         html.raw("<p>" + DiagnosticsHtml.badge(level) + " <a href=\"/app/" + DiagnosticsHtml.escape(tableName) + "/" + DiagnosticsHtml.escape(row.getValueString("id")) + "\">"
            + DiagnosticsHtml.escape(row.getValueString("startTime")) + "</a> (" + DiagnosticsHtml.escape(row.getValueString("triggerType")) + "): "
            + DiagnosticsHtml.escape(row.getValueString("summary")) + "</p>");
      }
      catch(Exception e)
      {
         LOG.info("Could not read the latest validation run for the diagnostics widget", e);
         html.status(Level.WARN, "Could not read the validation runs: " + e.getMessage());
      }
   }



   /*******************************************************************************
    *******************************************************************************/
   private static String shortClass(String className)
   {
      return (className == null ? "error" : className.substring(className.lastIndexOf('.') + 1));
   }



   /*******************************************************************************
    *******************************************************************************/
   private static String abbreviate(String text, int max)
   {
      return (text == null ? "" : (text.length() > max ? text.substring(0, max) + "..." : text));
   }

}

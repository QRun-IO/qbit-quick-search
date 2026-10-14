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


import java.util.Collection;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.model.QuickSearchDiagnosticEvent;
import com.kingsrook.qbits.quicksearch.model.QuickSearchValidationRun;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QUserFacingException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Shared pieces of the diagnostic tools: tool names, the current user, the
 ** per-user rate limit, and recording tool runs and search failures as
 ** quick_search_diagnostic_event rows (the audit trail).
 *******************************************************************************/
public final class DiagnosticsSupport
{
   private static final QLogger LOG = QLogger.getLogger(DiagnosticsSupport.class);

   public static final String TOOL_PLAYGROUND      = "SEARCH_PLAYGROUND";
   public static final String TOOL_INSPECT_RECORD  = "INSPECT_RECORD";
   public static final String TOOL_REINDEX_RECORDS = "REINDEX_RECORDS";
   public static final String TOOL_VALIDATE_INDEX  = "VALIDATE_INDEX";
   public static final String TOOL_ANALYZE_TEXT    = "ANALYZE_TEXT";



   /*******************************************************************************
    ** Latest saved validation, shared by the home widget and host status API.
    ** This does not run validation or scan source tables.
    *******************************************************************************/
   public static QRecord latestValidation(QuickSearchQBitConfig config) throws QException
   {
      String tableName = config.applyTablePrefix(QuickSearchValidationRun.TABLE_NAME);
      if(QContext.getQInstance().getTable(tableName) == null)
      {
         return (null);
      }
      var input = new QueryInput(tableName)
         .withFilter(new QQueryFilter()
            .withOrderBy(new QFilterOrderBy("id", false)).withLimit(1));
      var rows = new QueryAction().execute(input).getRecords();
      return (rows == null || rows.isEmpty() ? null : rows.get(0));
   }



   /*******************************************************************************
    ** Utility class - no instances.
    *******************************************************************************/
   private DiagnosticsSupport()
   {
   }



   /*******************************************************************************
    ** The session's user id reference (else full name), or null.
    *******************************************************************************/
   public static String currentUserName()
   {
      QSession session = QContext.getQSession();
      if(session == null || session.getUser() == null)
      {
         return (null);
      }
      return (session.getUser().getIdReference() != null ? session.getUser().getIdReference() : session.getUser().getFullName());
   }



   /*******************************************************************************
    ** The runtime of the Quick Search QBit, or a user-facing error saying the
    ** tool is unavailable.
    *******************************************************************************/
   public static QuickSearchRuntime requireRuntime() throws QUserFacingException
   {
      QuickSearchRuntime runtime = QuickSearchRuntime.get();
      if(runtime == null)
      {
         throw (new QUserFacingException("Quick Search is not configured in this application, so its diagnostic tools are unavailable."));
      }
      return (runtime);
   }



   /*******************************************************************************
    ** Admit one run of a tool for the current user, or refuse it (recording
    ** the refusal) when the user ran diagnosticRunsPerMinute tools in the last
    ** minute.
    *******************************************************************************/
   public static void checkRateLimit(QuickSearchRuntime runtime, String tool) throws QUserFacingException
   {
      String user = currentUserName();
      if(runtime.getDiagnosticsRateLimiter().tryAcquire((user == null ? "anonymous" : user) + "|" + tool, runtime.getConfig().getDiagnosticRunsPerMinute()))
      {
         return;
      }

      runtime.getDiagnosticEventRecorder().record(new QuickSearchDiagnosticEvent()
         .withEventType(QuickSearchDiagnosticEvent.TYPE_RATE_LIMITED)
         .withSource(tool)
         .withOutcome(QuickSearchDiagnosticEvent.OUTCOME_REFUSED));
      throw (new QUserFacingException("Too many Quick Search diagnostic runs in the last minute (limit " + runtime.getConfig().getDiagnosticRunsPerMinute()
         + " per tool). Wait a minute and try again."));
   }



   /*******************************************************************************
    ** Record a tool run (audit): who ran which tool on which tables with which
    ** term, how long it took, and its outcome.
    *******************************************************************************/
   public static void recordToolRun(QuickSearchRuntime runtime, String tool, String eventType, Collection<String> tableNames, String term, long startMillis, Throwable error, String detail)
   {
      try
      {
         runtime.getDiagnosticEventRecorder().record(new QuickSearchDiagnosticEvent()
            .withEventType(eventType)
            .withSource(tool)
            .withOutcome(error == null ? QuickSearchDiagnosticEvent.OUTCOME_OK : QuickSearchDiagnosticEvent.OUTCOME_ERROR)
            .withTableNames(tableNames == null || tableNames.isEmpty() ? null : String.join(",", tableNames))
            .withSearchTerm(term)
            .withDurationMillis((int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - startMillis))
            .withErrorClass(error == null ? null : error.getClass().getName())
            .withErrorMessage(error == null ? null : error.getMessage())
            .withDetail(detail));
      }
      catch(Exception e)
      {
         LOG.debug("Could not queue a diagnostic event", e);
      }

      LOG.info("Quick Search diagnostic tool run", logPair("tool", tool), logPair("user", currentUserName()), logPair("tables", tableNames),
         logPair("millis", System.currentTimeMillis() - startMillis), logPair("error", error == null ? null : error.getMessage()));
   }



   /*******************************************************************************
    ** Record a failed search (OpenSearch error or timeout). userTerm is the
    ** user's term: never stored, and redacted from the message and detail.
    *******************************************************************************/
   public static void recordSearchFailure(QuickSearchRuntime runtime, String source, Collection<String> tableNames, String userTerm, long durationMillis, Throwable error, String detail)
   {
      if(runtime == null)
      {
         return;
      }
      try
      {
         Throwable cause = rootCause(error);
         runtime.getDiagnosticEventRecorder().record(new QuickSearchDiagnosticEvent()
            .withEventType(QuickSearchDiagnosticEvent.TYPE_SEARCH_FAILED)
            .withSource(source)
            .withOutcome(QuickSearchDiagnosticEvent.OUTCOME_ERROR)
            .withTableNames(tableNames == null || tableNames.isEmpty() ? null : String.join(",", tableNames))
            .withDurationMillis((int) Math.min(Integer.MAX_VALUE, durationMillis))
            .withErrorClass(cause == null ? null : cause.getClass().getName())
            .withErrorMessage(redactTerm(error == null ? null : error.getMessage(), userTerm))
            .withDetail(redactTerm(detail, userTerm)));
      }
      catch(Exception e)
      {
         LOG.debug("Could not queue a search failure event", e);
      }
   }



   /*******************************************************************************
    ** The text with the user's search term, and each of its words of three or
    ** more characters, replaced by a marker (case-insensitive): error messages
    ** (ours and OpenSearch's) can echo the term, which may hold personal data.
    *******************************************************************************/
   public static String redactTerm(String text, String userTerm)
   {
      if(text == null || userTerm == null || userTerm.isBlank())
      {
         return (text);
      }
      String redacted = text.replaceAll("(?i)" + java.util.regex.Pattern.quote(userTerm.trim()), "[search term]");
      for(String word : userTerm.trim().split("\\s+"))
      {
         if(word.length() >= 3)
         {
            redacted = redacted.replaceAll("(?i)" + java.util.regex.Pattern.quote(word), "[search term]");
         }
      }
      return (redacted);
   }



   /*******************************************************************************
    ** The innermost cause of a throwable (the one that names the real error
    ** class: timeout, connection refused, OpenSearch error).
    *******************************************************************************/
   public static Throwable rootCause(Throwable error)
   {
      Throwable cause = error;
      int       depth = 0;
      while(cause != null && cause.getCause() != null && cause.getCause() != cause && depth++ < 20)
      {
         cause = cause.getCause();
      }
      return (cause);
   }



   /*******************************************************************************
    ** A user-facing error for a tool whose OpenSearch call failed: the tool
    ** reports the failure honestly instead of showing partial data.
    *******************************************************************************/
   public static QUserFacingException unavailable(String what, QException e)
   {
      return (new QUserFacingException(what + " is unavailable: " + e.getMessage(), e));
   }

}

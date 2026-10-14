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
import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QUserFacingException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.diagnostics.DiagnosticsHtml;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;


/*******************************************************************************
 ** Base of the diagnostic tool steps. Each tool's process has the steps
 ** PREPARE (backend), FORM or CONFIRM (frontend: fields and an HTML block),
 ** RUN (backend) and, for one-shot tools, RESULT (frontend: HTML). One step
 ** class serves a tool's backend steps, switching on the step name.
 ** Looping tools (playground, analyzer, inspect) end RUN by pointing the
 ** process back at PREPARE, so the next step is FORM again with the results
 ** below the fields: change the input, run again, without restarting.
 ** HTML goes in the process value "{step}.html", which the HTML component of
 ** that frontend step renders.
 *******************************************************************************/
public abstract class AbstractDiagnosticToolStep implements BackendStep
{
   public static final String STEP_PREPARE = "prepare";
   public static final String STEP_FORM    = "form";
   public static final String STEP_CONFIRM = "confirm";
   public static final String STEP_RUN     = "run";
   public static final String STEP_RESULT  = "result";



   /*******************************************************************************
    ** One record a tool works on.
    *******************************************************************************/
   public record Target(String tableName, String recordId)
   {
      /*******************************************************************************
       ** "table:recordId"
       *******************************************************************************/
      @Override
      public String toString()
      {
         return (tableName + ":" + recordId);
      }



      /*******************************************************************************
       ** Parse "table:recordId" (null when malformed).
       *******************************************************************************/
      public static Target parse(String value)
      {
         int colon = value == null ? -1 : value.indexOf(':');
         return (colon <= 0 || colon == value.length() - 1 ? null : new Target(value.substring(0, colon), value.substring(colon + 1)));
      }
   }



   /*******************************************************************************
    ** Set the HTML shown by a frontend step.
    *******************************************************************************/
   protected static void setHtml(RunBackendStepOutput output, String frontendStepName, String html)
   {
      output.addValue(frontendStepName + ".html", html);
   }



   /*******************************************************************************
    ** After RUN, show FORM again (the step after PREPARE).
    *******************************************************************************/
   protected static void loopBackToForm(RunBackendStepOutput output)
   {
      output.setOverrideLastStepName(STEP_PREPARE);
   }



   /*******************************************************************************
    ** A failure shown in the step's HTML instead of ending the process, so the
    ** user can change the input and run again.
    *******************************************************************************/
   protected static String failureHtml(String what, Exception e)
   {
      return (new DiagnosticsHtml().open().status(DiagnosticsHtml.Level.FAIL, what + ": " + e.getMessage()).close().toString());
   }



   /*******************************************************************************
    ** The records the process was started on, as targets: for a source table,
    ** its primary keys; for the failed event table, each row's table and
    ** record id (only tables Quick Search indexes). Read from the records the
    ** backend loaded into the process state (never from client values), so
    ** every step works on exactly the records the user selected and may read.
    ** At most max; more is a user-facing error.
    *******************************************************************************/
   protected static List<Target> targetsFromInput(RunBackendStepInput input, QuickSearchRuntime runtime, int max) throws QException
   {
      QProcessMetaData process   = QContext.getQInstance().getProcess(input.getProcessName());
      String           tableName = process == null ? null : process.getTableName();
      QTableMetaData   table     = tableName == null ? null : QContext.getQInstance().getTable(tableName);
      if(table == null)
      {
         throw (new QUserFacingException("This tool runs on records of a table."));
      }

      List<QRecord> records = CollectionUtils.nonNullList(input.getRecords());
      if(records.isEmpty())
      {
         throw (new QUserFacingException("Select at least one record."));
      }
      if(records.size() > max)
      {
         throw (new QUserFacingException("Select at most " + max + " records (" + records.size() + "+ selected)."));
      }

      boolean      failedEvents = tableName.equals(runtime.getConfig().applyTablePrefix(QuickSearchFailedEvent.TABLE_NAME));
      Set<Target>  targets      = new LinkedHashSet<>();
      for(QRecord record : records)
      {
         String targetTable = failedEvents ? record.getValueString("tableName") : tableName;
         String recordId    = failedEvents ? record.getValueString("recordId") : record.getValueString(table.getPrimaryKeyField());
         if(StringUtils.hasContent(targetTable) && StringUtils.hasContent(recordId) && runtime.getTableConfig(targetTable) != null)
         {
            targets.add(new Target(targetTable, recordId));
         }
      }
      return (new ArrayList<>(targets));
   }

}

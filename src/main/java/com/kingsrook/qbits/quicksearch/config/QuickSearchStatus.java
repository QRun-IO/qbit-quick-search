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

package com.kingsrook.qbits.quicksearch.config;


import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.diagnostics.DiagnosticsHtml;
import com.kingsrook.qbits.quicksearch.model.QuickSearchSettings;
import com.kingsrook.qbits.quicksearch.processes.AbstractIndexingStep;


/*******************************************************************************
 ** The indexing status for the Quick Search home: the global pause ("Paused
 ** since ... by ..."), the last self-priming report ("New: 2 tables added and
 ** building..."), the tables queued or building, and those whose field
 ** changes wait for Apply Search Index Changes. The QBit's diagnostics widget
 ** shows it; hosts with their own home can use read() and bannerHtml().
 **
 ** @param paused             whether all indexing is paused
 ** @param pausedAt           since when (null when not paused)
 ** @param pausedBy           by whom
 ** @param pauseReason        the reason given, if any
 ** @param lastPrimedAt       when self-priming last ran
 ** @param lastPrimingSummary what it last changed
 ** @param building           labels of tables queued for or in a build
 ** @param needsReindex       labels of tables with field changes to apply
 ** @param recordsMode        whether the config comes from the records (else
 **                           code: the config tables are missing or not primed)
 *******************************************************************************/
public record QuickSearchStatus(boolean paused, Instant pausedAt, String pausedBy, String pauseReason, Instant lastPrimedAt, String lastPrimingSummary,
                                List<String> building, List<String> needsReindex, boolean recordsMode)
{
   private static final QLogger LOG = QLogger.getLogger(QuickSearchStatus.class);



   /*******************************************************************************
    ** Read the status. Never throws; parts that cannot be read are empty.
    *******************************************************************************/
   public static QuickSearchStatus read(QuickSearchRuntime runtime)
   {
      List<String> building     = new ArrayList<>();
      List<String> needsReindex = new ArrayList<>();
      if(runtime == null)
      {
         return (new QuickSearchStatus(false, null, null, null, null, null, building, needsReindex, false));
      }

      try
      {
         String indexTable = runtime.getConfig().getQuickSearchIndexTableName();
         if(QContext.getQInstance() != null && QContext.getQInstance().getTable(indexTable) != null)
         {
            for(QRecord row : QuickSearchConfigPrimer.queryAll(indexTable))
            {
               String status = row.getValueString("status");
               String label  = labelOf(row.getValueString("tableName"));
               if(Boolean.FALSE.equals(row.getValueBoolean("enabled")))
               {
                  continue;
               }
               if(AbstractIndexingStep.STATUS_PENDING_BUILD.equals(status) || AbstractIndexingStep.STATUS_BUILDING.equals(status)
                  || AbstractIndexingStep.STATUS_REBUILDING.equals(status))
               {
                  building.add(label);
               }
               else if(AbstractIndexingStep.STATUS_NEEDS_REINDEX.equals(status))
               {
                  needsReindex.add(label);
               }
            }
         }
      }
      catch(Exception e)
      {
         LOG.debug("Could not read the index rows for the Quick Search status", e);
      }

      QRecord settings = runtime.getConfigStore().getSettings();
      boolean paused   = settings != null && Boolean.TRUE.equals(settings.getValueBoolean("indexingPaused"));
      return (new QuickSearchStatus(paused,
         settings == null ? null : settings.getValueInstant("pausedAt"),
         settings == null ? null : settings.getValueString("pausedBy"),
         settings == null ? null : settings.getValueString("pauseReason"),
         settings == null ? null : settings.getValueInstant("lastPrimedAt"),
         settings == null ? null : settings.getValueString("lastPrimingSummary"),
         building, needsReindex, runtime.getConfigStore().isRecordsMode()));
   }



   /*******************************************************************************
    ** "Paused since 2026-10-12T14:03:00Z by Jane Admin (reason)".
    *******************************************************************************/
   public String pausedText()
   {
      return ("Paused since " + (pausedAt == null ? "an unknown time" : pausedAt) + " by " + (pausedBy == null ? "an unknown user" : pausedBy)
         + (StringUtils.hasContent(pauseReason) ? " (" + pauseReason + ")" : "")
         + ". Nothing is indexed; search keeps serving the current index, and changes are indexed when indexing resumes.");
   }



   /*******************************************************************************
    ** The building line: "Building: Blog Post, Page", or null.
    *******************************************************************************/
   public String buildingText()
   {
      if(building.isEmpty())
      {
         return (null);
      }
      return ((StringUtils.hasContent(lastPrimingSummary) && lastPrimingSummary.startsWith("New:") ? lastPrimingSummary + " " : "")
         + "Building now or with the next update: " + String.join(", ", building) + ".");
   }



   /*******************************************************************************
    ** The pending-changes line, or null.
    *******************************************************************************/
   public String needsReindexText()
   {
      return (needsReindex.isEmpty() ? null : "Field changes waiting to be applied (Apply Search Index Changes): " + String.join(", ", needsReindex) + ".");
   }



   /*******************************************************************************
    ** The status as standalone HTML, for a host's own home widget: one line
    ** per item, empty when there is nothing to report.
    *******************************************************************************/
   public String bannerHtml()
   {
      StringBuilder html = new StringBuilder();
      if(paused)
      {
         html.append(banner("#b26a00", "#fff4e5", pausedText()));
      }
      if(buildingText() != null)
      {
         html.append(banner("#0b5394", "#e8f0fe", buildingText()));
      }
      if(needsReindexText() != null)
      {
         html.append(banner("#5f6368", "#f1f3f4", needsReindexText()));
      }
      return (html.toString());
   }



   /*******************************************************************************
    ** One banner line.
    *******************************************************************************/
   private static String banner(String color, String background, String text)
   {
      return ("<div style=\"padding:8px 12px;margin:0 0 8px 0;border-radius:6px;border-left:4px solid " + color + ";background:" + background + ";color:#202124\">"
         + DiagnosticsHtml.escape(text) + "</div>");
   }



   /*******************************************************************************
    ** A table's label, or its name.
    *******************************************************************************/
   private static String labelOf(String tableName)
   {
      var table = tableName == null || QContext.getQInstance() == null ? null : QContext.getQInstance().getTable(tableName);
      return (table == null ? String.valueOf(tableName) : QuickSearchFieldRules.label(table));
   }



   /*******************************************************************************
    ** The settings table name for links ("/app/" + name + "/1").
    *******************************************************************************/
   public static String settingsPath(QuickSearchRuntime runtime)
   {
      return ("/app/" + runtime.getConfig().applyTablePrefix(QuickSearchSettings.TABLE_NAME) + "/" + QuickSearchSettings.SETTINGS_ID);
   }

}

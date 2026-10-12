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


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionCheckResult;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper;
import com.kingsrook.qqq.backend.core.actions.permissions.TablePermissionSubType;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanel;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelAction;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelBlock;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelContext;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelKeyValue;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelLink;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelProviderInterface;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelTone;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.metadata.QuickSearchAdminAppMetaDataProducer;
import com.kingsrook.qbits.quicksearch.metadata.QuickSearchDiagnosticsProcessMetaDataHelper;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchDiagnosticsOperations.DocumentView;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** The "Quick Search" panel of a record's developer view (QQQ's
 ** DeveloperPanelProviderInterface, /app/{table}/{id}/dev).
 **
 ** Shown for tables this QBit indexes, to users who may read the Quick Search
 ** Index table. It shows the table's index row (enabled, status, counts, last
 ** basepull and full reindex, searchable fields), the latest index run, the
 ** record's indexed document compared with the database (Inspect Search
 ** Document's comparison; needs the debug permission), the record's failed
 ** index events, and the Inspect / Reindex record actions and admin links the
 ** user may use. Every read checks the user's permissions; an unreachable
 ** OpenSearch shows a warning instead of failing the panel.
 *******************************************************************************/
public class QuickSearchDeveloperPanelProvider implements DeveloperPanelProviderInterface
{
   private static final QLogger LOG = QLogger.getLogger(QuickSearchDeveloperPanelProvider.class);

   public static final String ID    = "quickSearch";
   public static final String TITLE = "Quick Search";

   public static final String STATUS_INDEXED     = "Indexed";
   public static final String STATUS_STALE       = "Stale";
   public static final String STATUS_NOT_INDEXED = "Not indexed";
   public static final String STATUS_DISABLED    = "Disabled";
   public static final String STATUS_ENABLED     = "Enabled";
   public static final String STATUS_UNAVAILABLE = "OpenSearch unavailable";

   static final int MAX_FAILED_EVENTS = 20;
   static final int MAX_VALUE_LENGTH  = 300;



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getId()
   {
      return (ID);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getTitle()
   {
      return (TITLE);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public Integer getOrder()
   {
      return (400);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getIcon()
   {
      return ("search");
   }



   /*******************************************************************************
    ** Available for a table this QBit indexes, when the user may read the Quick
    ** Search Index table (the panel shows admin-level indexing state).
    *******************************************************************************/
   @Override
   public boolean isAvailable(DeveloperPanelContext context)
   {
      QuickSearchRuntime runtime = QuickSearchRuntime.get();
      if(runtime == null || context == null || runtime.getTableConfig(context.getTableName()) == null)
      {
         return (false);
      }
      return (mayRead(runtime.getConfig().getQuickSearchIndexTableName()));
   }



   /*******************************************************************************
    ** Build the panel.
    *******************************************************************************/
   @Override
   public DeveloperPanel build(DeveloperPanelContext context) throws QException
   {
      QuickSearchRuntime          runtime     = QuickSearchRuntime.getOrThrow();
      QuickSearchQBitConfig       config      = runtime.getConfig();
      String                      tableName   = context.getTableName();
      QuickSearchableTableConfig  tableConfig = runtime.getTableConfig(tableName);
      Serializable                primaryKey  = context.getPrimaryKey();
      String                      recordId    = primaryKey == null ? null : String.valueOf(primaryKey);
      String                      indexTable  = config.getQuickSearchIndexTableName();

      DeveloperPanel panel = new DeveloperPanel()
         .withDescription("How Quick Search (OpenSearch) indexes this table, and this record's search document.");

      //////////////////////////////////////////////////////////////
      // the table's index row (readable: checked by isAvailable) //
      //////////////////////////////////////////////////////////////
      QRecord indexRow = null;
      if(mayRead(indexTable))
      {
         indexRow = queryFirst(indexTable, new QQueryFilter(new QFilterCriteria("tableName", QCriteriaOperator.EQUALS, tableName)));
      }
      boolean enabled = indexRow == null || !Boolean.FALSE.equals(indexRow.getValueBoolean("enabled"));
      panel.withBlock(indexBlock(config, tableConfig, indexTable, indexRow, enabled));

      //////////////////////
      // latest index run //
      //////////////////////
      String runTable = config.getQuickSearchIndexRunTableName();
      if(indexRow != null && mayRead(runTable))
      {
         QRecord run = queryFirst(runTable, new QQueryFilter(new QFilterCriteria("quickSearchIndexId", QCriteriaOperator.EQUALS, indexRow.getValue("id")))
            .withOrderBy(new QFilterOrderBy("id", false)));
         panel.withBlock(run == null
            ? DeveloperPanelBlock.text("No index runs are recorded for this table yet.", DeveloperPanelTone.INFO).withLabel("Latest index run")
            : runBlock(runTable, run));
      }

      /////////////////////////////////////////
      // this record's document vs. database //
      /////////////////////////////////////////
      String verdict = null;
      boolean openSearchFailed = false;
      if(recordId != null)
      {
         if(!Boolean.TRUE.equals(config.getEnableDiagnosticTools()))
         {
            panel.withBlock(DeveloperPanelBlock.text("Quick Search diagnostic tools are disabled (enableDiagnosticTools), so this record's search document is not compared.", DeveloperPanelTone.INFO)
               .withLabel("Search document"));
         }
         else if(!mayInspect(config, tableName))
         {
            panel.withBlock(DeveloperPanelBlock.text("You do not have the Quick Search debug permission, so this record's search document is not shown.", DeveloperPanelTone.INFO)
               .withLabel("Search document"));
         }
         else
         {
            try
            {
               RecordInspector.Inspection inspection = inspect(runtime, tableName, recordId, new QueryInput(tableName));
               verdict = inspection.verdict();
               addInspectionBlocks(panel, inspection);
            }
            catch(Exception e)
            {
               openSearchFailed = true;
               LOG.info("Quick Search developer panel could not read the search document", logPair("tableName", tableName), logPair("recordId", recordId), logPair("error", e.getMessage()));
               panel.withBlock(DeveloperPanelBlock.text("OpenSearch could not be reached, so this record's search document cannot be shown: "
                  + DiagnosticsSupport.rootCause(e).getMessage(), DeveloperPanelTone.WARNING).withLabel("Search document"));
            }
         }
      }

      /////////////////////////////////
      // failed index events, if any //
      /////////////////////////////////
      String failedTable = config.applyTablePrefix(QuickSearchFailedEvent.TABLE_NAME);
      if(recordId != null && mayRead(failedTable))
      {
         DeveloperPanelBlock failedBlock = failedEventsBlock(failedTable, tableName, recordId);
         if(failedBlock != null)
         {
            panel.withBlock(failedBlock);
         }
      }

      //////////////////////////////
      // actions the user may run //
      //////////////////////////////
      if(recordId != null)
      {
         DeveloperPanelBlock actions = DeveloperPanelBlock.actions("Actions");
         addActionIfPermitted(actions, QuickSearchDiagnosticsProcessMetaDataHelper.inspectProcessName(config, tableName), tableName, "Inspect search document",
            "Compare the indexed document with the database, and see why the record does or does not match a term.");
         addActionIfPermitted(actions, QuickSearchDiagnosticsProcessMetaDataHelper.reindexRecordsProcessName(config, tableName), tableName, "Reindex this record",
            "Rebuild this record's search document from the database now.");
         if(!actions.getActions().isEmpty())
         {
            panel.withBlock(actions);
         }
      }

      ///////////
      // links //
      ///////////
      DeveloperPanelBlock links   = DeveloperPanelBlock.links("Links");
      String              appName = config.applyPrefix(QuickSearchAdminAppMetaDataProducer.APP_NAME);
      if(QContext.getQInstance().getApp(appName) != null && PermissionsHelper.hasAppPermission(new QueryInput(indexTable), appName))
      {
         links.withLink(new DeveloperPanelLink().withLabel("Quick Search Admin").withHref("/app/" + appName).withDescription("Index status, runs, failed events and diagnostic tools."));
      }
      if(indexRow != null)
      {
         links.withLink(new DeveloperPanelLink().withLabel("Quick Search Index row for " + tableName).withHref(recordHref(indexTable, indexRow.getValue("id"))));
      }
      if(!links.getLinks().isEmpty())
      {
         panel.withBlock(links);
      }

      setStatus(panel, enabled, verdict, openSearchFailed);
      return (panel);
   }



   /*******************************************************************************
    ** Inspect one record (overridable for tests).
    *******************************************************************************/
   protected RecordInspector.Inspection inspect(QuickSearchRuntime runtime, String tableName, String recordId, AbstractActionInput permissionInput) throws QException
   {
      return (new RecordInspector().inspect(runtime, tableName, recordId, null, permissionInput));
   }



   /*******************************************************************************
    ** The table's index row as key/values.
    *******************************************************************************/
   private DeveloperPanelBlock indexBlock(QuickSearchQBitConfig config, QuickSearchableTableConfig tableConfig, String indexTable, QRecord indexRow, boolean enabled)
   {
      DeveloperPanelBlock block = DeveloperPanelBlock.keyValues("Index");
      if(indexRow == null)
      {
         block.withItem("Index row", "none yet (the first indexing run creates it)");
      }
      else
      {
         block.withItem(new DeveloperPanelKeyValue().withLabel("Index row").withValue("#" + indexRow.getValueString("id")).withHref(recordHref(indexTable, indexRow.getValue("id"))));
      }
      block.withItem("Enabled", enabled ? "Yes" : "No");
      block.withItem("Status", indexRow == null ? null : indexRow.getValueString("status"));
      block.withItem("Indexed records", indexRow == null ? null : indexRow.getValueString("recordCount"));
      block.withItem("Last basepull", indexRow == null ? null : indexRow.getValueString("lastBasepullTime"));
      block.withItem("Last full reindex", indexRow == null ? null : indexRow.getValueString("lastFullReindexTime"));
      block.withItem("Searchable fields", String.join(", ", CollectionUtils.nonNullList(tableConfig.getSearchableFields())));
      block.withItem(new DeveloperPanelKeyValue().withLabel("OpenSearch index (alias)").withValue(config.getOpensearchIndexName()).withMonospace(true));
      block.withItem("Real-time indexing", Boolean.TRUE.equals(config.getEnableRealTimeIndexing()) ? "On" : "Off");
      return (block);
   }



   /*******************************************************************************
    ** The latest index run as key/values.
    *******************************************************************************/
   private DeveloperPanelBlock runBlock(String runTable, QRecord run)
   {
      DeveloperPanelBlock block = DeveloperPanelBlock.keyValues("Latest index run")
         .withItem(new DeveloperPanelKeyValue().withLabel("Run").withValue("#" + run.getValueString("id")).withHref(recordHref(runTable, run.getValue("id"))))
         .withItem("Type", run.getValueString("runType"))
         .withItem("Status", run.getValueString("status"))
         .withItem("Started", run.getValueString("startTime"))
         .withItem("Ended", run.getValueString("endTime"))
         .withItem("Processed", run.getValueString("recordsProcessed"))
         .withItem("Indexed", run.getValueString("recordsIndexed"))
         .withItem("Errors", run.getValueString("errorCount"));
      if(run.getValueString("errorMessage") != null)
      {
         block.withItem("Error", truncate(run.getValueString("errorMessage")));
      }
      return (block);
   }



   /*******************************************************************************
    ** The inspection: verdict text, document facts, field comparison table.
    *******************************************************************************/
   private void addInspectionBlocks(DeveloperPanel panel, RecordInspector.Inspection inspection)
   {
      if(inspection.error() != null)
      {
         panel.withBlock(DeveloperPanelBlock.text(inspection.error(), RecordInspector.VERDICT_NOT_INDEXED.equals(inspection.verdict()) ? DeveloperPanelTone.INFO : DeveloperPanelTone.WARNING)
            .withLabel("Search document"));
         return;
      }

      switch(inspection.verdict())
      {
         case RecordInspector.VERDICT_CURRENT -> panel.withBlock(DeveloperPanelBlock.text("Indexed and current: the search document matches the database.", DeveloperPanelTone.SUCCESS)
            .withLabel("Search document"));
         case RecordInspector.VERDICT_STALE -> panel.withBlock(DeveloperPanelBlock.text("Indexed but stale: some values differ from the database. Reindex this record to refresh it.", DeveloperPanelTone.WARNING)
            .withLabel("Search document"));
         default -> panel.withBlock(DeveloperPanelBlock.text("Not in the index: the global search cannot find this record. Reindex this record to add it (or wait for the next basepull or reconcile).",
            DeveloperPanelTone.ERROR).withLabel("Search document"));
      }

      DocumentView indexed = inspection.indexed();
      panel.withBlock(DeveloperPanelBlock.keyValues("Document")
         .withItem(new DeveloperPanelKeyValue().withLabel("Document id").withValue(OpenSearchDocument.buildDocumentId(inspection.tableName(), inspection.recordId())).withMonospace(true))
         .withItem(new DeveloperPanelKeyValue().withLabel("Physical index").withValue(indexed == null ? null : indexed.physicalIndex()).withMonospace(true))
         .withItem("Indexed version", indexed == null || indexed.version() == null ? null : String.valueOf(indexed.version()))
         .withItem("Database version", inspection.expected() == null || inspection.expected().getVersion() == null ? "not versioned (no timestamp field)" : String.valueOf(inspection.expected().getVersion()))
         .withItem("Indexed at", indexed == null || indexed.source() == null ? null : indexed.source().path("indexedAt").asText(null)));

      DeveloperPanelBlock table = DeveloperPanelBlock.table("Database vs index", List.of("Field", "Database", "Index", "Match"));
      for(RecordInspector.FieldDiff diff : CollectionUtils.nonNullList(inspection.diffs()))
      {
         table.withRow(row(diff.name(), truncate(diff.databaseValue()), truncate(diff.indexValue()), diff.isSame() ? "yes" : "NO"));
      }
      panel.withBlock(table);
   }



   /*******************************************************************************
    ** The record's failed index events, or null when there are none.
    *******************************************************************************/
   private DeveloperPanelBlock failedEventsBlock(String failedTable, String tableName, String recordId) throws QException
   {
      QueryInput queryInput = new QueryInput(failedTable);
      queryInput.setFilter(new QQueryFilter(
         new QFilterCriteria("tableName", QCriteriaOperator.EQUALS, tableName),
         new QFilterCriteria("recordId", QCriteriaOperator.EQUALS, recordId))
         .withOrderBy(new QFilterOrderBy("id", false))
         .withLimit(MAX_FAILED_EVENTS));
      List<QRecord> events = CollectionUtils.nonNullList(new QueryAction().execute(queryInput).getRecords());
      if(events.isEmpty())
      {
         return (null);
      }

      DeveloperPanelBlock block = DeveloperPanelBlock.table("Failed index events" + (events.size() >= MAX_FAILED_EVENTS ? " (latest " + MAX_FAILED_EVENTS + ")" : ""),
         List.of("Id", "Action", "Status", "Attempts", "Created", "Error"));
      for(QRecord event : events)
      {
         block.withRow(row(event.getValueString("id"), event.getValueString("action"), event.getValueString("status"), event.getValueString("attempts"),
            event.getValueString("createDate"), truncate(event.getValueString("errorMessage"))));
      }
      return (block);
   }



   /*******************************************************************************
    ** Add a record action when the process exists and the user may run it.
    *******************************************************************************/
   private void addActionIfPermitted(DeveloperPanelBlock actions, String processName, String tableName, String label, String description)
   {
      if(QContext.getQInstance().getProcess(processName) != null && PermissionsHelper.hasProcessPermission(new QueryInput(tableName), processName))
      {
         actions.withAction(new DeveloperPanelAction().withLabel(label).withProcessName(processName).withTableName(tableName).withDescription(description));
      }
   }



   /*******************************************************************************
    ** Whether the user may inspect records: the Inspect Search Document
    ** process's permission (debugPermissionRules), evaluated on the
    ** registered process, or on an unregistered copy when the record actions
    ** are turned off.
    *******************************************************************************/
   private boolean mayInspect(QuickSearchQBitConfig config, String tableName)
   {
      String processName = QuickSearchDiagnosticsProcessMetaDataHelper.inspectProcessName(config, tableName);
      if(QContext.getQInstance().getProcess(processName) != null)
      {
         return (PermissionsHelper.hasProcessPermission(new QueryInput(tableName), processName));
      }
      return (PermissionsHelper.getPermissionCheckResult(new QueryInput(tableName), QuickSearchDiagnosticsProcessMetaDataHelper.buildInspectProcess(config, tableName))
         == PermissionCheckResult.ALLOW);
   }



   /*******************************************************************************
    ** Status label and tone from the enabled flag and the inspection verdict.
    *******************************************************************************/
   private void setStatus(DeveloperPanel panel, boolean enabled, String verdict, boolean openSearchFailed)
   {
      if(!enabled)
      {
         panel.withStatusLabel(STATUS_DISABLED).withStatusTone(DeveloperPanelTone.WARNING);
      }
      else if(openSearchFailed)
      {
         panel.withStatusLabel(STATUS_UNAVAILABLE).withStatusTone(DeveloperPanelTone.WARNING);
      }
      else if(RecordInspector.VERDICT_CURRENT.equals(verdict))
      {
         panel.withStatusLabel(STATUS_INDEXED).withStatusTone(DeveloperPanelTone.SUCCESS);
      }
      else if(RecordInspector.VERDICT_STALE.equals(verdict))
      {
         panel.withStatusLabel(STATUS_STALE).withStatusTone(DeveloperPanelTone.WARNING);
      }
      else if(RecordInspector.VERDICT_MISSING.equals(verdict))
      {
         panel.withStatusLabel(STATUS_NOT_INDEXED).withStatusTone(DeveloperPanelTone.ERROR);
      }
      else
      {
         panel.withStatusLabel(STATUS_ENABLED).withStatusTone(DeveloperPanelTone.INFO);
      }
   }



   /*******************************************************************************
    ** Whether the table exists and the user may read it.
    *******************************************************************************/
   private static boolean mayRead(String tableName)
   {
      QInstance qInstance = QContext.getQInstance();
      return (qInstance != null && qInstance.getTable(tableName) != null && PermissionsHelper.hasTablePermission(new QueryInput(tableName), tableName, TablePermissionSubType.READ));
   }



   /*******************************************************************************
    ** First row of a query (as the user), or null.
    *******************************************************************************/
   private static QRecord queryFirst(String tableName, QQueryFilter filter) throws QException
   {
      QueryInput queryInput = new QueryInput(tableName);
      queryInput.setFilter(filter.withLimit(1));
      List<QRecord> records = new QueryAction().execute(queryInput).getRecords();
      return (CollectionUtils.nullSafeHasContents(records) ? records.get(0) : null);
   }



   /*******************************************************************************
    ** App-relative link to a record.
    *******************************************************************************/
   private static String recordHref(String tableName, Serializable id)
   {
      return ("/app/" + tableName + "/" + id);
   }



   /*******************************************************************************
    ** A table row (null cells as empty strings).
    *******************************************************************************/
   private static List<String> row(String... cells)
   {
      List<String> row = new ArrayList<>();
      for(String cell : cells)
      {
         row.add(cell == null ? "" : cell);
      }
      return (row);
   }



   /*******************************************************************************
    ** Long values cut for display.
    *******************************************************************************/
   static String truncate(String value)
   {
      if(value == null || value.length() <= MAX_VALUE_LENGTH)
      {
         return (value);
      }
      return (value.substring(0, MAX_VALUE_LENGTH) + "... (" + value.length() + " characters)");
   }

}

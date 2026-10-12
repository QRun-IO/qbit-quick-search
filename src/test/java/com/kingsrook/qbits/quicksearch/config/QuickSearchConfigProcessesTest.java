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


import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.metadata.QuickSearchConfigProcessMetaDataHelper;
import com.kingsrook.qbits.quicksearch.model.QuickSearchConfigTombstone;
import com.kingsrook.qbits.quicksearch.model.QuickSearchDocumentsChoice;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexField;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;
import com.kingsrook.qbits.quicksearch.model.QuickSearchSettings;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.processes.AbstractIndexingStep;
import com.kingsrook.qbits.quicksearch.processes.BasepullIndexStep;
import com.kingsrook.qbits.quicksearch.processes.FullReindexStep;
import com.kingsrook.qbits.quicksearch.processes.ReconcileIndexStep;
import com.kingsrook.qbits.quicksearch.processes.config.StopIndexingSteps;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/*******************************************************************************
 ** The config processes end to end over the memory backend (OpenSearch is a
 ** mock that records documents): Add Search Index, Apply Search Index
 ** Changes, Disable / Remove Search Index, Cancel Index Run, Pause / Resume
 ** Indexing, and their permission.
 *******************************************************************************/
class QuickSearchConfigProcessesTest extends BaseDataDrivenTest
{

   /*******************************************************************************
    ** Create flow: pick a table, review (estimated records, default fields,
    ** what is never indexed), save: the index row and fields exist, the
    ** first build runs (no scheduler here, so at once) and the records are
    ** indexed with the new config.
    *******************************************************************************/
   @Test
   void testAddSearchIndex_createBuildSearchable() throws QException
   {
      runtime.getConfigStore().tryPrime();
      insert(ARTICLE_TABLE,
         new QRecord().withValue("id", 1).withValue("title", "Blue Widget").withValue("body", "A fine widget").withValue("sku", "BW-1").withValue("internalNotes", "do not leak"),
         new QRecord().withValue("id", 2).withValue("title", "Red Gadget").withValue("body", "A handy gadget").withValue("sku", "RG-2"));

      RunProcessOutput output = runProcess(QuickSearchConfigProcessMetaDataHelper.ADD_INDEX_PROCESS_NAME, Map.of("tableName", ARTICLE_TABLE), null);

      assertThat(reviewMessages(output)).anySatisfy(m -> assertThat(m).contains("Article will be added").contains("about 2 records"));
      assertThat(reviewMessages(output)).anySatisfy(m -> assertThat(m).contains("starts with 3 fields").contains("title: Title, weight 3"));
      assertThat(reviewMessages(output)).anySatisfy(m -> assertThat(m).contains("Never indexed").contains("internalNotes").contains("sensitive"));
      assertThat(resultMessages(output)).anySatisfy(m -> assertThat(m).contains("Added Article to Quick Search with 3 fields").contains("built now"));

      QRecord row = indexRow(ARTICLE_TABLE);
      assertThat(row.getValueString("source")).isEqualTo(QuickSearchIndex.SOURCE_OWNER);
      assertThat(row.getValueBoolean("ownerEdited")).isTrue();
      assertThat(row.getValueString("basepullTimestampField")).isEqualTo("modifyDate");
      assertThat(row.getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_ACTIVE);
      assertThat(fieldRows(ARTICLE_TABLE)).extracting(r -> r.getValueString("fieldName")).containsExactly("title", "body", "sku");

      assertThat(indexed).filteredOn(d -> ARTICLE_TABLE.equals(d.getSourceTable())).extracting(OpenSearchDocument::getRecordId).containsExactlyInAnyOrder("1", "2");
      OpenSearchDocument widget = indexed.stream().filter(d -> ARTICLE_TABLE.equals(d.getSourceTable()) && "1".equals(d.getRecordId())).findFirst().orElseThrow();
      assertThat(widget.getRecordLabel()).isEqualTo("Blue Widget");
      assertThat(widget.getSearchableText()).contains("A fine widget").doesNotContain("do not leak");
      assertThat(runtime.isTableEnabled(ARTICLE_TABLE)).isTrue();
      assertThat(runtime.getDiscoveredTables()).extracting(t -> t.getTableName()).contains(ARTICLE_TABLE);

      ////////////////////////////////////////////////////////////
      // adding it again, or a secret or unknown table, refuses //
      ////////////////////////////////////////////////////////////
      RunProcessOutput again = runProcess(QuickSearchConfigProcessMetaDataHelper.ADD_INDEX_PROCESS_NAME, Map.of("tableName", ARTICLE_TABLE), null);
      assertThat(reviewMessages(again)).anySatisfy(m -> assertThat(m).contains("already in Quick Search"));
      assertThat(rows(QuickSearchIndex.TABLE_NAME)).hasSize(2);

      assertThat(reviewMessages(runProcess(QuickSearchConfigProcessMetaDataHelper.ADD_INDEX_PROCESS_NAME, Map.of("tableName", SECRET_TABLE), null)))
         .anySatisfy(m -> assertThat(m).contains("secrets"));
      assertThat(reviewMessages(runProcess(QuickSearchConfigProcessMetaDataHelper.ADD_INDEX_PROCESS_NAME, Map.of(), null)))
         .anySatisfy(m -> assertThat(m).contains("Choose a table"));
      assertThat(rows(QuickSearchIndex.TABLE_NAME)).hasSize(2);
   }



   /*******************************************************************************
    ** With a scheduler, saving queues the build; the next basepull runs it.
    ** A bad timestamp field is refused; an added-disabled table is not built.
    *******************************************************************************/
   @Test
   void testAddSearchIndex_withScheduler_queuedThenBuiltByBasepull() throws QException
   {
      config.withSchedulerName("testScheduler");
      runtime.getConfigStore().tryPrime();
      insert(ARTICLE_TABLE, new QRecord().withValue("id", 1).withValue("title", "Blue Widget").withValue("modifyDate", Instant.now()));

      assertThat(reviewMessages(runProcess(QuickSearchConfigProcessMetaDataHelper.ADD_INDEX_PROCESS_NAME, Map.of("tableName", ARTICLE_TABLE, "basepullTimestampField", "title"), null)))
         .anySatisfy(m -> assertThat(m).contains("not a date-time field"));

      RunProcessOutput output = runProcess(QuickSearchConfigProcessMetaDataHelper.ADD_INDEX_PROCESS_NAME, Map.of("tableName", ARTICLE_TABLE, "basepullIntervalMinutes", 10), null);
      assertThat(resultMessages(output)).anySatisfy(m -> assertThat(m).contains("queued and starts with the next update"));
      assertThat(indexRow(ARTICLE_TABLE).getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_PENDING_BUILD);
      assertThat(indexRow(ARTICLE_TABLE).getValueInteger("basepullIntervalMinutes")).isEqualTo(10);
      assertThat(QuickSearchStatus.read(runtime).buildingText()).contains("Article");

      RunBackendStepOutput basepull = new RunBackendStepOutput();
      new BasepullIndexStep().run(new RunBackendStepInput(), basepull);
      assertThat(basepull.getValueInteger("tablesBuilt")).isEqualTo(2);
      assertThat(indexRow(ARTICLE_TABLE).getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_ACTIVE);
      assertThat(indexed).extracting(OpenSearchDocument::getSourceTable).contains(ARTICLE_TABLE);
      assertThat(rows(QuickSearchIndexRun.TABLE_NAME)).extracting(r -> r.getValueString("runType")).contains(FullReindexStep.RUN_TYPE);
   }



   /*******************************************************************************
    ** Field edit: the review previews what changed since the last build and
    ** how many records the rebuild reads; executing rebuilds with the new
    ** field.
    *******************************************************************************/
   @Test
   void testApplyChanges_previewThenReindex() throws QException
   {
      runtime.getConfigStore().tryPrime();
      insert(TEST_ENTITY_TABLE, new QRecord().withValue("id", 1).withValue("name", "Alpha").withValue("description", "first").withValue("modifyDate", Instant.now()));
      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());
      QRecord row = indexRow(TEST_ENTITY_TABLE);
      assertThat(row.getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_ACTIVE);

      insert(QuickSearchIndexField.TABLE_NAME, new QRecord().withValue("quickSearchIndexId", row.getValue("id")).withValue("fieldName", "modifyDate").withValue("weight", 2));
      assertThat(indexRow(TEST_ENTITY_TABLE).getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_NEEDS_REINDEX);
      assertThat(QuickSearchStatus.read(runtime).needsReindexText()).contains("Apply Search Index Changes");

      indexed.clear();
      RunProcessOutput output = runProcess(QuickSearchConfigProcessMetaDataHelper.APPLY_CHANGES_PROCESS_NAME, Map.of(), List.of(row.getValueInteger("id")));
      assertThat(reviewMessages(output)).anySatisfy(m -> assertThat(m).contains("will be rebuilt with these field changes").contains("about 1 record").contains("Added: ").contains("modifyDate"));
      assertThat(resultMessages(output)).anySatisfy(m -> assertThat(m).contains("built now"));
      assertThat(indexRow(TEST_ENTITY_TABLE).getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_ACTIVE);
      assertThat(indexRow(TEST_ENTITY_TABLE).getValueString("searchableFieldsJson")).contains("modifyDate");
      assertThat(indexed).singleElement().satisfies(d -> assertThat(d.getFieldValues()).containsKey("modifyDate"));

      ///////////////////////////////////////////////////////////////
      // with nothing changed, apply says so (and rebuilds anyway) //
      ///////////////////////////////////////////////////////////////
      RunProcessOutput unchanged = runProcess(QuickSearchConfigProcessMetaDataHelper.APPLY_CHANGES_PROCESS_NAME, Map.of(), List.of(row.getValueInteger("id")));
      assertThat(reviewMessages(unchanged)).anySatisfy(m -> assertThat(m).contains("no field changes"));
   }



   /*******************************************************************************
    ** Disable stops indexing (real-time and search) and deletes or keeps the
    ** documents per the choice; enabling again queues a build.
    *******************************************************************************/
   @Test
   void testDisable_stopsIndexing() throws QException
   {
      runtime.getConfigStore().tryPrime();
      Integer id = indexRow(TEST_ENTITY_TABLE).getValueInteger("id");
      when(mockClient.countDocumentsForTable(TEST_ENTITY_TABLE)).thenReturn(4L);

      RunProcessOutput keep = runProcess(QuickSearchConfigProcessMetaDataHelper.DISABLE_INDEX_PROCESS_NAME,
         Map.of(StopIndexingSteps.FIELD_DOCUMENTS, QuickSearchDocumentsChoice.KEEP.name()), List.of(id));
      assertThat(reviewMessages(keep)).anySatisfy(m -> assertThat(m).contains("will be disabled").contains("4 search documents will be kept"));
      assertThat(runtime.isTableEnabled(TEST_ENTITY_TABLE)).isFalse();
      verify(mockClient, never()).deleteDocumentsForTable(TEST_ENTITY_TABLE);

      //////////////////////////////////////////////////////////////////
      // disabled: the listener and the basepull leave the table alone //
      //////////////////////////////////////////////////////////////////
      indexed.clear();
      insert(TEST_ENTITY_TABLE, new QRecord().withValue("id", 7).withValue("name", "Ignored").withValue("modifyDate", Instant.now()));
      new com.kingsrook.qbits.quicksearch.listeners.QuickSearchRecordChangeListener().onRecordsChanged(
         new com.kingsrook.qqq.backend.core.actions.tables.listeners.RecordChangeEvent()
            .withTableName(TEST_ENTITY_TABLE).withType(com.kingsrook.qqq.backend.core.actions.tables.listeners.RecordChangeType.INSERT)
            .withRecords(List.of(new QRecord().withValue("id", 7))));
      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());
      assertThat(indexed).isEmpty();

      assertThat(reviewMessages(runProcess(QuickSearchConfigProcessMetaDataHelper.DISABLE_INDEX_PROCESS_NAME, Map.of(), List.of(id))))
         .anySatisfy(m -> assertThat(m).contains("already disabled"));

      new UpdateAction().execute(new UpdateInput(QuickSearchIndex.TABLE_NAME).withRecord(new QRecord().withValue("id", id).withValue("enabled", true)));
      assertThat(indexRow(TEST_ENTITY_TABLE).getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_PENDING_BUILD);

      RunProcessOutput delete = runProcess(QuickSearchConfigProcessMetaDataHelper.DISABLE_INDEX_PROCESS_NAME,
         Map.of(StopIndexingSteps.FIELD_DOCUMENTS, QuickSearchDocumentsChoice.DELETE.name()), List.of(id));
      assertThat(resultMessages(delete)).anySatisfy(m -> assertThat(m).contains("was disabled").contains("deleted"));
      verify(mockClient).deleteDocumentsForTable(TEST_ENTITY_TABLE);
      assertThat(indexRow(TEST_ENTITY_TABLE).getValueInteger("documentCount")).isZero();
   }



   /*******************************************************************************
    ** Remove deletes the rows and documents, leaves a tombstone, and priming
    ** does not bring the code table back.
    *******************************************************************************/
   @Test
   void testRemove_cleanAndNotResurrected() throws QException
   {
      runtime.getConfigStore().tryPrime();
      Integer id = indexRow(TEST_ENTITY_TABLE).getValueInteger("id");
      insert(QuickSearchFailedEvent.TABLE_NAME, new QRecord().withValue("tableName", TEST_ENTITY_TABLE).withValue("recordId", "1").withValue("action", "INDEX")
         .withValue("attempts", 0).withValue("status", QuickSearchFailedEvent.STATUS_PENDING));

      RunProcessOutput output = runProcess(QuickSearchConfigProcessMetaDataHelper.REMOVE_INDEX_PROCESS_NAME,
         Map.of(StopIndexingSteps.FIELD_DOCUMENTS, QuickSearchDocumentsChoice.DELETE.name()), List.of(id));
      assertThat(reviewMessages(output)).anySatisfy(m -> assertThat(m).contains("will be removed from Quick Search").contains("deleted from the search index"));
      assertThat(reviewMessages(output)).anySatisfy(m -> assertThat(m).contains("declared searchable in code"));
      assertThat(resultMessages(output)).anySatisfy(m -> assertThat(m).contains("was removed"));

      assertThat(indexRow(TEST_ENTITY_TABLE)).isNull();
      assertThat(rows(QuickSearchIndexField.TABLE_NAME)).isEmpty();
      assertThat(rows(QuickSearchFailedEvent.TABLE_NAME)).isEmpty();
      assertThat(rows(QuickSearchConfigTombstone.TABLE_NAME)).singleElement().satisfies(t -> assertThat(t.getValueString("fieldName")).isNull());
      verify(mockClient).deleteDocumentsForTable(TEST_ENTITY_TABLE);
      assertThat(runtime.getTableConfig(TEST_ENTITY_TABLE)).isNull();

      runtime.getConfigStore().setPrimed(false);
      runtime.getConfigStore().tryPrime();
      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());
      assertThat(indexRow(TEST_ENTITY_TABLE)).as("code defaults do not resurrect a removed table").isNull();
   }



   /*******************************************************************************
    ** Global pause: nothing is indexed (real-time changes are deferred,
    ** basepull, reconcile and full reindex skip), search is untouched; resume
    ** replays the deferred changes.
    *******************************************************************************/
   @Test
   void testPauseResume_noIndexingButSearchWorks() throws QException
   {
      runtime.getConfigStore().tryPrime();
      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());
      indexed.clear();

      RunProcessOutput pause = runProcess(QuickSearchConfigProcessMetaDataHelper.PAUSE_PROCESS_NAME, Map.of("pauseReason", "maintenance"), List.of(QuickSearchSettings.SETTINGS_ID));
      assertThat(reviewMessages(pause)).anySatisfy(m -> assertThat(m).contains("All indexing will pause"));
      assertThat(runtime.isIndexingPaused()).isTrue();
      assertThat(QuickSearchStatus.read(runtime).pausedText()).contains("(maintenance)");

      insert(TEST_ENTITY_TABLE, new QRecord().withValue("id", 1).withValue("name", "Paused change").withValue("modifyDate", Instant.now()));
      new com.kingsrook.qbits.quicksearch.listeners.QuickSearchRecordChangeListener().onRecordsChanged(
         new com.kingsrook.qqq.backend.core.actions.tables.listeners.RecordChangeEvent()
            .withTableName(TEST_ENTITY_TABLE).withType(com.kingsrook.qqq.backend.core.actions.tables.listeners.RecordChangeType.INSERT)
            .withRecords(List.of(new QRecord().withValue("id", 1))));
      assertThat(rows(QuickSearchFailedEvent.TABLE_NAME)).singleElement().satisfies(e -> assertThat(e.getValueString("status")).isEqualTo(QuickSearchFailedEvent.STATUS_DEFERRED));

      RunBackendStepOutput basepull = new RunBackendStepOutput();
      new BasepullIndexStep().run(new RunBackendStepInput(), basepull);
      assertThat(basepull.getValueBoolean("paused")).isTrue();
      RunBackendStepOutput reconcile = new RunBackendStepOutput();
      new ReconcileIndexStep().run(new RunBackendStepInput(), reconcile);
      assertThat(reconcile.getValueString("message")).contains("paused");
      RunBackendStepOutput reindex = new RunBackendStepOutput();
      new FullReindexStep().run(new RunBackendStepInput(), reindex);
      assertThat(reindex.getValueString("message")).contains("paused");
      assertThat(indexed).as("nothing is indexed while paused").isEmpty();

      ////////////////////////////////////////////////////////////////////
      // search still runs against the existing index while paused       //
      ////////////////////////////////////////////////////////////////////
      when(mockClient.searchRecordIdsPerTable(org.mockito.ArgumentMatchers.anyString(), anyList(), anyInt(), anyList(), anyInt())).thenReturn(Map.of(TEST_ENTITY_TABLE, List.of("1")));
      config.setServeCoreRecordSearch(true);
      var provider = new com.kingsrook.qbits.quicksearch.actions.QuickSearchRecordSearchProvider();
      assertThat(provider.claimsTable(QContext.getQInstance().getTable(TEST_ENTITY_TABLE))).isTrue();

      RunProcessOutput resume = runProcess(QuickSearchConfigProcessMetaDataHelper.RESUME_PROCESS_NAME, Map.of(), List.of(QuickSearchSettings.SETTINGS_ID));
      assertThat(reviewMessages(resume)).anySatisfy(m -> assertThat(m).contains("1 changes made while paused"));
      assertThat(runtime.isIndexingPaused()).isFalse();
      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());
      assertThat(indexed).extracting(OpenSearchDocument::getRecordId).contains("1");
      assertThat(rows(QuickSearchFailedEvent.TABLE_NAME)).isEmpty();
   }



   /*******************************************************************************
    ** Cancel mid-run: the flag set by Cancel Index Run stops a running build
    ** between batches; the run ends CANCELLED with its counts, and the index
    ** waits for Apply Search Index Changes.
    *******************************************************************************/
   @Test
   void testCancel_midRun() throws QException
   {
      config.withSourceBatchSize(2);
      runtime.getConfigStore().tryPrime();
      for(int i = 1; i <= 6; i++)
      {
         insert(TEST_ENTITY_TABLE, new QRecord().withValue("id", i).withValue("name", "Row " + i).withValue("modifyDate", Instant.now()));
      }

      ///////////////////////////////////////////////////////////////////////
      // while the first batch is indexed, an admin cancels the running run //
      ///////////////////////////////////////////////////////////////////////
      doAnswer(invocation ->
      {
         List<OpenSearchDocument> docs = invocation.getArgument(0);
         indexed.addAll(docs);
         QRecord running = rows(QuickSearchIndexRun.TABLE_NAME).stream().filter(r -> AbstractIndexingStep.RUN_RUNNING.equals(r.getValueString("status"))).findFirst().orElseThrow();
         if(indexed.size() == 2)
         {
            RunProcessOutput review = runProcess(QuickSearchConfigProcessMetaDataHelper.CANCEL_RUN_PROCESS_NAME, Map.of(), List.of(running.getValueInteger("id")));
            assertThat(reviewMessages(review)).anySatisfy(m -> assertThat(m).contains("will stop after its current batch"));
         }
         return (new com.kingsrook.qbits.quicksearch.opensearch.BulkIndexResult().withSuccessCount(docs.size()));
      }).when(mockClient).indexDocuments(anyList(), anyInt());

      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      QRecord run = rows(QuickSearchIndexRun.TABLE_NAME).get(0);
      assertThat(run.getValueString("status")).isEqualTo(AbstractIndexingStep.RUN_CANCELLED);
      assertThat(run.getValueInteger("recordsProcessed")).isEqualTo(2);
      assertThat(run.getValueInteger("recordsIndexed")).isEqualTo(2);
      assertThat(run.getValueString("errorMessage")).startsWith("Cancelled by");
      assertThat(indexed).hasSize(2);
      assertThat(indexRow(TEST_ENTITY_TABLE).getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_NEEDS_REINDEX);

      ///////////////////////////////////////////////////////
      // a finished run is skipped by Cancel Index Run      //
      ///////////////////////////////////////////////////////
      RunProcessOutput again = runProcess(QuickSearchConfigProcessMetaDataHelper.CANCEL_RUN_PROCESS_NAME, Map.of(), List.of(run.getValueInteger("id")));
      assertThat(reviewMessages(again)).anySatisfy(m -> assertThat(m).contains("is not running"));
   }



   /*******************************************************************************
    ** The processes need the admin permission; the config tables their own.
    *******************************************************************************/
   @Test
   void testPermissions() throws QException
   {
      QuickSearchQBitConfig adminConfig = config;
      String                processName = adminConfig.applyPrefix(QuickSearchConfigProcessMetaDataHelper.ADD_INDEX_PROCESS_NAME);
      assertThat(QContext.getQInstance().getProcess(processName).getPermissionRules().getPermissionBaseName()).isEqualTo(QuickSearchQBitConfig.DEFAULT_ADMIN_PERMISSION_BASE_NAME);

      QContext.init(QContext.getQInstance(), new QSession());
      for(String name : List.of(QuickSearchConfigProcessMetaDataHelper.ADD_INDEX_PROCESS_NAME, QuickSearchConfigProcessMetaDataHelper.REMOVE_INDEX_PROCESS_NAME,
         QuickSearchConfigProcessMetaDataHelper.DISABLE_INDEX_PROCESS_NAME, QuickSearchConfigProcessMetaDataHelper.APPLY_CHANGES_PROCESS_NAME,
         QuickSearchConfigProcessMetaDataHelper.CANCEL_RUN_PROCESS_NAME, QuickSearchConfigProcessMetaDataHelper.PAUSE_PROCESS_NAME,
         QuickSearchConfigProcessMetaDataHelper.RESUME_PROCESS_NAME))
      {
         assertThatThrownBy(() -> PermissionsHelper.checkProcessPermissionThrowing(new RunProcessInput(), name)).isInstanceOf(QPermissionDeniedException.class);
      }

      QContext.getQSession().withPermission(QuickSearchQBitConfig.DEFAULT_ADMIN_PERMISSION_BASE_NAME + ".hasAccess");
      PermissionsHelper.checkProcessPermissionThrowing(new RunProcessInput(), processName);

      QTableMetaData fieldTable = QContext.getQInstance().getTable(QuickSearchIndexField.TABLE_NAME);
      assertThat(fieldTable.getAuditRules().getAuditLevel().name()).isEqualTo("FIELD");
      assertThat(QContext.getQInstance().getTable(QuickSearchIndex.TABLE_NAME).getAuditRules().getAuditLevel().name()).isEqualTo("FIELD");
      assertThat(QContext.getQInstance().getTable(QuickSearchSettings.TABLE_NAME).getAuditRules().getAuditLevel().name()).isEqualTo("FIELD");
   }



   /*******************************************************************************
    ** Roles shape the document: TITLE fields make the label, SUBTITLE the
    ** subtitle, DISPLAY the details; KEYWORD and EXACT stay out of the snippet;
    ** EXACT values are stored lower-cased for whole-value matching.
    *******************************************************************************/
   @Test
   void testRolesShapeDocuments() throws QException
   {
      QContext.getQInstance().getTable(TEST_ENTITY_TABLE).withField(new QFieldMetaData("sku", QFieldType.STRING));
      runtime.getConfigStore().tryPrime();
      Integer indexId = indexRow(TEST_ENTITY_TABLE).getValueInteger("id");
      for(QRecord field : fieldRows(TEST_ENTITY_TABLE))
      {
         String role = "name".equals(field.getValueString("fieldName")) ? "TITLE" : "SUBTITLE";
         new UpdateAction().execute(new UpdateInput(QuickSearchIndexField.TABLE_NAME).withRecord(new QRecord().withValue("id", field.getValue("id")).withValue("role", role)));
      }
      insert(QuickSearchIndexField.TABLE_NAME, new QRecord().withValue("quickSearchIndexId", indexId).withValue("fieldName", "sku").withValue("role", "EXACT"));
      insert(QuickSearchIndexField.TABLE_NAME, new QRecord().withValue("quickSearchIndexId", indexId).withValue("fieldName", "modifyDate").withValue("role", "DISPLAY"));
      insert(TEST_ENTITY_TABLE, new QRecord().withValue("id", 1).withValue("name", "Blue Widget").withValue("description", "Small").withValue("sku", " BW-001 ").withValue("modifyDate", Instant.now()));

      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());

      OpenSearchDocument doc = indexed.get(indexed.size() - 1);
      assertThat(doc.getRecordLabel()).isEqualTo("Blue Widget");
      assertThat(doc.getSubtitle()).isEqualTo("Small");
      assertThat(doc.getDisplayValues()).containsKey("Modify Date");
      assertThat(doc.getFieldValuesExact()).containsEntry("sku", "bw-001");
      assertThat(doc.getSearchableText()).contains("Blue Widget").doesNotContain("BW-001");
   }

}

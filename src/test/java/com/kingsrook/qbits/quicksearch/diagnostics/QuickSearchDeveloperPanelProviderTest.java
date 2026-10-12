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


import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.developer.DeveloperPanelsAction;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper;
import com.kingsrook.qqq.backend.core.actions.permissions.TablePermissionSubType;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanel;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelBlock;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelBlockType;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelContext;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelKeyValue;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelTone;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelsMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryRecordStore;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitContext;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitProducer;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.QuickSearchStartupMode;
import com.kingsrook.qbits.quicksearch.SearchableFieldConfig;
import com.kingsrook.qbits.quicksearch.metadata.QuickSearchAdminAppMetaDataProducer;
import com.kingsrook.qbits.quicksearch.metadata.QuickSearchDeveloperPanelsMetaDataProducer;
import com.kingsrook.qbits.quicksearch.metadata.QuickSearchDiagnosticsProcessMetaDataHelper;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Unit test for {@link QuickSearchDeveloperPanelProvider}: registration,
 ** availability (indexed table, index read permission), the panel's content
 ** with OpenSearch unreachable (a warning, not a failure) and with a faked
 ** inspection, and actions and blocks hidden without permission.
 *******************************************************************************/
class QuickSearchDeveloperPanelProviderTest
{
   private static final String BACKEND = "memory";
   private static final String PRODUCT = "product";
   private static final String NOTE    = "note";

   private QuickSearchQBitConfig config;
   private QuickSearchRuntime    runtime;
   private Integer               indexRowId;



   /*******************************************************************************
    ** An indexed product table and an unindexed note table; Quick Search
    ** produced in DEGRADED mode against a closed port (OpenSearch "down").
    *******************************************************************************/
   @BeforeEach
   void setUp() throws QException
   {
      MemoryRecordStore.getInstance().reset();

      QInstance qInstance = new QInstance();
      qInstance.addBackend(new QBackendMetaData().withName(BACKEND).withBackendType(MemoryBackendModule.class));
      qInstance.withInstanceDefaultAuthentication(new QAuthenticationMetaData().withName("anonymous").withType(QAuthenticationType.FULLY_ANONYMOUS));
      qInstance.addTable(table(PRODUCT));
      qInstance.addTable(table(NOTE));
      QContext.init(qInstance, new QSession());

      config = new QuickSearchQBitConfig()
         .withStartupMode(QuickSearchStartupMode.DEGRADED)
         .withBackendName(BACKEND)
         .withOpensearchHost("127.0.0.1")
         .withOpensearchPort(1)
         .withConnectTimeoutMillis(500)
         .withResponseTimeoutMillis(1000)
         .withOpensearchIndexName("devpanel-test")
         .withEnableRealTimeIndexing(false)
         .withSearchableTable(PRODUCT, List.of(new SearchableFieldConfig("name")));
      new QuickSearchQBitProducer().withConfig(config).produce(qInstance);
      runtime = config.getRuntime();

      ///////////////////////////////////////////////////////////////
      // data, inserted by a session that may write every table    //
      ///////////////////////////////////////////////////////////////
      QContext.setQSession(new QSession().withPermissions(allTablePermissions()));
      new InsertAction().execute(new InsertInput(PRODUCT).withRecords(List.of(
         new QRecord().withValue("id", 1).withValue("name", "Blue Widget").withValue("modifyDate", Instant.now()))));
      indexRowId = new InsertAction().execute(new InsertInput(config.getQuickSearchIndexTableName()).withRecords(List.of(
         new QRecord().withValue("tableName", PRODUCT).withValue("enabled", true).withValue("status", "ACTIVE").withValue("recordCount", 1)
            .withValue("lastBasepullTime", Instant.parse("2026-10-01T10:00:00Z"))))).getRecords().get(0).getValueInteger("id");
      new InsertAction().execute(new InsertInput(config.getQuickSearchIndexRunTableName()).withRecords(List.of(
         new QRecord().withValue("quickSearchIndexId", indexRowId).withValue("runType", "BASEPULL").withValue("status", "COMPLETED")
            .withValue("startTime", Instant.parse("2026-10-01T10:00:00Z")).withValue("recordsProcessed", 1).withValue("recordsIndexed", 1).withValue("errorCount", 0))));
      new InsertAction().execute(new InsertInput(config.applyPrefix(QuickSearchFailedEvent.TABLE_NAME)).withRecords(List.of(
         new QRecord().withValue("tableName", PRODUCT).withValue("recordId", "1").withValue("action", "UPSERT").withValue("errorMessage", "connection refused")
            .withValue("attempts", 3).withValue("status", QuickSearchFailedEvent.STATUS_PENDING))));
   }



   /*******************************************************************************
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      if(runtime != null)
      {
         runtime.close();
      }
      QContext.clear();
      QuickSearchQBitContext.clear();
      MemoryRecordStore.getInstance().reset();
   }



   /*******************************************************************************
    *******************************************************************************/
   private static QTableMetaData table(String name)
   {
      return (new QTableMetaData()
         .withName(name)
         .withLabel(name)
         .withBackendName(BACKEND)
         .withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("name", QFieldType.STRING))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME)));
   }



   /*******************************************************************************
    ** Read and write permissions on the three operational tables.
    *******************************************************************************/
   private List<String> allTablePermissions()
   {
      List<String> permissions = new ArrayList<>();
      for(String tableName : List.of(config.getQuickSearchIndexTableName(), config.getQuickSearchIndexRunTableName(), config.applyPrefix(QuickSearchFailedEvent.TABLE_NAME)))
      {
         for(TablePermissionSubType subType : TablePermissionSubType.values())
         {
            permissions.add(PermissionsHelper.getTablePermissionName(tableName, subType));
         }
      }
      return (permissions);
   }



   /*******************************************************************************
    ** A session with read on the operational tables, plus the debug and admin
    ** permissions.
    *******************************************************************************/
   private void loginAsAdmin()
   {
      List<String> permissions = new ArrayList<>(allTablePermissions());
      permissions.add(QuickSearchQBitConfig.DEFAULT_DEBUG_PERMISSION_BASE_NAME + ".hasAccess");
      permissions.add(QuickSearchQBitConfig.DEFAULT_ADMIN_PERMISSION_BASE_NAME + ".hasAccess");
      QContext.setQSession(new QSession().withPermissions(permissions));
   }



   /*******************************************************************************
    *******************************************************************************/
   private static DeveloperPanelContext context(String tableName, Integer id)
   {
      return (new DeveloperPanelContext()
         .withTable(QContext.getQInstance().getTable(tableName))
         .withRecord(new QRecord().withValue("id", id).withValue("name", "Blue Widget")));
   }



   /*******************************************************************************
    *******************************************************************************/
   private static DeveloperPanel quickSearchPanel(DeveloperPanelContext context)
   {
      return (new DeveloperPanelsAction().execute(context).stream()
         .filter(panel -> QuickSearchDeveloperPanelProvider.ID.equals(panel.getId()))
         .findFirst().orElse(null));
   }



   /*******************************************************************************
    *******************************************************************************/
   private static DeveloperPanelBlock block(DeveloperPanel panel, String label)
   {
      return (panel.getBlocks().stream().filter(block -> label.equals(block.getLabel())).findFirst().orElse(null));
   }



   /*******************************************************************************
    *******************************************************************************/
   private static DeveloperPanelKeyValue item(DeveloperPanelBlock block, String label)
   {
      return (block.getItems().stream().filter(item -> label.equals(item.getLabel())).findFirst().orElseThrow());
   }



   @Test
   void testProducerRegistersProviderOnce() throws QException
   {
      QInstance qInstance = QContext.getQInstance();
      new QuickSearchDeveloperPanelsMetaDataProducer().produce(qInstance).addSelfToInstance(qInstance);

      assertThat(DeveloperPanelsMetaData.of(qInstance).getProviders())
         .filteredOn(reference -> QuickSearchDeveloperPanelProvider.class.getName().equals(reference.getName()))
         .hasSize(1);
   }



   @Test
   void testAvailability()
   {
      QuickSearchDeveloperPanelProvider provider = new QuickSearchDeveloperPanelProvider();

      loginAsAdmin();
      assertThat(provider.isAvailable(context(PRODUCT, 1))).isTrue();
      assertThat(provider.isAvailable(context(NOTE, 1))).as("table Quick Search does not index").isFalse();

      QContext.setQSession(new QSession());
      assertThat(provider.isAvailable(context(PRODUCT, 1))).as("no read permission on Quick Search Index").isFalse();
      assertThat(quickSearchPanel(context(PRODUCT, 1))).isNull();
   }



   @Test
   void testPanelWithOpenSearchDown_warnsInsteadOfFailing()
   {
      loginAsAdmin();
      DeveloperPanel panel = quickSearchPanel(context(PRODUCT, 1));

      assertThat(panel).isNotNull();
      assertThat(panel.getError()).isNull();
      assertThat(panel.getTitle()).isEqualTo("Quick Search");
      assertThat(panel.getOrder()).isEqualTo(400);
      assertThat(panel.getStatusLabel()).isEqualTo(QuickSearchDeveloperPanelProvider.STATUS_UNAVAILABLE);
      assertThat(panel.getStatusTone()).isEqualTo(DeveloperPanelTone.WARNING);

      DeveloperPanelBlock index = block(panel, "Index");
      assertThat(index.getType()).isEqualTo(DeveloperPanelBlockType.KEY_VALUES);
      assertThat(item(index, "Index row").getHref()).isEqualTo("/app/" + config.getQuickSearchIndexTableName() + "/" + indexRowId);
      assertThat(item(index, "Enabled").getValue()).isEqualTo("Yes");
      assertThat(item(index, "Status").getValue()).isEqualTo("ACTIVE");
      assertThat(item(index, "Indexed records").getValue()).isEqualTo("1");
      assertThat(item(index, "Last basepull").getValue()).contains("2026-10-01");
      assertThat(item(index, "Searchable fields").getValue()).isEqualTo("name");

      DeveloperPanelBlock run = block(panel, "Latest index run");
      assertThat(item(run, "Type").getValue()).isEqualTo("BASEPULL");
      assertThat(item(run, "Status").getValue()).isEqualTo("COMPLETED");
      assertThat(item(run, "Run").getHref()).startsWith("/app/" + config.getQuickSearchIndexRunTableName() + "/");

      DeveloperPanelBlock document = block(panel, "Search document");
      assertThat(document.getType()).isEqualTo(DeveloperPanelBlockType.TEXT);
      assertThat(document.getTone()).isEqualTo(DeveloperPanelTone.WARNING);
      assertThat(document.getText()).contains("OpenSearch could not be reached");

      DeveloperPanelBlock failed = block(panel, "Failed index events");
      assertThat(failed.getType()).isEqualTo(DeveloperPanelBlockType.TABLE);
      assertThat(failed.getRows()).hasSize(1);
      assertThat(failed.getRows().get(0)).contains("UPSERT", QuickSearchFailedEvent.STATUS_PENDING, "3", "connection refused");

      DeveloperPanelBlock actions = block(panel, "Actions");
      assertThat(actions.getActions()).extracting(action -> action.getProcessName()).containsExactly(
         QuickSearchDiagnosticsProcessMetaDataHelper.inspectProcessName(config, PRODUCT),
         QuickSearchDiagnosticsProcessMetaDataHelper.reindexRecordsProcessName(config, PRODUCT));
      assertThat(actions.getActions()).allMatch(action -> PRODUCT.equals(action.getTableName()));

      DeveloperPanelBlock links = block(panel, "Links");
      assertThat(links.getLinks()).extracting(link -> link.getHref()).contains(
         "/app/" + config.applyPrefix(QuickSearchAdminAppMetaDataProducer.APP_NAME),
         "/app/" + config.getQuickSearchIndexTableName() + "/" + indexRowId);
   }



   @Test
   void testWithoutDebugOrAdminPermission_noDocumentNoActions()
   {
      QContext.setQSession(new QSession().withPermission(PermissionsHelper.getTablePermissionName(config.getQuickSearchIndexTableName(), TablePermissionSubType.READ)));
      DeveloperPanel panel = quickSearchPanel(context(PRODUCT, 1));

      assertThat(panel).isNotNull();
      assertThat(panel.getError()).isNull();
      assertThat(block(panel, "Search document").getText()).contains("debug permission");
      assertThat(block(panel, "Actions")).isNull();
      assertThat(block(panel, "Latest index run")).as("run table not readable").isNull();
      assertThat(block(panel, "Failed index events")).as("failed event table not readable").isNull();
      assertThat(block(panel, "Links").getLinks()).extracting(link -> link.getHref())
         .doesNotContain("/app/" + config.applyPrefix(QuickSearchAdminAppMetaDataProducer.APP_NAME));
      assertThat(panel.getStatusLabel()).isEqualTo(QuickSearchDeveloperPanelProvider.STATUS_ENABLED);
   }



   @Test
   void testDebugWithoutAdmin_inspectActionOnly()
   {
      QContext.setQSession(new QSession()
         .withPermission(PermissionsHelper.getTablePermissionName(config.getQuickSearchIndexTableName(), TablePermissionSubType.READ))
         .withPermission(QuickSearchQBitConfig.DEFAULT_DEBUG_PERMISSION_BASE_NAME + ".hasAccess"));
      DeveloperPanel panel = quickSearchPanel(context(PRODUCT, 1));

      assertThat(block(panel, "Actions").getActions()).extracting(action -> action.getProcessName())
         .containsExactly(QuickSearchDiagnosticsProcessMetaDataHelper.inspectProcessName(config, PRODUCT));
   }



   @Test
   void testFakedInspection_currentAndStale() throws QException
   {
      loginAsAdmin();

      DeveloperPanel current = new FakeInspectionProvider(RecordInspector.VERDICT_CURRENT,
         List.of(new RecordInspector.FieldDiff("fieldValues.name", "Blue Widget", "Blue Widget"))).build(context(PRODUCT, 1));
      assertThat(current.getStatusLabel()).isEqualTo(QuickSearchDeveloperPanelProvider.STATUS_INDEXED);
      assertThat(current.getStatusTone()).isEqualTo(DeveloperPanelTone.SUCCESS);
      DeveloperPanelBlock comparison = block(current, "Database vs index");
      assertThat(comparison.getColumns()).containsExactly("Field", "Database", "Index", "Match");
      assertThat(comparison.getRows()).containsExactly(List.of("fieldValues.name", "Blue Widget", "Blue Widget", "yes"));

      DeveloperPanel stale = new FakeInspectionProvider(RecordInspector.VERDICT_STALE,
         List.of(new RecordInspector.FieldDiff("fieldValues.name", "Blue Widget", "Old Name"))).build(context(PRODUCT, 1));
      assertThat(stale.getStatusLabel()).isEqualTo(QuickSearchDeveloperPanelProvider.STATUS_STALE);
      assertThat(block(stale, "Database vs index").getRows().get(0)).containsExactly("fieldValues.name", "Blue Widget", "Old Name", "NO");

      DeveloperPanel missing = new FakeInspectionProvider(RecordInspector.VERDICT_MISSING, List.of()).build(context(PRODUCT, 1));
      assertThat(missing.getStatusLabel()).isEqualTo(QuickSearchDeveloperPanelProvider.STATUS_NOT_INDEXED);
      assertThat(missing.getStatusTone()).isEqualTo(DeveloperPanelTone.ERROR);
   }



   @Test
   void testDisabledTable() throws QException
   {
      QContext.setQSession(new QSession().withPermissions(allTablePermissions()));
      new UpdateAction().execute(new UpdateInput(config.getQuickSearchIndexTableName()).withRecords(List.of(new QRecord().withValue("id", indexRowId).withValue("enabled", false))));

      loginAsAdmin();
      DeveloperPanel panel = new FakeInspectionProvider(RecordInspector.VERDICT_CURRENT, List.of()).build(context(PRODUCT, 1));
      assertThat(panel.getStatusLabel()).isEqualTo(QuickSearchDeveloperPanelProvider.STATUS_DISABLED);
      assertThat(item(block(panel, "Index"), "Enabled").getValue()).isEqualTo("No");
   }



   @Test
   void testTruncate()
   {
      assertThat(QuickSearchDeveloperPanelProvider.truncate(null)).isNull();
      assertThat(QuickSearchDeveloperPanelProvider.truncate("short")).isEqualTo("short");
      assertThat(QuickSearchDeveloperPanelProvider.truncate("x".repeat(QuickSearchDeveloperPanelProvider.MAX_VALUE_LENGTH + 5))).endsWith("(" + (QuickSearchDeveloperPanelProvider.MAX_VALUE_LENGTH + 5) + " characters)");
   }



   /*******************************************************************************
    ** A provider whose inspection is a fixed result (no OpenSearch).
    *******************************************************************************/
   private static class FakeInspectionProvider extends QuickSearchDeveloperPanelProvider
   {
      private final String                         verdict;
      private final List<RecordInspector.FieldDiff> diffs;



      /*******************************************************************************
       *******************************************************************************/
      FakeInspectionProvider(String verdict, List<RecordInspector.FieldDiff> diffs)
      {
         this.verdict = verdict;
         this.diffs = diffs;
      }



      /*******************************************************************************
       *******************************************************************************/
      @Override
      protected RecordInspector.Inspection inspect(QuickSearchRuntime runtime, String tableName, String recordId, AbstractActionInput permissionInput)
      {
         return (new RecordInspector.Inspection(tableName, recordId, verdict, diffs, null, null, List.of(), null, null));
      }
   }

}

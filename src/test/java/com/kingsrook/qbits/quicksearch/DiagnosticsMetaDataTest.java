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

package com.kingsrook.qbits.quicksearch;


import java.util.List;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.QInstanceValidator;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.scheduleing.QSchedulerMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.scheduleing.quartz.QuartzSchedulerMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.scheduleing.simple.SimpleSchedulerMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Capability;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;

import com.kingsrook.qbits.quicksearch.metadata.QuickSearchDiagnosticsProcessMetaDataHelper;
import com.kingsrook.qbits.quicksearch.metadata.QuickSearchDiagnosticsWidgetMetaDataProducer;
import com.kingsrook.qbits.quicksearch.model.QuickSearchDiagnosticEvent;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchValidationRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** The metadata the diagnostic tools add: the two history tables, the fixed
 ** processes on Quick Search Index and Quick Search Failed Event, the
 ** per-table record actions, the widget on the admin app, their permission
 ** rules, scheduling, the opt-out flags, and that the produced instance
 ** passes QInstanceValidator.
 *******************************************************************************/
class DiagnosticsMetaDataTest
{
   private static final String BACKEND = "memory";
   private static final String SOURCE  = "product";



   /*******************************************************************************
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      QContext.clear();
      QuickSearchQBitContext.clear();
   }



   /*******************************************************************************
    *******************************************************************************/
   private static QInstance buildInstance()
   {
      QInstance qInstance = new QInstance();
      qInstance.addBackend(new QBackendMetaData().withName(BACKEND).withBackendType(MemoryBackendModule.class));
      qInstance.withInstanceDefaultAuthentication(new QAuthenticationMetaData().withName("anonymous").withType(QAuthenticationType.FULLY_ANONYMOUS));
      qInstance.addTable(new QTableMetaData()
         .withName(SOURCE)
         .withBackendName(BACKEND)
         .withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("name", QFieldType.STRING))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME)));
      QContext.init(qInstance, new QSession());
      return (qInstance);
   }



   /*******************************************************************************
    *******************************************************************************/
   private static QuickSearchQBitConfig config()
   {
      return (new QuickSearchQBitConfig()
         .withStartupMode(QuickSearchStartupMode.DEGRADED)
         .withBackendName(BACKEND)
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("diag-meta")
         .withSearchableTable(SOURCE, List.of(new SearchableFieldConfig("name"))));
   }



   @Test
   void testProduce_addsTablesProcessesAndWidget() throws QException
   {
      QInstance qInstance = buildInstance();
      new QuickSearchQBitProducer().withConfig(config()).produce(qInstance);

      assertThat(qInstance.getTable(QuickSearchDiagnosticEvent.TABLE_NAME)).isNotNull();
      assertThat(qInstance.getTable(QuickSearchValidationRun.TABLE_NAME)).isNotNull();
      assertThat(qInstance.getTable(QuickSearchValidationRun.TABLE_NAME).getField("reportHtml").getType()).isEqualTo(QFieldType.HTML);
      for(String history : List.of(QuickSearchDiagnosticEvent.TABLE_NAME, QuickSearchValidationRun.TABLE_NAME))
      {
         assertThat(qInstance.getTable(history).getDisabledCapabilities()).as(history)
            .contains(Capability.TABLE_INSERT, Capability.TABLE_UPDATE, Capability.TABLE_DELETE);
      }

      for(String name : List.of(QuickSearchDiagnosticsProcessMetaDataHelper.PLAYGROUND_PROCESS_NAME, QuickSearchDiagnosticsProcessMetaDataHelper.VALIDATE_PROCESS_NAME,
         QuickSearchDiagnosticsProcessMetaDataHelper.ANALYZE_PROCESS_NAME))
      {
         QProcessMetaData process = qInstance.getProcess(name);
         assertThat(process).as(name).isNotNull();
         assertThat(process.getTableName()).isEqualTo(QuickSearchIndex.TABLE_NAME);
         assertThat(process.getPermissionRules().getLevel()).isEqualTo(PermissionLevel.HAS_ACCESS_PERMISSION);
         assertThat(process.getPermissionRules().getPermissionBaseName()).isEqualTo(QuickSearchQBitConfig.DEFAULT_DEBUG_PERMISSION_BASE_NAME);
      }

      QProcessMetaData inspect = qInstance.getProcess(QuickSearchDiagnosticsProcessMetaDataHelper.inspectProcessName(config(), SOURCE));
      QProcessMetaData reindex = qInstance.getProcess(QuickSearchDiagnosticsProcessMetaDataHelper.reindexRecordsProcessName(config(), SOURCE));
      assertThat(inspect.getTableName()).isEqualTo(SOURCE);
      assertThat(inspect.getPermissionRules().getPermissionBaseName()).isEqualTo(QuickSearchQBitConfig.DEFAULT_DEBUG_PERMISSION_BASE_NAME);
      assertThat(inspect.getSourceQBitName()).isNotBlank();
      assertThat(reindex.getTableName()).isEqualTo(SOURCE);
      assertThat(reindex.getPermissionRules().getPermissionBaseName()).isEqualTo(QuickSearchQBitConfig.DEFAULT_ADMIN_PERMISSION_BASE_NAME);

      assertThat(qInstance.getProcess(QuickSearchDiagnosticsProcessMetaDataHelper.inspectProcessName(config(), QuickSearchFailedEvent.TABLE_NAME)).getTableName())
         .isEqualTo(QuickSearchFailedEvent.TABLE_NAME);
      assertThat(qInstance.getProcess(QuickSearchDiagnosticsProcessMetaDataHelper.reindexRecordsProcessName(config(), QuickSearchFailedEvent.TABLE_NAME))).isNotNull();

      assertThat(qInstance.getWidget(QuickSearchDiagnosticsWidgetMetaDataProducer.WIDGET_NAME)).isNotNull();
      assertThat(qInstance.getWidget(QuickSearchDiagnosticsWidgetMetaDataProducer.WIDGET_NAME).getPermissionRules().getPermissionBaseName())
         .isEqualTo(QuickSearchQBitConfig.DEFAULT_DEBUG_PERMISSION_BASE_NAME);
   }



   /*******************************************************************************
    ** The tools are actions, never nav entries: no diagnostic process is a
    ** child of the admin app; the history tables and the widget are.
    *******************************************************************************/
   @Test
   void testProduce_toolsAreNotInTheAppNav() throws QException
   {
      QInstance qInstance = buildInstance();
      new QuickSearchQBitProducer().withConfig(config()).produce(qInstance);

      QAppMetaData app = qInstance.getApp(QuickSearchQBitProducer.APP_NAME);
      List<String> children = app.getChildren().stream().map(c -> c.getName()).toList();
      assertThat(children).contains(QuickSearchDiagnosticEvent.TABLE_NAME, QuickSearchValidationRun.TABLE_NAME);
      assertThat(children).noneMatch(n -> n.startsWith("quickSearchPlayground") || n.startsWith("quickSearchInspect") || n.startsWith("quickSearchReindexRecords")
         || n.startsWith("quickSearchValidateIndex") || n.startsWith("quickSearchAnalyzeText"));
      assertThat(app.getWidgets()).containsExactly(QuickSearchDiagnosticsWidgetMetaDataProducer.WIDGET_NAME);
   }



   @Test
   void testProduce_instancePassesValidation() throws QException
   {
      QInstance qInstance = buildInstance();
      new QuickSearchQBitProducer().withConfig(config()).produce(qInstance);
      new QInstanceValidator().revalidate(qInstance);
   }



   @Test
   void testProduce_diagnosticsOff_addsNothing() throws QException
   {
      QInstance qInstance = buildInstance();
      new QuickSearchQBitProducer().withConfig(config().withEnableDiagnosticTools(false)).produce(qInstance);

      assertThat(qInstance.getTable(QuickSearchDiagnosticEvent.TABLE_NAME)).isNull();
      assertThat(qInstance.getTable(QuickSearchValidationRun.TABLE_NAME)).isNull();
      assertThat(qInstance.getProcess(QuickSearchDiagnosticsProcessMetaDataHelper.PLAYGROUND_PROCESS_NAME)).isNull();
      assertThat(qInstance.getProcess(QuickSearchDiagnosticsProcessMetaDataHelper.inspectProcessName(config(), SOURCE))).isNull();
      assertThat(qInstance.getWidget(QuickSearchDiagnosticsWidgetMetaDataProducer.WIDGET_NAME)).isNull();
   }



   @Test
   void testProduce_recordActionsOff_keepsFixedTools() throws QException
   {
      QInstance qInstance = buildInstance();
      new QuickSearchQBitProducer().withConfig(config().withEnableRecordDiagnosticActions(false)).produce(qInstance);

      assertThat(qInstance.getProcess(QuickSearchDiagnosticsProcessMetaDataHelper.PLAYGROUND_PROCESS_NAME)).isNotNull();
      assertThat(qInstance.getProcess(QuickSearchDiagnosticsProcessMetaDataHelper.inspectProcessName(config(), SOURCE))).isNull();
      assertThat(qInstance.getProcess(QuickSearchDiagnosticsProcessMetaDataHelper.reindexRecordsProcessName(config(), SOURCE))).isNull();
   }



   @Test
   void testProduce_validationCron_schedulesTheValidation() throws QException
   {
      QInstance qInstance = buildInstance();
      QSchedulerMetaData scheduler = new QuartzSchedulerMetaData().withName("simple");
      qInstance.addScheduler(scheduler);
      new QuickSearchQBitProducer().withConfig(config().withSchedulerName("simple").withValidationCronExpression("0 0 4 * * ?").withValidationCronTimeZoneId("UTC")).produce(qInstance);

      QProcessMetaData validate = qInstance.getProcess(QuickSearchDiagnosticsProcessMetaDataHelper.VALIDATE_PROCESS_NAME);
      assertThat(validate.getSchedule()).isNotNull();
      assertThat(validate.getSchedule().getCronExpression()).isEqualTo("0 0 4 * * ?");
      assertThat(validate.getSchedule().getCronTimeZoneId()).isEqualTo("UTC");
   }



   @Test
   void testValidate_validationCronNeedsSchedulerAndTools()
   {
      QInstance qInstance = buildInstance();
      assertThatThrownBy(() -> new QuickSearchQBitProducer().withConfig(config().withValidationCronExpression("0 0 4 * * ?")).produce(qInstance))
         .hasMessageContaining("validationCronExpression requires schedulerName");

      QInstance second = buildInstance();
      second.addScheduler(new QuartzSchedulerMetaData().withName("simple"));
      assertThatThrownBy(() -> new QuickSearchQBitProducer().withConfig(config().withSchedulerName("simple").withValidationCronExpression("0 0 4 * * ?").withValidationCronTimeZoneId("UTC")
            .withEnableDiagnosticTools(false))
         .produce(second))
         .hasMessageContaining("validationCronExpression requires enableDiagnosticTools");
   }



   /*******************************************************************************
    ** The validation schedule follows the same rules as the reconcile one: a
    ** cron-capable scheduler, a valid Quartz expression and a time zone.
    *******************************************************************************/
   @Test
   void testValidate_validationCronRules()
   {
      QInstance simple = buildInstance();
      simple.addScheduler(new SimpleSchedulerMetaData().withName("simple"));
      assertThatThrownBy(() -> new QuickSearchQBitProducer().withConfig(config().withSchedulerName("simple").withValidationCronExpression("0 0 4 * * ?").withValidationCronTimeZoneId("UTC"))
         .produce(simple))
         .hasMessageContaining("validationCronExpression requires a scheduler that supports cron schedules");

      QInstance quartz = buildInstance();
      quartz.addScheduler(new QuartzSchedulerMetaData().withName("quartz"));
      assertThatThrownBy(() -> new QuickSearchQBitProducer().withConfig(config().withSchedulerName("quartz").withValidationCronExpression("0 0 4 * *")).produce(quartz))
         .hasMessageContaining("validationCronExpression [0 0 4 * *] is not a valid Quartz cron expression")
         .hasMessageContaining("validationCronExpression requires validationCronTimeZoneId");

      QInstance zone = buildInstance();
      zone.addScheduler(new QuartzSchedulerMetaData().withName("quartz"));
      assertThatThrownBy(() -> new QuickSearchQBitProducer().withConfig(config().withSchedulerName("quartz").withValidationCronExpression("0 0 4 * * ?").withValidationCronTimeZoneId("Mars/Base"))
         .produce(zone))
         .hasMessageContaining("validationCronTimeZoneId [Mars/Base] is not a recognized time zone ID");
   }



   @Test
   void testValidate_sampleSizeAndRateBounds()
   {
      assertThatThrownBy(() -> new QuickSearchQBitProducer().withConfig(config().withValidationSampleSize(0)).produce(buildInstance()))
         .hasMessageContaining("validationSampleSize must be positive");
      assertThatThrownBy(() -> new QuickSearchQBitProducer().withConfig(config().withValidationSampleSize(5000)).produce(buildInstance()))
         .hasMessageContaining("validationSampleSize must be at most");
      assertThatThrownBy(() -> new QuickSearchQBitProducer().withConfig(config().withDiagnosticRunsPerMinute(-1)).produce(buildInstance()))
         .hasMessageContaining("diagnosticRunsPerMinute must be positive");
   }



   @Test
   void testProduce_prefixAppliesToToolsAndTables() throws QException
   {
      QInstance             qInstance = buildInstance();
      QuickSearchQBitConfig config    = config().withTableNamePrefix("myApp_");
      new QuickSearchQBitProducer().withConfig(config).produce(qInstance);

      assertThat(qInstance.getTable("myApp_" + QuickSearchDiagnosticEvent.TABLE_NAME)).isNotNull();
      assertThat(qInstance.getTable("myApp_" + QuickSearchValidationRun.TABLE_NAME)).isNotNull();
      QProcessMetaData playground = qInstance.getProcess("myApp_" + QuickSearchDiagnosticsProcessMetaDataHelper.PLAYGROUND_PROCESS_NAME);
      assertThat(playground.getTableName()).isEqualTo("myApp_" + QuickSearchIndex.TABLE_NAME);
      assertThat(qInstance.getProcess("myApp_quickSearchInspect_" + SOURCE)).isNotNull();
   }

}

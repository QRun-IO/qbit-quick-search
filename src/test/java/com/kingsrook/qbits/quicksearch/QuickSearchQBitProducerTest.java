/*
 * Copyright 2024 Kingsrook, LLC
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
import java.util.Map;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
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
import com.kingsrook.qbits.quicksearch.annotations.QuickSearchField;
import com.kingsrook.qbits.quicksearch.annotations.QuickSearchable;
import com.kingsrook.qbits.quicksearch.customizers.QuickSearchPostDeleteCustomizer;
import com.kingsrook.qbits.quicksearch.customizers.QuickSearchPostInsertCustomizer;
import com.kingsrook.qbits.quicksearch.customizers.QuickSearchPostUpdateCustomizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Tests for QuickSearchQBitProducer.
 **
 ** Note: OpenSearch client creation will fail in these tests because no
 ** OpenSearch instance is running. The producer is designed to log a warning
 ** and continue in this case, so we can still verify metadata production.
 *******************************************************************************/
class QuickSearchQBitProducerTest
{
   private static final String BACKEND_NAME = "memory";
   private static final String SOURCE_TABLE = "person";



   /***************************************************************************
    ** Test annotated entity: person table with @QuickSearchField annotations.
    ***************************************************************************/
   @QuickSearchable(tableName = "person", basepullIntervalMinutes = 10, basepullTimestampField = "modifyDate")
   static class PersonEntity
   {
      @QuickSearchField(weight = 3, includeLabel = true)
      private String firstName;

      @QuickSearchField(weight = 2)
      private String lastName;

      @QuickSearchField
      private String email;

      private String internalField;
   }



   /***************************************************************************
    ** Test annotated entity using fields() array instead of @QuickSearchField.
    ***************************************************************************/
   @QuickSearchable(tableName = "order", fields = {"orderNumber", "customerName"}, basepullIntervalMinutes = 15)
   static class OrderEntity
   {
      private String orderNumber;
      private String customerName;
   }



   /***************************************************************************
    ** Entity class without @QuickSearchable annotation.
    ***************************************************************************/
   static class UnannotatedEntity
   {
      private String name;
   }



   @BeforeEach
   void setUp()
   {
      MemoryRecordStore.getInstance().reset();
      QuickSearchQBitContext.clear();
   }



   @AfterEach
   void tearDown()
   {
      QContext.clear();
      QuickSearchQBitContext.clear();
   }



   /***************************************************************************
    ** Test that produce creates operational tables in the QInstance.
    ***************************************************************************/
   @Test
   void testProduce_createsOperationalTables() throws QException
   {
      QInstance qInstance = buildQInstance();
      QContext.init(qInstance, new QSession());

      QuickSearchQBitProducer producer = new QuickSearchQBitProducer()
         .withConfig(buildValidConfig());

      producer.produce(qInstance);

      assertThat(qInstance.getTable("quickSearchIndex")).isNotNull();
      assertThat(qInstance.getTable("quickSearchIndex").getBackendName()).isEqualTo(BACKEND_NAME);
      assertThat(qInstance.getTable("quickSearchIndex").getPrimaryKeyField()).isEqualTo("id");

      assertThat(qInstance.getTable("quickSearchIndexRun")).isNotNull();
      assertThat(qInstance.getTable("quickSearchIndexRun").getBackendName()).isEqualTo(BACKEND_NAME);
      assertThat(qInstance.getTable("quickSearchIndexRun").getPrimaryKeyField()).isEqualTo("id");
   }



   /***************************************************************************
    ** Test that tableNamePrefix is applied to operational table names.
    ***************************************************************************/
   @Test
   void testProduce_tableNamePrefixApplied() throws QException
   {
      QInstance qInstance = buildQInstance();
      QContext.init(qInstance, new QSession());

      QuickSearchQBitConfig config = buildValidConfig()
         .withTableNamePrefix("myApp_");

      QuickSearchQBitProducer producer = new QuickSearchQBitProducer()
         .withConfig(config);

      producer.produce(qInstance);

      assertThat(qInstance.getTable("myApp_quickSearchIndex")).isNotNull();
      assertThat(qInstance.getTable("myApp_quickSearchIndexRun")).isNotNull();
      assertThat(qInstance.getProcess("myApp_" + QuickSearchQBitProducer.BASEPULL_PROCESS_NAME)).isNotNull();
      assertThat(qInstance.getProcess("myApp_" + QuickSearchQBitProducer.FULL_REINDEX_PROCESS_NAME)).isNotNull();
   }



   /***************************************************************************
    ** Test annotation discovery extracts correct field names and weights.
    ***************************************************************************/
   @Test
   void testAnnotationDiscovery_fieldWeightsAndLabels() throws QException
   {
      QInstance qInstance = buildQInstance();
      QContext.init(qInstance, new QSession());

      QuickSearchQBitProducer producer = new QuickSearchQBitProducer()
         .withConfig(buildValidConfig());

      List<QuickSearchableTableConfig> discovered = producer.discoverSearchableTables(qInstance);

      assertThat(discovered).hasSize(1);
      QuickSearchableTableConfig personConfig = discovered.get(0);

      assertThat(personConfig.getTableName()).isEqualTo("person");
      assertThat(personConfig.getPrimaryKeyField()).isEqualTo("id");
      assertThat(personConfig.getBasepullIntervalMinutes()).isEqualTo(10);
      assertThat(personConfig.getBasepullTimestampField()).isEqualTo("modifyDate");
      assertThat(personConfig.getEnabledByDefault()).isTrue();

      assertThat(personConfig.getSearchableFields())
         .containsExactly("firstName", "lastName", "email");

      assertThat(personConfig.getFieldWeights())
         .containsEntry("firstName", 3)
         .containsEntry("lastName", 2)
         .containsEntry("email", 1);

      assertThat(personConfig.getFieldIncludeLabels())
         .containsEntry("firstName", true)
         .containsEntry("lastName", false)
         .containsEntry("email", false);
   }



   /***************************************************************************
    ** Test annotation discovery falls back to fields() array when no
    ** @QuickSearchField annotations are found.
    ***************************************************************************/
   @Test
   void testAnnotationDiscovery_fieldsArrayFallback() throws QException
   {
      QInstance qInstance = buildQInstance();
      addOrderTable(qInstance);
      QContext.init(qInstance, new QSession());

      QuickSearchQBitConfig config = buildValidConfig()
         .withSearchableEntityClasses(List.of(OrderEntity.class));

      QuickSearchQBitProducer producer = new QuickSearchQBitProducer()
         .withConfig(config);

      List<QuickSearchableTableConfig> discovered = producer.discoverSearchableTables(qInstance);

      assertThat(discovered).hasSize(1);
      QuickSearchableTableConfig orderConfig = discovered.get(0);

      assertThat(orderConfig.getTableName()).isEqualTo("order");
      assertThat(orderConfig.getSearchableFields())
         .containsExactly("orderNumber", "customerName");
      assertThat(orderConfig.getFieldWeights())
         .containsEntry("orderNumber", 1)
         .containsEntry("customerName", 1);
      assertThat(orderConfig.getBasepullIntervalMinutes()).isEqualTo(15);
   }



   /***************************************************************************
    ** Test that unannotated classes are skipped during discovery.
    ***************************************************************************/
   @Test
   void testAnnotationDiscovery_unannotatedClassSkipped() throws QException
   {
      QInstance qInstance = buildQInstance();
      QContext.init(qInstance, new QSession());

      QuickSearchQBitConfig config = buildValidConfig()
         .withSearchableEntityClasses(List.of(UnannotatedEntity.class));

      QuickSearchQBitProducer producer = new QuickSearchQBitProducer()
         .withConfig(config);

      List<QuickSearchableTableConfig> discovered = producer.discoverSearchableTables(qInstance);

      assertThat(discovered).isEmpty();
   }



   /***************************************************************************
    ** Test that produce registers post-insert/update/delete customizers
    ** on source tables when enableRealTimeIndexing is true.
    ***************************************************************************/
   @Test
   void testProduce_registersCustomizers() throws QException
   {
      QInstance qInstance = buildQInstance();
      QContext.init(qInstance, new QSession());

      QuickSearchQBitConfig config = buildValidConfig()
         .withEnableRealTimeIndexing(true);

      QuickSearchQBitProducer producer = new QuickSearchQBitProducer()
         .withConfig(config);

      producer.produce(qInstance);

      QTableMetaData personTable = qInstance.getTable(SOURCE_TABLE);
      assertThat(personTable).isNotNull();

      Map<String, com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference> customizers =
         personTable.getCustomizers();
      assertThat(customizers).isNotNull();
      assertThat(customizers).containsKey("postInsertRecord");
      assertThat(customizers).containsKey("postUpdateRecord");
      assertThat(customizers).containsKey("postDeleteRecord");

      assertThat(customizers.get("postInsertRecord").getName())
         .isEqualTo(QuickSearchPostInsertCustomizer.class.getName());
      assertThat(customizers.get("postUpdateRecord").getName())
         .isEqualTo(QuickSearchPostUpdateCustomizer.class.getName());
      assertThat(customizers.get("postDeleteRecord").getName())
         .isEqualTo(QuickSearchPostDeleteCustomizer.class.getName());
   }



   /***************************************************************************
    ** Test that no customizers are registered when enableRealTimeIndexing
    ** is false.
    ***************************************************************************/
   @Test
   void testProduce_realTimeIndexingDisabled_noCustomizers() throws QException
   {
      QInstance qInstance = buildQInstance();
      QContext.init(qInstance, new QSession());

      QuickSearchQBitConfig config = buildValidConfig()
         .withEnableRealTimeIndexing(false);

      QuickSearchQBitProducer producer = new QuickSearchQBitProducer()
         .withConfig(config);

      producer.produce(qInstance);

      QTableMetaData personTable = qInstance.getTable(SOURCE_TABLE);
      assertThat(personTable).isNotNull();

      Map<String, com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference> customizers =
         personTable.getCustomizers();
      assertThat(customizers == null || customizers.isEmpty()).isTrue();
   }



   /***************************************************************************
    ** Test that produce creates processes in the QInstance.
    ***************************************************************************/
   @Test
   void testProduce_createsProcesses() throws QException
   {
      QInstance qInstance = buildQInstance();
      QContext.init(qInstance, new QSession());

      QuickSearchQBitProducer producer = new QuickSearchQBitProducer()
         .withConfig(buildValidConfig());

      producer.produce(qInstance);

      assertThat(qInstance.getProcess(QuickSearchQBitProducer.BASEPULL_PROCESS_NAME)).isNotNull();
      assertThat(qInstance.getProcess(QuickSearchQBitProducer.FULL_REINDEX_PROCESS_NAME)).isNotNull();
   }



   /***************************************************************************
    ** Test that produce creates an admin app in the QInstance.
    ***************************************************************************/
   @Test
   void testProduce_createsAdminApp() throws QException
   {
      QInstance qInstance = buildQInstance();
      QContext.init(qInstance, new QSession());

      QuickSearchQBitProducer producer = new QuickSearchQBitProducer()
         .withConfig(buildValidConfig());

      producer.produce(qInstance);

      assertThat(qInstance.getApp(QuickSearchQBitProducer.APP_NAME)).isNotNull();
      assertThat(qInstance.getApp(QuickSearchQBitProducer.APP_NAME).getLabel())
         .isEqualTo("Quick Search Admin");
   }



   /***************************************************************************
    ** Test that produce populates QuickSearchQBitContext with discovered
    ** tables and config.
    ***************************************************************************/
   @Test
   void testProduce_populatesContext() throws QException
   {
      QInstance qInstance = buildQInstance();
      QContext.init(qInstance, new QSession());

      QuickSearchQBitConfig config = buildValidConfig();

      QuickSearchQBitProducer producer = new QuickSearchQBitProducer()
         .withConfig(config);

      producer.produce(qInstance);

      assertThat(QuickSearchQBitContext.getConfig()).isSameAs(config);
      assertThat(QuickSearchQBitContext.getDiscoveredTables()).isNotNull();
      assertThat(QuickSearchQBitContext.getDiscoveredTables()).hasSize(1);
      assertThat(QuickSearchQBitContext.getDiscoveredTables().get(0).getTableName()).isEqualTo("person");
   }



   /***************************************************************************
    ** Test that validation failure throws an exception with all error messages.
    ***************************************************************************/
   @Test
   void testProduce_validationFailure_throwsWithMessages()
   {
      QInstance qInstance = buildQInstance();
      QContext.init(qInstance, new QSession());

      QuickSearchQBitConfig config = new QuickSearchQBitConfig();

      QuickSearchQBitProducer producer = new QuickSearchQBitProducer()
         .withConfig(config);

      assertThatThrownBy(() -> producer.produce(qInstance))
         .isInstanceOf(QException.class)
         .hasMessageContaining("validation failed")
         .hasMessageContaining("backendName is required");
   }



   /***************************************************************************
    ** Test the fluent withConfig setter returns this.
    ***************************************************************************/
   @Test
   void testFluentSetter_returnsThis()
   {
      QuickSearchQBitProducer producer = new QuickSearchQBitProducer();
      QuickSearchQBitConfig config = new QuickSearchQBitConfig();

      assertThat(producer.withConfig(config)).isSameAs(producer);
      assertThat(producer.getConfig()).isSameAs(config);
   }



   /***************************************************************************
    ** Test that the operational tables have all expected fields from the
    ** QuickSearchIndex and QuickSearchIndexRun entities.
    ***************************************************************************/
   @Test
   void testProduce_operationalTableFields() throws QException
   {
      QInstance qInstance = buildQInstance();
      QContext.init(qInstance, new QSession());

      QuickSearchQBitProducer producer = new QuickSearchQBitProducer()
         .withConfig(buildValidConfig());

      producer.produce(qInstance);

      QTableMetaData indexTable = qInstance.getTable("quickSearchIndex");
      assertThat(indexTable.getFields()).containsKey("id");
      assertThat(indexTable.getFields()).containsKey("tableName");
      assertThat(indexTable.getFields()).containsKey("enabled");
      assertThat(indexTable.getFields()).containsKey("basepullIntervalMinutes");
      assertThat(indexTable.getFields()).containsKey("basepullTimestampField");
      assertThat(indexTable.getFields()).containsKey("searchableFieldsJson");
      assertThat(indexTable.getFields()).containsKey("lastBasepullTime");
      assertThat(indexTable.getFields()).containsKey("lastFullReindexTime");
      assertThat(indexTable.getFields()).containsKey("recordCount");
      assertThat(indexTable.getFields()).containsKey("status");
      assertThat(indexTable.getFields()).containsKey("createDate");
      assertThat(indexTable.getFields()).containsKey("modifyDate");

      QTableMetaData indexRunTable = qInstance.getTable("quickSearchIndexRun");
      assertThat(indexRunTable.getFields()).containsKey("id");
      assertThat(indexRunTable.getFields()).containsKey("quickSearchIndexId");
      assertThat(indexRunTable.getFields()).containsKey("runType");
      assertThat(indexRunTable.getFields()).containsKey("status");
      assertThat(indexRunTable.getFields()).containsKey("startTime");
      assertThat(indexRunTable.getFields()).containsKey("endTime");
      assertThat(indexRunTable.getFields()).containsKey("recordsProcessed");
      assertThat(indexRunTable.getFields()).containsKey("recordsIndexed");
      assertThat(indexRunTable.getFields()).containsKey("errorCount");
      assertThat(indexRunTable.getFields()).containsKey("errorMessage");
      assertThat(indexRunTable.getFields()).containsKey("createDate");
      assertThat(indexRunTable.getFields()).containsKey("modifyDate");
   }



   /***************************************************************************
    ** Test that config-driven-only produce discovers tables from
    ** searchableTables config.
    ***************************************************************************/
   @Test
   void testProduce_configDrivenOnly_discoversTable() throws QException
   {
      QInstance qInstance = buildQInstance();
      QContext.init(qInstance, new QSession());

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName(BACKEND_NAME)
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-config-driven")
         .withSearchableTable(SOURCE_TABLE, List.of(
            new SearchableFieldConfig("firstName").withWeight(2),
            new SearchableFieldConfig("lastName"),
            new SearchableFieldConfig("email").withWeight(3)));

      QuickSearchQBitProducer producer = new QuickSearchQBitProducer()
         .withConfig(config);

      producer.produce(qInstance);

      assertThat(QuickSearchQBitContext.getDiscoveredTables()).hasSize(1);
      assertThat(QuickSearchQBitContext.getDiscoveredTables().get(0).getTableName()).isEqualTo(SOURCE_TABLE);
      assertThat(QuickSearchQBitContext.getDiscoveredTables().get(0).getSearchableFields())
         .containsExactly("firstName", "lastName", "email");
   }



   /***************************************************************************
    ** Test that config-driven tables register customizers on source tables.
    ***************************************************************************/
   @Test
   void testProduce_configDriven_registersCustomizers() throws QException
   {
      QInstance qInstance = buildQInstance();
      QContext.init(qInstance, new QSession());

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName(BACKEND_NAME)
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-config-driven")
         .withEnableRealTimeIndexing(true)
         .withSearchableTable(SOURCE_TABLE, List.of(new SearchableFieldConfig("firstName")));

      QuickSearchQBitProducer producer = new QuickSearchQBitProducer()
         .withConfig(config);

      producer.produce(qInstance);

      QTableMetaData personTable = qInstance.getTable(SOURCE_TABLE);
      Map<String, com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference> customizers =
         personTable.getCustomizers();

      assertThat(customizers).containsKey("postInsertRecord");
      assertThat(customizers).containsKey("postUpdateRecord");
      assertThat(customizers).containsKey("postDeleteRecord");
   }



   /***************************************************************************
    ** Test mixed mode: both annotation-based and config-driven tables
    ** are discovered and merged.
    ***************************************************************************/
   @Test
   void testProduce_mixedMode_bothSourcesMerged() throws QException
   {
      QInstance qInstance = buildQInstance();
      addOrderTable(qInstance);
      QContext.init(qInstance, new QSession());

      QuickSearchQBitConfig config = buildValidConfig()
         .withSearchableTable("order", List.of(
            new SearchableFieldConfig("orderNumber").withWeight(2),
            new SearchableFieldConfig("customerName")));

      QuickSearchQBitProducer producer = new QuickSearchQBitProducer()
         .withConfig(config);

      producer.produce(qInstance);

      assertThat(QuickSearchQBitContext.getDiscoveredTables()).hasSize(2);
      assertThat(QuickSearchQBitContext.getDiscoveredTables())
         .extracting(QuickSearchableTableConfig::getTableName)
         .containsExactlyInAnyOrder("person", "order");
   }



   /***************************************************************************
    ** Test that duplicate table name across annotation and config-driven
    ** sources throws an exception.
    ***************************************************************************/
   @Test
   void testProduce_duplicateTableName_throws()
   {
      QInstance qInstance = buildQInstance();
      QContext.init(qInstance, new QSession());

      QuickSearchQBitConfig config = buildValidConfig()
         .withSearchableTable(SOURCE_TABLE, List.of(
            new SearchableFieldConfig("firstName")));

      QuickSearchQBitProducer producer = new QuickSearchQBitProducer()
         .withConfig(config);

      assertThatThrownBy(() -> producer.produce(qInstance))
         .isInstanceOf(QException.class)
         .hasMessageContaining("Duplicate table name")
         .hasMessageContaining(SOURCE_TABLE);
   }



   /***************************************************************************
    ** Test that config-driven table with null basepullIntervalMinutes
    ** inherits the default from config.
    ***************************************************************************/
   @Test
   void testProduce_configDriven_defaultsApplied() throws QException
   {
      QInstance qInstance = buildQInstance();
      QContext.init(qInstance, new QSession());

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName(BACKEND_NAME)
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-config-driven")
         .withDefaultBasepullIntervalMinutes(7)
         .withSearchableTable(SOURCE_TABLE, List.of(new SearchableFieldConfig("firstName")));

      QuickSearchQBitProducer producer = new QuickSearchQBitProducer()
         .withConfig(config);

      producer.produce(qInstance);

      QuickSearchableTableConfig tableConfig = QuickSearchQBitContext.getDiscoveredTables().get(0);
      assertThat(tableConfig.getBasepullIntervalMinutes()).isEqualTo(7);
      assertThat(tableConfig.getBasepullTimestampField()).isEqualTo("modifyDate");
      assertThat(tableConfig.getEnabledByDefault()).isTrue();
   }



   /***************************************************************************
    ** Test that config-driven table with explicit basepullIntervalMinutes
    ** preserves it (does not override with default).
    ***************************************************************************/
   @Test
   void testProduce_configDriven_explicitIntervalPreserved() throws QException
   {
      QInstance qInstance = buildQInstance();
      QContext.init(qInstance, new QSession());

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName(BACKEND_NAME)
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-config-driven")
         .withDefaultBasepullIntervalMinutes(5)
         .withSearchableTable(new SearchableTableConfig(SOURCE_TABLE,
            List.of(new SearchableFieldConfig("firstName")))
            .withBasepullIntervalMinutes(30));

      QuickSearchQBitProducer producer = new QuickSearchQBitProducer()
         .withConfig(config);

      producer.produce(qInstance);

      QuickSearchableTableConfig tableConfig = QuickSearchQBitContext.getDiscoveredTables().get(0);
      assertThat(tableConfig.getBasepullIntervalMinutes()).isEqualTo(30);
   }



   /***************************************************************************
    ** Test that config-driven table resolves primaryKeyField from QInstance
    ** table metadata.
    ***************************************************************************/
   @Test
   void testProduce_configDriven_primaryKeyFromQInstance() throws QException
   {
      QInstance qInstance = buildQInstance();
      QContext.init(qInstance, new QSession());

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName(BACKEND_NAME)
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-config-driven")
         .withSearchableTable(SOURCE_TABLE, List.of(new SearchableFieldConfig("firstName")));

      QuickSearchQBitProducer producer = new QuickSearchQBitProducer()
         .withConfig(config);

      producer.produce(qInstance);

      QuickSearchableTableConfig tableConfig = QuickSearchQBitContext.getDiscoveredTables().get(0);
      assertThat(tableConfig.getPrimaryKeyField()).isEqualTo("id");
   }



   /***************************************************************************
    ** Test that config-driven table for a table not in QInstance falls back
    ** to "id" as primary key.
    ***************************************************************************/
   @Test
   void testProduce_configDriven_primaryKeyFallbackToId() throws QException
   {
      QInstance qInstance = buildQInstance();
      QContext.init(qInstance, new QSession());

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName(BACKEND_NAME)
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-config-driven")
         .withEnableRealTimeIndexing(false)
         .withSearchableTable("unknownTable", List.of(new SearchableFieldConfig("name")));

      QuickSearchQBitProducer producer = new QuickSearchQBitProducer()
         .withConfig(config);

      producer.produce(qInstance);

      QuickSearchableTableConfig tableConfig = QuickSearchQBitContext.getDiscoveredTables().get(0);
      assertThat(tableConfig.getPrimaryKeyField()).isEqualTo("id");
   }



   /***************************************************************************
    ** Test that config-driven table transfers recordLabelFormat and
    ** recordLabelFields to QuickSearchableTableConfig.
    ***************************************************************************/
   @Test
   void testProduce_configDriven_recordLabelFormatTransferred() throws QException
   {
      QInstance qInstance = buildQInstance();
      QContext.init(qInstance, new QSession());

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName(BACKEND_NAME)
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-config-driven")
         .withSearchableTable(new SearchableTableConfig(SOURCE_TABLE,
            List.of(new SearchableFieldConfig("firstName"), new SearchableFieldConfig("lastName")))
            .withRecordLabelFormat("%s %s", "firstName", "lastName"));

      QuickSearchQBitProducer producer = new QuickSearchQBitProducer()
         .withConfig(config);

      producer.produce(qInstance);

      QuickSearchableTableConfig tableConfig = QuickSearchQBitContext.getDiscoveredTables().get(0);
      assertThat(tableConfig.getRecordLabelFormat()).isEqualTo("%s %s");
      assertThat(tableConfig.getRecordLabelFields()).containsExactly("firstName", "lastName");
   }



   /***************************************************************************
    ** Test that config-driven field weights and includeLabels are mapped
    ** correctly to QuickSearchableTableConfig maps.
    ***************************************************************************/
   @Test
   void testProduce_configDriven_fieldWeightsAndLabelsMapped() throws QException
   {
      QInstance qInstance = buildQInstance();
      QContext.init(qInstance, new QSession());

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName(BACKEND_NAME)
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-config-driven")
         .withSearchableTable(SOURCE_TABLE, List.of(
            new SearchableFieldConfig("firstName").withWeight(3).withIncludeLabel(true),
            new SearchableFieldConfig("lastName").withWeight(2),
            new SearchableFieldConfig("email")));

      QuickSearchQBitProducer producer = new QuickSearchQBitProducer()
         .withConfig(config);

      producer.produce(qInstance);

      QuickSearchableTableConfig tableConfig = QuickSearchQBitContext.getDiscoveredTables().get(0);
      assertThat(tableConfig.getFieldWeights())
         .containsEntry("firstName", 3)
         .containsEntry("lastName", 2)
         .containsEntry("email", 1);
      assertThat(tableConfig.getFieldIncludeLabels())
         .containsEntry("firstName", true)
         .containsEntry("lastName", false)
         .containsEntry("email", false);
   }



   /***************************************************************************
    ** Build a base QInstance with a memory backend and source tables.
    ***************************************************************************/
   private QInstance buildQInstance()
   {
      QInstance qInstance = new QInstance();

      qInstance.addBackend(new QBackendMetaData()
         .withName(BACKEND_NAME)
         .withBackendType(MemoryBackendModule.class));

      qInstance.setAuthentication(new QAuthenticationMetaData()
         .withName("anonymous")
         .withType(QAuthenticationType.FULLY_ANONYMOUS));

      qInstance.addTable(new QTableMetaData()
         .withName(SOURCE_TABLE)
         .withBackendName(BACKEND_NAME)
         .withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("firstName", QFieldType.STRING))
         .withField(new QFieldMetaData("lastName", QFieldType.STRING))
         .withField(new QFieldMetaData("email", QFieldType.STRING))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME)));

      return (qInstance);
   }



   /***************************************************************************
    ** Add an order table to the QInstance.
    ***************************************************************************/
   private void addOrderTable(QInstance qInstance)
   {
      qInstance.addTable(new QTableMetaData()
         .withName("order")
         .withBackendName(BACKEND_NAME)
         .withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("orderNumber", QFieldType.STRING))
         .withField(new QFieldMetaData("customerName", QFieldType.STRING))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME)));
   }



   /***************************************************************************
    ** Build a valid QuickSearchQBitConfig for testing.
    ***************************************************************************/
   private QuickSearchQBitConfig buildValidConfig()
   {
      return new QuickSearchQBitConfig()
         .withBackendName(BACKEND_NAME)
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-quick-search")
         .withSearchableEntityClasses(List.of(PersonEntity.class));
   }

}

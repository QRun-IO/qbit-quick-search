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
import com.kingsrook.qbits.quicksearch.annotations.QuickSearchField;
import com.kingsrook.qbits.quicksearch.annotations.QuickSearchable;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;


/*******************************************************************************
 ** Tests for QuickSearchQBitProducer.
 *******************************************************************************/
class QuickSearchQBitProducerTest
{
   private static final String BACKEND_NAME = "memory";
   private static final String NAMESPACE    = "testNamespace";



   /***************************************************************************
    ** Test produce with invalid config throws exception.
    ***************************************************************************/
   @Test
   void testProduce_invalidConfig_throwsException()
   {
      QInstance qInstance = createBaseQInstance();

      QuickSearchQBitConfig config = new QuickSearchQBitConfig();
      QuickSearchQBitProducer producer = new QuickSearchQBitProducer().withConfig(config);

      assertThatThrownBy(() -> producer.produce(qInstance, NAMESPACE))
         .isInstanceOf(QException.class)
         .hasMessageContaining("configuration errors");
   }



   /***************************************************************************
    ** Test produce stores config in context.
    ***************************************************************************/
   @Test
   void testProduce_validConfig_storesConfigInContext() throws Exception
   {
      QInstance qInstance = createBaseQInstance();
      QuickSearchQBitConfig config = createValidConfig();
      QuickSearchQBitProducer producer = new QuickSearchQBitProducer().withConfig(config);

      try(MockedConstruction<QuickSearchOpenSearchClient> mocked = Mockito.mockConstruction(
         QuickSearchOpenSearchClient.class,
         (mock, context) ->
         {
            doNothing().when(mock).ensureIndexExists();
            doNothing().when(mock).close();
         }))
      {
         producer.produce(qInstance, NAMESPACE);

         assertThat(QuickSearchQBitContext.getConfig()).isSameAs(config);
      }
   }



   /***************************************************************************
    ** Test produce creates index table.
    ***************************************************************************/
   @Test
   void testProduce_validConfig_createsIndexTable() throws Exception
   {
      QInstance qInstance = createBaseQInstance();
      QuickSearchQBitConfig config = createValidConfig();
      QuickSearchQBitProducer producer = new QuickSearchQBitProducer().withConfig(config);

      try(MockedConstruction<QuickSearchOpenSearchClient> mocked = Mockito.mockConstruction(
         QuickSearchOpenSearchClient.class,
         (mock, context) ->
         {
            doNothing().when(mock).ensureIndexExists();
            doNothing().when(mock).close();
         }))
      {
         producer.produce(qInstance, NAMESPACE);

         QTableMetaData indexTable = qInstance.getTable(QuickSearchIndex.TABLE_NAME);
         assertThat(indexTable).isNotNull();
         assertThat(indexTable.getField("tableName")).isNotNull();
         assertThat(indexTable.getField("isEnabled")).isNotNull();
         assertThat(indexTable.getField("lastFullIndexTime")).isNotNull();
      }
   }



   /***************************************************************************
    ** Test produce creates index run table.
    ***************************************************************************/
   @Test
   void testProduce_validConfig_createsIndexRunTable() throws Exception
   {
      QInstance qInstance = createBaseQInstance();
      QuickSearchQBitConfig config = createValidConfig();
      QuickSearchQBitProducer producer = new QuickSearchQBitProducer().withConfig(config);

      try(MockedConstruction<QuickSearchOpenSearchClient> mocked = Mockito.mockConstruction(
         QuickSearchOpenSearchClient.class,
         (mock, context) ->
         {
            doNothing().when(mock).ensureIndexExists();
            doNothing().when(mock).close();
         }))
      {
         producer.produce(qInstance, NAMESPACE);

         QTableMetaData runTable = qInstance.getTable(QuickSearchIndexRun.TABLE_NAME);
         assertThat(runTable).isNotNull();
         assertThat(runTable.getField("runType")).isNotNull();
         assertThat(runTable.getField("status")).isNotNull();
         assertThat(runTable.getField("recordsIndexed")).isNotNull();
      }
   }



   /***************************************************************************
    ** Test produce creates processes.
    ***************************************************************************/
   @Test
   void testProduce_validConfig_createsProcesses() throws Exception
   {
      QInstance qInstance = createBaseQInstance();
      QuickSearchQBitConfig config = createValidConfig();
      QuickSearchQBitProducer producer = new QuickSearchQBitProducer().withConfig(config);

      try(MockedConstruction<QuickSearchOpenSearchClient> mocked = Mockito.mockConstruction(
         QuickSearchOpenSearchClient.class,
         (mock, context) ->
         {
            doNothing().when(mock).ensureIndexExists();
            doNothing().when(mock).close();
         }))
      {
         producer.produce(qInstance, NAMESPACE);

         assertThat(qInstance.getProcess(QuickSearchQBitProducer.FULL_REINDEX_PROCESS_NAME)).isNotNull();
         assertThat(qInstance.getProcess(QuickSearchQBitProducer.BASEPULL_PROCESS_NAME)).isNotNull();
      }
   }



   /***************************************************************************
    ** Test produce creates app.
    ***************************************************************************/
   @Test
   void testProduce_validConfig_createsApp() throws Exception
   {
      QInstance qInstance = createBaseQInstance();
      QuickSearchQBitConfig config = createValidConfig();
      QuickSearchQBitProducer producer = new QuickSearchQBitProducer().withConfig(config);

      try(MockedConstruction<QuickSearchOpenSearchClient> mocked = Mockito.mockConstruction(
         QuickSearchOpenSearchClient.class,
         (mock, context) ->
         {
            doNothing().when(mock).ensureIndexExists();
            doNothing().when(mock).close();
         }))
      {
         producer.produce(qInstance, NAMESPACE);

         assertThat(qInstance.getApp(QuickSearchQBitProducer.APP_NAME)).isNotNull();
      }
   }



   /***************************************************************************
    ** Test produce with table prefix applies prefix to tables.
    ***************************************************************************/
   @Test
   void testProduce_withTablePrefix_appliesPrefix() throws Exception
   {
      QInstance qInstance = createBaseQInstance();
      QuickSearchQBitConfig config = createValidConfig()
         .withTableNamePrefix("myApp_");
      QuickSearchQBitProducer producer = new QuickSearchQBitProducer().withConfig(config);

      try(MockedConstruction<QuickSearchOpenSearchClient> mocked = Mockito.mockConstruction(
         QuickSearchOpenSearchClient.class,
         (mock, context) ->
         {
            doNothing().when(mock).ensureIndexExists();
            doNothing().when(mock).close();
         }))
      {
         producer.produce(qInstance, NAMESPACE);

         assertThat(qInstance.getTable("myApp_" + QuickSearchIndex.TABLE_NAME)).isNotNull();
         assertThat(qInstance.getTable("myApp_" + QuickSearchIndexRun.TABLE_NAME)).isNotNull();
         assertThat(qInstance.getProcess("myApp_" + QuickSearchQBitProducer.FULL_REINDEX_PROCESS_NAME)).isNotNull();
      }
   }



   /***************************************************************************
    ** Test produce discovers annotated entity classes.
    ***************************************************************************/
   @Test
   void testProduce_withSearchableEntities_discoversAnnotations() throws Exception
   {
      QInstance qInstance = createBaseQInstance();
      qInstance.addTable(createSearchableEntityTable());

      QuickSearchQBitConfig config = createValidConfig()
         .withSearchableEntityClasses(List.of(SearchableTestEntity.class));
      QuickSearchQBitProducer producer = new QuickSearchQBitProducer().withConfig(config);

      try(MockedConstruction<QuickSearchOpenSearchClient> mocked = Mockito.mockConstruction(
         QuickSearchOpenSearchClient.class,
         (mock, context) ->
         {
            doNothing().when(mock).ensureIndexExists();
            doNothing().when(mock).close();
         }))
      {
         producer.produce(qInstance, NAMESPACE);

         List<QuickSearchableTableConfig> discovered = QuickSearchQBitContext.getDiscoveredTables();
         assertThat(discovered).hasSize(1);
         assertThat(discovered.get(0).getTableName()).isEqualTo("searchableEntity");
         assertThat(discovered.get(0).getSearchableFields()).containsExactly("name", "description");
      }
   }



   /***************************************************************************
    ** Test produce handles OpenSearch initialization failure gracefully.
    ***************************************************************************/
   @Test
   void testProduce_openSearchFails_continuesWithWarning() throws Exception
   {
      QInstance qInstance = createBaseQInstance();
      QuickSearchQBitConfig config = createValidConfig();
      QuickSearchQBitProducer producer = new QuickSearchQBitProducer().withConfig(config);

      try(MockedConstruction<QuickSearchOpenSearchClient> mocked = Mockito.mockConstruction(
         QuickSearchOpenSearchClient.class,
         (mock, context) ->
         {
            doThrow(new QException("Connection refused")).when(mock).ensureIndexExists();
            doNothing().when(mock).close();
         }))
      {
         //////////////////////////////////////////////////////////
         // Should complete without throwing - logs warning only //
         //////////////////////////////////////////////////////////
         producer.produce(qInstance, NAMESPACE);

         assertThat(qInstance.getTable(QuickSearchIndex.TABLE_NAME)).isNotNull();
      }
   }



   /***************************************************************************
    ** Test produce disables scheduled processes when configured.
    ***************************************************************************/
   @Test
   void testProduce_scheduledProcessesDisabled_noSchedule() throws Exception
   {
      QInstance qInstance = createBaseQInstance();
      QuickSearchQBitConfig config = createValidConfig()
         .withEnableScheduledProcesses(false);
      QuickSearchQBitProducer producer = new QuickSearchQBitProducer().withConfig(config);

      try(MockedConstruction<QuickSearchOpenSearchClient> mocked = Mockito.mockConstruction(
         QuickSearchOpenSearchClient.class,
         (mock, context) ->
         {
            doNothing().when(mock).ensureIndexExists();
            doNothing().when(mock).close();
         }))
      {
         producer.produce(qInstance, NAMESPACE);

         assertThat(qInstance.getProcess(QuickSearchQBitProducer.BASEPULL_PROCESS_NAME).getSchedule()).isNull();
      }
   }



   /***************************************************************************
    ** Test produce skips non-annotated classes in entity list.
    ***************************************************************************/
   @Test
   void testProduce_nonAnnotatedClass_skipped() throws Exception
   {
      QInstance qInstance = createBaseQInstance();

      QuickSearchQBitConfig config = createValidConfig()
         .withSearchableEntityClasses(List.of(NonAnnotatedClass.class));
      QuickSearchQBitProducer producer = new QuickSearchQBitProducer().withConfig(config);

      try(MockedConstruction<QuickSearchOpenSearchClient> mocked = Mockito.mockConstruction(
         QuickSearchOpenSearchClient.class,
         (mock, context) ->
         {
            doNothing().when(mock).ensureIndexExists();
            doNothing().when(mock).close();
         }))
      {
         producer.produce(qInstance, NAMESPACE);

         List<QuickSearchableTableConfig> discovered = QuickSearchQBitContext.getDiscoveredTables();
         assertThat(discovered).isEmpty();
      }
   }



   /***************************************************************************
    ** Test withConfig and getConfig.
    ***************************************************************************/
   @Test
   void testConfigAccessors()
   {
      QuickSearchQBitConfig config = createValidConfig();
      QuickSearchQBitProducer producer = new QuickSearchQBitProducer().withConfig(config);

      assertThat(producer.getConfig()).isSameAs(config);
   }



   /***************************************************************************
    ** Helper to create a base QInstance for testing.
    ***************************************************************************/
   private QInstance createBaseQInstance()
   {
      QInstance qInstance = new QInstance();
      qInstance.addBackend(new QBackendMetaData()
         .withName(BACKEND_NAME)
         .withBackendType(MemoryBackendModule.class));
      qInstance.setAuthentication(new QAuthenticationMetaData()
         .withName("anonymous")
         .withType(QAuthenticationType.FULLY_ANONYMOUS));
      return qInstance;
   }



   /***************************************************************************
    ** Helper to create a valid config for testing.
    ***************************************************************************/
   private QuickSearchQBitConfig createValidConfig()
   {
      return new QuickSearchQBitConfig()
         .withBackendName(BACKEND_NAME)
         .withTableNamePrefix("")
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-quick-search")
         .withUseSsl(false);
   }



   /***************************************************************************
    ** Helper to create a searchable entity table.
    ***************************************************************************/
   private QTableMetaData createSearchableEntityTable()
   {
      return new QTableMetaData()
         .withName("searchableEntity")
         .withLabel("Searchable Entity")
         .withBackendName(BACKEND_NAME)
         .withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("name", QFieldType.STRING))
         .withField(new QFieldMetaData("description", QFieldType.STRING))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME));
   }



   /***************************************************************************
    ** Test entity class annotated with @QuickSearchable.
    ***************************************************************************/
   @QuickSearchable(tableName = "searchableEntity")
   public static class SearchableTestEntity
   {
      @QuickSearchField
      private String name;

      @QuickSearchField
      private String description;

      private Integer id;
   }



   /***************************************************************************
    ** Non-annotated test class.
    ***************************************************************************/
   public static class NonAnnotatedClass
   {
      private String name;
   }

}

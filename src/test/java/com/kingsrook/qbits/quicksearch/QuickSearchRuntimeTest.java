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
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QCollectingLogger;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.publisher.IndexEventPublisher;
import com.kingsrook.qbits.quicksearch.publisher.SynchronousIndexEventPublisher;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;


/*******************************************************************************
 ** Unit test for QuickSearchRuntime
 *******************************************************************************/
class QuickSearchRuntimeTest extends BaseQuickSearchTest
{

   /*******************************************************************************
    ** Register a QBit with a runtime on the current QInstance.
    *******************************************************************************/
   private QuickSearchRuntime registerRuntime(QuickSearchQBitConfig config)
   {
      QuickSearchRuntime runtime = new QuickSearchRuntime(config);
      runtime.setDiscoveredTables(List.of(new QuickSearchableTableConfig().withTableName(TEST_ENTITY_TABLE).withPrimaryKeyField("id").withSearchableFields(List.of("name"))));
      config.setRuntime(runtime);

      QInstance qInstance = QContext.getQInstance();
      qInstance.addQBit(new QBitMetaData().withGroupId("com.kingsrook.qbits").withArtifactId("quick-search").withVersion("test").withConfig(config));
      return (runtime);
   }



   @Test
   void testGet_resolvesThroughQInstanceQBits()
   {
      QuickSearchQBitContext.clear();
      assertThat(QuickSearchRuntime.get()).isNull();

      QuickSearchRuntime runtime = registerRuntime(new QuickSearchQBitConfig().withBackendName(TEST_BACKEND_NAME).withOpensearchHost("localhost").withOpensearchPort(9200).withOpensearchIndexName("t"));

      assertThat(QuickSearchRuntime.get()).isSameAs(runtime);
      assertThat(QuickSearchQBitContext.getConfig()).isSameAs(runtime.getConfig());
      assertThat(QuickSearchQBitContext.getTableConfig(TEST_ENTITY_TABLE)).isNotNull();
   }



   @Test
   void testGetOrThrow_withoutRuntime_throws()
   {
      QuickSearchQBitContext.clear();
      assertThatThrownBy(QuickSearchRuntime::getOrThrow).isInstanceOf(QException.class).hasMessageContaining("not registered");
   }



   @Test
   void testGetClient_isLazyAndDoesNotContactCluster() throws QException
   {
      QuickSearchRuntime runtime = registerRuntime(new QuickSearchQBitConfig().withBackendName(TEST_BACKEND_NAME).withOpensearchHost("127.0.0.1").withOpensearchPort(1).withOpensearchIndexName("t"));
      assertThat(runtime.isIndexReady()).isFalse();
      assertThat(runtime.getClient()).isNotNull().isSameAs(runtime.getClient());
      assertThat(runtime.isIndexReady()).isFalse();
      runtime.close();
      runtime.close();
   }



   @Test
   void testGetPublisher_prefersHostSuppliedPublisher() throws QException
   {
      IndexEventPublisher custom = mock(IndexEventPublisher.class);
      QuickSearchRuntime  runtime = registerRuntime(new QuickSearchQBitConfig().withBackendName(TEST_BACKEND_NAME).withOpensearchHost("127.0.0.1").withOpensearchPort(1).withOpensearchIndexName("t").withIndexEventPublisher(custom));
      assertThat(runtime.getPublisher()).isSameAs(custom);

      QuickSearchRuntime defaultRuntime = new QuickSearchRuntime(new QuickSearchQBitConfig().withBackendName(TEST_BACKEND_NAME).withOpensearchHost("127.0.0.1").withOpensearchPort(1).withOpensearchIndexName("t"));
      assertThat(defaultRuntime.getPublisher()).isInstanceOf(SynchronousIndexEventPublisher.class);
      defaultRuntime.close();
   }



   @Test
   void testIsTableEnabled_readsRowAndCaches() throws QException
   {
      QuickSearchRuntime runtime = registerRuntime(new QuickSearchQBitConfig().withBackendName(TEST_BACKEND_NAME).withOpensearchHost("localhost").withOpensearchPort(9200).withOpensearchIndexName("t"));

      assertThat(runtime.isTableEnabled("notConfigured")).isFalse();
      assertThat(runtime.isTableEnabled(TEST_ENTITY_TABLE)).isTrue();

      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(QuickSearchIndex.TABLE_NAME);
      insertInput.setRecords(List.of(new QRecord().withValue("tableName", TEST_ENTITY_TABLE).withValue("enabled", false)));
      Integer id = new InsertAction().execute(insertInput).getRecords().get(0).getValueInteger("id");

      //////////////////////////////////////////////////////////////
      // cached value survives the row change until invalidated   //
      //////////////////////////////////////////////////////////////
      assertThat(runtime.isTableEnabled(TEST_ENTITY_TABLE)).isTrue();
      runtime.invalidateEnabledCache();
      assertThat(runtime.isTableEnabled(TEST_ENTITY_TABLE)).isFalse();

      UpdateInput updateInput = new UpdateInput();
      updateInput.setTableName(QuickSearchIndex.TABLE_NAME);
      updateInput.setRecords(List.of(new QRecord().withValue("id", id).withValue("enabled", true)));
      new UpdateAction().execute(updateInput);
      runtime.invalidateEnabledCache();
      assertThat(runtime.isTableEnabled(TEST_ENTITY_TABLE)).isTrue();
   }



   @Test
   void testStart_degradedSwallowsConnectionFailure_failFastThrows()
   {
      QuickSearchRuntime degraded = new QuickSearchRuntime(new QuickSearchQBitConfig().withBackendName(TEST_BACKEND_NAME).withOpensearchHost("127.0.0.1").withOpensearchPort(1).withOpensearchIndexName("t").withStartupMode(QuickSearchStartupMode.DEGRADED).withConnectTimeoutMillis(200).withResponseTimeoutMillis(500));
      assertThatCode(degraded::start).doesNotThrowAnyException();
      assertThat(degraded.isIndexReady()).isFalse();
      degraded.close();

      QuickSearchRuntime failFast = new QuickSearchRuntime(new QuickSearchQBitConfig().withBackendName(TEST_BACKEND_NAME).withOpensearchHost("127.0.0.1").withOpensearchPort(1).withOpensearchIndexName("t").withConnectTimeoutMillis(200).withResponseTimeoutMillis(500));
      assertThatThrownBy(failFast::start).isInstanceOf(QException.class).hasMessageContaining("startupMode=DEGRADED");
      failFast.close();
   }



   @Test
   void testStart_missingOptionalLibrary_degradedBoots_failFastThrows()
   {
      QCodeReference customizer = new QCodeReference(MissingLibraryTransportCustomizer.class);

      QuickSearchRuntime degraded         = new QuickSearchRuntime(new QuickSearchQBitConfig().withBackendName(TEST_BACKEND_NAME).withOpensearchHost("localhost").withOpensearchPort(9200).withOpensearchIndexName("t").withStartupMode(QuickSearchStartupMode.DEGRADED).withTransportCustomizer(customizer));
      QCollectingLogger  collectingLogger = QLogger.activateCollectingLoggerForClass(QuickSearchRuntime.class);
      try
      {
         assertThatCode(degraded::start).doesNotThrowAnyException();
      }
      finally
      {
         QLogger.deactivateCollectingLoggerForClass(QuickSearchRuntime.class);
      }
      assertThat(degraded.isIndexReady()).isFalse();
      assertThat(collectingLogger.getCollectedMessages()).anySatisfy(m ->
      {
         assertThat(m.getLevel()).isEqualTo(Level.WARN);
         assertThat(m.getMessage()).contains(MissingLibraryTransportCustomizer.MISSING_CLASS).contains("AWS SDK v2").contains("apache-client, sts").doesNotContain("not reachable");
      });
      degraded.close();

      QuickSearchRuntime failFast = new QuickSearchRuntime(new QuickSearchQBitConfig().withBackendName(TEST_BACKEND_NAME).withOpensearchHost("localhost").withOpensearchPort(9200).withOpensearchIndexName("t").withTransportCustomizer(customizer));
      assertThatThrownBy(failFast::start).isInstanceOf(QException.class)
         .hasMessageContaining(MissingLibraryTransportCustomizer.MISSING_CLASS)
         .hasMessageContaining("AWS SDK v2 dependencies (apache-client, sts)")
         .hasMessageNotContaining("startupMode=DEGRADED")
         .hasCauseInstanceOf(NoClassDefFoundError.class);
      failFast.close();
   }

}

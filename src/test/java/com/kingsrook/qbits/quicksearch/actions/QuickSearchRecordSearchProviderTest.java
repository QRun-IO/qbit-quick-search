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

package com.kingsrook.qbits.quicksearch.actions;


import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.RecordSearchAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.search.RecordSearchInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.search.RecordSearchOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.search.RecordSearchResult;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qbits.quicksearch.BaseQuickSearchTest;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/*******************************************************************************
 ** Unit tests for QuickSearchRecordSearchProvider: claim logic, result
 ** mapping, order and per-table id cap, failure and timeout handling, and
 ** core RecordSearchAction end to end (re-read by primary key, fallback to
 ** core's own search) over the memory backend with a mock OpenSearch client.
 *******************************************************************************/
class QuickSearchRecordSearchProviderTest extends BaseQuickSearchTest
{
   private static final String OTHER_TABLE     = "otherEntity";
   private static final String UNINDEXED_TABLE = "unindexedEntity";

   private QuickSearchQBitConfig           config;
   private QuickSearchRuntime              runtime;
   private QuickSearchOpenSearchClient     mockClient;
   private QuickSearchRecordSearchProvider provider;



   /*******************************************************************************
    ** Register a Quick Search QBit whose runtime returns a mock client, with
    ** testEntity and otherEntity indexed; add the provider to the instance.
    *******************************************************************************/
   @BeforeEach
   void setUp() throws QException
   {
      QInstance qInstance = QContext.getQInstance();
      qInstance.addTable(simpleTable(OTHER_TABLE));
      qInstance.addTable(simpleTable(UNINDEXED_TABLE));
      qInstance.getTable(TEST_ENTITY_TABLE).withSearchFields("name", "description");
      qInstance.withRecordSearchProvider(new QCodeReference(QuickSearchRecordSearchProvider.class));

      config = new QuickSearchQBitConfig()
         .withBackendName(TEST_BACKEND_NAME)
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-index")
         .withRecordSearchTimeoutMillis(1234);

      mockClient = mock(QuickSearchOpenSearchClient.class);
      runtime    = registerRuntime(config, mockClient);
      provider   = new QuickSearchRecordSearchProvider();
   }



   /*******************************************************************************
    ** Register the QBit with a runtime (a spy whose client is the given one,
    ** or the real lazily built client when null).
    *******************************************************************************/
   private QuickSearchRuntime registerRuntime(QuickSearchQBitConfig config, QuickSearchOpenSearchClient client) throws QException
   {
      QuickSearchRuntime runtime = new QuickSearchRuntime(config);
      if(client != null)
      {
         runtime = spy(runtime);
         doReturn(client).when(runtime).getClient();
      }
      runtime.setDiscoveredTables(List.of(
         new QuickSearchableTableConfig().withTableName(TEST_ENTITY_TABLE).withPrimaryKeyField("id").withSearchableFields(List.of("name", "description")),
         new QuickSearchableTableConfig().withTableName(OTHER_TABLE).withPrimaryKeyField("id").withSearchableFields(List.of("name"))));
      config.setRuntime(runtime);

      QInstance qInstance = QContext.getQInstance();
      if(qInstance.getQBits() != null)
      {
         qInstance.getQBits().clear();
      }
      qInstance.addQBit(new QBitMetaData().withGroupId("com.kingsrook.qbits").withArtifactId("quick-search").withVersion("test").withConfig(config));
      return (runtime);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static QTableMetaData simpleTable(String name)
   {
      return (new QTableMetaData()
         .withName(name)
         .withBackendName(TEST_BACKEND_NAME)
         .withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("name", QFieldType.STRING))
         .withSearchFields("name"));
   }



   /*******************************************************************************
    ** Insert testEntity rows with ids 1..n named "<prefix> <i>".
    *******************************************************************************/
   private void insertEntities(String prefix, int count) throws QException
   {
      List<QRecord> records = new ArrayList<>();
      for(int i = 1; i <= count; i++)
      {
         records.add(new QRecord().withValue("id", i).withValue("name", prefix + " " + i).withValue("description", "row " + i));
      }
      new InsertAction().execute(new InsertInput(TEST_ENTITY_TABLE).withRecords(records));
   }



   @Test
   void testClaimsTable_indexedEnabledTablesOnly()
   {
      QInstance qInstance = QContext.getQInstance();
      assertThat(provider.claimsTable(qInstance.getTable(TEST_ENTITY_TABLE))).isTrue();
      assertThat(provider.claimsTable(qInstance.getTable(OTHER_TABLE))).isTrue();
      assertThat(provider.claimsTable(qInstance.getTable(UNINDEXED_TABLE))).isFalse();
      assertThat(provider.claimsTable(null)).isFalse();
   }



   @Test
   void testClaimsTable_disabledTable_notClaimed() throws QException
   {
      new InsertAction().execute(new InsertInput(config.getQuickSearchIndexTableName()).withRecords(List.of(
         new QRecord().withValue("tableName", TEST_ENTITY_TABLE).withValue("enabled", false))));
      runtime.invalidateEnabledCache();

      assertThat(provider.claimsTable(QContext.getQInstance().getTable(TEST_ENTITY_TABLE))).isFalse();
      assertThat(provider.claimsTable(QContext.getQInstance().getTable(OTHER_TABLE))).isTrue();
   }



   @Test
   void testClaimsTable_serveCoreRecordSearchOff_orBackingOff_orNoRuntime()
   {
      QTableMetaData table = QContext.getQInstance().getTable(TEST_ENTITY_TABLE);

      config.setServeCoreRecordSearch(false);
      assertThat(provider.claimsTable(table)).isFalse();
      config.setServeCoreRecordSearch(true);

      runtime.markRecordSearchFailed();
      assertThat(runtime.isRecordSearchBackingOff()).isTrue();
      assertThat(provider.claimsTable(table)).isFalse();
      runtime.clearRecordSearchBackoff();
      assertThat(provider.claimsTable(table)).isTrue();

      QContext.getQInstance().getQBits().clear();
      assertThat(provider.claimsTable(table)).isFalse();
   }



   @Test
   void testSearch_mapsIdsPerTableInOrder_andCapsIdsPerTable() throws QException
   {
      Map<String, List<String>> ids = new LinkedHashMap<>();
      ids.put(OTHER_TABLE, List.of("7"));
      ids.put(TEST_ENTITY_TABLE, List.of("3", "1", "2"));
      when(mockClient.searchRecordIdsPerTable(anyString(), anyList(), anyInt(), anyList(), anyInt())).thenReturn(ids);

      RecordSearchOutput output = provider.search(new RecordSearchInput()
         .withSearchTerm("  widget   blue ")
         .withTableNames(List.of(TEST_ENTITY_TABLE, UNINDEXED_TABLE, OTHER_TABLE, TEST_ENTITY_TABLE))
         .withLimitPerTable(5));

      assertThat(output.getResults()).extracting(RecordSearchResult::getTableName, RecordSearchResult::getRecordId).containsExactly(
         tuple(TEST_ENTITY_TABLE, "3"),
         tuple(TEST_ENTITY_TABLE, "1"),
         tuple(TEST_ENTITY_TABLE, "2"),
         tuple(OTHER_TABLE, "7"));

      verify(mockClient).searchRecordIdsPerTable(eq("widget blue"), eq(List.of(TEST_ENTITY_TABLE, OTHER_TABLE)), eq(5 * QuickSearchRecordSearchProvider.IDS_PER_LIMIT_MULTIPLIER), anyList(), eq(1234));

      provider.search(new RecordSearchInput().withSearchTerm("widget").withTableNames(List.of(TEST_ENTITY_TABLE)).withLimitPerTable(25));
      verify(mockClient).searchRecordIdsPerTable(eq("widget"), eq(List.of(TEST_ENTITY_TABLE)), eq(RecordSearchAction.MAX_PROVIDER_IDS_PER_TABLE), anyList(), eq(1234));

      provider.search(new RecordSearchInput().withSearchTerm("gadget").withTableNames(List.of(TEST_ENTITY_TABLE)));
      verify(mockClient).searchRecordIdsPerTable(eq("gadget"), eq(List.of(TEST_ENTITY_TABLE)), eq(RecordSearchAction.DEFAULT_LIMIT_PER_TABLE * QuickSearchRecordSearchProvider.IDS_PER_LIMIT_MULTIPLIER), anyList(), eq(1234));
   }



   @Test
   void testSearch_shortTermOrNoIndexedTables_returnsEmptyWithoutOpenSearch() throws QException
   {
      assertThat(provider.search(new RecordSearchInput().withSearchTerm("x").withTableNames(List.of(TEST_ENTITY_TABLE))).getResults()).isEmpty();
      assertThat(provider.search(new RecordSearchInput().withSearchTerm("widget").withTableNames(List.of(UNINDEXED_TABLE))).getResults()).isEmpty();
      assertThat(provider.search(new RecordSearchInput().withSearchTerm("widget")).getResults()).isEmpty();
      verify(mockClient, never()).searchRecordIdsPerTable(any(), any(), anyInt(), any(), anyInt());
   }



   @Test
   void testSearch_clientFailure_throwsAndBacksOff() throws QException
   {
      when(mockClient.searchRecordIdsPerTable(anyString(), anyList(), anyInt(), anyList(), anyInt())).thenThrow(new QException("boom"));

      assertThatThrownBy(() -> provider.search(new RecordSearchInput().withSearchTerm("widget").withTableNames(List.of(TEST_ENTITY_TABLE))))
         .isInstanceOf(QException.class).hasMessageContaining("boom");
      assertThat(runtime.isRecordSearchBackingOff()).isTrue();
   }



   @Test
   void testSearch_uncheckedClientFailure_wrappedAsQException() throws QException
   {
      when(mockClient.searchRecordIdsPerTable(anyString(), anyList(), anyInt(), anyList(), anyInt())).thenThrow(new IllegalStateException("closed"));

      assertThatThrownBy(() -> provider.search(new RecordSearchInput().withSearchTerm("widget").withTableNames(List.of(TEST_ENTITY_TABLE))))
         .isInstanceOf(QException.class).hasMessageContaining("closed");
      assertThat(runtime.isRecordSearchBackingOff()).isTrue();
   }



   /*******************************************************************************
    ** A server that accepts connections and never answers (a hung OpenSearch):
    ** the real client gives up after recordSearchTimeoutMillis with a
    ** QException, well before the transport's response timeout.
    *******************************************************************************/
   @Test
   void testSearch_hungOpenSearch_timesOutWithQException() throws Exception
   {
      try(ServerSocket hung = new ServerSocket(0, 50, InetAddress.getLoopbackAddress()))
      {
         QuickSearchQBitConfig hungConfig = new QuickSearchQBitConfig()
            .withBackendName(TEST_BACKEND_NAME)
            .withOpensearchHost("127.0.0.1")
            .withOpensearchPort(hung.getLocalPort())
            .withOpensearchIndexName("test-index")
            .withResponseTimeoutMillis(60_000)
            .withRecordSearchTimeoutMillis(300);
         QuickSearchRuntime hungRuntime = registerRuntime(hungConfig, null);

         long start = System.currentTimeMillis();
         assertThatThrownBy(() -> provider.search(new RecordSearchInput().withSearchTerm("widget").withTableNames(List.of(TEST_ENTITY_TABLE))))
            .isInstanceOf(QException.class).hasMessageContaining("timed out after 300 ms");
         assertThat(System.currentTimeMillis() - start).isLessThan(10_000);
         assertThat(hungRuntime.isRecordSearchBackingOff()).isTrue();
         assertThat(provider.claimsTable(QContext.getQInstance().getTable(TEST_ENTITY_TABLE))).isFalse();

         hungRuntime.close();
      }
   }



   /*******************************************************************************
    ** Core RecordSearchAction re-reads the provider's ids by primary key, keeps
    ** its order, drops ids that no longer exist, applies limitPerTable, and
    ** fills record labels; unclaimed tables keep core's own search.
    *******************************************************************************/
   @Test
   void testCoreRecordSearchAction_usesProviderIds() throws QException
   {
      insertEntities("Widget", 6);
      new InsertAction().execute(new InsertInput(UNINDEXED_TABLE).withRecords(List.of(new QRecord().withValue("id", 1).withValue("name", "Widget elsewhere"))));

      Map<String, List<String>> ids = new LinkedHashMap<>();
      ids.put(TEST_ENTITY_TABLE, List.of("5", "999", "2", "4"));
      ids.put(OTHER_TABLE, List.of());
      when(mockClient.searchRecordIdsPerTable(anyString(), anyList(), anyInt(), anyList(), anyInt())).thenReturn(ids);

      RecordSearchOutput output = new RecordSearchAction().execute(new RecordSearchInput().withSearchTerm("widget").withLimitPerTable(2));

      assertThat(output.getResults()).filteredOn(r -> r.getTableName().equals(TEST_ENTITY_TABLE)).extracting(RecordSearchResult::getRecordId).containsExactly(5, 2);
      assertThat(output.getResults()).filteredOn(r -> r.getTableName().equals(UNINDEXED_TABLE)).extracting(RecordSearchResult::getRecordId).containsExactly(1);
      assertThat(output.getResults()).filteredOn(r -> r.getTableName().equals(OTHER_TABLE)).isEmpty();
   }



   /*******************************************************************************
    ** When the provider fails, core searches the claimed tables itself.
    *******************************************************************************/
   @Test
   void testCoreRecordSearchAction_providerFailure_fallsBackToCoreSearch() throws QException
   {
      insertEntities("Widget", 3);
      when(mockClient.searchRecordIdsPerTable(anyString(), anyList(), anyInt(), anyList(), anyInt())).thenThrow(new QException("OpenSearch is down"));

      RecordSearchOutput output = new RecordSearchAction().execute(new RecordSearchInput().withSearchTerm("widget").withTableNames(List.of(TEST_ENTITY_TABLE)));

      assertThat(output.getResults()).extracting(RecordSearchResult::getRecordId).containsExactly(1, 2, 3);
      assertThat(runtime.isRecordSearchBackingOff()).isTrue();

      //////////////////////////////////////////////////////////////////////
      // while backing off, the provider claims nothing: OpenSearch is not //
      // called again and core still answers                               //
      //////////////////////////////////////////////////////////////////////
      RecordSearchOutput again = new RecordSearchAction().execute(new RecordSearchInput().withSearchTerm("widget").withTableNames(List.of(TEST_ENTITY_TABLE)));
      assertThat(again.getResults()).hasSize(3);
      verify(mockClient, times(1)).searchRecordIdsPerTable(anyString(), anyList(), anyInt(), anyList(), anyInt());
   }

}

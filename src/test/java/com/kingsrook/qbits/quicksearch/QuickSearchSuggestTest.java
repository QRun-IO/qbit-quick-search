/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2021-2026.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qbits.quicksearch;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kingsrook.qbits.quicksearch.api.QuickSearchSuggest;
import com.kingsrook.qbits.quicksearch.api.QuickSearchSuggestService;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import com.kingsrook.qqq.backend.core.actions.ai.AiFieldMetaData;
import com.kingsrook.qqq.backend.core.actions.ai.DataClassification;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.security.QSecurityKeyType;
import com.kingsrook.qqq.backend.core.model.metadata.security.RecordSecurityLock;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.TotalHitsRelation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;


/*******************************************************************************
 ** Exercise real search filtering and bounded requests with a stubbed transport.
 *******************************************************************************/
class QuickSearchSuggestTest extends BaseQuickSearchTest
{
   private QuickSearchRuntime runtime;
   private QuickSearchQBitConfig config;
   private QuickSearchOpenSearchClient client;
   private List<QuickSearchableTableConfig> tables;



   /*******************************************************************************
    ** Register an instance runtime while keeping the real permission/search path.
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      config = QuickSearchQBitContext.getConfig();
      client = QuickSearchQBitContext.getClient();
      runtime = spy(new QuickSearchRuntime(config));
      tables = new ArrayList<>(QuickSearchQBitContext.getDiscoveredTables());
      doReturn(client).when(runtime).getClient();
      doReturn(tables).when(runtime).getDiscoveredTables();
      doReturn(true).when(runtime).isTableEnabled(anyString());
      config.setRuntime(runtime);
      QContext.getQInstance().addQBit(new QBitMetaData().withGroupId("com.kingsrook.qbits").withArtifactId("quick-search").withVersion("test").withConfig(config));
      QContext.getQInstance().getTable(TEST_ENTITY_TABLE).withLabel("Test Entity");
      insert(TEST_ENTITY_TABLE);
      stub(3);
   }



   /*******************************************************************************
    ** Populate enough source rows for actual QueryAction authorization.
    *******************************************************************************/
   private void insert(String table) throws Exception
   {
      new InsertAction().execute(new InsertInput(table).withRecords(IntStream.rangeClosed(1, 30)
         .mapToObj(id -> new QRecord().withValue("id", id).withValue("name", "Widget " + id)).toList()));
   }



   /*******************************************************************************
    ** Add another configured, readable table.
    *******************************************************************************/
   private void addTable(String name) throws Exception
   {
      QTableMetaData metadata = QContext.getQInstance().getTable(TEST_ENTITY_TABLE).clone();
      metadata.setName(name);
      metadata.setLabel(name);
      QContext.getQInstance().addTable(metadata);
      tables.add(new QuickSearchableTableConfig().withTableName(name).withPrimaryKeyField("id").withSearchableFields(List.of("name")).withEnabledByDefault(true));
      insert(name);
   }



   /*******************************************************************************
    ** Honor fetch bounds and give later table names higher scores for ranking.
    *******************************************************************************/
   private void stub(Integer total) throws Exception
   {
      doAnswer(invocation ->
      {
         String table = ((List<String>) invocation.getArgument(1)).get(0);
         Integer size = invocation.getArgument(2);
         List<Hit<OpenSearchDocument>> hits = IntStream.rangeClosed(1, Math.min(total, size)).mapToObj(id ->
            new Hit.Builder<OpenSearchDocument>().index("test").id(table + ":" + id).score((table.equals(TEST_ENTITY_TABLE) ? 10.0 : 100.0) - id)
               .source(new OpenSearchDocument().withSourceTable(table).withRecordId(String.valueOf(id)).withRecordLabel("Widget " + id)
                  .withSubtitle("Public subtitle").withDisplayValues(Map.of("Extra", "not exposed")))
               .highlight(Map.of("searchableText", List.of("<em>Widget</em>"))).build()).toList();
         return (new SearchResponse.Builder<OpenSearchDocument>().took(1).timedOut(false).shards(sh -> sh.total(1).successful(1).failed(0))
            .hits(h -> h.hits(hits).total(t -> t.value(total).relation(TotalHitsRelation.Eq))).build());
      }).when(client).search(anyString(), anyList(), anyInt(), anyInt(), anyList(), any());
   }



   /*******************************************************************************
    ** Normalize, group, preserve relevance order and expose only the wire fields.
    *******************************************************************************/
   @Test
   void groupedResultsAndWireShape() throws Exception
   {
      addTable("other");
      QuickSearchSuggest result = QuickSearchSuggestService.suggest("  blue   wid  ", 2, 1, null);
      assertThat(result.query()).isEqualTo("blue wid");
      assertThat(result.truncated()).isTrue();
      assertThat(result.groups()).hasSize(1);
      QuickSearchSuggest.Group group = result.groups().get(0);
      assertThat(group.tableName()).isEqualTo("other");
      assertThat(group.hasMore()).isTrue();
      assertThat(group.items()).extracting(QuickSearchSuggest.Item::recordId).containsExactly("1", "2");
      assertThat(group.items().get(0).label()).isEqualTo("Widget 1");
      assertThat(group.items().get(0).subtitle()).isEqualTo("Public subtitle");
      assertThat(new ObjectMapper().writeValueAsString(result)).doesNotContain("score", "highlight", "displayValues", "<em>", "not exposed");
      verify(client).search(eq("blue wid"), eq(List.of("other")), eq(6), eq(0), anyList(), any());
      verify(client).search(eq("blue wid"), eq(List.of(TEST_ENTITY_TABLE)), eq(6), eq(0), anyList(), any());
      assertThatThrownBy(() -> result.groups().clear()).isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(() -> group.items().clear()).isInstanceOf(UnsupportedOperationException.class);
   }



   /*******************************************************************************
    ** Default and maximum caps include an extra visible item without rescanning.
    *******************************************************************************/
   @Test
   void defaultAndMaximumCaps() throws Exception
   {
      stub(30);
      for(int i = 0; i < 21; i++)
      {
         addTable("table" + i);
      }
      QuickSearchSuggest defaults = QuickSearchSuggestService.suggest("wi", null, null, null);
      assertThat(defaults.groups()).hasSize(8).allSatisfy(group ->
      {
         assertThat(group.items()).hasSize(5);
         assertThat(group.hasMore()).isTrue();
      });
      assertThat(defaults.truncated()).isTrue();
      QuickSearchSuggest maximum = QuickSearchSuggestService.suggest("wi", 10, 20, null);
      assertThat(maximum.groups()).hasSize(20).allSatisfy(group -> assertThat(group.items()).hasSize(10));
      assertThat(maximum.truncated()).isTrue();
   }



   /*******************************************************************************
    ** Table selection, permissions, enabled flags and protected primary keys apply.
    *******************************************************************************/
   @Test
   void tableFilters() throws Exception
   {
      addTable("other");
      assertThat(QuickSearchSuggestService.suggest("wi", null, null, " other, other,unknown ").groups())
         .extracting(QuickSearchSuggest.Group::tableName).containsExactly("other");
      assertThat(QuickSearchSuggestService.suggest("wi", null, null, " , ").groups()).isEmpty();
      QContext.getQInstance().getTable("other").withPermissionRules(new QPermissionRules().withLevel(PermissionLevel.READ_WRITE_PERMISSIONS));
      assertThat(QuickSearchSuggestService.suggest("wi", null, null, "other").groups()).isEmpty();
      doReturn(false).when(runtime).isTableEnabled(TEST_ENTITY_TABLE);
      assertThat(QuickSearchSuggestService.suggest("wi", null, null, null).groups()).isEmpty();
      doReturn(true).when(runtime).isTableEnabled(TEST_ENTITY_TABLE);
      QContext.getQInstance().getTable(TEST_ENTITY_TABLE).getField("id")
         .withSupplementalMetaData(new AiFieldMetaData().withClassification(DataClassification.PII));
      assertThat(QuickSearchSuggestService.suggest("wi", null, null, TEST_ENTITY_TABLE).groups()).isEmpty();
   }



   /*******************************************************************************
    ** Real record locks remove hits; an empty candidate batch never triggers scans.
    *******************************************************************************/
   @Test
   void recordLocksAndSinglePass() throws Exception
   {
      QContext.getQInstance().addSecurityKeyType(new QSecurityKeyType().withName("entity"));
      QContext.getQInstance().getTable(TEST_ENTITY_TABLE).withRecordSecurityLock(new RecordSecurityLock().withSecurityKeyType("entity").withFieldName("id"));
      QContext.getQSession().withSecurityKeyValue("entity", 2);
      QuickSearchSuggest result = QuickSearchSuggestService.suggest("wi", 1, null, null);
      assertThat(result.groups().get(0).items()).extracting(QuickSearchSuggest.Item::recordId).containsExactly("2");
      assertThat(result.groups().get(0).hasMore()).isFalse();
      clearInvocations(client);
      QContext.getQSession().clearSecurityKeyValues();
      stub(30);
      assertThat(QuickSearchSuggestService.suggest("wi", 1, null, null).groups()).isEmpty();
      verify(client).search(anyString(), anyList(), eq(4), eq(0), anyList(), any());
      verify(client, never()).search(anyString(), anyList(), anyInt(), eq(4), anyList(), any());
   }



   /*******************************************************************************
    ** Stale PHI/PII labels and subtitles are rebuilt from allowed fields only.
    *******************************************************************************/
   @Test
   void classifiedStaleResultAndFallback() throws Exception
   {
      QTableMetaData metadata = QContext.getQInstance().getTable(TEST_ENTITY_TABLE);
      metadata.getField("description").withSupplementalMetaData(new AiFieldMetaData().withClassification(DataClassification.PHI));
      tables.get(0).withRecordLabelFields(List.of("description")).withFieldRoles(Map.of("description", "SUBTITLE"));
      OpenSearchDocument stale = new OpenSearchDocument().withSourceTable(TEST_ENTITY_TABLE).withRecordId("1")
         .withRecordLabel("PHI-CANARY-7f3c").withSubtitle("PHI-CANARY-7f3c").withSearchableText("PHI-CANARY-7f3c")
         .withFieldValues(Map.of("name", "Public widget", "description", "PHI-CANARY-7f3c"));
      SearchResponse<OpenSearchDocument> response = new SearchResponse.Builder<OpenSearchDocument>().took(1).timedOut(false)
         .shards(sh -> sh.total(1).successful(1).failed(0)).hits(h -> h.hits(hit -> hit.index("test").id("1").source(stale))).build();
      doReturn(response).when(client).search(anyString(), anyList(), anyInt(), anyInt(), anyList(), any());
      QuickSearchSuggest result = QuickSearchSuggestService.suggest("Public", null, null, null);
      assertThat(result.groups().get(0).items().get(0).label()).isEqualTo("Test Entity #1");
      assertThat(result.groups().get(0).items().get(0).subtitle()).isNull();
      assertThat(new ObjectMapper().writeValueAsString(result)).doesNotContain("PHI-CANARY-7f3c");
      assertThat(result.truncated()).isFalse();
   }



   /*******************************************************************************
    ** Input validation runs even when search is unavailable.
    *******************************************************************************/
   @Test
   void validationAndShortTerms() throws Exception
   {
      for(String term : List.of("", " ", " x "))
      {
         assertThat(QuickSearchSuggestService.suggest(term, null, null, null).groups()).isEmpty();
      }
      verifyNoInteractions(client);
      assertThatThrownBy(() -> QuickSearchSuggestService.suggest(null, null, null, null)).isInstanceOf(QBadRequestException.class);
      assertThatThrownBy(() -> QuickSearchSuggestService.suggest("x".repeat(101), null, null, null)).isInstanceOf(QBadRequestException.class);
      for(Integer limit : List.of(0, -1, 11))
      {
         assertThatThrownBy(() -> QuickSearchSuggestService.suggest("wi", limit, null, null)).isInstanceOf(QBadRequestException.class);
      }
      for(Integer limit : List.of(0, -1, 21))
      {
         assertThatThrownBy(() -> QuickSearchSuggestService.suggest("wi", null, limit, null)).isInstanceOf(QBadRequestException.class);
      }
      assertThat(QuickSearchSuggestService.suggest(" " + "x".repeat(100) + " ", null, null, null).query()).hasSize(100);
   }



   /*******************************************************************************
    ** Hosts that disable record locks still use one request and visible lookahead.
    *******************************************************************************/
   @Test
   void unlockedSearchAndHostLimit() throws Exception
   {
      config.withApplyRecordSecurityLocks(false);
      QuickSearchSuggest result = QuickSearchSuggestService.suggest("wi", 2, null, null);
      assertThat(result.groups().get(0).items()).hasSize(2);
      assertThat(result.groups().get(0).hasMore()).isTrue();
      verify(client).search(anyString(), anyList(), eq(3), eq(0), anyList(), any());
      config.withMaxSearchLimit(1);
      QuickSearchSuggest bounded = QuickSearchSuggestService.suggest("wi", 2, null, null);
      assertThat(bounded.groups().get(0).items()).hasSize(1);
      assertThat(bounded.groups().get(0).hasMore()).isFalse();
   }



   /*******************************************************************************
    ** Missing runtime/client and failed transports are forgiving; permissions are not.
    *******************************************************************************/
   @Test
   void unavailableAndSessionRequired() throws Exception
   {
      config.setRuntime(null);
      assertThat(QuickSearchSuggestService.suggest("wi", null, null, null).groups()).isEmpty();
      config.setRuntime(runtime);
      doReturn(null).when(runtime).getClient();
      assertThat(QuickSearchSuggestService.suggest("wi", null, null, null).groups()).isEmpty();
      doThrow(new QException("unavailable")).when(runtime).getClient();
      assertThat(QuickSearchSuggestService.suggest("wi", null, null, null).groups()).isEmpty();
      doThrow(new QPermissionDeniedException("denied")).when(runtime).getClient();
      assertThatThrownBy(() -> QuickSearchSuggestService.suggest("wi", null, null, null)).isInstanceOf(QPermissionDeniedException.class);
      QContext.init(QContext.getQInstance(), (QSession) null);
      assertThatThrownBy(() -> QuickSearchSuggestService.suggest("", null, null, null)).isInstanceOf(QPermissionDeniedException.class);
   }
}

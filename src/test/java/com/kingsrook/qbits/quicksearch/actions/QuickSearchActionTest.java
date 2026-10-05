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


import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitContext;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.core.search.TotalHits;
import org.opensearch.client.opensearch.core.search.TotalHitsRelation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/*******************************************************************************
 ** Tests for QuickSearchAction.
 **
 ** Uses Mockito to mock QuickSearchOpenSearchClient and the OpenSearch
 ** SearchResponse chain (SearchResponse, HitsMetadata, TotalHits, Hit). Record
 ** security locks are off here; QuickSearchActionSecurityTest covers them.
 *******************************************************************************/
@SuppressWarnings("unchecked")
class QuickSearchActionTest
{
   private QuickSearchOpenSearchClient  mockClient;
   private List<QuickSearchableTableConfig> tableConfigs;
   private QuickSearchAction            action;



   /*******************************************************************************
    ** Set up a mock client, table configs, and context state before each test.
    *******************************************************************************/
   @BeforeEach
   void setUp()
   {
      mockClient = mock(QuickSearchOpenSearchClient.class);

      tableConfigs = List.of(
         new QuickSearchableTableConfig()
            .withTableName("orders")
            .withPrimaryKeyField("id")
            .withSearchableFields(List.of("orderNumber", "customerName")));

      QuickSearchQBitContext.setConfig(new QuickSearchQBitConfig().withApplyRecordSecurityLocks(false));
      QuickSearchQBitContext.setClient(mockClient);
      QuickSearchQBitContext.setDiscoveredTables(tableConfigs);

      action = new QuickSearchAction();
   }



   /*******************************************************************************
    ** Clear context after each test to prevent state leakage.
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      QuickSearchQBitContext.clear();
   }



   /*******************************************************************************
    ** Build a mocked SearchResponse with the given hits and totalHits count.
    *******************************************************************************/
   private SearchResponse<OpenSearchDocument> buildMockResponse(List<Hit<OpenSearchDocument>> hits, long totalHits)
   {
      TotalHits mockTotal = mock(TotalHits.class);
      when(mockTotal.value()).thenReturn(totalHits);

      HitsMetadata<OpenSearchDocument> mockHitsMeta = mock(HitsMetadata.class);
      when(mockHitsMeta.total()).thenReturn(mockTotal);
      when(mockHitsMeta.hits()).thenReturn(hits);

      SearchResponse<OpenSearchDocument> mockResponse = mock(SearchResponse.class);
      when(mockResponse.hits()).thenReturn(mockHitsMeta);

      return (mockResponse);
   }



   /*******************************************************************************
    ** Build a single mocked Hit with the given document, score, and highlights.
    *******************************************************************************/
   private Hit<OpenSearchDocument> buildMockHit(OpenSearchDocument doc, Double score, Map<String, List<String>> highlight)
   {
      Hit<OpenSearchDocument> hit = mock(Hit.class);
      when(hit.source()).thenReturn(doc);
      when(hit.score()).thenReturn(score);
      when(hit.highlight()).thenReturn(highlight);
      return (hit);
   }



   /*******************************************************************************
    ** Null searchTerm returns empty output with totalHits=0 and hasMore=false.
    *******************************************************************************/
   @Test
   void testNullSearchTerm_returnsEmptyOutput() throws QException
   {
      QuickSearchInput input = new QuickSearchInput().withSearchTerm(null);

      QuickSearchOutput output = action.execute(input);

      assertThat(output.getResults()).isEmpty();
      assertThat(output.getTotalHits()).isEqualTo(0L);
      assertThat(output.getHasMore()).isFalse();
   }



   /*******************************************************************************
    ** Empty string searchTerm returns empty output.
    *******************************************************************************/
   @Test
   void testEmptySearchTerm_returnsEmptyOutput() throws QException
   {
      QuickSearchInput input = new QuickSearchInput().withSearchTerm("");

      QuickSearchOutput output = action.execute(input);

      assertThat(output.getResults()).isEmpty();
      assertThat(output.getTotalHits()).isEqualTo(0L);
      assertThat(output.getHasMore()).isFalse();
   }



   /*******************************************************************************
    ** Whitespace-only searchTerm returns empty output.
    *******************************************************************************/
   @Test
   void testBlankSearchTerm_returnsEmptyOutput() throws QException
   {
      QuickSearchInput input = new QuickSearchInput().withSearchTerm("   ");

      QuickSearchOutput output = action.execute(input);

      assertThat(output.getResults()).isEmpty();
      assertThat(output.getTotalHits()).isEqualTo(0L);
      assertThat(output.getHasMore()).isFalse();
   }



   /*******************************************************************************
    ** Valid search maps each hit to a QuickSearchResult with all fields populated.
    *******************************************************************************/
   @Test
   void testValidSearch_mapsResultsCorrectly() throws QException
   {
      OpenSearchDocument doc = new OpenSearchDocument()
         .withSourceTable("orders")
         .withRecordId("42")
         .withRecordLabel("Order #42");

      Hit<OpenSearchDocument> hit = buildMockHit(doc, 1.5, Map.of("searchableText", List.of("Order <em>42</em>")));

      SearchResponse<OpenSearchDocument> response = buildMockResponse(List.of(hit), 1L);
      when(mockClient.search(anyString(), any(), anyInt(), anyInt(), anyList())).thenReturn(response);

      QuickSearchInput input = new QuickSearchInput()
         .withSearchTerm("42")
         .withLimit(10)
         .withOffset(0);

      QuickSearchOutput output = action.execute(input);

      assertThat(output.getResults()).hasSize(1);
      QuickSearchResult result = output.getResults().get(0);
      assertThat(result.getTableName()).isEqualTo("orders");
      assertThat(result.getRecordId()).isEqualTo("42");
      assertThat(result.getRecordLabel()).isEqualTo("Order #42");
      assertThat(result.getScore()).isEqualTo(1.5f);
      assertThat(result.getHighlightSnippet()).isEqualTo("Order <em>42</em>");
      assertThat(output.getTotalHits()).isEqualTo(1L);
      assertThat(output.getHasMore()).isFalse();
   }



   /*******************************************************************************
    ** Multiple highlight fragments are joined with "...".
    *******************************************************************************/
   @Test
   void testHighlightFragments_joinedWithEllipsis() throws QException
   {
      OpenSearchDocument doc = new OpenSearchDocument()
         .withSourceTable("orders")
         .withRecordId("1")
         .withRecordLabel("Order 1");

      Hit<OpenSearchDocument> hit = buildMockHit(doc, 1.0, Map.of("searchableText", List.of("fragment one", "fragment two")));

      SearchResponse<OpenSearchDocument> response = buildMockResponse(List.of(hit), 1L);
      when(mockClient.search(anyString(), any(), anyInt(), anyInt(), anyList())).thenReturn(response);

      QuickSearchOutput output = action.execute(new QuickSearchInput().withSearchTerm("one"));

      assertThat(output.getResults().get(0).getHighlightSnippet()).isEqualTo("fragment one...fragment two");
   }



   /*******************************************************************************
    ** When highlight map is null, highlightSnippet is null.
    *******************************************************************************/
   @Test
   void testNullHighlight_resultHasNullSnippet() throws QException
   {
      OpenSearchDocument doc = new OpenSearchDocument()
         .withSourceTable("orders")
         .withRecordId("5")
         .withRecordLabel("Order 5");

      Hit<OpenSearchDocument> hit = buildMockHit(doc, 0.9, null);

      SearchResponse<OpenSearchDocument> response = buildMockResponse(List.of(hit), 1L);
      when(mockClient.search(anyString(), any(), anyInt(), anyInt(), anyList())).thenReturn(response);

      QuickSearchOutput output = action.execute(new QuickSearchInput().withSearchTerm("order"));

      assertThat(output.getResults().get(0).getHighlightSnippet()).isNull();
   }



   /*******************************************************************************
    ** Null limit is defaulted to 25.
    *******************************************************************************/
   @Test
   void testNullLimit_defaultsTo25() throws QException
   {
      SearchResponse<OpenSearchDocument> response = buildMockResponse(Collections.emptyList(), 0L);
      when(mockClient.search(anyString(), any(), anyInt(), anyInt(), anyList())).thenReturn(response);

      action.execute(new QuickSearchInput().withSearchTerm("test").withLimit(null));

      verify(mockClient).search(anyString(), any(), eq(25), anyInt(), anyList());
   }



   /*******************************************************************************
    ** Zero limit is defaulted to 25.
    *******************************************************************************/
   @Test
   void testZeroLimit_defaultsTo25() throws QException
   {
      SearchResponse<OpenSearchDocument> response = buildMockResponse(Collections.emptyList(), 0L);
      when(mockClient.search(anyString(), any(), anyInt(), anyInt(), anyList())).thenReturn(response);

      action.execute(new QuickSearchInput().withSearchTerm("test").withLimit(0));

      verify(mockClient).search(anyString(), any(), eq(25), anyInt(), anyList());
   }



   /*******************************************************************************
    ** Negative limit is defaulted to 25.
    *******************************************************************************/
   @Test
   void testNegativeLimit_defaultsTo25() throws QException
   {
      SearchResponse<OpenSearchDocument> response = buildMockResponse(Collections.emptyList(), 0L);
      when(mockClient.search(anyString(), any(), anyInt(), anyInt(), anyList())).thenReturn(response);

      action.execute(new QuickSearchInput().withSearchTerm("test").withLimit(-5));

      verify(mockClient).search(anyString(), any(), eq(25), anyInt(), anyList());
   }



   /*******************************************************************************
    ** Null offset is defaulted to 0.
    *******************************************************************************/
   @Test
   void testNullOffset_defaultsTo0() throws QException
   {
      SearchResponse<OpenSearchDocument> response = buildMockResponse(Collections.emptyList(), 0L);
      when(mockClient.search(anyString(), any(), anyInt(), anyInt(), anyList())).thenReturn(response);

      action.execute(new QuickSearchInput().withSearchTerm("test").withOffset(null));

      verify(mockClient).search(anyString(), any(), anyInt(), eq(0), anyList());
   }



   /*******************************************************************************
    ** Negative offset is defaulted to 0.
    *******************************************************************************/
   @Test
   void testNegativeOffset_defaultsTo0() throws QException
   {
      SearchResponse<OpenSearchDocument> response = buildMockResponse(Collections.emptyList(), 0L);
      when(mockClient.search(anyString(), any(), anyInt(), anyInt(), anyList())).thenReturn(response);

      action.execute(new QuickSearchInput().withSearchTerm("test").withOffset(-1));

      verify(mockClient).search(anyString(), any(), anyInt(), eq(0), anyList());
   }



   /*******************************************************************************
    ** hasMore is true when (offset + results.size()) < totalHits.
    *******************************************************************************/
   @Test
   void testHasMore_trueWhenMoreResultsAvailable() throws QException
   {
      OpenSearchDocument doc = new OpenSearchDocument()
         .withSourceTable("orders").withRecordId("1").withRecordLabel("Order 1");

      List<Hit<OpenSearchDocument>> hits = List.of(buildMockHit(doc, 1.0, null));

      // 1 result, offset=0, totalHits=5 → hasMore = (0+1) < 5 = true
      SearchResponse<OpenSearchDocument> response = buildMockResponse(hits, 5L);
      when(mockClient.search(anyString(), any(), anyInt(), anyInt(), anyList())).thenReturn(response);

      QuickSearchOutput output = action.execute(new QuickSearchInput()
         .withSearchTerm("order")
         .withOffset(0)
         .withLimit(1));

      assertThat(output.getHasMore()).isTrue();
      assertThat(output.getTotalHits()).isEqualTo(5L);
   }



   /*******************************************************************************
    ** hasMore is false when (offset + results.size()) >= totalHits.
    *******************************************************************************/
   @Test
   void testHasMore_falseWhenOnLastPage() throws QException
   {
      OpenSearchDocument doc1 = new OpenSearchDocument()
         .withSourceTable("orders").withRecordId("4").withRecordLabel("Order 4");
      OpenSearchDocument doc2 = new OpenSearchDocument()
         .withSourceTable("orders").withRecordId("5").withRecordLabel("Order 5");

      List<Hit<OpenSearchDocument>> hits = List.of(
         buildMockHit(doc1, 1.0, null),
         buildMockHit(doc2, 0.9, null));

      // 2 results, offset=3, totalHits=5 → hasMore = (3+2) < 5 = false
      SearchResponse<OpenSearchDocument> response = buildMockResponse(hits, 5L);
      when(mockClient.search(anyString(), any(), anyInt(), anyInt(), anyList())).thenReturn(response);

      QuickSearchOutput output = action.execute(new QuickSearchInput()
         .withSearchTerm("order")
         .withOffset(3)
         .withLimit(2));

      assertThat(output.getHasMore()).isFalse();
   }



   /*******************************************************************************
    ** tableName filter is passed through to client.search() when it names a
    ** configured table.
    *******************************************************************************/
   @Test
   void testTableNameFilter_passedToClient() throws QException
   {
      SearchResponse<OpenSearchDocument> response = buildMockResponse(Collections.emptyList(), 0L);
      when(mockClient.search(anyString(), anyCollection(), anyInt(), anyInt(), anyList())).thenReturn(response);

      action.execute(new QuickSearchInput()
         .withSearchTerm("widget")
         .withTableName("orders"));

      verify(mockClient).search(anyString(), eq(List.of("orders")), anyInt(), anyInt(), anyList());
   }



   /*******************************************************************************
    ** null tableName is passed through to client.search() as null.
    *******************************************************************************/
   @Test
   void testNullTableName_searchesAllConfiguredTables() throws QException
   {
      SearchResponse<OpenSearchDocument> response = buildMockResponse(Collections.emptyList(), 0L);
      when(mockClient.search(anyString(), anyCollection(), anyInt(), anyInt(), anyList())).thenReturn(response);

      action.execute(new QuickSearchInput()
         .withSearchTerm("widget")
         .withTableName(null));

      verify(mockClient).search(anyString(), anyCollection(), anyInt(), anyInt(), anyList());
   }



   /*******************************************************************************
    ** Search term is normalized (trimmed, whitespace collapsed, case kept)
    ** before being passed to client.
    *******************************************************************************/
   @Test
   void testSearchTerm_isNormalizedBeforeClientCall() throws QException
   {
      SearchResponse<OpenSearchDocument> response = buildMockResponse(Collections.emptyList(), 0L);
      when(mockClient.search(anyString(), any(), anyInt(), anyInt(), anyList())).thenReturn(response);

      action.execute(new QuickSearchInput().withSearchTerm("  HELLO  WORLD  "));

      ArgumentCaptor<String> termCaptor = ArgumentCaptor.forClass(String.class);
      verify(mockClient).search(termCaptor.capture(), any(), anyInt(), anyInt(), anyList());

      assertThat(termCaptor.getValue()).isEqualTo("HELLO WORLD");
   }



   /*******************************************************************************
    ** A one-character term is below the minimum and returns empty output
    ** without touching the client.
    *******************************************************************************/
   @Test
   void testOneCharacterTerm_returnsEmptyWithoutSearching() throws QException
   {
      QuickSearchOutput output = action.execute(new QuickSearchInput().withSearchTerm(" a "));

      assertThat(output.getResults()).isEmpty();
      assertThat(output.getTotalHits()).isEqualTo(0L);
      verify(mockClient, never()).search(anyString(), any(), anyInt(), anyInt(), anyList());
   }



   /*******************************************************************************
    ** A term over 100 characters is rejected.
    *******************************************************************************/
   @Test
   void testTermOver100Characters_throws() throws QException
   {
      String longTerm = "x".repeat(QuickSearchAction.MAX_TERM_LENGTH + 1);

      assertThatThrownBy(() -> action.execute(new QuickSearchInput().withSearchTerm(longTerm)))
         .isInstanceOf(QException.class)
         .hasMessageContaining("too long");

      verify(mockClient, never()).search(anyString(), any(), anyInt(), anyInt(), anyList());
   }



   /*******************************************************************************
    ** A term of exactly 100 characters is accepted.
    *******************************************************************************/
   @Test
   void testTermOfExactly100Characters_accepted() throws QException
   {
      SearchResponse<OpenSearchDocument> response = buildMockResponse(Collections.emptyList(), 0L);
      when(mockClient.search(anyString(), any(), anyInt(), anyInt(), anyList())).thenReturn(response);

      action.execute(new QuickSearchInput().withSearchTerm("x".repeat(QuickSearchAction.MAX_TERM_LENGTH)));

      verify(mockClient).search(anyString(), any(), anyInt(), anyInt(), anyList());
   }



   /*******************************************************************************
    ** limit above the configured maxSearchLimit is clamped to it.
    *******************************************************************************/
   @Test
   void testLimitAboveMaxSearchLimit_clamped() throws QException
   {
      QuickSearchQBitContext.setConfig(new QuickSearchQBitConfig().withApplyRecordSecurityLocks(false).withMaxSearchLimit(40));
      SearchResponse<OpenSearchDocument> response = buildMockResponse(Collections.emptyList(), 0L);
      when(mockClient.search(anyString(), any(), anyInt(), anyInt(), anyList())).thenReturn(response);

      action.execute(new QuickSearchInput().withSearchTerm("test").withLimit(500));

      verify(mockClient).search(anyString(), any(), eq(40), anyInt(), anyList());
   }



   /*******************************************************************************
    ** Without a config the limit is still capped at the default maximum.
    *******************************************************************************/
   @Test
   void testLimitAboveDefaultMax_noConfig_clampedTo100() throws QException
   {
      QuickSearchQBitContext.setConfig(null);
      SearchResponse<OpenSearchDocument> response = buildMockResponse(Collections.emptyList(), 0L);
      when(mockClient.search(anyString(), any(), anyInt(), anyInt(), anyList())).thenReturn(response);

      action.execute(new QuickSearchInput().withSearchTerm("test").withLimit(500));

      ////////////////////////////////////////////////////////////////////////
      // no config means locks default to on, so the fetch size is doubled  //
      ////////////////////////////////////////////////////////////////////////
      verify(mockClient).search(anyString(), any(), eq(200), anyInt(), anyList());
   }



   /*******************************************************************************
    ** offset + limit beyond the OpenSearch result window shrinks the limit so
    ** the window is not exceeded.
    *******************************************************************************/
   @Test
   void testOffsetPlusLimit_beyondResultWindow_limitShrunk() throws QException
   {
      SearchResponse<OpenSearchDocument> response = buildMockResponse(Collections.emptyList(), 0L);
      when(mockClient.search(anyString(), any(), anyInt(), anyInt(), anyList())).thenReturn(response);

      action.execute(new QuickSearchInput().withSearchTerm("test").withLimit(25).withOffset(QuickSearchOpenSearchClient.MAX_RESULT_WINDOW - 10));

      verify(mockClient).search(anyString(), any(), eq(10), eq(QuickSearchOpenSearchClient.MAX_RESULT_WINDOW - 10), anyList());
   }



   /*******************************************************************************
    ** An offset at or past the result window returns empty output without
    ** searching.
    *******************************************************************************/
   @Test
   void testOffsetAtResultWindow_returnsEmptyWithoutSearching() throws QException
   {
      QuickSearchOutput output = action.execute(new QuickSearchInput().withSearchTerm("test").withLimit(25).withOffset(QuickSearchOpenSearchClient.MAX_RESULT_WINDOW));

      assertThat(output.getResults()).isEmpty();
      assertThat(output.getHasMore()).isFalse();
      verify(mockClient, never()).search(anyString(), any(), anyInt(), anyInt(), anyList());
   }



   /*******************************************************************************
    ** tableNames is intersected with the configured tables.
    *******************************************************************************/
   @Test
   void testTableNames_intersectedWithConfiguredTables() throws QException
   {
      SearchResponse<OpenSearchDocument> response = buildMockResponse(Collections.emptyList(), 0L);
      when(mockClient.search(anyString(), anyCollection(), anyInt(), anyInt(), anyList())).thenReturn(response);

      action.execute(new QuickSearchInput().withSearchTerm("widget").withTableNames(List.of("orders", "notConfigured")));

      verify(mockClient).search(anyString(), eq(List.of("orders")), anyInt(), anyInt(), anyList());
   }



   /*******************************************************************************
    ** tableNames takes precedence over the single tableName.
    *******************************************************************************/
   @Test
   void testTableNames_takesPrecedenceOverTableName() throws QException
   {
      QuickSearchQBitContext.setDiscoveredTables(List.of(
         new QuickSearchableTableConfig().withTableName("orders").withPrimaryKeyField("id").withSearchableFields(List.of("orderNumber")),
         new QuickSearchableTableConfig().withTableName("customers").withPrimaryKeyField("id").withSearchableFields(List.of("name"))));
      SearchResponse<OpenSearchDocument> response = buildMockResponse(Collections.emptyList(), 0L);
      when(mockClient.search(anyString(), anyCollection(), anyInt(), anyInt(), anyList())).thenReturn(response);

      action.execute(new QuickSearchInput().withSearchTerm("widget").withTableName("orders").withTableNames(List.of("customers")));

      verify(mockClient).search(anyString(), eq(List.of("customers")), anyInt(), anyInt(), anyList());
   }



   /*******************************************************************************
    ** A request naming only unconfigured tables returns empty output without
    ** searching.
    *******************************************************************************/
   @Test
   void testTableNames_noneConfigured_returnsEmptyWithoutSearching() throws QException
   {
      QuickSearchOutput output = action.execute(new QuickSearchInput().withSearchTerm("widget").withTableNames(List.of("notConfigured")));

      assertThat(output.getResults()).isEmpty();
      verify(mockClient, never()).search(anyString(), any(), anyInt(), anyInt(), anyList());
   }



   /*******************************************************************************
    ** limitPerTable searches each allowed table separately with that limit and
    ** merges the hits.
    *******************************************************************************/
   @Test
   void testLimitPerTable_searchesEachTableSeparately() throws QException
   {
      QuickSearchQBitContext.setDiscoveredTables(List.of(
         new QuickSearchableTableConfig().withTableName("orders").withPrimaryKeyField("id").withSearchableFields(List.of("orderNumber")),
         new QuickSearchableTableConfig().withTableName("customers").withPrimaryKeyField("id").withSearchableFields(List.of("name"))));

      Hit<OpenSearchDocument> orderHit    = buildMockHit(new OpenSearchDocument().withSourceTable("orders").withRecordId("1").withRecordLabel("Order 1"), 2.0, null);
      Hit<OpenSearchDocument> customerHit = buildMockHit(new OpenSearchDocument().withSourceTable("customers").withRecordId("9").withRecordLabel("Customer 9"), 1.0, null);

      SearchResponse<OpenSearchDocument> orderResponse    = buildMockResponse(List.of(orderHit), 7L);
      SearchResponse<OpenSearchDocument> customerResponse = buildMockResponse(List.of(customerHit), 1L);
      when(mockClient.search(anyString(), eq(List.of("orders")), anyInt(), anyInt(), anyList())).thenReturn(orderResponse);
      when(mockClient.search(anyString(), eq(List.of("customers")), anyInt(), anyInt(), anyList())).thenReturn(customerResponse);

      QuickSearchOutput output = action.execute(new QuickSearchInput().withSearchTerm("widget").withLimitPerTable(3));

      verify(mockClient).search(anyString(), eq(List.of("orders")), eq(3), eq(0), anyList());
      verify(mockClient).search(anyString(), eq(List.of("customers")), eq(3), eq(0), anyList());

      assertThat(output.getResults()).extracting(QuickSearchResult::getRecordId).containsExactly("1", "9");
      assertThat(output.getTotalHits()).isEqualTo(8L);
      assertThat(output.getHasMore()).as("orders has 7 hits but only 1 was returned").isTrue();
   }



   /*******************************************************************************
    ** limitPerTable is capped at maxSearchLimit and trims each table's hits.
    *******************************************************************************/
   @Test
   void testLimitPerTable_aboveMax_clampedAndHitsTrimmed() throws QException
   {
      QuickSearchQBitContext.setConfig(new QuickSearchQBitConfig().withApplyRecordSecurityLocks(false).withMaxSearchLimit(2));

      List<Hit<OpenSearchDocument>> hits = List.of(
         buildMockHit(new OpenSearchDocument().withSourceTable("orders").withRecordId("1"), 3.0, null),
         buildMockHit(new OpenSearchDocument().withSourceTable("orders").withRecordId("2"), 2.0, null),
         buildMockHit(new OpenSearchDocument().withSourceTable("orders").withRecordId("3"), 1.0, null));
      SearchResponse<OpenSearchDocument> response = buildMockResponse(hits, 3L);
      when(mockClient.search(anyString(), anyCollection(), anyInt(), anyInt(), anyList())).thenReturn(response);

      QuickSearchOutput output = action.execute(new QuickSearchInput().withSearchTerm("widget").withLimitPerTable(50));

      verify(mockClient).search(anyString(), eq(List.of("orders")), eq(2), eq(0), anyList());
      assertThat(output.getResults()).extracting(QuickSearchResult::getRecordId).containsExactly("1", "2");
      assertThat(output.getHasMore()).isTrue();
   }



   /*******************************************************************************
    ** Without a QInstance in context the table label falls back to the table
    ** name.
    *******************************************************************************/
   @Test
   void testTableLabel_noQInstance_fallsBackToTableName() throws QException
   {
      Hit<OpenSearchDocument> hit = buildMockHit(new OpenSearchDocument().withSourceTable("orders").withRecordId("1"), 1.0, null);
      SearchResponse<OpenSearchDocument> response = buildMockResponse(List.of(hit), 1L);
      when(mockClient.search(anyString(), any(), anyInt(), anyInt(), anyList())).thenReturn(response);

      QuickSearchOutput output = action.execute(new QuickSearchInput().withSearchTerm("widget"));

      assertThat(output.getResults().get(0).getTableLabel()).isEqualTo("orders");
   }



   /*******************************************************************************
    ** totalHitsIsLowerBound mirrors a "gte" total relation from OpenSearch.
    *******************************************************************************/
   @Test
   void testTotalHitsIsLowerBound_whenRelationIsGte() throws QException
   {
      SearchResponse<OpenSearchDocument> response = buildMockResponse(Collections.emptyList(), 10000L);
      when(response.hits().total().relation()).thenReturn(TotalHitsRelation.Gte);
      when(mockClient.search(anyString(), any(), anyInt(), anyInt(), anyList())).thenReturn(response);

      QuickSearchOutput output = action.execute(new QuickSearchInput().withSearchTerm("widget"));

      assertThat(output.getTotalHitsIsLowerBound()).isTrue();
   }



   /*******************************************************************************
    ** A hit without a source document is skipped.
    *******************************************************************************/
   @Test
   void testHitWithoutSource_skipped() throws QException
   {
      Hit<OpenSearchDocument> hit = buildMockHit(null, 1.0, null);
      SearchResponse<OpenSearchDocument> response = buildMockResponse(List.of(hit), 1L);
      when(mockClient.search(anyString(), any(), anyInt(), anyInt(), anyList())).thenReturn(response);

      QuickSearchOutput output = action.execute(new QuickSearchInput().withSearchTerm("widget"));

      assertThat(output.getResults()).isEmpty();
   }



   /*******************************************************************************
    ** A missing client is reported as a QException.
    *******************************************************************************/
   @Test
   void testNoClient_throwsQException()
   {
      QuickSearchQBitContext.setClient(null);

      assertThatThrownBy(() -> action.execute(new QuickSearchInput().withSearchTerm("widget")))
         .isInstanceOf(QException.class)
         .hasMessageContaining("not initialized");
   }



   /*******************************************************************************
    ** An OpenSearchException escaping the client surfaces as a QException.
    *******************************************************************************/
   @Test
   void testOpenSearchException_surfacesAsQException() throws QException
   {
      OpenSearchException openSearchException = new OpenSearchException(ErrorResponse.of(e -> e
         .status(500)
         .error(c -> c.type("search_phase_execution_exception").reason("all shards failed"))));
      when(mockClient.search(anyString(), any(), anyInt(), anyInt(), anyList())).thenThrow(openSearchException);

      assertThatThrownBy(() -> action.execute(new QuickSearchInput().withSearchTerm("widget")))
         .isInstanceOf(QException.class)
         .hasMessageContaining("all shards failed");
   }

}

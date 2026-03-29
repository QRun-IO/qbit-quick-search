/*
 * QBit Quick Search
 * Copyright (C) 2024-2025 QRun-IO, LLC
 * https://www.qrun.io | https://github.com/QRun-IO
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.kingsrook.qbits.quicksearch.actions;


import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitContext;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.core.search.TotalHits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/*******************************************************************************
 ** Tests for QuickSearchAction.
 **
 ** Uses Mockito to mock QuickSearchOpenSearchClient and the OpenSearch
 ** SearchResponse chain (SearchResponse → HitsMetadata → TotalHits / Hit).
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
    ** tableName filter is passed through to client.search().
    *******************************************************************************/
   @Test
   void testTableNameFilter_passedToClient() throws QException
   {
      SearchResponse<OpenSearchDocument> response = buildMockResponse(Collections.emptyList(), 0L);
      when(mockClient.search(anyString(), anyString(), anyInt(), anyInt(), anyList())).thenReturn(response);

      action.execute(new QuickSearchInput()
         .withSearchTerm("widget")
         .withTableName("products"));

      verify(mockClient).search(anyString(), eq("products"), anyInt(), anyInt(), anyList());
   }



   /*******************************************************************************
    ** null tableName is passed through to client.search() as null.
    *******************************************************************************/
   @Test
   void testNullTableName_passedAsNullToClient() throws QException
   {
      SearchResponse<OpenSearchDocument> response = buildMockResponse(Collections.emptyList(), 0L);
      when(mockClient.search(anyString(), isNull(), anyInt(), anyInt(), anyList())).thenReturn(response);

      action.execute(new QuickSearchInput()
         .withSearchTerm("widget")
         .withTableName(null));

      verify(mockClient).search(anyString(), isNull(), anyInt(), anyInt(), anyList());
   }



   /*******************************************************************************
    ** Search term is normalized (lowercased, trimmed) before being passed to client.
    *******************************************************************************/
   @Test
   void testSearchTerm_isNormalizedBeforeClientCall() throws QException
   {
      SearchResponse<OpenSearchDocument> response = buildMockResponse(Collections.emptyList(), 0L);
      when(mockClient.search(anyString(), any(), anyInt(), anyInt(), anyList())).thenReturn(response);

      action.execute(new QuickSearchInput().withSearchTerm("  HELLO  WORLD  "));

      ArgumentCaptor<String> termCaptor = ArgumentCaptor.forClass(String.class);
      verify(mockClient).search(termCaptor.capture(), any(), anyInt(), anyInt(), anyList());

      assertThat(termCaptor.getValue()).isEqualTo("hello world");
   }

}

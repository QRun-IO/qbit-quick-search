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


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper;
import com.kingsrook.qqq.backend.core.actions.permissions.TablePermissionSubType;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qbits.quicksearch.BaseQuickSearchTest;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitContext;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/*******************************************************************************
 ** Tests for the permission-aware parts of QuickSearchAction: table READ
 ** permission, the record-security post-filter through QueryAction, the
 ** access-safe totals it reports, and tableLabel from the QInstance.
 **
 ** Uses the memory-backed QInstance, QSession and mock client from
 ** BaseQuickSearchTest; record security locks are on (the default).
 *******************************************************************************/
@SuppressWarnings("unchecked")
class QuickSearchActionSecurityTest extends BaseQuickSearchTest
{
   private static final String TABLE_LABEL = "Test Entity";

   private QuickSearchOpenSearchClient mockClient;
   private QuickSearchAction           action;



   /*******************************************************************************
    **
    *******************************************************************************/
   @BeforeEach
   void setUp()
   {
      mockClient = QuickSearchQBitContext.getClient();
      action     = new QuickSearchAction();
      QContext.getQInstance().getTable(TEST_ENTITY_TABLE).withLabel(TABLE_LABEL);
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
    ** Stub the client search with a response built before the stubbing call,
    ** so no mock is configured while another stubbing is in progress.
    *******************************************************************************/
   private void stubSearch(List<Hit<OpenSearchDocument>> hits, long totalHits) throws QException
   {
      SearchResponse<OpenSearchDocument> response = buildMockResponse(hits, totalHits);
      when(mockClient.search(anyString(), any(), anyInt(), anyInt(), anyList())).thenReturn(response);
   }



   /*******************************************************************************
    ** Build a mocked hit for a testEntity record.
    *******************************************************************************/
   private Hit<OpenSearchDocument> hitFor(String recordId)
   {
      Hit<OpenSearchDocument> hit = mock(Hit.class);
      when(hit.source()).thenReturn(new OpenSearchDocument().withSourceTable(TEST_ENTITY_TABLE).withRecordId(recordId).withRecordLabel("Entity " + recordId));
      when(hit.score()).thenReturn(1.0);
      when(hit.highlight()).thenReturn(Map.of());
      return (hit);
   }



   /*******************************************************************************
    ** Insert testEntity records with the given ids.
    *******************************************************************************/
   private void insertEntities(Integer... ids) throws QException
   {
      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(TEST_ENTITY_TABLE);
      insertInput.setRecords(List.of(ids).stream().map(id -> new QRecord().withValue("id", id).withValue("name", "Entity " + id)).toList());
      new InsertAction().execute(insertInput);
   }



   /*******************************************************************************
    ** Test: a hit whose record the query cannot return is dropped, the fetch
    ** over-reads by 2x, and the total reflects only verified-accessible hits.
    *******************************************************************************/
   @Test
   void testRecordLockPostFilter_dropsHitForRecordTheQueryCannotReturn() throws QException
   {
      insertEntities(1);
      stubSearch(List.of(hitFor("1"), hitFor("2")), 2L);

      QuickSearchOutput output = action.execute(new QuickSearchInput().withSearchTerm("entity"));

      verify(mockClient).search(anyString(), eq(List.of(TEST_ENTITY_TABLE)), eq(QuickSearchAction.DEFAULT_LIMIT * 2), eq(0), anyList());
      assertThat(output.getResults()).extracting(QuickSearchResult::getRecordId).containsExactly("1");
      assertThat(output.getTotalHits()).isEqualTo(1L);
      assertThat(output.getTotalHitsIsLowerBound()).isFalse();
      assertThat(output.getHasMore()).isFalse();
   }



   /*******************************************************************************
    ** Test: accessible hits beyond the limit are truncated and the total is
    ** reported as a lower bound.
    *******************************************************************************/
   @Test
   void testRecordLock_moreAccessibleHitsThanLimit_truncatedWithLowerBoundTotal() throws QException
   {
      insertEntities(1, 2, 3);
      stubSearch(List.of(hitFor("1"), hitFor("2"), hitFor("3")), 3L);

      QuickSearchOutput output = action.execute(new QuickSearchInput().withSearchTerm("entity").withLimit(2));

      verify(mockClient).search(anyString(), any(), eq(4), eq(0), anyList());
      assertThat(output.getResults()).extracting(QuickSearchResult::getRecordId).containsExactly("1", "2");
      assertThat(output.getTotalHits()).isEqualTo(3L);
      assertThat(output.getTotalHitsIsLowerBound()).isTrue();
      assertThat(output.getHasMore()).isTrue();
   }



   /*******************************************************************************
    ** Test: with locks disabled the raw OpenSearch total is reported and hits
    ** are not re-read from the table.
    *******************************************************************************/
   @Test
   void testLocksDisabled_rawTotalsAndNoPostFilter() throws QException
   {
      QuickSearchQBitContext.getConfig().withApplyRecordSecurityLocks(false);
      stubSearch(List.of(hitFor("1"), hitFor("2")), 40L);

      QuickSearchOutput output = action.execute(new QuickSearchInput().withSearchTerm("entity").withLimit(10));

      verify(mockClient).search(anyString(), any(), eq(10), eq(0), anyList());
      assertThat(output.getResults()).hasSize(2);
      assertThat(output.getTotalHits()).isEqualTo(40L);
      assertThat(output.getHasMore()).isTrue();
   }



   /*******************************************************************************
    ** Test: a session without READ permission on a protected table gets no
    ** hits from it, and the index is not even queried.
    *******************************************************************************/
   @Test
   void testSessionWithoutReadPermission_tableNotSearched() throws QException
   {
      QContext.getQInstance().getTable(TEST_ENTITY_TABLE).withPermissionRules(new QPermissionRules().withLevel(PermissionLevel.READ_WRITE_PERMISSIONS));

      QuickSearchOutput output = action.execute(new QuickSearchInput().withSearchTerm("entity"));

      assertThat(output.getResults()).isEmpty();
      assertThat(output.getTotalHits()).isEqualTo(0L);
      verify(mockClient, never()).search(anyString(), any(), anyInt(), anyInt(), anyList());
   }



   /*******************************************************************************
    ** Test: the same protected table is searched once the session holds the
    ** READ permission.
    *******************************************************************************/
   @Test
   void testSessionWithReadPermission_tableSearched() throws QException
   {
      QContext.getQInstance().getTable(TEST_ENTITY_TABLE).withPermissionRules(new QPermissionRules().withLevel(PermissionLevel.READ_WRITE_PERMISSIONS));
      QContext.getQSession().withPermission(PermissionsHelper.getTablePermissionName(TEST_ENTITY_TABLE, TablePermissionSubType.READ));

      insertEntities(1);
      stubSearch(List.of(hitFor("1")), 1L);

      QuickSearchOutput output = action.execute(new QuickSearchInput().withSearchTerm("entity"));

      assertThat(output.getResults()).extracting(QuickSearchResult::getRecordId).containsExactly("1");
   }



   /*******************************************************************************
    ** Test: tableLabel comes from the QInstance table label.
    *******************************************************************************/
   @Test
   void testTableLabel_populatedFromQInstance() throws QException
   {
      insertEntities(1);
      stubSearch(List.of(hitFor("1")), 1L);

      QuickSearchOutput output = action.execute(new QuickSearchInput().withSearchTerm("entity"));

      assertThat(output.getResults().get(0).getTableName()).isEqualTo(TEST_ENTITY_TABLE);
      assertThat(output.getResults().get(0).getTableLabel()).isEqualTo(TABLE_LABEL);
   }



   /*******************************************************************************
    ** Test: limitPerTable with locks on reports the filtered count per table;
    ** with every candidate read, the count is exact.
    *******************************************************************************/
   @Test
   void testLimitPerTable_withLocks_totalIsFilteredCount() throws QException
   {
      insertEntities(1);
      stubSearch(List.of(hitFor("1"), hitFor("2")), 2L);

      QuickSearchOutput output = action.execute(new QuickSearchInput().withSearchTerm("entity").withLimitPerTable(5));

      verify(mockClient).search(anyString(), eq(List.of(TEST_ENTITY_TABLE)), eq(10), eq(0), anyList());
      assertThat(output.getResults()).extracting(QuickSearchResult::getRecordId).containsExactly("1");
      assertThat(output.getTotalHits()).isEqualTo(1L);
      assertThat(output.getTotalHitsIsLowerBound()).isFalse();
      assertThat(output.getHasMore()).isFalse();
   }



   /*******************************************************************************
    ** Stub the client search over an ordered list of testEntity record ids:
    ** each call returns the slice at its from/size, as OpenSearch would.
    ** Fails the call if from + size passes the result window.
    *******************************************************************************/
   private void stubPagedSearch(List<String> recordIds) throws QException
   {
      when(mockClient.search(anyString(), any(), anyInt(), anyInt(), anyList())).thenAnswer(invocation ->
      {
         int size = invocation.getArgument(2);
         int from = invocation.getArgument(3);
         assertThat(from + size).isLessThanOrEqualTo(QuickSearchOpenSearchClient.MAX_RESULT_WINDOW);

         List<Hit<OpenSearchDocument>> hits = new ArrayList<>();
         for(int i = from; i < Math.min(recordIds.size(), from + size); i++)
         {
            hits.add(hitFor(recordIds.get(i)));
         }
         return (buildMockResponse(hits, recordIds.size()));
      });
   }



   /*******************************************************************************
    ** Run one search page and return its output.
    *******************************************************************************/
   private QuickSearchOutput page(Integer offset, Integer limit) throws QException
   {
      return (action.execute(new QuickSearchInput().withSearchTerm("entity").withOffset(offset).withLimit(limit)));
   }



   /*******************************************************************************
    ** Test: with locks on, offset counts accessible results, so consecutive
    ** pages neither overlap nor skip an accessible hit when the post-filter
    ** removes hits, and hasMore is false only on the last page.
    *******************************************************************************/
   @Test
   void testRecordLock_pagesByAccessibleResults_noOverlapNoSkip() throws QException
   {
      insertEntities(1, 3, 5, 7, 9);
      stubPagedSearch(IntStream.rangeClosed(1, 10).mapToObj(String::valueOf).toList());

      QuickSearchOutput page1 = page(0, 2);
      QuickSearchOutput page2 = page(2, 2);
      QuickSearchOutput page3 = page(4, 2);

      assertThat(page1.getResults()).extracting(QuickSearchResult::getRecordId).containsExactly("1", "3");
      assertThat(page2.getResults()).extracting(QuickSearchResult::getRecordId).containsExactly("5", "7");
      assertThat(page3.getResults()).extracting(QuickSearchResult::getRecordId).containsExactly("9");
      assertThat(page1.getHasMore()).isTrue();
      assertThat(page2.getHasMore()).isTrue();
      assertThat(page3.getHasMore()).isFalse();
      assertThat(page3.getTotalHits()).isEqualTo(5L);
      assertThat(page3.getTotalHitsIsLowerBound()).isFalse();
   }



   /*******************************************************************************
    ** Test: an offset past the last accessible result returns an empty page
    ** with hasMore false.
    *******************************************************************************/
   @Test
   void testRecordLock_offsetPastAccessibleResults_emptyPage() throws QException
   {
      insertEntities(1, 3);
      stubPagedSearch(IntStream.rangeClosed(1, 10).mapToObj(String::valueOf).toList());

      QuickSearchOutput output = page(5, 2);

      assertThat(output.getResults()).isEmpty();
      assertThat(output.getHasMore()).isFalse();
      assertThat(output.getTotalHits()).isEqualTo(2L);
   }



   /*******************************************************************************
    ** Test: per-table mode skips offset accessible results in each table.
    *******************************************************************************/
   @Test
   void testLimitPerTable_withLocks_honorsOffsetPerTable() throws QException
   {
      insertEntities(1, 3, 5, 7, 9);
      stubPagedSearch(IntStream.rangeClosed(1, 10).mapToObj(String::valueOf).toList());

      QuickSearchOutput output = action.execute(new QuickSearchInput().withSearchTerm("entity").withOffset(2).withLimitPerTable(2));

      assertThat(output.getResults()).extracting(QuickSearchResult::getRecordId).containsExactly("5", "7");
      assertThat(output.getHasMore()).isTrue();
   }



   /*******************************************************************************
    ** Test: per-table mode without locks passes offset straight to OpenSearch.
    *******************************************************************************/
   @Test
   void testLimitPerTable_locksDisabled_passesOffset() throws QException
   {
      QuickSearchQBitContext.getConfig().withApplyRecordSecurityLocks(false);
      stubPagedSearch(IntStream.rangeClosed(1, 10).mapToObj(String::valueOf).toList());

      QuickSearchOutput output = action.execute(new QuickSearchInput().withSearchTerm("entity").withOffset(8).withLimitPerTable(5));

      verify(mockClient).search(anyString(), eq(List.of(TEST_ENTITY_TABLE)), eq(5), eq(8), anyList());
      assertThat(output.getResults()).extracting(QuickSearchResult::getRecordId).containsExactly("9", "10");
      assertThat(output.getHasMore()).isFalse();
   }



   /*******************************************************************************
    ** Test: with a maxSearchLimit above half the result window, no request
    ** asks OpenSearch for more than the window (from + size at most 10,000).
    *******************************************************************************/
   @Test
   void testLargeMaxSearchLimit_requestsCappedAtResultWindow() throws QException
   {
      QuickSearchQBitConfig config = QuickSearchQBitContext.getConfig().withMaxSearchLimit(8_000);
      stubPagedSearch(IntStream.rangeClosed(1, 20).mapToObj(String::valueOf).toList());

      action.execute(new QuickSearchInput().withSearchTerm("entity").withLimitPerTable(8_000));
      action.execute(new QuickSearchInput().withSearchTerm("entity").withLimit(8_000));
      config.withApplyRecordSecurityLocks(false);
      action.execute(new QuickSearchInput().withSearchTerm("entity").withLimitPerTable(8_000).withOffset(5_000));

      ArgumentCaptor<Integer> sizes   = ArgumentCaptor.forClass(Integer.class);
      ArgumentCaptor<Integer> offsets = ArgumentCaptor.forClass(Integer.class);
      verify(mockClient, times(3)).search(anyString(), any(), sizes.capture(), offsets.capture(), anyList());
      assertThat(sizes.getAllValues()).containsExactly(QuickSearchOpenSearchClient.MAX_RESULT_WINDOW, QuickSearchOpenSearchClient.MAX_RESULT_WINDOW, 5_000);
      assertThat(offsets.getAllValues()).containsExactly(0, 0, 5_000);
   }

}

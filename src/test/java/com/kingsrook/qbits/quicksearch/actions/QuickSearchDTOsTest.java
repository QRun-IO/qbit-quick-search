/*
 * Copyright 2024 Kingsrook, LLC
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

package com.kingsrook.qbits.quicksearch.actions;


import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Tests for QuickSearchInput, QuickSearchOutput, and QuickSearchResult DTOs.
 *******************************************************************************/
class QuickSearchDTOsTest
{

   /*******************************************************************************
    ** Test that QuickSearchInput fluent setters return this and getters return values.
    *******************************************************************************/
   @Test
   void testQuickSearchInputFluentSetters()
   {
      QuickSearchInput input = new QuickSearchInput()
         .withSearchTerm("hello")
         .withTableName("myTable")
         .withLimit(10)
         .withOffset(20);

      assertThat(input.getSearchTerm()).isEqualTo("hello");
      assertThat(input.getTableName()).isEqualTo("myTable");
      assertThat(input.getLimit()).isEqualTo(10);
      assertThat(input.getOffset()).isEqualTo(20);
   }

   /*******************************************************************************
    ** Test that QuickSearchInput with setter returns this (fluent chaining).
    *******************************************************************************/
   @Test
   void testQuickSearchInputWithReturnsThis()
   {
      QuickSearchInput input = new QuickSearchInput();
      assertThat(input.withSearchTerm("test")).isSameAs(input);
      assertThat(input.withTableName("table")).isSameAs(input);
      assertThat(input.withLimit(5)).isSameAs(input);
      assertThat(input.withOffset(0)).isSameAs(input);
   }

   /*******************************************************************************
    ** Test that QuickSearchInput defaults are null.
    *******************************************************************************/
   @Test
   void testQuickSearchInputDefaults()
   {
      QuickSearchInput input = new QuickSearchInput();
      assertThat(input.getSearchTerm()).isNull();
      assertThat(input.getTableName()).isNull();
      assertThat(input.getLimit()).isNull();
      assertThat(input.getOffset()).isNull();
   }

   /*******************************************************************************
    ** Test that QuickSearchResult fluent setters return this and getters return values.
    *******************************************************************************/
   @Test
   void testQuickSearchResultFluentSetters()
   {
      QuickSearchResult result = new QuickSearchResult()
         .withTableName("orders")
         .withRecordId("42")
         .withRecordLabel("Order #42")
         .withScore(0.95f)
         .withHighlightSnippet("matched text here");

      assertThat(result.getTableName()).isEqualTo("orders");
      assertThat(result.getRecordId()).isEqualTo("42");
      assertThat(result.getRecordLabel()).isEqualTo("Order #42");
      assertThat(result.getScore()).isEqualTo(0.95f);
      assertThat(result.getHighlightSnippet()).isEqualTo("matched text here");
   }

   /*******************************************************************************
    ** Test that QuickSearchResult withX methods return this.
    *******************************************************************************/
   @Test
   void testQuickSearchResultWithReturnsThis()
   {
      QuickSearchResult result = new QuickSearchResult();
      assertThat(result.withTableName("t")).isSameAs(result);
      assertThat(result.withRecordId("1")).isSameAs(result);
      assertThat(result.withRecordLabel("label")).isSameAs(result);
      assertThat(result.withScore(1.0f)).isSameAs(result);
      assertThat(result.withHighlightSnippet("snip")).isSameAs(result);
   }

   /*******************************************************************************
    ** Test that QuickSearchOutput fluent setters return this and getters return values.
    *******************************************************************************/
   @Test
   void testQuickSearchOutputFluentSetters()
   {
      QuickSearchResult r1 = new QuickSearchResult().withRecordId("1");
      QuickSearchResult r2 = new QuickSearchResult().withRecordId("2");
      List<QuickSearchResult> results = List.of(r1, r2);

      QuickSearchOutput output = new QuickSearchOutput()
         .withResults(results)
         .withTotalHits(100L)
         .withHasMore(true);

      assertThat(output.getResults()).containsExactly(r1, r2);
      assertThat(output.getTotalHits()).isEqualTo(100L);
      assertThat(output.getHasMore()).isTrue();
   }

   /*******************************************************************************
    ** Test that QuickSearchOutput withX methods return this.
    *******************************************************************************/
   @Test
   void testQuickSearchOutputWithReturnsThis()
   {
      QuickSearchOutput output = new QuickSearchOutput();
      assertThat(output.withResults(List.of())).isSameAs(output);
      assertThat(output.withTotalHits(0L)).isSameAs(output);
      assertThat(output.withHasMore(false)).isSameAs(output);
   }

}

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


import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Tests for QuickSearch DTOs (Input, Output, Result).
 *******************************************************************************/
class QuickSearchDTOsTest
{

   /***************************************************************************
    ** Test QuickSearchInput getters and setters.
    ***************************************************************************/
   @Test
   void testQuickSearchInput_gettersAndSetters()
   {
      QuickSearchInput input = new QuickSearchInput()
         .withSearchTerm("test query")
         .withTableName("orders")
         .withLimit(25);

      assertThat(input.getSearchTerm()).isEqualTo("test query");
      assertThat(input.getTableName()).isEqualTo("orders");
      assertThat(input.getLimit()).isEqualTo(25);
   }



   /***************************************************************************
    ** Test QuickSearchInput standard setters.
    ***************************************************************************/
   @Test
   void testQuickSearchInput_standardSetters()
   {
      QuickSearchInput input = new QuickSearchInput();
      input.setSearchTerm("search");
      input.setTableName("customers");
      input.setLimit(100);

      assertThat(input.getSearchTerm()).isEqualTo("search");
      assertThat(input.getTableName()).isEqualTo("customers");
      assertThat(input.getLimit()).isEqualTo(100);
   }



   /***************************************************************************
    ** Test QuickSearchOutput getters and setters.
    ***************************************************************************/
   @Test
   void testQuickSearchOutput_gettersAndSetters()
   {
      List<QuickSearchResult> results = List.of(
         new QuickSearchResult().withRecordId("1"),
         new QuickSearchResult().withRecordId("2")
      );

      QuickSearchOutput output = new QuickSearchOutput()
         .withResults(results)
         .withTotalCount(2);

      assertThat(output.getResults()).hasSize(2);
      assertThat(output.getTotalCount()).isEqualTo(2);
   }



   /***************************************************************************
    ** Test QuickSearchOutput standard setters.
    ***************************************************************************/
   @Test
   void testQuickSearchOutput_standardSetters()
   {
      List<QuickSearchResult> results = List.of(new QuickSearchResult());

      QuickSearchOutput output = new QuickSearchOutput();
      output.setResults(results);
      output.setTotalCount(1);

      assertThat(output.getResults()).hasSize(1);
      assertThat(output.getTotalCount()).isEqualTo(1);
   }



   /***************************************************************************
    ** Test QuickSearchResult getters and setters.
    ***************************************************************************/
   @Test
   void testQuickSearchResult_gettersAndSetters()
   {
      QuickSearchResult result = new QuickSearchResult()
         .withTableName("orders")
         .withRecordId("12345")
         .withRecordLabel("Order #12345")
         .withMatchedText("customer john doe shipping");

      assertThat(result.getTableName()).isEqualTo("orders");
      assertThat(result.getRecordId()).isEqualTo("12345");
      assertThat(result.getRecordLabel()).isEqualTo("Order #12345");
      assertThat(result.getMatchedText()).isEqualTo("customer john doe shipping");
   }



   /***************************************************************************
    ** Test QuickSearchResult standard setters.
    ***************************************************************************/
   @Test
   void testQuickSearchResult_standardSetters()
   {
      QuickSearchResult result = new QuickSearchResult();
      result.setTableName("customers");
      result.setRecordId("999");
      result.setRecordLabel("Customer 999");
      result.setMatchedText("acme corp");

      assertThat(result.getTableName()).isEqualTo("customers");
      assertThat(result.getRecordId()).isEqualTo("999");
      assertThat(result.getRecordLabel()).isEqualTo("Customer 999");
      assertThat(result.getMatchedText()).isEqualTo("acme corp");
   }



   /***************************************************************************
    ** Test fluent setters return this.
    ***************************************************************************/
   @Test
   void testFluentSetters_returnThis()
   {
      QuickSearchInput input = new QuickSearchInput();
      assertThat(input.withSearchTerm("test")).isSameAs(input);
      assertThat(input.withTableName("table")).isSameAs(input);
      assertThat(input.withLimit(10)).isSameAs(input);

      QuickSearchOutput output = new QuickSearchOutput();
      assertThat(output.withResults(List.of())).isSameAs(output);
      assertThat(output.withTotalCount(0)).isSameAs(output);

      QuickSearchResult result = new QuickSearchResult();
      assertThat(result.withTableName("t")).isSameAs(result);
      assertThat(result.withRecordId("1")).isSameAs(result);
      assertThat(result.withRecordLabel("l")).isSameAs(result);
      assertThat(result.withMatchedText("m")).isSameAs(result);
   }

}

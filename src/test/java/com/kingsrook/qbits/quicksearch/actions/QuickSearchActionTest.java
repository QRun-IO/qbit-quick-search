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


import com.kingsrook.qbits.quicksearch.BaseQuickSearchTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Tests for QuickSearchAction.
 *******************************************************************************/
class QuickSearchActionTest extends BaseQuickSearchTest
{

   /***************************************************************************
    ** Test execute with empty search term returns empty results.
    ***************************************************************************/
   @Test
   void testExecute_emptySearchTerm_returnsEmptyResults() throws Exception
   {
      QuickSearchAction action = new QuickSearchAction();
      QuickSearchInput input = new QuickSearchInput()
         .withSearchTerm("");

      QuickSearchOutput output = action.execute(input);

      assertThat(output.getResults()).isEmpty();
      assertThat(output.getTotalCount()).isEqualTo(0);
   }



   /***************************************************************************
    ** Test execute with null search term returns empty results.
    ***************************************************************************/
   @Test
   void testExecute_nullSearchTerm_returnsEmptyResults() throws Exception
   {
      QuickSearchAction action = new QuickSearchAction();
      QuickSearchInput input = new QuickSearchInput()
         .withSearchTerm(null);

      QuickSearchOutput output = action.execute(input);

      assertThat(output.getResults()).isEmpty();
      assertThat(output.getTotalCount()).isEqualTo(0);
   }



   /***************************************************************************
    ** Test execute with whitespace search term returns empty results.
    ***************************************************************************/
   @Test
   void testExecute_whitespaceSearchTerm_returnsEmptyResults() throws Exception
   {
      QuickSearchAction action = new QuickSearchAction();
      QuickSearchInput input = new QuickSearchInput()
         .withSearchTerm("   ");

      QuickSearchOutput output = action.execute(input);

      assertThat(output.getResults()).isEmpty();
      assertThat(output.getTotalCount()).isEqualTo(0);
   }



   /***************************************************************************
    ** Test execute with valid search term throws exception when OpenSearch
    ** is not running, with expected error message.
    ***************************************************************************/
   @Test
   void testExecute_validSearchTerm_throwsWhenOpenSearchUnavailable()
   {
      QuickSearchAction action = new QuickSearchAction();
      QuickSearchInput input = new QuickSearchInput()
         .withSearchTerm("test")
         .withLimit(null);

      assertThatThrownBy(() -> action.execute(input))
         .isInstanceOf(Exception.class)
         .hasMessageContaining("Failed to search documents");
   }



   /***************************************************************************
    ** Test execute with negative limit throws exception when OpenSearch
    ** is not running, verifying limit handling occurs before the search.
    ***************************************************************************/
   @Test
   void testExecute_negativeLimit_throwsWhenOpenSearchUnavailable()
   {
      QuickSearchAction action = new QuickSearchAction();
      QuickSearchInput input = new QuickSearchInput()
         .withSearchTerm("test")
         .withLimit(-1);

      assertThatThrownBy(() -> action.execute(input))
         .isInstanceOf(Exception.class)
         .hasMessageContaining("Failed to search documents");
   }

}

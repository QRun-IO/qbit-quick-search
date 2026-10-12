/*
 * Copyright 2026 Kingsrook, LLC
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


import com.kingsrook.qbits.quicksearch.metadata.QuickSearchSearchableTablePossibleValueSourceProducer;
import com.kingsrook.qbits.quicksearch.model.QuickSearchDiagnosticEvent;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;
import com.kingsrook.qbits.quicksearch.model.QuickSearchValidationRun;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Table, possible-value source and permission names follow the owner naming
 ** rule (lowercase snake_case, prefixed quick_search_), and the deprecated
 ** LEGACY_* constants keep the pre-1.1.0-RC.3 names for upgrade scripts.
 *******************************************************************************/
@SuppressWarnings("deprecation")
class QuickSearchTableNamesTest
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testTableNames_areSnakeCaseWithQuickSearchPrefix()
   {
      assertThat(QuickSearchIndex.TABLE_NAME).isEqualTo("quick_search_index");
      assertThat(QuickSearchIndexRun.TABLE_NAME).isEqualTo("quick_search_index_run");
      assertThat(QuickSearchFailedEvent.TABLE_NAME).isEqualTo("quick_search_failed_event");
      assertThat(QuickSearchDiagnosticEvent.TABLE_NAME).isEqualTo("quick_search_diagnostic_event");
      assertThat(QuickSearchValidationRun.TABLE_NAME).isEqualTo("quick_search_validation_run");
      assertThat(QuickSearchSearchableTablePossibleValueSourceProducer.NAME).isEqualTo("quick_search_searchable_table");
      assertThat(QuickSearchQBitConfig.DEFAULT_ADMIN_PERMISSION_BASE_NAME).isEqualTo("quick_search_admin");
      assertThat(QuickSearchQBitConfig.DEFAULT_DEBUG_PERMISSION_BASE_NAME).isEqualTo("quick_search_debug");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testLegacyConstants_keepOldNames()
   {
      assertThat(QuickSearchIndex.LEGACY_TABLE_NAME).isEqualTo("quickSearchIndex");
      assertThat(QuickSearchIndexRun.LEGACY_TABLE_NAME).isEqualTo("quickSearchIndexRun");
      assertThat(QuickSearchFailedEvent.LEGACY_TABLE_NAME).isEqualTo("quickSearchFailedEvent");
      assertThat(QuickSearchDiagnosticEvent.LEGACY_TABLE_NAME).isEqualTo("quickSearchDiagnosticEvent");
      assertThat(QuickSearchValidationRun.LEGACY_TABLE_NAME).isEqualTo("quickSearchValidationRun");
      assertThat(QuickSearchSearchableTablePossibleValueSourceProducer.LEGACY_NAME).isEqualTo("quickSearchSearchableTable");
      assertThat(QuickSearchQBitConfig.LEGACY_ADMIN_PERMISSION_BASE_NAME).isEqualTo("quickSearchAdmin");
      assertThat(QuickSearchQBitConfig.LEGACY_DEBUG_PERMISSION_BASE_NAME).isEqualTo("quickSearchDebug");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testApplyTablePrefix_snakeCasesAndJoinsWithUnderscore()
   {
      assertThat(new QuickSearchQBitConfig().applyTablePrefix(QuickSearchIndex.TABLE_NAME)).isEqualTo("quick_search_index");
      assertThat(new QuickSearchQBitConfig().withTableNamePrefix("").applyTablePrefix(QuickSearchIndex.TABLE_NAME)).isEqualTo("quick_search_index");
      assertThat(new QuickSearchQBitConfig().withTableNamePrefix("acme").applyTablePrefix(QuickSearchIndex.TABLE_NAME)).isEqualTo("acme_quick_search_index");
      assertThat(new QuickSearchQBitConfig().withTableNamePrefix("acme_").applyTablePrefix(QuickSearchIndex.TABLE_NAME)).isEqualTo("acme_quick_search_index");
      assertThat(new QuickSearchQBitConfig().withTableNamePrefix("myApp_").applyTablePrefix(QuickSearchIndex.TABLE_NAME)).isEqualTo("my_app_quick_search_index");
   }

}

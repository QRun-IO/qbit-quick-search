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
package com.kingsrook.qbits.quicksearch.customizers;


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.quicksearch.BaseQuickSearchTest;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Tests for QuickSearchDeleteCustomizer.
 *******************************************************************************/
class QuickSearchDeleteCustomizerTest extends BaseQuickSearchTest
{

   /***************************************************************************
    ** Test postDelete with null records returns null.
    ***************************************************************************/
   @Test
   void testPostDelete_nullRecords_returnsNull() throws Exception
   {
      QuickSearchDeleteCustomizer customizer = new QuickSearchDeleteCustomizer(config, TEST_TABLE_NAME, "id");

      DeleteInput deleteInput = new DeleteInput();
      deleteInput.setTableName(TEST_TABLE_NAME);

      List<QRecord> result = customizer.postDelete(deleteInput, null);

      assertThat(result).isNull();
   }



   /***************************************************************************
    ** Test postDelete with empty records returns empty list.
    ***************************************************************************/
   @Test
   void testPostDelete_emptyRecords_returnsEmptyList() throws Exception
   {
      QuickSearchDeleteCustomizer customizer = new QuickSearchDeleteCustomizer(config, TEST_TABLE_NAME, "id");

      DeleteInput deleteInput = new DeleteInput();
      deleteInput.setTableName(TEST_TABLE_NAME);

      List<QRecord> result = customizer.postDelete(deleteInput, new ArrayList<>());

      assertThat(result).isEmpty();
   }



   /***************************************************************************
    ** Test postDelete returns same records it received.
    ***************************************************************************/
   @Test
   void testPostDelete_withRecords_returnsSameRecords() throws Exception
   {
      QuickSearchDeleteCustomizer customizer = new QuickSearchDeleteCustomizer(config, TEST_TABLE_NAME, "id");

      DeleteInput deleteInput = new DeleteInput();
      deleteInput.setTableName(TEST_TABLE_NAME);

      List<QRecord> records = List.of(
         new QRecord().withValue("id", 1).withValue("name", "Test 1"),
         new QRecord().withValue("id", 2).withValue("name", "Test 2")
      );

      // Note: This will fail to delete from OpenSearch since no OpenSearch is running,
      // but the customizer should handle errors gracefully and return the records
      List<QRecord> result = customizer.postDelete(deleteInput, records);

      assertThat(result).hasSize(2);
      assertThat(result).isEqualTo(records);
   }



}

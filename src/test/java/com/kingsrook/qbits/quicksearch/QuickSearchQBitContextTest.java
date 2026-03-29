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
package com.kingsrook.qbits.quicksearch;


import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Tests for QuickSearchQBitContext static state holder.
 *******************************************************************************/
class QuickSearchQBitContextTest
{

   /***************************************************************************
    ** Clear state before and after each test to prevent leakage.
    ***************************************************************************/
   @BeforeEach
   @AfterEach
   void clearContext()
   {
      QuickSearchQBitContext.clear();
   }



   /***************************************************************************
    ** Test that getConfig returns null before any value is set.
    ***************************************************************************/
   @Test
   void testGetConfig_beforeSet_returnsNull()
   {
      assertThat(QuickSearchQBitContext.getConfig()).isNull();
   }



   /***************************************************************************
    ** Test setConfig and getConfig round trip.
    ***************************************************************************/
   @Test
   void testSetAndGetConfig()
   {
      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName("testBackend");

      QuickSearchQBitContext.setConfig(config);

      assertThat(QuickSearchQBitContext.getConfig()).isSameAs(config);
   }



   /***************************************************************************
    ** Test setDiscoveredTables and getDiscoveredTables round trip.
    ***************************************************************************/
   @Test
   void testSetAndGetDiscoveredTables()
   {
      QuickSearchableTableConfig tableConfig = new QuickSearchableTableConfig()
         .withTableName("orders");
      List<QuickSearchableTableConfig> tables = List.of(tableConfig);

      QuickSearchQBitContext.setDiscoveredTables(tables);

      assertThat(QuickSearchQBitContext.getDiscoveredTables()).hasSize(1);
      assertThat(QuickSearchQBitContext.getDiscoveredTables().get(0).getTableName()).isEqualTo("orders");
   }



   /***************************************************************************
    ** Test setClient and getClient round trip.
    ***************************************************************************/
   @Test
   void testSetAndGetClient()
   {
      Object dummyClient = new Object();
      QuickSearchQBitContext.setClient(dummyClient);

      assertThat(QuickSearchQBitContext.getClient()).isSameAs(dummyClient);
   }



   /***************************************************************************
    ** Test setPublisher and getPublisher round trip.
    ***************************************************************************/
   @Test
   void testSetAndGetPublisher()
   {
      Object dummyPublisher = new Object();
      QuickSearchQBitContext.setPublisher(dummyPublisher);

      assertThat(QuickSearchQBitContext.getPublisher()).isSameAs(dummyPublisher);
   }



   /***************************************************************************
    ** Test getTableConfig finds a table by name.
    ***************************************************************************/
   @Test
   void testGetTableConfig_findsTableByName()
   {
      QuickSearchableTableConfig orders = new QuickSearchableTableConfig().withTableName("orders");
      QuickSearchableTableConfig customers = new QuickSearchableTableConfig().withTableName("customers");
      QuickSearchQBitContext.setDiscoveredTables(List.of(orders, customers));

      QuickSearchableTableConfig found = QuickSearchQBitContext.getTableConfig("customers");

      assertThat(found).isSameAs(customers);
   }



   /***************************************************************************
    ** Test getTableConfig returns null for an unknown table name.
    ***************************************************************************/
   @Test
   void testGetTableConfig_unknownTableName_returnsNull()
   {
      QuickSearchableTableConfig orders = new QuickSearchableTableConfig().withTableName("orders");
      QuickSearchQBitContext.setDiscoveredTables(List.of(orders));

      assertThat(QuickSearchQBitContext.getTableConfig("nonexistent")).isNull();
   }



   /***************************************************************************
    ** Test getTableConfig when discoveredTables is null.
    ***************************************************************************/
   @Test
   void testGetTableConfig_nullDiscoveredTables_returnsNull()
   {
      assertThat(QuickSearchQBitContext.getTableConfig("orders")).isNull();
   }



   /***************************************************************************
    ** Test clear() resets all fields to null.
    ***************************************************************************/
   @Test
   void testClear_resetsAllFieldsToNull()
   {
      QuickSearchQBitContext.setConfig(new QuickSearchQBitConfig());
      QuickSearchQBitContext.setDiscoveredTables(List.of(new QuickSearchableTableConfig()));
      QuickSearchQBitContext.setClient(new Object());
      QuickSearchQBitContext.setPublisher(new Object());

      QuickSearchQBitContext.clear();

      assertThat(QuickSearchQBitContext.getConfig()).isNull();
      assertThat(QuickSearchQBitContext.getDiscoveredTables()).isNull();
      assertThat(QuickSearchQBitContext.getClient()).isNull();
      assertThat(QuickSearchQBitContext.getPublisher()).isNull();
   }

}

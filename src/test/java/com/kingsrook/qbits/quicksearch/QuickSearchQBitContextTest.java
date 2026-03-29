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
package com.kingsrook.qbits.quicksearch;


import java.util.List;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import com.kingsrook.qbits.quicksearch.publisher.IndexEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;


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
      QuickSearchOpenSearchClient dummyClient = mock(QuickSearchOpenSearchClient.class);
      QuickSearchQBitContext.setClient(dummyClient);

      assertThat(QuickSearchQBitContext.getClient()).isSameAs(dummyClient);
   }



   /***************************************************************************
    ** Test setPublisher and getPublisher round trip.
    ***************************************************************************/
   @Test
   void testSetAndGetPublisher()
   {
      IndexEventPublisher dummyPublisher = mock(IndexEventPublisher.class);
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
      QuickSearchQBitContext.setClient(mock(QuickSearchOpenSearchClient.class));
      QuickSearchQBitContext.setPublisher(mock(IndexEventPublisher.class));

      QuickSearchQBitContext.clear();

      assertThat(QuickSearchQBitContext.getConfig()).isNull();
      assertThat(QuickSearchQBitContext.getDiscoveredTables()).isNull();
      assertThat(QuickSearchQBitContext.getClient()).isNull();
      assertThat(QuickSearchQBitContext.getPublisher()).isNull();
   }

}

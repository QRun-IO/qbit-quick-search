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


/*******************************************************************************
 ** Static holder for Quick Search QBit runtime state.
 **
 ** Provides access to the active configuration, the OpenSearch client, the
 ** index-event publisher, and the list of tables discovered at startup.
 **
 ** Call clear() in test @AfterEach / @BeforeEach to reset all state.
 *******************************************************************************/
public class QuickSearchQBitContext
{
   private static volatile QuickSearchQBitConfig             config;
   private static volatile List<QuickSearchableTableConfig>  discoveredTables;
   private static volatile QuickSearchOpenSearchClient       client;
   private static volatile IndexEventPublisher               publisher;



   /***************************************************************************
    ** Getter for config
    ***************************************************************************/
   public static QuickSearchQBitConfig getConfig()
   {
      return config;
   }



   /***************************************************************************
    ** Setter for config
    ***************************************************************************/
   public static void setConfig(QuickSearchQBitConfig config)
   {
      QuickSearchQBitContext.config = config;
   }



   /***************************************************************************
    ** Getter for discoveredTables
    ***************************************************************************/
   public static List<QuickSearchableTableConfig> getDiscoveredTables()
   {
      return discoveredTables;
   }



   /***************************************************************************
    ** Setter for discoveredTables
    ***************************************************************************/
   public static void setDiscoveredTables(List<QuickSearchableTableConfig> discoveredTables)
   {
      QuickSearchQBitContext.discoveredTables = discoveredTables;
   }



   /***************************************************************************
    ** Find a table config by table name from the discovered tables list.
    **
    ** Returns null if discoveredTables is null or no match is found.
    ***************************************************************************/
   public static QuickSearchableTableConfig getTableConfig(String tableName)
   {
      if(discoveredTables == null)
      {
         return null;
      }

      for(QuickSearchableTableConfig tableConfig : discoveredTables)
      {
         if(tableName.equals(tableConfig.getTableName()))
         {
            return tableConfig;
         }
      }

      return null;
   }



   /***************************************************************************
    ** Getter for client
    ***************************************************************************/
   public static QuickSearchOpenSearchClient getClient()
   {
      return client;
   }



   /***************************************************************************
    ** Setter for client
    ***************************************************************************/
   public static void setClient(QuickSearchOpenSearchClient client)
   {
      QuickSearchQBitContext.client = client;
   }



   /***************************************************************************
    ** Getter for publisher
    ***************************************************************************/
   public static IndexEventPublisher getPublisher()
   {
      return publisher;
   }



   /***************************************************************************
    ** Setter for publisher
    ***************************************************************************/
   public static void setPublisher(IndexEventPublisher publisher)
   {
      QuickSearchQBitContext.publisher = publisher;
   }



   /***************************************************************************
    ** Reset all static state to null.
    **
    ** Call in test @BeforeEach or @AfterEach to prevent state leakage
    ** between tests.
    ***************************************************************************/
   public static void clear()
   {
      config = null;
      discoveredTables = null;
      client = null;
      publisher = null;
   }

}

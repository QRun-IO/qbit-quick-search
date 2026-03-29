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


/*******************************************************************************
 ** Static holder for Quick Search QBit runtime state.
 **
 ** Provides access to the active configuration, the OpenSearch client, the
 ** index-event publisher, and the list of tables discovered at startup.
 **
 ** Note: client is typed as Object until QuickSearchOpenSearchClient is
 ** wired in; publisher is typed as Object until IndexEventPublisher is
 ** created in Task 7.
 **
 ** Call clear() in test @AfterEach / @BeforeEach to reset all state.
 *******************************************************************************/
public class QuickSearchQBitContext
{
   private static QuickSearchQBitConfig             config;
   private static List<QuickSearchableTableConfig>  discoveredTables;

   /////////////////////////////////////////////////////////////////////////
   // Typed as Object until QuickSearchOpenSearchClient is set here.      //
   /////////////////////////////////////////////////////////////////////////
   private static Object client;

   /////////////////////////////////////////////////////////////////////////
   // Typed as Object until IndexEventPublisher interface exists (Task 7) //
   /////////////////////////////////////////////////////////////////////////
   private static Object publisher;



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
   public static Object getClient()
   {
      return client;
   }



   /***************************************************************************
    ** Setter for client
    ***************************************************************************/
   public static void setClient(Object client)
   {
      QuickSearchQBitContext.client = client;
   }



   /***************************************************************************
    ** Getter for publisher
    ***************************************************************************/
   public static Object getPublisher()
   {
      return publisher;
   }



   /***************************************************************************
    ** Setter for publisher
    ***************************************************************************/
   public static void setPublisher(Object publisher)
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

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
 ** Static context for Quick Search QBit runtime state.
 *******************************************************************************/
public class QuickSearchQBitContext
{
   private static QuickSearchQBitConfig              config;
   private static List<QuickSearchableTableConfig>  discoveredTables;



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

}

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
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import com.kingsrook.qbits.quicksearch.publisher.IndexEventPublisher;


/*******************************************************************************
 ** Deprecated static facade over {@link QuickSearchRuntime}.
 **
 ** Until 1.0 the QBit kept its live state in these static fields. The state now
 ** lives on the produced QBit's runtime, resolved from QContext. The getters
 ** here return an explicitly set static value when one exists (tests and
 ** legacy hosts), otherwise they delegate to {@link QuickSearchRuntime#get()}.
 **
 ** @deprecated resolve state with {@link QuickSearchRuntime#get()}; this
 ** facade will be removed in 1.1.
 *******************************************************************************/
@Deprecated
public class QuickSearchQBitContext
{
   private static final QLogger LOG = QLogger.getLogger(QuickSearchQBitContext.class);

   private static volatile QuickSearchQBitConfig            config;
   private static volatile List<QuickSearchableTableConfig> discoveredTables;
   private static volatile QuickSearchOpenSearchClient      client;
   private static volatile IndexEventPublisher              publisher;



   /*******************************************************************************
    ** Get the config: the static override, else the resolved runtime's config.
    *******************************************************************************/
   public static QuickSearchQBitConfig getConfig()
   {
      if(config != null)
      {
         return (config);
      }
      QuickSearchRuntime runtime = QuickSearchRuntime.get();
      return (runtime == null ? null : runtime.getConfig());
   }



   /*******************************************************************************
    ** Set a static config override.
    *******************************************************************************/
   public static void setConfig(QuickSearchQBitConfig config)
   {
      QuickSearchQBitContext.config = config;
   }



   /*******************************************************************************
    ** Get the discovered tables: the static override, else the runtime's.
    *******************************************************************************/
   public static List<QuickSearchableTableConfig> getDiscoveredTables()
   {
      if(discoveredTables != null)
      {
         return (discoveredTables);
      }
      QuickSearchRuntime runtime = QuickSearchRuntime.get();
      return (runtime == null ? null : runtime.getDiscoveredTables());
   }



   /*******************************************************************************
    ** Set a static discovered-tables override.
    *******************************************************************************/
   public static void setDiscoveredTables(List<QuickSearchableTableConfig> discoveredTables)
   {
      QuickSearchQBitContext.discoveredTables = discoveredTables;
   }



   /*******************************************************************************
    ** Find a table config by name, or null.
    *******************************************************************************/
   public static QuickSearchableTableConfig getTableConfig(String tableName)
   {
      List<QuickSearchableTableConfig> tables = getDiscoveredTables();
      if(tables == null || tableName == null)
      {
         return (null);
      }

      for(QuickSearchableTableConfig tableConfig : tables)
      {
         if(tableName.equals(tableConfig.getTableName()))
         {
            return (tableConfig);
         }
      }
      return (null);
   }



   /*******************************************************************************
    ** Get the client: the static override, else the runtime's (built lazily).
    ** Returns null when no runtime is registered or the client cannot be built.
    *******************************************************************************/
   public static QuickSearchOpenSearchClient getClient()
   {
      if(client != null)
      {
         return (client);
      }

      QuickSearchRuntime runtime = QuickSearchRuntime.get();
      if(runtime == null)
      {
         return (null);
      }

      try
      {
         return (runtime.getClient());
      }
      catch(QException e)
      {
         LOG.warn("Could not build OpenSearch client", e);
         return (null);
      }
   }



   /*******************************************************************************
    ** Set a static client override.
    *******************************************************************************/
   public static void setClient(QuickSearchOpenSearchClient client)
   {
      QuickSearchQBitContext.client = client;
   }



   /*******************************************************************************
    ** Get the publisher: the static override, else the runtime's.
    *******************************************************************************/
   public static IndexEventPublisher getPublisher()
   {
      if(publisher != null)
      {
         return (publisher);
      }

      QuickSearchRuntime runtime = QuickSearchRuntime.get();
      if(runtime == null)
      {
         return (null);
      }

      try
      {
         return (runtime.getPublisher());
      }
      catch(QException e)
      {
         LOG.warn("Could not build index event publisher", e);
         return (null);
      }
   }



   /*******************************************************************************
    ** Set a static publisher override.
    *******************************************************************************/
   public static void setPublisher(IndexEventPublisher publisher)
   {
      QuickSearchQBitContext.publisher = publisher;
   }



   /*******************************************************************************
    ** Clear the static overrides (tests).
    *******************************************************************************/
   public static void clear()
   {
      config           = null;
      discoveredTables = null;
      client           = null;
      publisher        = null;
   }

}

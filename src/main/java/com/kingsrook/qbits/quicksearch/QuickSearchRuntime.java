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


import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import com.kingsrook.qbits.quicksearch.publisher.IndexEventPublisher;
import com.kingsrook.qbits.quicksearch.publisher.SynchronousIndexEventPublisher;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Live state of one produced Quick Search QBit: its discovered tables, the
 ** lazily built OpenSearch client and publisher, and a short-lived cache of
 ** which tables are enabled.
 **
 ** One runtime exists per produced QBit. It hangs off the QBit's config
 ** (QuickSearchQBitConfig.getRuntime()), which the host's QInstance holds in
 ** QBitMetaData, so steps, listeners and actions resolve it from QContext with
 ** {@link #get()} rather than from JVM-global statics. Two QInstances in one
 ** JVM therefore no longer share state.
 *******************************************************************************/
public class QuickSearchRuntime
{
   private static final QLogger LOG = QLogger.getLogger(QuickSearchRuntime.class);

   private static final long ENABLED_CACHE_TTL_MILLIS = 60_000L;

   private final QuickSearchQBitConfig config;

   private List<QuickSearchableTableConfig>        discoveredTables = new ArrayList<>();
   private Map<String, QuickSearchableTableConfig> tablesByName     = new LinkedHashMap<>();

   private volatile QuickSearchOpenSearchClient client;
   private volatile IndexEventPublisher         publisher;
   private volatile boolean                     indexReady = false;
   private volatile boolean                     closed     = false;

   private final Map<String, CachedFlag> enabledCache = new ConcurrentHashMap<>();



   /*******************************************************************************
    ** A cached enabled flag with the time it was read.
    *******************************************************************************/
   private record CachedFlag(boolean enabled, long readAtMillis)
   {
   }



   /*******************************************************************************
    ** Constructor
    *******************************************************************************/
   public QuickSearchRuntime(QuickSearchQBitConfig config)
   {
      this.config = config;
   }



   /*******************************************************************************
    ** Resolve the runtime of the Quick Search QBit registered in the current
    ** QContext's QInstance. Returns null when there is no QContext, no QInstance,
    ** no Quick Search QBit, or the QBit has not been produced yet.
    *******************************************************************************/
   public static QuickSearchRuntime get()
   {
      QInstance qInstance = QContext.getQInstance();
      if(qInstance == null || qInstance.getQBits() == null)
      {
         return (null);
      }

      for(QBitMetaData qBitMetaData : qInstance.getQBits().values())
      {
         if(qBitMetaData.getConfig() instanceof QuickSearchQBitConfig quickSearchConfig && quickSearchConfig.getRuntime() != null)
         {
            return (quickSearchConfig.getRuntime());
         }
      }

      return (null);
   }



   /*******************************************************************************
    ** Like {@link #get()}, but throws when no runtime can be resolved.
    *******************************************************************************/
   public static QuickSearchRuntime getOrThrow() throws QException
   {
      QuickSearchRuntime runtime = get();
      if(runtime == null)
      {
         throw (new QException("Quick Search QBit is not registered in the current QInstance (or has not been produced)"));
      }
      return (runtime);
   }



   /*******************************************************************************
    ** Getter for config
    *******************************************************************************/
   public QuickSearchQBitConfig getConfig()
   {
      return (this.config);
   }



   /*******************************************************************************
    ** Getter for discoveredTables (never null)
    *******************************************************************************/
   public List<QuickSearchableTableConfig> getDiscoveredTables()
   {
      return (Collections.unmodifiableList(this.discoveredTables));
   }



   /*******************************************************************************
    ** Replace the discovered tables.
    *******************************************************************************/
   public void setDiscoveredTables(List<QuickSearchableTableConfig> discoveredTables)
   {
      this.discoveredTables = new ArrayList<>(discoveredTables == null ? List.of() : discoveredTables);
      this.tablesByName     = new LinkedHashMap<>();
      for(QuickSearchableTableConfig tableConfig : this.discoveredTables)
      {
         this.tablesByName.put(tableConfig.getTableName(), tableConfig);
      }
   }



   /*******************************************************************************
    ** Find the config for one table, or null.
    *******************************************************************************/
   public QuickSearchableTableConfig getTableConfig(String tableName)
   {
      return (tableName == null ? null : tablesByName.get(tableName));
   }



   /*******************************************************************************
    ** Return the OpenSearch client, building it on first use. Building the
    ** client does not contact the cluster; {@link #ensureIndexReady()} does.
    *******************************************************************************/
   public QuickSearchOpenSearchClient getClient() throws QException
   {
      QuickSearchOpenSearchClient existing = client;
      if(existing != null)
      {
         return (existing);
      }

      synchronized(this)
      {
         if(client == null)
         {
            client = new QuickSearchOpenSearchClient(config);
         }
         return (client);
      }
   }



   /*******************************************************************************
    ** Make sure the index (or alias) exists with the current mapping. Runs the
    ** check once per runtime; a failure leaves it unready so the next call
    ** retries.
    *******************************************************************************/
   public void ensureIndexReady() throws QException
   {
      if(indexReady)
      {
         return;
      }

      synchronized(this)
      {
         if(!indexReady)
         {
            getClient().ensureIndexExists();
            indexReady = true;
         }
      }
   }



   /*******************************************************************************
    ** Whether {@link #ensureIndexReady()} has succeeded.
    *******************************************************************************/
   public boolean isIndexReady()
   {
      return (indexReady);
   }



   /*******************************************************************************
    ** Return the publisher: the host-supplied one from config, else a
    ** SynchronousIndexEventPublisher over this runtime's client.
    *******************************************************************************/
   public IndexEventPublisher getPublisher() throws QException
   {
      IndexEventPublisher existing = publisher;
      if(existing != null)
      {
         return (existing);
      }

      synchronized(this)
      {
         if(publisher == null)
         {
            if(config.getIndexEventPublisher() != null)
            {
               publisher = config.getIndexEventPublisher();
            }
            else
            {
               publisher = new SynchronousIndexEventPublisher(getClient(), discoveredTables, config.getBulkBatchSize());
            }
         }
         return (publisher);
      }
   }



   /*******************************************************************************
    ** Whether a table is enabled for indexing and search, per its
    ** quickSearchIndex row. A table with no row yet is enabled. Results are
    ** cached for one minute; a failed lookup is treated as enabled so indexing
    ** does not stop because the operational table is unreachable.
    *******************************************************************************/
   public boolean isTableEnabled(String tableName)
   {
      if(tableName == null || !tablesByName.containsKey(tableName))
      {
         return (false);
      }

      long       now    = System.currentTimeMillis();
      CachedFlag cached = enabledCache.get(tableName);
      if(cached != null && now - cached.readAtMillis() < ENABLED_CACHE_TTL_MILLIS)
      {
         return (cached.enabled());
      }

      boolean enabled = readEnabledFlag(tableName);
      enabledCache.put(tableName, new CachedFlag(enabled, now));
      return (enabled);
   }



   /*******************************************************************************
    ** Forget cached enabled flags (after an operator toggles a table).
    *******************************************************************************/
   public void invalidateEnabledCache()
   {
      enabledCache.clear();
   }



   /*******************************************************************************
    ** Read the enabled flag from the quickSearchIndex row.
    *******************************************************************************/
   private boolean readEnabledFlag(String tableName)
   {
      try
      {
         if(QContext.getQInstance() == null || QContext.getQInstance().getTable(config.getQuickSearchIndexTableName()) == null)
         {
            return (true);
         }

         QueryInput queryInput = new QueryInput();
         queryInput.setTableName(config.getQuickSearchIndexTableName());
         queryInput.setFilter(new QQueryFilter(new QFilterCriteria("tableName", QCriteriaOperator.EQUALS, tableName)));
         List<QRecord> rows = new QueryAction().execute(queryInput).getRecords();

         if(rows == null || rows.isEmpty())
         {
            return (true);
         }

         Boolean enabled = rows.get(0).getValueBoolean("enabled");
         return (enabled == null || enabled);
      }
      catch(Exception e)
      {
         LOG.warn("Could not read enabled flag for table; treating as enabled", e, logPair("tableName", tableName));
         return (true);
      }
   }



   /*******************************************************************************
    ** Start the runtime per the configured startup mode: FAIL_FAST throws when
    ** the index cannot be prepared; DEGRADED logs and continues. A LinkageError
    ** (an optional library such as the AWS SDK missing while the client is
    ** built) is handled the same way, so a DEGRADED host still boots.
    *******************************************************************************/
   public void start() throws QException
   {
      try
      {
         ensureIndexReady();
      }
      catch(Exception | LinkageError e)
      {
         if(config.getStartupMode() == QuickSearchStartupMode.DEGRADED)
         {
            LOG.warn("OpenSearch is not reachable; Quick Search is running degraded and will retry on first use", e,
               logPair("indexName", config.getOpensearchIndexName()));
            return;
         }

         throw (new QException("Quick Search could not prepare its OpenSearch index [" + config.getOpensearchIndexName()
            + "]; set startupMode=DEGRADED to boot without it: " + e.getMessage(), e));
      }
   }



   /*******************************************************************************
    ** Close the publisher and client. Safe to call more than once.
    *******************************************************************************/
   public synchronized void close()
   {
      if(closed)
      {
         return;
      }
      closed = true;

      if(publisher != null)
      {
         try
         {
            publisher.close();
         }
         catch(Exception e)
         {
            LOG.warn("Error closing Quick Search publisher", e);
         }
      }

      if(client != null)
      {
         client.close();
      }

      indexReady = false;
   }

}

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

package com.kingsrook.qbits.quicksearch.config;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitProducer;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFieldRole;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexField;
import com.kingsrook.qbits.quicksearch.model.QuickSearchSettings;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** The effective Quick Search configuration, read from the records: which
 ** tables are indexed (quick_search_index), with which fields, weights,
 ** roles and analyzers (quick_search_index_field), and the system settings
 ** (quick_search_settings: the global pause).
 **
 ** Precedence: the records are the source of truth once the instance has the
 ** config tables and priming has run. Code (annotations and searchableTables)
 ** only seeds them (QuickSearchConfigPrimer). Until priming has succeeded,
 ** or when the config tables are not in the instance or not readable (a host
 ** that has not added their DDL yet), the code-declared tables are used, as
 ** before 1.1.0-RC.3.
 **
 ** Reads are cached for CACHE_TTL_MILLIS and dropped at once by the config
 ** tables' customizers when a row changes, on every server: the runtime
 ** publishes a cluster cache invalidation (QuickSearchRuntime.CONFIG_CACHE_NAME).
 ** The TTL remains the safety net when the cluster bus is down.
 *******************************************************************************/
public class QuickSearchConfigStore
{
   private static final QLogger LOG = QLogger.getLogger(QuickSearchConfigStore.class);

   public static final long CACHE_TTL_MILLIS   = 30_000L;
   public static final long PRIME_RETRY_MILLIS = 300_000L;

   private static final ThreadLocal<Boolean> BUSY = ThreadLocal.withInitial(() -> false);

   private final QuickSearchRuntime ownerRuntime;

   private volatile Snapshot snapshot;
   private volatile QRecord  settings;
   private volatile long     settingsReadAtMillis = 0L;
   private volatile boolean  primed               = false;
   private volatile long     lastPrimeAttemptMillis = 0L;
   private volatile long     lastFailureLogMillis   = 0L;

   private final Set<String> coreSearchFieldsDefaulted = ConcurrentHashMap.newKeySet();



   /*******************************************************************************
    ** A loaded set of effective table configs.
    *******************************************************************************/
   private record Snapshot(List<QuickSearchableTableConfig> tables, Map<String, QuickSearchableTableConfig> byName, long loadedAtMillis)
   {
   }



   /*******************************************************************************
    ** Constructor
    *******************************************************************************/
   public QuickSearchConfigStore(QuickSearchRuntime runtime)
   {
      this.ownerRuntime = runtime;
   }



   /*******************************************************************************
    ** The runtime the config hangs off (the one the store was made for, unless
    ** the config has since been given another, such as a wrapper of it).
    *******************************************************************************/
   private QuickSearchRuntime runtime()
   {
      QuickSearchRuntime current = ownerRuntime.getConfig() == null ? null : ownerRuntime.getConfig().getRuntime();
      return (current != null ? current : ownerRuntime);
   }



   /*******************************************************************************
    ** Whether the instance in the current context has the config tables
    ** (quick_search_index and quick_search_index_field).
    *******************************************************************************/
   public boolean hasConfigTables()
   {
      QInstance qInstance = QContext.getQInstance();
      QuickSearchQBitConfig config = runtime().getConfig();
      return (qInstance != null
         && qInstance.getTable(config.getQuickSearchIndexTableName()) != null
         && qInstance.getTable(config.applyTablePrefix(QuickSearchIndexField.TABLE_NAME)) != null);
   }



   /*******************************************************************************
    ** Whether the effective config comes from the records (else from code).
    *******************************************************************************/
   public boolean isRecordsMode()
   {
      return (primed && hasConfigTables());
   }



   /*******************************************************************************
    ** Whether priming has succeeded in this runtime.
    *******************************************************************************/
   public boolean isPrimed()
   {
      return (primed);
   }



   /*******************************************************************************
    ** Record that priming succeeded (or, for tests, reset it).
    *******************************************************************************/
   public void setPrimed(boolean primed)
   {
      this.primed                 = primed;
      this.lastPrimeAttemptMillis = 0L;
      invalidate();
   }



   /*******************************************************************************
    ** The effective table configs: from the records in records mode, else the
    ** code-declared ones. Primes first when that has not happened yet (at most
    ** once per PRIME_RETRY_MILLIS while it keeps failing). Never throws.
    *******************************************************************************/
   public List<QuickSearchableTableConfig> getTables()
   {
      Snapshot current = getSnapshot();
      return (current == null ? runtime().getCodeTables() : current.tables());
   }



   /*******************************************************************************
    ** One table's effective config, or null when it is not indexed.
    *******************************************************************************/
   public QuickSearchableTableConfig getTable(String tableName)
   {
      if(tableName == null)
      {
         return (null);
      }
      Snapshot current = getSnapshot();
      if(current == null)
      {
         return (runtime().getCodeTableConfig(tableName));
      }
      return (current.byName().get(tableName));
   }



   /*******************************************************************************
    ** The current snapshot, loading it when stale; null means "use code".
    *******************************************************************************/
   private Snapshot getSnapshot()
   {
      if(BUSY.get() || !hasConfigTables())
      {
         return (null);
      }

      if(!primed)
      {
         tryPrime();
         if(!primed)
         {
            return (null);
         }
      }

      Snapshot current = snapshot;
      if(current != null && System.currentTimeMillis() - current.loadedAtMillis() < CACHE_TTL_MILLIS)
      {
         return (current);
      }

      Snapshot loaded = load();
      if(loaded != null)
      {
         snapshot = loaded;
         return (loaded);
      }
      return (current);
   }



   /*******************************************************************************
    ** Prime now, unless it already succeeded or failed less than
    ** PRIME_RETRY_MILLIS ago. Never throws.
    *******************************************************************************/
   public synchronized void tryPrime()
   {
      if(primed || BUSY.get() || !hasConfigTables())
      {
         return;
      }
      long now = System.currentTimeMillis();
      if(lastPrimeAttemptMillis != 0 && now - lastPrimeAttemptMillis < PRIME_RETRY_MILLIS)
      {
         return;
      }

      BUSY.set(true);
      try
      {
         new QuickSearchConfigPrimer(runtime()).prime();
         primed                 = true;
         lastPrimeAttemptMillis = 0L;
      }
      catch(Exception | LinkageError e)
      {
         lastPrimeAttemptMillis = now;
         LOG.warn("Quick Search could not prime its config records; using the code-declared tables until it can (retried every "
            + (PRIME_RETRY_MILLIS / 60_000) + " minutes)", e);
      }
      finally
      {
         BUSY.set(false);
         invalidate();
      }
   }



   /*******************************************************************************
    ** Drop the cached configs and settings (a config row changed).
    *******************************************************************************/
   public void invalidate()
   {
      snapshot             = null;
      settingsReadAtMillis = 0L;
   }



   /*******************************************************************************
    ** Read the records into a snapshot; null (logged, at most once a minute)
    ** when they cannot be read.
    *******************************************************************************/
   private Snapshot load()
   {
      BUSY.set(true);
      try
      {
         QuickSearchQBitConfig config    = runtime().getConfig();
         QInstance             qInstance = QContext.getQInstance();

         List<QRecord> indexRows = query(config.getQuickSearchIndexTableName());
         List<QRecord> fieldRows = query(config.applyTablePrefix(QuickSearchIndexField.TABLE_NAME));

         Map<Integer, List<QRecord>> fieldsByIndex = new LinkedHashMap<>();
         for(QRecord fieldRow : fieldRows)
         {
            fieldsByIndex.computeIfAbsent(fieldRow.getValueInteger("quickSearchIndexId"), k -> new ArrayList<>()).add(fieldRow);
         }

         List<QuickSearchableTableConfig>        tables = new ArrayList<>();
         Map<String, QuickSearchableTableConfig> byName = new LinkedHashMap<>();
         for(QRecord indexRow : indexRows)
         {
            QuickSearchableTableConfig tableConfig = buildTableConfig(qInstance, config, indexRow, fieldsByIndex.getOrDefault(indexRow.getValueInteger("id"), List.of()));
            if(tableConfig != null && !byName.containsKey(tableConfig.getTableName()))
            {
               tables.add(tableConfig);
               byName.put(tableConfig.getTableName(), tableConfig);
            }
         }

         ////////////////////////////////////////////////////////////////////////
         // tables added at runtime get core searchFields (once per table), so //
         // the global search covers them too                                  //
         ////////////////////////////////////////////////////////////////////////
         if(Boolean.TRUE.equals(config.getServeCoreRecordSearch()) && qInstance != null)
         {
            List<QuickSearchableTableConfig> unseen = tables.stream().filter(t -> coreSearchFieldsDefaulted.add(t.getTableName())).toList();
            if(!unseen.isEmpty())
            {
               QuickSearchQBitProducer.defaultCoreSearchFields(qInstance, unseen);
            }
         }

         return (new Snapshot(Collections.unmodifiableList(tables), Collections.unmodifiableMap(byName), System.currentTimeMillis()));
      }
      catch(Exception | LinkageError e)
      {
         long now = System.currentTimeMillis();
         if(now - lastFailureLogMillis > 60_000L)
         {
            lastFailureLogMillis = now;
            LOG.warn("Could not read the Quick Search config records; keeping the last config read", e);
         }
         return (null);
      }
      finally
      {
         BUSY.set(false);
      }
   }



   /*******************************************************************************
    ** Build one table's effective config from its index row and field rows.
    ** A table no longer in the instance is skipped; a field that is gone or
    ** can no longer be indexed (hidden, sensitive) is dropped with a warning.
    *******************************************************************************/
   static QuickSearchableTableConfig buildTableConfig(QInstance qInstance, QuickSearchQBitConfig config, QRecord indexRow, List<QRecord> fieldRows)
   {
      String         tableName = indexRow.getValueString("tableName");
      QTableMetaData table     = qInstance == null || tableName == null ? null : qInstance.getTable(tableName);
      if(table == null)
      {
         LOG.debug("Quick Search index row names a table that is not in the instance; skipped", logPair("tableName", tableName));
         return (null);
      }

      List<QRecord> sorted = new ArrayList<>(fieldRows);
      sorted.sort(Comparator.comparing((QRecord r) -> r.getValueInteger("sortOrder") == null ? Integer.MAX_VALUE : r.getValueInteger("sortOrder"))
         .thenComparing(r -> r.getValueInteger("id") == null ? Integer.MAX_VALUE : r.getValueInteger("id")));

      List<String>         fields         = new ArrayList<>();
      Map<String, Integer> weights        = new LinkedHashMap<>();
      Map<String, Boolean> includeLabels  = new LinkedHashMap<>();
      Map<String, String>  roles          = new LinkedHashMap<>();
      Map<String, String>  analyzers      = new LinkedHashMap<>();
      Map<String, Boolean> snippetFlags   = new LinkedHashMap<>();
      for(QRecord fieldRow : sorted)
      {
         String fieldName = fieldRow.getValueString("fieldName");
         String problem   = QuickSearchFieldRules.whyNotIndexable(table, fieldName);
         if(problem != null)
         {
            QuickSearchFieldRules.warnExcludedFields(table);
            continue;
         }
         if(fields.contains(fieldName))
         {
            continue;
         }

         fields.add(fieldName);
         weights.put(fieldName, fieldRow.getValueInteger("weight") == null ? 1 : fieldRow.getValueInteger("weight"));
         includeLabels.put(fieldName, Boolean.TRUE.equals(fieldRow.getValueBoolean("includeLabel")));

         ///////////////////////////////////////////////////////////////////////
         // only non-defaults go in the maps, so a table of plain fields has  //
         // the same searchableFieldsJson as its code config (no false drift) //
         ///////////////////////////////////////////////////////////////////////
         QuickSearchFieldRole role = QuickSearchFieldRole.fromName(fieldRow.getValueString("role"), QuickSearchFieldRole.BODY);
         if(role != QuickSearchFieldRole.BODY)
         {
            roles.put(fieldName, role.name());
         }
         if(StringUtils.hasContent(fieldRow.getValueString("analyzer")))
         {
            analyzers.put(fieldName, fieldRow.getValueString("analyzer"));
         }
         if(fieldRow.getValueBoolean("includeInSnippet") != null)
         {
            snippetFlags.put(fieldName, fieldRow.getValueBoolean("includeInSnippet"));
         }
      }

      String timestampField = indexRow.getValueString("basepullTimestampField");
      if(timestampField != null && !QuickSearchFieldRules.isTimestampField(table, timestampField))
      {
         LOG.warn("Quick Search basepull timestamp field is not a date-time field of the table; incremental basepull is off for it",
            logPair("tableName", tableName), logPair("timestampField", timestampField));
         timestampField = null;
      }

      Integer interval = indexRow.getValueInteger("basepullIntervalMinutes");
      Boolean enabled  = indexRow.getValueBoolean("enabled");

      QuickSearchableTableConfig codeConfig = config.getRuntime() == null ? null : config.getRuntime().getCodeTableConfig(tableName);

      return (new QuickSearchableTableConfig()
         .withTableName(tableName)
         .withPrimaryKeyField(table.getPrimaryKeyField())
         .withSearchableFields(fields)
         .withFieldWeights(weights)
         .withFieldIncludeLabels(includeLabels)
         .withFieldRoles(roles)
         .withFieldAnalyzers(analyzers)
         .withFieldIncludeInSnippet(snippetFlags)
         .withBasepullIntervalMinutes(interval == null ? config.getDefaultBasepullIntervalMinutes() : interval)
         .withBasepullTimestampField(timestampField)
         .withEnabledByDefault(codeConfig == null ? Boolean.TRUE : codeConfig.getEnabledByDefault())
         .withEnabled(enabled == null || enabled)
         .withQuickSearchIndexId(indexRow.getValueInteger("id"))
         .withRecordLabelFormat(codeConfig == null ? null : codeConfig.getRecordLabelFormat())
         .withRecordLabelFields(codeConfig == null ? null : codeConfig.getRecordLabelFields())
         .withMaxFieldLength(config.getMaxFieldLength()));
   }



   /*******************************************************************************
    ** The settings row (id 1), cached like the configs; null when the table is
    ** not in the instance, the row does not exist, or it cannot be read.
    *******************************************************************************/
   public QRecord getSettings()
   {
      long now = System.currentTimeMillis();
      if(settingsReadAtMillis != 0 && now - settingsReadAtMillis < CACHE_TTL_MILLIS)
      {
         return (settings);
      }

      QRecord read = null;
      String  name = runtime().getConfig().applyTablePrefix(QuickSearchSettings.TABLE_NAME);
      if(QContext.getQInstance() != null && QContext.getQInstance().getTable(name) != null)
      {
         try
         {
            QueryInput queryInput = new QueryInput();
            queryInput.setTableName(name);
            queryInput.setFilter(new QQueryFilter().withLimit(1));
            List<QRecord> rows = CollectionUtils.nonNullList(new QueryAction().execute(queryInput).getRecords());
            read = rows.isEmpty() ? null : rows.get(0);
         }
         catch(Exception | LinkageError e)
         {
            LOG.debug("Could not read the Quick Search settings; treating indexing as not paused", e);
         }
      }
      settings             = read;
      settingsReadAtMillis = now;
      return (read);
   }



   /*******************************************************************************
    ** Whether an admin has paused all indexing (quick_search_settings).
    *******************************************************************************/
   public boolean isIndexingPaused()
   {
      QRecord row = getSettings();
      return (row != null && Boolean.TRUE.equals(row.getValueBoolean("indexingPaused")));
   }



   /*******************************************************************************
    ** All rows of a config table.
    *******************************************************************************/
   private static List<QRecord> query(String tableName) throws QException
   {
      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(tableName);
      queryInput.setFilter(new QQueryFilter());
      return (CollectionUtils.nonNullList(new QueryAction().execute(queryInput).getRecords()));
   }

}

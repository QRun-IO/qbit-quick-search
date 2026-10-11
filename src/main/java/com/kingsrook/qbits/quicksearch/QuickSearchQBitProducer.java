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


import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.metadata.MetaDataProducerMultiOutput;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaDataProducer;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchRecordSearchProvider;
import com.kingsrook.qbits.quicksearch.annotations.QuickSearchField;
import com.kingsrook.qbits.quicksearch.annotations.QuickSearchable;
import com.kingsrook.qbits.quicksearch.listeners.QuickSearchRecordChangeListener;
import com.kingsrook.qbits.quicksearch.metadata.QuickSearchAdminAppMetaDataProducer;
import com.kingsrook.qbits.quicksearch.metadata.QuickSearchProcessMetaDataHelper;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Entry point of the Quick Search QBit, a QQQ 4.1 QBitMetaDataProducer.
 **
 ** The framework's default produce() registers QBitMetaData, validates the
 ** config, runs the component producers in this package (operational tables,
 ** processes, admin app), applies the config's table customizer and default
 ** backend, stamps sourceQBitName, then calls {@link #postProduceActions}, which
 ** discovers searchable tables, builds the runtime, registers the record-change
 ** listener and runtime service, registers the core record-search provider
 ** (serveCoreRecordSearch) and gives indexed tables core searchFields, and (in
 ** FAIL_FAST mode) prepares the index.
 **
 ** Host usage, unchanged from 0.x:
 **   new QuickSearchQBitProducer().withConfig(config).produce(qInstance);
 ** That overload adds everything to the instance itself; hosts that discover
 ** this producer through MetaDataProducerHelper get the standard contract.
 *******************************************************************************/
public class QuickSearchQBitProducer implements QBitMetaDataProducer<QuickSearchQBitConfig>
{
   private static final QLogger LOG = QLogger.getLogger(QuickSearchQBitProducer.class);

   public static final String GROUP_ID    = "com.kingsrook.qbits";
   public static final String ARTIFACT_ID = "quick-search";

   public static final String BASEPULL_PROCESS_NAME     = QuickSearchProcessMetaDataHelper.BASEPULL_PROCESS_NAME;
   public static final String FULL_REINDEX_PROCESS_NAME = QuickSearchProcessMetaDataHelper.FULL_REINDEX_PROCESS_NAME;
   public static final String RECONCILE_PROCESS_NAME    = QuickSearchProcessMetaDataHelper.RECONCILE_PROCESS_NAME;
   public static final String APP_NAME                  = QuickSearchAdminAppMetaDataProducer.APP_NAME;

   private static final String VERSION = loadVersion();

   private QuickSearchQBitConfig qBitConfig;
   private String                namespace;



   /*******************************************************************************
    ** Fluent setter for the config (0.x name).
    *******************************************************************************/
   public QuickSearchQBitProducer withConfig(QuickSearchQBitConfig config)
   {
      this.qBitConfig = config;
      return (this);
   }



   /*******************************************************************************
    ** Getter for the config (0.x name).
    *******************************************************************************/
   public QuickSearchQBitConfig getConfig()
   {
      return (this.qBitConfig);
   }



   /*******************************************************************************
    ** Fluent setter for the config.
    *******************************************************************************/
   public QuickSearchQBitProducer withQBitConfig(QuickSearchQBitConfig config)
   {
      this.qBitConfig = config;
      return (this);
   }



   /*******************************************************************************
    ** Setter for the config.
    *******************************************************************************/
   public void setQBitConfig(QuickSearchQBitConfig config)
   {
      this.qBitConfig = config;
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public QuickSearchQBitConfig getQBitConfig()
   {
      return (this.qBitConfig);
   }



   /*******************************************************************************
    ** Namespace for this QBit instance; also the default tableNamePrefix.
    *******************************************************************************/
   public QuickSearchQBitProducer withNamespace(String namespace)
   {
      this.namespace = namespace;
      return (this);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getNamespace()
   {
      return (this.namespace);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public QBitMetaData getQBitMetaData()
   {
      return (new QBitMetaData()
         .withGroupId(GROUP_ID)
         .withArtifactId(ARTIFACT_ID)
         .withVersion(VERSION)
         .withNamespace(getNamespace())
         .withConfig(getQBitConfig()));
   }



   /*******************************************************************************
    ** A producer found by package scanning has no config; skip it.
    *******************************************************************************/
   @Override
   public boolean isEnabled()
   {
      return (qBitConfig != null);
   }



   /*******************************************************************************
    ** Produce and add everything to the instance. Returns an empty output so a
    ** host that also calls addSelfToInstance on the result adds nothing twice.
    **
    ** Validation errors are reported as one QException listing every error.
    *******************************************************************************/
   @Override
   public MetaDataProducerMultiOutput produce(QInstance qInstance) throws QException
   {
      if(qBitConfig == null)
      {
         throw (new QException("QuickSearchQBit configuration validation failed: no config was supplied (call withConfig)"));
      }

      if(StringUtils.hasContent(namespace) && !StringUtils.hasContent(qBitConfig.getTableNamePrefix()))
      {
         qBitConfig.setTableNamePrefix(namespace);
      }

      List<String> errors = new ArrayList<>();
      qBitConfig.validate(qInstance, errors);
      if(!errors.isEmpty())
      {
         throw (new QException("QuickSearchQBit configuration validation failed: " + String.join("; ", errors)));
      }

      MetaDataProducerMultiOutput output = QBitMetaDataProducer.super.produce(qInstance);
      output.addSelfToInstance(qInstance);

      return (new MetaDataProducerMultiOutput());
   }



   /*******************************************************************************
    ** Discover tables, build the runtime, register the listener and runtime
    ** service, and prepare the index in FAIL_FAST mode.
    *******************************************************************************/
   @Override
   public void postProduceActions(MetaDataProducerMultiOutput output, QInstance qInstance) throws QException
   {
      List<QuickSearchableTableConfig> annotationTables = discoverSearchableTables(qInstance);
      List<QuickSearchableTableConfig> configTables     = convertConfigDrivenTables(qInstance);

      for(QuickSearchableTableConfig annotationTable : annotationTables)
      {
         for(QuickSearchableTableConfig configTable : configTables)
         {
            if(annotationTable.getTableName().equals(configTable.getTableName()))
            {
               throw (new QException("Duplicate table name found in both annotation-based and config-driven searchable tables: " + annotationTable.getTableName()));
            }
         }
      }

      List<QuickSearchableTableConfig> discoveredTables = new ArrayList<>();
      discoveredTables.addAll(annotationTables);
      discoveredTables.addAll(configTables);

      QuickSearchRuntime previous = qBitConfig.getRuntime();
      if(previous != null)
      {
         previous.close();
      }

      QuickSearchRuntime runtime = new QuickSearchRuntime(qBitConfig);
      runtime.setDiscoveredTables(discoveredTables);
      qBitConfig.setRuntime(runtime);

      if(Boolean.TRUE.equals(qBitConfig.getEnableRealTimeIndexing()))
      {
         qInstance.withRecordChangeListener(new QCodeReference(QuickSearchRecordChangeListener.class));
      }

      qInstance.withRuntimeService(new QCodeReference(QuickSearchRuntimeService.class));

      if(Boolean.TRUE.equals(qBitConfig.getServeCoreRecordSearch()))
      {
         registerRecordSearchProvider(qInstance);
         defaultCoreSearchFields(qInstance, discoveredTables);
      }

      if(!StringUtils.hasContent(qBitConfig.getSchedulerName()) && Boolean.TRUE.equals(qBitConfig.getEnableBasepullProcess()))
      {
         LOG.warn("Quick Search has no schedulerName; the basepull and reconcile processes are registered but will not run on a schedule");
      }

      if(qBitConfig.getStartupMode() == QuickSearchStartupMode.FAIL_FAST)
      {
         runtime.start();
      }

      LOG.info("Quick Search QBit produced", logPair("version", VERSION), logPair("tables", discoveredTables.size()),
         logPair("indexName", qBitConfig.getOpensearchIndexName()), logPair("startupMode", qBitConfig.getStartupMode()));
   }



   /*******************************************************************************
    ** Register QuickSearchRecordSearchProvider as the instance's core record
    ** search provider. An instance holds one provider; a different one the host
    ** already registered is kept (with a warning).
    *******************************************************************************/
   static void registerRecordSearchProvider(QInstance qInstance)
   {
      QCodeReference existing = qInstance.getRecordSearchProvider();
      if(existing != null && !QuickSearchRecordSearchProvider.class.getName().equals(existing.getName()))
      {
         LOG.warn("The instance already has a record search provider; Quick Search does not replace it, so core search does not use OpenSearch",
            logPair("provider", existing.getName()));
         return;
      }

      qInstance.withRecordSearchProvider(new QCodeReference(QuickSearchRecordSearchProvider.class));
   }



   /*******************************************************************************
    ** Core record search only searches tables with searchFields, so give each
    ** indexed table that has none the searchable fields core accepts (visible
    ** string or integer fields; never password fields). Fields the host set are
    ** never replaced. A table that is not in the instance yet, uses backend
    ** variants, or has no such field is skipped with a warning.
    *******************************************************************************/
   static void defaultCoreSearchFields(QInstance qInstance, List<QuickSearchableTableConfig> tableConfigs)
   {
      for(QuickSearchableTableConfig tableConfig : tableConfigs)
      {
         QTableMetaData table = qInstance.getTable(tableConfig.getTableName());
         if(table == null)
         {
            LOG.warn("Indexed table is not in the instance when Quick Search is produced, so it gets no core searchFields; set them on the table for core search to use it",
               logPair("tableName", tableConfig.getTableName()));
            continue;
         }

         if(table.getSearchFields() != null)
         {
            continue;
         }

         QBackendMetaData backend = qInstance.getBackend(table.getBackendName());
         if(backend != null && Boolean.TRUE.equals(backend.getUsesVariants()))
         {
            LOG.warn("Indexed table uses backend variants, which core record search does not support; it gets no core searchFields", logPair("tableName", table.getName()));
            continue;
         }

         List<String> searchFields = new ArrayList<>();
         for(String fieldName : CollectionUtils.nonNullList(tableConfig.getSearchableFields()))
         {
            QFieldMetaData field = table.getFields() == null ? null : table.getFields().get(fieldName);
            if(field != null && !Boolean.TRUE.equals(field.getIsHidden()) && field.getType() != null
               && (field.getType().isStringLike() || field.getType().isIntegral()) && !field.getType().needsMasked()
               && !searchFields.contains(fieldName))
            {
               searchFields.add(fieldName);
            }
         }

         if(searchFields.isEmpty())
         {
            LOG.warn("Indexed table has no string or integer searchable field, so it gets no core searchFields; set them on the table for core search to use it",
               logPair("tableName", table.getName()));
            continue;
         }

         table.setSearchFields(searchFields);
      }
   }



   /*******************************************************************************
    ** Discover searchable tables from @QuickSearchable entity classes. Fails when
    ** a configured field does not exist on a table that is in the instance.
    *******************************************************************************/
   List<QuickSearchableTableConfig> discoverSearchableTables(QInstance qInstance) throws QException
   {
      List<QuickSearchableTableConfig> tables        = new ArrayList<>();
      List<Class<?>>                   entityClasses = qBitConfig.getSearchableEntityClasses();

      if(entityClasses == null || entityClasses.isEmpty())
      {
         return (tables);
      }

      for(Class<?> entityClass : entityClasses)
      {
         QuickSearchable annotation = entityClass.getAnnotation(QuickSearchable.class);
         if(annotation == null)
         {
            LOG.warn("Entity class does not have @QuickSearchable annotation; skipping", logPair("className", entityClass.getName()));
            continue;
         }

         String tableName = annotation.tableName();

         List<String>         searchableFields   = new ArrayList<>();
         Map<String, Integer> fieldWeights       = new LinkedHashMap<>();
         Map<String, Boolean> fieldIncludeLabels = new LinkedHashMap<>();

         List<Field> allFields = new ArrayList<>();
         Class<?>    current   = entityClass;
         while(current != null && current != Object.class)
         {
            allFields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
         }

         for(Field field : allFields)
         {
            QuickSearchField fieldAnnotation = field.getAnnotation(QuickSearchField.class);
            if(fieldAnnotation != null)
            {
               searchableFields.add(field.getName());
               fieldWeights.put(field.getName(), fieldAnnotation.weight());
               fieldIncludeLabels.put(field.getName(), fieldAnnotation.includeLabel());
            }
         }

         if(searchableFields.isEmpty() && annotation.fields() != null)
         {
            for(String fieldName : annotation.fields())
            {
               searchableFields.add(fieldName);
               fieldWeights.put(fieldName, 1);
               fieldIncludeLabels.put(fieldName, false);
            }
         }

         String         primaryKeyField        = "id";
         String         basepullTimestampField = StringUtils.hasContent(annotation.basepullTimestampField()) ? annotation.basepullTimestampField() : null;
         QTableMetaData table                  = qInstance.getTable(tableName);
         if(table != null)
         {
            primaryKeyField        = table.getPrimaryKeyField();
            basepullTimestampField = resolveTimestampField(table, searchableFields, basepullTimestampField);
            removeHiddenFields(table, searchableFields, fieldWeights, fieldIncludeLabels);
         }

         tables.add(new QuickSearchableTableConfig()
            .withTableName(tableName)
            .withPrimaryKeyField(primaryKeyField)
            .withSearchableFields(searchableFields)
            .withFieldWeights(fieldWeights)
            .withFieldIncludeLabels(fieldIncludeLabels)
            .withBasepullIntervalMinutes(annotation.basepullIntervalMinutes())
            .withBasepullTimestampField(basepullTimestampField)
            .withEnabledByDefault(annotation.enabledByDefault())
            .withMaxFieldLength(qBitConfig.getMaxFieldLength()));
      }

      return (tables);
   }



   /*******************************************************************************
    ** Convert config-driven tables, resolving primary keys and defaults.
    *******************************************************************************/
   private List<QuickSearchableTableConfig> convertConfigDrivenTables(QInstance qInstance)
   {
      List<QuickSearchableTableConfig> tables           = new ArrayList<>();
      List<SearchableTableConfig>      searchableTables = qBitConfig.getSearchableTables();

      if(searchableTables == null || searchableTables.isEmpty())
      {
         return (tables);
      }

      for(SearchableTableConfig stc : searchableTables)
      {
         List<String>         searchableFields   = new ArrayList<>();
         Map<String, Integer> fieldWeights       = new LinkedHashMap<>();
         Map<String, Boolean> fieldIncludeLabels = new LinkedHashMap<>();

         for(SearchableFieldConfig fieldConfig : stc.getFields() == null ? List.<SearchableFieldConfig>of() : stc.getFields())
         {
            searchableFields.add(fieldConfig.getFieldName());
            fieldWeights.put(fieldConfig.getFieldName(), fieldConfig.getWeight() != null ? fieldConfig.getWeight() : 1);
            fieldIncludeLabels.put(fieldConfig.getFieldName(), fieldConfig.getIncludeLabel() != null ? fieldConfig.getIncludeLabel() : false);
         }

         String         primaryKeyField        = "id";
         String         basepullTimestampField = stc.getBasepullTimestampField();
         QTableMetaData table                  = qInstance.getTable(stc.getTableName());
         if(table != null)
         {
            primaryKeyField        = table.getPrimaryKeyField();
            basepullTimestampField = (basepullTimestampField != null && !table.getFields().containsKey(basepullTimestampField)) ? null : basepullTimestampField;
            removeHiddenFields(table, searchableFields, fieldWeights, fieldIncludeLabels);
         }

         Integer basepullInterval = stc.getBasepullIntervalMinutes();
         if(basepullInterval == null)
         {
            basepullInterval = qBitConfig.getDefaultBasepullIntervalMinutes();
         }

         tables.add(new QuickSearchableTableConfig()
            .withTableName(stc.getTableName())
            .withPrimaryKeyField(primaryKeyField)
            .withSearchableFields(searchableFields)
            .withFieldWeights(fieldWeights)
            .withFieldIncludeLabels(fieldIncludeLabels)
            .withBasepullIntervalMinutes(basepullInterval)
            .withBasepullTimestampField(basepullTimestampField)
            .withEnabledByDefault(stc.getEnabledByDefault())
            .withRecordLabelFormat(stc.getRecordLabelFormat())
            .withRecordLabelFields(stc.getRecordLabelFields())
            .withMaxFieldLength(qBitConfig.getMaxFieldLength()));
      }

      return (tables);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   /*******************************************************************************
    ** Fail on missing searchable fields. A missing timestamp field disables
    ** incremental basepull for the table (reconcile still covers it): the
    ** default "modifyDate" is downgraded with a warning, an explicit other name
    ** is an error.
    *******************************************************************************/
   private static String resolveTimestampField(QTableMetaData table, List<String> searchableFields, String basepullTimestampField) throws QException
   {
      List<String> missing = new ArrayList<>();
      for(String fieldName : searchableFields)
      {
         if(!table.getFields().containsKey(fieldName))
         {
            missing.add(fieldName);
         }
      }
      if(!missing.isEmpty())
      {
         throw (new QException("Searchable field(s) " + missing + " do not exist on table [" + table.getName() + "]"));
      }

      if(basepullTimestampField != null && !table.getFields().containsKey(basepullTimestampField))
      {
         if(QuickSearchQBitConfig.DEFAULT_BASEPULL_TIMESTAMP_FIELD.equals(basepullTimestampField))
         {
            LOG.warn("Table has no modifyDate field; incremental basepull is disabled for it (reconcile still covers it)", logPair("tableName", table.getName()));
            return (null);
         }
         throw (new QException("basepullTimestampField [" + basepullTimestampField + "] does not exist on table [" + table.getName() + "]"));
      }
      return (basepullTimestampField);
   }



   /*******************************************************************************
    ** Hidden and password fields are never indexed: their values would leak
    ** through searchable text and highlights.
    *******************************************************************************/
   private static void removeHiddenFields(QTableMetaData table, List<String> searchableFields, Map<String, Integer> fieldWeights, Map<String, Boolean> fieldIncludeLabels)
   {
      List<String> hidden = new ArrayList<>();
      for(String fieldName : searchableFields)
      {
         QFieldMetaData field = table.getFields().get(fieldName);
         if(field != null && (Boolean.TRUE.equals(field.getIsHidden()) || (field.getType() != null && field.getType().needsMasked())))
         {
            hidden.add(fieldName);
         }
      }
      if(!hidden.isEmpty())
      {
         LOG.warn("Hidden and password fields are excluded from Quick Search indexing", logPair("tableName", table.getName()), logPair("fields", hidden));
         searchableFields.removeAll(hidden);
         hidden.forEach(fieldWeights::remove);
         hidden.forEach(fieldIncludeLabels::remove);
      }
   }



   /*******************************************************************************
    ** The artifact version, from the Maven-filtered quick-search.properties.
    *******************************************************************************/
   static String loadVersion()
   {
      try(InputStream inputStream = QuickSearchQBitProducer.class.getClassLoader().getResourceAsStream("quick-search.properties"))
      {
         if(inputStream != null)
         {
            Properties properties = new Properties();
            properties.load(inputStream);
            String version = properties.getProperty("version");
            if(StringUtils.hasContent(version) && !version.contains("${"))
            {
               return (version);
            }
         }
      }
      catch(Exception e)
      {
         LOG.debug("Could not read quick-search.properties", e);
      }
      return ("unknown");
   }



   /*******************************************************************************
    ** Getter for the artifact version.
    *******************************************************************************/
   public static String getVersion()
   {
      return (VERSION);
   }

}

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


import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizers;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QBackendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qbits.quicksearch.annotations.QuickSearchField;
import com.kingsrook.qbits.quicksearch.annotations.QuickSearchable;
import com.kingsrook.qbits.quicksearch.customizers.QuickSearchPostDeleteCustomizer;
import com.kingsrook.qbits.quicksearch.customizers.QuickSearchPostInsertCustomizer;
import com.kingsrook.qbits.quicksearch.customizers.QuickSearchPostUpdateCustomizer;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import com.kingsrook.qbits.quicksearch.processes.BasepullIndexStep;
import com.kingsrook.qbits.quicksearch.processes.FullReindexStep;
import com.kingsrook.qbits.quicksearch.publisher.IndexEventPublisher;
import com.kingsrook.qbits.quicksearch.publisher.SynchronousIndexEventPublisher;


/*******************************************************************************
 ** Central orchestrator for the Quick Search QBit.
 **
 ** Validates config, discovers annotated tables, produces QQQ metadata
 ** (tables, processes, app), initializes the OpenSearch client, and registers
 ** real-time customizers on source tables.
 **
 ** Entry point: {@link #produce(QInstance)}.
 *******************************************************************************/
public class QuickSearchQBitProducer
{
   private static final QLogger LOG = QLogger.getLogger(QuickSearchQBitProducer.class);

   public static final String BASEPULL_PROCESS_NAME      = "quickSearchBasepullIndex";
   public static final String FULL_REINDEX_PROCESS_NAME  = "quickSearchFullReindex";
   public static final String APP_NAME                   = "quickSearchAdmin";

   private QuickSearchQBitConfig config;



   /***************************************************************************
    ** Fluent setter for config.
    ***************************************************************************/
   public QuickSearchQBitProducer withConfig(QuickSearchQBitConfig config)
   {
      this.config = config;
      return (this);
   }



   /***************************************************************************
    ** Getter for config.
    ***************************************************************************/
   public QuickSearchQBitConfig getConfig()
   {
      return (this.config);
   }



   /***************************************************************************
    ** Produce all QQQ metadata for the Quick Search QBit and register it
    ** into the given QInstance.
    **
    ** Steps:
    ** 1. Validate configuration
    ** 2. Discover @QuickSearchable annotated entity classes
    ** 3. Create and initialize the OpenSearch client
    ** 4. Create the index event publisher
    ** 5. Store everything in QuickSearchQBitContext
    ** 6. Produce operational tables
    ** 7. Produce processes
    ** 8. Register customizers on source tables (if real-time indexing enabled)
    ** 9. Produce an admin app
    **
    ** @param qInstance the QQQ instance to register metadata into
    ** @throws QException if validation fails or an unrecoverable error occurs
    ***************************************************************************/
   public void produce(QInstance qInstance) throws QException
   {
      ////////////////////////////////////////////////////
      // 1. Validate config                             //
      ////////////////////////////////////////////////////
      List<String> errors = new ArrayList<>();
      config.validate(qInstance, errors);
      if(!errors.isEmpty())
      {
         throw new QException("QuickSearchQBit configuration validation failed: " + String.join("; ", errors));
      }

      ////////////////////////////////////////////////////
      // 2. Discover searchable tables from annotations //
      ////////////////////////////////////////////////////
      List<QuickSearchableTableConfig> discoveredTables = discoverSearchableTables(qInstance);

      ////////////////////////////////////////////////////
      // 3. Create OpenSearch client                    //
      ////////////////////////////////////////////////////
      QuickSearchOpenSearchClient client = null;
      try
      {
         client = new QuickSearchOpenSearchClient(config);
      }
      catch(Exception e)
      {
         LOG.warn("Failed to create OpenSearch client during produce; continuing without client", e);
      }

      ////////////////////////////////////////////////////
      // 4. Initialize index (best-effort)              //
      ////////////////////////////////////////////////////
      if(client != null)
      {
         try
         {
            client.ensureIndexExists();
         }
         catch(Exception e)
         {
            LOG.warn("Failed to ensure OpenSearch index exists during produce; continuing", e);
         }
      }

      ////////////////////////////////////////////////////
      // 5. Create publisher                            //
      ////////////////////////////////////////////////////
      IndexEventPublisher publisher = null;
      if(config.getIndexEventPublisher() != null)
      {
         publisher = config.getIndexEventPublisher();
      }
      else if(client != null)
      {
         publisher = new SynchronousIndexEventPublisher(client, discoveredTables, config.getBulkBatchSize());
      }

      ////////////////////////////////////////////////////
      // 6. Store in context                            //
      ////////////////////////////////////////////////////
      QuickSearchQBitContext.setConfig(config);
      QuickSearchQBitContext.setClient(client);
      QuickSearchQBitContext.setPublisher(publisher);
      QuickSearchQBitContext.setDiscoveredTables(discoveredTables);

      ////////////////////////////////////////////////////
      // 7. Produce operational tables                  //
      ////////////////////////////////////////////////////
      String indexTableName    = config.applyPrefix(QuickSearchIndex.TABLE_NAME);
      String indexRunTableName = config.applyPrefix(QuickSearchIndexRun.TABLE_NAME);

      QTableMetaData indexTable = buildQuickSearchIndexTable(indexTableName, config.getBackendName());
      QTableMetaData indexRunTable = buildQuickSearchIndexRunTable(indexRunTableName, config.getBackendName());

      qInstance.addTable(indexTable);
      qInstance.addTable(indexRunTable);

      ////////////////////////////////////////////////////
      // 8. Produce processes                           //
      ////////////////////////////////////////////////////
      String basepullProcessName   = config.applyPrefix(BASEPULL_PROCESS_NAME);
      String fullReindexProcessName = config.applyPrefix(FULL_REINDEX_PROCESS_NAME);

      QProcessMetaData basepullProcess = buildBasepullProcess(basepullProcessName);
      QProcessMetaData fullReindexProcess = buildFullReindexProcess(fullReindexProcessName);

      qInstance.addProcess(basepullProcess);
      qInstance.addProcess(fullReindexProcess);

      ////////////////////////////////////////////////////
      // 9. Register customizers on source tables       //
      ////////////////////////////////////////////////////
      if(Boolean.TRUE.equals(config.getEnableRealTimeIndexing()))
      {
         registerCustomizers(qInstance, discoveredTables);
      }

      ////////////////////////////////////////////////////
      // 10. Produce admin app                          //
      ////////////////////////////////////////////////////
      String appName = config.applyPrefix(APP_NAME);
      QAppMetaData app = new QAppMetaData()
         .withName(appName)
         .withLabel("Quick Search Admin")
         .withChild(indexTable)
         .withChild(indexRunTable)
         .withChild(basepullProcess)
         .withChild(fullReindexProcess);

      qInstance.addApp(app);
   }



   /***************************************************************************
    ** Discover searchable table configurations by scanning annotated entity
    ** classes from the config.
    **
    ** For each class, extracts @QuickSearchable annotation attributes and
    ** scans fields for @QuickSearchField annotations. Falls back to the
    ** annotation's fields() array if no @QuickSearchField-annotated fields
    ** are found.
    **
    ** @param qInstance the QInstance to look up table primary keys
    ** @return list of discovered table configurations
    ***************************************************************************/
   List<QuickSearchableTableConfig> discoverSearchableTables(QInstance qInstance)
   {
      List<QuickSearchableTableConfig> tables = new ArrayList<>();
      List<Class<?>> entityClasses = config.getSearchableEntityClasses();

      if(entityClasses == null || entityClasses.isEmpty())
      {
         return (tables);
      }

      for(Class<?> entityClass : entityClasses)
      {
         QuickSearchable annotation = entityClass.getAnnotation(QuickSearchable.class);
         if(annotation == null)
         {
            LOG.warn("Entity class does not have @QuickSearchable annotation; skipping",
               "className", entityClass.getName());
            continue;
         }

         String tableName                  = annotation.tableName();
         Integer basepullIntervalMinutes   = annotation.basepullIntervalMinutes();
         String basepullTimestampField     = annotation.basepullTimestampField();
         Boolean enabledByDefault          = annotation.enabledByDefault();

         //////////////////////////////////////////////////////////
         // Scan fields for @QuickSearchField annotations        //
         //////////////////////////////////////////////////////////
         List<String> searchableFields         = new ArrayList<>();
         Map<String, Integer> fieldWeights     = new LinkedHashMap<>();
         Map<String, Boolean> fieldIncludeLabels = new LinkedHashMap<>();

         List<Field> allFields = new ArrayList<>();
         Class<?> current = entityClass;
         while(current != null && current != Object.class)
         {
            allFields.addAll(java.util.Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
         }
         for(Field field : allFields)
         {
            QuickSearchField fieldAnnotation = field.getAnnotation(QuickSearchField.class);
            if(fieldAnnotation != null)
            {
               String fieldName = field.getName();
               searchableFields.add(fieldName);
               fieldWeights.put(fieldName, fieldAnnotation.weight());
               fieldIncludeLabels.put(fieldName, fieldAnnotation.includeLabel());
            }
         }

         //////////////////////////////////////////////////////////
         // Fall back to @QuickSearchable.fields() if no         //
         // @QuickSearchField-annotated fields were found        //
         //////////////////////////////////////////////////////////
         if(searchableFields.isEmpty())
         {
            String[] annotationFields = annotation.fields();
            if(annotationFields != null)
            {
               for(String fieldName : annotationFields)
               {
                  searchableFields.add(fieldName);
                  fieldWeights.put(fieldName, 1);
                  fieldIncludeLabels.put(fieldName, false);
               }
            }
         }

         //////////////////////////////////////////////////////////
         // Get primary key field from QInstance table metadata   //
         //////////////////////////////////////////////////////////
         String primaryKeyField = "id";
         QTableMetaData table = qInstance.getTable(tableName);
         if(table != null)
         {
            primaryKeyField = table.getPrimaryKeyField();
         }

         QuickSearchableTableConfig tableConfig = new QuickSearchableTableConfig()
            .withTableName(tableName)
            .withPrimaryKeyField(primaryKeyField)
            .withSearchableFields(searchableFields)
            .withFieldWeights(fieldWeights)
            .withFieldIncludeLabels(fieldIncludeLabels)
            .withBasepullIntervalMinutes(basepullIntervalMinutes)
            .withBasepullTimestampField(basepullTimestampField)
            .withEnabledByDefault(enabledByDefault);

         tables.add(tableConfig);
      }

      return (tables);
   }



   /***************************************************************************
    ** Build QTableMetaData for the quickSearchIndex operational table.
    ***************************************************************************/
   private QTableMetaData buildQuickSearchIndexTable(String tableName, String backendName)
   {
      return new QTableMetaData()
         .withName(tableName)
         .withLabel("Quick Search Index")
         .withBackendName(backendName)
         .withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("tableName", QFieldType.STRING))
         .withField(new QFieldMetaData("enabled", QFieldType.BOOLEAN))
         .withField(new QFieldMetaData("basepullIntervalMinutes", QFieldType.INTEGER))
         .withField(new QFieldMetaData("basepullTimestampField", QFieldType.STRING))
         .withField(new QFieldMetaData("searchableFieldsJson", QFieldType.STRING))
         .withField(new QFieldMetaData("lastBasepullTime", QFieldType.DATE_TIME))
         .withField(new QFieldMetaData("lastFullReindexTime", QFieldType.DATE_TIME))
         .withField(new QFieldMetaData("recordCount", QFieldType.INTEGER))
         .withField(new QFieldMetaData("status", QFieldType.STRING))
         .withField(new QFieldMetaData("createDate", QFieldType.DATE_TIME))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME));
   }



   /***************************************************************************
    ** Build QTableMetaData for the quickSearchIndexRun operational table.
    ***************************************************************************/
   private QTableMetaData buildQuickSearchIndexRunTable(String tableName, String backendName)
   {
      return new QTableMetaData()
         .withName(tableName)
         .withLabel("Quick Search Index Run")
         .withBackendName(backendName)
         .withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("quickSearchIndexId", QFieldType.INTEGER))
         .withField(new QFieldMetaData("runType", QFieldType.STRING))
         .withField(new QFieldMetaData("status", QFieldType.STRING))
         .withField(new QFieldMetaData("startTime", QFieldType.DATE_TIME))
         .withField(new QFieldMetaData("endTime", QFieldType.DATE_TIME))
         .withField(new QFieldMetaData("recordsProcessed", QFieldType.INTEGER))
         .withField(new QFieldMetaData("recordsIndexed", QFieldType.INTEGER))
         .withField(new QFieldMetaData("errorCount", QFieldType.INTEGER))
         .withField(new QFieldMetaData("errorMessage", QFieldType.STRING))
         .withField(new QFieldMetaData("createDate", QFieldType.DATE_TIME))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME));
   }



   /***************************************************************************
    ** Build the basepull indexing process metadata.
    ***************************************************************************/
   private QProcessMetaData buildBasepullProcess(String processName)
   {
      return new QProcessMetaData()
         .withName(processName)
         .withLabel("Quick Search Basepull Index")
         .withStep(new QBackendStepMetaData()
            .withName("basepull")
            .withCode(new QCodeReference(BasepullIndexStep.class)));
   }



   /***************************************************************************
    ** Build the full reindex process metadata with optional tableName input.
    ***************************************************************************/
   private QProcessMetaData buildFullReindexProcess(String processName)
   {
      return new QProcessMetaData()
         .withName(processName)
         .withLabel("Quick Search Full Reindex")
         .withStep(new QBackendStepMetaData()
            .withName("fullReindex")
            .withCode(new QCodeReference(FullReindexStep.class)));
   }



   /***************************************************************************
    ** Register post-insert, post-update, and post-delete customizers on each
    ** discovered source table.
    ***************************************************************************/
   private void registerCustomizers(QInstance qInstance, List<QuickSearchableTableConfig> discoveredTables)
   {
      for(QuickSearchableTableConfig tableConfig : discoveredTables)
      {
         String tableName = tableConfig.getTableName();
         QTableMetaData table = qInstance.getTable(tableName);

         if(table == null)
         {
            LOG.warn("Source table not found in QInstance; skipping customizer registration",
               "tableName", tableName);
            continue;
         }

         table.withCustomizer(TableCustomizers.POST_INSERT_RECORD,
            new QCodeReference(QuickSearchPostInsertCustomizer.class));

         table.withCustomizer(TableCustomizers.POST_UPDATE_RECORD,
            new QCodeReference(QuickSearchPostUpdateCustomizer.class));

         table.withCustomizer(TableCustomizers.POST_DELETE_RECORD,
            new QCodeReference(QuickSearchPostDeleteCustomizer.class));
      }
   }

}

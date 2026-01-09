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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.kingsrook.qbits.quicksearch.annotations.QuickSearchField;
import com.kingsrook.qbits.quicksearch.annotations.QuickSearchable;
import com.kingsrook.qbits.quicksearch.customizers.QuickSearchDeleteCustomizer;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import com.kingsrook.qbits.quicksearch.processes.BasepullIndexStep;
import com.kingsrook.qbits.quicksearch.processes.FullReindexStep;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppSection;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValue;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSourceType;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QBackendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFrontendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitProducer;
import com.kingsrook.qqq.backend.core.model.metadata.scheduleing.QScheduleMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizers;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Producer for the Quick Search QBit.
 *******************************************************************************/
public class QuickSearchQBitProducer implements QBitProducer
{
   private static final QLogger LOG = QLogger.getLogger(QuickSearchQBitProducer.class);

   public static final String GROUP_ID                    = "com.kingsrook.qbits";
   public static final String ARTIFACT_ID                 = "qbit-quick-search";
   public static final String VERSION                     = "0.1.0";
   public static final String FULL_REINDEX_PROCESS_NAME   = "quickSearchFullReindex";
   public static final String BASEPULL_PROCESS_NAME       = "quickSearchBasepull";
   public static final String APP_NAME                    = "quickSearchApp";

   private QuickSearchQBitConfig config;



   /***************************************************************************
    ** Produce this QBit into the given QInstance.
    ***************************************************************************/
   @Override
   public void produce(QInstance qInstance, String namespace) throws QException
   {
      /////////////////////////////
      // Validate configuration //
      /////////////////////////////
      List<String> errors = new ArrayList<>();
      config.validate(qInstance, errors);
      if(!errors.isEmpty())
      {
         throw new QException("QuickSearch QBit configuration errors: " + String.join(", ", errors));
      }

      ///////////////////////////////
      // Register QBit identity //
      ///////////////////////////////
      QBitMetaData qBitMetaData = new QBitMetaData()
         .withGroupId(GROUP_ID)
         .withArtifactId(ARTIFACT_ID)
         .withVersion(VERSION)
         .withNamespace(namespace)
         .withConfig(config);

      qInstance.addQBit(qBitMetaData);

      ///////////////////////////////////////
      // Discover @QuickSearchable tables //
      ///////////////////////////////////////
      List<QuickSearchableTableConfig> discoveredTables = discoverSearchableTables(qInstance);
      LOG.info("Discovered searchable tables", logPair("count", discoveredTables.size()));

      ///////////////////////////////
      // Produce operational tables //
      ///////////////////////////////
      producePossibleValueSources(qInstance);
      produceQuickSearchIndexTable(qInstance);
      produceQuickSearchIndexRunTable(qInstance);

      ///////////////////////////////
      // Produce processes //
      ///////////////////////////////
      produceFullReindexProcess(qInstance);
      produceBasepullProcess(qInstance);

      ///////////////////////////////
      // Produce app //
      ///////////////////////////////
      produceApp(qInstance);

      ///////////////////////////////
      // Initialize OpenSearch //
      ///////////////////////////////
      initializeOpenSearch();

      ///////////////////////////////
      // Register delete customizers //
      ///////////////////////////////
      registerDeleteCustomizers(qInstance, discoveredTables);

      ///////////////////////////////
      // Store discovered configs //
      ///////////////////////////////
      QuickSearchQBitContext.setConfig(config);
      QuickSearchQBitContext.setDiscoveredTables(discoveredTables);
   }



   /***************************************************************************
    ** Discover tables annotated with @QuickSearchable.
    ***************************************************************************/
   private List<QuickSearchableTableConfig> discoverSearchableTables(QInstance qInstance)
   {
      List<QuickSearchableTableConfig> result = new ArrayList<>();

      List<Class<?>> entityClasses = config.getSearchableEntityClasses();
      if(entityClasses == null || entityClasses.isEmpty())
      {
         return result;
      }

      for(Class<?> entityClass : entityClasses)
      {
         if(!entityClass.isAnnotationPresent(QuickSearchable.class))
         {
            LOG.warn("Entity class not annotated with @QuickSearchable", logPair("class", entityClass.getName()));
            continue;
         }

         QuickSearchable annotation = entityClass.getAnnotation(QuickSearchable.class);
         String tableName = annotation.tableName();

         QTableMetaData table = qInstance.getTable(tableName);
         if(table == null)
         {
            LOG.warn("Table not found for searchable entity", logPair("class", entityClass.getName()), logPair("tableName", tableName));
            continue;
         }

         QuickSearchableTableConfig tableConfig = buildTableConfig(table, entityClass, annotation);
         result.add(tableConfig);

         LOG.debug("Found searchable table", logPair("tableName", table.getName()), logPair("fields", tableConfig.getSearchableFields()));
      }

      return result;
   }



   /***************************************************************************
    ** Build configuration for a single searchable table.
    ***************************************************************************/
   private QuickSearchableTableConfig buildTableConfig(QTableMetaData table, Class<?> recordClass, QuickSearchable annotation)
   {
      List<String> searchableFields = new ArrayList<>();

      if(annotation.fields().length > 0)
      {
         searchableFields.addAll(Arrays.asList(annotation.fields()));
      }
      else
      {
         for(Field field : recordClass.getDeclaredFields())
         {
            if(field.isAnnotationPresent(QuickSearchField.class))
            {
               searchableFields.add(field.getName());
            }
         }
      }

      return new QuickSearchableTableConfig()
         .withTableName(table.getName())
         .withSearchableFields(searchableFields)
         .withBasepullIntervalMinutes(annotation.basepullIntervalMinutes())
         .withBasepullTimestampField(annotation.basepullTimestampField())
         .withEnabledByDefault(annotation.enabledByDefault());
   }



   /***************************************************************************
    ** Produce possible value sources for enum fields.
    ***************************************************************************/
   private void producePossibleValueSources(QInstance qInstance)
   {
      String prefix = config.getTableNamePrefix();

      qInstance.addPossibleValueSource(new QPossibleValueSource()
         .withName(prefix + "quickSearchRunType")
         .withType(QPossibleValueSourceType.ENUM)
         .withEnumValues(List.of(
            new QPossibleValue<>("FULL", "Full Reindex"),
            new QPossibleValue<>("BASEPULL", "Basepull")
         )));

      qInstance.addPossibleValueSource(new QPossibleValueSource()
         .withName(prefix + "quickSearchRunStatus")
         .withType(QPossibleValueSourceType.ENUM)
         .withEnumValues(List.of(
            new QPossibleValue<>("RUNNING", "Running"),
            new QPossibleValue<>("COMPLETE", "Complete"),
            new QPossibleValue<>("ERROR", "Error")
         )));
   }



   /***************************************************************************
    ** Produce the quickSearchIndex table.
    ***************************************************************************/
   private void produceQuickSearchIndexTable(QInstance qInstance)
   {
      String tableName = config.applyPrefix(QuickSearchIndex.TABLE_NAME);

      QTableMetaData table = new QTableMetaData()
         .withName(tableName)
         .withLabel("Quick Search Index")
         .withBackendName(config.getBackendName())
         .withPrimaryKeyField("id")
         .withRecordLabelFormat("%s")
         .withRecordLabelFields(List.of("tableName"))
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("tableName", QFieldType.STRING).withLabel("Table Name").withIsRequired(true).withIsEditable(false))
         .withField(new QFieldMetaData("isEnabled", QFieldType.BOOLEAN).withLabel("Enabled"))
         .withField(new QFieldMetaData("basepullIntervalMinutes", QFieldType.INTEGER).withLabel("Basepull Interval (min)"))
         .withField(new QFieldMetaData("basepullTimestampField", QFieldType.STRING).withLabel("Timestamp Field"))
         .withField(new QFieldMetaData("searchableFieldsJson", QFieldType.STRING).withLabel("Searchable Fields"))
         .withField(new QFieldMetaData("lastFullIndexTime", QFieldType.DATE_TIME).withLabel("Last Full Index").withIsEditable(false))
         .withField(new QFieldMetaData("lastBasepullTime", QFieldType.DATE_TIME).withLabel("Last Basepull").withIsEditable(false))
         .withField(new QFieldMetaData("indexedRecordCount", QFieldType.INTEGER).withLabel("Record Count").withIsEditable(false))
         .withField(new QFieldMetaData("createDate", QFieldType.DATE_TIME).withLabel("Create Date").withIsEditable(false))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME).withLabel("Modify Date").withIsEditable(false));

      qInstance.addTable(table);
   }



   /***************************************************************************
    ** Produce the quickSearchIndexRun table.
    ***************************************************************************/
   private void produceQuickSearchIndexRunTable(QInstance qInstance)
   {
      String tableName = config.applyPrefix(QuickSearchIndexRun.TABLE_NAME);
      String indexTableName = config.applyPrefix(QuickSearchIndex.TABLE_NAME);
      String prefix = config.getTableNamePrefix();

      QTableMetaData table = new QTableMetaData()
         .withName(tableName)
         .withLabel("Quick Search Index Run")
         .withBackendName(config.getBackendName())
         .withPrimaryKeyField("id")
         .withRecordLabelFormat("%s - %s")
         .withRecordLabelFields(List.of("runType", "startTime"))
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("quickSearchIndexId", QFieldType.INTEGER).withLabel("Index").withIsRequired(true).withPossibleValueSourceName(indexTableName))
         .withField(new QFieldMetaData("runType", QFieldType.STRING).withLabel("Run Type").withIsRequired(true).withPossibleValueSourceName(prefix + "quickSearchRunType"))
         .withField(new QFieldMetaData("status", QFieldType.STRING).withLabel("Status").withIsRequired(true).withPossibleValueSourceName(prefix + "quickSearchRunStatus"))
         .withField(new QFieldMetaData("startTime", QFieldType.DATE_TIME).withLabel("Start Time"))
         .withField(new QFieldMetaData("endTime", QFieldType.DATE_TIME).withLabel("End Time"))
         .withField(new QFieldMetaData("recordsProcessed", QFieldType.INTEGER).withLabel("Records Processed"))
         .withField(new QFieldMetaData("recordsIndexed", QFieldType.INTEGER).withLabel("Records Indexed"))
         .withField(new QFieldMetaData("errorCount", QFieldType.INTEGER).withLabel("Error Count"))
         .withField(new QFieldMetaData("errorMessage", QFieldType.STRING).withLabel("Error Message"));

      qInstance.addTable(table);
   }



   /***************************************************************************
    ** Produce the full reindex process.
    ***************************************************************************/
   private void produceFullReindexProcess(QInstance qInstance)
   {
      String processName = config.applyPrefix(FULL_REINDEX_PROCESS_NAME);
      String indexTableName = config.applyPrefix(QuickSearchIndex.TABLE_NAME);

      QProcessMetaData process = new QProcessMetaData()
         .withName(processName)
         .withLabel("Full Reindex")
         .withTableName(indexTableName)
         .withStepList(List.of(
            new QFrontendStepMetaData()
               .withName("input")
               .withFormFields(List.of(
                  new QFieldMetaData("tableName", QFieldType.STRING)
                     .withLabel("Table Name (empty for all)")
                     .withIsRequired(false)
               )),
            new QBackendStepMetaData()
               .withName("execute")
               .withCode(new QCodeReference(FullReindexStep.class))
         ));

      qInstance.addProcess(process);
   }



   /***************************************************************************
    ** Produce the basepull index process.
    ***************************************************************************/
   private void produceBasepullProcess(QInstance qInstance)
   {
      String processName = config.applyPrefix(BASEPULL_PROCESS_NAME);

      QProcessMetaData process = new QProcessMetaData()
         .withName(processName)
         .withLabel("Basepull Index Update")
         .withStepList(List.of(
            new QBackendStepMetaData()
               .withName("execute")
               .withCode(new QCodeReference(BasepullIndexStep.class))
         ));

      if(!Boolean.FALSE.equals(config.getEnableScheduledProcesses()))
      {
         process.withSchedule(new QScheduleMetaData()
            .withRepeatSeconds(60)
            .withDescription("Quick Search Basepull - runs every minute"));
      }

      qInstance.addProcess(process);
   }



   /***************************************************************************
    ** Produce the maintenance app.
    ***************************************************************************/
   private void produceApp(QInstance qInstance)
   {
      String appName = config.applyPrefix(APP_NAME);
      String indexTableName = config.applyPrefix(QuickSearchIndex.TABLE_NAME);
      String runTableName = config.applyPrefix(QuickSearchIndexRun.TABLE_NAME);
      String reindexProcessName = config.applyPrefix(FULL_REINDEX_PROCESS_NAME);

      QAppSection mainSection = new QAppSection()
         .withName("main")
         .withLabel("Search Indexes")
         .withIcon(new QIcon().withName("search"))
         .withTables(List.of(indexTableName, runTableName))
         .withProcesses(List.of(reindexProcessName));

      QAppMetaData app = new QAppMetaData()
         .withName(appName)
         .withLabel("Quick Search")
         .withIcon(new QIcon().withName("search"))
         .withSections(List.of(mainSection));

      qInstance.addApp(app);
   }



   /***************************************************************************
    ** Initialize the OpenSearch index.
    ***************************************************************************/
   private void initializeOpenSearch() throws QException
   {
      try
      {
         QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(config);
         client.ensureIndexExists();
         client.close();
         LOG.info("OpenSearch index initialized", logPair("indexName", config.getOpensearchIndexName()));
      }
      catch(Exception e)
      {
         LOG.warn("Failed to initialize OpenSearch index - search may not work", logPair("indexName", config.getOpensearchIndexName()), e);
      }
   }



   /***************************************************************************
    ** Register delete customizers on discovered tables.
    ***************************************************************************/
   private void registerDeleteCustomizers(QInstance qInstance, List<QuickSearchableTableConfig> discoveredTables)
   {
      for(QuickSearchableTableConfig tableConfig : discoveredTables)
      {
         QTableMetaData table = qInstance.getTable(tableConfig.getTableName());
         if(table != null)
         {
            String primaryKeyField = table.getPrimaryKeyField();
            QuickSearchDeleteCustomizer customizer = new QuickSearchDeleteCustomizer(config, tableConfig.getTableName(), primaryKeyField);

            table.withCustomizer(TableCustomizers.POST_DELETE_RECORD, new QCodeReference(customizer.getClass()));

            LOG.debug("Registered delete customizer", logPair("tableName", tableConfig.getTableName()));
         }
      }
   }



   /***************************************************************************
    ** Fluent setter for config.
    ***************************************************************************/
   public QuickSearchQBitProducer withConfig(QuickSearchQBitConfig config)
   {
      this.config = config;
      return this;
   }



   /***************************************************************************
    ** Getter for config.
    ***************************************************************************/
   public QuickSearchQBitConfig getConfig()
   {
      return config;
   }

}

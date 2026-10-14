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


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.processes.RunProcessAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.StreamedETLWithFrontendProcess;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qbits.quicksearch.BaseQuickSearchTest;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitContext;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.metadata.QuickSearchConfigProcessMetaDataHelper;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexField;
import com.kingsrook.qbits.quicksearch.opensearch.BulkIndexResult;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import org.junit.jupiter.api.BeforeEach;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;


/*******************************************************************************
 ** Base of the data-driven config tests: the in-memory instance of
 ** BaseQuickSearchTest with a registered Quick Search runtime (its client a
 ** mock that records indexed documents), testEntity declared in code, an
 ** "article" table that is not, a "secretVault" table and the config
 ** processes. The static discovered tables of the base are cleared, so the
 ** effective config comes from the records.
 *******************************************************************************/
public abstract class BaseDataDrivenTest extends BaseQuickSearchTest
{
   protected static final String ARTICLE_TABLE = "article";
   protected static final String SECRET_TABLE  = "secretVault";

   protected QuickSearchQBitConfig       config;
   protected QuickSearchRuntime          runtime;
   protected QuickSearchOpenSearchClient mockClient;
   protected List<OpenSearchDocument>    indexed;



   /*******************************************************************************
    ** Register the runtime and processes.
    *******************************************************************************/
   @BeforeEach
   void setUpDataDriven() throws QException
   {
      QInstance qInstance = QContext.getQInstance();
      qInstance.addTable(new QTableMetaData()
         .withName(ARTICLE_TABLE)
         .withLabel("Article")
         .withBackendName(TEST_BACKEND_NAME)
         .withPrimaryKeyField("id")
         .withRecordLabelFields("title")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("title", QFieldType.STRING))
         .withField(new QFieldMetaData("body", QFieldType.TEXT))
         .withField(new QFieldMetaData("sku", QFieldType.STRING))
         .withField(new QFieldMetaData("internalNotes", QFieldType.STRING).withIsSensitive(true))
         .withField(new QFieldMetaData("hiddenCode", QFieldType.STRING).withIsHidden(true))
         .withField(new QFieldMetaData("published", QFieldType.BOOLEAN))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME)));
      qInstance.addTable(new QTableMetaData()
         .withName(SECRET_TABLE)
         .withBackendName(TEST_BACKEND_NAME)
         .withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("name", QFieldType.STRING)));

      config = new QuickSearchQBitConfig()
         .withBackendName(TEST_BACKEND_NAME)
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-index")
         .withServeCoreRecordSearch(false);

      indexed    = new ArrayList<>();
      mockClient = mock(QuickSearchOpenSearchClient.class);
      doAnswer(invocation ->
      {
         List<OpenSearchDocument> docs = invocation.getArgument(0);
         indexed.addAll(docs);
         return (new BulkIndexResult().withSuccessCount(docs.size()));
      }).when(mockClient).indexDocuments(anyList(), anyInt());
      when(mockClient.deleteDocuments(anyList(), anyInt())).thenAnswer(i -> new BulkIndexResult().withSuccessCount(((List<?>) i.getArgument(0)).size()));
      when(mockClient.countDocumentsForTable(anyString())).thenReturn(0L);
      when(mockClient.indexExists()).thenReturn(true);
      when(mockClient.countAllDocuments()).thenReturn(1_000L);
      when(mockClient.deleteDocumentsIndexedBefore(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(0L);

      QuickSearchRuntime real = new QuickSearchRuntime(config);
      runtime = spy(real);
      doReturn(mockClient).when(runtime).getClient();
      runtime.setDiscoveredTables(List.of(new QuickSearchableTableConfig()
         .withTableName(TEST_ENTITY_TABLE)
         .withPrimaryKeyField("id")
         .withSearchableFields(List.of("name", "description"))
         .withFieldWeights(Map.of("name", 3, "description", 1))
         .withBasepullTimestampField("modifyDate")
         .withBasepullIntervalMinutes(5)
         .withEnabledByDefault(true)));
      config.setRuntime(runtime);

      if(qInstance.getQBits() != null)
      {
         qInstance.getQBits().clear();
      }
      qInstance.addQBit(new QBitMetaData().withGroupId("com.kingsrook.qbits").withArtifactId("quick-search").withVersion("test").withConfig(config));
      for(QProcessMetaData process : QuickSearchConfigProcessMetaDataHelper.buildProcesses(config))
      {
         qInstance.addProcess(process);
      }

      QuickSearchQBitContext.setDiscoveredTables(null);
      QuickSearchQBitContext.setConfig(null);
      QuickSearchQBitContext.setClient(null);
   }



   /*******************************************************************************
    ** Run a config process past every screen with the given values (records
    ** selected by id when ids are given).
    *******************************************************************************/
   protected RunProcessOutput runProcess(String processName, Map<String, Serializable> values, List<Integer> ids) throws QException
   {
      RunProcessInput input = new RunProcessInput();
      input.setProcessName(processName);
      input.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.SKIP);
      values.forEach(input::addValue);
      if(ids != null)
      {
         input.addValue("queryFilterJson", JsonUtils.toJson(new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.IN, new ArrayList<>(ids)))));
      }
      return (new RunProcessAction().execute(input));
   }



   /*******************************************************************************
    ** The messages of a process run's result-screen summary.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   protected static List<String> resultMessages(RunProcessOutput output)
   {
      List<String> messages = new ArrayList<>();
      Serializable summary  = output.getValues().get(StreamedETLWithFrontendProcess.FIELD_PROCESS_SUMMARY);
      if(summary instanceof List<?> list)
      {
         for(Object line : list)
         {
            messages.add(lineText((ProcessSummaryLineInterface) line));
         }
      }
      return (messages);
   }



   /*******************************************************************************
    ** The messages of a process run's review-screen (validation) summary.
    *******************************************************************************/
   protected static List<String> reviewMessages(RunProcessOutput output)
   {
      List<String> messages = new ArrayList<>();
      Serializable summary  = output.getValues().get(StreamedETLWithFrontendProcess.FIELD_VALIDATION_SUMMARY);
      if(summary instanceof List<?> list)
      {
         for(Object line : list)
         {
            messages.add(lineText((ProcessSummaryLineInterface) line));
         }
      }
      return (messages);
   }



   /*******************************************************************************
    ** A summary line's message with its bullets.
    *******************************************************************************/
   private static String lineText(ProcessSummaryLineInterface line)
   {
      StringBuilder text = new StringBuilder(String.valueOf(line.getMessage()));
      if(line instanceof com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine summaryLine && summaryLine.getBulletsOfText() != null)
      {
         text.append(" ").append(String.join(" | ", summaryLine.getBulletsOfText()));
      }
      return (text.toString());
   }



   /*******************************************************************************
    ** All rows of a table.
    *******************************************************************************/
   protected static List<QRecord> rows(String tableName) throws QException
   {
      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(tableName);
      queryInput.setFilter(new QQueryFilter());
      return (CollectionUtils.nonNullList(new QueryAction().execute(queryInput).getRecords()));
   }



   /*******************************************************************************
    ** The index row of a table, or null.
    *******************************************************************************/
   protected static QRecord indexRow(String tableName) throws QException
   {
      return (rows(QuickSearchIndex.TABLE_NAME).stream().filter(r -> tableName.equals(r.getValueString("tableName"))).findFirst().orElse(null));
   }



   /*******************************************************************************
    ** The field rows of a table's index.
    *******************************************************************************/
   protected static List<QRecord> fieldRows(String tableName) throws QException
   {
      QRecord index = indexRow(tableName);
      if(index == null)
      {
         return (List.of());
      }
      return (rows(QuickSearchIndexField.TABLE_NAME).stream().filter(r -> index.getValueInteger("id").equals(r.getValueInteger("quickSearchIndexId"))).toList());
   }



   /*******************************************************************************
    ** Insert rows into a table.
    *******************************************************************************/
   protected static List<QRecord> insert(String tableName, QRecord... records) throws QException
   {
      return (new InsertAction().execute(new InsertInput(tableName).withRecords(List.of(records))).getRecords());
   }

}

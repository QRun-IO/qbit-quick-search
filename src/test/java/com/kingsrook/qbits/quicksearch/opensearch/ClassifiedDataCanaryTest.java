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
package com.kingsrook.qbits.quicksearch.opensearch;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.ai.AiFieldMetaData;
import com.kingsrook.qqq.backend.core.actions.ai.DataClassification;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kingsrook.qbits.quicksearch.BaseQuickSearchTest;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitContext;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchAction;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchInput;
import com.kingsrook.qbits.quicksearch.listeners.QuickSearchRecordChangeListener;
import com.kingsrook.qbits.quicksearch.processes.BasepullIndexStep;
import com.kingsrook.qbits.quicksearch.processes.IndexingUtils;
import com.kingsrook.qbits.quicksearch.publisher.SynchronousIndexEventPublisher;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.core.SearchResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;


/*******************************************************************************
 ** Canary coverage at the backend seam for live writes, batch indexing and
 ** stale search results. No OpenSearch server is required.
 *******************************************************************************/
class ClassifiedDataCanaryTest extends BaseQuickSearchTest
{
   private static final String CANARY = "PHI-CANARY-7f3c";



   /*******************************************************************************
    ** Classified values must never reach any document representation or result,
    ** even with explicit field lists, label formats, roles and analyzer copies.
    *******************************************************************************/
   @Test
   void testCanaryAcrossRealtimeBasepullAndResults() throws Exception
   {
      QTableMetaData table = QContext.getQInstance().getTable(TEST_ENTITY_TABLE);
      table.getField("description").withSupplementalMetaData(new AiFieldMetaData().withClassification(DataClassification.PHI));
      table.withField(new QFieldMetaData("personal", QFieldType.STRING)
         .withSupplementalMetaData(new AiFieldMetaData().withClassification(DataClassification.PII)));
      table.withRecordLabelFields("description").withRecordLabelFormat("%s");

      QuickSearchableTableConfig fields = new QuickSearchableTableConfig().withTableName(TEST_ENTITY_TABLE).withPrimaryKeyField("id")
         .withSearchableFields(List.of("name", "description", "personal")).withEnabledByDefault(true)
         .withBasepullTimestampField("modifyDate").withBasepullIntervalMinutes(5)
         .withRecordLabelFields(List.of("description", "personal")).withRecordLabelFormat("%s %s")
         .withFieldRoles(Map.of("name", "TITLE", "description", "SUBTITLE", "personal", "DISPLAY"))
         .withFieldAnalyzers(Map.of("description", "ENGLISH", "personal", "EXACT"));
      QuickSearchOpenSearchClient client = mock(QuickSearchOpenSearchClient.class);
      List<OpenSearchDocument> captured = new ArrayList<>();
      doAnswer(invocation ->
      {
         List<OpenSearchDocument> documents = invocation.getArgument(0);
         captured.addAll(documents);
         return (new BulkIndexResult().withSuccessCount(documents.size()));
      }).when(client).indexDocuments(anyList(), anyInt());
      when(client.indexExists()).thenReturn(true);
      when(client.countAllDocuments()).thenReturn(1_000L);
      when(client.countDocumentsForTable(anyString())).thenReturn(1L);

      QuickSearchQBitConfig config = QuickSearchQBitContext.getConfig();
      config.withIndexEventPublisher(new SynchronousIndexEventPublisher(client, List.of(fields), 100));
      QuickSearchRuntime runtime = spy(new QuickSearchRuntime(config));
      doReturn(client).when(runtime).getClient();
      runtime.setDiscoveredTables(List.of(fields));
      config.setRuntime(runtime);
      QContext.getQInstance().addQBit(new QBitMetaData().withGroupId("com.kingsrook.qbits").withArtifactId("quick-search").withVersion("test").withConfig(config));
      QContext.getQInstance().withRecordChangeListener(new QCodeReference(QuickSearchRecordChangeListener.class));
      QuickSearchQBitContext.setClient(client);
      QuickSearchQBitContext.setDiscoveredTables(List.of(fields));

      new InsertAction().execute(new InsertInput(TEST_ENTITY_TABLE).withRecord(new QRecord()
         .withValue("name", "Public widget").withValue("description", CANARY).withValue("personal", CANARY)));
      assertThat(captured).as("real-time indexing reached the backend").hasSize(1);
      new BasepullIndexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());
      assertThat(captured.size()).as("basepull also reached the backend").isGreaterThan(1);
      ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
      for(OpenSearchDocument document : captured)
      {
         assertThat(mapper.writeValueAsString(document)).doesNotContain(CANARY).contains("Public widget");
         assertThat(document.getFieldValues()).containsEntry("name", "Public widget").doesNotContainKeys("description", "personal");
      }

      OpenSearchDocument stale = new OpenSearchDocument().withSourceTable(TEST_ENTITY_TABLE).withRecordId("1")
         .withRecordLabel(CANARY).withSearchableText(CANARY).withSubtitle(CANARY).withDisplayValues(Map.of("Personal", CANARY))
         .withFieldValues(Map.of("name", "Public widget", "description", CANARY, "personal", CANARY));
      SearchResponse<OpenSearchDocument> response = new SearchResponse.Builder<OpenSearchDocument>().took(1).timedOut(false)
         .shards(sh -> sh.total(1).successful(1).failed(0))
         .hits(h -> h.hits(hit -> hit.index("test").id("1").source(stale).highlight(Map.of("searchableText", List.of(CANARY))))).build();
      when(client.search(anyString(), anyList(), anyInt(), anyInt(), anyList(), any())).thenReturn(response);
      config.setApplyRecordSecurityLocks(false);
      var result = new QuickSearchAction().execute(new QuickSearchInput().withSearchTerm("Public"));
      assertThat(result.getResults()).hasSize(1);
      assertThat(mapper.writeValueAsString(result)).doesNotContain(CANARY).contains("Public widget");

      try(QuickSearchOpenSearchClient queryClient = new QuickSearchOpenSearchClient(config))
      {
         String request = queryClient.buildSearchQuery("Public", List.of(TEST_ENTITY_TABLE), List.of(fields), false).toJsonString();
         assertThat(request).doesNotContain("searchableText", "fieldValues.description", "fieldValuesEnglish.description", "fieldValuesExact.personal", CANARY);
         assertThat(request).contains("fieldValues.name");
      }
   }



   /*******************************************************************************
    ** Diagnostic document views must not reveal protected values retained in
    ** old index documents, including analyzer copies and combined labels.
    *******************************************************************************/
   @Test
   void testDiagnosticDocumentReadsOmitClassifiedValues() throws Exception
   {
      QContext.getQInstance().getTable(TEST_ENTITY_TABLE).getField("description")
         .withSupplementalMetaData(new AiFieldMetaData().withClassification(DataClassification.PHI));
      OpenSearchDocument stale = new OpenSearchDocument().withRecordId("1").withSourceTable(TEST_ENTITY_TABLE)
         .withRecordLabel(CANARY).withSearchableText(CANARY).withSubtitle(CANARY).withDisplayValues(Map.of("Secret", CANARY))
         .withFieldValues(Map.of("name", "Public widget", "description", CANARY))
         .withFieldValuesWord(Map.of("description", CANARY)).withFieldValuesEnglish(Map.of("description", CANARY))
         .withFieldValuesExact(Map.of("description", CANARY));
      ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
      var body = mapper.valueToTree(Map.of("found", true, "_source", stale,
         "docs", List.of(Map.of("found", true, "_id", TEST_ENTITY_TABLE + ":1", "_source", stale))));
      QuickSearchOpenSearchClient client = mock(QuickSearchOpenSearchClient.class);
      when(client.getIndexName()).thenReturn("test");
      QuickSearchDiagnosticsOperations ops = spy(new QuickSearchDiagnosticsOperations(client));
      doReturn(new QuickSearchDiagnosticsOperations.RawResponse(200, body)).when(ops).raw(anyString(), anyString(), any());
      assertThat(ops.getDocument(TEST_ENTITY_TABLE, "1").source().toString()).doesNotContain(CANARY).contains("Public widget");
      assertThat(ops.getDocuments(TEST_ENTITY_TABLE, List.of("1")).get("1").toString()).doesNotContain(CANARY).contains("Public widget");
   }



   /*******************************************************************************
    ** Recheck metadata at document construction, including primary keys and
    ** untrusted preformatted labels, without depending on config priming.
    *******************************************************************************/
   @Test
   void testTableClassificationAndProtectedPrimaryKey() throws Exception
   {
      QTableMetaData table = QContext.getQInstance().getTable(TEST_ENTITY_TABLE);
      table.getField("description").withSupplementalMetaData(new AiFieldMetaData().withClassification(DataClassification.PHI));
      table.withRecordLabelFields("description").withRecordLabelFormat("%s");
      QRecord record = new QRecord().withValue("id", 1).withValue("name", "Public widget").withValue("description", CANARY);
      record.setRecordLabel(CANARY);
      OpenSearchDocument document = IndexingUtils.buildDocument(record, QuickSearchQBitContext.getDiscoveredTables().get(0));
      assertThat(document.getRecordLabel()).isNull();
      assertThat(document.getSearchableText()).isEqualTo("Public widget");
      table.getField("id").withSupplementalMetaData(new AiFieldMetaData().withClassification(DataClassification.PII));
      assertThat(IndexingUtils.buildDocument(record, QuickSearchQBitContext.getDiscoveredTables().get(0))).isNull();
   }
}

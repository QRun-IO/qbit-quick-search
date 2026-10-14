/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2021-2026.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qbits.quicksearch;


import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kingsrook.qbits.quicksearch.api.QuickSearchSuggest;
import com.kingsrook.qbits.quicksearch.api.QuickSearchSuggestService;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.processes.IndexingUtils;
import com.kingsrook.qqq.backend.core.actions.ai.AiFieldMetaData;
import com.kingsrook.qqq.backend.core.actions.ai.DataClassification;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.security.QSecurityKeyType;
import com.kingsrook.qqq.backend.core.model.metadata.security.RecordSecurityLock;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;


/*******************************************************************************
 ** Actual edge-ngram matches, grouping, permissions and classified stale documents.
 *******************************************************************************/
@ExtendWith(RequiresDockerCondition.class)
@Testcontainers
class QuickSearchSuggestIntegrationTest extends BaseQuickSearchTest
{
   @Container
   static GenericContainer<?> opensearch = new GenericContainer<>(System.getProperty("opensearch.test.image", "opensearchproject/opensearch:2.19.6"))
      .withExposedPorts(9200)
      .withEnv("discovery.type", "single-node")
      .withEnv("DISABLE_SECURITY_PLUGIN", "true")
      .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
      .waitingFor(Wait.forHttp("/_cluster/health").forStatusCode(200));

   private QuickSearchRuntime runtime;
   private List<QuickSearchableTableConfig> tables;



   /*******************************************************************************
    ** Use real indexing and search over two memory-backed source tables.
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      QTableMetaData first = QContext.getQInstance().getTable(TEST_ENTITY_TABLE);
      first.withLabel("Products").withRecordLabelFields("name").withRecordLabelFormat("%s");
      first.withField(new QFieldMetaData("personal", QFieldType.STRING));
      QTableMetaData second = first.clone().withName("other").withLabel("Other Products");
      QContext.getQInstance().addTable(second);
      tables = List.of(table(TEST_ENTITY_TABLE), table("other"));
      QuickSearchQBitConfig config = QuickSearchQBitContext.getConfig()
         .withOpensearchHost(opensearch.getHost()).withOpensearchPort(opensearch.getMappedPort(9200))
         .withOpensearchIndexName("suggest-" + UUID.randomUUID()).withEnableRealTimeIndexing(false);
      runtime = spy(new QuickSearchRuntime(config));
      runtime.setDiscoveredTables(tables);
      doReturn(tables).when(runtime).getDiscoveredTables();
      config.setRuntime(runtime);
      QContext.getQInstance().addQBit(new QBitMetaData().withGroupId("com.kingsrook.qbits").withArtifactId("quick-search").withVersion("test").withConfig(config));
      runtime.getClient().ensureIndexExists();
      for(QuickSearchableTableConfig table : tables)
      {
         List<QRecord> records = IntStream.rangeClosed(1, 12).mapToObj(id -> new QRecord()
            .withValue("id", id).withValue("name", "Blue Widget " + id).withValue("description", "Public details")
            .withValue("personal", "PII-CANARY-7f3c")).toList();
         new InsertAction().execute(new InsertInput(table.getTableName()).withRecords(records));
         List<OpenSearchDocument> documents = records.stream().map(record -> IndexingUtils.buildDocument(record, table)).toList();
         runtime.getClient().indexDocuments(documents, 100);
      }
      runtime.getClient().refreshIndex();
   }



   /*******************************************************************************
    ** Explicit title and subtitle roles exercise the indexed presentation fields.
    *******************************************************************************/
   private QuickSearchableTableConfig table(String name)
   {
      return (new QuickSearchableTableConfig().withTableName(name).withPrimaryKeyField("id")
         .withSearchableFields(List.of("name", "description", "personal")).withEnabledByDefault(true)
         .withRecordLabelFields(List.of("name")).withRecordLabelFormat("%s")
         .withFieldRoles(Map.of("name", "TITLE", "description", "SUBTITLE", "personal", "DISPLAY")));
   }



   /*******************************************************************************
    ** Each test owns its alias and closes its transport.
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      if(runtime != null)
      {
         runtime.close();
      }
   }



   /*******************************************************************************
    ** Prefix of the final word matches through the unchanged OpenSearch query.
    *******************************************************************************/
   @Test
   void prefixGroupsAndCaps() throws Exception
   {
      QuickSearchSuggest result = QuickSearchSuggestService.suggest("  Blue   Wid ", 2, 1, null);
      assertThat(result.query()).isEqualTo("Blue Wid");
      assertThat(result.groups()).hasSize(1);
      assertThat(result.truncated()).isTrue();
      QuickSearchSuggest.Group group = result.groups().get(0);
      assertThat(group.items()).hasSize(2);
      assertThat(group.hasMore()).isTrue();
      assertThat(group.items()).allSatisfy(item ->
      {
         assertThat(item.label()).startsWith("Blue Widget ");
         assertThat(item.subtitle()).isEqualTo("Public details");
      });
      assertThat(new ObjectMapper().writeValueAsString(result)).doesNotContain("highlight", "displayValues", "score");
      QuickSearchSuggest filtered = QuickSearchSuggestService.suggest("Blue Wid", 10, 20, TEST_ENTITY_TABLE);
      assertThat(filtered.groups()).extracting(QuickSearchSuggest.Group::tableName).containsExactly(TEST_ENTITY_TABLE);
      assertThat(filtered.groups().get(0).items()).hasSize(10);
      assertThat(filtered.groups().get(0).hasMore()).isTrue();
      assertThat(filtered.truncated()).isFalse();
   }



   /*******************************************************************************
    ** Search cannot expose unreadable tables or rows excluded by actual QQQ locks.
    *******************************************************************************/
   @Test
   void tableAndRecordSecurity() throws Exception
   {
      QContext.getQInstance().getTable("other").withPermissionRules(new QPermissionRules().withLevel(PermissionLevel.READ_WRITE_PERMISSIONS));
      QContext.getQInstance().addSecurityKeyType(new QSecurityKeyType().withName("entity"));
      QContext.getQInstance().getTable(TEST_ENTITY_TABLE).withRecordSecurityLock(new RecordSecurityLock().withSecurityKeyType("entity").withFieldName("id"));
      QContext.getQSession().withSecurityKeyValue("entity", 2);
      QuickSearchSuggest result = QuickSearchSuggestService.suggest("Blue Wid", 10, null, null);
      assertThat(result.groups()).extracting(QuickSearchSuggest.Group::tableName).containsExactly(TEST_ENTITY_TABLE);
      assertThat(result.groups().get(0).items()).extracting(QuickSearchSuggest.Item::recordId).containsExactly("2");
      assertThat(result.groups().get(0).hasMore()).isFalse();
      assertThat(result.truncated()).isFalse();
   }



   /*******************************************************************************
    ** Classification changes protect old documents before a rebuild is performed.
    *******************************************************************************/
   @Test
   void classifiedFieldsNeverMatchOrReturn() throws Exception
   {
      OpenSearchDocument stale = new OpenSearchDocument().withSourceTable(TEST_ENTITY_TABLE).withRecordId("1")
         .withRecordLabel("PHI-CANARY-7f3c").withSubtitle("PHI-CANARY-7f3c").withSearchableText("PHI-CANARY-7f3c PII-CANARY-7f3c")
         .withFieldValues(Map.of("name", "Public widget", "description", "PHI-CANARY-7f3c", "personal", "PII-CANARY-7f3c"))
         .withDisplayValues(Map.of("Personal", "PII-CANARY-7f3c"));
      runtime.getClient().indexDocuments(List.of(stale), 100);
      runtime.getClient().refreshIndex();
      QTableMetaData metadata = QContext.getQInstance().getTable(TEST_ENTITY_TABLE);
      metadata.getField("description").withSupplementalMetaData(new AiFieldMetaData().withClassification(DataClassification.PHI));
      metadata.getField("personal").withSupplementalMetaData(new AiFieldMetaData().withClassification(DataClassification.PII));
      for(String term : List.of("PHI-CANARY", "PII-CANARY"))
      {
         assertThat(QuickSearchSuggestService.suggest(term, null, null, TEST_ENTITY_TABLE).groups()).isEmpty();
      }
      QuickSearchSuggest result = QuickSearchSuggestService.suggest("Public wid", null, null, TEST_ENTITY_TABLE);
      assertThat(result.groups()).hasSize(1);
      assertThat(result.groups().get(0).items()).hasSize(1);
      assertThat(result.groups().get(0).items().get(0).label()).isEqualTo("Public widget");
      assertThat(result.groups().get(0).items().get(0).subtitle()).isNull();
      assertThat(new ObjectMapper().writeValueAsString(result)).doesNotContain("PHI-CANARY", "PII-CANARY");
      metadata.getField("id").withSupplementalMetaData(new AiFieldMetaData().withClassification(DataClassification.PII));
      assertThat(QuickSearchSuggestService.suggest("Public wid", null, null, TEST_ENTITY_TABLE).groups()).isEmpty();
   }
}

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


import java.io.Serializable;
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryRecordStore;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchAction;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchInput;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchResult;
import com.kingsrook.qbits.quicksearch.annotations.QuickSearchField;
import com.kingsrook.qbits.quicksearch.annotations.QuickSearchable;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import com.kingsrook.qbits.quicksearch.processes.FullReindexStep;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Integration test, against a real OpenSearch, that a record deleted while a
 ** full reindex rebuilds the index is gone from the new physical index after
 ** the alias swap, even though the delete itself only reached the old one.
 *******************************************************************************/
@ExtendWith(RequiresDockerCondition.class)
@Testcontainers
class FullReindexDeleteReplayIntegrationTest
{
   private static final String BACKEND_NAME = "reindexDeleteMemoryBackend";
   private static final String TABLE_NAME   = "reindexCustomer";
   private static final String INDEX_NAME   = "quick_search_reindex_delete_test";

   @Container
   static GenericContainer<?> opensearch = new GenericContainer<>(System.getProperty("opensearch.test.image", "opensearchproject/opensearch:2.19.6"))
      .withExposedPorts(9200)
      .withEnv("discovery.type", "single-node")
      .withEnv("DISABLE_SECURITY_PLUGIN", "true")
      .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
      .waitingFor(Wait.forHttp("/_cluster/health").forStatusCode(200));



   /*******************************************************************************
    ** Entity annotated for quick search indexing.
    *******************************************************************************/
   @QuickSearchable(tableName = TABLE_NAME)
   static class ReindexCustomer
   {
      @QuickSearchField(weight = 2)
      private String name;

      @QuickSearchField
      private String city;
   }



   /*******************************************************************************
    ** Build the instance, produce the QBit with real-time indexing on and the
    ** record-security re-read off (search returns whatever the index holds),
    ** and initialize QContext.
    *******************************************************************************/
   @BeforeAll
   static void setUpAll() throws Exception
   {
      MemoryRecordStore.getInstance().reset();

      QInstance qInstance = new QInstance();
      qInstance.addBackend(new QBackendMetaData()
         .withName(BACKEND_NAME)
         .withBackendType(MemoryBackendModule.class));
      qInstance.withInstanceDefaultAuthentication(new QAuthenticationMetaData()
         .withName("anonymous")
         .withType(QAuthenticationType.FULLY_ANONYMOUS));
      qInstance.addTable(new QTableMetaData()
         .withName(TABLE_NAME)
         .withBackendName(BACKEND_NAME)
         .withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("name", QFieldType.STRING))
         .withField(new QFieldMetaData("city", QFieldType.STRING)));

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName(BACKEND_NAME)
         .withOpensearchHost(opensearch.getHost())
         .withOpensearchPort(opensearch.getMappedPort(9200))
         .withOpensearchIndexName(INDEX_NAME)
         .withSearchableEntityClasses(List.of(ReindexCustomer.class))
         .withEnableRealTimeIndexing(true)
         .withApplyRecordSecurityLocks(false);

      new QuickSearchQBitProducer().withConfig(config).produce(qInstance);

      QContext.init(qInstance, new QSession());
   }



   /*******************************************************************************
    ** Clean up QContext and QBit state after all tests complete.
    *******************************************************************************/
   @AfterAll
   static void tearDownAll()
   {
      QContext.clear();
      QuickSearchQBitContext.clear();
      MemoryRecordStore.getInstance().reset();
   }



   /*******************************************************************************
    ** Test: a record deleted after the rebuild read it (its document is already
    ** in the new physical index) is absent from the index and from search once
    ** the rebuild has swapped the alias.
    *******************************************************************************/
   @Test
   void testDeleteDuringFullReindex_isAbsentAfterTheSwap() throws Exception
   {
      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(TABLE_NAME);
      insertInput.setRecords(List.of(
         new QRecord().withValue("id", 1).withValue("name", "Gwendolyn").withValue("city", "Rebuildton"),
         new QRecord().withValue("id", 2).withValue("name", "Stanford").withValue("city", "Rebuildton")));
      new InsertAction().execute(insertInput);
      assertThat(searchRecordIds("rebuildton")).containsExactlyInAnyOrder("1", "2");

      RunBackendStepOutput output = new RunBackendStepOutput();
      new FullReindexStep()
      {
         ///////////////////////////////////////////////////////////////////////
         // delete a record once its document is in the new physical index,  //
         // while the alias still points at the old one                       //
         ///////////////////////////////////////////////////////////////////////
         @Override
         protected IndexCounts indexAllRecords(QuickSearchOpenSearchClient client, QuickSearchableTableConfig tableConfig, List<QFilterCriteria> extraCriteria, String targetIndex) throws QException
         {
            IndexCounts counts = super.indexAllRecords(client, tableConfig, extraCriteria, targetIndex);

            DeleteInput deleteInput = new DeleteInput();
            deleteInput.setTableName(TABLE_NAME);
            deleteInput.setPrimaryKeys(List.<Serializable>of(1));
            new DeleteAction().execute(deleteInput);

            return (counts);
         }
      }.run(new RunBackendStepInput(), output);

      assertThat(output.getValueBoolean("aliasSwapped")).isTrue();
      assertThat(documentRecordIds("rebuildton"))
         .as("the deleted record's document must not survive the rebuild")
         .containsExactly("2");
      assertThat(searchRecordIds("rebuildton")).containsExactly("2");

      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(QuickSearchFailedEvent.TABLE_NAME);
      assertThat(new QueryAction().execute(queryInput).getRecords())
         .as("the captured delete was applied and its row removed")
         .isEmpty();
   }



   /*******************************************************************************
    ** Refresh the index, then return the record IDs search returns for the term.
    *******************************************************************************/
   private List<String> searchRecordIds(String term) throws QException
   {
      client().refreshIndex();
      return (new QuickSearchAction().execute(new QuickSearchInput().withSearchTerm(term).withTableName(TABLE_NAME))
         .getResults().stream()
         .map(QuickSearchResult::getRecordId)
         .toList());
   }



   /*******************************************************************************
    ** Refresh the index, then return the record IDs of the documents matching
    ** the term, straight from OpenSearch.
    *******************************************************************************/
   private List<String> documentRecordIds(String term) throws QException
   {
      client().refreshIndex();
      return (client().search(term, List.of(TABLE_NAME), 10, 0, QuickSearchQBitContext.getDiscoveredTables())
         .hits().hits().stream()
         .map(hit -> hit.source().getRecordId())
         .toList());
   }



   /*******************************************************************************
    ** The live OpenSearch client from the QBit context.
    *******************************************************************************/
   private QuickSearchOpenSearchClient client()
   {
      return (QuickSearchQBitContext.getClient());
   }

}

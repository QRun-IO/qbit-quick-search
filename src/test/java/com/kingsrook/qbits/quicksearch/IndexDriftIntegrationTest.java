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
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
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
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
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
 ** Integration tests, against a real OpenSearch, that real-time indexing keeps
 ** the index in step with the source table through updates and deletes.
 **
 ** Each test uses its own record IDs and search terms, so tests are independent.
 *******************************************************************************/
@ExtendWith(RequiresDockerCondition.class)
@Testcontainers
class IndexDriftIntegrationTest
{
   private static final String BACKEND_NAME = "driftMemoryBackend";
   private static final String TABLE_NAME   = "driftCustomer";
   private static final String INDEX_NAME   = "quick_search_drift_test";

   @Container
   static GenericContainer<?> opensearch = new GenericContainer<>("opensearchproject/opensearch:2.11.0")
      .withExposedPorts(9200)
      .withEnv("discovery.type", "single-node")
      .withEnv("plugins.security.disabled", "true")
      .withEnv("OPENSEARCH_INITIAL_ADMIN_PASSWORD", "Admin123!")
      .waitingFor(Wait.forHttp("/_cluster/health").forStatusCode(200));



   /*******************************************************************************
    ** Entity annotated for quick search indexing.
    *******************************************************************************/
   @QuickSearchable(tableName = TABLE_NAME)
   static class DriftCustomer
   {
      @QuickSearchField(weight = 2)
      private String name;

      @QuickSearchField
      private String city;
   }



   /*******************************************************************************
    ** Build the instance, produce the QBit with real-time indexing on, and
    ** initialize QContext.
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
         .withSearchableEntityClasses(List.of(DriftCustomer.class))
         .withEnableRealTimeIndexing(true);

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
    ** Test: a partial update (one field) keeps the other fields searchable.
    *******************************************************************************/
   @Test
   void testPartialUpdate_unchangedFieldsStaySearchable() throws Exception
   {
      insert(new QRecord().withValue("id", 1).withValue("name", "Ada").withValue("city", "Lovelaceville"));

      UpdateInput updateInput = new UpdateInput();
      updateInput.setTableName(TABLE_NAME);
      updateInput.setRecords(List.of(new QRecord().withValue("id", 1).withValue("name", "Adeline")));
      new UpdateAction().execute(updateInput);

      assertThat(searchRecordIds("lovelaceville"))
         .as("the unchanged city must stay searchable after updating only the name")
         .containsExactly("1");
      assertThat(searchRecordIds("adeline")).containsExactly("1");
   }



   /*******************************************************************************
    ** Test: deleting records removes their documents, even when the batch also
    ** holds a record whose document is already missing from the index.
    *******************************************************************************/
   @Test
   void testDelete_removesDocuments_evenWithMissingDocumentInBatch() throws Exception
   {
      insert(
         new QRecord().withValue("id", 10).withValue("name", "Tenley").withValue("city", "Deleteton"),
         new QRecord().withValue("id", 11).withValue("name", "Elvira").withValue("city", "Deleteton"),
         new QRecord().withValue("id", 12).withValue("name", "Twila").withValue("city", "Deleteton"));

      ////////////////////////////////////////////////////////////////////
      // simulate earlier drift: record 12's document is already gone   //
      ////////////////////////////////////////////////////////////////////
      client().deleteDocument(TABLE_NAME, "12");

      DeleteInput deleteInput = new DeleteInput();
      deleteInput.setTableName(TABLE_NAME);
      deleteInput.setPrimaryKeys(List.<Serializable>of(12, 10));
      new DeleteAction().execute(deleteInput);

      assertThat(searchRecordIds("deleteton"))
         .as("only the record that was not deleted should remain")
         .containsExactly("11");
   }



   /*******************************************************************************
    ** Insert records through InsertAction (so real-time indexing runs).
    *******************************************************************************/
   private void insert(QRecord... records) throws QException
   {
      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(TABLE_NAME);
      insertInput.setRecords(List.of(records));
      new InsertAction().execute(insertInput);
   }



   /*******************************************************************************
    ** Refresh the index, then return the record IDs matching the term.
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
    ** The live OpenSearch client from the QBit context.
    *******************************************************************************/
   private QuickSearchOpenSearchClient client()
   {
      return (QuickSearchQBitContext.getClient());
   }

}

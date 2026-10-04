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


import java.util.List;
import com.kingsrook.qqq.backend.core.context.QContext;
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
import com.kingsrook.qbits.quicksearch.metadata.QuickSearchTableMetaDataHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import static org.mockito.Mockito.mock;


/*******************************************************************************
 ** Shared test base class for Quick Search QBit tests that need a full
 ** in-memory QInstance with QContext and QuickSearchQBitContext initialized.
 **
 ** Sets up:
 ** - A memory-backed QInstance with quickSearchIndex, quickSearchIndexRun,
 **   and a "testEntity" source table.
 ** - QContext initialized with the QInstance and a new anonymous session.
 ** - QuickSearchQBitContext with a minimal test config and a mock OpenSearch
 **   client.
 **
 ** Subclasses with @Test methods can extend this class to reuse all setup.
 *******************************************************************************/
public class BaseQuickSearchTest
{
   protected static final String TEST_BACKEND_NAME = "testMemoryBackend";
   protected static final String TEST_ENTITY_TABLE = "testEntity";



   /*******************************************************************************
    ** Build and return a fresh QInstance configured for in-memory testing.
    **
    ** Registers:
    ** - A memory backend named TEST_BACKEND_NAME
    ** - FULLY_ANONYMOUS authentication
    ** - quickSearchIndex table (all fields from QuickSearchIndex entity)
    ** - quickSearchIndexRun table (all fields from QuickSearchIndexRun entity)
    ** - testEntity table (id, name, description, modifyDate)
    *******************************************************************************/
   protected QInstance buildTestQInstance()
   {
      QInstance qInstance = new QInstance();

      ////////////////////////////////////////////////////
      // Register the in-memory backend                 //
      ////////////////////////////////////////////////////
      qInstance.addBackend(new QBackendMetaData()
         .withName(TEST_BACKEND_NAME)
         .withBackendType(MemoryBackendModule.class));

      ////////////////////////////////////////////////////
      // Set up fully-anonymous authentication          //
      ////////////////////////////////////////////////////
      qInstance.withInstanceDefaultAuthentication(new QAuthenticationMetaData()
         .withName("anonymous")
         .withType(QAuthenticationType.FULLY_ANONYMOUS));

      ////////////////////////////////////////////////////
      // operational tables, as the QBit produces them  //
      ////////////////////////////////////////////////////
      QuickSearchQBitConfig tableConfig = new QuickSearchQBitConfig().withBackendName(TEST_BACKEND_NAME);
      qInstance.addTable(QuickSearchTableMetaDataHelper.buildIndexTable(tableConfig));
      qInstance.addTable(QuickSearchTableMetaDataHelper.buildIndexRunTable(tableConfig));
      qInstance.addTable(QuickSearchTableMetaDataHelper.buildFailedEventTable(tableConfig));
      qInstance.addPossibleValueSource(QuickSearchTableMetaDataHelper.buildIndexPossibleValueSource(tableConfig));

      ////////////////////////////////////////////////////
      // testEntity source table                        //
      ////////////////////////////////////////////////////
      qInstance.addTable(buildTestEntityTable());

      return (qInstance);
   }



   /*******************************************************************************
    ** Build QTableMetaData for quickSearchIndex.
    *******************************************************************************/
   /*******************************************************************************
    ** Build QTableMetaData for quickSearchIndexRun.
    *******************************************************************************/
   /*******************************************************************************
    ** Build QTableMetaData for the test source entity table.
    *******************************************************************************/
   private QTableMetaData buildTestEntityTable()
   {
      return new QTableMetaData()
         .withName(TEST_ENTITY_TABLE)
         .withBackendName(TEST_BACKEND_NAME)
         .withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("name", QFieldType.STRING))
         .withField(new QFieldMetaData("description", QFieldType.STRING))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME));
   }



   /*******************************************************************************
    ** Set up test environment before each test.
    **
    ** Resets MemoryRecordStore, initializes QContext, and sets up
    ** QuickSearchQBitContext with a minimal config and mock OpenSearch client.
    *******************************************************************************/
   @BeforeEach
   void baseSetUp()
   {
      ////////////////////////////////////////////////////
      // Reset in-memory record store                   //
      ////////////////////////////////////////////////////
      MemoryRecordStore.getInstance().reset();

      ////////////////////////////////////////////////////
      // Initialize QContext with test QInstance        //
      ////////////////////////////////////////////////////
      QInstance qInstance = buildTestQInstance();
      QContext.init(qInstance, new QSession());

      ////////////////////////////////////////////////////
      // Set up QuickSearchQBitContext with test config  //
      ////////////////////////////////////////////////////
      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName(TEST_BACKEND_NAME)
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-index")
         .withSearchableEntityClasses(List.of());

      QuickSearchQBitContext.setConfig(config);

      ////////////////////////////////////////////////////
      // Set up a mock OpenSearch client               //
      ////////////////////////////////////////////////////
      QuickSearchQBitContext.setClient(mock(com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient.class));

      ////////////////////////////////////////////////////
      // Set up a test discoveredTables list            //
      ////////////////////////////////////////////////////
      QuickSearchQBitContext.setDiscoveredTables(List.of(
         new QuickSearchableTableConfig()
            .withTableName(TEST_ENTITY_TABLE)
            .withPrimaryKeyField("id")
            .withSearchableFields(List.of("name", "description"))
            .withBasepullTimestampField("modifyDate")
            .withBasepullIntervalMinutes(5)
            .withEnabledByDefault(true)
      ));
   }



   /*******************************************************************************
    ** Clean up test environment after each test.
    **
    ** Clears QContext and QuickSearchQBitContext to prevent state leakage.
    *******************************************************************************/
   @AfterEach
   void baseTearDown()
   {
      QContext.clear();
      QuickSearchQBitContext.clear();
   }

}

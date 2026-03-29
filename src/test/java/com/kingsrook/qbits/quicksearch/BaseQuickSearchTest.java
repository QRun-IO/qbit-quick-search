/*
 * QBit Quick Search
 * Copyright (C) 2024-2025 QRun-IO, LLC
 * https://www.qrun.io | https://github.com/QRun-IO
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
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
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;
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
      qInstance.setAuthentication(new QAuthenticationMetaData()
         .withName("anonymous")
         .withType(QAuthenticationType.FULLY_ANONYMOUS));

      ////////////////////////////////////////////////////
      // quickSearchIndex table                         //
      ////////////////////////////////////////////////////
      qInstance.addTable(buildQuickSearchIndexTable());

      ////////////////////////////////////////////////////
      // quickSearchIndexRun table                      //
      ////////////////////////////////////////////////////
      qInstance.addTable(buildQuickSearchIndexRunTable());

      ////////////////////////////////////////////////////
      // testEntity source table                        //
      ////////////////////////////////////////////////////
      qInstance.addTable(buildTestEntityTable());

      return (qInstance);
   }



   /*******************************************************************************
    ** Build QTableMetaData for quickSearchIndex.
    *******************************************************************************/
   private QTableMetaData buildQuickSearchIndexTable()
   {
      return new QTableMetaData()
         .withName(QuickSearchIndex.TABLE_NAME)
         .withBackendName(TEST_BACKEND_NAME)
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



   /*******************************************************************************
    ** Build QTableMetaData for quickSearchIndexRun.
    *******************************************************************************/
   private QTableMetaData buildQuickSearchIndexRunTable()
   {
      return new QTableMetaData()
         .withName(QuickSearchIndexRun.TABLE_NAME)
         .withBackendName(TEST_BACKEND_NAME)
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

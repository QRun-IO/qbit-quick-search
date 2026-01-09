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
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryRecordStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;


/*******************************************************************************
 ** Base test class for Quick Search QBit tests.
 *******************************************************************************/
public class BaseQuickSearchTest
{
   public static final String BACKEND_NAME        = "memory";
   public static final String TEST_TABLE_NAME     = "testEntity";
   public static final String TEST_NAMESPACE      = "testNamespace";

   protected QuickSearchQBitConfig config;
   protected QInstance             qInstance;



   /***************************************************************************
    ** Setup before each test.
    ***************************************************************************/
   @BeforeEach
   void setup() throws QException
   {
      MemoryRecordStore.getInstance().reset();

      qInstance = new QInstance();

      ////////////////////////////////////////////
      // Add memory backend for testing        //
      ////////////////////////////////////////////
      qInstance.addBackend(new QBackendMetaData()
         .withName(BACKEND_NAME)
         .withBackendType(MemoryBackendModule.class));

      ////////////////////////////////////////////
      // Add anonymous auth for testing        //
      ////////////////////////////////////////////
      qInstance.setAuthentication(new QAuthenticationMetaData()
         .withName("anonymous")
         .withType(QAuthenticationType.FULLY_ANONYMOUS));

      ////////////////////////////////////////////
      // Create test tables manually           //
      ////////////////////////////////////////////
      qInstance.addTable(createQuickSearchIndexTable());
      qInstance.addTable(createQuickSearchIndexRunTable());
      qInstance.addTable(createTestEntityTable());

      ////////////////////////////////////////////
      // Register QBit config                  //
      ////////////////////////////////////////////
      config = createTestConfig();
      QBitMetaData qBitMetaData = new QBitMetaData()
         .withGroupId(QuickSearchQBitProducer.GROUP_ID)
         .withArtifactId(QuickSearchQBitProducer.ARTIFACT_ID)
         .withVersion(QuickSearchQBitProducer.VERSION)
         .withNamespace(TEST_NAMESPACE)
         .withConfig(config);
      qInstance.addQBit(qBitMetaData);

      ////////////////////////////////////////////
      // Store config in context               //
      ////////////////////////////////////////////
      QuickSearchQBitContext.setConfig(config);
      QuickSearchQBitContext.setDiscoveredTables(List.of());

      QContext.init(qInstance, newSession());
   }



   /***************************************************************************
    ** Cleanup after each test.
    ***************************************************************************/
   @AfterEach
   void tearDown()
   {
      QContext.clear();
      MemoryRecordStore.getInstance().reset();
   }



   /***************************************************************************
    ** Create test configuration.
    ***************************************************************************/
   protected QuickSearchQBitConfig createTestConfig()
   {
      return new QuickSearchQBitConfig()
         .withBackendName(BACKEND_NAME)
         .withTableNamePrefix("")
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-quick-search")
         .withUseSsl(false);
   }



   /***************************************************************************
    ** Create a new session for testing.
    ***************************************************************************/
   protected QSession newSession()
   {
      return new QSession();
   }



   /***************************************************************************
    ** Create the quickSearchIndex table for testing.
    ***************************************************************************/
   protected QTableMetaData createQuickSearchIndexTable()
   {
      return new QTableMetaData()
         .withName(QuickSearchIndex.TABLE_NAME)
         .withLabel("Quick Search Index")
         .withBackendName(BACKEND_NAME)
         .withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("tableName", QFieldType.STRING).withIsRequired(true))
         .withField(new QFieldMetaData("isEnabled", QFieldType.BOOLEAN))
         .withField(new QFieldMetaData("basepullIntervalMinutes", QFieldType.INTEGER))
         .withField(new QFieldMetaData("basepullTimestampField", QFieldType.STRING))
         .withField(new QFieldMetaData("searchableFieldsJson", QFieldType.STRING))
         .withField(new QFieldMetaData("lastFullIndexTime", QFieldType.DATE_TIME))
         .withField(new QFieldMetaData("lastBasepullTime", QFieldType.DATE_TIME))
         .withField(new QFieldMetaData("indexedRecordCount", QFieldType.INTEGER))
         .withField(new QFieldMetaData("createDate", QFieldType.DATE_TIME))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME));
   }



   /***************************************************************************
    ** Create the quickSearchIndexRun table for testing.
    ***************************************************************************/
   protected QTableMetaData createQuickSearchIndexRunTable()
   {
      return new QTableMetaData()
         .withName(QuickSearchIndexRun.TABLE_NAME)
         .withLabel("Quick Search Index Run")
         .withBackendName(BACKEND_NAME)
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
         .withField(new QFieldMetaData("errorMessage", QFieldType.STRING));
   }



   /***************************************************************************
    ** Create a test entity table for testing.
    ***************************************************************************/
   protected QTableMetaData createTestEntityTable()
   {
      return new QTableMetaData()
         .withName(TEST_TABLE_NAME)
         .withLabel("Test Entity")
         .withBackendName(BACKEND_NAME)
         .withPrimaryKeyField("id")
         .withRecordLabelFormat("%s")
         .withRecordLabelFields(List.of("name"))
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("name", QFieldType.STRING))
         .withField(new QFieldMetaData("description", QFieldType.STRING))
         .withField(new QFieldMetaData("status", QFieldType.STRING))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME));
   }

}

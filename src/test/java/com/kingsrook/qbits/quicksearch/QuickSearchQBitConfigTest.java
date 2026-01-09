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


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Tests for QuickSearchQBitConfig.
 *******************************************************************************/
class QuickSearchQBitConfigTest
{

   /***************************************************************************
    ** Test validation with valid config.
    ***************************************************************************/
   @Test
   void testValidate_validConfig_noErrors()
   {
      QInstance qInstance = createQInstanceWithBackend();

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName("memory")
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-index");

      List<String> errors = new ArrayList<>();
      config.validate(qInstance, errors);

      assertThat(errors).isEmpty();
   }



   /***************************************************************************
    ** Test validation with missing backend name.
    ***************************************************************************/
   @Test
   void testValidate_missingBackendName_addsError()
   {
      QInstance qInstance = createQInstanceWithBackend();

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-index");

      List<String> errors = new ArrayList<>();
      config.validate(qInstance, errors);

      assertThat(errors).contains("backendName is required for QuickSearchQBit");
   }



   /***************************************************************************
    ** Test validation with non-existent backend.
    ***************************************************************************/
   @Test
   void testValidate_nonExistentBackend_addsError()
   {
      QInstance qInstance = createQInstanceWithBackend();

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName("nonexistent")
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-index");

      List<String> errors = new ArrayList<>();
      config.validate(qInstance, errors);

      assertThat(errors).contains("Backend not found: nonexistent");
   }



   /***************************************************************************
    ** Test validation with missing OpenSearch host.
    ***************************************************************************/
   @Test
   void testValidate_missingOpensearchHost_addsError()
   {
      QInstance qInstance = createQInstanceWithBackend();

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName("memory")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-index");

      List<String> errors = new ArrayList<>();
      config.validate(qInstance, errors);

      assertThat(errors).contains("opensearchHost is required for QuickSearchQBit");
   }



   /***************************************************************************
    ** Test validation with missing OpenSearch port.
    ***************************************************************************/
   @Test
   void testValidate_missingOpensearchPort_addsError()
   {
      QInstance qInstance = createQInstanceWithBackend();

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName("memory")
         .withOpensearchHost("localhost")
         .withOpensearchIndexName("test-index");

      List<String> errors = new ArrayList<>();
      config.validate(qInstance, errors);

      assertThat(errors).contains("opensearchPort is required for QuickSearchQBit");
   }



   /***************************************************************************
    ** Test validation with missing OpenSearch index name.
    ***************************************************************************/
   @Test
   void testValidate_missingOpensearchIndexName_addsError()
   {
      QInstance qInstance = createQInstanceWithBackend();

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName("memory")
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200);

      List<String> errors = new ArrayList<>();
      config.validate(qInstance, errors);

      assertThat(errors).contains("opensearchIndexName is required for QuickSearchQBit");
   }



   /***************************************************************************
    ** Test applyPrefix with prefix set.
    ***************************************************************************/
   @Test
   void testApplyPrefix_withPrefix_appliesPrefix()
   {
      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withTableNamePrefix("myApp_");

      String result = config.applyPrefix("tableName");

      assertThat(result).isEqualTo("myApp_tableName");
   }



   /***************************************************************************
    ** Test applyPrefix with null prefix.
    ***************************************************************************/
   @Test
   void testApplyPrefix_nullPrefix_returnsOriginal()
   {
      QuickSearchQBitConfig config = new QuickSearchQBitConfig();

      String result = config.applyPrefix("tableName");

      assertThat(result).isEqualTo("tableName");
   }



   /***************************************************************************
    ** Test applyPrefix with empty prefix.
    ***************************************************************************/
   @Test
   void testApplyPrefix_emptyPrefix_returnsOriginal()
   {
      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withTableNamePrefix("");

      String result = config.applyPrefix("tableName");

      assertThat(result).isEqualTo("tableName");
   }



   /***************************************************************************
    ** Test all fluent setters.
    ***************************************************************************/
   @Test
   void testFluentSetters()
   {
      List<Class<?>> entityClasses = List.of(String.class);

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName("testBackend")
         .withTableNamePrefix("prefix_")
         .withOpensearchHost("search.example.com")
         .withOpensearchPort(443)
         .withOpensearchIndexName("my-index")
         .withOpensearchUsername("user")
         .withOpensearchPassword("pass")
         .withUseSsl(true)
         .withAutoDiscoverAnnotations(true)
         .withEnableScheduledProcesses(false)
         .withDefaultBasepullIntervalMinutes(10)
         .withSearchableEntityClasses(entityClasses);

      assertThat(config.getBackendName()).isEqualTo("testBackend");
      assertThat(config.getTableNamePrefix()).isEqualTo("prefix_");
      assertThat(config.getOpensearchHost()).isEqualTo("search.example.com");
      assertThat(config.getOpensearchPort()).isEqualTo(443);
      assertThat(config.getOpensearchIndexName()).isEqualTo("my-index");
      assertThat(config.getOpensearchUsername()).isEqualTo("user");
      assertThat(config.getOpensearchPassword()).isEqualTo("pass");
      assertThat(config.getUseSsl()).isTrue();
      assertThat(config.getAutoDiscoverAnnotations()).isTrue();
      assertThat(config.getEnableScheduledProcesses()).isFalse();
      assertThat(config.getDefaultBasepullIntervalMinutes()).isEqualTo(10);
      assertThat(config.getSearchableEntityClasses()).isEqualTo(entityClasses);
   }



   /***************************************************************************
    ** Helper to create a QInstance with a memory backend.
    ***************************************************************************/
   private QInstance createQInstanceWithBackend()
   {
      QInstance qInstance = new QInstance();
      qInstance.addBackend(new QBackendMetaData()
         .withName("memory")
         .withBackendType(MemoryBackendModule.class));
      return qInstance;
   }

}

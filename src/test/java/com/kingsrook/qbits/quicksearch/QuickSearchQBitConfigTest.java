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


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;
import com.kingsrook.qbits.quicksearch.publisher.IndexEventPublisher;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;


/*******************************************************************************
 ** Tests for QuickSearchQBitConfig.
 *******************************************************************************/
class QuickSearchQBitConfigTest
{

   /***************************************************************************
    ** Test validation with a fully valid config.
    ***************************************************************************/
   @Test
   void testValidate_validConfig_noErrors()
   {
      QInstance qInstance = createQInstanceWithBackend();

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName("memory")
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-index")
         .withSearchableEntityClasses(List.of(String.class));

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
         .withOpensearchIndexName("test-index")
         .withSearchableEntityClasses(List.of(String.class));

      List<String> errors = new ArrayList<>();
      config.validate(qInstance, errors);

      assertThat(errors).anyMatch(e -> e.contains("backendName is required"));
   }



   /***************************************************************************
    ** Test validation with a backend name not present in QInstance.
    ***************************************************************************/
   @Test
   void testValidate_backendNotInQInstance_addsError()
   {
      QInstance qInstance = createQInstanceWithBackend();

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName("nonexistent")
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-index")
         .withSearchableEntityClasses(List.of(String.class));

      List<String> errors = new ArrayList<>();
      config.validate(qInstance, errors);

      assertThat(errors).anyMatch(e -> e.contains("nonexistent"));
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
         .withOpensearchIndexName("test-index")
         .withSearchableEntityClasses(List.of(String.class));

      List<String> errors = new ArrayList<>();
      config.validate(qInstance, errors);

      assertThat(errors).anyMatch(e -> e.contains("opensearchHost is required"));
   }



   /***************************************************************************
    ** Test validation with null OpenSearch port.
    ***************************************************************************/
   @Test
   void testValidate_nullOpensearchPort_addsError()
   {
      QInstance qInstance = createQInstanceWithBackend();

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName("memory")
         .withOpensearchHost("localhost")
         .withOpensearchIndexName("test-index")
         .withSearchableEntityClasses(List.of(String.class));

      List<String> errors = new ArrayList<>();
      config.validate(qInstance, errors);

      assertThat(errors).anyMatch(e -> e.contains("opensearchPort is required"));
   }



   /***************************************************************************
    ** Test validation with zero port.
    ***************************************************************************/
   @Test
   void testValidate_zeroPort_addsError()
   {
      QInstance qInstance = createQInstanceWithBackend();

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName("memory")
         .withOpensearchHost("localhost")
         .withOpensearchPort(0)
         .withOpensearchIndexName("test-index")
         .withSearchableEntityClasses(List.of(String.class));

      List<String> errors = new ArrayList<>();
      config.validate(qInstance, errors);

      assertThat(errors).anyMatch(e -> e.contains("opensearchPort must be positive"));
   }



   /***************************************************************************
    ** Test validation with negative port.
    ***************************************************************************/
   @Test
   void testValidate_negativePort_addsError()
   {
      QInstance qInstance = createQInstanceWithBackend();

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName("memory")
         .withOpensearchHost("localhost")
         .withOpensearchPort(-1)
         .withOpensearchIndexName("test-index")
         .withSearchableEntityClasses(List.of(String.class));

      List<String> errors = new ArrayList<>();
      config.validate(qInstance, errors);

      assertThat(errors).anyMatch(e -> e.contains("opensearchPort must be positive"));
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
         .withOpensearchPort(9200)
         .withSearchableEntityClasses(List.of(String.class));

      List<String> errors = new ArrayList<>();
      config.validate(qInstance, errors);

      assertThat(errors).anyMatch(e -> e.contains("opensearchIndexName is required"));
   }



   /***************************************************************************
    ** Test validation when username is set but password is missing.
    ***************************************************************************/
   @Test
   void testValidate_usernameWithoutPassword_addsError()
   {
      QInstance qInstance = createQInstanceWithBackend();

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName("memory")
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-index")
         .withOpensearchUsername("user")
         .withSearchableEntityClasses(List.of(String.class));

      List<String> errors = new ArrayList<>();
      config.validate(qInstance, errors);

      assertThat(errors).anyMatch(e -> e.contains("opensearchPassword is required"));
   }



   /***************************************************************************
    ** Test validation when password is set but username is missing.
    ***************************************************************************/
   @Test
   void testValidate_passwordWithoutUsername_addsError()
   {
      QInstance qInstance = createQInstanceWithBackend();

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName("memory")
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-index")
         .withOpensearchPassword("secret")
         .withSearchableEntityClasses(List.of(String.class));

      List<String> errors = new ArrayList<>();
      config.validate(qInstance, errors);

      assertThat(errors).anyMatch(e -> e.contains("opensearchUsername is required"));
   }



   /***************************************************************************
    ** Test validation with both username and password set passes auth check.
    ***************************************************************************/
   @Test
   void testValidate_bothUsernameAndPassword_passesAuthCheck()
   {
      QInstance qInstance = createQInstanceWithBackend();

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName("memory")
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-index")
         .withOpensearchUsername("user")
         .withOpensearchPassword("secret")
         .withSearchableEntityClasses(List.of(String.class));

      List<String> errors = new ArrayList<>();
      config.validate(qInstance, errors);

      assertThat(errors).noneMatch(e -> e.contains("opensearchUsername") || e.contains("opensearchPassword"));
   }



   /***************************************************************************
    ** Test validation with neither username nor password passes auth check.
    ***************************************************************************/
   @Test
   void testValidate_neitherUsernameNorPassword_passesAuthCheck()
   {
      QInstance qInstance = createQInstanceWithBackend();

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName("memory")
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-index")
         .withSearchableEntityClasses(List.of(String.class));

      List<String> errors = new ArrayList<>();
      config.validate(qInstance, errors);

      assertThat(errors).noneMatch(e -> e.contains("opensearchUsername") || e.contains("opensearchPassword"));
   }



   /***************************************************************************
    ** Test validation with empty searchableEntityClasses list.
    ***************************************************************************/
   @Test
   void testValidate_emptySearchableEntityClasses_addsError()
   {
      QInstance qInstance = createQInstanceWithBackend();

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName("memory")
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-index")
         .withSearchableEntityClasses(List.of());

      List<String> errors = new ArrayList<>();
      config.validate(qInstance, errors);

      assertThat(errors).anyMatch(e -> e.contains("searchableEntityClasses is required"));
   }



   /***************************************************************************
    ** Test validation with null searchableEntityClasses.
    ***************************************************************************/
   @Test
   void testValidate_nullSearchableEntityClasses_addsError()
   {
      QInstance qInstance = createQInstanceWithBackend();

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName("memory")
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-index");

      List<String> errors = new ArrayList<>();
      config.validate(qInstance, errors);

      assertThat(errors).anyMatch(e -> e.contains("searchableEntityClasses is required"));
   }



   /***************************************************************************
    ** Test validation with zero bulkBatchSize.
    ***************************************************************************/
   @Test
   void testValidate_zeroBulkBatchSize_addsError()
   {
      QInstance qInstance = createQInstanceWithBackend();

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName("memory")
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-index")
         .withSearchableEntityClasses(List.of(String.class))
         .withBulkBatchSize(0);

      List<String> errors = new ArrayList<>();
      config.validate(qInstance, errors);

      assertThat(errors).anyMatch(e -> e.contains("bulkBatchSize must be positive"));
   }



   /***************************************************************************
    ** Test validation with zero sourceBatchSize.
    ***************************************************************************/
   @Test
   void testValidate_zeroSourceBatchSize_addsError()
   {
      QInstance qInstance = createQInstanceWithBackend();

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName("memory")
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-index")
         .withSearchableEntityClasses(List.of(String.class))
         .withSourceBatchSize(0);

      List<String> errors = new ArrayList<>();
      config.validate(qInstance, errors);

      assertThat(errors).anyMatch(e -> e.contains("sourceBatchSize must be positive"));
   }



   /***************************************************************************
    ** Test validation with zero defaultBasepullIntervalMinutes.
    ***************************************************************************/
   @Test
   void testValidate_zeroDefaultBasepullInterval_addsError()
   {
      QInstance qInstance = createQInstanceWithBackend();

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName("memory")
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("test-index")
         .withSearchableEntityClasses(List.of(String.class))
         .withDefaultBasepullIntervalMinutes(0);

      List<String> errors = new ArrayList<>();
      config.validate(qInstance, errors);

      assertThat(errors).anyMatch(e -> e.contains("defaultBasepullIntervalMinutes must be positive"));
   }



   /***************************************************************************
    ** Test that multiple errors are collected in a single validate call.
    ***************************************************************************/
   @Test
   void testValidate_multipleErrors_allCollected()
   {
      QInstance qInstance = createQInstanceWithBackend();

      QuickSearchQBitConfig config = new QuickSearchQBitConfig();

      List<String> errors = new ArrayList<>();
      config.validate(qInstance, errors);

      assertThat(errors.size()).isGreaterThan(1);
      assertThat(errors).anyMatch(e -> e.contains("backendName"));
      assertThat(errors).anyMatch(e -> e.contains("opensearchHost"));
      assertThat(errors).anyMatch(e -> e.contains("opensearchPort"));
      assertThat(errors).anyMatch(e -> e.contains("opensearchIndexName"));
   }



   /***************************************************************************
    ** Test applyPrefix with prefix set.
    ***************************************************************************/
   @Test
   void testApplyPrefix_withPrefix_appliesPrefix()
   {
      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withTableNamePrefix("myApp_");

      assertThat(config.applyPrefix("tableName")).isEqualTo("myApp_tableName");
   }



   /***************************************************************************
    ** Test applyPrefix with null prefix returns original name.
    ***************************************************************************/
   @Test
   void testApplyPrefix_nullPrefix_returnsOriginal()
   {
      QuickSearchQBitConfig config = new QuickSearchQBitConfig();

      assertThat(config.applyPrefix("tableName")).isEqualTo("tableName");
   }



   /***************************************************************************
    ** Test applyPrefix with empty prefix returns original name.
    ***************************************************************************/
   @Test
   void testApplyPrefix_emptyPrefix_returnsOriginal()
   {
      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withTableNamePrefix("");

      assertThat(config.applyPrefix("tableName")).isEqualTo("tableName");
   }



   /***************************************************************************
    ** Test that all fluent setters return this.
    ***************************************************************************/
   @Test
   void testFluentSetters_returnThis()
   {
      List<Class<?>> entityClasses = List.of(String.class);
      IndexEventPublisher dummyPublisher = mock(IndexEventPublisher.class);

      QuickSearchQBitConfig config = new QuickSearchQBitConfig();

      assertThat(config.withBackendName("b")).isSameAs(config);
      assertThat(config.withTableNamePrefix("p_")).isSameAs(config);
      assertThat(config.withOpensearchHost("h")).isSameAs(config);
      assertThat(config.withOpensearchPort(9200)).isSameAs(config);
      assertThat(config.withOpensearchIndexName("i")).isSameAs(config);
      assertThat(config.withOpensearchUsername("u")).isSameAs(config);
      assertThat(config.withOpensearchPassword("pw")).isSameAs(config);
      assertThat(config.withUseSsl(true)).isSameAs(config);
      assertThat(config.withEnableScheduledProcesses(false)).isSameAs(config);
      assertThat(config.withEnableRealTimeIndexing(false)).isSameAs(config);
      assertThat(config.withDefaultBasepullIntervalMinutes(10)).isSameAs(config);
      assertThat(config.withBulkBatchSize(100)).isSameAs(config);
      assertThat(config.withSourceBatchSize(200)).isSameAs(config);
      assertThat(config.withSearchableEntityClasses(entityClasses)).isSameAs(config);
      assertThat(config.withIndexEventPublisher(dummyPublisher)).isSameAs(config);
   }



   /***************************************************************************
    ** Test all fluent setters and corresponding getters.
    ***************************************************************************/
   @Test
   void testFluentSetters_gettersReturnCorrectValues()
   {
      List<Class<?>> entityClasses = List.of(String.class, Integer.class);
      IndexEventPublisher dummyPublisher = mock(IndexEventPublisher.class);

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName("testBackend")
         .withTableNamePrefix("prefix_")
         .withOpensearchHost("search.example.com")
         .withOpensearchPort(443)
         .withOpensearchIndexName("my-index")
         .withOpensearchUsername("user")
         .withOpensearchPassword("pass")
         .withUseSsl(true)
         .withEnableScheduledProcesses(false)
         .withEnableRealTimeIndexing(false)
         .withDefaultBasepullIntervalMinutes(10)
         .withBulkBatchSize(250)
         .withSourceBatchSize(500)
         .withSearchableEntityClasses(entityClasses)
         .withIndexEventPublisher(dummyPublisher);

      assertThat(config.getBackendName()).isEqualTo("testBackend");
      assertThat(config.getTableNamePrefix()).isEqualTo("prefix_");
      assertThat(config.getOpensearchHost()).isEqualTo("search.example.com");
      assertThat(config.getOpensearchPort()).isEqualTo(443);
      assertThat(config.getOpensearchIndexName()).isEqualTo("my-index");
      assertThat(config.getOpensearchUsername()).isEqualTo("user");
      assertThat(config.getOpensearchPassword()).isEqualTo("pass");
      assertThat(config.getUseSsl()).isTrue();
      assertThat(config.getEnableScheduledProcesses()).isFalse();
      assertThat(config.getEnableRealTimeIndexing()).isFalse();
      assertThat(config.getDefaultBasepullIntervalMinutes()).isEqualTo(10);
      assertThat(config.getBulkBatchSize()).isEqualTo(250);
      assertThat(config.getSourceBatchSize()).isEqualTo(500);
      assertThat(config.getSearchableEntityClasses()).isEqualTo(entityClasses);
      assertThat(config.getIndexEventPublisher()).isSameAs(dummyPublisher);
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

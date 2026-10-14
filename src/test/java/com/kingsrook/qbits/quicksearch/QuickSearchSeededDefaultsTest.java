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

package com.kingsrook.qbits.quicksearch;


import java.util.List;
import com.kingsrook.qbits.quicksearch.annotations.QuickSearchField;
import com.kingsrook.qbits.quicksearch.annotations.QuickSearchable;
import com.kingsrook.qbits.quicksearch.config.QuickSearchConfigPrimer;
import com.kingsrook.qbits.quicksearch.model.QuickSearchConfigTombstone;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexField;
import com.kingsrook.qbits.quicksearch.model.QuickSearchSettings;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** This qbit ships no DDL and no changelog: QuickSearchConfigPrimer seeds its
 ** config tables at runtime. This proves, on a fresh (memory) backend produced
 ** by the real producer, that priming populates quick_search_index,
 ** quick_search_index_field and the quick_search_settings row (id 1), leaves
 ** quick_search_config_tombstone empty, is idempotent, and never overwrites an
 ** admin's edit.
 *******************************************************************************/
class QuickSearchSeededDefaultsTest
{
   /*******************************************************************************
    ** A searchable entity.
    *******************************************************************************/
   @QuickSearchable(tableName = "person", basepullIntervalMinutes = 10)
   static class PersonEntity
   {
      @QuickSearchField(weight = 3)
      private String name;

      @QuickSearchField
      private String email;
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @BeforeEach
   void setUp()
   {
      MemoryRecordStore.getInstance().reset();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      QContext.clear();
   }



   /*******************************************************************************
    ** A fresh install: priming fills the config tables, twice changes nothing,
    ** and an edited row survives.
    *******************************************************************************/
   @Test
   void primerPopulatesConfigTablesOnFreshBackend_idempotent_andKeepsEdits() throws QException
   {
      QuickSearchQBitConfig config = produce(List.of(PersonEntity.class));
      QuickSearchRuntime    runtime = config.getRuntime();

      assertThat(rows(QuickSearchIndex.TABLE_NAME)).isEmpty();
      assertThat(rows(QuickSearchSettings.TABLE_NAME)).isEmpty();

      QuickSearchConfigPrimer.PrimingReport first = new QuickSearchConfigPrimer(runtime).prime();

      assertThat(first.newTables()).hasSize(1);
      assertThat(rows(QuickSearchIndex.TABLE_NAME)).singleElement().satisfies(row ->
      {
         assertThat(row.getValueString("tableName")).isEqualTo("person");
         assertThat(row.getValueString("source")).isEqualTo(QuickSearchIndex.SOURCE_CODE);
         assertThat(row.getValueInteger("basepullIntervalMinutes")).isEqualTo(10);
      });
      assertThat(rows(QuickSearchIndexField.TABLE_NAME)).extracting(r -> r.getValueString("fieldName")).containsExactlyInAnyOrder("name", "email");
      assertThat(rows(QuickSearchSettings.TABLE_NAME)).singleElement().satisfies(row ->
      {
         assertThat(row.getValueInteger("id")).isEqualTo(QuickSearchSettings.SETTINGS_ID);
         assertThat(row.getValueBoolean("indexingPaused")).isFalse();
         assertThat(row.getValueString("lastPrimingSummary")).isNotBlank();
      });
      assertThat(rows(QuickSearchConfigTombstone.TABLE_NAME)).as("tombstones legitimately start empty").isEmpty();

      /////////////////////////////////////////////
      // an admin edits the interval (user write) //
      /////////////////////////////////////////////
      QRecord indexRow = rows(QuickSearchIndex.TABLE_NAME).get(0);
      new UpdateAction().execute(new UpdateInput(QuickSearchIndex.TABLE_NAME).withRecord(new QRecord().withValue("id", indexRow.getValue("id")).withValue("basepullIntervalMinutes", 45)));

      QuickSearchConfigPrimer.PrimingReport second = new QuickSearchConfigPrimer(runtime).prime();

      assertThat(second.newTables()).isEmpty();
      assertThat(second.newFields()).isZero();
      assertThat(rows(QuickSearchIndex.TABLE_NAME)).hasSize(1);
      assertThat(rows(QuickSearchIndexField.TABLE_NAME)).hasSize(2);
      assertThat(rows(QuickSearchSettings.TABLE_NAME)).hasSize(1);
      assertThat(rows(QuickSearchIndex.TABLE_NAME).get(0).getValueInteger("basepullIntervalMinutes")).as("the admin's edit survives priming").isEqualTo(45);
   }



   /*******************************************************************************
    ** Even with nothing searchable declared in code, priming creates the settings
    ** row (id 1), so the pause switch and priming summary always exist.
    *******************************************************************************/
   @Test
   void primerCreatesSettingsRowEvenWithNoCodeTables() throws QException
   {
      QuickSearchQBitConfig config = produce(List.of(PersonEntity.class));
      config.getRuntime().setDiscoveredTables(List.of());

      new QuickSearchConfigPrimer(config.getRuntime()).prime();

      assertThat(rows(QuickSearchIndex.TABLE_NAME)).isEmpty();
      assertThat(rows(QuickSearchSettings.TABLE_NAME)).singleElement().satisfies(row ->
      {
         assertThat(row.getValueInteger("id")).isEqualTo(QuickSearchSettings.SETTINGS_ID);
         assertThat(row.getValueBoolean("indexingPaused")).isFalse();
         assertThat(row.getValueString("lastPrimingSummary")).isNotBlank();
      });

      new QuickSearchConfigPrimer(config.getRuntime()).prime();
      assertThat(rows(QuickSearchSettings.TABLE_NAME)).hasSize(1);
   }



   /*******************************************************************************
    ** Produce the qbit on a fresh memory-backed instance.
    *******************************************************************************/
   private QuickSearchQBitConfig produce(List<Class<?>> entityClasses) throws QException
   {
      QInstance qInstance = new QInstance();
      qInstance.addBackend(new QBackendMetaData().withName("memory").withBackendType(MemoryBackendModule.class));
      qInstance.withInstanceDefaultAuthentication(new QAuthenticationMetaData().withName("anonymous").withType(QAuthenticationType.FULLY_ANONYMOUS));
      qInstance.addTable(new QTableMetaData().withName("person").withBackendName("memory").withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("name", QFieldType.STRING))
         .withField(new QFieldMetaData("email", QFieldType.STRING))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME)));
      QContext.init(qInstance, new QSession());

      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withStartupMode(QuickSearchStartupMode.DEGRADED)
         .withBackendName("memory")
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("seeded-defaults")
         .withSearchableEntityClasses(entityClasses);
      new QuickSearchQBitProducer().withConfig(config).produce(qInstance);
      return (config);
   }



   /*******************************************************************************
    ** Every row of a table.
    *******************************************************************************/
   private List<QRecord> rows(String tableName) throws QException
   {
      return (QuickSearchConfigPrimer.queryAll(tableName));
   }

}

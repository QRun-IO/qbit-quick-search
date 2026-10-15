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

package com.kingsrook.qbits.quicksearch.config;


import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.audits.AuditsMetaDataProvider;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.model.QuickSearchConfigTombstone;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexField;
import com.kingsrook.qbits.quicksearch.model.QuickSearchSettings;
import com.kingsrook.qbits.quicksearch.processes.AbstractIndexingStep;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Self-priming and the precedence of code defaults versus owner edits.
 *******************************************************************************/
class QuickSearchConfigPrimerTest extends BaseDataDrivenTest
{

   /*******************************************************************************
    ** A new code table gets its index row (source CODE, queued to build) and
    ** field rows, and the home's summary says so.
    *******************************************************************************/
   @Test
   void testNewCodeTable_seededAndQueued() throws QException
   {
      QuickSearchConfigPrimer.PrimingReport report = new QuickSearchConfigPrimer(runtime).prime();

      assertThat(report.newTables()).hasSize(1);
      assertThat(report.summary()).startsWith("New: 1 table added and building");
      QRecord row = indexRow(TEST_ENTITY_TABLE);
      assertThat(row.getValueString("source")).isEqualTo(QuickSearchIndex.SOURCE_CODE);
      assertThat(row.getValueBoolean("ownerEdited")).isFalse();
      assertThat(row.getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_PENDING_BUILD);
      assertThat(row.getValueInteger("basepullIntervalMinutes")).isEqualTo(5);
      assertThat(fieldRows(TEST_ENTITY_TABLE)).extracting(r -> r.getValueString("fieldName")).containsExactly("name", "description");
      assertThat(fieldRows(TEST_ENTITY_TABLE)).extracting(r -> r.getValueInteger("weight")).containsExactly(3, 1);

      QRecord settings = rows(QuickSearchSettings.TABLE_NAME).get(0);
      assertThat(settings.getValueInteger("id")).isEqualTo(QuickSearchSettings.SETTINGS_ID);
      assertThat(settings.getValueString("lastPrimingSummary")).contains("New: 1 table");
      assertThat(settings.getValueBoolean("indexingPaused")).isFalse();

      //////////////////////////////////////////////
      // priming again changes nothing            //
      //////////////////////////////////////////////
      QuickSearchConfigPrimer.PrimingReport again = new QuickSearchConfigPrimer(runtime).prime();
      assertThat(again.hasChanges()).isFalse();
      assertThat(again.summary()).startsWith("No changes");
      assertThat(rows(QuickSearchIndex.TABLE_NAME)).hasSize(1);
      assertThat(fieldRows(TEST_ENTITY_TABLE)).hasSize(2);
   }



   /*******************************************************************************
    ** Re-priming only records the priming outcome on the settings row: that is
    ** not audited. An admin settings edit still is.
    *******************************************************************************/
   @Test
   void testRepriming_settingsBookkeepingIsNotAudited() throws QException
   {
      new AuditsMetaDataProvider().defineAll(QContext.getQInstance(), TEST_BACKEND_NAME, null);
      new QuickSearchConfigPrimer(runtime).prime();
      long afterFirst = settingsEditAudits();
      new QuickSearchConfigPrimer(runtime).prime();
      assertThat(settingsEditAudits()).as("re-priming is not audited").isEqualTo(afterFirst);

      new UpdateAction().execute(new UpdateInput().withTableName(QuickSearchSettings.TABLE_NAME)
         .withRecord(new QRecord().withValue("id", QuickSearchSettings.SETTINGS_ID).withValue("indexingPaused", true)));
      assertThat(settingsEditAudits()).as("an admin settings edit is audited").isEqualTo(afterFirst + 1);
   }



   /*******************************************************************************
    ** Audit rows recording an edit of the settings row.
    *******************************************************************************/
   private long settingsEditAudits() throws QException
   {
      return (rows(AuditsMetaDataProvider.TABLE_NAME_AUDIT).stream()
         .filter(audit -> audit.getValueString("message") != null && audit.getValueString("message").contains("Edited"))
         .count());
   }



   /*******************************************************************************
    ** Code defaults update rows nobody edited, and never rows an owner edited.
    *******************************************************************************/
   @Test
   void testPrecedence_ownerEditsWin() throws QException
   {
      new QuickSearchConfigPrimer(runtime).prime();
      QRecord row = indexRow(TEST_ENTITY_TABLE);
      setStatus(row, AbstractIndexingStep.STATUS_ACTIVE);

      ////////////////////////////////////////////////////////////////////////
      // an admin edits the interval and the weight of "name" (user writes)  //
      ////////////////////////////////////////////////////////////////////////
      new UpdateAction().execute(new UpdateInput(QuickSearchIndex.TABLE_NAME).withRecord(new QRecord().withValue("id", row.getValue("id")).withValue("basepullIntervalMinutes", 30)));
      QRecord nameField = fieldRows(TEST_ENTITY_TABLE).stream().filter(f -> "name".equals(f.getValueString("fieldName"))).findFirst().orElseThrow();
      new UpdateAction().execute(new UpdateInput(QuickSearchIndexField.TABLE_NAME).withRecord(new QRecord().withValue("id", nameField.getValue("id")).withValue("weight", 7)));
      assertThat(indexRow(TEST_ENTITY_TABLE).getValueBoolean("ownerEdited")).isTrue();

      ////////////////////////////////////////////////////////////////////////
      // code now says interval 15, name weight 9, description weight 4, and //
      // adds a field                                                        //
      ////////////////////////////////////////////////////////////////////////
      runtime.setDiscoveredTables(List.of(new QuickSearchableTableConfig()
         .withTableName(TEST_ENTITY_TABLE)
         .withPrimaryKeyField("id")
         .withSearchableFields(List.of("name", "description"))
         .withFieldWeights(Map.of("name", 9, "description", 4))
         .withBasepullTimestampField("modifyDate")
         .withBasepullIntervalMinutes(15)
         .withEnabledByDefault(true)));
      setStatus(indexRow(TEST_ENTITY_TABLE), AbstractIndexingStep.STATUS_ACTIVE);
      QuickSearchConfigPrimer.PrimingReport report = new QuickSearchConfigPrimer(runtime).prime();

      QRecord after = indexRow(TEST_ENTITY_TABLE);
      assertThat(after.getValueInteger("basepullIntervalMinutes")).as("owner-edited row keeps its interval").isEqualTo(30);
      Map<String, Integer> weights = new java.util.HashMap<>();
      fieldRows(TEST_ENTITY_TABLE).forEach(f -> weights.put(f.getValueString("fieldName"), f.getValueInteger("weight")));
      assertThat(weights).containsEntry("name", 7).as("owner-edited field keeps its weight");
      assertThat(weights).containsEntry("description", 4).as("untouched field follows code");
      assertThat(report.updatedFields()).isEqualTo(1);
      assertThat(after.getValueString("status")).as("a code field change queues a rebuild").isEqualTo(AbstractIndexingStep.STATUS_PENDING_BUILD);
   }



   /*******************************************************************************
    ** A row nobody edited follows code's interval; a new code field is added
    ** and queues a rebuild.
    *******************************************************************************/
   @Test
   void testUneditedRow_followsCode_andNewFieldAdded() throws QException
   {
      new QuickSearchConfigPrimer(runtime).prime();
      setStatus(indexRow(TEST_ENTITY_TABLE), AbstractIndexingStep.STATUS_ACTIVE);

      runtime.setDiscoveredTables(List.of(new QuickSearchableTableConfig()
         .withTableName(TEST_ENTITY_TABLE)
         .withPrimaryKeyField("id")
         .withSearchableFields(List.of("name", "description", "modifyDate"))
         .withBasepullTimestampField("modifyDate")
         .withBasepullIntervalMinutes(15)
         .withEnabledByDefault(true)));
      QuickSearchConfigPrimer.PrimingReport report = new QuickSearchConfigPrimer(runtime).prime();

      assertThat(indexRow(TEST_ENTITY_TABLE).getValueInteger("basepullIntervalMinutes")).isEqualTo(15);
      assertThat(fieldRows(TEST_ENTITY_TABLE)).extracting(r -> r.getValueString("fieldName")).contains("modifyDate");
      assertThat(report.newFields()).isEqualTo(1);
      assertThat(report.rebuilds()).hasSize(1);
      assertThat(report.summary()).contains("1 field added").contains("rebuilding");
   }



   /*******************************************************************************
    ** Removed (tombstoned) tables and fields are never added back by code.
    *******************************************************************************/
   @Test
   void testTombstones_noResurrection() throws QException
   {
      new QuickSearchConfigPrimer(runtime).prime();

      ////////////////////////////////////////////////////////
      // the owner removes "description" (a user delete)     //
      ////////////////////////////////////////////////////////
      QRecord description = fieldRows(TEST_ENTITY_TABLE).stream().filter(f -> "description".equals(f.getValueString("fieldName"))).findFirst().orElseThrow();
      DeleteInput deleteInput = new DeleteInput(QuickSearchIndexField.TABLE_NAME);
      deleteInput.setPrimaryKeys(List.of(description.getValue("id")));
      new DeleteAction().execute(deleteInput);
      assertThat(rows(QuickSearchConfigTombstone.TABLE_NAME)).singleElement().satisfies(t -> assertThat(t.getValueString("fieldName")).isEqualTo("description"));

      new QuickSearchConfigPrimer(runtime).prime();
      assertThat(fieldRows(TEST_ENTITY_TABLE)).extracting(r -> r.getValueString("fieldName")).containsExactly("name");

      ////////////////////////////////////////////////////////
      // the owner removes the whole table                   //
      ////////////////////////////////////////////////////////
      QuickSearchTombstones.add(TEST_ENTITY_TABLE, null, "test");
      QuickSearchConfigWrites.asSystem(() ->
      {
         DeleteInput removeTable = new DeleteInput(QuickSearchIndex.TABLE_NAME);
         removeTable.setPrimaryKeys(List.of(indexRow(TEST_ENTITY_TABLE).getValue("id")));
         return (new DeleteAction().execute(removeTable));
      });
      QuickSearchConfigPrimer.PrimingReport report = new QuickSearchConfigPrimer(runtime).prime();
      assertThat(indexRow(TEST_ENTITY_TABLE)).isNull();
      assertThat(report.skipped()).isEqualTo(1);

      ////////////////////////////////////////////////////////
      // deleting the tombstone lets code seed it again      //
      ////////////////////////////////////////////////////////
      QuickSearchTombstones.remove(TEST_ENTITY_TABLE, null);
      new QuickSearchConfigPrimer(runtime).prime();
      assertThat(indexRow(TEST_ENTITY_TABLE)).isNotNull();
      assertThat(fieldRows(TEST_ENTITY_TABLE)).extracting(r -> r.getValueString("fieldName")).containsExactly("name");
   }



   /*******************************************************************************
    ** Rows from before field records existed get their fields without a
    ** rebuild when the index was built with exactly code's fields.
    *******************************************************************************/
   @Test
   void testUpgrade_existingRowWithoutFields_noRebuild() throws QException
   {
      String builtJson = AbstractIndexingStep.buildSearchableFieldsJson(runtime.getCodeTables().get(0));
      QuickSearchConfigWrites.asSystem(() -> insert(QuickSearchIndex.TABLE_NAME, new QRecord()
         .withValue("tableName", TEST_ENTITY_TABLE).withValue("enabled", false).withValue("basepullIntervalMinutes", 5)
         .withValue("basepullTimestampField", "modifyDate").withValue("searchableFieldsJson", builtJson).withValue("status", AbstractIndexingStep.STATUS_ACTIVE)));

      QuickSearchConfigPrimer.PrimingReport report = new QuickSearchConfigPrimer(runtime).prime();
      QRecord row = indexRow(TEST_ENTITY_TABLE);
      assertThat(report.newTables()).isEmpty();
      assertThat(report.rebuilds()).isEmpty();
      assertThat(row.getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_ACTIVE);
      assertThat(row.getValueBoolean("enabled")).as("priming never changes enabled on an existing row").isFalse();
      assertThat(row.getValueString("source")).isEqualTo(QuickSearchIndex.SOURCE_CODE);
      assertThat(fieldRows(TEST_ENTITY_TABLE)).hasSize(2);
      assertThat(AbstractIndexingStep.buildSearchableFieldsJson(runtime.getTableConfig(TEST_ENTITY_TABLE))).as("records give the same fields JSON as code").isEqualTo(builtJson);
   }



   /*******************************************************************************
    ** The store reads code until primed, then the records; a code table that
    ** is not in the instance is never primed.
    *******************************************************************************/
   @Test
   void testStore_recordsModeAfterPriming() throws QException
   {
      QuickSearchConfigStore store = runtime.getConfigStore();
      assertThat(store.isPrimed()).isFalse();
      assertThat(runtime.getDiscoveredTables()).extracting(QuickSearchableTableConfig::getTableName).containsExactly(TEST_ENTITY_TABLE);
      assertThat(store.isPrimed()).isTrue();
      assertThat(store.isRecordsMode()).isTrue();
      assertThat(runtime.getTableConfig(TEST_ENTITY_TABLE).getQuickSearchIndexId()).isNotNull();
      assertThat(runtime.getTableConfig(TEST_ENTITY_TABLE).getEnabled()).isTrue();
      assertThat(runtime.getTableConfig(null)).isNull();

      runtime.setDiscoveredTables(List.of(new QuickSearchableTableConfig().withTableName("notInInstance").withSearchableFields(List.of("x"))));
      store.setPrimed(false);
      store.tryPrime();
      assertThat(indexRow("notInInstance")).isNull();
   }



   /*******************************************************************************
    ** Set a row's status (a system write, as the steps do).
    *******************************************************************************/
   private static void setStatus(QRecord row, String status) throws QException
   {
      QuickSearchConfigWrites.asSystem(() ->
      {
         new UpdateAction().execute(new UpdateInput(QuickSearchIndex.TABLE_NAME).withRecord(new QRecord().withValue("id", row.getValue("id")).withValue("status", status)));
         return (null);
      });
   }

}

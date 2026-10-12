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
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.values.SearchPossibleValueSourceInput;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValue;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFieldAnalyzer;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFieldRole;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexField;
import com.kingsrook.qbits.quicksearch.model.QuickSearchSettings;
import com.kingsrook.qbits.quicksearch.processes.AbstractIndexingStep;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The config tables' customizers (validation, ownerEdited, NEEDS_REINDEX,
 ** tombstones, pause stamping), the field rules and the possible-value
 ** providers.
 *******************************************************************************/
class QuickSearchConfigCustomizersTest extends BaseDataDrivenTest
{

   /*******************************************************************************
    ** Field rows: a field must exist, be indexable, not sensitive or hidden,
    ** and not be listed twice; weights 1-1000; known role and analyzer.
    *******************************************************************************/
   @Test
   void testFieldValidation() throws QException
   {
      runtime.getConfigStore().tryPrime();
      Integer indexId = indexRow(TEST_ENTITY_TABLE).getValueInteger("id");

      assertThat(insertField(indexId, "nope", 1, null).getErrors()).singleElement().satisfies(e -> assertThat(e.getMessage()).contains("does not exist"));
      assertThat(insertField(indexId, "name", 1, null).getErrors()).anySatisfy(e -> assertThat(e.getMessage()).contains("already a searchable field"));
      assertThat(insertField(indexId, "modifyDate", 0, null).getErrors()).singleElement().satisfies(e -> assertThat(e.getMessage()).contains("Weight"));
      assertThat(insertField(indexId, "modifyDate", 2, "NOT_A_ROLE").getErrors()).singleElement().satisfies(e -> assertThat(e.getMessage()).contains("Unknown role"));
      assertThat(insertField(null, "modifyDate", 2, null).getErrors()).anySatisfy(e -> assertThat(e.getMessage()).contains("Choose the search index"));

      QContext.getQInstance().getTable(TEST_ENTITY_TABLE).withField(new QFieldMetaData("apiKey", QFieldType.STRING).withIsSensitive(true));
      QContext.getQInstance().getTable(TEST_ENTITY_TABLE).withField(new QFieldMetaData("secretHash", QFieldType.PASSWORD));
      assertThat(insertField(indexId, "apiKey", 1, null).getErrors()).singleElement().satisfies(e -> assertThat(e.getMessage()).contains("sensitive"));
      assertThat(insertField(indexId, "secretHash", 1, null).getErrors()).singleElement().satisfies(e -> assertThat(e.getMessage()).contains("sensitive"));

      QRecord ok = insertField(indexId, "modifyDate", 2, QuickSearchFieldRole.EXACT.name());
      assertThat(ok.getErrors()).isEmpty();
      assertThat(ok.getValueString("source")).isEqualTo(QuickSearchIndexField.SOURCE_OWNER);
      assertThat(ok.getValueBoolean("ownerEdited")).isTrue();
      assertThat(ok.getValueInteger("sortOrder")).isEqualTo(30);

      QRecord bad = new UpdateAction().execute(new UpdateInput(QuickSearchIndexField.TABLE_NAME).withRecord(new QRecord().withValue("id", ok.getValue("id"))
         .withValue("analyzer", "KLINGON"))).getRecords().get(0);
      assertThat(bad.getErrors()).singleElement().satisfies(e -> assertThat(e.getMessage()).contains("Unknown analyzer"));
      QRecord moved = new UpdateAction().execute(new UpdateInput(QuickSearchIndexField.TABLE_NAME).withRecord(new QRecord().withValue("id", ok.getValue("id"))
         .withValue("quickSearchIndexId", indexId + 1))).getRecords().get(0);
      assertThat(moved.getErrors()).singleElement().satisfies(e -> assertThat(e.getMessage()).contains("cannot be moved"));
   }



   /*******************************************************************************
    ** An owner's field edit takes effect for search at once and marks the index
    ** NEEDS_REINDEX; the role and analyzer reach the effective config.
    *******************************************************************************/
   @Test
   void testFieldEdit_effectiveAtOnce_marksNeedsReindex() throws QException
   {
      runtime.getConfigStore().tryPrime();
      setIndexStatus(TEST_ENTITY_TABLE, AbstractIndexingStep.STATUS_ACTIVE);
      QRecord description = fieldRows(TEST_ENTITY_TABLE).stream().filter(f -> "description".equals(f.getValueString("fieldName"))).findFirst().orElseThrow();

      new UpdateAction().execute(new UpdateInput(QuickSearchIndexField.TABLE_NAME).withRecord(new QRecord().withValue("id", description.getValue("id"))
         .withValue("weight", 5).withValue("role", QuickSearchFieldRole.KEYWORD.name()).withValue("analyzer", QuickSearchFieldAnalyzer.ENGLISH.name())));

      assertThat(indexRow(TEST_ENTITY_TABLE).getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_NEEDS_REINDEX);
      var tableConfig = runtime.getTableConfig(TEST_ENTITY_TABLE);
      assertThat(tableConfig.getFieldWeights()).containsEntry("description", 5);
      assertThat(tableConfig.getRole("description")).isEqualTo(QuickSearchFieldRole.KEYWORD);
      assertThat(tableConfig.getAnalyzer("description")).isEqualTo(QuickSearchFieldAnalyzer.ENGLISH);
      assertThat(tableConfig.isIncludedInSnippet("description")).isFalse();
      assertThat(tableConfig.isAllPlainFields()).isFalse();
      assertThat(fieldRows(TEST_ENTITY_TABLE).stream().filter(f -> "description".equals(f.getValueString("fieldName"))).findFirst().orElseThrow().getValueBoolean("ownerEdited")).isTrue();

      ///////////////////////////////////////////////////////////////
      // a sort-order-only edit does not ask for a rebuild         //
      ///////////////////////////////////////////////////////////////
      setIndexStatus(TEST_ENTITY_TABLE, AbstractIndexingStep.STATUS_ACTIVE);
      new UpdateAction().execute(new UpdateInput(QuickSearchIndexField.TABLE_NAME).withRecord(new QRecord().withValue("id", description.getValue("id")).withValue("sortOrder", 5)));
      assertThat(indexRow(TEST_ENTITY_TABLE).getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_ACTIVE);
      assertThat(runtime.getTableConfig(TEST_ENTITY_TABLE).getSearchableFields()).containsExactly("description", "name");

      ///////////////////////////////////////////////////////////////
      // re-adding a removed field clears its tombstone            //
      ///////////////////////////////////////////////////////////////
      QuickSearchTombstones.add(TEST_ENTITY_TABLE, "modifyDate", "test");
      assertThat(QuickSearchTombstones.exists(TEST_ENTITY_TABLE, "modifyDate")).isTrue();
      insertField(indexRow(TEST_ENTITY_TABLE).getValueInteger("id"), "modifyDate", 1, null);
      assertThat(QuickSearchTombstones.exists(TEST_ENTITY_TABLE, "modifyDate")).isFalse();
   }



   /*******************************************************************************
    ** An owner's edit of the index row marks it ownerEdited; enabling it again
    ** queues a build; a bad timestamp field or negative interval is refused.
    *******************************************************************************/
   @Test
   void testIndexRowEdits() throws QException
   {
      runtime.getConfigStore().tryPrime();
      QRecord row = indexRow(TEST_ENTITY_TABLE);
      setIndexStatus(TEST_ENTITY_TABLE, AbstractIndexingStep.STATUS_ACTIVE);

      updateIndex(row, Map.of("enabled", false));
      assertThat(indexRow(TEST_ENTITY_TABLE).getValueBoolean("ownerEdited")).isTrue();
      assertThat(runtime.isTableEnabled(TEST_ENTITY_TABLE)).isFalse();

      updateIndex(row, Map.of("enabled", true));
      assertThat(indexRow(TEST_ENTITY_TABLE).getValueString("status")).isEqualTo(AbstractIndexingStep.STATUS_PENDING_BUILD);

      assertThat(updateIndex(row, Map.of("basepullTimestampField", "name")).getErrors()).singleElement().satisfies(e -> assertThat(e.getMessage()).contains("not a date-time field"));
      assertThat(updateIndex(row, Map.of("basepullIntervalMinutes", -1)).getErrors()).singleElement().satisfies(e -> assertThat(e.getMessage()).contains("negative"));

      ////////////////////////////////////////////////////////////////////////
      // a status-only update (what the indexing steps write) is not an edit //
      ////////////////////////////////////////////////////////////////////////
      assertThat(QuickSearchIndexTableCustomizer.changesConfig(new QRecord().withValue("status", "X"), row)).isFalse();
      assertThat(QuickSearchIndexTableCustomizer.changesConfig(new QRecord().withValue("enabled", "true"), new QRecord().withValue("enabled", true))).isFalse();
   }



   /*******************************************************************************
    ** Pausing stamps who and when; resuming clears them and releases the
    ** changes deferred while paused.
    *******************************************************************************/
   @Test
   void testPauseAndResume_settingsCustomizer() throws QException
   {
      runtime.getConfigStore().tryPrime();
      QContext.getQSession().setUser(new com.kingsrook.qqq.backend.core.model.session.QUser().withFullName("Jane Admin"));

      new UpdateAction().execute(new UpdateInput(QuickSearchSettings.TABLE_NAME).withRecord(new QRecord().withValue("id", 1).withValue("indexingPaused", true).withValue("pauseReason", "migration")));
      QRecord paused = rows(QuickSearchSettings.TABLE_NAME).get(0);
      assertThat(paused.getValueString("pausedBy")).isEqualTo("Jane Admin");
      assertThat(paused.getValue("pausedAt")).isNotNull();
      assertThat(runtime.isIndexingPaused()).isTrue();

      QuickSearchStatus status = QuickSearchStatus.read(runtime);
      assertThat(status.paused()).isTrue();
      assertThat(status.pausedText()).startsWith("Paused since ").contains("by Jane Admin").contains("(migration)");
      assertThat(status.bannerHtml()).contains("Paused since");

      insert(QuickSearchFailedEvent.TABLE_NAME, new QRecord().withValue("tableName", TEST_ENTITY_TABLE).withValue("recordId", "1").withValue("action", "INDEX")
         .withValue("attempts", 0).withValue("status", QuickSearchFailedEvent.STATUS_DEFERRED));

      new UpdateAction().execute(new UpdateInput(QuickSearchSettings.TABLE_NAME).withRecord(new QRecord().withValue("id", 1).withValue("indexingPaused", false)));
      QRecord resumed = rows(QuickSearchSettings.TABLE_NAME).get(0);
      assertThat(resumed.getValue("pausedAt")).isNull();
      assertThat(resumed.getValue("pausedBy")).isNull();
      assertThat(runtime.isIndexingPaused()).isFalse();
      assertThat(rows(QuickSearchFailedEvent.TABLE_NAME)).singleElement().satisfies(e -> assertThat(e.getValueString("status")).isEqualTo(QuickSearchFailedEvent.STATUS_PENDING));
   }



   /*******************************************************************************
    ** Which tables can be added and which fields offered.
    *******************************************************************************/
   @Test
   void testFieldRulesAndPossibleValues() throws QException
   {
      runtime.getConfigStore().tryPrime();
      var qInstance = QContext.getQInstance();

      assertThat(QuickSearchFieldRules.whyNotEligible(qInstance, config, ARTICLE_TABLE)).isNull();
      assertThat(QuickSearchFieldRules.whyNotEligible(qInstance, config, SECRET_TABLE)).contains("secrets");
      assertThat(QuickSearchFieldRules.whyNotEligible(qInstance, config, QuickSearchIndex.TABLE_NAME)).contains("internal");
      assertThat(QuickSearchFieldRules.whyNotEligible(qInstance, config, "nope")).contains("not in this application");
      qInstance.getTable(ARTICLE_TABLE).setSourceQBitName("some:qbit");
      assertThat(QuickSearchFieldRules.whyNotEligible(qInstance, config, ARTICLE_TABLE)).contains("internal");
      qInstance.getTable(ARTICLE_TABLE).setSourceQBitName(null);

      assertThat(QuickSearchFieldRules.defaultFields(qInstance.getTable(ARTICLE_TABLE))).extracting(QuickSearchFieldRules.DefaultField::fieldName)
         .containsExactly("title", "body", "sku");
      assertThat(QuickSearchFieldRules.defaultFields(qInstance.getTable(ARTICLE_TABLE)).get(0).role()).isEqualTo(QuickSearchFieldRole.TITLE);
      assertThat(QuickSearchFieldRules.defaultTimestampField(qInstance.getTable(ARTICLE_TABLE))).isEqualTo("modifyDate");
      assertThat(QuickSearchFieldRules.whyNotIndexable(qInstance.getTable(ARTICLE_TABLE), "hiddenCode")).contains("hidden");
      assertThat(QuickSearchFieldRules.whyNotIndexable(qInstance.getTable(ARTICLE_TABLE), "published")).contains("boolean");

      ////////////////////////////////////////////////////////////////////////
      // eligible tables: readable, not indexed, not internal or secret     //
      ////////////////////////////////////////////////////////////////////////
      List<QPossibleValue<String>> eligible = new QuickSearchTablePossibleValueProviders.EligibleTables().search(new SearchPossibleValueSourceInput());
      assertThat(eligible).extracting(QPossibleValue::getId).contains(ARTICLE_TABLE).doesNotContain(TEST_ENTITY_TABLE, SECRET_TABLE, QuickSearchIndex.TABLE_NAME);
      assertThat(new QuickSearchTablePossibleValueProviders.EligibleTables().search(new SearchPossibleValueSourceInput().withSearchTerm("artic"))).extracting(QPossibleValue::getLabel).containsExactly("Article");

      qInstance.getTable(ARTICLE_TABLE).withPermissionRules(new com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules()
         .withLevel(com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel.READ_WRITE_PERMISSIONS));
      assertThat(new QuickSearchTablePossibleValueProviders.EligibleTables().search(new SearchPossibleValueSourceInput())).extracting(QPossibleValue::getId)
         .as("a table the user cannot read is not offered").doesNotContain(ARTICLE_TABLE);
      qInstance.getTable(ARTICLE_TABLE).setPermissionRules(null);

      assertThat(new QuickSearchTablePossibleValueProviders.SearchableTables().search(new SearchPossibleValueSourceInput())).extracting(QPossibleValue::getId).containsExactly(TEST_ENTITY_TABLE);
      assertThat(new QuickSearchTablePossibleValueProviders.SearchableTables().getPossibleValue(ARTICLE_TABLE).getLabel()).isEqualTo("Article");

      SearchPossibleValueSourceInput byTable = new SearchPossibleValueSourceInput();
      byTable.setOtherValues(Map.of("tableName", ARTICLE_TABLE));
      assertThat(new QuickSearchTablePossibleValueProviders.IndexableFields().search(byTable)).extracting(QPossibleValue::getId)
         .contains("title", "body", "sku", "modifyDate").doesNotContain("internalNotes", "hiddenCode", "published");
      assertThat(new QuickSearchTablePossibleValueProviders.TimestampFields().search(byTable)).extracting(QPossibleValue::getId).containsExactly("modifyDate");

      SearchPossibleValueSourceInput byIndex = new SearchPossibleValueSourceInput();
      byIndex.setOtherValues(Map.of("quickSearchIndexId", indexRow(TEST_ENTITY_TABLE).getValueInteger("id")));
      assertThat(new QuickSearchTablePossibleValueProviders.IndexableFields().search(byIndex)).extracting(QPossibleValue::getId).contains("name", "description");
      assertThat(new QuickSearchTablePossibleValueProviders.IndexableFields().search(new SearchPossibleValueSourceInput())).isEmpty();
      assertThat(new QuickSearchTablePossibleValueProviders.IndexableFields().getPossibleValue("title").getLabel()).isEqualTo("title");
   }



   /*******************************************************************************
    ** Insert a field row (a user write) and return it with any errors.
    *******************************************************************************/
   private static QRecord insertField(Integer indexId, String fieldName, Integer weight, String role) throws QException
   {
      return (insert(QuickSearchIndexField.TABLE_NAME, new QRecord().withValue("quickSearchIndexId", indexId).withValue("fieldName", fieldName)
         .withValue("weight", weight).withValue("role", role)).get(0));
   }



   /*******************************************************************************
    ** Update an index row (a user write).
    *******************************************************************************/
   private static QRecord updateIndex(QRecord row, Map<String, ? extends java.io.Serializable> values) throws QException
   {
      QRecord update = new QRecord().withValue("id", row.getValue("id"));
      values.forEach(update::setValue);
      return (new UpdateAction().execute(new UpdateInput(QuickSearchIndex.TABLE_NAME).withRecord(update)).getRecords().get(0));
   }



   /*******************************************************************************
    ** Set an index row's status (a system write).
    *******************************************************************************/
   private static void setIndexStatus(String tableName, String status) throws QException
   {
      QRecord row = indexRow(tableName);
      QuickSearchConfigWrites.asSystem(() ->
      {
         new UpdateAction().execute(new UpdateInput(QuickSearchIndex.TABLE_NAME).withRecord(new QRecord().withValue("id", row.getValue("id")).withValue("status", status)));
         return (null);
      });
   }

}

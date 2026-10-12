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

package com.kingsrook.qbits.quicksearch.metadata;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizers;
import com.kingsrook.qqq.backend.core.actions.dashboard.widgets.ChildRecordListRenderer;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.audits.AuditLevel;
import com.kingsrook.qqq.backend.core.model.metadata.audits.QAuditRules;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.joins.JoinOn;
import com.kingsrook.qqq.backend.core.model.metadata.joins.JoinType;
import com.kingsrook.qqq.backend.core.model.metadata.joins.QJoinMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValue;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSourceType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Capability;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QFieldSection;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Tier;
import com.kingsrook.qqq.backend.core.model.metadata.tables.UniqueKey;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.config.QuickSearchIndexFieldTableCustomizer;
import com.kingsrook.qbits.quicksearch.config.QuickSearchSettingsTableCustomizer;
import com.kingsrook.qbits.quicksearch.config.QuickSearchTablePossibleValueProviders;
import com.kingsrook.qbits.quicksearch.model.QuickSearchConfigTombstone;
import com.kingsrook.qbits.quicksearch.model.QuickSearchDocumentsChoice;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFieldAnalyzer;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFieldRole;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexField;
import com.kingsrook.qbits.quicksearch.model.QuickSearchSettings;


/*******************************************************************************
 ** Builds the data-driven config metadata (1.1.0-RC.3): the field, settings
 ** and tombstone tables, the index-to-fields join and child-record widget,
 ** and the possible-value sources the config screens use.
 *******************************************************************************/
public class QuickSearchConfigMetaDataHelper
{
   public static final String ELIGIBLE_TABLE_PVS   = "quick_search_eligible_table";
   public static final String INDEXABLE_FIELD_PVS  = "quick_search_indexable_field";
   public static final String TIMESTAMP_FIELD_PVS  = "quick_search_timestamp_field";
   public static final String FIELD_ROLE_PVS       = "quick_search_field_role";
   public static final String FIELD_ANALYZER_PVS   = "quick_search_field_analyzer";
   public static final String DOCUMENTS_CHOICE_PVS = "quick_search_documents_choice";
   public static final String CONFIG_SOURCE_PVS    = "quick_search_config_source";
   public static final String INDEX_FIELDS_JOIN    = "quick_search_index_join_quick_search_index_field";
   public static final String INDEX_FIELDS_WIDGET  = "quickSearchIndexFields";
   public static final String FIELDS_ASSOCIATION   = "fields";



   /*******************************************************************************
    ** quick_search_index_field: the searchable fields of each index row.
    *******************************************************************************/
   public static QTableMetaData buildIndexFieldTable(QuickSearchQBitConfig config)
   {
      return (new QTableMetaData()
         .withName(config.applyTablePrefix(QuickSearchIndexField.TABLE_NAME))
         .withLabel("Quick Search Index Field")
         .withIcon(new QIcon("text_fields"))
         .withBackendName(config.getBackendName())
         .withPrimaryKeyField("id")
         .withRecordLabelFormat("%s")
         .withRecordLabelFields("fieldName")
         .withUniqueKey(new UniqueKey("quickSearchIndexId", "fieldName"))
         .withPermissionRules(config.getTablePermissionRules() == null ? null : config.getTablePermissionRules().clone())
         .withAuditRules(new QAuditRules().withAuditLevel(AuditLevel.FIELD))
         .withCustomizer(TableCustomizers.PRE_INSERT_RECORD, new QCodeReference(QuickSearchIndexFieldTableCustomizer.class))
         .withCustomizer(TableCustomizers.PRE_UPDATE_RECORD, new QCodeReference(QuickSearchIndexFieldTableCustomizer.class))
         .withCustomizer(TableCustomizers.POST_INSERT_RECORD, new QCodeReference(QuickSearchIndexFieldTableCustomizer.class))
         .withCustomizer(TableCustomizers.POST_UPDATE_RECORD, new QCodeReference(QuickSearchIndexFieldTableCustomizer.class))
         .withCustomizer(TableCustomizers.POST_DELETE_RECORD, new QCodeReference(QuickSearchIndexFieldTableCustomizer.class))
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("quickSearchIndexId", QFieldType.INTEGER).withLabel("Search Index").withIsRequired(true)
            .withPossibleValueSourceName(config.applyTablePrefix(QuickSearchIndex.TABLE_NAME)))
         .withField(new QFieldMetaData("fieldName", QFieldType.STRING).withLabel("Field").withIsRequired(true)
            .withPossibleValueSourceName(config.applyTablePrefix(INDEXABLE_FIELD_PVS)))
         .withField(new QFieldMetaData("weight", QFieldType.INTEGER).withDefaultValue(1))
         .withField(new QFieldMetaData("role", QFieldType.STRING).withDefaultValue(QuickSearchFieldRole.BODY.name())
            .withPossibleValueSourceName(config.applyTablePrefix(FIELD_ROLE_PVS)))
         .withField(new QFieldMetaData("analyzer", QFieldType.STRING).withLabel("Analyzer (blank: by role)")
            .withPossibleValueSourceName(config.applyTablePrefix(FIELD_ANALYZER_PVS)))
         .withField(new QFieldMetaData("includeInSnippet", QFieldType.BOOLEAN).withLabel("In Snippet (blank: by role)"))
         .withField(new QFieldMetaData("includeLabel", QFieldType.BOOLEAN).withLabel("Index With Field Label").withDefaultValue(false))
         .withField(new QFieldMetaData("sortOrder", QFieldType.INTEGER))
         .withField(new QFieldMetaData("source", QFieldType.STRING).withIsEditable(false).withPossibleValueSourceName(config.applyTablePrefix(CONFIG_SOURCE_PVS)))
         .withField(new QFieldMetaData("ownerEdited", QFieldType.BOOLEAN).withLabel("Edited by Owner").withIsEditable(false))
         .withField(new QFieldMetaData("createDate", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME).withIsEditable(false))
         .withSection(new QFieldSection("identity", "Field", new QIcon("text_fields"), Tier.T1, List.of("id", "quickSearchIndexId", "fieldName", "weight", "role")))
         .withSection(new QFieldSection("matching", "Matching", new QIcon("tune"), Tier.T2, List.of("analyzer", "includeInSnippet", "includeLabel", "sortOrder")))
         .withSection(new QFieldSection("origin", "Origin", new QIcon("history"), Tier.T3, List.of("source", "ownerEdited", "createDate", "modifyDate"))));
   }



   /*******************************************************************************
    ** quick_search_settings: the one row of system settings (the global pause
    ** and the last priming report). Rows are never inserted or deleted by
    ** users; the QBit creates the row when it primes.
    *******************************************************************************/
   public static QTableMetaData buildSettingsTable(QuickSearchQBitConfig config)
   {
      return (new QTableMetaData()
         .withName(config.applyTablePrefix(QuickSearchSettings.TABLE_NAME))
         .withLabel("Quick Search Settings")
         .withIcon(new QIcon("settings"))
         .withBackendName(config.getBackendName())
         .withPrimaryKeyField("id")
         .withoutCapabilities(Capability.TABLE_INSERT, Capability.TABLE_DELETE)
         .withRecordLabelFormat("Quick Search Settings")
         .withPermissionRules(config.getTablePermissionRules() == null ? null : config.getTablePermissionRules().clone())
         .withAuditRules(new QAuditRules().withAuditLevel(AuditLevel.FIELD))
         .withCustomizer(TableCustomizers.PRE_UPDATE_RECORD, new QCodeReference(QuickSearchSettingsTableCustomizer.class))
         .withCustomizer(TableCustomizers.POST_UPDATE_RECORD, new QCodeReference(QuickSearchSettingsTableCustomizer.class))
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("indexingPaused", QFieldType.BOOLEAN).withLabel("Indexing Paused"))
         .withField(new QFieldMetaData("pauseReason", QFieldType.STRING).withLabel("Pause Reason"))
         .withField(new QFieldMetaData("pausedAt", QFieldType.DATE_TIME).withLabel("Paused Since").withIsEditable(false))
         .withField(new QFieldMetaData("pausedBy", QFieldType.STRING).withLabel("Paused By").withIsEditable(false))
         .withField(new QFieldMetaData("lastPrimedAt", QFieldType.DATE_TIME).withLabel("Last Primed").withIsEditable(false))
         .withField(new QFieldMetaData("lastPrimingSummary", QFieldType.TEXT).withLabel("Last Priming").withIsEditable(false))
         .withField(new QFieldMetaData("lastPrimingNewTables", QFieldType.INTEGER).withLabel("Tables Added by Last Priming").withIsEditable(false))
         .withField(new QFieldMetaData("createDate", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME).withIsEditable(false))
         .withSection(new QFieldSection("pause", "Indexing", new QIcon("pause_circle"), Tier.T1, List.of("id", "indexingPaused", "pauseReason", "pausedAt", "pausedBy")))
         .withSection(new QFieldSection("priming", "Self-priming", new QIcon("auto_mode"), Tier.T2, List.of("lastPrimedAt", "lastPrimingSummary", "lastPrimingNewTables")))
         .withSection(new QFieldSection("dates", "Dates", new QIcon("calendar_today"), Tier.T3, List.of("createDate", "modifyDate"))));
   }



   /*******************************************************************************
    ** quick_search_config_tombstone: what an admin removed, so priming does
    ** not add it back. Written by the QBit; deleting a row lets code defaults
    ** seed that table or field again.
    *******************************************************************************/
   public static QTableMetaData buildTombstoneTable(QuickSearchQBitConfig config)
   {
      return (new QTableMetaData()
         .withName(config.applyTablePrefix(QuickSearchConfigTombstone.TABLE_NAME))
         .withLabel("Quick Search Removed Config")
         .withIcon(new QIcon("delete_sweep"))
         .withBackendName(config.getBackendName())
         .withPrimaryKeyField("id")
         .withoutCapabilities(Capability.TABLE_INSERT, Capability.TABLE_UPDATE)
         .withRecordLabelFormat("%s %s")
         .withRecordLabelFields("tableName", "fieldName")
         .withPermissionRules(config.getTablePermissionRules() == null ? null : config.getTablePermissionRules().clone())
         .withAuditRules(new QAuditRules().withAuditLevel(AuditLevel.RECORD))
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("tableName", QFieldType.STRING).withIsEditable(false)
            .withPossibleValueSourceName(config.applyTablePrefix(QuickSearchSearchableTablePossibleValueSourceProducer.NAME)))
         .withField(new QFieldMetaData("fieldName", QFieldType.STRING).withLabel("Field (blank: whole table)").withIsEditable(false))
         .withField(new QFieldMetaData("removedBy", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("reason", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("createDate", QFieldType.DATE_TIME).withLabel("Removed").withIsEditable(false)));
   }



   /*******************************************************************************
    ** quick_search_index to its field rows.
    *******************************************************************************/
   public static QJoinMetaData buildIndexFieldsJoin(QuickSearchQBitConfig config)
   {
      return (new QJoinMetaData()
         .withName(config.applyTablePrefix(INDEX_FIELDS_JOIN))
         .withType(JoinType.ONE_TO_MANY)
         .withLeftTable(config.applyTablePrefix(QuickSearchIndex.TABLE_NAME))
         .withRightTable(config.applyTablePrefix(QuickSearchIndexField.TABLE_NAME))
         .withJoinOn(new JoinOn("id", "quickSearchIndexId"))
         .withOrderBy(new QFilterOrderBy("sortOrder"))
         .withOrderBy(new QFilterOrderBy("id")));
   }



   /*******************************************************************************
    ** The field rows on the index record view, where fields are added,
    ** edited and removed.
    *******************************************************************************/
   public static QWidgetMetaData buildIndexFieldsWidget(QuickSearchQBitConfig config)
   {
      QWidgetMetaData widget = ChildRecordListRenderer.widgetMetaDataBuilder(buildIndexFieldsJoin(config))
         .withName(config.applyPrefix(INDEX_FIELDS_WIDGET))
         .withLabel("Searchable Fields")
         .withCanAddChildRecord(true)
         .withDisabledFieldsForNewChildRecords(Set.of("quickSearchIndexId"))
         .withDefaultValuesForNewChildRecordsFromParentFields(Map.of("quickSearchIndexId", "id"))
         .withOmitFieldNames(List.of("quickSearchIndexId", "createDate", "modifyDate"))
         .withMaxRows(100)
         .getWidgetMetaData();
      return (widget);
   }



   /*******************************************************************************
    ** The custom possible-value sources over the instance's tables and fields.
    *******************************************************************************/
   public static List<QPossibleValueSource> buildCustomPossibleValueSources(QuickSearchQBitConfig config)
   {
      return (List.of(
         custom(config.applyTablePrefix(ELIGIBLE_TABLE_PVS), QuickSearchTablePossibleValueProviders.EligibleTables.class),
         custom(config.applyTablePrefix(INDEXABLE_FIELD_PVS), QuickSearchTablePossibleValueProviders.IndexableFields.class),
         custom(config.applyTablePrefix(TIMESTAMP_FIELD_PVS), QuickSearchTablePossibleValueProviders.TimestampFields.class)));
   }



   /*******************************************************************************
    ** The enum possible-value sources (role, analyzer, documents choice,
    ** config source).
    *******************************************************************************/
   public static List<QPossibleValueSource> buildEnumPossibleValueSources(QuickSearchQBitConfig config)
   {
      return (List.of(
         QPossibleValueSource.newForEnum(config.applyTablePrefix(FIELD_ROLE_PVS), QuickSearchFieldRole.values()),
         QPossibleValueSource.newForEnum(config.applyTablePrefix(FIELD_ANALYZER_PVS), QuickSearchFieldAnalyzer.values()),
         QPossibleValueSource.newForEnum(config.applyTablePrefix(DOCUMENTS_CHOICE_PVS), QuickSearchDocumentsChoice.values()),
         new QPossibleValueSource()
            .withName(config.applyTablePrefix(CONFIG_SOURCE_PVS))
            .withType(QPossibleValueSourceType.ENUM)
            .withEnumValues(List.of(
               new QPossibleValue<>(QuickSearchIndex.SOURCE_CODE, "Code default"),
               new QPossibleValue<>(QuickSearchIndex.SOURCE_OWNER, "Added by owner")))));
   }



   /*******************************************************************************
    ** Add what quick_search_index refers to (its field, settings and tombstone
    ** tables, join, field-list widget and possible-value sources) to an
    ** instance whose tables are built with the helpers instead of produced by
    ** the QBit (tests, hand-assembled instances). Anything already there is
    ** kept.
    *******************************************************************************/
   public static void addCompanionMetaData(QInstance qInstance, QuickSearchQBitConfig config)
   {
      for(QTableMetaData table : List.of(buildIndexFieldTable(config), buildSettingsTable(config), buildTombstoneTable(config)))
      {
         if(qInstance.getTable(table.getName()) == null)
         {
            qInstance.addTable(table);
         }
      }
      QJoinMetaData join = buildIndexFieldsJoin(config);
      if(qInstance.getJoin(join.getName()) == null)
      {
         qInstance.addJoin(join);
      }
      QWidgetMetaData widget = buildIndexFieldsWidget(config);
      if(qInstance.getWidget(widget.getName()) == null)
      {
         qInstance.addWidget(widget);
      }

      List<QPossibleValueSource> sources = new ArrayList<>(buildCustomPossibleValueSources(config));
      sources.addAll(buildEnumPossibleValueSources(config));
      sources.add(new QPossibleValueSource()
         .withName(config.applyTablePrefix(QuickSearchSearchableTablePossibleValueSourceProducer.NAME))
         .withType(QPossibleValueSourceType.CUSTOM)
         .withIdType(QFieldType.STRING)
         .withCustomCodeReference(new QCodeReference(QuickSearchTablePossibleValueProviders.SearchableTables.class)));
      for(QPossibleValueSource source : sources)
      {
         if(qInstance.getPossibleValueSource(source.getName()) == null)
         {
            qInstance.addPossibleValueSource(source);
         }
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static QPossibleValueSource custom(String name, Class<?> provider)
   {
      return (new QPossibleValueSource()
         .withName(name)
         .withType(QPossibleValueSourceType.CUSTOM)
         .withIdType(QFieldType.STRING)
         .withCustomCodeReference(new QCodeReference(provider)));
   }

}

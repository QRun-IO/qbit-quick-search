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


import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.model.QuickSearchConfigTombstone;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFieldRole;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexField;
import com.kingsrook.qbits.quicksearch.model.QuickSearchSettings;
import com.kingsrook.qbits.quicksearch.processes.AbstractIndexingStep;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Self-priming: seeds the config records from the code defaults
 ** (@QuickSearchable / @QuickSearchField entities and searchableTables), on
 ** startup and until it has succeeded once per runtime.
 **
 ** Precedence (the records win; code only fills in what is missing):
 ** - A table code declares that has no quick_search_index row, and no
 **   tombstone, gets a row (source CODE, enabled per enabledByDefault) and
 **   field rows, and is queued for its first build (status PENDING_BUILD,
 **   which the next basepull runs). A tombstoned table is never re-added.
 ** - On a row an admin has not edited (ownerEdited false), the basepull
 **   interval and timestamp field follow code. enabled is set only when the
 **   row is created, never afterwards.
 ** - A field code declares that has no row, and no tombstone, is added; a
 **   field row an admin has not edited follows code's weight and label flag.
 **   Either change queues a rebuild of the table. Field rows an admin added
 **   or edited are never changed, and nothing is ever deleted by priming:
 **   removing an annotation leaves its rows for the admin to remove.
 ** - Rows from before 1.1.0-RC.3 (no field rows yet) get their field rows
 **   without a rebuild when the index was already built with those fields.
 **
 ** All writes are system writes (QuickSearchConfigWrites), so they are not
 ** taken for an admin's edits. The outcome is written to quick_search_settings
 ** (lastPrimingSummary), which the Quick Search home shows.
 *******************************************************************************/
public class QuickSearchConfigPrimer
{
   private static final QLogger LOG = QLogger.getLogger(QuickSearchConfigPrimer.class);

   private final QuickSearchRuntime    runtime;
   private final QuickSearchQBitConfig config;



   /*******************************************************************************
    ** What priming did.
    **
    ** @param newTables     labels of the tables added (and queued to build)
    ** @param newFields     field rows added
    ** @param updatedFields field rows updated to code's defaults
    ** @param updatedTables index rows updated to code's defaults
    ** @param rebuilds      labels of existing tables queued to rebuild
    ** @param skipped       code tables or fields skipped for a tombstone
    *******************************************************************************/
   public record PrimingReport(List<String> newTables, int newFields, int updatedFields, int updatedTables, List<String> rebuilds, int skipped)
   {
      /*******************************************************************************
       ** Whether priming changed anything.
       *******************************************************************************/
      public boolean hasChanges()
      {
         return (!newTables.isEmpty() || newFields > 0 || updatedFields > 0 || updatedTables > 0 || !rebuilds.isEmpty());
      }



      /*******************************************************************************
       ** The one-line summary for the Quick Search home.
       *******************************************************************************/
      public String summary()
      {
         List<String> parts = new ArrayList<>();
         if(!newTables.isEmpty())
         {
            parts.add("New: " + newTables.size() + (newTables.size() == 1 ? " table" : " tables") + " added and building (" + String.join(", ", newTables) + ")");
         }
         if(newFields > 0 || updatedFields > 0)
         {
            parts.add(newFields + " field" + (newFields == 1 ? "" : "s") + " added, " + updatedFields + " updated from code defaults");
         }
         if(!rebuilds.isEmpty())
         {
            parts.add("rebuilding " + String.join(", ", rebuilds));
         }
         if(updatedTables > 0)
         {
            parts.add(updatedTables + " index setting" + (updatedTables == 1 ? "" : "s") + " updated from code defaults");
         }
         return (parts.isEmpty() ? "No changes: the records match the code defaults." : String.join("; ", parts) + ".");
      }
   }



   /*******************************************************************************
    ** Constructor
    *******************************************************************************/
   public QuickSearchConfigPrimer(QuickSearchRuntime runtime)
   {
      this.runtime = runtime;
      this.config  = runtime.getConfig();
   }



   /*******************************************************************************
    ** Seed the records from the code defaults. Throws when the config tables
    ** cannot be read or written (the caller retries later).
    *******************************************************************************/
   public PrimingReport prime() throws QException
   {
      PrimingReport report = QuickSearchConfigWrites.asSystem(this::doPrime);
      if(report.hasChanges())
      {
         LOG.info("Quick Search primed its config records: " + report.summary());
      }
      return (report);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private PrimingReport doPrime() throws QException
   {
      QInstance qInstance  = QContext.getQInstance();
      String    indexTable = config.getQuickSearchIndexTableName();
      String    fieldTable = config.applyTablePrefix(QuickSearchIndexField.TABLE_NAME);

      Set<String> tableTombstones = new HashSet<>();
      Set<String> fieldTombstones = new HashSet<>();
      String      tombstoneTable  = config.applyTablePrefix(QuickSearchConfigTombstone.TABLE_NAME);
      if(qInstance.getTable(tombstoneTable) != null)
      {
         for(QRecord tombstone : queryAll(tombstoneTable))
         {
            String fieldName = tombstone.getValueString("fieldName");
            if(fieldName == null)
            {
               tableTombstones.add(tombstone.getValueString("tableName"));
            }
            else
            {
               fieldTombstones.add(tombstone.getValueString("tableName") + "." + fieldName);
            }
         }
      }

      Map<String, QRecord> rowsByTable = new LinkedHashMap<>();
      for(QRecord row : queryAll(indexTable))
      {
         rowsByTable.putIfAbsent(row.getValueString("tableName"), row);
      }
      Map<Integer, Map<String, QRecord>> fieldsByIndex = new HashMap<>();
      for(QRecord fieldRow : queryAll(fieldTable))
      {
         fieldsByIndex.computeIfAbsent(fieldRow.getValueInteger("quickSearchIndexId"), k -> new LinkedHashMap<>()).putIfAbsent(fieldRow.getValueString("fieldName"), fieldRow);
      }

      List<String> newTables     = new ArrayList<>();
      List<String> rebuilds      = new ArrayList<>();
      int          newFields     = 0;
      int          updatedFields = 0;
      int          updatedTables = 0;
      int          skipped       = 0;

      for(QuickSearchableTableConfig code : runtime.getCodeTables())
      {
         String         tableName = code.getTableName();
         QTableMetaData table     = qInstance.getTable(tableName);
         if(table == null)
         {
            LOG.warn("A table declared searchable in code is not in the instance; it is not primed", logPair("tableName", tableName));
            continue;
         }
         if(tableTombstones.contains(tableName))
         {
            skipped++;
            continue;
         }

         List<QRecord> codeFields = new ArrayList<>();
         List<String>  fieldNames = CollectionUtils.nonNullList(code.getSearchableFields());
         for(int i = 0; i < fieldNames.size(); i++)
         {
            String fieldName = fieldNames.get(i);
            if(fieldTombstones.contains(tableName + "." + fieldName))
            {
               skipped++;
               continue;
            }
            if(!QuickSearchFieldRules.isIndexable(table, fieldName))
            {
               LOG.warn("A field declared searchable in code cannot be indexed; it is not primed: " + QuickSearchFieldRules.whyNotIndexable(table, fieldName),
                  logPair("tableName", tableName), logPair("fieldName", fieldName));
               continue;
            }
            codeFields.add(codeFieldRow(code, fieldName, i));
         }

         QRecord row = rowsByTable.get(tableName);
         if(row == null)
         {
            boolean enabled = !Boolean.FALSE.equals(code.getEnabledByDefault());
            QRecord inserted = insertOne(indexTable, new QRecord()
               .withValue("tableName", tableName)
               .withValue("enabled", enabled)
               .withValue("basepullIntervalMinutes", code.getBasepullIntervalMinutes())
               .withValue("basepullTimestampField", code.getBasepullTimestampField())
               .withValue("source", QuickSearchIndex.SOURCE_CODE)
               .withValue("ownerEdited", false)
               .withValue("realTimeErrorCount", 0)
               .withValue("status", enabled ? AbstractIndexingStep.STATUS_PENDING_BUILD : AbstractIndexingStep.STATUS_ACTIVE));
            Integer indexId = inserted.getValueInteger("id");
            codeFields.forEach(f -> f.setValue("quickSearchIndexId", indexId));
            insertAll(fieldTable, codeFields);
            newFields += codeFields.size();
            newTables.add(QuickSearchFieldRules.label(table));
            continue;
         }

         Integer                   indexId = row.getValueInteger("id");
         Map<String, Serializable> updates = new LinkedHashMap<>();
         if(row.getValueString("source") == null)
         {
            updates.put("source", QuickSearchIndex.SOURCE_CODE);
         }
         if(!Boolean.TRUE.equals(row.getValueBoolean("ownerEdited")))
         {
            if(!Objects.equals(row.getValueInteger("basepullIntervalMinutes"), code.getBasepullIntervalMinutes()) && code.getBasepullIntervalMinutes() != null)
            {
               updates.put("basepullIntervalMinutes", code.getBasepullIntervalMinutes());
            }
            if(!Objects.equals(row.getValueString("basepullTimestampField"), code.getBasepullTimestampField()))
            {
               updates.put("basepullTimestampField", code.getBasepullTimestampField());
            }
         }
         if(updates.keySet().stream().anyMatch(k -> !"source".equals(k)))
         {
            updatedTables++;
         }

         Map<String, QRecord> existingFields = fieldsByIndex.getOrDefault(indexId, Map.of());
         boolean              migration      = existingFields.isEmpty();
         boolean              fieldsChanged  = false;
         List<QRecord>        toInsert       = new ArrayList<>();
         List<QRecord>        toUpdate       = new ArrayList<>();
         for(QRecord codeField : codeFields)
         {
            QRecord existing = existingFields.get(codeField.getValueString("fieldName"));
            if(existing == null)
            {
               codeField.setValue("quickSearchIndexId", indexId);
               toInsert.add(codeField);
               fieldsChanged = true;
            }
            else if(!Boolean.TRUE.equals(existing.getValueBoolean("ownerEdited"))
               && (!Objects.equals(existing.getValueInteger("weight"), codeField.getValueInteger("weight"))
               || !Objects.equals(Boolean.TRUE.equals(existing.getValueBoolean("includeLabel")), codeField.getValueBoolean("includeLabel"))))
            {
               toUpdate.add(new QRecord().withValue("id", existing.getValue("id"))
                  .withValue("weight", codeField.getValueInteger("weight"))
                  .withValue("includeLabel", codeField.getValueBoolean("includeLabel")));
               fieldsChanged = true;
            }
         }
         insertAll(fieldTable, toInsert);
         updateAll(fieldTable, toUpdate);
         newFields += toInsert.size();
         updatedFields += toUpdate.size();

         if(fieldsChanged && needsRebuild(row, code, migration))
         {
            updates.put("status", AbstractIndexingStep.STATUS_PENDING_BUILD);
            rebuilds.add(QuickSearchFieldRules.label(table));
         }

         if(!updates.isEmpty())
         {
            QRecord update = new QRecord().withValue("id", indexId);
            updates.forEach(update::setValue);
            updateAll(indexTable, List.of(update));
         }
      }

      PrimingReport report = new PrimingReport(newTables, newFields, updatedFields, updatedTables, rebuilds, skipped);
      writeSettings(report);
      return (report);
   }



   /*******************************************************************************
    ** Whether a primed field change needs the table rebuilt: not when it is
    ** disabled or already building, and not for rows from before field
    ** records when the index was already built with exactly code's fields
    ** (or never built: its first basepull reads everything anyway).
    *******************************************************************************/
   private static boolean needsRebuild(QRecord row, QuickSearchableTableConfig code, boolean migration)
   {
      String status = row.getValueString("status");
      if(Boolean.FALSE.equals(row.getValueBoolean("enabled"))
         || AbstractIndexingStep.STATUS_PENDING_BUILD.equals(status)
         || AbstractIndexingStep.STATUS_BUILDING.equals(status)
         || AbstractIndexingStep.STATUS_REBUILDING.equals(status))
      {
         return (false);
      }
      if(migration)
      {
         String built = row.getValueString("searchableFieldsJson");
         return (built != null && !built.equals(AbstractIndexingStep.buildSearchableFieldsJson(code)));
      }
      return (true);
   }



   /*******************************************************************************
    ** A field row from code's defaults (role BODY; analyzer and snippet left
    ** to their defaults).
    *******************************************************************************/
   private static QRecord codeFieldRow(QuickSearchableTableConfig code, String fieldName, int sortOrder)
   {
      Integer weight       = code.getFieldWeights() == null ? null : code.getFieldWeights().get(fieldName);
      Boolean includeLabel = code.getFieldIncludeLabels() == null ? null : code.getFieldIncludeLabels().get(fieldName);
      return (new QRecord()
         .withValue("fieldName", fieldName)
         .withValue("weight", weight == null ? 1 : weight)
         .withValue("includeLabel", Boolean.TRUE.equals(includeLabel))
         .withValue("role", QuickSearchFieldRole.BODY.name())
         .withValue("sortOrder", (sortOrder + 1) * 10)
         .withValue("source", QuickSearchIndexField.SOURCE_CODE)
         .withValue("ownerEdited", false));
   }



   /*******************************************************************************
    ** Create the settings row when missing, and record the priming outcome.
    *******************************************************************************/
   private void writeSettings(PrimingReport report) throws QException
   {
      String settingsTable = config.applyTablePrefix(QuickSearchSettings.TABLE_NAME);
      if(QContext.getQInstance().getTable(settingsTable) == null)
      {
         return;
      }

      QRecord values = new QRecord().withValue("lastPrimedAt", Instant.now());
      if(report.hasChanges())
      {
         values.withValue("lastPrimingSummary", report.summary()).withValue("lastPrimingNewTables", report.newTables().size());
      }

      List<QRecord> rows = queryAll(settingsTable);
      if(rows.isEmpty())
      {
         values.withValue("id", QuickSearchSettings.SETTINGS_ID).withValue("indexingPaused", false);
         if(!report.hasChanges())
         {
            values.withValue("lastPrimingSummary", report.summary()).withValue("lastPrimingNewTables", 0);
         }
         insertOne(settingsTable, values);
      }
      else
      {
         values.withValue("id", rows.get(0).getValue("id"));
         updateAll(settingsTable, List.of(values));
      }
   }



   /*******************************************************************************
    ** Every row of a table.
    *******************************************************************************/
   public static List<QRecord> queryAll(String tableName) throws QException
   {
      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(tableName);
      queryInput.setFilter(new QQueryFilter());
      return (CollectionUtils.nonNullList(new QueryAction().execute(queryInput).getRecords()));
   }



   /*******************************************************************************
    ** Insert one row, failing on any record error.
    *******************************************************************************/
   public static QRecord insertOne(String tableName, QRecord record) throws QException
   {
      List<QRecord> inserted = insertAll(tableName, List.of(record));
      if(inserted.isEmpty())
      {
         throw (new QException("Insert into " + tableName + " returned no record"));
      }
      return (inserted.get(0));
   }



   /*******************************************************************************
    ** Insert rows, failing on any record error.
    *******************************************************************************/
   public static List<QRecord> insertAll(String tableName, List<QRecord> records) throws QException
   {
      if(records.isEmpty())
      {
         return (List.of());
      }
      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(tableName);
      insertInput.setRecords(records);
      InsertOutput  output   = new InsertAction().execute(insertInput);
      List<QRecord> inserted = CollectionUtils.nonNullList(output.getRecords());
      for(QRecord record : inserted)
      {
         if(CollectionUtils.nullSafeHasContents(record.getErrors()))
         {
            throw (new QException("Could not insert into " + tableName + ": " + record.getErrors().get(0).getMessage()));
         }
      }
      return (inserted);
   }



   /*******************************************************************************
    ** Update rows, failing on any record error.
    *******************************************************************************/
   public static void updateAll(String tableName, List<QRecord> records) throws QException
   {
      if(records.isEmpty())
      {
         return;
      }
      UpdateInput updateInput = new UpdateInput();
      updateInput.setTableName(tableName);
      updateInput.setRecords(records);
      for(QRecord record : CollectionUtils.nonNullList(new UpdateAction().execute(updateInput).getRecords()))
      {
         if(CollectionUtils.nullSafeHasContents(record.getErrors()))
         {
            throw (new QException("Could not update " + tableName + ": " + record.getErrors().get(0).getMessage()));
         }
      }
   }

}

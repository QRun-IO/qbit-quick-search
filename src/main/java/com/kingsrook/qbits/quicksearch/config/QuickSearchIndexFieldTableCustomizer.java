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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizerInterface;
import com.kingsrook.qqq.backend.core.actions.tables.GetAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.get.GetInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.statusmessages.BadInputStatusMessage;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFieldAnalyzer;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFieldRole;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexField;
import com.kingsrook.qbits.quicksearch.processes.AbstractIndexingStep;


/*******************************************************************************
 ** Customizer of quick_search_index_field, the searchable fields.
 **
 ** An admin's insert or edit is validated: the index row exists, the field
 ** exists on its table and can be indexed (not hidden, not isSensitive, not a
 ** password, blob or boolean), it is not already a field of that index, the
 ** weight is 1 to 1000, and the role and analyzer are known. The row is marked
 ** ownerEdited (an insert also source OWNER), so priming never overwrites it.
 **
 ** Changes take effect for searches and real-time indexing at once (weights
 ** are applied at query time). After an admin's change other than the sort
 ** order, the index row is marked NEEDS_REINDEX, so the documents indexed
 ** before can be brought in line: Apply Search Index Changes previews the
 ** change and rebuilds the table. Removing a field leaves a tombstone, and
 ** adding it back clears it. Every change drops the cached config.
 *******************************************************************************/
public class QuickSearchIndexFieldTableCustomizer implements TableCustomizerInterface
{
   public static final int MAX_WEIGHT = 1000;

   public static final List<String> OWNER_FIELDS    = List.of("fieldName", "weight", "role", "analyzer", "includeInSnippet", "includeLabel", "sortOrder");
   public static final List<String> DOCUMENT_FIELDS = List.of("fieldName", "weight", "role", "analyzer", "includeInSnippet", "includeLabel");



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> preInsert(InsertInput insertInput, List<QRecord> records, boolean isPreview) throws QException
   {
      if(QuickSearchConfigWrites.isSystemWrite())
      {
         return (records);
      }

      Set<String> inBatch = new HashSet<>();
      for(QRecord record : records)
      {
         Integer indexId = record.getValueInteger("quickSearchIndexId");
         QRecord index   = indexRow(indexId);
         if(index == null)
         {
            record.addError(new BadInputStatusMessage("Choose the search index this field belongs to"));
            continue;
         }

         String fieldName = record.getValueString("fieldName");
         if(!validateField(record, index, fieldName, null) || !inBatch.add(indexId + "." + fieldName))
         {
            if(CollectionUtils.nullSafeIsEmpty(record.getErrors()))
            {
               record.addError(new BadInputStatusMessage("Field [" + fieldName + "] is listed twice"));
            }
            continue;
         }

         if(record.getValue("weight") == null)
         {
            record.setValue("weight", 1);
         }
         if(!StringUtils.hasContent(record.getValueString("role")))
         {
            record.setValue("role", QuickSearchFieldRole.BODY.name());
         }
         if(record.getValue("includeLabel") == null)
         {
            record.setValue("includeLabel", false);
         }
         if(record.getValue("sortOrder") == null)
         {
            record.setValue("sortOrder", nextSortOrder(indexId));
         }
         validateOptions(record);
         record.setValue("source", QuickSearchIndexField.SOURCE_OWNER);
         record.setValue("ownerEdited", true);
      }
      return (records);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> preUpdate(UpdateInput updateInput, List<QRecord> records, boolean isPreview, Optional<List<QRecord>> oldRecordList) throws QException
   {
      if(QuickSearchConfigWrites.isSystemWrite())
      {
         return (records);
      }

      Map<Serializable, QRecord> oldById = oldRecordListToMap("id", oldRecordList).orElse(Map.of());
      for(QRecord record : records)
      {
         QRecord old = oldById.get(record.getValue("id"));
         if(old == null)
         {
            continue;
         }
         if(record.getValues().containsKey("quickSearchIndexId") && !Objects.equals(record.getValueInteger("quickSearchIndexId"), old.getValueInteger("quickSearchIndexId")))
         {
            record.addError(new BadInputStatusMessage("A field cannot be moved to another search index; remove it and add it there"));
            continue;
         }
         if(!changes(record, old, OWNER_FIELDS))
         {
            continue;
         }
         if(record.getValues().containsKey("fieldName") && !Objects.equals(record.getValueString("fieldName"), old.getValueString("fieldName")))
         {
            validateField(record, indexRow(old.getValueInteger("quickSearchIndexId")), record.getValueString("fieldName"), old.getValue("id"));
         }
         validateOptions(record);
         record.setValue("ownerEdited", true);
      }
      return (records);
   }



   /*******************************************************************************
    ** Validate a field name against its index's table and the index's other
    ** fields (excludeId: the row being edited). Adds an error and returns false
    ** when it is not acceptable.
    *******************************************************************************/
   private static boolean validateField(QRecord record, QRecord index, String fieldName, Serializable excludeId) throws QException
   {
      if(index == null)
      {
         record.addError(new BadInputStatusMessage("The search index of this field no longer exists"));
         return (false);
      }
      String         tableName = index.getValueString("tableName");
      QTableMetaData table     = QContext.getQInstance().getTable(tableName);
      String         problem   = QuickSearchFieldRules.whyNotIndexable(table, fieldName);
      if(problem != null)
      {
         record.addError(new BadInputStatusMessage("Cannot index " + fieldName + ": " + problem));
         return (false);
      }

      QQueryFilter filter = new QQueryFilter(new QFilterCriteria("quickSearchIndexId", QCriteriaOperator.EQUALS, index.getValue("id")))
         .withCriteria(new QFilterCriteria("fieldName", QCriteriaOperator.EQUALS, fieldName));
      if(excludeId != null)
      {
         filter.withCriteria(new QFilterCriteria("id", QCriteriaOperator.NOT_EQUALS, excludeId));
      }
      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(fieldTableName());
      queryInput.setFilter(filter);
      if(CollectionUtils.nullSafeHasContents(new QueryAction().execute(queryInput).getRecords()))
      {
         record.addError(new BadInputStatusMessage(fieldName + " is already a searchable field of " + tableName));
         return (false);
      }
      return (true);
   }



   /*******************************************************************************
    ** Weight 1 to MAX_WEIGHT; a known role and analyzer.
    *******************************************************************************/
   private static void validateOptions(QRecord record)
   {
      Integer weight = record.getValueInteger("weight");
      if(record.getValues().containsKey("weight") && (weight == null || weight < 1 || weight > MAX_WEIGHT))
      {
         record.addError(new BadInputStatusMessage("Weight must be from 1 to " + MAX_WEIGHT));
      }
      String role = record.getValueString("role");
      if(StringUtils.hasContent(role) && QuickSearchFieldRole.fromName(role, null) == null)
      {
         record.addError(new BadInputStatusMessage("Unknown role [" + role + "]"));
      }
      String analyzer = record.getValueString("analyzer");
      if(StringUtils.hasContent(analyzer) && QuickSearchFieldAnalyzer.fromName(analyzer, null) == null)
      {
         record.addError(new BadInputStatusMessage("Unknown analyzer [" + analyzer + "]"));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> postInsert(InsertInput insertInput, List<QRecord> records) throws QException
   {
      if(!QuickSearchConfigWrites.isSystemWrite())
      {
         for(QRecord record : succeeded(records))
         {
            QRecord index = indexRow(record.getValueInteger("quickSearchIndexId"));
            if(index != null)
            {
               QuickSearchTombstones.remove(index.getValueString("tableName"), record.getValueString("fieldName"));
               markNeedsReindex(index);
            }
         }
      }
      QuickSearchIndexTableCustomizer.invalidate();
      return (records);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> postUpdate(UpdateInput updateInput, List<QRecord> records, Optional<List<QRecord>> oldRecordList) throws QException
   {
      if(!QuickSearchConfigWrites.isSystemWrite())
      {
         Map<Serializable, QRecord> oldById = oldRecordListToMap("id", oldRecordList).orElse(Map.of());
         Map<Integer, QRecord>      marked  = new HashMap<>();
         for(QRecord record : succeeded(records))
         {
            QRecord old = oldById.get(record.getValue("id"));
            if(old != null && changes(record, old, DOCUMENT_FIELDS))
            {
               Integer indexId = old.getValueInteger("quickSearchIndexId");
               if(!marked.containsKey(indexId))
               {
                  QRecord index = indexRow(indexId);
                  marked.put(indexId, index);
                  markNeedsReindex(index);
               }
            }
         }
      }
      QuickSearchIndexTableCustomizer.invalidate();
      return (records);
   }



   /*******************************************************************************
    ** An admin's removal leaves a tombstone (unless the whole index is going).
    *******************************************************************************/
   @Override
   public List<QRecord> postDelete(DeleteInput deleteInput, List<QRecord> records) throws QException
   {
      if(!QuickSearchConfigWrites.isSystemWrite())
      {
         for(QRecord record : succeeded(records))
         {
            QRecord index = indexRow(record.getValueInteger("quickSearchIndexId"));
            if(index != null)
            {
               QuickSearchTombstones.add(index.getValueString("tableName"), record.getValueString("fieldName"), "removed by an admin");
               markNeedsReindex(index);
            }
         }
      }
      QuickSearchIndexTableCustomizer.invalidate();
      return (records);
   }



   /*******************************************************************************
    ** Mark the index NEEDS_REINDEX (a system write), unless a build is already
    ** queued or running.
    *******************************************************************************/
   static void markNeedsReindex(QRecord index) throws QException
   {
      if(index == null)
      {
         return;
      }
      String status = index.getValueString("status");
      if(AbstractIndexingStep.STATUS_PENDING_BUILD.equals(status) || AbstractIndexingStep.STATUS_BUILDING.equals(status)
         || AbstractIndexingStep.STATUS_REBUILDING.equals(status) || AbstractIndexingStep.STATUS_NEEDS_REINDEX.equals(status))
      {
         return;
      }
      QuickSearchConfigWrites.asSystem(() ->
      {
         QuickSearchConfigPrimer.updateAll(QuickSearchTablePossibleValueProviders.config().getQuickSearchIndexTableName(),
            List.of(new QRecord().withValue("id", index.getValue("id")).withValue("status", AbstractIndexingStep.STATUS_NEEDS_REINDEX)));
         return (null);
      });
   }



   /*******************************************************************************
    ** Whether the update changes any of the given fields.
    *******************************************************************************/
   private static boolean changes(QRecord record, QRecord old, List<String> fields)
   {
      for(String field : fields)
      {
         if(record.getValues().containsKey(field) && !Objects.equals(normalize(record.getValue(field)), normalize(old.getValue(field))))
         {
            return (true);
         }
      }
      return (false);
   }



   /*******************************************************************************
    ** Compare values as strings, blank as null.
    *******************************************************************************/
   private static String normalize(Serializable value)
   {
      String s = value == null ? null : String.valueOf(value);
      return (StringUtils.hasContent(s) ? s : null);
   }



   /*******************************************************************************
    ** The records without errors.
    *******************************************************************************/
   private static List<QRecord> succeeded(List<QRecord> records)
   {
      return (CollectionUtils.nonNullList(records).stream().filter(r -> CollectionUtils.nullSafeIsEmpty(r.getErrors())).toList());
   }



   /*******************************************************************************
    ** The index row, or null.
    *******************************************************************************/
   static QRecord indexRow(Integer indexId) throws QException
   {
      if(indexId == null)
      {
         return (null);
      }
      GetInput getInput = new GetInput(QuickSearchTablePossibleValueProviders.config().getQuickSearchIndexTableName());
      getInput.setPrimaryKey(indexId);
      return (new GetAction().execute(getInput).getRecord());
   }



   /*******************************************************************************
    ** One past the index's highest sortOrder, in steps of 10.
    *******************************************************************************/
   private static Integer nextSortOrder(Integer indexId) throws QException
   {
      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(fieldTableName());
      queryInput.setFilter(new QQueryFilter(new QFilterCriteria("quickSearchIndexId", QCriteriaOperator.EQUALS, indexId)));
      int max = 0;
      for(QRecord row : CollectionUtils.nonNullList(new QueryAction().execute(queryInput).getRecords()))
      {
         max = Math.max(max, row.getValueInteger("sortOrder") == null ? 0 : row.getValueInteger("sortOrder"));
      }
      return (max + 10);
   }



   /*******************************************************************************
    ** The field table's name.
    *******************************************************************************/
   private static String fieldTableName()
   {
      QuickSearchQBitConfig config = QuickSearchTablePossibleValueProviders.config();
      return (config.applyTablePrefix(QuickSearchIndexField.TABLE_NAME));
   }

}

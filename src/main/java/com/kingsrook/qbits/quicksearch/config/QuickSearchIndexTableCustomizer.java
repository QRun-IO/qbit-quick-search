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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizerInterface;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.statusmessages.BadInputStatusMessage;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.processes.AbstractIndexingStep;


/*******************************************************************************
 ** Customizer of quick_search_index, the searchable-table registry.
 **
 ** Rows are added by Add Search Index, which validates the table (eligible,
 ** readable, not yet indexed), previews and queues the first build, and by
 ** priming; the generic insert is turned off. Any other insert is marked
 ** source OWNER and ownerEdited, and its schedule validated. An admin's edit of the
 ** enabled flag, interval or timestamp field marks the row ownerEdited, so
 ** priming never overwrites it; turning enabled back on queues a build. Any
 ** change drops the cached config, so it takes effect at once on this server.
 ** The QBit's own writes (QuickSearchConfigWrites) pass straight through.
 *******************************************************************************/
public class QuickSearchIndexTableCustomizer implements TableCustomizerInterface
{
   public static final List<String> CONFIG_FIELDS = List.of("enabled", "basepullIntervalMinutes", "basepullTimestampField");



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

      ////////////////////////////////////////////////////////////////////////////
      // rows are added by Add Search Index (which validates and previews) and  //
      // by priming; the generic insert is turned off. Anything else inserting  //
      // a row is the owner's doing: mark it so priming never overwrites it     //
      ////////////////////////////////////////////////////////////////////////////
      for(QRecord record : records)
      {
         if(record.getValue("source") == null)
         {
            record.setValue("source", QuickSearchIndex.SOURCE_OWNER);
         }
         if(record.getValue("ownerEdited") == null)
         {
            record.setValue("ownerEdited", true);
         }
         String tableName = record.getValueString("tableName");
         if(QContext.getQInstance().getTable(tableName) != null)
         {
            validateSchedule(record, tableName);
         }
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
         if(old == null || !changesConfig(record, old))
         {
            continue;
         }

         validateSchedule(record, old.getValueString("tableName"));
         record.setValue("ownerEdited", true);

         boolean wasEnabled = !Boolean.FALSE.equals(old.getValueBoolean("enabled"));
         boolean nowEnabled = !Boolean.FALSE.equals(record.getValues().containsKey("enabled") ? record.getValueBoolean("enabled") : old.getValueBoolean("enabled"));
         String  status     = old.getValueString("status");
         if(!wasEnabled && nowEnabled && !AbstractIndexingStep.STATUS_REBUILDING.equals(status) && !AbstractIndexingStep.STATUS_BUILDING.equals(status))
         {
            record.setValue("status", AbstractIndexingStep.STATUS_PENDING_BUILD);
         }
      }
      return (records);
   }



   /*******************************************************************************
    ** Whether the update changes the enabled flag, interval or timestamp field.
    *******************************************************************************/
   static boolean changesConfig(QRecord record, QRecord old)
   {
      for(String field : CONFIG_FIELDS)
      {
         if(record.getValues().containsKey(field) && !Objects.equals(normalize(record.getValue(field)), normalize(old.getValue(field))))
         {
            return (true);
         }
      }
      return (false);
   }



   /*******************************************************************************
    ** Compare values as strings, blank as null (forms send "" and "true").
    *******************************************************************************/
   private static String normalize(Serializable value)
   {
      String s = value == null ? null : String.valueOf(value);
      return (StringUtils.hasContent(s) ? s : null);
   }



   /*******************************************************************************
    ** A timestamp field must be a date-time field of the table; the interval
    ** cannot be negative.
    *******************************************************************************/
   private static void validateSchedule(QRecord record, String tableName)
   {
      String timestampField = record.getValueString("basepullTimestampField");
      if(StringUtils.hasContent(timestampField) && !QuickSearchFieldRules.isTimestampField(QContext.getQInstance().getTable(tableName), timestampField))
      {
         record.addError(new BadInputStatusMessage("Timestamp field [" + timestampField + "] is not a date-time field of " + tableName));
      }
      Integer interval = record.getValueInteger("basepullIntervalMinutes");
      if(record.getValues().containsKey("basepullIntervalMinutes") && interval != null && interval < 0)
      {
         record.addError(new BadInputStatusMessage("Update interval cannot be negative (0 means every update)"));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> postInsert(InsertInput insertInput, List<QRecord> records) throws QException
   {
      invalidate();
      return (records);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> postUpdate(UpdateInput updateInput, List<QRecord> records, Optional<List<QRecord>> oldRecordList) throws QException
   {
      invalidate();
      return (records);
   }



   /*******************************************************************************
    ** An admin's delete leaves a tombstone, so code defaults do not add the
    ** table back.
    *******************************************************************************/
   @Override
   public List<QRecord> postDelete(DeleteInput deleteInput, List<QRecord> records) throws QException
   {
      if(!QuickSearchConfigWrites.isSystemWrite())
      {
         for(QRecord record : CollectionUtils.nonNullList(records))
         {
            if(CollectionUtils.nullSafeIsEmpty(record.getErrors()) && record.getValueString("tableName") != null)
            {
               QuickSearchTombstones.add(record.getValueString("tableName"), null, "removed by an admin");
            }
         }
      }
      invalidate();
      return (records);
   }



   /*******************************************************************************
    ** Drop the runtime's cached config.
    *******************************************************************************/
   static void invalidate()
   {
      QuickSearchRuntime runtime = QuickSearchRuntime.get();
      if(runtime != null)
      {
         runtime.invalidateConfig();
      }
   }

}

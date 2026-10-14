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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizerInterface;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Customizer of quick_search_settings. Pausing stamps pausedAt and pausedBy
 ** (the Quick Search home shows "Paused since ... by ..."); resuming clears
 ** them and hands the changes deferred while paused (DEFERRED failed events)
 ** to the next basepull (PENDING). Every change drops the cached settings, so
 ** a pause takes effect at once on this server and within
 ** QuickSearchConfigStore.CACHE_TTL_MILLIS on the others.
 *******************************************************************************/
public class QuickSearchSettingsTableCustomizer implements TableCustomizerInterface
{
   private static final QLogger LOG = QLogger.getLogger(QuickSearchSettingsTableCustomizer.class);



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> preUpdate(UpdateInput updateInput, List<QRecord> records, boolean isPreview, Optional<List<QRecord>> oldRecordList) throws QException
   {
      Map<Serializable, QRecord> oldById = oldRecordListToMap("id", oldRecordList).orElse(Map.of());
      for(QRecord record : records)
      {
         QRecord old = oldById.get(record.getValue("id"));
         if(old == null || !record.getValues().containsKey("indexingPaused"))
         {
            continue;
         }
         boolean wasPaused = Boolean.TRUE.equals(old.getValueBoolean("indexingPaused"));
         boolean nowPaused = Boolean.TRUE.equals(record.getValueBoolean("indexingPaused"));
         if(!wasPaused && nowPaused)
         {
            record.setValue("pausedAt", Instant.now());
            record.setValue("pausedBy", QuickSearchConfigWrites.currentUserName());
         }
         else if(wasPaused && !nowPaused)
         {
            record.setValue("pausedAt", null);
            record.setValue("pausedBy", null);
            record.setValue("pauseReason", null);
         }
      }
      return (records);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> postUpdate(UpdateInput updateInput, List<QRecord> records, Optional<List<QRecord>> oldRecordList) throws QException
   {
      Map<Serializable, QRecord> oldById = oldRecordListToMap("id", oldRecordList).orElse(Map.of());
      for(QRecord record : records)
      {
         QRecord old = oldById.get(record.getValue("id"));
         if(old != null && CollectionUtils.nullSafeIsEmpty(record.getErrors()) && Boolean.TRUE.equals(old.getValueBoolean("indexingPaused"))
            && record.getValues().containsKey("indexingPaused") && !Boolean.TRUE.equals(record.getValueBoolean("indexingPaused")))
         {
            releaseDeferredEvents();
         }
      }
      QuickSearchIndexTableCustomizer.invalidate();
      return (records);
   }



   /*******************************************************************************
    ** Make the events deferred while paused PENDING, so the next basepull
    ** replays them. Never throws (they stay DEFERRED; a reconcile covers them).
    *******************************************************************************/
   static void releaseDeferredEvents()
   {
      try
      {
         QuickSearchQBitConfig config = QuickSearchTablePossibleValueProviders.config();
         String                table  = config.applyTablePrefix(QuickSearchFailedEvent.TABLE_NAME);
         if(QContext.getQInstance().getTable(table) == null)
         {
            return;
         }

         while(true)
         {
            QueryInput queryInput = new QueryInput();
            queryInput.setTableName(table);
            queryInput.setFilter(new QQueryFilter(new QFilterCriteria("status", QCriteriaOperator.EQUALS, QuickSearchFailedEvent.STATUS_DEFERRED)).withLimit(1000));
            List<QRecord> deferred = CollectionUtils.nonNullList(new QueryAction().execute(queryInput).getRecords());
            if(deferred.isEmpty())
            {
               return;
            }
            List<QRecord> updates = new ArrayList<>();
            deferred.forEach(r -> updates.add(new QRecord().withValue("id", r.getValue("id")).withValue("status", QuickSearchFailedEvent.STATUS_PENDING)));
            QuickSearchConfigPrimer.updateAll(table, updates);
            LOG.info("Released changes deferred while Quick Search indexing was paused", logPair("count", updates.size()));
         }
      }
      catch(Exception e)
      {
         LOG.warn("Could not release the changes deferred while indexing was paused; the next reconcile covers them", e);
      }
   }

}

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

package com.kingsrook.qbits.quicksearch.listeners;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.listeners.RecordChangeEvent;
import com.kingsrook.qqq.backend.core.actions.tables.listeners.RecordChangeListenerInterface;
import com.kingsrook.qqq.backend.core.actions.tables.listeners.RecordChangeType;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;
import com.kingsrook.qbits.quicksearch.publisher.IndexEvent;
import com.kingsrook.qbits.quicksearch.publisher.IndexEventAction;
import com.kingsrook.qbits.quicksearch.publisher.IndexEventPublisher;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Real-time indexing hook. Registered once per QInstance; applies to the
 ** tables this QBit indexes.
 **
 ** Events are built while the write's transaction is open (so updates can be
 ** re-read in full), but published only after that transaction commits, via
 ** QBackendTransaction.addAfterCommitCallback. A write without a transaction
 ** is already committed, so it publishes immediately. A rolled-back write never
 ** reaches the index.
 **
 ** This listener never fails the host's write. When publishing fails, each
 ** affected (table, record, action) is written to quickSearchFailedEvent for
 ** the scheduled basepull to replay.
 *******************************************************************************/
public class QuickSearchRecordChangeListener implements RecordChangeListenerInterface
{
   private static final QLogger LOG = QLogger.getLogger(QuickSearchRecordChangeListener.class);



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public boolean appliesTo(String tableName, RecordChangeType type)
   {
      QuickSearchRuntime runtime = QuickSearchRuntime.get();
      return (runtime != null
         && Boolean.TRUE.equals(runtime.getConfig().getEnableRealTimeIndexing())
         && runtime.getTableConfig(tableName) != null);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void onRecordsChanged(RecordChangeEvent event) throws QException
   {
      QuickSearchRuntime runtime = QuickSearchRuntime.get();
      if(runtime == null || event == null || CollectionUtils.nullSafeIsEmpty(event.getRecords()))
      {
         return;
      }

      String                     tableName   = event.getTableName();
      QuickSearchableTableConfig tableConfig = runtime.getTableConfig(tableName);
      if(tableConfig == null || !runtime.isTableEnabled(tableName))
      {
         return;
      }

      String primaryKeyField = tableConfig.getPrimaryKeyField() != null ? tableConfig.getPrimaryKeyField() : "id";

      List<IndexEvent> indexEvents  = new ArrayList<>();
      List<IndexEvent> deleteEvents = new ArrayList<>();

      switch(event.getType())
      {
         case INSERT -> indexEvents.addAll(toIndexEvents(tableName, primaryKeyField, event.getRecords()));
         case UPDATE -> indexEvents.addAll(toIndexEvents(tableName, primaryKeyField, fetchCurrentRecords(event, primaryKeyField)));
         case DELETE -> deleteEvents.addAll(toDeleteEvents(tableName, primaryKeyField, event.getRecords()));
      }

      if(indexEvents.isEmpty() && deleteEvents.isEmpty())
      {
         return;
      }

      Runnable publish = () -> publish(runtime, indexEvents, deleteEvents);

      if(event.getTransaction() != null)
      {
         event.getTransaction().addAfterCommitCallback(publish);
      }
      else
      {
         publish.run();
      }
   }



   /*******************************************************************************
    ** Publish, recording any failure durably. Never throws.
    *******************************************************************************/
   static void publish(QuickSearchRuntime runtime, List<IndexEvent> indexEvents, List<IndexEvent> deleteEvents)
   {
      IndexEventPublisher publisher;
      try
      {
         publisher = runtime.getPublisher();
      }
      catch(Exception e)
      {
         recordFailures(runtime, indexEvents, e);
         recordFailures(runtime, deleteEvents, e);
         return;
      }

      if(!indexEvents.isEmpty())
      {
         try
         {
            publisher.publishIndexEvents(indexEvents);
         }
         catch(Exception e)
         {
            recordFailures(runtime, indexEvents, e);
         }
      }

      if(!deleteEvents.isEmpty())
      {
         try
         {
            publisher.publishDeleteEvents(deleteEvents);
         }
         catch(Exception e)
         {
            recordFailures(runtime, deleteEvents, e);
         }
      }
   }



   /*******************************************************************************
    ** Write one quickSearchFailedEvent row per event.
    *******************************************************************************/
   static void recordFailures(QuickSearchRuntime runtime, List<IndexEvent> events, Exception cause)
   {
      if(events.isEmpty())
      {
         return;
      }

      String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
      LOG.warn("Real-time index publish failed; recording failed events for replay", cause,
         logPair("tableName", events.get(0).getTableName()), logPair("eventCount", events.size()));

      try
      {
         String failedEventTable = runtime.getConfig().applyPrefix(QuickSearchFailedEvent.TABLE_NAME);
         if(QContext.getQInstance() == null || QContext.getQInstance().getTable(failedEventTable) == null)
         {
            LOG.error("quickSearchFailedEvent table is not in the QInstance; failed events are lost", logPair("eventCount", events.size()));
            return;
         }

         List<QRecord> rows = new ArrayList<>();
         for(IndexEvent event : events)
         {
            rows.add(new QRecord()
               .withValue("tableName", event.getTableName())
               .withValue("recordId", event.getRecordId())
               .withValue("action", event.getAction() == null ? null : event.getAction().name())
               .withValue("errorMessage", truncate(message, 4000))
               .withValue("attempts", 0)
               .withValue("status", QuickSearchFailedEvent.STATUS_PENDING));
         }

         InsertInput insertInput = new InsertInput();
         insertInput.setTableName(failedEventTable);
         insertInput.setRecords(rows);
         new InsertAction().execute(insertInput);
      }
      catch(Exception e)
      {
         LOG.error("Could not record failed index events", e, logPair("eventCount", events.size()));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static List<IndexEvent> toIndexEvents(String tableName, String primaryKeyField, List<QRecord> records)
   {
      List<IndexEvent> events = new ArrayList<>();
      for(QRecord record : CollectionUtils.nonNullList(records))
      {
         String recordId = record.getValueString(primaryKeyField);
         if(recordId == null || CollectionUtils.nullSafeHasContents(record.getErrors()))
         {
            continue;
         }
         events.add(new IndexEvent().withAction(IndexEventAction.INDEX).withTableName(tableName).withRecordId(recordId).withRecord(record));
      }
      return (events);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static List<IndexEvent> toDeleteEvents(String tableName, String primaryKeyField, List<QRecord> records)
   {
      List<IndexEvent> events = new ArrayList<>();
      for(QRecord record : CollectionUtils.nonNullList(records))
      {
         String recordId = record.getValueString(primaryKeyField);
         if(recordId == null || CollectionUtils.nullSafeHasContents(record.getErrors()))
         {
            continue;
         }
         events.add(new IndexEvent().withAction(IndexEventAction.DELETE).withTableName(tableName).withRecordId(recordId));
      }
      return (events);
   }



   /*******************************************************************************
    ** Updated records are "as the backend returned them", which for RDBMS is the
    ** sparse input. Re-read the full rows inside the same transaction, with
    ** display values so possible-value labels are indexed.
    *******************************************************************************/
   private static List<QRecord> fetchCurrentRecords(RecordChangeEvent event, String primaryKeyField) throws QException
   {
      List<Serializable> primaryKeys = new ArrayList<>();
      for(QRecord record : event.getRecords())
      {
         Serializable primaryKey = record.getValue(primaryKeyField);
         if(primaryKey != null && CollectionUtils.nullSafeIsEmpty(record.getErrors()))
         {
            primaryKeys.add(primaryKey);
         }
      }

      if(primaryKeys.isEmpty())
      {
         return (List.of());
      }

      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(event.getTableName());
      queryInput.setTransaction(event.getTransaction());
      queryInput.setShouldGenerateDisplayValues(true);
      queryInput.setShouldTranslatePossibleValues(true);
      queryInput.setFilter(new QQueryFilter(new QFilterCriteria(primaryKeyField, QCriteriaOperator.IN, primaryKeys)));

      return (CollectionUtils.nonNullList(new QueryAction().execute(queryInput).getRecords()));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String truncate(String s, int max)
   {
      return (s == null || s.length() <= max ? s : s.substring(0, max));
   }

}

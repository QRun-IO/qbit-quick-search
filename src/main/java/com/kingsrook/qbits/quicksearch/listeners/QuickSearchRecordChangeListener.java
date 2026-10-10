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
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
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
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;
import com.kingsrook.qbits.quicksearch.processes.AbstractIndexingStep;
import com.kingsrook.qbits.quicksearch.publisher.IndexEvent;
import com.kingsrook.qbits.quicksearch.publisher.IndexEventAction;
import com.kingsrook.qbits.quicksearch.publisher.IndexEventPublisher;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Real-time indexing hook. Registered once per QInstance; applies to the
 ** tables this QBit indexes.
 **
 ** Events are built while the write's transaction is open (so inserted and
 ** updated records can be re-read in full, with display values), but
 ** published only after that transaction commits, via
 ** QBackendTransaction.addAfterCommitCallback. A write without a transaction
 ** is already committed, so it publishes immediately. A rolled-back write never
 ** reaches the index.
 **
 ** This listener never fails the host's write. When the re-read of inserted
 ** or updated records or publishing fails (including a LinkageError from a
 ** missing optional library while the client is built), each affected (table,
 ** record, action) is written to quickSearchFailedEvent for the scheduled
 ** basepull to replay. A failed re-read is recorded after the commit too, so a
 ** rolled-back write leaves no row.
 **
 ** While a full reindex rebuilds into a new physical index, the alias still
 ** points at the old one, so a published delete never reaches the new index.
 ** Such deletes are also written to quickSearchFailedEvent with status
 ** AWAITING_REINDEX; the reindex applies them after the alias swap.
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
         case INSERT, UPDATE ->
         {
            try
            {
               indexEvents.addAll(toIndexEvents(tableName, primaryKeyField, fetchCurrentRecords(event, primaryKeyField)));
            }
            catch(Exception | LinkageError e)
            {
               ///////////////////////////////////////////////////////////////////////
               // log now: with a transaction, nothing else is logged until commit, //
               // and a rollback (a transaction the failed re-read aborted, too)    //
               // would leave no trace. Then record the written keys after commit,  //
               // so basepull re-reads and indexes them.                            //
               ///////////////////////////////////////////////////////////////////////
               LOG.warn("Could not re-read inserted or updated records for indexing; recording them for replay once the write commits", e,
                  logPair("tableName", tableName), logPair("changeType", event.getType()), logPair("recordCount", event.getRecords().size()));
               List<IndexEvent> failedEvents = toIndexEvents(tableName, primaryKeyField, event.getRecords());
               runAfterCommit(event, () -> recordFailures(runtime, failedEvents, e));
               return;
            }
         }
         case DELETE -> deleteEvents.addAll(toDeleteEvents(tableName, primaryKeyField, event.getRecords()));
      }

      if(indexEvents.isEmpty() && deleteEvents.isEmpty())
      {
         return;
      }

      runAfterCommit(event, () -> publish(runtime, indexEvents, deleteEvents));
   }



   /*******************************************************************************
    ** Run work once the event's transaction commits, or now when the write had
    ** no transaction (it is already committed).
    *******************************************************************************/
   private static void runAfterCommit(RecordChangeEvent event, Runnable work)
   {
      if(event.getTransaction() != null)
      {
         event.getTransaction().addAfterCommitCallback(work);
      }
      else
      {
         work.run();
      }
   }



   /*******************************************************************************
    ** Publish, recording any failure durably, and capture the deletes if a full
    ** reindex is rebuilding the table. Never throws.
    **
    ** The status is read before the deletes are published, never after: a
    ** reindex can swap the alias and clear REBUILDING while the publish is in
    ** flight, and a read after that would miss a delete that reached only the
    ** old index. A read before the publish that sees no REBUILDING means either
    ** the reindex has not started reading the table (the record, deleted and
    ** committed, cannot be read into the new index) or the alias already points
    ** at the new index (the publish reaches it).
    *******************************************************************************/
   static void publish(QuickSearchRuntime runtime, List<IndexEvent> indexEvents, List<IndexEvent> deleteEvents)
   {
      boolean rebuilding = isRebuildingBeforePublish(runtime, deleteEvents);
      publishOrRecordFailures(runtime, indexEvents, deleteEvents);
      if(rebuilding)
      {
         captureDeletesDuringRebuild(runtime, deleteEvents);
      }
   }



   /*******************************************************************************
    ** A LinkageError is caught along with Exception: building the lazy client,
    ** or a host publisher, can hit an optional library that is not on the
    ** classpath (the AWS SDK for AWS_SIGV4), which a DEGRADED start leaves for
    ** the first write to find.
    *******************************************************************************/
   private static void publishOrRecordFailures(QuickSearchRuntime runtime, List<IndexEvent> indexEvents, List<IndexEvent> deleteEvents)
   {
      IndexEventPublisher publisher;
      try
      {
         publisher = runtime.getPublisher();
      }
      catch(Exception | LinkageError e)
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
         catch(Exception | LinkageError e)
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
         catch(Exception | LinkageError e)
         {
            recordFailures(runtime, deleteEvents, e);
         }
      }
   }



   /*******************************************************************************
    ** Write one quickSearchFailedEvent row per event. Never throws.
    *******************************************************************************/
   static void recordFailures(QuickSearchRuntime runtime, List<IndexEvent> events, Throwable cause)
   {
      if(events.isEmpty())
      {
         return;
      }

      String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
      LOG.warn("Real-time indexing failed; recording failed events for replay", cause,
         logPair("tableName", events.get(0).getTableName()), logPair("eventCount", events.size()));

      try
      {
         if(!hasTable(runtime.getConfig().applyPrefix(QuickSearchFailedEvent.TABLE_NAME)))
         {
            LOG.error("quickSearchFailedEvent table is not in the QInstance; failed events are lost", logPair("eventCount", events.size()));
            return;
         }

         insertFailedEventRows(runtime, events, message, QuickSearchFailedEvent.STATUS_PENDING);
      }
      catch(Exception e)
      {
         LOG.error("Could not record failed index events", e, logPair("eventCount", events.size()));
      }
   }



   /*******************************************************************************
    ** Whether a full reindex is rebuilding the deletes' table. The status is
    ** read from the database (not cached) so a reindex on another node is seen.
    ** Runs after the delete committed. Never throws.
    *******************************************************************************/
   static boolean isRebuildingBeforePublish(QuickSearchRuntime runtime, List<IndexEvent> deleteEvents)
   {
      if(deleteEvents.isEmpty())
      {
         return (false);
      }

      String tableName = deleteEvents.get(0).getTableName();
      try
      {
         return (hasTable(runtime.getConfig().applyPrefix(QuickSearchFailedEvent.TABLE_NAME)) && isRebuilding(runtime, tableName));
      }
      catch(Exception e)
      {
         LOG.error("Could not read whether a full reindex is running; deletes made during one are not captured", e, logPair("tableName", tableName));
         return (false);
      }
   }



   /*******************************************************************************
    ** Record deletes made while a full reindex is rebuilding the table, so the
    ** reindex can apply them to the new physical index after the alias swap.
    ** basepull does not replay AWAITING_REINDEX rows, so it cannot consume one
    ** against the old index before the swap. Never throws.
    *******************************************************************************/
   static void captureDeletesDuringRebuild(QuickSearchRuntime runtime, List<IndexEvent> deleteEvents)
   {
      String tableName = deleteEvents.get(0).getTableName();
      try
      {
         List<QRecord> inserted = insertFailedEventRows(runtime, deleteEvents, "deleted during a full reindex", QuickSearchFailedEvent.STATUS_AWAITING_REINDEX);
         handOverIfRebuildFinished(runtime, tableName, inserted);
      }
      catch(Exception e)
      {
         LOG.error("Could not capture deletes made during a full reindex; the next full reindex or reconcile removes them", e,
            logPair("tableName", tableName), logPair("eventCount", deleteEvents.size()));
      }
   }



   /*******************************************************************************
    ** The reindex may have finished, and drained its captured deletes, between
    ** the status read and the insert. Read the status again: if the table is no
    ** longer REBUILDING, make the rows PENDING so basepull replays them against
    ** the current index (deletes are idempotent). If it still is, the reindex
    ** has not drained yet and will see these rows.
    *******************************************************************************/
   static void handOverIfRebuildFinished(QuickSearchRuntime runtime, String tableName, List<QRecord> inserted) throws QException
   {
      if(inserted.isEmpty() || isRebuilding(runtime, tableName))
      {
         return;
      }

      List<QRecord> updates = new ArrayList<>();
      for(QRecord row : inserted)
      {
         updates.add(new QRecord().withValue("id", row.getValue("id")).withValue("status", QuickSearchFailedEvent.STATUS_PENDING));
      }

      UpdateInput updateInput = new UpdateInput();
      updateInput.setTableName(runtime.getConfig().applyPrefix(QuickSearchFailedEvent.TABLE_NAME));
      updateInput.setRecords(updates);
      new UpdateAction().execute(updateInput);
   }



   /*******************************************************************************
    ** Whether the table's quickSearchIndex row is REBUILDING.
    *******************************************************************************/
   private static boolean isRebuilding(QuickSearchRuntime runtime, String tableName) throws QException
   {
      String indexTable = runtime.getConfig().getQuickSearchIndexTableName();
      if(!hasTable(indexTable))
      {
         return (false);
      }

      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(indexTable);
      queryInput.setFilter(new QQueryFilter(new QFilterCriteria("tableName", QCriteriaOperator.EQUALS, tableName)).withLimit(1));
      List<QRecord> rows = new QueryAction().execute(queryInput).getRecords();
      return (CollectionUtils.nullSafeHasContents(rows) && AbstractIndexingStep.STATUS_REBUILDING.equals(rows.get(0).getValueString("status")));
   }



   /*******************************************************************************
    ** Insert one quickSearchFailedEvent row per event; returns the inserted rows.
    *******************************************************************************/
   static List<QRecord> insertFailedEventRows(QuickSearchRuntime runtime, List<IndexEvent> events, String message, String status) throws QException
   {
      List<QRecord> rows = new ArrayList<>();
      for(IndexEvent event : events)
      {
         rows.add(new QRecord()
            .withValue("tableName", event.getTableName())
            .withValue("recordId", event.getRecordId())
            .withValue("action", event.getAction() == null ? null : event.getAction().name())
            .withValue("errorMessage", truncate(message, 4000))
            .withValue("attempts", 0)
            .withValue("status", status));
      }

      InsertInput insertInput = new InsertInput();
      insertInput.setTableName(runtime.getConfig().applyPrefix(QuickSearchFailedEvent.TABLE_NAME));
      insertInput.setRecords(rows);
      return (CollectionUtils.nonNullList(new InsertAction().execute(insertInput).getRecords()));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static boolean hasTable(String tableName)
   {
      return (QContext.getQInstance() != null && QContext.getQInstance().getTable(tableName) != null);
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
    ** Changed records are "as the backend returned them": for an update on
    ** RDBMS that is the sparse input, and an insert has raw values only. Re-read
    ** the full rows inside the same transaction, in one query per event, with
    ** display values so possible-value labels are indexed (which can run host
    ** possible-value providers).
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

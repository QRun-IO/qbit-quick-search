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

package com.kingsrook.qbits.quicksearch.diagnostics;


import java.net.InetAddress;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QSystemUserSession;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.model.QuickSearchDiagnosticEvent;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Writes quick_search_diagnostic_event rows: failed searches, diagnostic tool
 ** runs (the audit trail) and refused runs.
 ** Best effort and off the caller's thread: events are queued (at most
 ** QUEUE_CAPACITY) and inserted by one daemon thread as the system user, so
 ** a slow or failing database never slows or fails a search. At most
 ** MAX_EVENTS_PER_MINUTE events are written per JVM per minute; the rest are
 ** counted as dropped (see getDroppedCount), so a search outage cannot flood
 ** the table. A no-op when the diagnostic tables are not configured.
 *******************************************************************************/
public class DiagnosticEventRecorder implements AutoCloseable
{
   private static final QLogger LOG = QLogger.getLogger(DiagnosticEventRecorder.class);

   public static final int MAX_EVENTS_PER_MINUTE = 120;
   public static final int QUEUE_CAPACITY        = 500;

   private final QuickSearchQBitConfig config;
   private final ThreadPoolExecutor    executor;
   private final AtomicLong            droppedCount = new AtomicLong();
   private final AtomicLong            failedCount  = new AtomicLong();
   private final String                hostName;

   private long windowStartMillis = 0L;
   private int  windowCount       = 0;



   /*******************************************************************************
    ** Constructor
    *******************************************************************************/
   public DiagnosticEventRecorder(QuickSearchQBitConfig config)
   {
      this.config   = config;
      this.hostName = resolveHostName();
      this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(QUEUE_CAPACITY), runnable ->
      {
         Thread thread = new Thread(runnable, "quick-search-diagnostic-events");
         thread.setDaemon(true);
         return (thread);
      }, (runnable, pool) -> droppedCount.incrementAndGet());
   }



   /*******************************************************************************
    ** Queue an event for insert. Fills in host name and create date, bounds
    ** string lengths, and uses the current session's user when none is set.
    *******************************************************************************/
   public void record(QuickSearchDiagnosticEvent event)
   {
      QInstance qInstance = QContext.getQInstance();
      String    tableName = config.applyTablePrefix(QuickSearchDiagnosticEvent.TABLE_NAME);
      if(event == null || qInstance == null || !Boolean.TRUE.equals(config.getEnableDiagnosticTools()) || qInstance.getTable(tableName) == null)
      {
         return;
      }

      if(!admit())
      {
         droppedCount.incrementAndGet();
         return;
      }

      if(event.getUserName() == null)
      {
         event.setUserName(DiagnosticsSupport.currentUserName());
      }

      event.withHostName(truncate(hostName, 100))
         .withCreateDate(Instant.now())
         .withSource(truncate(event.getSource(), 50))
         .withUserName(truncate(event.getUserName(), 250))
         .withTableNames(truncate(event.getTableNames(), 1000))
         .withSearchTerm(truncate(event.getSearchTerm(), 100))
         .withErrorClass(truncate(event.getErrorClass(), 250))
         .withErrorMessage(truncate(event.getErrorMessage(), 4000))
         .withDetail(truncate(event.getDetail(), 4000));

      executor.execute(() -> insert(qInstance, tableName, event));
   }



   /*******************************************************************************
    ** Insert one event as the system user. Failures are logged and counted.
    *******************************************************************************/
   private void insert(QInstance qInstance, String tableName, QuickSearchDiagnosticEvent event)
   {
      QSession session = new QSystemUserSession(qInstance);
      try
      {
         QContext.init(qInstance, session);
         new InsertAction().execute(new InsertInput(tableName).withRecordEntity(event));
      }
      catch(Exception e)
      {
         failedCount.incrementAndGet();
         LOG.warn("Could not record a Quick Search diagnostic event", e, logPair("eventType", event.getEventType()), logPair("source", event.getSource()));
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    ** Whether one more event fits in the current minute's budget.
    *******************************************************************************/
   private synchronized boolean admit()
   {
      long now = System.currentTimeMillis();
      if(now - windowStartMillis >= 60_000L)
      {
         windowStartMillis = now;
         windowCount       = 0;
      }
      if(windowCount >= MAX_EVENTS_PER_MINUTE)
      {
         return (false);
      }
      windowCount++;
      return (true);
   }



   /*******************************************************************************
    ** Wait (up to timeoutMillis) until every queued event has been written.
    ** For tests and orderly shutdown.
    *******************************************************************************/
   public void flush(long timeoutMillis)
   {
      try
      {
         executor.submit(() ->
         {
         }).get(timeoutMillis, TimeUnit.MILLISECONDS);
      }
      catch(Exception e)
      {
         LOG.debug("Diagnostic event flush did not complete", e);
      }
   }



   /*******************************************************************************
    ** Events not written because the queue or the per-minute budget was full.
    *******************************************************************************/
   public long getDroppedCount()
   {
      return (droppedCount.get());
   }



   /*******************************************************************************
    ** Events whose insert failed.
    *******************************************************************************/
   public long getFailedCount()
   {
      return (failedCount.get());
   }



   /*******************************************************************************
    ** Stop the writer after the queued events (up to 2 seconds).
    *******************************************************************************/
   @Override
   public void close()
   {
      executor.shutdown();
      try
      {
         if(!executor.awaitTermination(2, TimeUnit.SECONDS))
         {
            executor.shutdownNow();
         }
      }
      catch(InterruptedException e)
      {
         executor.shutdownNow();
         Thread.currentThread().interrupt();
      }
   }



   /*******************************************************************************
    ** This JVM's host name (the pod name in Kubernetes).
    *******************************************************************************/
   static String resolveHostName()
   {
      String fromEnv = System.getenv("HOSTNAME");
      if(fromEnv != null && !fromEnv.isBlank())
      {
         return (fromEnv);
      }
      try
      {
         return (InetAddress.getLocalHost().getHostName());
      }
      catch(Exception e)
      {
         return ("unknown");
      }
   }



   /*******************************************************************************
    *******************************************************************************/
   static String truncate(String value, int max)
   {
      return (value == null || value.length() <= max ? value : value.substring(0, max));
   }

}

/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2021-2026.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qbits.quicksearch.diagnostics;


import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.api.QuickSearchApi;
import com.kingsrook.qbits.quicksearch.api.QuickSearchStatus;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.session.QSystemUserSession;


/*******************************************************************************
 ** Instance-owned five-second cache with one in-flight refresh. A stuck backend
 ** cannot grow a queue or create more workers. Runtime shutdown stops the worker.
 *******************************************************************************/
public class StatusCache implements AutoCloseable
{
   private final ReentrantLock lock = new ReentrantLock();
   private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable ->
   {
      Thread thread = new Thread(runnable, "quick-search-status");
      thread.setDaemon(true);
      return (thread);
   });
   private Future<QuickSearchStatus> refresh;
   private QuickSearchStatus cached;
   private long expiresAt;
   private long refreshStartedAt;


   /*******************************************************************************
    ** Authorization belongs to the public service, before reaching this cache.
    ** Only refreshes consume the existing per-user diagnostic rate limit.
    *******************************************************************************/
   public QuickSearchStatus get(QuickSearchRuntime runtime) throws QException
   {
      return (get(runtime, Instant.now().plusSeconds(2)));
   }



   /*******************************************************************************
    ** Bound both lock contention and the shared refresh wait by the caller's deadline.
    *******************************************************************************/
   public QuickSearchStatus get(QuickSearchRuntime runtime, Instant deadline) throws QException
   {
      try
      {
         long remaining = Duration.between(Instant.now(), deadline).toNanos();
         if(remaining <= 0 || !lock.tryLock(remaining, TimeUnit.NANOSECONDS))
         {
            throw (new QException("Status deadline exceeded"));
         }
         try
         {
            return (read(runtime, deadline));
         }
         finally
         {
            lock.unlock();
         }
      }
      catch(InterruptedException e)
      {
         Thread.currentThread().interrupt();
         throw (new QException("Status refresh interrupted", e));
      }
   }



   /*******************************************************************************
    ** Read or refresh while holding the deadline-aware cache lock.
    *******************************************************************************/
   private QuickSearchStatus read(QuickSearchRuntime runtime, Instant deadline) throws QException
   {
      if(cached != null && System.nanoTime() < expiresAt)
      {
         return (cached);
      }
      String homeHref = "/app/" + runtime.getConfig().applyPrefix(QuickSearchApi.APP_NAME);
      // A refresh that finished while nobody was polling must not resurrect an old snapshot.
      if(refresh != null && refresh.isDone() && System.nanoTime() - refreshStartedAt >= TimeUnit.SECONDS.toNanos(5))
      {
         refresh = null;
      }
      if(refresh == null)
      {
         DiagnosticsSupport.checkRateLimit(runtime, "STATUS");
         QInstance instance = QContext.getQInstance();
         refreshStartedAt = System.nanoTime();
         refresh = executor.submit(() ->
         {
            QContext.init(instance, new QSystemUserSession(instance));
            try
            {
               return (StatusDiagnostics.read(runtime));
            }
            finally
            {
               QContext.clear();
            }
         });
      }
      try
      {
         long remaining = Math.max(0, Duration.between(Instant.now(), deadline).toNanos());
         cached = refresh.get(Math.min(TimeUnit.SECONDS.toNanos(2), remaining), TimeUnit.NANOSECONDS);
         refresh = null;
      }
      catch(TimeoutException e)
      {
         // Leave the one worker in flight; never pile up abandoned requests.
         cached = StatusDiagnostics.unavailable(QuickSearchStatus.State.DEGRADED, "Status refresh timed out; measurements are unknown.", homeHref);
      }
      catch(InterruptedException e)
      {
         Thread.currentThread().interrupt();
         throw (new QException("Status refresh interrupted", e));
      }
      catch(ExecutionException e)
      {
         refresh = null;
         cached = StatusDiagnostics.unavailable(QuickSearchStatus.State.DEGRADED, "Status diagnostics could not be read.", homeHref);
      }
      expiresAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      return (cached);
   }



   /*******************************************************************************
    ** Stop the bounded worker when its runtime closes.
    *******************************************************************************/
   @Override
   public void close()
   {
      executor.shutdownNow();
   }
}

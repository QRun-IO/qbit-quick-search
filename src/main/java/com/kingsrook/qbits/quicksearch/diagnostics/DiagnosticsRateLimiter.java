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


import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;


/*******************************************************************************
 ** Sliding one-minute window of diagnostic tool runs per key (user and
 ** tool), per JVM. tryAcquire admits a run when fewer than the limit ran in
 ** the last 60 seconds.
 *******************************************************************************/
public class DiagnosticsRateLimiter
{
   private static final long WINDOW_MILLIS = 60_000L;

   private final Map<String, Deque<Long>> runsByKey = new ConcurrentHashMap<>();
   private final LongSupplier             clock;



   /*******************************************************************************
    ** Constructor (system clock)
    *******************************************************************************/
   public DiagnosticsRateLimiter()
   {
      this(System::currentTimeMillis);
   }



   /*******************************************************************************
    ** Constructor with a clock (tests)
    *******************************************************************************/
   public DiagnosticsRateLimiter(LongSupplier clock)
   {
      this.clock = clock;
   }



   /*******************************************************************************
    ** Admit one run for the key when fewer than perMinute ran in the last
    ** minute; a null or non-positive limit admits everything.
    *******************************************************************************/
   public boolean tryAcquire(String key, Integer perMinute)
   {
      if(perMinute == null || perMinute <= 0)
      {
         return (true);
      }

      long        now  = clock.getAsLong();
      Deque<Long> runs = runsByKey.computeIfAbsent(key, k -> new ArrayDeque<>());
      synchronized(runs)
      {
         while(!runs.isEmpty() && now - runs.peekFirst() >= WINDOW_MILLIS)
         {
            runs.pollFirst();
         }
         if(runs.size() >= perMinute)
         {
            return (false);
         }
         runs.addLast(now);
         return (true);
      }
   }

}

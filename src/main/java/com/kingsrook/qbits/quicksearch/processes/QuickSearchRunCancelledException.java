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

package com.kingsrook.qbits.quicksearch.processes;


import com.kingsrook.qqq.backend.core.exceptions.QException;


/*******************************************************************************
 ** Thrown between batches when a run's cancel flag is set (Cancel Index Run),
 ** or indexing was paused, carrying the counts so far. The run ends CANCELLED
 ** with those counts; a full rebuild that is cancelled keeps the old index.
 *******************************************************************************/
public class QuickSearchRunCancelledException extends QException
{
   private final AbstractIndexingStep.IndexCounts counts;



   /*******************************************************************************
    ** Constructor
    *******************************************************************************/
   public QuickSearchRunCancelledException(String message, AbstractIndexingStep.IndexCounts counts)
   {
      super(message);
      this.counts = counts;
   }



   /*******************************************************************************
    ** The counts when the run stopped.
    *******************************************************************************/
   public AbstractIndexingStep.IndexCounts getCounts()
   {
      return (counts);
   }

}

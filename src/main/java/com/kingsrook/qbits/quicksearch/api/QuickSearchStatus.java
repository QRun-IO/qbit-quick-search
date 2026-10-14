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

package com.kingsrook.qbits.quicksearch.api;


import java.time.Instant;
import java.util.List;
import com.kingsrook.qqq.middleware.javalin.schemabuilder.ToSchema;
import com.kingsrook.qqq.openapi.model.Schema;


/*******************************************************************************
 ** Immutable administrative snapshot. Null measurements are unknown, never zero.
 ** Counts come from persisted diagnostics; countsCheckedAt identifies their age.
 *******************************************************************************/
public record QuickSearchStatus(State state, Boolean clusterReachable, String clusterHealth, Integer nodes, Integer activeShards, Integer unassignedShards,
                                Boolean indexingPaused, List<IndexRow> indexes, Integer pending, Integer exhausted, Boolean backlogTruncated,
                                Boolean tablesTruncated, Instant lastSuccessfulSync, String lastError, Instant checkedAt, String homeHref) implements ToSchema
{
   /*******************************************************************************
    ** Published wire schema for the same immutable value used by Java hosts.
    *******************************************************************************/
   @Override
   public Schema toSchema()
   {
      return (new QuickSearchStatusSpec().defineComponentSchemas().get("QuickSearchStatusV1"));
   }



   /*******************************************************************************
    ** Protect the snapshot from mutations by callers.
    *******************************************************************************/
   public QuickSearchStatus
   {
      indexes = List.copyOf(indexes);
   }



   /*******************************************************************************
    ** Overall availability; DEGRADED also covers incomplete measurements.
    *******************************************************************************/
   public enum State
   {
      OK, DEGRADED, DOWN, DISABLED
   }



   /*******************************************************************************
    ** A persisted index row with counts from the latest saved validation, when
    ** available. Otherwise documentCount is the last indexing run's count and
    ** sourceCount/countsCheckedAt are unknown. Sync time is a successful watermark.
    *******************************************************************************/
   public record IndexRow(String tableName, Boolean enabled, String status, Long documentCount, Long sourceCount, Instant countsCheckedAt,
                          Instant lastSuccessfulSync, String lastError, Integer realTimeErrorCount) implements ToSchema
   {
      /*******************************************************************************
       ** Published per-table schema.
       *******************************************************************************/
      @Override
      public Schema toSchema()
      {
         return (new QuickSearchStatusSpec().defineComponentSchemas().get("QuickSearchIndexStatusV1"));
      }
   }
}

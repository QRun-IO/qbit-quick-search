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

package com.kingsrook.qbits.quicksearch.health;


import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.api.QuickSearchStatus;
import com.kingsrook.qbits.quicksearch.api.QuickSearchStatus.IndexRow;
import com.kingsrook.qbits.quicksearch.config.QuickSearchConfigPrimer;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.health.HealthCategory;
import com.kingsrook.qqq.backend.core.health.HealthCheck;
import com.kingsrook.qqq.backend.core.health.HealthCheckInput;
import com.kingsrook.qqq.backend.core.health.HealthCheckKind;
import com.kingsrook.qqq.backend.core.health.HealthCheckMetaData;
import com.kingsrook.qqq.backend.core.health.HealthCheckOutcome;
import com.kingsrook.qqq.backend.core.health.HealthCheckSupplier;
import com.kingsrook.qqq.backend.core.health.HealthCheckSupplierMetaData;
import com.kingsrook.qqq.backend.core.health.HealthChecksMetaData;
import com.kingsrook.qqq.backend.core.health.HealthDetails;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;


/*******************************************************************************
 ** Central health projections of the existing, shared diagnostic snapshot.
 ** Free-form errors and remote strings never cross this boundary.
 *******************************************************************************/
public class QuickSearchHealthChecks implements HealthCheckSupplier
{
   /*******************************************************************************
    ** Register once, even if the producer is run again on the same instance.
    *******************************************************************************/
   public static void register(QInstance instance)
   {
      HealthChecksMetaData registry = HealthChecksMetaData.ofOrWithNew(instance);
      if(registry.getChecks().stream().noneMatch(check -> "quick_search.opensearch".equals(check.getName())))
      {
         registry.withCheck(definition("quick_search.opensearch", input -> run(input, QuickSearchHealthChecks::opensearch)));
      }
      if(registry.getChecks().stream().noneMatch(check -> "quick_search.backlog".equals(check.getName())))
      {
         registry.withCheck(definition("quick_search.backlog", input -> run(input, QuickSearchHealthChecks::backlog)));
      }
      if(registry.getSuppliers().stream().noneMatch(supplier -> "quick_search.index".equals(supplier.getNamePrefix())))
      {
         registry.withSupplier(new HealthCheckSupplierMetaData().withNamePrefix("quick_search.index").withComponent("quick_search")
            .withSupplier(new QCodeReference(QuickSearchHealthChecks.class)));
      }
   }



   /*******************************************************************************
    ** Enumerate persisted index identities, including disabled indexes. Execution
    ** reads a fresh cached snapshot, never a row captured at supplier resolution.
    *******************************************************************************/
   @Override
   public List<HealthCheckMetaData> supply(QInstance instance) throws QException
   {
      QuickSearchRuntime runtime = runtime(instance);
      if(runtime == null)
      {
         return (List.of());
      }
      List<HealthCheckMetaData> checks = new ArrayList<>();
      for(QRecord row : QuickSearchConfigPrimer.queryAll(runtime.getConfig().getQuickSearchIndexTableName()))
      {
         String tableName = row.getValueString("tableName");
         String name = tableName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_").replaceAll("[._-]{2,}", "_").replaceAll("^[._-]|[._-]$", "");
         if(name.isEmpty())
         {
            throw (new QException("Index name cannot form a health check identifier"));
         }
         checks.add(definition("quick_search.index." + name, input -> run(input, status -> index(status, tableName))));
      }
      return (checks);
   }



   /*******************************************************************************
    ** All Quick Search checks are dependencies; none gate Kubernetes readiness.
    *******************************************************************************/
   private static HealthCheckMetaData definition(String name, HealthCheck check)
   {
      return (new HealthCheckMetaData().withName(name).withLabel(name).withComponent("quick_search")
         .withCategory(HealthCategory.SEARCH).withKind(HealthCheckKind.DEPENDENCY)
         .withIntervalSeconds(60).withTimeoutMs(5000).withCheckInstance(check));
   }



   /*******************************************************************************
    ** Resolve from the supplied instance, not a static or previously closed runtime.
    *******************************************************************************/
   private static QuickSearchRuntime runtime(QInstance instance)
   {
      if(instance != null && instance.getQBits() != null)
      {
         for(QBitMetaData qbit : instance.getQBits().values())
         {
            if(qbit.getConfig() instanceof QuickSearchQBitConfig config)
            {
               return (config.getRuntime());
            }
         }
      }
      return (null);
   }



   /*******************************************************************************
    ** Share the diagnostic worker and respect the deadline even on a cache hit.
    ** Expected diagnostic failures use fixed messages, never exception payloads.
    *******************************************************************************/
   private static HealthCheckOutcome run(HealthCheckInput input, Function<QuickSearchStatus, HealthCheckOutcome> project)
   {
      if(!Instant.now().isBefore(input.getDeadline()))
      {
         return (HealthCheckOutcome.down("Quick Search health deadline exceeded"));
      }
      QuickSearchRuntime runtime = runtime(input.getQInstance());
      if(runtime == null)
      {
         return (HealthCheckOutcome.unknown("Quick Search is not configured"));
      }
      try
      {
         QuickSearchStatus status = runtime.getStatusCache().get(runtime, input.getDeadline());
         if(!Instant.now().isBefore(input.getDeadline()))
         {
            return (HealthCheckOutcome.down("Quick Search health deadline exceeded"));
         }
         return (project.apply(status));
      }
      catch(QException e)
      {
         return (HealthCheckOutcome.down("Quick Search diagnostics unavailable"));
      }
   }



   /*******************************************************************************
    ** Cluster health is independent of backlog and per-index degradation.
    *******************************************************************************/
   private static HealthCheckOutcome opensearch(QuickSearchStatus status)
   {
      if(status.state() == QuickSearchStatus.State.DISABLED)
      {
         return (HealthCheckOutcome.unknown("Quick Search is disabled"));
      }
      HealthCheckOutcome outcome;
      if(status.state() == QuickSearchStatus.State.DOWN || Boolean.FALSE.equals(status.clusterReachable()) || "red".equals(status.clusterHealth()))
      {
         outcome = HealthCheckOutcome.down("OpenSearch is unavailable or red");
      }
      else if(Boolean.TRUE.equals(status.clusterReachable()) && "green".equals(status.clusterHealth()))
      {
         outcome = HealthCheckOutcome.up("OpenSearch is green");
      }
      else if(Boolean.TRUE.equals(status.clusterReachable()) && QuickSearchStatus.isSingleNodeReplicaYellow(status.clusterHealth(), status.nodes(), status.unassignedReplicaShards()))
      {
         outcome = HealthCheckOutcome.up("OpenSearch is yellow only because replicas cannot be assigned on a single node");
         outcome.getDetails().withValue("note", "replicas cannot be assigned on a single-node cluster");
      }
      else
      {
         outcome = HealthCheckOutcome.degraded("OpenSearch health is yellow or incomplete");
      }
      detail(outcome, "reachable", status.clusterReachable());
      detail(outcome, "nodes", status.nodes());
      detail(outcome, "activeShards", status.activeShards());
      detail(outcome, "unassignedShards", status.unassignedShards());
      detail(outcome, "unassignedReplicaShards", status.unassignedReplicaShards());
      detail(outcome, "targetReplicas", status.targetReplicas());
      detail(outcome, "replicaSettingsFailed", status.replicaSettingsFailed());
      return (outcome);
   }



   /*******************************************************************************
    ** Counts are lower bounds when truncated; do not declare an incomplete backlog healthy.
    *******************************************************************************/
   private static HealthCheckOutcome backlog(QuickSearchStatus status)
   {
      HealthCheckOutcome outcome;
      if(status.state() == QuickSearchStatus.State.DISABLED)
      {
         outcome = HealthCheckOutcome.unknown("Quick Search is disabled");
      }
      else if(status.pending() == null || status.exhausted() == null || status.indexingPaused() == null)
      {
         outcome = HealthCheckOutcome.degraded("Indexing backlog measurements unavailable");
      }
      else if(status.indexingPaused() || status.exhausted() > 0 || status.pending() > 100 || Boolean.TRUE.equals(status.backlogTruncated()))
      {
         outcome = HealthCheckOutcome.degraded("Indexing is paused or backlog needs attention");
      }
      else
      {
         outcome = HealthCheckOutcome.up("Indexing backlog is within limits");
      }
      detail(outcome, "pending", status.pending());
      detail(outcome, "exhausted", status.exhausted());
      detail(outcome, "indexingPaused", status.indexingPaused());
      detail(outcome, "backlogTruncated", status.backlogTruncated());
      return (outcome);
   }



   /*******************************************************************************
    ** Missing rows (including those outside the bounded snapshot) stay unknown.
    ** Persisted error text is represented only by a boolean.
    *******************************************************************************/
   private static HealthCheckOutcome index(QuickSearchStatus status, String tableName)
   {
      IndexRow row = status.indexes().stream().filter(candidate -> tableName.equals(candidate.tableName())).findFirst().orElse(null);
      if(row == null)
      {
         return (HealthCheckOutcome.unknown("Index measurements unavailable"));
      }
      boolean hasError = row.lastError() != null && !row.lastError().isBlank();
      boolean mismatch = row.documentCount() != null && row.sourceCount() != null && !row.documentCount().equals(row.sourceCount());
      HealthCheckOutcome outcome;
      if(Boolean.FALSE.equals(row.enabled()))
      {
         outcome = HealthCheckOutcome.unknown("Index is disabled");
      }
      else if(!"ACTIVE".equals(row.status()) || hasError || mismatch || (row.realTimeErrorCount() != null && row.realTimeErrorCount() > 0))
      {
         outcome = HealthCheckOutcome.degraded("Index needs attention");
      }
      else
      {
         outcome = HealthCheckOutcome.up("Index is active");
      }
      outcome.withDetails(new HealthDetails().withValue("hasError", hasError).withValue("countMismatch", mismatch));
      detail(outcome, "enabled", row.enabled());
      detail(outcome, "documentCount", row.documentCount());
      detail(outcome, "sourceCount", row.sourceCount());
      detail(outcome, "countsCheckedAt", row.countsCheckedAt());
      detail(outcome, "lastSuccessfulSync", row.lastSuccessfulSync());
      detail(outcome, "realTimeErrorCount", row.realTimeErrorCount());
      return (outcome);
   }



   /*******************************************************************************
    ** Unknown measurements are omitted rather than converted to zero.
    *******************************************************************************/
   private static void detail(HealthCheckOutcome outcome, String name, Object value)
   {
      if(value != null)
      {
         outcome.getDetails().withValue(name, value);
      }
   }
}

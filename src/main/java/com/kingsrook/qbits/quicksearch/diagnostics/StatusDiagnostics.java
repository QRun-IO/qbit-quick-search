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


import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.api.QuickSearchApi;
import com.kingsrook.qbits.quicksearch.api.QuickSearchStatus;
import com.kingsrook.qbits.quicksearch.api.QuickSearchStatus.IndexRow;
import com.kingsrook.qbits.quicksearch.api.QuickSearchStatus.State;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchDiagnosticsOperations;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;


/*******************************************************************************
 ** Cheap projection of existing diagnostics: one health request, bounded metadata
 ** reads, saved IndexValidator counts. No source scans or per-table remote calls.
 *******************************************************************************/
public final class StatusDiagnostics
{
   public static final int MAX_TABLES = 100;
   public static final int MAX_BACKLOG = 1000;
   private static final ObjectMapper MAPPER = new ObjectMapper();


   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private StatusDiagnostics()
   {
   }



   /*******************************************************************************
    ** Read as the trusted diagnostics worker after the caller's admin check.
    *******************************************************************************/
   public static QuickSearchStatus read(QuickSearchRuntime runtime) throws Exception
   {
      String homeHref = "/app/" + runtime.getConfig().applyPrefix(QuickSearchApi.APP_NAME);
      List<QRecord> rows = query(runtime.getConfig().getQuickSearchIndexTableName(), new QQueryFilter()
         .withOrderBy(new QFilterOrderBy("id")).withLimit(MAX_TABLES + 1));
      boolean truncated = rows.size() > MAX_TABLES;
      QRecord validation = DiagnosticsSupport.latestValidation(runtime.getConfig());
      String json = validation == null ? null : validation.getValueString("reportJson");
      JsonNode report = json == null || json.length() > 1_000_000 ? MAPPER.createObjectNode() : MAPPER.readTree(json);
      Instant countsAt = validation == null ? null : validation.getValueInstant("endTime");
      List<IndexRow> indexes = new ArrayList<>();
      Instant lastSync = null;
      String lastError = runtime.getLastRecordSearchFailureMessage();
      Instant lastErrorAt = runtime.getLastRecordSearchFailureTime();
      boolean degraded = truncated || runtime.isRecordSearchBackingOff() || (rows.isEmpty() && !runtime.getCodeTables().isEmpty());
      for(QRecord row : rows.subList(0, Math.min(rows.size(), MAX_TABLES)))
      {
         String tableName = row.getValueString("tableName");
         Long documents = row.getValueLong("documentCount");
         Long source = null;
         Instant measuredAt = null;
         for(JsonNode stats : report.path("tableStats"))
         {
            if(tableName.equals(stats.path("tableName").asText()))
            {
               documents = stats.path("indexCount").isNumber() ? stats.path("indexCount").longValue() : null;
               source = stats.path("databaseCount").isNumber() ? stats.path("databaseCount").longValue() : null;
               measuredAt = countsAt;
               break;
            }
         }
         Instant sync = latest(row.getValueInstant("lastBasepullTime"), latest(row.getValueInstant("lastFullReindexTime"), row.getValueInstant("lastReconcileTime")));
         lastSync = latest(lastSync, sync);
         String error = row.getValueString("lastErrorMessage");
         if(error != null && !error.isBlank() && (lastErrorAt == null || (row.getValueInstant("modifyDate") != null && row.getValueInstant("modifyDate").isAfter(lastErrorAt))))
         {
            lastError = error;
            lastErrorAt = row.getValueInstant("modifyDate");
         }
         boolean enabled = !Boolean.FALSE.equals(row.getValueBoolean("enabled"));
         Integer realTimeErrors = row.getValueInteger("realTimeErrorCount");
         degraded |= enabled && (!"ACTIVE".equals(row.getValueString("status")) || "FAILED".equals(row.getValueString("lastRunStatus"))
            || (realTimeErrors != null && realTimeErrors > 0) || (source != null && documents != null && !source.equals(documents)));
         indexes.add(new IndexRow(tableName, enabled, row.getValueString("status"), documents, source, measuredAt, sync, error, realTimeErrors));
      }
      List<QRecord> backlog = query(runtime.getConfig().applyTablePrefix(QuickSearchFailedEvent.TABLE_NAME),
         new QQueryFilter(new QFilterCriteria("status", QCriteriaOperator.IN, List.of("PENDING", "EXHAUSTED", "AWAITING_REINDEX")))
            .withOrderBy(new QFilterOrderBy("id")).withLimit(MAX_BACKLOG + 1));
      int pending = 0;
      int exhausted = 0;
      for(QRecord row : backlog.subList(0, Math.min(backlog.size(), MAX_BACKLOG)))
      {
         if("EXHAUSTED".equals(row.getValueString("status")))
         {
            exhausted++;
         }
         else
         {
            pending++;
         }
      }
      boolean paused = runtime.isIndexingPaused();
      degraded |= paused || pending > 0 || exhausted > 0;
      Boolean reachable = null;
      String health = null;
      Integer nodes = null;
      Integer active = null;
      Integer unassigned = null;
      State state = degraded ? State.DEGRADED : State.OK;
      if(!truncated && !indexes.isEmpty() && indexes.stream().noneMatch(row -> Boolean.TRUE.equals(row.enabled())))
      {
         state = State.DISABLED;
      }
      else
      {
         try
         {
            var response = new QuickSearchDiagnosticsOperations(runtime.getClient()).clusterHealth();
            reachable = true;
            QuickSearchDiagnosticsOperations.requireOk(response, "Cluster health unavailable");
            JsonNode body = response.body();
            health = body.path("status").asText("unknown");
            nodes = number(body, "number_of_nodes");
            active = number(body, "active_shards");
            unassigned = number(body, "unassigned_shards");
            if(IndexValidator.healthLevel(health) != DiagnosticsHtml.Level.OK || body.path("timed_out").asBoolean())
            {
               state = "red".equals(health) ? State.DOWN : State.DEGRADED;
            }
         }
         catch(QException e)
         {
            if(reachable == null)
            {
               reachable = false;
            }
            state = State.DOWN;
            lastError = "OpenSearch health is unavailable; see Quick Search diagnostics.";
         }
      }
      return (new QuickSearchStatus(state, reachable, health, nodes, active, unassigned, paused, indexes, pending, exhausted, backlog.size() > MAX_BACKLOG,
         truncated, lastSync, lastError, Instant.now(), homeHref));
   }



   /*******************************************************************************
    ** Unknown measurements for disabled or failed snapshots.
    *******************************************************************************/
   public static QuickSearchStatus unavailable(State state, String error, String homeHref)
   {
      return (new QuickSearchStatus(state, null, null, null, null, null, null, List.of(), null, null, false, false, null, error, Instant.now(), homeHref));
   }



   /*******************************************************************************
    ** Bounded metadata queries; failures propagate rather than looking healthy.
    *******************************************************************************/
   private static List<QRecord> query(String tableName, QQueryFilter filter) throws QException
   {
      return (CollectionUtils.nonNullList(new QueryAction().execute(new QueryInput(tableName).withFilter(filter)).getRecords()));
   }



   /*******************************************************************************
    ** Preserve unknown counters from incomplete cluster responses.
    *******************************************************************************/
   private static Integer number(JsonNode body, String field)
   {
      return (body.path(field).isNumber() ? body.path(field).intValue() : null);
   }



   /*******************************************************************************
    ** Newest non-null timestamp.
    *******************************************************************************/
   private static Instant latest(Instant first, Instant second)
   {
      return (first == null ? second : second == null || first.isAfter(second) ? first : second);
   }
}

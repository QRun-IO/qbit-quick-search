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


import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper;
import com.kingsrook.qqq.backend.core.actions.permissions.TablePermissionSubType;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Capability;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchRecordSearchProvider;


/*******************************************************************************
 ** Which path the global search (core RecordSearchAction, POST
 ** /qqq/v1/search) takes for each table, and why: OpenSearch through the
 ** Quick Search record-search provider, core's own database search, or not
 ** searched at all. Mirrors RecordSearchAction's table selection and
 ** QuickSearchRecordSearchProvider.claimsTable without calling them, so a
 ** diagnosis changes no state (no backoff, no counters).
 *******************************************************************************/
public final class SearchPathDiagnosis
{
   public static final String PATH_OPENSEARCH = "OpenSearch (Quick Search provider)";
   public static final String PATH_CORE       = "Core database search";
   public static final String PATH_NONE       = "Not searched";



   /*******************************************************************************
    ** The record-search provider's state in this JVM.
    *******************************************************************************/
   public record ProviderState(String registeredProvider, Boolean quickSearchProvider, Boolean serveCoreRecordSearch, Long backoffRemainingMillis,
                               Long failureCount, Instant lastFailureTime, String lastFailureMessage, Long backoffDeclinedClaims, Integer timeoutMillis, Instant runtimeStartedAt)
   {
   }



   /*******************************************************************************
    ** One table's path and the reason for it.
    *******************************************************************************/
   public record TablePath(String tableName, String tableLabel, Boolean indexed, Boolean enabled, Boolean readable, Boolean coreSearchable, String path, String reason)
   {
   }



   /*******************************************************************************
    ** Utility class - no instances.
    *******************************************************************************/
   private SearchPathDiagnosis()
   {
   }



   /*******************************************************************************
    ** The provider's state from the instance and the runtime.
    *******************************************************************************/
   public static ProviderState providerState(QInstance qInstance, QuickSearchRuntime runtime)
   {
      QCodeReference        provider = qInstance.getRecordSearchProvider();
      QuickSearchQBitConfig config   = runtime.getConfig();
      return (new ProviderState(
         provider == null ? null : provider.getName(),
         provider != null && QuickSearchRecordSearchProvider.class.getName().equals(provider.getName()),
         Boolean.TRUE.equals(config.getServeCoreRecordSearch()),
         runtime.getRecordSearchBackoffRemainingMillis(),
         runtime.getRecordSearchFailureCount(),
         runtime.getLastRecordSearchFailureTime(),
         runtime.getLastRecordSearchFailureMessage(),
         runtime.getBackoffDeclinedClaimCount(),
         config.getRecordSearchTimeoutMillis() == null ? QuickSearchQBitConfig.DEFAULT_RECORD_SEARCH_TIMEOUT_MILLIS : config.getRecordSearchTimeoutMillis(),
         runtime.getStartedAt()));
   }



   /*******************************************************************************
    ** The path of each named table (unknown names are reported as not
    ** searched), as seen by the session of permissionInput.
    *******************************************************************************/
   public static List<TablePath> diagnose(QInstance qInstance, QuickSearchRuntime runtime, List<String> tableNames, AbstractActionInput permissionInput)
   {
      ProviderState   state = providerState(qInstance, runtime);
      List<TablePath> paths = new ArrayList<>();
      for(String tableName : CollectionUtils.nonNullList(tableNames))
      {
         paths.add(diagnoseTable(qInstance, runtime, state, tableName, permissionInput));
      }
      return (paths);
   }



   /*******************************************************************************
    *******************************************************************************/
   private static TablePath diagnoseTable(QInstance qInstance, QuickSearchRuntime runtime, ProviderState state, String tableName, AbstractActionInput permissionInput)
   {
      QTableMetaData table = qInstance.getTable(tableName);
      if(table == null)
      {
         return (new TablePath(tableName, tableName, false, false, false, false, PATH_NONE, "There is no table with this name."));
      }

      boolean indexed        = runtime.getTableConfig(tableName) != null;
      boolean enabled        = indexed && runtime.isTableEnabled(tableName);
      boolean readable       = PermissionsHelper.hasTablePermission(permissionInput, tableName, TablePermissionSubType.READ);
      boolean coreSearchable = !CollectionUtils.nullSafeIsEmpty(table.getSearchFields()) && table.isCapabilityEnabled(qInstance.getBackendForTable(tableName), Capability.TABLE_QUERY);
      String  label          = table.getLabel() == null ? tableName : table.getLabel();

      if(!coreSearchable)
      {
         return (new TablePath(tableName, label, indexed, enabled, readable, false, PATH_NONE,
            "Global search skips this table: it has no core searchFields (or does not support query)."));
      }
      if(!readable)
      {
         return (new TablePath(tableName, label, indexed, enabled, false, true, PATH_NONE,
            "Global search skips this table for you: you do not have read permission on it."));
      }
      if(!indexed)
      {
         return (new TablePath(tableName, label, false, false, true, true, PATH_CORE, "Quick Search does not index this table."));
      }
      if(!Boolean.TRUE.equals(state.serveCoreRecordSearch()))
      {
         return (new TablePath(tableName, label, true, enabled, true, true, PATH_CORE, "serveCoreRecordSearch is off in the Quick Search configuration."));
      }
      if(!Boolean.TRUE.equals(state.quickSearchProvider()))
      {
         String reason = state.registeredProvider() == null
            ? "No record search provider is registered on the instance."
            : "Another record search provider is registered (" + state.registeredProvider() + "), so Quick Search does not serve global search.";
         return (new TablePath(tableName, label, true, enabled, true, true, PATH_CORE, reason));
      }
      if(!enabled)
      {
         return (new TablePath(tableName, label, true, false, true, true, PATH_CORE, "The table is disabled in Quick Search Index (enabled = false)."));
      }
      if(state.backoffRemainingMillis() != null && state.backoffRemainingMillis() > 0)
      {
         return (new TablePath(tableName, label, true, true, true, true, PATH_CORE,
            "Quick Search is backing off for " + Math.max(1, state.backoffRemainingMillis() / 1000) + " more second(s) after an OpenSearch failure"
               + (state.lastFailureTime() == null ? "" : " at " + state.lastFailureTime())
               + (state.lastFailureMessage() == null ? "" : ": " + state.lastFailureMessage())));
      }

      return (new TablePath(tableName, label, true, true, true, true, PATH_OPENSEARCH,
         "Claimed by the Quick Search provider. If OpenSearch fails or takes longer than " + state.timeoutMillis()
            + " ms, core searches this table itself for that request and Quick Search backs off for "
            + (QuickSearchRuntime.RECORD_SEARCH_BACKOFF_MILLIS / 1000) + " seconds."));
   }

}

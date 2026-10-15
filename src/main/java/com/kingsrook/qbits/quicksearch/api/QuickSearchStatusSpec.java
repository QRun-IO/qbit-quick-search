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


import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.kingsrook.qqq.middleware.javalin.specs.BasicOperation;
import com.kingsrook.qqq.middleware.javalin.specs.BasicResponse;
import com.kingsrook.qqq.middleware.javalin.specs.extensions.AbstractJsonEndpointSpec;
import com.kingsrook.qqq.middleware.javalin.specs.v1.responses.BasicErrorResponseV1;
import com.kingsrook.qqq.openapi.model.HttpMethod;
import com.kingsrook.qqq.openapi.model.Method;
import com.kingsrook.qqq.openapi.model.Parameter;
import com.kingsrook.qqq.openapi.model.Schema;
import com.kingsrook.qqq.openapi.model.Type;
import io.javalin.http.HttpStatus;


/*******************************************************************************
 ** Administrative status, available through the spec-managed UI API only.
 *******************************************************************************/
public class QuickSearchStatusSpec extends AbstractJsonEndpointSpec
{
   /*******************************************************************************
    ** Describe the supported operation.
    *******************************************************************************/
   @Override
   public BasicOperation defineBasicOperation()
   {
      return (new BasicOperation().withHttpMethod(HttpMethod.GET).withPath("/qbit/quick-search/status")
         .withShortSummary("Get Quick Search status")
         .withLongDescription("Requires Quick Search admin permission. Five-second instance cache; two-second refresh wait. Counts are saved diagnostics, not live source scans. Unknown values are null. Backlog counts are lower bounds when backlogTruncated is true."));
   }



   /*******************************************************************************
    ** Stable generated-client operation name.
    *******************************************************************************/
   @Override
   protected void customizeMethod(Method method)
   {
      method.setOperationId("getQuickSearchStatus");
   }



   /*******************************************************************************
    ** No request parameters.
    *******************************************************************************/
   @Override
   public List<Parameter> defineRequestParameters()
   {
      return (List.of());
   }



   /*******************************************************************************
    ** Versioned response component.
    *******************************************************************************/
   @Override
   protected String getResponseSchemaName()
   {
      return ("QuickSearchStatusV1");
   }



   /*******************************************************************************
    ** Use exactly the same authorized service as in-process hosts.
    *******************************************************************************/
   @Override
   protected Object handle(JsonInput input) throws Exception
   {
      return (QuickSearchStatusService.get());
   }



   /*******************************************************************************
    ** Standard framework errors, including the diagnostics rate limit.
    *******************************************************************************/
   @Override
   public List<BasicResponse> defineAdditionalBasicResponses()
   {
      return (List.of(new BasicResponse(HttpStatus.BAD_REQUEST, "Diagnostic rate limit exceeded", BasicErrorResponseV1.class.getSimpleName()),
         new BasicResponse(HttpStatus.UNAUTHORIZED, "Sign in required", BasicErrorResponseV1.class.getSimpleName()),
         new BasicResponse(HttpStatus.FORBIDDEN, "Quick Search admin permission required", BasicErrorResponseV1.class.getSimpleName())));
   }



   /*******************************************************************************
    ** Explicit schemas make nullability, bounds and measurement age discoverable.
    *******************************************************************************/
   @Override
   public Map<String, Schema> defineComponentSchemas()
   {
      Map<String, Schema> status = new TreeMap<>();
      status.put("state", field(Type.STRING, "Overall availability; incomplete diagnostics are DEGRADED.", false)
         .withEnumValues(List.of("OK", "DEGRADED", "DOWN", "DISABLED")));
      status.put("clusterReachable", field(Type.BOOLEAN, "Whether OpenSearch answered; null when not probed.", true));
      status.put("clusterHealth", field(Type.STRING, "OpenSearch health color; null when unknown.", true));
      for(String name : List.of("nodes", "activeShards", "unassignedShards"))
      {
         status.put(name, field(Type.INTEGER, "Cluster health counter; null when unavailable.", true));
      }
      status.put("indexingPaused", field(Type.BOOLEAN, "Indexing is paused; search can still serve.", true));
      status.put("indexes", new Schema().withType(Type.ARRAY).withItems(new Schema().withRef("#/components/schemas/QuickSearchIndexStatusV1"))
         .withDescription("At most 100 persisted index rows, ordered by id."));
      status.put("pending", field(Type.INTEGER, "Pending and awaiting-reindex failed events, from at most 1000 backlog rows.", true));
      status.put("exhausted", field(Type.INTEGER, "Exhausted failed events, from the same bounded backlog read.", true));
      status.put("backlogTruncated", field(Type.BOOLEAN, "Counts are lower bounds when more than 1000 backlog rows exist.", false));
      status.put("tablesTruncated", field(Type.BOOLEAN, "More than 100 index rows exist.", false));
      status.put("lastSuccessfulSync", timestamp("Newest successful indexing watermark; not a guarantee all tables are current."));
      status.put("lastError", field(Type.STRING, "Last known indexing/search error or snapshot failure; null when absent.", true));
      status.put("checkedAt", timestamp("Snapshot completion time; remains unchanged on cache hits.").withNullable(false));
      status.put("homeHref", field(Type.STRING, "Quick Search home link, including the configured metadata prefix.", false));
      Map<String, Schema> row = new TreeMap<>();
      row.put("tableName", field(Type.STRING, "Indexed source table metadata name.", false));
      row.put("enabled", field(Type.BOOLEAN, "Whether this index is enabled.", false));
      row.put("status", field(Type.STRING, "Persisted indexing state, such as ACTIVE or NEEDS_REINDEX.", true));
      row.put("documentCount", field(Type.INTEGER, "Saved validation document count, else last indexing run count; not live.", true).withFormat("int64"));
      row.put("sourceCount", field(Type.INTEGER, "Source count from saved IndexValidator report; null without validation.", true).withFormat("int64"));
      row.put("countsCheckedAt", timestamp("Saved validation completion; null when validation counts are unavailable."));
      row.put("lastSuccessfulSync", timestamp("Newest successful basepull/full-reindex/reconcile watermark."));
      row.put("lastError", field(Type.STRING, "Persisted indexing error; null when absent.", true));
      row.put("realTimeErrorCount", field(Type.INTEGER, "Persisted real-time indexing error count.", true));
      return (Map.of("QuickSearchStatusV1", object(status), "QuickSearchIndexStatusV1", object(row),
         "QuickSearchErrorV1", object(Map.of("error", field(Type.STRING, "Framework error message.", false)))
            .withDescription("Deprecated since 1.1.0: errors use BasicErrorResponseV1.")));
   }



   /*******************************************************************************
    ** A response object always includes its properties, including nulls.
    *******************************************************************************/
   private static Schema object(Map<String, Schema> properties)
   {
      return (new Schema().withType(Type.OBJECT).withProperties(new TreeMap<>(properties)));
   }



   /*******************************************************************************
    ** A scalar with explicit unknown semantics.
    *******************************************************************************/
   private static Schema field(Type type, String description, Boolean nullable)
   {
      return (new Schema().withType(type).withDescription(description).withNullable(nullable));
   }



   /*******************************************************************************
    ** ISO-8601 instant.
    *******************************************************************************/
   private static Schema timestamp(String description)
   {
      return (field(Type.STRING, description, true).withFormat("date-time"));
   }
}

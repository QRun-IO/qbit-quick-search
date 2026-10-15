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
import com.kingsrook.qqq.openapi.model.In;
import com.kingsrook.qqq.openapi.model.Method;
import com.kingsrook.qqq.openapi.model.Parameter;
import com.kingsrook.qqq.openapi.model.Schema;
import com.kingsrook.qqq.openapi.model.Type;
import io.javalin.http.HttpStatus;


/*******************************************************************************
 ** Permission-filtered suggestions through the spec-managed UI API.
 *******************************************************************************/
public class QuickSearchSuggestSpec extends AbstractJsonEndpointSpec
{
   /*******************************************************************************
    ** Describe the session-only operation and its bounded search semantics.
    *******************************************************************************/
   @Override
   public BasicOperation defineBasicOperation()
   {
      return (new BasicOperation().withHttpMethod(HttpMethod.GET).withPath("/qbit/quick-search/suggest")
         .withShortSummary("Get Quick Search suggestions")
         .withLongDescription("Requires a signed-in session, not Quick Search admin permission. Results inherit table READ permissions, configured record security locks and protected-field exclusions. One bounded candidate batch per table; no extra scans. Unavailable search and normalized terms shorter than two characters return empty groups."));
   }



   /*******************************************************************************
    ** Stable generated-client operation name.
    *******************************************************************************/
   @Override
   protected void customizeMethod(Method method)
   {
      method.setOperationId("getQuickSearchSuggestions");
   }



   /*******************************************************************************
    ** Bounds apply after whitespace normalization; short terms are forgiving.
    *******************************************************************************/
   @Override
   public List<Parameter> defineRequestParameters()
   {
      return (List.of(
         new Parameter().withName("q").withIn(In.QUERY).withRequired(true)
            .withDescription("Search text: trim and collapse whitespace before applying the 2..100 character range. Shorter terms return empty groups; longer terms return 400.")
            .withSchema(field(Type.STRING, "Normalized search term.", false)),
         new Parameter().withName("limitPerTable").withIn(In.QUERY).withRequired(false)
            .withDescription("Items per table: integer 1..10, default 5.").withSchema(field(Type.INTEGER, "Maximum items per table; default 5, maximum 10.", false)),
         new Parameter().withName("maxTables").withIn(In.QUERY).withRequired(false)
            .withDescription("Groups to return: integer 1..20, default 8.").withSchema(field(Type.INTEGER, "Maximum groups; default 8, maximum 20.", false)),
         new Parameter().withName("tables").withIn(In.QUERY).withRequired(false)
            .withDescription("Optional comma-separated table metadata names. Unknown, disabled and unreadable tables are omitted.")
            .withSchema(field(Type.STRING, "Table-name filter; omitted means all eligible tables.", false))));
   }



   /*******************************************************************************
    ** Versioned response component.
    *******************************************************************************/
   @Override
   protected String getResponseSchemaName()
   {
      return ("QuickSearchSuggestV1");
   }



   /*******************************************************************************
    ** The HTTP and in-process entry points share validation and authorization.
    *******************************************************************************/
   @Override
   protected Object handle(JsonInput input) throws Exception
   {
      return (QuickSearchSuggestService.suggest(input.getQueryParam("q"), input.getQueryParamInteger("limitPerTable"),
         input.getQueryParamInteger("maxTables"), input.getQueryParam("tables")));
   }



   /*******************************************************************************
    ** Reuse the extension's standard framework error shape.
    *******************************************************************************/
   @Override
   public List<BasicResponse> defineAdditionalBasicResponses()
   {
      return (List.of(new BasicResponse(HttpStatus.BAD_REQUEST, "Missing q, normalized term longer than 100 characters, or invalid limits", BasicErrorResponseV1.class.getSimpleName()),
         new BasicResponse(HttpStatus.UNAUTHORIZED, "Sign in required", BasicErrorResponseV1.class.getSimpleName()),
         new BasicResponse(HttpStatus.FORBIDDEN, "Session permission denied", BasicErrorResponseV1.class.getSimpleName())));
   }



   /*******************************************************************************
    ** Explicit wire schemas exclude scores, highlights and display values.
    *******************************************************************************/
   @Override
   public Map<String, Schema> defineComponentSchemas()
   {
      Schema response = object(Map.of(
         "query", field(Type.STRING, "Search term after trimming and collapsing whitespace.", false),
         "groups", array("QuickSearchSuggestGroupV1", "Readable tables ordered by best accessible score; at most maxTables."),
         "truncated", field(Type.BOOLEAN, "More tables had accessible hits than maxTables.", false)));
      Schema group = object(Map.of(
         "tableName", field(Type.STRING, "Source table metadata name.", false),
         "tableLabel", field(Type.STRING, "Source table label, falling back to its metadata name.", false),
         "hasMore", field(Type.BOOLEAN, "An extra accessible item was found in the bounded batch. False does not prove the index has no further matches.", false),
         "items", array("QuickSearchSuggestItemV1", "At most limitPerTable accessible items in per-table relevance order.")));
      Schema item = object(Map.of(
         "recordId", field(Type.STRING, "Source record primary key.", false),
         "label", field(Type.STRING, "Filtered record label, falling back to tableLabel #recordId when blank.", false),
         "subtitle", field(Type.STRING, "Already-filtered result subtitle; null when absent.", true)));
      return (Map.of("QuickSearchSuggestV1", response, "QuickSearchSuggestGroupV1", group, "QuickSearchSuggestItemV1", item));
   }



   /*******************************************************************************
    ** All response objects have explicit properties.
    *******************************************************************************/
   private static Schema object(Map<String, Schema> properties)
   {
      return (new Schema().withType(Type.OBJECT).withNullable(false).withProperties(new TreeMap<>(properties)));
   }



   /*******************************************************************************
    ** Scalar fields declare nullability and meaning.
    *******************************************************************************/
   private static Schema field(Type type, String description, Boolean nullable)
   {
      return (new Schema().withType(type).withDescription(description).withNullable(nullable));
   }



   /*******************************************************************************
    ** Arrays always exist, including when empty.
    *******************************************************************************/
   private static Schema array(String itemSchema, String description)
   {
      return (field(Type.ARRAY, description, false).withItems(new Schema().withRef("#/components/schemas/" + itemSchema)));
   }
}

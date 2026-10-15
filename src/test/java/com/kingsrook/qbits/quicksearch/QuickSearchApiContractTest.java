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

package com.kingsrook.qbits.quicksearch;


import java.nio.file.Files;
import java.nio.file.Path;
import com.kingsrook.qbits.quicksearch.api.QuickSearchApi;
import com.kingsrook.qqq.middleware.javalin.specs.v1.MiddlewareVersionV1;
import com.kingsrook.qqq.middleware.javalin.tools.UiApiContract;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The committed contract is generated from exactly the registered specs.
 *******************************************************************************/
class QuickSearchApiContractTest
{
   /*******************************************************************************
    ** Opt-in regeneration follows core and collaboration's contract workflow.
    *******************************************************************************/
   @Test
   void contract() throws Exception
   {
      var version = new MiddlewareVersionV1();
      var extension = QuickSearchApi.extension();
      String generated = UiApiContract.generateExtensionYaml(version, extension);
      Path path = Path.of("src/main/resources/openapi/qqq-v1/quick-search.yaml");
      if(Boolean.getBoolean("quicksearch.openapi.update"))
      {
         Files.createDirectories(path.getParent());
         Files.writeString(path, generated);
      }
      assertThat(UiApiContract.check(generated, Files.readString(path), null, 1,
         "mvnw.cmd -Dtest=QuickSearchApiContractTest -Dquicksearch.openapi.update=true test")).isEmpty();
      var model = version.generateExtensionOpenAPIModel("qqq", extension);
      assertThat(model.getComponents().getSchemas().keySet()).allMatch(name -> name.startsWith("QuickSearch") || name.equals("BasicErrorResponseV1"));
      assertThat(model.getPaths().keySet()).containsExactlyInAnyOrder("/qqq/v1/qbit/quick-search/status", "/qqq/v1/qbit/quick-search/suggest");
      assertThat(extension.getSpecs()).hasSize(2);
      assertThat(generated).contains("getQuickSearchSuggestions", "QuickSearchSuggestV1", "QuickSearchSuggestGroupV1", "QuickSearchSuggestItemV1");
      var schemas = model.getComponents().getSchemas();
      assertThat(schemas.get("BasicErrorResponseV1").getProperties()).containsOnlyKeys("error", "code", "message", "fieldErrors", "traceId");
      assertThat(schemas.get("QuickSearchErrorV1").getDescription()).isEqualTo("Deprecated since 1.1.0: errors use BasicErrorResponseV1.");
      assertThat(generated).contains("getQuickSearchStatus").doesNotContain("#/components/schemas/QuickSearchErrorV1");
      assertThat(schemas.get("QuickSearchSuggestV1").getProperties()).containsOnlyKeys("query", "groups", "truncated");
      assertThat(schemas.get("QuickSearchSuggestGroupV1").getProperties()).containsOnlyKeys("tableName", "tableLabel", "hasMore", "items");
      assertThat(schemas.get("QuickSearchSuggestItemV1").getProperties()).containsOnlyKeys("recordId", "label", "subtitle");
      assertThat(schemas.get("QuickSearchSuggestItemV1").getProperties().get("subtitle").getNullable()).isTrue();
   }
}

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


import com.kingsrook.qbits.quicksearch.metadata.QuickSearchAdminAppMetaDataProducer;
import com.kingsrook.qqq.middleware.javalin.specs.extensions.MiddlewareSpecExtension;


/*******************************************************************************
 ** Supported host navigation and UI API extension identity.
 *******************************************************************************/
public final class QuickSearchApi
{
   public static final String APP_NAME = QuickSearchAdminAppMetaDataProducer.APP_NAME;
   public static final String HOME_HREF = "/app/" + APP_NAME;


   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private QuickSearchApi()
   {
   }



   /*******************************************************************************
    ** The spec registration shared by production and contract generation.
    *******************************************************************************/
   public static MiddlewareSpecExtension extension()
   {
      return (new MiddlewareSpecExtension().withName("quick-search").withSemanticVersion("1.1.0")
         .withDescription("Quick Search administration and suggestions").withSpec(QuickSearchStatusSpec.class).withSpec(QuickSearchSuggestSpec.class));
   }
}

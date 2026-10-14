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


import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.diagnostics.StatusDiagnostics;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;


/*******************************************************************************
 ** Supported entry point for hosts. Uses the current QContext and checks the
 ** Quick Search admin app permission on EVERY call, including cache hits.
 *******************************************************************************/
public final class QuickSearchStatusService
{
   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private QuickSearchStatusService()
   {
   }



   /*******************************************************************************
    ** Return a briefly cached snapshot, waiting at most two seconds for a refresh.
    ** Call with an authenticated QSession (or an explicitly trusted system session).
    *******************************************************************************/
   public static QuickSearchStatus get() throws QException
   {
      if(QContext.getQSession() == null)
      {
         throw (new QPermissionDeniedException("A session is required for Quick Search status."));
      }
      QuickSearchRuntime runtime = QuickSearchRuntime.get();
      if(runtime == null)
      {
         return (StatusDiagnostics.unavailable(QuickSearchStatus.State.DISABLED, null, QuickSearchApi.HOME_HREF));
      }
      String appName = runtime.getConfig().applyPrefix(QuickSearchApi.APP_NAME);
      if(QContext.getQInstance().getApp(appName) == null || !PermissionsHelper.hasAppPermission(new QueryInput(), appName))
      {
         throw (new QPermissionDeniedException("Quick Search admin permission is required."));
      }
      return (runtime.getStatusCache().get(runtime));
   }
}

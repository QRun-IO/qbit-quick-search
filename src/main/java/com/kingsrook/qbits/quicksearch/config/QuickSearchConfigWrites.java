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

package com.kingsrook.qbits.quicksearch.config;


import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QUser;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Marks the QBit's own writes to its config tables (priming, status updates
 ** made on the owner's behalf), so the table customizers can tell them from
 ** an admin's edits: only an admin's edit marks a row ownerEdited, writes a
 ** tombstone, or flags the index for a rebuild.
 *******************************************************************************/
public final class QuickSearchConfigWrites
{
   private static final ThreadLocal<Boolean> SYSTEM_WRITE = ThreadLocal.withInitial(() -> false);



   /*******************************************************************************
    ** Work that may throw.
    *******************************************************************************/
   @FunctionalInterface
   public interface Work<T>
   {
      /*******************************************************************************
       ** Do it.
       *******************************************************************************/
      T run() throws QException;
   }



   /*******************************************************************************
    ** Utility class - no instances.
    *******************************************************************************/
   private QuickSearchConfigWrites()
   {
   }



   /*******************************************************************************
    ** Run work as a system write (nested calls stay system writes).
    *******************************************************************************/
   public static <T> T asSystem(Work<T> work) throws QException
   {
      boolean previous = SYSTEM_WRITE.get();
      SYSTEM_WRITE.set(true);
      try
      {
         return (work.run());
      }
      finally
      {
         SYSTEM_WRITE.set(previous);
      }
   }



   /*******************************************************************************
    ** Whether the current thread is making a system write.
    *******************************************************************************/
   public static boolean isSystemWrite()
   {
      return (SYSTEM_WRITE.get());
   }



   /*******************************************************************************
    ** The current user's name for audit-style columns (pausedBy, removedBy):
    ** full name, else id, else "system".
    *******************************************************************************/
   public static String currentUserName()
   {
      QSession session = QContext.getQSession();
      QUser    user    = session == null ? null : session.getUser();
      if(user != null && StringUtils.hasContent(user.getFullName()))
      {
         return (user.getFullName());
      }
      if(user != null && StringUtils.hasContent(user.getIdReference()))
      {
         return (user.getIdReference());
      }
      return ("system");
   }

}

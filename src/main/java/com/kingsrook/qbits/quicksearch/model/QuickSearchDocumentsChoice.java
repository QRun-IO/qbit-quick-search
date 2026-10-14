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

package com.kingsrook.qbits.quicksearch.model;


import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.PossibleValueEnum;


/*******************************************************************************
 ** What happens to a table's search documents when it is disabled or
 ** removed: DELETE takes them out of the index now (they stop matching at
 ** once); KEEP leaves them in the index. A disabled table's kept documents
 ** are hidden from search and rebuilt when it is enabled again; a removed
 ** table's are swept by the next all-tables reconcile.
 *******************************************************************************/
public enum QuickSearchDocumentsChoice implements PossibleValueEnum<String>
{
   DELETE("Delete the documents from the search index"),
   KEEP("Keep the documents");

   private final String label;



   /*******************************************************************************
    ** Constructor
    *******************************************************************************/
   QuickSearchDocumentsChoice(String label)
   {
      this.label = label;
   }



   /*******************************************************************************
    ** The value stored in the database (the constant's name).
    *******************************************************************************/
   @Override
   public String getPossibleValueId()
   {
      return (name());
   }



   /*******************************************************************************
    ** The label shown in the UI.
    *******************************************************************************/
   @Override
   public String getPossibleValueLabel()
   {
      return (label);
   }



   /*******************************************************************************
    ** The constant with the given name, or the fallback when it is null or
    ** unknown (a value written by a newer version, or by hand).
    *******************************************************************************/
   public static QuickSearchDocumentsChoice fromName(String value, QuickSearchDocumentsChoice fallback)
   {
      if(value != null)
      {
         for(QuickSearchDocumentsChoice candidate : values())
         {
            if(candidate.name().equalsIgnoreCase(value.trim()))
            {
               return (candidate);
            }
         }
      }
      return (fallback);
   }

}

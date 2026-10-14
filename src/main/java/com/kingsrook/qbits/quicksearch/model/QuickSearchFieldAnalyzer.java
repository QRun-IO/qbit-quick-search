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
 ** How a searchable field's text is analyzed. PREFIX (the default) matches
 ** word prefixes as you type; WORD matches whole words; ENGLISH matches English
 ** word stems (run, running); EXACT matches the whole value, ignoring case.
 *******************************************************************************/
public enum QuickSearchFieldAnalyzer implements PossibleValueEnum<String>
{
   PREFIX("Prefix (as you type)"),
   WORD("Whole words"),
   ENGLISH("English (stemmed)"),
   EXACT("Exact value");

   private final String label;



   /*******************************************************************************
    ** Constructor
    *******************************************************************************/
   QuickSearchFieldAnalyzer(String label)
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
   public static QuickSearchFieldAnalyzer fromName(String value, QuickSearchFieldAnalyzer fallback)
   {
      if(value != null)
      {
         for(QuickSearchFieldAnalyzer candidate : values())
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

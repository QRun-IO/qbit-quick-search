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


/*******************************************************************************
 ** Minimal immutable suggestion response. Search internals never cross this API.
 *******************************************************************************/
public record QuickSearchSuggest(String query, List<Group> groups, Boolean truncated)
{
   /*******************************************************************************
    ** Freeze the group order for callers.
    *******************************************************************************/
   public QuickSearchSuggest
   {
      groups = List.copyOf(groups);
   }



   /*******************************************************************************
    ** One readable table, ordered by its highest scoring accessible hit.
    *******************************************************************************/
   public record Group(String tableName, String tableLabel, Boolean hasMore, List<Item> items)
   {
      /*******************************************************************************
       ** Freeze the per-table relevance order.
       *******************************************************************************/
      public Group
      {
         items = List.copyOf(items);
      }
   }



   /*******************************************************************************
    ** Only already-filtered labels and subtitles are exposed.
    *******************************************************************************/
   public record Item(String recordId, String label, String subtitle)
   {
   }
}

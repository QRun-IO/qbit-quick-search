/*
 * Copyright 2024 Kingsrook, LLC
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

package com.kingsrook.qbits.quicksearch.metadata;


import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSourceType;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitComponentMetaDataProducer;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.config.QuickSearchTablePossibleValueProviders;


/*******************************************************************************
 ** Possible value source listing the indexed tables, used by the process input
 ** screens (pick one table for a full reindex or reconcile) and to show stored
 ** table names by label.
 *******************************************************************************/
public class QuickSearchSearchableTablePossibleValueSourceProducer extends QBitComponentMetaDataProducer<QPossibleValueSource, QuickSearchQBitConfig>
{
   public static final String NAME = "quick_search_searchable_table";

   /** Possible-value source name before 1.1.0-RC.3 (camelCase). */
   @Deprecated(since = "1.1.0-RC.3")
   public static final String LEGACY_NAME = "quickSearchSearchableTable";



   /*******************************************************************************
    ** A custom source over the effective indexed tables (since 1.1.0-RC.3; it
    ** was an enum of the code-declared tables), so tables added at runtime
    ** appear at once, by label.
    *******************************************************************************/
   @Override
   public QPossibleValueSource produce(QInstance qInstance) throws QException
   {
      return (new QPossibleValueSource()
         .withName(getQBitConfig().applyTablePrefix(NAME))
         .withType(QPossibleValueSourceType.CUSTOM)
         .withIdType(QFieldType.STRING)
         .withCustomCodeReference(new QCodeReference(QuickSearchTablePossibleValueProviders.SearchableTables.class)));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public int getSortOrder()
   {
      return (100);
   }

}

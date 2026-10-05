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


import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValue;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSourceType;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitComponentMetaDataProducer;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.SearchableTableConfig;
import com.kingsrook.qbits.quicksearch.annotations.QuickSearchable;


/*******************************************************************************
 ** Enum possible value source listing the searchable tables (from annotated
 ** entity classes and config-driven tables), used by the process input screens
 ** so an operator can pick one table for a full reindex or reconcile.
 *******************************************************************************/
public class QuickSearchSearchableTablePossibleValueSourceProducer extends QBitComponentMetaDataProducer<QPossibleValueSource, QuickSearchQBitConfig>
{
   public static final String NAME = "quickSearchSearchableTable";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public QPossibleValueSource produce(QInstance qInstance) throws QException
   {
      QuickSearchQBitConfig config = getQBitConfig();

      Set<String> tableNames = new LinkedHashSet<>();
      for(Class<?> entityClass : config.getSearchableEntityClasses() == null ? List.<Class<?>>of() : config.getSearchableEntityClasses())
      {
         QuickSearchable annotation = entityClass.getAnnotation(QuickSearchable.class);
         if(annotation != null)
         {
            tableNames.add(annotation.tableName());
         }
      }
      for(SearchableTableConfig tableConfig : config.getSearchableTables() == null ? List.<SearchableTableConfig>of() : config.getSearchableTables())
      {
         if(tableConfig.getTableName() != null)
         {
            tableNames.add(tableConfig.getTableName());
         }
      }

      List<QPossibleValue<?>> values = new ArrayList<>();
      for(String tableName : tableNames)
      {
         QTableMetaData table = qInstance.getTable(tableName);
         String         label = table != null && table.getLabel() != null ? table.getLabel() : tableName;
         values.add(new QPossibleValue<>(tableName, label));
      }

      return (new QPossibleValueSource()
         .withName(config.applyPrefix(NAME))
         .withType(QPossibleValueSourceType.ENUM)
         .withEnumValues(values));
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

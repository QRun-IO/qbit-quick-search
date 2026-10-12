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


import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qqq.backend.core.actions.tables.GetAction;
import com.kingsrook.qqq.backend.core.actions.values.QCustomPossibleValueProvider;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.get.GetInput;
import com.kingsrook.qqq.backend.core.model.actions.values.SearchPossibleValueSourceInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValue;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitContext;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;


/*******************************************************************************
 ** Custom possible-value providers over the instance's tables and fields, for
 ** the config screens:
 ** - SearchableTables: the indexed tables (quick_search_searchable_table),
 **   shown by label wherever a table name is stored;
 ** - EligibleTables: tables the current user could add (readable, eligible,
 **   not yet indexed), for Add Search Index;
 ** - IndexableFields and TimestampFields: the indexable or date-time fields
 **   of the table the form is about, read from the form's other values
 **   (tableName, or the field row's quickSearchIndexId).
 *******************************************************************************/
public final class QuickSearchTablePossibleValueProviders
{

   /*******************************************************************************
    ** Utility holder - no instances.
    *******************************************************************************/
   private QuickSearchTablePossibleValueProviders()
   {
   }



   /*******************************************************************************
    ** The QBit's config: the runtime's, else the static facade's.
    *******************************************************************************/
   static QuickSearchQBitConfig config()
   {
      QuickSearchRuntime runtime = QuickSearchRuntime.get();
      return (runtime != null ? runtime.getConfig() : QuickSearchQBitContext.getConfig());
   }



   /*******************************************************************************
    ** Table possible values whose id is the table name and label its label.
    *******************************************************************************/
   abstract static class AbstractTableProvider implements QCustomPossibleValueProvider<String>
   {
      /*******************************************************************************
       ** The table names to offer.
       *******************************************************************************/
      abstract List<String> tableNames() throws QException;



      /*******************************************************************************
       ** Any table in the instance, by label.
       *******************************************************************************/
      @Override
      public QPossibleValue<String> getPossibleValue(Serializable idValue)
      {
         String         tableName = ValueUtils.getValueAsString(idValue);
         QTableMetaData table     = tableName == null || QContext.getQInstance() == null ? null : QContext.getQInstance().getTable(tableName);
         return (tableName == null ? null : new QPossibleValue<>(tableName, table == null ? tableName : QuickSearchFieldRules.label(table)));
      }



      /*******************************************************************************
       ** The offered tables matching the search term (or ids).
       *******************************************************************************/
      @Override
      public List<QPossibleValue<String>> search(SearchPossibleValueSourceInput input) throws QException
      {
         List<QPossibleValue<String>> values = new ArrayList<>();
         for(String tableName : tableNames())
         {
            QPossibleValue<String> value = getPossibleValue(tableName);
            if(matches(input, value))
            {
               values.add(value);
            }
         }
         return (values);
      }
   }



   /*******************************************************************************
    ** The indexed tables.
    *******************************************************************************/
   public static class SearchableTables extends AbstractTableProvider
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      List<String> tableNames()
      {
         QuickSearchRuntime runtime = QuickSearchRuntime.get();
         List<String>       names   = new ArrayList<>();
         List<QuickSearchableTableConfig> tables = runtime != null ? runtime.getDiscoveredTables() : QuickSearchQBitContext.getDiscoveredTables();
         for(QuickSearchableTableConfig tableConfig : CollectionUtils.nonNullList(tables))
         {
            names.add(tableConfig.getTableName());
         }
         return (names);
      }
   }



   /*******************************************************************************
    ** The tables the current user could add to Quick Search.
    *******************************************************************************/
   public static class EligibleTables extends AbstractTableProvider
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      List<String> tableNames() throws QException
      {
         QuickSearchQBitConfig config  = config();
         Set<String>           indexed = new LinkedHashSet<>();
         for(QRecord row : QuickSearchConfigPrimer.queryAll(config.getQuickSearchIndexTableName()))
         {
            indexed.add(row.getValueString("tableName"));
         }
         List<String> names = new ArrayList<>();
         QuickSearchFieldRules.eligibleTables(QContext.getQInstance(), config, indexed).forEach(t -> names.add(t.getName()));
         return (names);
      }
   }



   /*******************************************************************************
    ** Field possible values of the form's table; id is the field name.
    *******************************************************************************/
   abstract static class AbstractFieldProvider implements QCustomPossibleValueProvider<String>
   {
      /*******************************************************************************
       ** Whether to offer a field.
       *******************************************************************************/
      abstract boolean offers(QTableMetaData table, QFieldMetaData field);



      /*******************************************************************************
       ** A field name is shown as itself: without the form's values the table is
       ** not known here.
       *******************************************************************************/
      @Override
      public QPossibleValue<String> getPossibleValue(Serializable idValue)
      {
         String fieldName = ValueUtils.getValueAsString(idValue);
         return (fieldName == null ? null : new QPossibleValue<>(fieldName, fieldName));
      }



      /*******************************************************************************
       ** The offered fields of the form's table, labeled "Label (name)".
       *******************************************************************************/
      @Override
      public List<QPossibleValue<String>> search(SearchPossibleValueSourceInput input) throws QException
      {
         List<QPossibleValue<String>> values = new ArrayList<>();
         QTableMetaData               table  = resolveTable(input.getOtherValues());
         if(table == null)
         {
            return (values);
         }
         for(QFieldMetaData field : table.getFields().values())
         {
            if(!offers(table, field))
            {
               continue;
            }
            QPossibleValue<String> value = new QPossibleValue<>(field.getName(), (field.getLabel() == null ? field.getName() : field.getLabel()) + " (" + field.getName() + ")");
            if(matches(input, value))
            {
               values.add(value);
            }
         }
         return (values);
      }
   }



   /*******************************************************************************
    ** Fields that can be indexed.
    *******************************************************************************/
   public static class IndexableFields extends AbstractFieldProvider
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      boolean offers(QTableMetaData table, QFieldMetaData field)
      {
         return (QuickSearchFieldRules.isIndexable(table, field.getName()));
      }
   }



   /*******************************************************************************
    ** Date-time fields, for the basepull timestamp.
    *******************************************************************************/
   public static class TimestampFields extends AbstractFieldProvider
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      boolean offers(QTableMetaData table, QFieldMetaData field)
      {
         return (QuickSearchFieldRules.isTimestampField(table, field.getName()));
      }
   }



   /*******************************************************************************
    ** The table a form is about: its tableName value, else the table of its
    ** quickSearchIndexId row. Null when neither resolves.
    *******************************************************************************/
   static QTableMetaData resolveTable(Map<String, Serializable> otherValues) throws QException
   {
      QInstance qInstance = QContext.getQInstance();
      if(otherValues == null || qInstance == null)
      {
         return (null);
      }

      String tableName = ValueUtils.getValueAsString(otherValues.get("tableName"));
      if(!StringUtils.hasContent(tableName) && otherValues.get("quickSearchIndexId") != null)
      {
         GetInput getInput = new GetInput(config().getQuickSearchIndexTableName());
         getInput.setPrimaryKey(ValueUtils.getValueAsInteger(otherValues.get("quickSearchIndexId")));
         QRecord row = new GetAction().execute(getInput).getRecord();
         tableName = row == null ? null : row.getValueString("tableName");
      }
      return (StringUtils.hasContent(tableName) ? qInstance.getTable(tableName) : null);
   }



   /*******************************************************************************
    ** Whether a value is wanted: in idList when one is given, else its label or
    ** id contains the search term (any case).
    *******************************************************************************/
   static boolean matches(SearchPossibleValueSourceInput input, QPossibleValue<String> value)
   {
      if(value == null)
      {
         return (false);
      }
      if(CollectionUtils.nullSafeHasContents(input.getIdList()))
      {
         return (input.getIdList().stream().anyMatch(id -> value.getId().equals(ValueUtils.getValueAsString(id))));
      }
      String term = input.getSearchTerm();
      if(!StringUtils.hasContent(term))
      {
         return (true);
      }
      String lower = term.toLowerCase(Locale.ROOT);
      return (value.getLabel().toLowerCase(Locale.ROOT).contains(lower) || value.getId().toLowerCase(Locale.ROOT).contains(lower));
   }

}

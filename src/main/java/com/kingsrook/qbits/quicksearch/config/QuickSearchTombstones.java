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
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.model.QuickSearchConfigTombstone;


/*******************************************************************************
 ** Reads and writes quick_search_config_tombstone: what an admin removed, so
 ** priming does not add it back. Every write is a system write. With no
 ** tombstone table in the instance these do nothing (and priming then cannot
 ** tell a removed code table from a new one; add the table's DDL).
 *******************************************************************************/
public final class QuickSearchTombstones
{

   /*******************************************************************************
    ** Utility class - no instances.
    *******************************************************************************/
   private QuickSearchTombstones()
   {
   }



   /*******************************************************************************
    ** The tombstone table's name, or null when it is not in the instance.
    *******************************************************************************/
   static String tableName()
   {
      QuickSearchQBitConfig config = QuickSearchTablePossibleValueProviders.config();
      String                name   = config == null ? null : config.applyTablePrefix(QuickSearchConfigTombstone.TABLE_NAME);
      return (name != null && QContext.getQInstance() != null && QContext.getQInstance().getTable(name) != null ? name : null);
   }



   /*******************************************************************************
    ** Remember that a table (fieldName null) or one of its fields was removed.
    *******************************************************************************/
   public static void add(String tableName, String fieldName, String reason) throws QException
   {
      String name = tableName();
      if(name == null || !find(name, tableName, fieldName).isEmpty())
      {
         return;
      }
      QuickSearchConfigWrites.asSystem(() -> QuickSearchConfigPrimer.insertOne(name, new QRecord()
         .withValue("tableName", tableName)
         .withValue("fieldName", fieldName)
         .withValue("removedBy", QuickSearchConfigWrites.currentUserName())
         .withValue("reason", reason)));
   }



   /*******************************************************************************
    ** Forget a removal (the admin added the table or field back).
    *******************************************************************************/
   public static void remove(String tableName, String fieldName) throws QException
   {
      String name = tableName();
      if(name == null)
      {
         return;
      }
      List<Serializable> ids = new ArrayList<>();
      find(name, tableName, fieldName).forEach(r -> ids.add(r.getValue("id")));
      if(ids.isEmpty())
      {
         return;
      }
      QuickSearchConfigWrites.asSystem(() ->
      {
         DeleteInput deleteInput = new DeleteInput();
         deleteInput.setTableName(name);
         deleteInput.setPrimaryKeys(ids);
         return (new DeleteAction().execute(deleteInput));
      });
   }



   /*******************************************************************************
    ** Whether a table (fieldName null) or field has a tombstone.
    *******************************************************************************/
   public static boolean exists(String tableName, String fieldName) throws QException
   {
      String name = tableName();
      return (name != null && !find(name, tableName, fieldName).isEmpty());
   }



   /*******************************************************************************
    ** The tombstones for one table (fieldName null) or field.
    *******************************************************************************/
   static List<QRecord> find(String name, String tableName, String fieldName) throws QException
   {
      QQueryFilter filter = new QQueryFilter(new QFilterCriteria("tableName", QCriteriaOperator.EQUALS, tableName));
      filter.addCriteria(fieldName == null
         ? new QFilterCriteria("fieldName", QCriteriaOperator.IS_BLANK)
         : new QFilterCriteria("fieldName", QCriteriaOperator.EQUALS, fieldName));
      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(name);
      queryInput.setFilter(filter);
      return (CollectionUtils.nonNullList(new QueryAction().execute(queryInput).getRecords()));
   }

}

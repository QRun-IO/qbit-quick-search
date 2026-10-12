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

package com.kingsrook.qbits.quicksearch.diagnostics;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.tables.CountAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.count.CountInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;


/*******************************************************************************
 ** Source-table reads for the diagnostics, in the current session (so table
 ** permissions and record security locks apply, as for any query the user
 ** runs). Records are read the way indexing reads them: display values
 ** generated and possible values translated, so a document built from them
 ** is exactly the document indexing would write.
 *******************************************************************************/
public final class DiagnosticRecordReads
{
   public static final int MAX_IDS_PER_QUERY = 1_000;



   /*******************************************************************************
    ** Utility class - no instances.
    *******************************************************************************/
   private DiagnosticRecordReads()
   {
   }



   /*******************************************************************************
    ** Records of a table by primary key (as strings), keyed by primary key
    ** string, read as indexing reads them. Ids that do not exist or that the
    ** session cannot read are absent. Ids that do not convert to the key's
    ** type are skipped.
    *******************************************************************************/
   public static Map<String, QRecord> loadForIndexing(String tableName, Collection<String> recordIds) throws QException
   {
      QTableMetaData       table   = requireTable(tableName);
      String               pk      = table.getPrimaryKeyField();
      Map<String, QRecord> records = new LinkedHashMap<>();
      List<Serializable>   ids     = typedIds(table, recordIds);

      for(int start = 0; start < ids.size(); start += MAX_IDS_PER_QUERY)
      {
         QueryInput queryInput = new QueryInput();
         queryInput.setTableName(tableName);
         queryInput.setFilter(new QQueryFilter(new QFilterCriteria(pk, QCriteriaOperator.IN, ids.subList(start, Math.min(ids.size(), start + MAX_IDS_PER_QUERY)))));
         queryInput.setShouldGenerateDisplayValues(true);
         queryInput.setShouldTranslatePossibleValues(true);
         for(QRecord record : CollectionUtils.nonNullList(new QueryAction().execute(queryInput).getRecords()))
         {
            records.put(record.getValueString(pk), record);
         }
      }
      return (records);
   }



   /*******************************************************************************
    ** Up to limit records of a table ordered by primary key (ascending or
    ** descending), read as indexing reads them.
    *******************************************************************************/
   public static List<QRecord> loadEdge(String tableName, boolean ascending, int limit) throws QException
   {
      QTableMetaData table      = requireTable(tableName);
      QueryInput     queryInput = new QueryInput();
      queryInput.setTableName(tableName);
      queryInput.setFilter(new QQueryFilter().withOrderBy(new QFilterOrderBy(table.getPrimaryKeyField(), ascending)).withLimit(limit));
      queryInput.setShouldGenerateDisplayValues(true);
      queryInput.setShouldTranslatePossibleValues(true);
      return (CollectionUtils.nonNullList(new QueryAction().execute(queryInput).getRecords()));
   }



   /*******************************************************************************
    ** The primary keys (as strings) among recordIds that exist and that the
    ** session can read.
    *******************************************************************************/
   public static List<String> existingIds(String tableName, Collection<String> recordIds) throws QException
   {
      QTableMetaData     table  = requireTable(tableName);
      String             pk     = table.getPrimaryKeyField();
      List<String>       found  = new ArrayList<>();
      List<Serializable> ids    = typedIds(table, recordIds);

      for(int start = 0; start < ids.size(); start += MAX_IDS_PER_QUERY)
      {
         QueryInput queryInput = new QueryInput();
         queryInput.setTableName(tableName);
         queryInput.setFilter(new QQueryFilter(new QFilterCriteria(pk, QCriteriaOperator.IN, ids.subList(start, Math.min(ids.size(), start + MAX_IDS_PER_QUERY)))));
         for(QRecord record : CollectionUtils.nonNullList(new QueryAction().execute(queryInput).getRecords()))
         {
            found.add(record.getValueString(pk));
         }
      }
      return (found);
   }



   /*******************************************************************************
    ** Row count of a table in the current session.
    *******************************************************************************/
   public static Integer count(String tableName) throws QException
   {
      CountInput countInput = new CountInput();
      countInput.setTableName(tableName);
      return (new CountAction().execute(countInput).getCount());
   }



   /*******************************************************************************
    ** Ids converted to the primary key's type; ids that do not convert are
    ** skipped (they cannot exist).
    *******************************************************************************/
   static List<Serializable> typedIds(QTableMetaData table, Collection<String> recordIds)
   {
      QFieldMetaData     pkField = table.getField(table.getPrimaryKeyField());
      List<Serializable> ids     = new ArrayList<>();
      for(String recordId : CollectionUtils.nonNullCollection(recordIds))
      {
         try
         {
            Serializable id = ValueUtils.getValueAsFieldType(pkField.getType(), recordId);
            if(id != null && !ids.contains(id))
            {
               ids.add(id);
            }
         }
         catch(Exception e)
         {
            //////////////////////////////////////////////////////////
            // e.g. "abc" for an integer key: no such record exists //
            //////////////////////////////////////////////////////////
         }
      }
      return (ids);
   }



   /*******************************************************************************
    *******************************************************************************/
   private static QTableMetaData requireTable(String tableName) throws QException
   {
      QTableMetaData table = QContext.getQInstance() == null ? null : QContext.getQInstance().getTable(tableName);
      if(table == null)
      {
         throw (new QException("Table [" + tableName + "] is not in the instance"));
      }
      return (table);
   }

}

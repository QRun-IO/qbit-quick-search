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

package com.kingsrook.qbits.quicksearch.customizers;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizerInterface;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitContext;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.publisher.IndexEvent;
import com.kingsrook.qbits.quicksearch.publisher.IndexEventAction;
import com.kingsrook.qbits.quicksearch.publisher.IndexEventPublisher;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Post-update table customizer that re-indexes updated records in the Quick
 ** Search OpenSearch index.
 **
 ** An update's records may hold only the fields that changed, so each updated
 ** record is re-read from the source table (in the update's transaction) and
 ** the full record is indexed. Indexing the sparse record would drop the
 ** unchanged fields from the document.
 **
 ** QQQ instantiates this class via reflection using the no-arg constructor.
 ** The publisher and table configuration are obtained from the static
 ** QuickSearchQBitContext at call time.
 *******************************************************************************/
public class QuickSearchPostUpdateCustomizer implements TableCustomizerInterface
{
   private static final QLogger LOG = QLogger.getLogger(QuickSearchPostUpdateCustomizer.class);



   /*******************************************************************************
    ** No-arg constructor required by QQQ's QCodeReference instantiation mechanism.
    *******************************************************************************/
   public QuickSearchPostUpdateCustomizer()
   {
   }



   /*******************************************************************************
    ** Post-update hook: re-reads the successfully updated records from the
    ** source table, builds INDEX events from them, and publishes them via the
    ** configured IndexEventPublisher. Records with errors were not updated, so
    ** they are skipped.
    **
    ** If the publisher is null (QBit not yet initialized) or if publishing fails,
    ** a warning is logged and the original records are returned without throwing,
    ** so as never to block the underlying QQQ update operation.
    *******************************************************************************/
   @Override
   public List<QRecord> postUpdate(UpdateInput updateInput, List<QRecord> records, Optional<List<QRecord>> oldRecordList) throws QException
   {
      if(records == null || records.isEmpty())
      {
         return records;
      }

      IndexEventPublisher publisher = QuickSearchQBitContext.getPublisher();
      if(publisher == null)
      {
         LOG.warn("QuickSearch publisher is null; skipping post-update indexing", logPair("tableName", updateInput.getTableName()));
         return records;
      }

      try
      {
         String tableName = updateInput.getTableName();
         QuickSearchableTableConfig tableConfig = QuickSearchQBitContext.getTableConfig(tableName);
         String primaryKeyField = (tableConfig != null && tableConfig.getPrimaryKeyField() != null) ? tableConfig.getPrimaryKeyField() : "id";

         List<IndexEvent> events = new ArrayList<>();
         for(QRecord record : fetchCurrentRecords(updateInput, records, primaryKeyField))
         {
            events.add(new IndexEvent()
               .withAction(IndexEventAction.INDEX)
               .withTableName(tableName)
               .withRecordId(record.getValueString(primaryKeyField))
               .withRecord(record));
         }

         if(!events.isEmpty())
         {
            publisher.publishIndexEvents(events);
         }
      }
      catch(Exception e)
      {
         LOG.warn("Failed to publish post-update index events", e, logPair("tableName", updateInput.getTableName()));
      }

      return records;
   }



   /*******************************************************************************
    ** Query the source table for the full, current version of each successfully
    ** updated record. Uses the update's transaction, so uncommitted changes are
    ** visible.
    *******************************************************************************/
   private List<QRecord> fetchCurrentRecords(UpdateInput updateInput, List<QRecord> updatedRecords, String primaryKeyField) throws QException
   {
      List<Serializable> primaryKeys = new ArrayList<>();
      for(QRecord record : updatedRecords)
      {
         Serializable primaryKey = record.getValue(primaryKeyField);
         if(primaryKey != null && CollectionUtils.nullSafeIsEmpty(record.getErrors()))
         {
            primaryKeys.add(primaryKey);
         }
      }

      if(primaryKeys.isEmpty())
      {
         return (new ArrayList<>());
      }

      QueryInput queryInput = new QueryInput();
      queryInput.setTableName(updateInput.getTableName());
      queryInput.setTransaction(updateInput.getTransaction());
      queryInput.setFilter(new QQueryFilter(new QFilterCriteria(primaryKeyField, QCriteriaOperator.IN, primaryKeys)));

      return (CollectionUtils.nonNullList(new QueryAction().execute(queryInput).getRecords()));
   }

}

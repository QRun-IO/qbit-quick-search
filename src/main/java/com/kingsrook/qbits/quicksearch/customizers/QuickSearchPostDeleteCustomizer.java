/*
 * Copyright 2024 Kingsrook, LLC
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

package com.kingsrook.qbits.quicksearch.customizers;


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizerInterface;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitContext;
import com.kingsrook.qbits.quicksearch.publisher.IndexEvent;
import com.kingsrook.qbits.quicksearch.publisher.IndexEventAction;
import com.kingsrook.qbits.quicksearch.publisher.IndexEventPublisher;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Post-delete table customizer that removes deleted records from the Quick
 ** Search OpenSearch index.
 **
 ** QQQ instantiates this class via reflection using the no-arg constructor.
 ** The publisher and table configuration are obtained from the static
 ** QuickSearchQBitContext at call time.
 *******************************************************************************/
public class QuickSearchPostDeleteCustomizer implements TableCustomizerInterface
{
   private static final QLogger LOG = QLogger.getLogger(QuickSearchPostDeleteCustomizer.class);



   /*******************************************************************************
    ** No-arg constructor required by QQQ's QCodeReference instantiation mechanism.
    *******************************************************************************/
   public QuickSearchPostDeleteCustomizer()
   {
   }



   /*******************************************************************************
    ** Post-delete hook: builds DELETE events for each deleted record and publishes
    ** them via the configured IndexEventPublisher.
    **
    ** If the publisher is null (QBit not yet initialized) or if publishing fails,
    ** a warning is logged and the original records are returned without throwing,
    ** so as never to block the underlying QQQ delete operation.
    *******************************************************************************/
   @Override
   public List<QRecord> postDelete(DeleteInput deleteInput, List<QRecord> records) throws QException
   {
      if(records == null || records.isEmpty())
      {
         return records;
      }

      IndexEventPublisher publisher = QuickSearchQBitContext.getPublisher();
      if(publisher == null)
      {
         LOG.warn("QuickSearch publisher is null; skipping post-delete indexing", logPair("tableName", deleteInput.getTableName()));
         return records;
      }

      try
      {
         String tableName = deleteInput.getTableName();
         String primaryKeyField = QContext.getQInstance().getTable(tableName).getPrimaryKeyField();

         List<IndexEvent> events = new ArrayList<>();
         for(QRecord record : records)
         {
            events.add(new IndexEvent()
               .withAction(IndexEventAction.DELETE)
               .withTableName(tableName)
               .withRecordId(record.getValueString(primaryKeyField)));
         }

         publisher.publishDeleteEvents(events);
      }
      catch(Exception e)
      {
         LOG.warn("Failed to publish post-delete index events", e, logPair("tableName", deleteInput.getTableName()));
      }

      return records;
   }

}

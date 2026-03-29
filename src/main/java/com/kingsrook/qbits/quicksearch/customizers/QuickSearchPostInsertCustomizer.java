/*
 * QBit Quick Search
 * Copyright (C) 2024-2025 QRun-IO, LLC
 * https://www.qrun.io | https://github.com/QRun-IO
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.kingsrook.qbits.quicksearch.customizers;


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizerInterface;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitContext;
import com.kingsrook.qbits.quicksearch.publisher.IndexEvent;
import com.kingsrook.qbits.quicksearch.publisher.IndexEventAction;
import com.kingsrook.qbits.quicksearch.publisher.IndexEventPublisher;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Post-insert table customizer that indexes newly inserted records in the
 ** Quick Search OpenSearch index.
 **
 ** QQQ instantiates this class via reflection using the no-arg constructor.
 ** The publisher and table configuration are obtained from the static
 ** QuickSearchQBitContext at call time.
 *******************************************************************************/
public class QuickSearchPostInsertCustomizer implements TableCustomizerInterface
{
   private static final QLogger LOG = QLogger.getLogger(QuickSearchPostInsertCustomizer.class);



   /*******************************************************************************
    ** No-arg constructor required by QQQ's QCodeReference instantiation mechanism.
    *******************************************************************************/
   public QuickSearchPostInsertCustomizer()
   {
   }



   /*******************************************************************************
    ** Post-insert hook: builds INDEX events for each inserted record and publishes
    ** them via the configured IndexEventPublisher.
    **
    ** If the publisher is null (QBit not yet initialized) or if publishing fails,
    ** a warning is logged and the original records are returned without throwing,
    ** so as never to block the underlying QQQ insert operation.
    *******************************************************************************/
   @Override
   public List<QRecord> postInsert(InsertInput insertInput, List<QRecord> records) throws QException
   {
      if(records == null || records.isEmpty())
      {
         return records;
      }

      IndexEventPublisher publisher = QuickSearchQBitContext.getPublisher();
      if(publisher == null)
      {
         LOG.warn("QuickSearch publisher is null; skipping post-insert indexing", logPair("tableName", insertInput.getTableName()));
         return records;
      }

      try
      {
         String tableName = insertInput.getTableName();
         String primaryKeyField = QContext.getQInstance().getTable(tableName).getPrimaryKeyField();

         List<IndexEvent> events = new ArrayList<>();
         for(QRecord record : records)
         {
            events.add(new IndexEvent()
               .withAction(IndexEventAction.INDEX)
               .withTableName(tableName)
               .withRecordId(record.getValueString(primaryKeyField))
               .withRecord(record));
         }

         publisher.publishIndexEvents(events);
      }
      catch(Exception e)
      {
         LOG.warn("Failed to publish post-insert index events", e, logPair("tableName", insertInput.getTableName()));
      }

      return records;
   }

}

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
import com.kingsrook.qqq.backend.core.model.metadata.MetaDataProducerMultiOutput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitComponentMetaDataProducer;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitProductionContext;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;


/*******************************************************************************
 ** Produces the quickSearchAdmin app from the tables and processes the other
 ** component producers added to the production output. Runs last.
 *******************************************************************************/
public class QuickSearchAdminAppMetaDataProducer extends QBitComponentMetaDataProducer<QAppMetaData, QuickSearchQBitConfig>
{
   public static final String APP_NAME = "quickSearchAdmin";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public QAppMetaData produce(QInstance qInstance) throws QException
   {
      QuickSearchQBitConfig config = getQBitConfig();

      QAppMetaData app = new QAppMetaData()
         .withName(config.applyPrefix(APP_NAME))
         .withLabel("Quick Search Admin")
         .withIcon(new QIcon("search"))
         .withPermissionRules(config.getAdminPermissionRules() == null ? null : config.getAdminPermissionRules().clone());

      MetaDataProducerMultiOutput produced = QBitProductionContext.peekMetaDataProducerMultiOutput();
      if(produced == null)
      {
         return (app);
      }

      for(String tableName : new String[] { QuickSearchIndex.TABLE_NAME, QuickSearchIndexRun.TABLE_NAME, QuickSearchFailedEvent.TABLE_NAME })
      {
         QTableMetaData table = produced.get(QTableMetaData.class, config.applyPrefix(tableName));
         if(table != null)
         {
            app.withChild(table);
         }
      }

      for(String processName : new String[] { QuickSearchProcessMetaDataHelper.BASEPULL_PROCESS_NAME, QuickSearchProcessMetaDataHelper.FULL_REINDEX_PROCESS_NAME, QuickSearchProcessMetaDataHelper.RECONCILE_PROCESS_NAME })
      {
         QProcessMetaData process = produced.get(QProcessMetaData.class, config.applyPrefix(processName));
         if(process != null)
         {
            app.withChild(process);
         }
      }

      return (app);
   }



   /*******************************************************************************
    ** last, so the tables and processes exist in the output
    *******************************************************************************/
   @Override
   public int getSortOrder()
   {
      return (900);
   }

}

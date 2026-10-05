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
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitComponentMetaDataProducer;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;


/*******************************************************************************
 ** Produces the reconcile process when enableMaintenanceProcesses is true.
 *******************************************************************************/
public class QuickSearchReconcileProcessMetaDataProducer extends QBitComponentMetaDataProducer<QProcessMetaData, QuickSearchQBitConfig>
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public QProcessMetaData produce(QInstance qInstance) throws QException
   {
      return (QuickSearchProcessMetaDataHelper.buildReconcileProcess(getQBitConfig()));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public boolean isEnabled()
   {
      return (getQBitConfig() != null && Boolean.TRUE.equals(getQBitConfig().getEnableMaintenanceProcesses()));
   }



   /*******************************************************************************
    ** after tables, before the app
    *******************************************************************************/
   @Override
   public int getSortOrder()
   {
      return (200);
   }

}

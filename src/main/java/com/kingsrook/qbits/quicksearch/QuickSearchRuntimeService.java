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

package com.kingsrook.qbits.quicksearch;


import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.QRuntimeServiceInterface;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;


/*******************************************************************************
 ** Lifecycle hook for hosts that start through QApplicationLauncher: prepares
 ** the OpenSearch index on start (per startupMode) and closes the transport on
 ** stop. Hosts without a launcher get the same start behaviour from produce()
 ** in FAIL_FAST mode and lazily on first use in DEGRADED mode.
 *******************************************************************************/
public class QuickSearchRuntimeService implements QRuntimeServiceInterface
{
   private static final QLogger LOG = QLogger.getLogger(QuickSearchRuntimeService.class);

   private QuickSearchRuntime runtime;



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getName()
   {
      return ("quickSearch");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void start(QInstance qInstance) throws QException
   {
      runtime = findRuntime(qInstance);
      if(runtime == null)
      {
         LOG.warn("Quick Search runtime service started, but no produced Quick Search QBit was found in the QInstance");
         return;
      }
      runtime.start();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void stop()
   {
      if(runtime != null)
      {
         runtime.close();
      }
   }



   /*******************************************************************************
    ** Find the runtime on the instance's Quick Search QBit (not through
    ** QContext, which may not be set when the launcher starts services).
    *******************************************************************************/
   static QuickSearchRuntime findRuntime(QInstance qInstance)
   {
      if(qInstance == null || qInstance.getQBits() == null)
      {
         return (null);
      }
      for(QBitMetaData qBitMetaData : qInstance.getQBits().values())
      {
         if(qBitMetaData.getConfig() instanceof QuickSearchQBitConfig config && config.getRuntime() != null)
         {
            return (config.getRuntime());
         }
      }
      return (null);
   }

}

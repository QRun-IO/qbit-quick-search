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

package com.kingsrook.qbits.quicksearch.metadata;


import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.developer.DeveloperPanelsMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitComponentMetaDataProducer;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.diagnostics.QuickSearchDeveloperPanelProvider;


/*******************************************************************************
 ** Registers the Quick Search record developer panel
 ** ({@link QuickSearchDeveloperPanelProvider}) in the instance's developer
 ** panels. The provider decides per table and user whether the panel shows
 ** (indexed tables, users who may read Quick Search Index). Registration is
 ** idempotent, so producing the QBit again adds nothing twice.
 *******************************************************************************/
public class QuickSearchDeveloperPanelsMetaDataProducer extends QBitComponentMetaDataProducer<DeveloperPanelsMetaData, QuickSearchQBitConfig>
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public DeveloperPanelsMetaData produce(QInstance qInstance) throws QException
   {
      return (DeveloperPanelsMetaData.ofOrWithNew(qInstance).withProvider(new QCodeReference(QuickSearchDeveloperPanelProvider.class)));
   }

}

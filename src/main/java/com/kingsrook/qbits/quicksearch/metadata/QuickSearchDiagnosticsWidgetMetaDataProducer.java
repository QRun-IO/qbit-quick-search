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
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.WidgetType;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitComponentMetaDataProducer;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.diagnostics.QuickSearchDiagnosticsWidgetRenderer;


/*******************************************************************************
 ** Produces the Search Diagnostics widget (quickSearchDiagnostics) when
 ** enableDiagnosticTools is true. The admin app shows it on its home; a host
 ** with its own Quick Search home adds it there by name.
 *******************************************************************************/
public class QuickSearchDiagnosticsWidgetMetaDataProducer extends QBitComponentMetaDataProducer<QWidgetMetaData, QuickSearchQBitConfig>
{
   public static final String WIDGET_NAME = "quickSearchDiagnostics";



   /*******************************************************************************
    *******************************************************************************/
   @Override
   public QWidgetMetaData produce(QInstance qInstance) throws QException
   {
      QuickSearchQBitConfig config = getQBitConfig();
      return (new QWidgetMetaData()
         .withName(config.applyPrefix(WIDGET_NAME))
         .withLabel("Search Diagnostics")
         .withIcon("troubleshoot")
         .withType(WidgetType.HTML.getType())
         .withGridColumns(12)
         .withIsCard(true)
         .withCodeReference(new QCodeReference(QuickSearchDiagnosticsWidgetRenderer.class))
         .withPermissionRules(config.getDebugPermissionRules() == null ? null : config.getDebugPermissionRules().clone()));
   }



   /*******************************************************************************
    *******************************************************************************/
   @Override
   public boolean isEnabled()
   {
      return (getQBitConfig() != null && Boolean.TRUE.equals(getQBitConfig().getEnableDiagnosticTools()));
   }



   /*******************************************************************************
    ** after tables, before the app
    *******************************************************************************/
   @Override
   public int getSortOrder()
   {
      return (300);
   }

}

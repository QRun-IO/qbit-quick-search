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


import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.UniqueKey;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;


/*******************************************************************************
 ** Builds the QTableMetaData for the three operational tables. Shared by the
 ** component producers and by tests that need the same shapes.
 *******************************************************************************/
public class QuickSearchTableMetaDataHelper
{

   /*******************************************************************************
    ** quickSearchIndex: one row per indexed table.
    *******************************************************************************/
   public static QTableMetaData buildIndexTable(QuickSearchQBitConfig config)
   {
      return (new QTableMetaData()
         .withName(config.applyPrefix(QuickSearchIndex.TABLE_NAME))
         .withLabel("Quick Search Index")
         .withIcon(new QIcon("manage_search"))
         .withBackendName(config.getBackendName())
         .withPrimaryKeyField("id")
         .withRecordLabelFields("tableName")
         .withUniqueKey(new UniqueKey("tableName"))
         .withPermissionRules(config.getTablePermissionRules() == null ? null : config.getTablePermissionRules().clone())
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("tableName", QFieldType.STRING).withIsRequired(true).withIsEditable(false))
         .withField(new QFieldMetaData("enabled", QFieldType.BOOLEAN))
         .withField(new QFieldMetaData("basepullIntervalMinutes", QFieldType.INTEGER))
         .withField(new QFieldMetaData("basepullTimestampField", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("searchableFieldsJson", QFieldType.TEXT).withIsEditable(false))
         .withField(new QFieldMetaData("lastBasepullTime", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("lastFullReindexTime", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("lastReconcileTime", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("lastRunStatus", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("lastErrorMessage", QFieldType.TEXT).withIsEditable(false))
         .withField(new QFieldMetaData("recordCount", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("documentCount", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("realTimeErrorCount", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("status", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("createDate", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME).withIsEditable(false)));
   }



   /*******************************************************************************
    ** quickSearchIndexRun: history of basepull, reindex and reconcile runs.
    *******************************************************************************/
   public static QTableMetaData buildIndexRunTable(QuickSearchQBitConfig config)
   {
      return (new QTableMetaData()
         .withName(config.applyPrefix(QuickSearchIndexRun.TABLE_NAME))
         .withLabel("Quick Search Index Run")
         .withIcon(new QIcon("history"))
         .withBackendName(config.getBackendName())
         .withPrimaryKeyField("id")
         .withPermissionRules(config.getTablePermissionRules() == null ? null : config.getTablePermissionRules().clone())
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("quickSearchIndexId", QFieldType.INTEGER).withIsEditable(false)
            .withPossibleValueSourceName(config.applyPrefix(QuickSearchIndex.TABLE_NAME)))
         .withField(new QFieldMetaData("runType", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("status", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("startTime", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("endTime", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("recordsProcessed", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("recordsIndexed", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("errorCount", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("errorMessage", QFieldType.TEXT).withIsEditable(false))
         .withField(new QFieldMetaData("createDate", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME).withIsEditable(false)));
   }



   /*******************************************************************************
    ** quickSearchFailedEvent: real-time index or delete events that could not
    ** reach OpenSearch, replayed by the scheduled basepull.
    *******************************************************************************/
   public static QTableMetaData buildFailedEventTable(QuickSearchQBitConfig config)
   {
      return (new QTableMetaData()
         .withName(config.applyPrefix(QuickSearchFailedEvent.TABLE_NAME))
         .withLabel("Quick Search Failed Event")
         .withIcon(new QIcon("error_outline"))
         .withBackendName(config.getBackendName())
         .withPrimaryKeyField("id")
         .withPermissionRules(config.getTablePermissionRules() == null ? null : config.getTablePermissionRules().clone())
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("tableName", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("recordId", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("action", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("errorMessage", QFieldType.TEXT).withIsEditable(false))
         .withField(new QFieldMetaData("attempts", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("status", QFieldType.STRING).withIsEditable(false))
         .withField(new QFieldMetaData("createDate", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME).withIsEditable(false)));
   }



   /*******************************************************************************
    ** Possible value source over quickSearchIndex, so run rows show the table
    ** name instead of an id.
    *******************************************************************************/
   public static QPossibleValueSource buildIndexPossibleValueSource(QuickSearchQBitConfig config)
   {
      return (QPossibleValueSource.newForTable(config.applyPrefix(QuickSearchIndex.TABLE_NAME)));
   }

}

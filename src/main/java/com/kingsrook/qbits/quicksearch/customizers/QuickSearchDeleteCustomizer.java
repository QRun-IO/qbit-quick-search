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


import java.util.List;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizerInterface;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Customizer that removes deleted records from the Quick Search index.
 *******************************************************************************/
public class QuickSearchDeleteCustomizer implements TableCustomizerInterface
{
   private static final QLogger LOG = QLogger.getLogger(QuickSearchDeleteCustomizer.class);

   private final QuickSearchQBitConfig config;
   private final String                tableName;
   private final String                primaryKeyField;



   /***************************************************************************
    ** Constructor.
    ***************************************************************************/
   public QuickSearchDeleteCustomizer(QuickSearchQBitConfig config, String tableName, String primaryKeyField)
   {
      this.config = config;
      this.tableName = tableName;
      this.primaryKeyField = primaryKeyField;
   }



   /***************************************************************************
    ** Post-delete hook - removes documents from OpenSearch.
    ***************************************************************************/
   @Override
   public List<QRecord> postDelete(DeleteInput deleteInput, List<QRecord> records) throws QException
   {
      if(records == null || records.isEmpty())
      {
         return records;
      }

      QuickSearchOpenSearchClient searchClient = null;

      try
      {
         searchClient = new QuickSearchOpenSearchClient(config);

         for(QRecord record : records)
         {
            Object primaryKeyValue = record.getValue(primaryKeyField);
            if(primaryKeyValue != null)
            {
               String recordId = String.valueOf(primaryKeyValue);
               try
               {
                  searchClient.deleteDocument(tableName, recordId);
                  LOG.debug("Deleted document from search index", logPair("tableName", tableName), logPair("recordId", recordId));
               }
               catch(Exception e)
               {
                  LOG.warn("Failed to delete document from search index", logPair("tableName", tableName), logPair("recordId", recordId), e);
               }
            }
         }
      }
      catch(Exception e)
      {
         LOG.warn("Failed to create OpenSearch client for delete", logPair("tableName", tableName), e);
      }
      finally
      {
         if(searchClient != null)
         {
            searchClient.close();
         }
      }

      return records;
   }

}

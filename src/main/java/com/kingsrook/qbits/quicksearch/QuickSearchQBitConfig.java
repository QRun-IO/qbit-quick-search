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


import java.util.List;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitConfig;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;
import com.kingsrook.qbits.quicksearch.publisher.IndexEventPublisher;


/*******************************************************************************
 ** Configuration for the Quick Search QBit.
 **
 ** Required fields: backendName, opensearchHost, opensearchPort,
 ** opensearchIndexName, searchableEntityClasses.
 **
 ** Optional: opensearchUsername and opensearchPassword (must be set together),
 ** tableNamePrefix, useSsl (default false), enableScheduledProcesses (default
 ** true), enableRealTimeIndexing (default true), defaultBasepullIntervalMinutes
 ** (default 5), bulkBatchSize (default 500), sourceBatchSize (default 1000).
 **
 *******************************************************************************/
public class QuickSearchQBitConfig implements QBitConfig
{
   private String         backendName;
   private String         tableNamePrefix;
   private String         opensearchHost;
   private Integer        opensearchPort;
   private String         opensearchIndexName;
   private String         opensearchUsername;
   private String         opensearchPassword;
   private Boolean        useSsl                        = false;
   private Boolean        enableScheduledProcesses      = true;
   private Boolean        enableRealTimeIndexing        = true;
   private Integer        defaultBasepullIntervalMinutes = 5;
   private Integer        bulkBatchSize                  = 500;
   private Integer        sourceBatchSize                = 1000;
   private List<Class<?>> searchableEntityClasses;

   private IndexEventPublisher indexEventPublisher;



   /***************************************************************************
    ** Validate configuration before QBit is produced.
    **
    ** Collects all validation errors into the provided errors list.
    ***************************************************************************/
   @Override
   public void validate(QInstance qInstance, List<String> errors)
   {
      if(!StringUtils.hasContent(backendName))
      {
         errors.add("backendName is required for QuickSearchQBit");
      }
      else if(qInstance.getBackend(backendName) == null)
      {
         errors.add("Backend not found: " + backendName);
      }

      if(!StringUtils.hasContent(opensearchHost))
      {
         errors.add("opensearchHost is required for QuickSearchQBit");
      }

      if(opensearchPort == null)
      {
         errors.add("opensearchPort is required for QuickSearchQBit");
      }
      else if(opensearchPort <= 0)
      {
         errors.add("opensearchPort must be positive for QuickSearchQBit");
      }

      if(!StringUtils.hasContent(opensearchIndexName))
      {
         errors.add("opensearchIndexName is required for QuickSearchQBit");
      }

      boolean hasUsername = StringUtils.hasContent(opensearchUsername);
      boolean hasPassword = StringUtils.hasContent(opensearchPassword);
      if(hasUsername && !hasPassword)
      {
         errors.add("opensearchPassword is required when opensearchUsername is set");
      }
      else if(!hasUsername && hasPassword)
      {
         errors.add("opensearchUsername is required when opensearchPassword is set");
      }

      if(searchableEntityClasses == null || searchableEntityClasses.isEmpty())
      {
         errors.add("searchableEntityClasses is required for QuickSearchQBit");
      }

      if(bulkBatchSize != null && bulkBatchSize <= 0)
      {
         errors.add("bulkBatchSize must be positive for QuickSearchQBit");
      }

      if(sourceBatchSize != null && sourceBatchSize <= 0)
      {
         errors.add("sourceBatchSize must be positive for QuickSearchQBit");
      }

      if(defaultBasepullIntervalMinutes != null && defaultBasepullIntervalMinutes <= 0)
      {
         errors.add("defaultBasepullIntervalMinutes must be positive for QuickSearchQBit");
      }
   }



   /***************************************************************************
    ** Apply table name prefix if configured.
    **
    ** If tableNamePrefix is non-null and non-empty, returns prefix + name.
    ** Otherwise returns name unchanged.
    ***************************************************************************/
   public String applyPrefix(String name)
   {
      if(StringUtils.hasContent(tableNamePrefix))
      {
         return tableNamePrefix + name;
      }
      return name;
   }



   /***************************************************************************
    ** Return the prefixed table name for the QuickSearchIndex table.
    ***************************************************************************/
   public String getQuickSearchIndexTableName()
   {
      return applyPrefix(QuickSearchIndex.TABLE_NAME);
   }



   /***************************************************************************
    ** Return the prefixed table name for the QuickSearchIndexRun table.
    ***************************************************************************/
   public String getQuickSearchIndexRunTableName()
   {
      return applyPrefix(QuickSearchIndexRun.TABLE_NAME);
   }



   /***************************************************************************
    ** Getter for backendName
    ***************************************************************************/
   public String getBackendName()
   {
      return (this.backendName);
   }



   /***************************************************************************
    ** Setter for backendName
    ***************************************************************************/
   public void setBackendName(String backendName)
   {
      this.backendName = backendName;
   }



   /***************************************************************************
    ** Fluent setter for backendName
    ***************************************************************************/
   public QuickSearchQBitConfig withBackendName(String backendName)
   {
      this.backendName = backendName;
      return (this);
   }



   /***************************************************************************
    ** Getter for tableNamePrefix
    ***************************************************************************/
   public String getTableNamePrefix()
   {
      return (this.tableNamePrefix);
   }



   /***************************************************************************
    ** Setter for tableNamePrefix
    ***************************************************************************/
   public void setTableNamePrefix(String tableNamePrefix)
   {
      this.tableNamePrefix = tableNamePrefix;
   }



   /***************************************************************************
    ** Fluent setter for tableNamePrefix
    ***************************************************************************/
   public QuickSearchQBitConfig withTableNamePrefix(String tableNamePrefix)
   {
      this.tableNamePrefix = tableNamePrefix;
      return (this);
   }



   /***************************************************************************
    ** Getter for opensearchHost
    ***************************************************************************/
   public String getOpensearchHost()
   {
      return (this.opensearchHost);
   }



   /***************************************************************************
    ** Setter for opensearchHost
    ***************************************************************************/
   public void setOpensearchHost(String opensearchHost)
   {
      this.opensearchHost = opensearchHost;
   }



   /***************************************************************************
    ** Fluent setter for opensearchHost
    ***************************************************************************/
   public QuickSearchQBitConfig withOpensearchHost(String opensearchHost)
   {
      this.opensearchHost = opensearchHost;
      return (this);
   }



   /***************************************************************************
    ** Getter for opensearchPort
    ***************************************************************************/
   public Integer getOpensearchPort()
   {
      return (this.opensearchPort);
   }



   /***************************************************************************
    ** Setter for opensearchPort
    ***************************************************************************/
   public void setOpensearchPort(Integer opensearchPort)
   {
      this.opensearchPort = opensearchPort;
   }



   /***************************************************************************
    ** Fluent setter for opensearchPort
    ***************************************************************************/
   public QuickSearchQBitConfig withOpensearchPort(Integer opensearchPort)
   {
      this.opensearchPort = opensearchPort;
      return (this);
   }



   /***************************************************************************
    ** Getter for opensearchIndexName
    ***************************************************************************/
   public String getOpensearchIndexName()
   {
      return (this.opensearchIndexName);
   }



   /***************************************************************************
    ** Setter for opensearchIndexName
    ***************************************************************************/
   public void setOpensearchIndexName(String opensearchIndexName)
   {
      this.opensearchIndexName = opensearchIndexName;
   }



   /***************************************************************************
    ** Fluent setter for opensearchIndexName
    ***************************************************************************/
   public QuickSearchQBitConfig withOpensearchIndexName(String opensearchIndexName)
   {
      this.opensearchIndexName = opensearchIndexName;
      return (this);
   }



   /***************************************************************************
    ** Getter for opensearchUsername
    ***************************************************************************/
   public String getOpensearchUsername()
   {
      return (this.opensearchUsername);
   }



   /***************************************************************************
    ** Setter for opensearchUsername
    ***************************************************************************/
   public void setOpensearchUsername(String opensearchUsername)
   {
      this.opensearchUsername = opensearchUsername;
   }



   /***************************************************************************
    ** Fluent setter for opensearchUsername
    ***************************************************************************/
   public QuickSearchQBitConfig withOpensearchUsername(String opensearchUsername)
   {
      this.opensearchUsername = opensearchUsername;
      return (this);
   }



   /***************************************************************************
    ** Getter for opensearchPassword
    ***************************************************************************/
   public String getOpensearchPassword()
   {
      return (this.opensearchPassword);
   }



   /***************************************************************************
    ** Setter for opensearchPassword
    ***************************************************************************/
   public void setOpensearchPassword(String opensearchPassword)
   {
      this.opensearchPassword = opensearchPassword;
   }



   /***************************************************************************
    ** Fluent setter for opensearchPassword
    ***************************************************************************/
   public QuickSearchQBitConfig withOpensearchPassword(String opensearchPassword)
   {
      this.opensearchPassword = opensearchPassword;
      return (this);
   }



   /***************************************************************************
    ** Getter for useSsl
    ***************************************************************************/
   public Boolean getUseSsl()
   {
      return (this.useSsl);
   }



   /***************************************************************************
    ** Setter for useSsl
    ***************************************************************************/
   public void setUseSsl(Boolean useSsl)
   {
      this.useSsl = useSsl;
   }



   /***************************************************************************
    ** Fluent setter for useSsl
    ***************************************************************************/
   public QuickSearchQBitConfig withUseSsl(Boolean useSsl)
   {
      this.useSsl = useSsl;
      return (this);
   }



   /***************************************************************************
    ** Getter for enableScheduledProcesses
    ***************************************************************************/
   public Boolean getEnableScheduledProcesses()
   {
      return (this.enableScheduledProcesses);
   }



   /***************************************************************************
    ** Setter for enableScheduledProcesses
    ***************************************************************************/
   public void setEnableScheduledProcesses(Boolean enableScheduledProcesses)
   {
      this.enableScheduledProcesses = enableScheduledProcesses;
   }



   /***************************************************************************
    ** Fluent setter for enableScheduledProcesses
    ***************************************************************************/
   public QuickSearchQBitConfig withEnableScheduledProcesses(Boolean enableScheduledProcesses)
   {
      this.enableScheduledProcesses = enableScheduledProcesses;
      return (this);
   }



   /***************************************************************************
    ** Getter for enableRealTimeIndexing
    ***************************************************************************/
   public Boolean getEnableRealTimeIndexing()
   {
      return (this.enableRealTimeIndexing);
   }



   /***************************************************************************
    ** Setter for enableRealTimeIndexing
    ***************************************************************************/
   public void setEnableRealTimeIndexing(Boolean enableRealTimeIndexing)
   {
      this.enableRealTimeIndexing = enableRealTimeIndexing;
   }



   /***************************************************************************
    ** Fluent setter for enableRealTimeIndexing
    ***************************************************************************/
   public QuickSearchQBitConfig withEnableRealTimeIndexing(Boolean enableRealTimeIndexing)
   {
      this.enableRealTimeIndexing = enableRealTimeIndexing;
      return (this);
   }



   /***************************************************************************
    ** Getter for defaultBasepullIntervalMinutes
    ***************************************************************************/
   public Integer getDefaultBasepullIntervalMinutes()
   {
      return (this.defaultBasepullIntervalMinutes);
   }



   /***************************************************************************
    ** Setter for defaultBasepullIntervalMinutes
    ***************************************************************************/
   public void setDefaultBasepullIntervalMinutes(Integer defaultBasepullIntervalMinutes)
   {
      this.defaultBasepullIntervalMinutes = defaultBasepullIntervalMinutes;
   }



   /***************************************************************************
    ** Fluent setter for defaultBasepullIntervalMinutes
    ***************************************************************************/
   public QuickSearchQBitConfig withDefaultBasepullIntervalMinutes(Integer defaultBasepullIntervalMinutes)
   {
      this.defaultBasepullIntervalMinutes = defaultBasepullIntervalMinutes;
      return (this);
   }



   /***************************************************************************
    ** Getter for bulkBatchSize
    ***************************************************************************/
   public Integer getBulkBatchSize()
   {
      return (this.bulkBatchSize);
   }



   /***************************************************************************
    ** Setter for bulkBatchSize
    ***************************************************************************/
   public void setBulkBatchSize(Integer bulkBatchSize)
   {
      this.bulkBatchSize = bulkBatchSize;
   }



   /***************************************************************************
    ** Fluent setter for bulkBatchSize
    ***************************************************************************/
   public QuickSearchQBitConfig withBulkBatchSize(Integer bulkBatchSize)
   {
      this.bulkBatchSize = bulkBatchSize;
      return (this);
   }



   /***************************************************************************
    ** Getter for sourceBatchSize
    ***************************************************************************/
   public Integer getSourceBatchSize()
   {
      return (this.sourceBatchSize);
   }



   /***************************************************************************
    ** Setter for sourceBatchSize
    ***************************************************************************/
   public void setSourceBatchSize(Integer sourceBatchSize)
   {
      this.sourceBatchSize = sourceBatchSize;
   }



   /***************************************************************************
    ** Fluent setter for sourceBatchSize
    ***************************************************************************/
   public QuickSearchQBitConfig withSourceBatchSize(Integer sourceBatchSize)
   {
      this.sourceBatchSize = sourceBatchSize;
      return (this);
   }



   /***************************************************************************
    ** Getter for searchableEntityClasses
    ***************************************************************************/
   public List<Class<?>> getSearchableEntityClasses()
   {
      return (this.searchableEntityClasses);
   }



   /***************************************************************************
    ** Setter for searchableEntityClasses
    ***************************************************************************/
   public void setSearchableEntityClasses(List<Class<?>> searchableEntityClasses)
   {
      this.searchableEntityClasses = searchableEntityClasses;
   }



   /***************************************************************************
    ** Fluent setter for searchableEntityClasses
    ***************************************************************************/
   public QuickSearchQBitConfig withSearchableEntityClasses(List<Class<?>> searchableEntityClasses)
   {
      this.searchableEntityClasses = searchableEntityClasses;
      return (this);
   }



   /***************************************************************************
    ** Getter for indexEventPublisher
    ***************************************************************************/
   public IndexEventPublisher getIndexEventPublisher()
   {
      return (this.indexEventPublisher);
   }



   /***************************************************************************
    ** Setter for indexEventPublisher
    ***************************************************************************/
   public void setIndexEventPublisher(IndexEventPublisher indexEventPublisher)
   {
      this.indexEventPublisher = indexEventPublisher;
   }



   /***************************************************************************
    ** Fluent setter for indexEventPublisher
    ***************************************************************************/
   public QuickSearchQBitConfig withIndexEventPublisher(IndexEventPublisher indexEventPublisher)
   {
      this.indexEventPublisher = indexEventPublisher;
      return (this);
   }

}

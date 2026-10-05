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


import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.producers.MetaDataCustomizerInterface;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitConfig;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexRun;
import com.kingsrook.qbits.quicksearch.publisher.IndexEventPublisher;


/*******************************************************************************
 ** Host-facing configuration for the Quick Search QBit.
 **
 ** Connection: opensearchUrl (preferred) or opensearchHost/opensearchPort/useSsl.
 ** Authentication: authMode NONE, BASIC (opensearchUsername/opensearchPassword)
 ** or AWS_SIGV4 (awsRegion, awsServiceName, awsAssumeRoleArn). Secret-bearing
 ** values may be ${env.X} / ${prop.X} references, resolved only when the
 ** transport is built. TLS trust lives in tls. Timeouts, pool size and a
 ** transportCustomizer complete the transport settings.
 **
 ** Behaviour: startupMode, enableRealTimeIndexing, enableBasepullProcess,
 ** enableMaintenanceProcesses, schedulerName plus basepull/reconcile schedules,
 ** permission rules for the admin surface, search bounds, and indexing limits.
 **
 ** Tables: searchableEntityClasses (annotated entities) and/or searchableTables
 ** (config-driven). validate() is list-based and never throws; the producer
 ** fails production when it reports errors.
 *******************************************************************************/
public class QuickSearchQBitConfig implements QBitConfig
{
   public static final String DEFAULT_ADMIN_PERMISSION_BASE_NAME = "quickSearchAdmin";
   public static final String DEFAULT_BASEPULL_TIMESTAMP_FIELD   = "modifyDate";

   private static final Pattern INDEX_NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_+.-]*$");
   private static final Set<String> AWS_SERVICE_NAMES = Set.of("es", "aoss");

   private String backendName;
   private String tableNamePrefix;
   private String opensearchUrl;
   private String opensearchHost;
   private Integer opensearchPort;
   private Boolean useSsl = false;
   private String opensearchIndexName;
   private QuickSearchAuthMode authMode;
   private String opensearchUsername;
   private String opensearchPassword;
   private String awsRegion;
   private String awsServiceName = "es";
   private String awsAssumeRoleArn;
   private Boolean allowPlaintextCredentials = false;
   private QuickSearchTlsConfig tls;
   private Integer connectTimeoutMillis = 5000;
   private Integer responseTimeoutMillis = 60000;
   private Integer maxConnections = 30;
   private Integer maxBulkRequestBytes = 5 * 1024 * 1024;
   private QCodeReference transportCustomizer;
   private QuickSearchStartupMode startupMode = QuickSearchStartupMode.FAIL_FAST;
   private Boolean enableRealTimeIndexing = true;
   private Boolean enableBasepullProcess = true;
   private Boolean enableMaintenanceProcesses = true;
   private String schedulerName;
   private Integer basepullRepeatSeconds = 300;
   private String reconcileCronExpression;
   private String reconcileCronTimeZoneId;
   private Integer basepullOverlapSeconds = 300;
   private Integer defaultBasepullIntervalMinutes = 5;
   private Integer runHistoryRetentionDays = 30;
   private Integer bulkBatchSize = 500;
   private Integer sourceBatchSize = 1000;
   private Integer maxFieldLength = 10000;
   private Integer maxSearchLimit = 100;
   private Boolean applyRecordSecurityLocks = true;
   private List<Class<?>> searchableEntityClasses;
   private List<SearchableTableConfig> searchableTables;

   private transient IndexEventPublisher indexEventPublisher;
   private transient QPermissionRules adminPermissionRules = new QPermissionRules().withLevel(PermissionLevel.HAS_ACCESS_PERMISSION).withPermissionBaseName(DEFAULT_ADMIN_PERMISSION_BASE_NAME);
   private transient QPermissionRules tablePermissionRules = new QPermissionRules().withLevel(PermissionLevel.READ_WRITE_PERMISSIONS);
   private transient MetaDataCustomizerInterface<QTableMetaData> tableMetaDataCustomizer;
   private transient QuickSearchRuntime runtime;




   /*******************************************************************************
    ** Collect configuration errors. Never throws; null-safe for a partially
    ** built QInstance.
    *******************************************************************************/
   @Override
   public void validate(QInstance qInstance, List<String> errors)
   {
      validateBackend(qInstance, errors);
      validateConnection(errors);
      validateAuth(errors);
      validateTls(errors);
      validateIndexName(errors);
      validateTables(qInstance, errors);
      validateNumbers(errors);
      validateScheduling(qInstance, errors);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private void validateBackend(QInstance qInstance, List<String> errors)
   {
      if(!StringUtils.hasContent(backendName))
      {
         errors.add("backendName is required for QuickSearchQBit");
      }
      else if(qInstance != null && qInstance.getBackends() != null && qInstance.getBackend(backendName) == null)
      {
         errors.add("Backend not found: " + backendName);
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private void validateConnection(List<String> errors)
   {
      if(StringUtils.hasContent(opensearchUrl))
      {
         try
         {
            URI uri = new URI(opensearchUrl.trim());
            if(uri.getScheme() == null || !(uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https")))
            {
               errors.add("opensearchUrl must start with http:// or https://");
            }
            if(!StringUtils.hasContent(uri.getHost()))
            {
               errors.add("opensearchUrl must include a host");
            }
         }
         catch(Exception e)
         {
            errors.add("opensearchUrl is not a valid URL; expected http(s)://host[:port]");
         }
         return;
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
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private void validateAuth(List<String> errors)
   {
      boolean hasUsername = StringUtils.hasContent(opensearchUsername);
      boolean hasPassword = StringUtils.hasContent(opensearchPassword);

      QuickSearchAuthMode effectiveAuthMode = getEffectiveAuthMode();
      switch(effectiveAuthMode)
      {
         case BASIC ->
         {
            if(hasUsername && !hasPassword)
            {
               errors.add("opensearchPassword is required when opensearchUsername is set");
            }
            else if(!hasUsername && hasPassword)
            {
               errors.add("opensearchUsername is required when opensearchPassword is set");
            }
            else if(!hasUsername)
            {
               errors.add("opensearchUsername and opensearchPassword are required for authMode BASIC");
            }

            if("http".equals(getEffectiveScheme()) && !Boolean.TRUE.equals(allowPlaintextCredentials))
            {
               errors.add("BASIC credentials over plain http are refused; use https or set allowPlaintextCredentials=true for development");
            }
         }
         case AWS_SIGV4 ->
         {
            if(hasUsername || hasPassword)
            {
               errors.add("opensearchUsername and opensearchPassword are not used with authMode AWS_SIGV4");
            }
            if(!StringUtils.hasContent(getEffectiveAwsRegion()))
            {
               errors.add("awsRegion is required for authMode AWS_SIGV4 (or set AWS_REGION in the environment)");
            }
            if(!AWS_SERVICE_NAMES.contains(getEffectiveAwsServiceName()))
            {
               errors.add("awsServiceName must be es or aoss for authMode AWS_SIGV4");
            }
            if(!"https".equals(getEffectiveScheme()))
            {
               errors.add("authMode AWS_SIGV4 requires an https connection");
            }
         }
         case NONE ->
         {
            if(hasUsername || hasPassword)
            {
               errors.add("opensearchUsername and opensearchPassword are set but authMode is NONE");
            }
         }
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private void validateTls(List<String> errors)
   {
      if(tls == null)
      {
         return;
      }

      if(Boolean.TRUE.equals(tls.getInsecureSkipVerify()) && !isLoopbackHost(getEffectiveHost()) && !Boolean.TRUE.equals(tls.getAllowInsecureInProduction()))
      {
         errors.add("tls.insecureSkipVerify is only allowed for loopback hosts unless tls.allowInsecureInProduction=true");
      }

      if(StringUtils.hasContent(tls.getCaCertificatePath()) && StringUtils.hasContent(tls.getTrustStorePath()))
      {
         errors.add("Set either tls.caCertificatePath or tls.trustStorePath, not both");
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private void validateIndexName(List<String> errors)
   {
      if(!StringUtils.hasContent(opensearchIndexName))
      {
         errors.add("opensearchIndexName is required for QuickSearchQBit");
      }
      else if(!isValidIndexName(opensearchIndexName))
      {
         errors.add("opensearchIndexName must be lowercase, up to 255 characters, contain only letters, digits, _ - + ., and not start with _ - or +");
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private void validateTables(QInstance qInstance, List<String> errors)
   {
      boolean hasEntityClasses    = !CollectionUtils.nullSafeIsEmpty(searchableEntityClasses);
      boolean hasSearchableTables = !CollectionUtils.nullSafeIsEmpty(searchableTables);

      if(!hasEntityClasses && !hasSearchableTables)
      {
         errors.add("At least one of searchableEntityClasses or searchableTables is required for QuickSearchQBit");
      }

      if(!hasSearchableTables)
      {
         return;
      }

      Set<String> seenTableNames = new HashSet<>();
      for(SearchableTableConfig tableConfig : searchableTables)
      {
         String tableName = tableConfig.getTableName();
         if(!StringUtils.hasContent(tableName))
         {
            errors.add("tableName is required on each SearchableTableConfig for QuickSearchQBit");
         }
         else if(!seenTableNames.add(tableName))
         {
            errors.add("Duplicate tableName in searchableTables: " + tableName);
         }
         else if(tableName.contains(":"))
         {
            errors.add("tableName must not contain ':' (it is part of the document id): " + tableName);
         }

         if(CollectionUtils.nullSafeIsEmpty(tableConfig.getFields()))
         {
            errors.add("fields is required and must be non-empty on SearchableTableConfig"
               + (StringUtils.hasContent(tableName) ? " [" + tableName + "]" : "")
               + " for QuickSearchQBit");
         }

         QTableMetaData table = (qInstance == null || tableName == null) ? null : qInstance.getTable(tableName);
         if(table != null)
         {
            validateFieldsExist(table, tableConfig, errors);
         }
      }
   }



   /*******************************************************************************
    ** Check that configured fields exist on a table that is already in the
    ** instance. Tables added after this QBit is produced are checked by the
    ** validator plugin.
    *******************************************************************************/
   public static void validateFieldsExist(QTableMetaData table, SearchableTableConfig tableConfig, List<String> errors)
   {
      for(SearchableFieldConfig field : CollectionUtils.nonNullList(tableConfig.getFields()))
      {
         if(field.getFieldName() != null && !table.getFields().containsKey(field.getFieldName()))
         {
            errors.add("Searchable field [" + field.getFieldName() + "] does not exist on table [" + table.getName() + "]");
         }
      }

      String timestampField = tableConfig.getBasepullTimestampField();
      if(timestampField != null && !DEFAULT_BASEPULL_TIMESTAMP_FIELD.equals(timestampField) && !table.getFields().containsKey(timestampField))
      {
         errors.add("basepullTimestampField [" + timestampField + "] does not exist on table [" + table.getName() + "]; set it to null to disable incremental basepull for that table");
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private void validateNumbers(List<String> errors)
   {
      requirePositive(bulkBatchSize, "bulkBatchSize", errors);
      requirePositive(sourceBatchSize, "sourceBatchSize", errors);
      requirePositive(defaultBasepullIntervalMinutes, "defaultBasepullIntervalMinutes", errors);
      requirePositive(connectTimeoutMillis, "connectTimeoutMillis", errors);
      requirePositive(responseTimeoutMillis, "responseTimeoutMillis", errors);
      requirePositive(maxConnections, "maxConnections", errors);
      requirePositive(maxBulkRequestBytes, "maxBulkRequestBytes", errors);
      requirePositive(basepullRepeatSeconds, "basepullRepeatSeconds", errors);
      requirePositive(maxSearchLimit, "maxSearchLimit", errors);
      requirePositive(maxFieldLength, "maxFieldLength", errors);
      requirePositive(runHistoryRetentionDays, "runHistoryRetentionDays", errors);

      if(basepullOverlapSeconds != null && basepullOverlapSeconds < 0)
      {
         errors.add("basepullOverlapSeconds must not be negative for QuickSearchQBit");
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void requirePositive(Integer value, String name, List<String> errors)
   {
      if(value != null && value <= 0)
      {
         errors.add(name + " must be positive for QuickSearchQBit");
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private void validateScheduling(QInstance qInstance, List<String> errors)
   {
      if(StringUtils.hasContent(schedulerName) && qInstance != null && !CollectionUtils.nullSafeIsEmpty(qInstance.getSchedulers()) && qInstance.getScheduler(schedulerName) == null)
      {
         errors.add("Scheduler not found: " + schedulerName);
      }

      if(StringUtils.hasContent(reconcileCronExpression) && !StringUtils.hasContent(schedulerName))
      {
         errors.add("reconcileCronExpression requires schedulerName");
      }
   }



   /*******************************************************************************
    ** OpenSearch index and alias naming rules.
    *******************************************************************************/
   public static boolean isValidIndexName(String name)
   {
      if(name == null || name.isEmpty() || name.length() > 255 || name.equals(".") || name.equals(".."))
      {
         return (false);
      }
      return (INDEX_NAME_PATTERN.matcher(name).matches());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static boolean isLoopbackHost(String host)
   {
      if(host == null)
      {
         return (false);
      }
      String lower = host.toLowerCase();
      return (lower.equals("localhost") || lower.equals("127.0.0.1") || lower.equals("::1") || lower.equals("[::1]") || lower.startsWith("127."));
   }



   /*******************************************************************************
    ** The auth mode in effect: the configured one, else BASIC when credentials
    ** are present, else NONE.
    *******************************************************************************/
   @JsonIgnore
   public QuickSearchAuthMode getEffectiveAuthMode()
   {
      if(authMode != null)
      {
         return (authMode);
      }
      return (StringUtils.hasContent(opensearchUsername) || StringUtils.hasContent(opensearchPassword) ? QuickSearchAuthMode.BASIC : QuickSearchAuthMode.NONE);
   }



   /*******************************************************************************
    ** http or https, from opensearchUrl or useSsl.
    *******************************************************************************/
   @JsonIgnore
   public String getEffectiveScheme()
   {
      URI uri = parseUrlQuietly();
      if(uri != null && uri.getScheme() != null)
      {
         return (uri.getScheme().toLowerCase());
      }
      return (Boolean.TRUE.equals(useSsl) ? "https" : "http");
   }



   /*******************************************************************************
    ** Host from opensearchUrl or opensearchHost.
    *******************************************************************************/
   @JsonIgnore
   public String getEffectiveHost()
   {
      URI uri = parseUrlQuietly();
      if(uri != null && StringUtils.hasContent(uri.getHost()))
      {
         return (uri.getHost());
      }
      return (opensearchHost);
   }



   /*******************************************************************************
    ** Port from opensearchUrl (443 for https, 9200 for http when absent) or
    ** opensearchPort.
    *******************************************************************************/
   @JsonIgnore
   public Integer getEffectivePort()
   {
      URI uri = parseUrlQuietly();
      if(uri != null && StringUtils.hasContent(uri.getHost()))
      {
         if(uri.getPort() > 0)
         {
            return (uri.getPort());
         }
         return ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 9200);
      }
      return (opensearchPort);
   }



   /*******************************************************************************
    ** awsRegion, else AWS_REGION, else AWS_DEFAULT_REGION from the environment.
    *******************************************************************************/
   @JsonIgnore
   public String getEffectiveAwsRegion()
   {
      if(StringUtils.hasContent(awsRegion))
      {
         return (awsRegion);
      }
      String fromEnv = System.getenv("AWS_REGION");
      if(!StringUtils.hasContent(fromEnv))
      {
         fromEnv = System.getenv("AWS_DEFAULT_REGION");
      }
      return (fromEnv);
   }



   /*******************************************************************************
    ** awsServiceName, defaulting to es.
    *******************************************************************************/
   @JsonIgnore
   public String getEffectiveAwsServiceName()
   {
      return (StringUtils.hasContent(awsServiceName) ? awsServiceName.toLowerCase() : "es");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private URI parseUrlQuietly()
   {
      if(!StringUtils.hasContent(opensearchUrl))
      {
         return (null);
      }
      try
      {
         return (new URI(opensearchUrl.trim()));
      }
      catch(Exception e)
      {
         return (null);
      }
   }



   /*******************************************************************************
    ** Apply the table-name prefix, if any.
    *******************************************************************************/
   public String applyPrefix(String name)
   {
      if(StringUtils.hasContent(tableNamePrefix))
      {
         return tableNamePrefix + name;
      }
      return name;
   }



   /*******************************************************************************
    ** Name of the produced quickSearchIndex table.
    *******************************************************************************/
   public String getQuickSearchIndexTableName()
   {
      return applyPrefix(QuickSearchIndex.TABLE_NAME);
   }



   /*******************************************************************************
    ** Name of the produced quickSearchIndexRun table.
    *******************************************************************************/
   public String getQuickSearchIndexRunTableName()
   {
      return applyPrefix(QuickSearchIndexRun.TABLE_NAME);
   }



   /*******************************************************************************
    ** QBitConfig hook: tables this QBit produces default to backendName.
    *******************************************************************************/
   @Override
   public String getDefaultBackendNameForTables()
   {
      return (backendName);
   }



   /*******************************************************************************
    ** QBitConfig hook: host-supplied customizer for produced tables.
    *******************************************************************************/
   @Override
   public MetaDataCustomizerInterface<QTableMetaData> getTableMetaDataCustomizer()
   {
      return (tableMetaDataCustomizer);
   }



   /*******************************************************************************
    ** Setter for tableMetaDataCustomizer
    *******************************************************************************/
   public void setTableMetaDataCustomizer(MetaDataCustomizerInterface<QTableMetaData> tableMetaDataCustomizer)
   {
      this.tableMetaDataCustomizer = tableMetaDataCustomizer;
   }



   /*******************************************************************************
    ** Fluent setter for tableMetaDataCustomizer
    *******************************************************************************/
   public QuickSearchQBitConfig withTableMetaDataCustomizer(MetaDataCustomizerInterface<QTableMetaData> tableMetaDataCustomizer)
   {
      this.tableMetaDataCustomizer = tableMetaDataCustomizer;
      return (this);
   }



   /*******************************************************************************
    ** Deprecated alias: true when both enableBasepullProcess and
    ** enableMaintenanceProcesses are true.
    **
    ** @deprecated use enableBasepullProcess and enableMaintenanceProcesses
    *******************************************************************************/
   @Deprecated
   public Boolean getEnableScheduledProcesses()
   {
      return (Boolean.TRUE.equals(enableBasepullProcess) && Boolean.TRUE.equals(enableMaintenanceProcesses));
   }



   /*******************************************************************************
    ** Deprecated alias: sets both enableBasepullProcess and
    ** enableMaintenanceProcesses.
    **
    ** @deprecated use enableBasepullProcess and enableMaintenanceProcesses
    *******************************************************************************/
   @Deprecated
   public void setEnableScheduledProcesses(Boolean enableScheduledProcesses)
   {
      this.enableBasepullProcess      = enableScheduledProcesses;
      this.enableMaintenanceProcesses = enableScheduledProcesses;
   }



   /*******************************************************************************
    ** Deprecated alias: sets both enableBasepullProcess and
    ** enableMaintenanceProcesses.
    **
    ** @deprecated use enableBasepullProcess and enableMaintenanceProcesses
    *******************************************************************************/
   @Deprecated
   public QuickSearchQBitConfig withEnableScheduledProcesses(Boolean enableScheduledProcesses)
   {
      setEnableScheduledProcesses(enableScheduledProcesses);
      return (this);
   }



   /*******************************************************************************
    ** Add one config-driven searchable table.
    *******************************************************************************/
   public QuickSearchQBitConfig withSearchableTable(SearchableTableConfig tableConfig)
   {
      if(this.searchableTables == null)
      {
         this.searchableTables = new ArrayList<>();
      }
      this.searchableTables.add(tableConfig);
      return (this);
   }



   /*******************************************************************************
    ** Add one config-driven searchable table from its name and fields.
    *******************************************************************************/
   public QuickSearchQBitConfig withSearchableTable(String tableName, List<SearchableFieldConfig> fields)
   {
      return withSearchableTable(new SearchableTableConfig(tableName, fields));
   }



   /*******************************************************************************
    ** Summary without secrets.
    *******************************************************************************/
   @Override
   public String toString()
   {
      return ("QuickSearchQBitConfig{backendName=" + backendName
         + ", url=" + getEffectiveScheme() + "://" + getEffectiveHost() + ":" + getEffectivePort()
         + ", indexName=" + opensearchIndexName
         + ", authMode=" + getEffectiveAuthMode()
         + ", username=" + (StringUtils.hasContent(opensearchUsername) ? "[set]" : "[none]")
         + ", password=" + (StringUtils.hasContent(opensearchPassword) ? "[redacted]" : "[none]")
         + ", startupMode=" + startupMode
         + ", schedulerName=" + schedulerName
         + ", entityClasses=" + (searchableEntityClasses == null ? 0 : searchableEntityClasses.size())
         + ", searchableTables=" + (searchableTables == null ? 0 : searchableTables.size())
         + "}");
   }


   /*******************************************************************************
    ** Getter for backendName: QQQ backend that holds the operational tables (required)
    *******************************************************************************/
   public String getBackendName()
   {
      return (this.backendName);
   }



   /*******************************************************************************
    ** Setter for backendName
    *******************************************************************************/
   public void setBackendName(String backendName)
   {
      this.backendName = backendName;
   }



   /*******************************************************************************
    ** Fluent setter for backendName
    *******************************************************************************/
   public QuickSearchQBitConfig withBackendName(String backendName)
   {
      this.backendName = backendName;
      return (this);
   }




   /*******************************************************************************
    ** Getter for tableNamePrefix: optional prefix for produced table, process and app names
    *******************************************************************************/
   public String getTableNamePrefix()
   {
      return (this.tableNamePrefix);
   }



   /*******************************************************************************
    ** Setter for tableNamePrefix
    *******************************************************************************/
   public void setTableNamePrefix(String tableNamePrefix)
   {
      this.tableNamePrefix = tableNamePrefix;
   }



   /*******************************************************************************
    ** Fluent setter for tableNamePrefix
    *******************************************************************************/
   public QuickSearchQBitConfig withTableNamePrefix(String tableNamePrefix)
   {
      this.tableNamePrefix = tableNamePrefix;
      return (this);
   }




   /*******************************************************************************
    ** Getter for opensearchUrl: full OpenSearch URL, e.g. https://search.example.com:443 (preferred over host/port/useSsl)
    *******************************************************************************/
   public String getOpensearchUrl()
   {
      return (this.opensearchUrl);
   }



   /*******************************************************************************
    ** Setter for opensearchUrl
    *******************************************************************************/
   public void setOpensearchUrl(String opensearchUrl)
   {
      this.opensearchUrl = opensearchUrl;
   }



   /*******************************************************************************
    ** Fluent setter for opensearchUrl
    *******************************************************************************/
   public QuickSearchQBitConfig withOpensearchUrl(String opensearchUrl)
   {
      this.opensearchUrl = opensearchUrl;
      return (this);
   }




   /*******************************************************************************
    ** Getter for opensearchHost: OpenSearch host (when opensearchUrl is not set)
    *******************************************************************************/
   public String getOpensearchHost()
   {
      return (this.opensearchHost);
   }



   /*******************************************************************************
    ** Setter for opensearchHost
    *******************************************************************************/
   public void setOpensearchHost(String opensearchHost)
   {
      this.opensearchHost = opensearchHost;
   }



   /*******************************************************************************
    ** Fluent setter for opensearchHost
    *******************************************************************************/
   public QuickSearchQBitConfig withOpensearchHost(String opensearchHost)
   {
      this.opensearchHost = opensearchHost;
      return (this);
   }




   /*******************************************************************************
    ** Getter for opensearchPort: OpenSearch port (when opensearchUrl is not set)
    *******************************************************************************/
   public Integer getOpensearchPort()
   {
      return (this.opensearchPort);
   }



   /*******************************************************************************
    ** Setter for opensearchPort
    *******************************************************************************/
   public void setOpensearchPort(Integer opensearchPort)
   {
      this.opensearchPort = opensearchPort;
   }



   /*******************************************************************************
    ** Fluent setter for opensearchPort
    *******************************************************************************/
   public QuickSearchQBitConfig withOpensearchPort(Integer opensearchPort)
   {
      this.opensearchPort = opensearchPort;
      return (this);
   }




   /*******************************************************************************
    ** Getter for useSsl: use https (when opensearchUrl is not set)
    *******************************************************************************/
   public Boolean getUseSsl()
   {
      return (this.useSsl);
   }



   /*******************************************************************************
    ** Setter for useSsl
    *******************************************************************************/
   public void setUseSsl(Boolean useSsl)
   {
      this.useSsl = useSsl;
   }



   /*******************************************************************************
    ** Fluent setter for useSsl
    *******************************************************************************/
   public QuickSearchQBitConfig withUseSsl(Boolean useSsl)
   {
      this.useSsl = useSsl;
      return (this);
   }




   /*******************************************************************************
    ** Getter for opensearchIndexName: name of the OpenSearch index (alias) this QBit owns (required)
    *******************************************************************************/
   public String getOpensearchIndexName()
   {
      return (this.opensearchIndexName);
   }



   /*******************************************************************************
    ** Setter for opensearchIndexName
    *******************************************************************************/
   public void setOpensearchIndexName(String opensearchIndexName)
   {
      this.opensearchIndexName = opensearchIndexName;
   }



   /*******************************************************************************
    ** Fluent setter for opensearchIndexName
    *******************************************************************************/
   public QuickSearchQBitConfig withOpensearchIndexName(String opensearchIndexName)
   {
      this.opensearchIndexName = opensearchIndexName;
      return (this);
   }




   /*******************************************************************************
    ** Getter for authMode: authentication mode; inferred as BASIC when credentials are set, else NONE
    *******************************************************************************/
   public QuickSearchAuthMode getAuthMode()
   {
      return (this.authMode);
   }



   /*******************************************************************************
    ** Setter for authMode
    *******************************************************************************/
   public void setAuthMode(QuickSearchAuthMode authMode)
   {
      this.authMode = authMode;
   }



   /*******************************************************************************
    ** Fluent setter for authMode
    *******************************************************************************/
   public QuickSearchQBitConfig withAuthMode(QuickSearchAuthMode authMode)
   {
      this.authMode = authMode;
      return (this);
   }




   /*******************************************************************************
    ** Getter for opensearchUsername: BASIC auth username; may be a ${env.X} reference
    *******************************************************************************/
   public String getOpensearchUsername()
   {
      return (this.opensearchUsername);
   }



   /*******************************************************************************
    ** Setter for opensearchUsername
    *******************************************************************************/
   public void setOpensearchUsername(String opensearchUsername)
   {
      this.opensearchUsername = opensearchUsername;
   }



   /*******************************************************************************
    ** Fluent setter for opensearchUsername
    *******************************************************************************/
   public QuickSearchQBitConfig withOpensearchUsername(String opensearchUsername)
   {
      this.opensearchUsername = opensearchUsername;
      return (this);
   }




   /*******************************************************************************
    ** Getter for opensearchPassword: BASIC auth password; may be a ${env.X} reference. Never serialized to JSON
    *******************************************************************************/
   @JsonIgnore
   public String getOpensearchPassword()
   {
      return (this.opensearchPassword);
   }



   /*******************************************************************************
    ** Setter for opensearchPassword
    *******************************************************************************/
   public void setOpensearchPassword(String opensearchPassword)
   {
      this.opensearchPassword = opensearchPassword;
   }



   /*******************************************************************************
    ** Fluent setter for opensearchPassword
    *******************************************************************************/
   public QuickSearchQBitConfig withOpensearchPassword(String opensearchPassword)
   {
      this.opensearchPassword = opensearchPassword;
      return (this);
   }




   /*******************************************************************************
    ** Getter for awsRegion: AWS_SIGV4 region; defaults to AWS_REGION / AWS_DEFAULT_REGION from the environment
    *******************************************************************************/
   public String getAwsRegion()
   {
      return (this.awsRegion);
   }



   /*******************************************************************************
    ** Setter for awsRegion
    *******************************************************************************/
   public void setAwsRegion(String awsRegion)
   {
      this.awsRegion = awsRegion;
   }



   /*******************************************************************************
    ** Fluent setter for awsRegion
    *******************************************************************************/
   public QuickSearchQBitConfig withAwsRegion(String awsRegion)
   {
      this.awsRegion = awsRegion;
      return (this);
   }




   /*******************************************************************************
    ** Getter for awsServiceName: AWS_SIGV4 signing service: es (managed domains) or aoss (Serverless)
    *******************************************************************************/
   public String getAwsServiceName()
   {
      return (this.awsServiceName);
   }



   /*******************************************************************************
    ** Setter for awsServiceName
    *******************************************************************************/
   public void setAwsServiceName(String awsServiceName)
   {
      this.awsServiceName = awsServiceName;
   }



   /*******************************************************************************
    ** Fluent setter for awsServiceName
    *******************************************************************************/
   public QuickSearchQBitConfig withAwsServiceName(String awsServiceName)
   {
      this.awsServiceName = awsServiceName;
      return (this);
   }




   /*******************************************************************************
    ** Getter for awsAssumeRoleArn: AWS_SIGV4 optional role to assume via STS
    *******************************************************************************/
   public String getAwsAssumeRoleArn()
   {
      return (this.awsAssumeRoleArn);
   }



   /*******************************************************************************
    ** Setter for awsAssumeRoleArn
    *******************************************************************************/
   public void setAwsAssumeRoleArn(String awsAssumeRoleArn)
   {
      this.awsAssumeRoleArn = awsAssumeRoleArn;
   }



   /*******************************************************************************
    ** Fluent setter for awsAssumeRoleArn
    *******************************************************************************/
   public QuickSearchQBitConfig withAwsAssumeRoleArn(String awsAssumeRoleArn)
   {
      this.awsAssumeRoleArn = awsAssumeRoleArn;
      return (this);
   }




   /*******************************************************************************
    ** Getter for allowPlaintextCredentials: permit BASIC credentials over plain http (development only)
    *******************************************************************************/
   public Boolean getAllowPlaintextCredentials()
   {
      return (this.allowPlaintextCredentials);
   }



   /*******************************************************************************
    ** Setter for allowPlaintextCredentials
    *******************************************************************************/
   public void setAllowPlaintextCredentials(Boolean allowPlaintextCredentials)
   {
      this.allowPlaintextCredentials = allowPlaintextCredentials;
   }



   /*******************************************************************************
    ** Fluent setter for allowPlaintextCredentials
    *******************************************************************************/
   public QuickSearchQBitConfig withAllowPlaintextCredentials(Boolean allowPlaintextCredentials)
   {
      this.allowPlaintextCredentials = allowPlaintextCredentials;
      return (this);
   }




   /*******************************************************************************
    ** Getter for tls: TLS trust and client certificate settings
    *******************************************************************************/
   public QuickSearchTlsConfig getTls()
   {
      return (this.tls);
   }



   /*******************************************************************************
    ** Setter for tls
    *******************************************************************************/
   public void setTls(QuickSearchTlsConfig tls)
   {
      this.tls = tls;
   }



   /*******************************************************************************
    ** Fluent setter for tls
    *******************************************************************************/
   public QuickSearchQBitConfig withTls(QuickSearchTlsConfig tls)
   {
      this.tls = tls;
      return (this);
   }




   /*******************************************************************************
    ** Getter for connectTimeoutMillis: transport connect timeout
    *******************************************************************************/
   public Integer getConnectTimeoutMillis()
   {
      return (this.connectTimeoutMillis);
   }



   /*******************************************************************************
    ** Setter for connectTimeoutMillis
    *******************************************************************************/
   public void setConnectTimeoutMillis(Integer connectTimeoutMillis)
   {
      this.connectTimeoutMillis = connectTimeoutMillis;
   }



   /*******************************************************************************
    ** Fluent setter for connectTimeoutMillis
    *******************************************************************************/
   public QuickSearchQBitConfig withConnectTimeoutMillis(Integer connectTimeoutMillis)
   {
      this.connectTimeoutMillis = connectTimeoutMillis;
      return (this);
   }




   /*******************************************************************************
    ** Getter for responseTimeoutMillis: transport response timeout
    *******************************************************************************/
   public Integer getResponseTimeoutMillis()
   {
      return (this.responseTimeoutMillis);
   }



   /*******************************************************************************
    ** Setter for responseTimeoutMillis
    *******************************************************************************/
   public void setResponseTimeoutMillis(Integer responseTimeoutMillis)
   {
      this.responseTimeoutMillis = responseTimeoutMillis;
   }



   /*******************************************************************************
    ** Fluent setter for responseTimeoutMillis
    *******************************************************************************/
   public QuickSearchQBitConfig withResponseTimeoutMillis(Integer responseTimeoutMillis)
   {
      this.responseTimeoutMillis = responseTimeoutMillis;
      return (this);
   }




   /*******************************************************************************
    ** Getter for maxConnections: transport connection pool size
    *******************************************************************************/
   public Integer getMaxConnections()
   {
      return (this.maxConnections);
   }



   /*******************************************************************************
    ** Setter for maxConnections
    *******************************************************************************/
   public void setMaxConnections(Integer maxConnections)
   {
      this.maxConnections = maxConnections;
   }



   /*******************************************************************************
    ** Fluent setter for maxConnections
    *******************************************************************************/
   public QuickSearchQBitConfig withMaxConnections(Integer maxConnections)
   {
      this.maxConnections = maxConnections;
      return (this);
   }




   /*******************************************************************************
    ** Getter for maxBulkRequestBytes: flush a bulk request when its serialized size reaches this many bytes
    *******************************************************************************/
   public Integer getMaxBulkRequestBytes()
   {
      return (this.maxBulkRequestBytes);
   }



   /*******************************************************************************
    ** Setter for maxBulkRequestBytes
    *******************************************************************************/
   public void setMaxBulkRequestBytes(Integer maxBulkRequestBytes)
   {
      this.maxBulkRequestBytes = maxBulkRequestBytes;
   }



   /*******************************************************************************
    ** Fluent setter for maxBulkRequestBytes
    *******************************************************************************/
   public QuickSearchQBitConfig withMaxBulkRequestBytes(Integer maxBulkRequestBytes)
   {
      this.maxBulkRequestBytes = maxBulkRequestBytes;
      return (this);
   }




   /*******************************************************************************
    ** Getter for transportCustomizer: QCodeReference to an OpenSearchTransportCustomizer
    *******************************************************************************/
   public QCodeReference getTransportCustomizer()
   {
      return (this.transportCustomizer);
   }



   /*******************************************************************************
    ** Setter for transportCustomizer
    *******************************************************************************/
   public void setTransportCustomizer(QCodeReference transportCustomizer)
   {
      this.transportCustomizer = transportCustomizer;
   }



   /*******************************************************************************
    ** Fluent setter for transportCustomizer
    *******************************************************************************/
   public QuickSearchQBitConfig withTransportCustomizer(QCodeReference transportCustomizer)
   {
      this.transportCustomizer = transportCustomizer;
      return (this);
   }




   /*******************************************************************************
    ** Getter for startupMode: FAIL_FAST or DEGRADED startup when OpenSearch is unreachable
    *******************************************************************************/
   public QuickSearchStartupMode getStartupMode()
   {
      return (this.startupMode);
   }



   /*******************************************************************************
    ** Setter for startupMode
    *******************************************************************************/
   public void setStartupMode(QuickSearchStartupMode startupMode)
   {
      this.startupMode = startupMode;
   }



   /*******************************************************************************
    ** Fluent setter for startupMode
    *******************************************************************************/
   public QuickSearchQBitConfig withStartupMode(QuickSearchStartupMode startupMode)
   {
      this.startupMode = startupMode;
      return (this);
   }




   /*******************************************************************************
    ** Getter for enableRealTimeIndexing: register the record-change listener that indexes writes as they happen
    *******************************************************************************/
   public Boolean getEnableRealTimeIndexing()
   {
      return (this.enableRealTimeIndexing);
   }



   /*******************************************************************************
    ** Setter for enableRealTimeIndexing
    *******************************************************************************/
   public void setEnableRealTimeIndexing(Boolean enableRealTimeIndexing)
   {
      this.enableRealTimeIndexing = enableRealTimeIndexing;
   }



   /*******************************************************************************
    ** Fluent setter for enableRealTimeIndexing
    *******************************************************************************/
   public QuickSearchQBitConfig withEnableRealTimeIndexing(Boolean enableRealTimeIndexing)
   {
      this.enableRealTimeIndexing = enableRealTimeIndexing;
      return (this);
   }




   /*******************************************************************************
    ** Getter for enableBasepullProcess: register the basepull catch-up process
    *******************************************************************************/
   public Boolean getEnableBasepullProcess()
   {
      return (this.enableBasepullProcess);
   }



   /*******************************************************************************
    ** Setter for enableBasepullProcess
    *******************************************************************************/
   public void setEnableBasepullProcess(Boolean enableBasepullProcess)
   {
      this.enableBasepullProcess = enableBasepullProcess;
   }



   /*******************************************************************************
    ** Fluent setter for enableBasepullProcess
    *******************************************************************************/
   public QuickSearchQBitConfig withEnableBasepullProcess(Boolean enableBasepullProcess)
   {
      this.enableBasepullProcess = enableBasepullProcess;
      return (this);
   }




   /*******************************************************************************
    ** Getter for enableMaintenanceProcesses: register the full reindex and reconcile processes
    *******************************************************************************/
   public Boolean getEnableMaintenanceProcesses()
   {
      return (this.enableMaintenanceProcesses);
   }



   /*******************************************************************************
    ** Setter for enableMaintenanceProcesses
    *******************************************************************************/
   public void setEnableMaintenanceProcesses(Boolean enableMaintenanceProcesses)
   {
      this.enableMaintenanceProcesses = enableMaintenanceProcesses;
   }



   /*******************************************************************************
    ** Fluent setter for enableMaintenanceProcesses
    *******************************************************************************/
   public QuickSearchQBitConfig withEnableMaintenanceProcesses(Boolean enableMaintenanceProcesses)
   {
      this.enableMaintenanceProcesses = enableMaintenanceProcesses;
      return (this);
   }




   /*******************************************************************************
    ** Getter for schedulerName: QQQ scheduler to run basepull and reconcile on; null leaves them unscheduled
    *******************************************************************************/
   public String getSchedulerName()
   {
      return (this.schedulerName);
   }



   /*******************************************************************************
    ** Setter for schedulerName
    *******************************************************************************/
   public void setSchedulerName(String schedulerName)
   {
      this.schedulerName = schedulerName;
   }



   /*******************************************************************************
    ** Fluent setter for schedulerName
    *******************************************************************************/
   public QuickSearchQBitConfig withSchedulerName(String schedulerName)
   {
      this.schedulerName = schedulerName;
      return (this);
   }




   /*******************************************************************************
    ** Getter for basepullRepeatSeconds: basepull schedule interval when schedulerName is set
    *******************************************************************************/
   public Integer getBasepullRepeatSeconds()
   {
      return (this.basepullRepeatSeconds);
   }



   /*******************************************************************************
    ** Setter for basepullRepeatSeconds
    *******************************************************************************/
   public void setBasepullRepeatSeconds(Integer basepullRepeatSeconds)
   {
      this.basepullRepeatSeconds = basepullRepeatSeconds;
   }



   /*******************************************************************************
    ** Fluent setter for basepullRepeatSeconds
    *******************************************************************************/
   public QuickSearchQBitConfig withBasepullRepeatSeconds(Integer basepullRepeatSeconds)
   {
      this.basepullRepeatSeconds = basepullRepeatSeconds;
      return (this);
   }




   /*******************************************************************************
    ** Getter for reconcileCronExpression: optional cron schedule for reconcile (needs a scheduler that supports cron)
    *******************************************************************************/
   public String getReconcileCronExpression()
   {
      return (this.reconcileCronExpression);
   }



   /*******************************************************************************
    ** Setter for reconcileCronExpression
    *******************************************************************************/
   public void setReconcileCronExpression(String reconcileCronExpression)
   {
      this.reconcileCronExpression = reconcileCronExpression;
   }



   /*******************************************************************************
    ** Fluent setter for reconcileCronExpression
    *******************************************************************************/
   public QuickSearchQBitConfig withReconcileCronExpression(String reconcileCronExpression)
   {
      this.reconcileCronExpression = reconcileCronExpression;
      return (this);
   }




   /*******************************************************************************
    ** Getter for reconcileCronTimeZoneId: time zone for reconcileCronExpression
    *******************************************************************************/
   public String getReconcileCronTimeZoneId()
   {
      return (this.reconcileCronTimeZoneId);
   }



   /*******************************************************************************
    ** Setter for reconcileCronTimeZoneId
    *******************************************************************************/
   public void setReconcileCronTimeZoneId(String reconcileCronTimeZoneId)
   {
      this.reconcileCronTimeZoneId = reconcileCronTimeZoneId;
   }



   /*******************************************************************************
    ** Fluent setter for reconcileCronTimeZoneId
    *******************************************************************************/
   public QuickSearchQBitConfig withReconcileCronTimeZoneId(String reconcileCronTimeZoneId)
   {
      this.reconcileCronTimeZoneId = reconcileCronTimeZoneId;
      return (this);
   }




   /*******************************************************************************
    ** Getter for basepullOverlapSeconds: how far before the previous run start basepull re-reads, to absorb clock skew and long transactions
    *******************************************************************************/
   public Integer getBasepullOverlapSeconds()
   {
      return (this.basepullOverlapSeconds);
   }



   /*******************************************************************************
    ** Setter for basepullOverlapSeconds
    *******************************************************************************/
   public void setBasepullOverlapSeconds(Integer basepullOverlapSeconds)
   {
      this.basepullOverlapSeconds = basepullOverlapSeconds;
   }



   /*******************************************************************************
    ** Fluent setter for basepullOverlapSeconds
    *******************************************************************************/
   public QuickSearchQBitConfig withBasepullOverlapSeconds(Integer basepullOverlapSeconds)
   {
      this.basepullOverlapSeconds = basepullOverlapSeconds;
      return (this);
   }




   /*******************************************************************************
    ** Getter for defaultBasepullIntervalMinutes: default per-table basepull interval for config-driven tables
    *******************************************************************************/
   public Integer getDefaultBasepullIntervalMinutes()
   {
      return (this.defaultBasepullIntervalMinutes);
   }



   /*******************************************************************************
    ** Setter for defaultBasepullIntervalMinutes
    *******************************************************************************/
   public void setDefaultBasepullIntervalMinutes(Integer defaultBasepullIntervalMinutes)
   {
      this.defaultBasepullIntervalMinutes = defaultBasepullIntervalMinutes;
   }



   /*******************************************************************************
    ** Fluent setter for defaultBasepullIntervalMinutes
    *******************************************************************************/
   public QuickSearchQBitConfig withDefaultBasepullIntervalMinutes(Integer defaultBasepullIntervalMinutes)
   {
      this.defaultBasepullIntervalMinutes = defaultBasepullIntervalMinutes;
      return (this);
   }




   /*******************************************************************************
    ** Getter for runHistoryRetentionDays: delete quickSearchIndexRun rows older than this many days; null keeps them forever
    *******************************************************************************/
   public Integer getRunHistoryRetentionDays()
   {
      return (this.runHistoryRetentionDays);
   }



   /*******************************************************************************
    ** Setter for runHistoryRetentionDays
    *******************************************************************************/
   public void setRunHistoryRetentionDays(Integer runHistoryRetentionDays)
   {
      this.runHistoryRetentionDays = runHistoryRetentionDays;
   }



   /*******************************************************************************
    ** Fluent setter for runHistoryRetentionDays
    *******************************************************************************/
   public QuickSearchQBitConfig withRunHistoryRetentionDays(Integer runHistoryRetentionDays)
   {
      this.runHistoryRetentionDays = runHistoryRetentionDays;
      return (this);
   }




   /*******************************************************************************
    ** Getter for bulkBatchSize: documents per OpenSearch bulk request
    *******************************************************************************/
   public Integer getBulkBatchSize()
   {
      return (this.bulkBatchSize);
   }



   /*******************************************************************************
    ** Setter for bulkBatchSize
    *******************************************************************************/
   public void setBulkBatchSize(Integer bulkBatchSize)
   {
      this.bulkBatchSize = bulkBatchSize;
   }



   /*******************************************************************************
    ** Fluent setter for bulkBatchSize
    *******************************************************************************/
   public QuickSearchQBitConfig withBulkBatchSize(Integer bulkBatchSize)
   {
      this.bulkBatchSize = bulkBatchSize;
      return (this);
   }




   /*******************************************************************************
    ** Getter for sourceBatchSize: records per source table query page
    *******************************************************************************/
   public Integer getSourceBatchSize()
   {
      return (this.sourceBatchSize);
   }



   /*******************************************************************************
    ** Setter for sourceBatchSize
    *******************************************************************************/
   public void setSourceBatchSize(Integer sourceBatchSize)
   {
      this.sourceBatchSize = sourceBatchSize;
   }



   /*******************************************************************************
    ** Fluent setter for sourceBatchSize
    *******************************************************************************/
   public QuickSearchQBitConfig withSourceBatchSize(Integer sourceBatchSize)
   {
      this.sourceBatchSize = sourceBatchSize;
      return (this);
   }




   /*******************************************************************************
    ** Getter for maxFieldLength: truncate each indexed field value to this many characters; null for no limit
    *******************************************************************************/
   public Integer getMaxFieldLength()
   {
      return (this.maxFieldLength);
   }



   /*******************************************************************************
    ** Setter for maxFieldLength
    *******************************************************************************/
   public void setMaxFieldLength(Integer maxFieldLength)
   {
      this.maxFieldLength = maxFieldLength;
   }



   /*******************************************************************************
    ** Fluent setter for maxFieldLength
    *******************************************************************************/
   public QuickSearchQBitConfig withMaxFieldLength(Integer maxFieldLength)
   {
      this.maxFieldLength = maxFieldLength;
      return (this);
   }




   /*******************************************************************************
    ** Getter for maxSearchLimit: largest page size QuickSearchAction will honour
    *******************************************************************************/
   public Integer getMaxSearchLimit()
   {
      return (this.maxSearchLimit);
   }



   /*******************************************************************************
    ** Setter for maxSearchLimit
    *******************************************************************************/
   public void setMaxSearchLimit(Integer maxSearchLimit)
   {
      this.maxSearchLimit = maxSearchLimit;
   }



   /*******************************************************************************
    ** Fluent setter for maxSearchLimit
    *******************************************************************************/
   public QuickSearchQBitConfig withMaxSearchLimit(Integer maxSearchLimit)
   {
      this.maxSearchLimit = maxSearchLimit;
      return (this);
   }




   /*******************************************************************************
    ** Getter for applyRecordSecurityLocks: re-read search hits through QueryAction so record security locks apply
    *******************************************************************************/
   public Boolean getApplyRecordSecurityLocks()
   {
      return (this.applyRecordSecurityLocks);
   }



   /*******************************************************************************
    ** Setter for applyRecordSecurityLocks
    *******************************************************************************/
   public void setApplyRecordSecurityLocks(Boolean applyRecordSecurityLocks)
   {
      this.applyRecordSecurityLocks = applyRecordSecurityLocks;
   }



   /*******************************************************************************
    ** Fluent setter for applyRecordSecurityLocks
    *******************************************************************************/
   public QuickSearchQBitConfig withApplyRecordSecurityLocks(Boolean applyRecordSecurityLocks)
   {
      this.applyRecordSecurityLocks = applyRecordSecurityLocks;
      return (this);
   }




   /*******************************************************************************
    ** Getter for searchableEntityClasses: entity classes annotated with @QuickSearchable
    *******************************************************************************/
   public List<Class<?>> getSearchableEntityClasses()
   {
      return (this.searchableEntityClasses);
   }



   /*******************************************************************************
    ** Setter for searchableEntityClasses
    *******************************************************************************/
   public void setSearchableEntityClasses(List<Class<?>> searchableEntityClasses)
   {
      this.searchableEntityClasses = searchableEntityClasses;
   }



   /*******************************************************************************
    ** Fluent setter for searchableEntityClasses
    *******************************************************************************/
   public QuickSearchQBitConfig withSearchableEntityClasses(List<Class<?>> searchableEntityClasses)
   {
      this.searchableEntityClasses = searchableEntityClasses;
      return (this);
   }




   /*******************************************************************************
    ** Getter for searchableTables: config-driven searchable tables
    *******************************************************************************/
   public List<SearchableTableConfig> getSearchableTables()
   {
      return (this.searchableTables);
   }



   /*******************************************************************************
    ** Setter for searchableTables
    *******************************************************************************/
   public void setSearchableTables(List<SearchableTableConfig> searchableTables)
   {
      this.searchableTables = searchableTables;
   }



   /*******************************************************************************
    ** Fluent setter for searchableTables
    *******************************************************************************/
   public QuickSearchQBitConfig withSearchableTables(List<SearchableTableConfig> searchableTables)
   {
      this.searchableTables = searchableTables;
      return (this);
   }




   /*******************************************************************************
    ** Getter for indexEventPublisher: host-supplied publisher (defaults to SynchronousIndexEventPublisher)
    *******************************************************************************/
   public IndexEventPublisher getIndexEventPublisher()
   {
      return (this.indexEventPublisher);
   }



   /*******************************************************************************
    ** Setter for indexEventPublisher
    *******************************************************************************/
   public void setIndexEventPublisher(IndexEventPublisher indexEventPublisher)
   {
      this.indexEventPublisher = indexEventPublisher;
   }



   /*******************************************************************************
    ** Fluent setter for indexEventPublisher
    *******************************************************************************/
   public QuickSearchQBitConfig withIndexEventPublisher(IndexEventPublisher indexEventPublisher)
   {
      this.indexEventPublisher = indexEventPublisher;
      return (this);
   }




   /*******************************************************************************
    ** Getter for adminPermissionRules: permission rules for the admin app and the three processes
    *******************************************************************************/
   public QPermissionRules getAdminPermissionRules()
   {
      return (this.adminPermissionRules);
   }



   /*******************************************************************************
    ** Setter for adminPermissionRules
    *******************************************************************************/
   public void setAdminPermissionRules(QPermissionRules adminPermissionRules)
   {
      this.adminPermissionRules = adminPermissionRules;
   }



   /*******************************************************************************
    ** Fluent setter for adminPermissionRules
    *******************************************************************************/
   public QuickSearchQBitConfig withAdminPermissionRules(QPermissionRules adminPermissionRules)
   {
      this.adminPermissionRules = adminPermissionRules;
      return (this);
   }




   /*******************************************************************************
    ** Getter for tablePermissionRules: permission rules for the operational tables
    *******************************************************************************/
   public QPermissionRules getTablePermissionRules()
   {
      return (this.tablePermissionRules);
   }



   /*******************************************************************************
    ** Setter for tablePermissionRules
    *******************************************************************************/
   public void setTablePermissionRules(QPermissionRules tablePermissionRules)
   {
      this.tablePermissionRules = tablePermissionRules;
   }



   /*******************************************************************************
    ** Fluent setter for tablePermissionRules
    *******************************************************************************/
   public QuickSearchQBitConfig withTablePermissionRules(QPermissionRules tablePermissionRules)
   {
      this.tablePermissionRules = tablePermissionRules;
      return (this);
   }




   /*******************************************************************************
    ** Getter for runtime: live state once produced (client, publisher, discovered tables)
    *******************************************************************************/
   @JsonIgnore
   public QuickSearchRuntime getRuntime()
   {
      return (this.runtime);
   }



   /*******************************************************************************
    ** Setter for runtime
    *******************************************************************************/
   public void setRuntime(QuickSearchRuntime runtime)
   {
      this.runtime = runtime;
   }



   /*******************************************************************************
    ** Fluent setter for runtime
    *******************************************************************************/
   public QuickSearchQBitConfig withRuntime(QuickSearchRuntime runtime)
   {
      this.runtime = runtime;
      return (this);
   }

}

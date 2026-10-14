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

package com.kingsrook.qbits.quicksearch.config;


import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Pattern;
import com.kingsrook.qqq.backend.core.actions.ai.AiDataClassifications;
import com.kingsrook.qqq.backend.core.actions.ai.DataClassification;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper;
import com.kingsrook.qqq.backend.core.actions.permissions.TablePermissionSubType;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Capability;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFieldRole;


/*******************************************************************************
 ** Which tables and fields may be indexed, and the defaults offered for a new
 ** index. Shared by the Add Search Index process, the eligible-table and
 ** field possible-value sources, the field table's validation and priming,
 ** so all of them agree.
 **
 ** A table is eligible when it is in the instance, queryable, not hidden, not
 ** on backend variants, not produced by a QBit (qbit-internal), not a QQQ
 ** system table (audits, saved views and reports, scheduled jobs, scripts),
 ** not named like a secret store, and has at least one indexable field. A
 ** field is indexable when it is not PII/PHI-classified, locked, hidden,
 ** sensitive, masked, a blob or a boolean.
 *******************************************************************************/
public final class QuickSearchFieldRules
{
   private static final QLogger LOG = QLogger.getLogger(QuickSearchFieldRules.class);
   private static final WeakHashMap<QTableMetaData, Boolean> WARNED_TABLES = new WeakHashMap<>();

   public static final int MAX_DEFAULT_FIELDS = 20;
   public static final int TITLE_WEIGHT       = 3;

   private static final Set<String> SYSTEM_TABLES = Set.of(
      "audit", "auditdetail", "audittable", "audituser", "savedview", "savedreport", "sharedsavedview", "sharedsavedreport",
      "scheduledjob", "scheduledjobparameter", "script", "scriptrevision", "scriptrevisionfile", "scriptlog", "scriptlogline",
      "scripttype", "scripttypefilemode", "tabletrigger", "qqqtable", "qqqtablecache", "qqqprocess", "processtrace", "cachedrecord");

   private static final Pattern SECRET_NAME = Pattern.compile("(secret|credential|password|passwd|token|api_?key|session|oauth|private_?key)", Pattern.CASE_INSENSITIVE);



   /*******************************************************************************
    ** A field picked by default for a new index.
    *******************************************************************************/
   public record DefaultField(String fieldName, Integer weight, QuickSearchFieldRole role)
   {
   }



   /*******************************************************************************
    ** Utility class - no instances.
    *******************************************************************************/
   private QuickSearchFieldRules()
   {
   }



   /*******************************************************************************
    ** Why a field cannot be indexed, or null when it can.
    ** When the qbit moves to QQQ 4.1.0-RC.7, also honour core
    ** ClassifiedValues.isClassified(table, field) (the host's AuditValueProtector
    ** classification, qqq#1182) and FieldAccessHelper.getLockTier (qqq#1172).
    *******************************************************************************/
   public static String whyNotIndexable(QTableMetaData table, String fieldName)
   {
      if(table == null)
      {
         return ("the table is not in this application");
      }
      QFieldMetaData field = fieldName == null ? null : table.getFields().get(fieldName);
      if(field == null)
      {
         return ("field [" + fieldName + "] does not exist on " + label(table));
      }
      if(Boolean.TRUE.equals(field.getIsHidden()))
      {
         return ("field [" + fieldName + "] is hidden");
      }
      DataClassification classification = AiDataClassifications.ofField(table, fieldName);
      if(classification == DataClassification.PII || classification == DataClassification.PHI)
      {
         return ("field [" + fieldName + "] is classified " + classification + " and cannot be indexed");
      }
      if(field.getFieldSecurityLock() != null)
      {
         return ("field [" + fieldName + "] has a field security lock and cannot be indexed");
      }
      if(field.getIsSensitive() || (field.getType() != null && field.getType().needsMasked()))
      {
         return ("field [" + fieldName + "] is sensitive (its values must not reach the search index)");
      }
      if(field.getType() == null || field.getType() == QFieldType.BLOB || field.getType() == QFieldType.BOOLEAN)
      {
         return ("field [" + fieldName + "] is a " + (field.getType() == null ? "typeless" : field.getType().name().toLowerCase(Locale.ROOT)) + " field, which cannot be searched");
      }
      return (null);
   }



   /*******************************************************************************
    ** Whether a field can be indexed.
    *******************************************************************************/
   public static boolean isIndexable(QTableMetaData table, String fieldName)
   {
      return (whyNotIndexable(table, fieldName) == null);
   }



   /*******************************************************************************
    ** Whether a field contains protected values, independently of searchability.
    ** Classification falls back to the table. When the qbit moves to QQQ
    ** 4.1.0-RC.7, also honour ClassifiedValues.isClassified(table, field)
    ** (qqq#1182) and FieldAccessHelper.getLockTier (qqq#1172).
    *******************************************************************************/
   public static boolean isProtected(QTableMetaData table, String fieldName)
   {
      QFieldMetaData field = table == null || fieldName == null ? null : table.getFields().get(fieldName);
      if(field == null)
      {
         return (false);
      }
      DataClassification classification = AiDataClassifications.ofField(table, fieldName);
      return (classification == DataClassification.PII || classification == DataClassification.PHI
         || field.getFieldSecurityLock() != null || field.getIsSensitive()
         || (field.getType() != null && field.getType().needsMasked()));
   }



   /*******************************************************************************
    ** Warn once per table metadata lifetime; never include record values.
    *******************************************************************************/
   public static synchronized void warnExcludedFields(QTableMetaData table)
   {
      if(table != null && WARNED_TABLES.put(table, Boolean.TRUE) == null)
      {
         LOG.warn("Quick Search dropped fields that cannot be indexed (classified PII/PHI, secured, hidden, sensitive or unsupported fields) from table [" + table.getName() + "]");
      }
   }



   /*******************************************************************************
    ** Filter explicit field lists as well as cached configurations at use time.
    *******************************************************************************/
   public static List<String> indexableFields(QTableMetaData table, List<String> fields)
   {
      List<String> result = new ArrayList<>();
      for(String field : CollectionUtils.nonNullList(fields))
      {
         if(isIndexable(table, field))
         {
            result.add(field);
         }
         else
         {
            warnExcludedFields(table);
         }
      }
      return (result);
   }



   /*******************************************************************************
    ** Resolve a table's metadata for backend query and result filtering.
    *******************************************************************************/
   public static QTableMetaData lookupTable(String tableName)
   {
      QInstance instance = QContext.getQInstance();
      return (instance == null || tableName == null ? null : instance.getTable(tableName));
   }



   /*******************************************************************************
    ** Combined text and labels from an older index cannot be attributed to
    ** individual fields. Use only allowed field values for these tables.
    *******************************************************************************/
   public static boolean requiresFieldFiltering(String tableName)
   {
      QTableMetaData table = lookupTable(tableName);
      return (table != null && table.getFields().keySet().stream().anyMatch(f -> isProtected(table, f)));
   }



   /*******************************************************************************
    ** A protected primary key cannot be sent as an index id, even for deletes.
    *******************************************************************************/
   public static boolean hasProtectedPrimaryKey(String tableName)
   {
      QTableMetaData table = lookupTable(tableName);
      return (table != null && isProtected(table, table.getPrimaryKeyField()));
   }



   /*******************************************************************************
    ** The indexable fields of a table, in the table's order.
    *******************************************************************************/
   public static List<QFieldMetaData> indexableFields(QTableMetaData table)
   {
      List<QFieldMetaData> fields = new ArrayList<>();
      if(table != null)
      {
         for(QFieldMetaData field : table.getFields().values())
         {
            if(isIndexable(table, field.getName()))
            {
               fields.add(field);
            }
         }
      }
      return (fields);
   }



   /*******************************************************************************
    ** The fields a new index starts with: the record-label fields as TITLE
    ** (weight 3), then the other text fields (string, text, HTML) as BODY
    ** (weight 1), up to MAX_DEFAULT_FIELDS. The admin adjusts them afterwards on
    ** the index's field rows.
    *******************************************************************************/
   public static List<DefaultField> defaultFields(QTableMetaData table)
   {
      List<DefaultField> fields = new ArrayList<>();
      Set<String>        picked = new LinkedHashSet<>();
      if(table == null)
      {
         return (fields);
      }

      for(String labelField : CollectionUtils.nonNullList(table.getRecordLabelFields()))
      {
         if(isIndexable(table, labelField) && picked.add(labelField))
         {
            fields.add(new DefaultField(labelField, TITLE_WEIGHT, QuickSearchFieldRole.TITLE));
         }
      }

      for(QFieldMetaData field : table.getFields().values())
      {
         if(fields.size() >= MAX_DEFAULT_FIELDS)
         {
            break;
         }
         if(field.getType() != null && field.getType().isStringLike() && isIndexable(table, field.getName()) && picked.add(field.getName()))
         {
            fields.add(new DefaultField(field.getName(), 1, QuickSearchFieldRole.BODY));
         }
      }
      return (fields);
   }



   /*******************************************************************************
    ** Whether a field can drive incremental basepull (a date-time).
    *******************************************************************************/
   public static boolean isTimestampField(QTableMetaData table, String fieldName)
   {
      QFieldMetaData field = table == null || fieldName == null ? null : table.getFields().get(fieldName);
      return (field != null && field.getType() == QFieldType.DATE_TIME);
   }



   /*******************************************************************************
    ** The timestamp field a new index starts with: modifyDate when the table
    ** has it, else the first date-time field named like a modify or update
    ** time, else none (incremental basepull off; reconcile covers the table).
    *******************************************************************************/
   public static String defaultTimestampField(QTableMetaData table)
   {
      if(table == null)
      {
         return (null);
      }
      if(isTimestampField(table, QuickSearchQBitConfig.DEFAULT_BASEPULL_TIMESTAMP_FIELD))
      {
         return (QuickSearchQBitConfig.DEFAULT_BASEPULL_TIMESTAMP_FIELD);
      }
      for(QFieldMetaData field : table.getFields().values())
      {
         String name = field.getName().toLowerCase(Locale.ROOT);
         if(field.getType() == QFieldType.DATE_TIME && (name.contains("modif") || name.contains("updat")))
         {
            return (field.getName());
         }
      }
      return (null);
   }



   /*******************************************************************************
    ** Why a table cannot be indexed at all (for anyone), or null when it can.
    *******************************************************************************/
   public static String whyNotEligible(QInstance qInstance, QuickSearchQBitConfig config, String tableName)
   {
      QTableMetaData table = qInstance == null || tableName == null ? null : qInstance.getTable(tableName);
      if(table == null)
      {
         return ("table [" + tableName + "] is not in this application");
      }
      if(isQuickSearchTable(config, tableName) || table.getSourceQBitName() != null)
      {
         return (label(table) + " is internal to a QBit");
      }
      String normalized = tableName.replace("_", "").toLowerCase(Locale.ROOT);
      if(SYSTEM_TABLES.contains(normalized))
      {
         return (label(table) + " is a system table");
      }
      if(SECRET_NAME.matcher(tableName).find())
      {
         return (label(table) + " looks like a store of secrets");
      }
      if(table.getIsHidden())
      {
         return (label(table) + " is hidden");
      }
      QBackendMetaData backend = qInstance.getBackend(table.getBackendName());
      if(backend != null && Boolean.TRUE.equals(backend.getUsesVariants()))
      {
         return (label(table) + " uses backend variants, which Quick Search does not support");
      }
      if(!table.isCapabilityEnabled(backend, Capability.TABLE_QUERY))
      {
         return (label(table) + " cannot be queried");
      }
      if(indexableFields(table).isEmpty())
      {
         return (label(table) + " has no field that can be searched");
      }
      return (null);
   }



   /*******************************************************************************
    ** Whether the current user may read a table.
    *******************************************************************************/
   public static boolean canRead(String tableName)
   {
      try
      {
         return (PermissionsHelper.hasTablePermission(new QueryInput(tableName), tableName, TablePermissionSubType.READ));
      }
      catch(Exception e)
      {
         return (false);
      }
   }



   /*******************************************************************************
    ** The tables the current user could add: eligible, readable and not
    ** already indexed, sorted by label.
    *******************************************************************************/
   public static List<QTableMetaData> eligibleTables(QInstance qInstance, QuickSearchQBitConfig config, Collection<String> alreadyIndexed)
   {
      List<QTableMetaData> tables = new ArrayList<>();
      if(qInstance == null)
      {
         return (tables);
      }
      for(QTableMetaData table : qInstance.getTables().values())
      {
         if(alreadyIndexed.contains(table.getName()) || whyNotEligible(qInstance, config, table.getName()) != null || !canRead(table.getName()))
         {
            continue;
         }
         tables.add(table);
      }
      tables.sort((a, b) -> label(a).compareToIgnoreCase(label(b)));
      return (tables);
   }



   /*******************************************************************************
    ** Whether a table is one of this QBit's own tables.
    *******************************************************************************/
   public static boolean isQuickSearchTable(QuickSearchQBitConfig config, String tableName)
   {
      return (tableName != null && config != null && tableName.startsWith(config.applyTablePrefix("quick_search_")));
   }



   /*******************************************************************************
    ** A table's label, or its name.
    *******************************************************************************/
   public static String label(QTableMetaData table)
   {
      return (table.getLabel() == null ? table.getName() : table.getLabel());
   }

}

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
import java.util.Map;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFieldAnalyzer;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFieldRole;


/*******************************************************************************
 ** Configuration for a table that is searchable via Quick Search.
 **
 ** Describes which fields to index, optional per-field boost weights,
 ** optional per-field label-inclusion flags, basepull scheduling, and
 ** whether the table is enabled by default.
 **
 ** Code (annotations, searchableTables) builds these as defaults; at runtime
 ** the effective configs are built from the quick_search_index and
 ** quick_search_index_field records (QuickSearchConfigStore), which add each
 ** field's role, analyzer and snippet flag, the table's enabled flag and its
 ** index row id.
 *******************************************************************************/
public class QuickSearchableTableConfig
{
   private String              tableName;
   private String              primaryKeyField;
   private List<String>        searchableFields;
   private Map<String, Integer> fieldWeights;
   private Map<String, Boolean> fieldIncludeLabels;
   private Integer             basepullIntervalMinutes;
   private String              basepullTimestampField;
   private Boolean             enabledByDefault;
   private String              recordLabelFormat;
   private List<String>        recordLabelFields;
   private Integer             maxFieldLength;
   private Map<String, String>  fieldRoles;
   private Map<String, String>  fieldAnalyzers;
   private Map<String, Boolean> fieldIncludeInSnippet;
   private Boolean             enabled;
   private Integer             quickSearchIndexId;



   /***************************************************************************
    ** Getter for tableName
    ***************************************************************************/
   public String getTableName()
   {
      return (this.tableName);
   }



   /***************************************************************************
    ** Setter for tableName
    ***************************************************************************/
   public void setTableName(String tableName)
   {
      this.tableName = tableName;
   }



   /***************************************************************************
    ** Fluent setter for tableName
    ***************************************************************************/
   public QuickSearchableTableConfig withTableName(String tableName)
   {
      this.tableName = tableName;
      return (this);
   }



   /***************************************************************************
    ** Getter for primaryKeyField
    ***************************************************************************/
   public String getPrimaryKeyField()
   {
      return (this.primaryKeyField);
   }



   /***************************************************************************
    ** Setter for primaryKeyField
    ***************************************************************************/
   public void setPrimaryKeyField(String primaryKeyField)
   {
      this.primaryKeyField = primaryKeyField;
   }



   /***************************************************************************
    ** Fluent setter for primaryKeyField
    ***************************************************************************/
   public QuickSearchableTableConfig withPrimaryKeyField(String primaryKeyField)
   {
      this.primaryKeyField = primaryKeyField;
      return (this);
   }



   /***************************************************************************
    ** Getter for searchableFields
    ***************************************************************************/
   public List<String> getSearchableFields()
   {
      return (this.searchableFields);
   }



   /***************************************************************************
    ** Setter for searchableFields
    ***************************************************************************/
   public void setSearchableFields(List<String> searchableFields)
   {
      this.searchableFields = searchableFields;
   }



   /***************************************************************************
    ** Fluent setter for searchableFields
    ***************************************************************************/
   public QuickSearchableTableConfig withSearchableFields(List<String> searchableFields)
   {
      this.searchableFields = searchableFields;
      return (this);
   }



   /***************************************************************************
    ** Getter for fieldWeights
    ***************************************************************************/
   public Map<String, Integer> getFieldWeights()
   {
      return (this.fieldWeights);
   }



   /***************************************************************************
    ** Setter for fieldWeights
    ***************************************************************************/
   public void setFieldWeights(Map<String, Integer> fieldWeights)
   {
      this.fieldWeights = fieldWeights;
   }



   /***************************************************************************
    ** Fluent setter for fieldWeights
    ***************************************************************************/
   public QuickSearchableTableConfig withFieldWeights(Map<String, Integer> fieldWeights)
   {
      this.fieldWeights = fieldWeights;
      return (this);
   }



   /***************************************************************************
    ** Getter for fieldIncludeLabels
    ***************************************************************************/
   public Map<String, Boolean> getFieldIncludeLabels()
   {
      return (this.fieldIncludeLabels);
   }



   /***************************************************************************
    ** Setter for fieldIncludeLabels
    ***************************************************************************/
   public void setFieldIncludeLabels(Map<String, Boolean> fieldIncludeLabels)
   {
      this.fieldIncludeLabels = fieldIncludeLabels;
   }



   /***************************************************************************
    ** Fluent setter for fieldIncludeLabels
    ***************************************************************************/
   public QuickSearchableTableConfig withFieldIncludeLabels(Map<String, Boolean> fieldIncludeLabels)
   {
      this.fieldIncludeLabels = fieldIncludeLabels;
      return (this);
   }



   /***************************************************************************
    ** Getter for basepullIntervalMinutes
    ***************************************************************************/
   public Integer getBasepullIntervalMinutes()
   {
      return (this.basepullIntervalMinutes);
   }



   /***************************************************************************
    ** Setter for basepullIntervalMinutes
    ***************************************************************************/
   public void setBasepullIntervalMinutes(Integer basepullIntervalMinutes)
   {
      this.basepullIntervalMinutes = basepullIntervalMinutes;
   }



   /***************************************************************************
    ** Fluent setter for basepullIntervalMinutes
    ***************************************************************************/
   public QuickSearchableTableConfig withBasepullIntervalMinutes(Integer basepullIntervalMinutes)
   {
      this.basepullIntervalMinutes = basepullIntervalMinutes;
      return (this);
   }



   /***************************************************************************
    ** Getter for basepullTimestampField
    ***************************************************************************/
   public String getBasepullTimestampField()
   {
      return (this.basepullTimestampField);
   }



   /***************************************************************************
    ** Setter for basepullTimestampField
    ***************************************************************************/
   public void setBasepullTimestampField(String basepullTimestampField)
   {
      this.basepullTimestampField = basepullTimestampField;
   }



   /***************************************************************************
    ** Fluent setter for basepullTimestampField
    ***************************************************************************/
   public QuickSearchableTableConfig withBasepullTimestampField(String basepullTimestampField)
   {
      this.basepullTimestampField = basepullTimestampField;
      return (this);
   }



   /***************************************************************************
    ** Getter for enabledByDefault
    ***************************************************************************/
   public Boolean getEnabledByDefault()
   {
      return (this.enabledByDefault);
   }



   /***************************************************************************
    ** Setter for enabledByDefault
    ***************************************************************************/
   public void setEnabledByDefault(Boolean enabledByDefault)
   {
      this.enabledByDefault = enabledByDefault;
   }



   /***************************************************************************
    ** Fluent setter for enabledByDefault
    ***************************************************************************/
   public QuickSearchableTableConfig withEnabledByDefault(Boolean enabledByDefault)
   {
      this.enabledByDefault = enabledByDefault;
      return (this);
   }



   /***************************************************************************
    ** Getter for recordLabelFormat
    ***************************************************************************/
   public String getRecordLabelFormat()
   {
      return (this.recordLabelFormat);
   }



   /***************************************************************************
    ** Setter for recordLabelFormat
    ***************************************************************************/
   public void setRecordLabelFormat(String recordLabelFormat)
   {
      this.recordLabelFormat = recordLabelFormat;
   }



   /***************************************************************************
    ** Fluent setter for recordLabelFormat
    ***************************************************************************/
   public QuickSearchableTableConfig withRecordLabelFormat(String recordLabelFormat)
   {
      this.recordLabelFormat = recordLabelFormat;
      return (this);
   }



   /***************************************************************************
    ** Getter for recordLabelFields
    ***************************************************************************/
   public List<String> getRecordLabelFields()
   {
      return (this.recordLabelFields);
   }



   /***************************************************************************
    ** Setter for recordLabelFields
    ***************************************************************************/
   public void setRecordLabelFields(List<String> recordLabelFields)
   {
      this.recordLabelFields = recordLabelFields;
   }



   /***************************************************************************
    ** Fluent setter for recordLabelFields
    ***************************************************************************/
   public QuickSearchableTableConfig withRecordLabelFields(List<String> recordLabelFields)
   {
      this.recordLabelFields = recordLabelFields;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxFieldLength
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
   public QuickSearchableTableConfig withMaxFieldLength(Integer maxFieldLength)
   {
      this.maxFieldLength = maxFieldLength;
      return (this);
   }



   /*******************************************************************************
    ** Getter for fieldRoles
    *******************************************************************************/
   public Map<String, String> getFieldRoles()
   {
      return (this.fieldRoles);
   }



   /*******************************************************************************
    ** Setter for fieldRoles
    *******************************************************************************/
   public void setFieldRoles(Map<String, String> fieldRoles)
   {
      this.fieldRoles = fieldRoles;
   }



   /*******************************************************************************
    ** Fluent setter for fieldRoles
    *******************************************************************************/
   public QuickSearchableTableConfig withFieldRoles(Map<String, String> fieldRoles)
   {
      this.fieldRoles = fieldRoles;
      return (this);
   }


   /*******************************************************************************
    ** Getter for fieldAnalyzers
    *******************************************************************************/
   public Map<String, String> getFieldAnalyzers()
   {
      return (this.fieldAnalyzers);
   }



   /*******************************************************************************
    ** Setter for fieldAnalyzers
    *******************************************************************************/
   public void setFieldAnalyzers(Map<String, String> fieldAnalyzers)
   {
      this.fieldAnalyzers = fieldAnalyzers;
   }



   /*******************************************************************************
    ** Fluent setter for fieldAnalyzers
    *******************************************************************************/
   public QuickSearchableTableConfig withFieldAnalyzers(Map<String, String> fieldAnalyzers)
   {
      this.fieldAnalyzers = fieldAnalyzers;
      return (this);
   }


   /*******************************************************************************
    ** Getter for fieldIncludeInSnippet
    *******************************************************************************/
   public Map<String, Boolean> getFieldIncludeInSnippet()
   {
      return (this.fieldIncludeInSnippet);
   }



   /*******************************************************************************
    ** Setter for fieldIncludeInSnippet
    *******************************************************************************/
   public void setFieldIncludeInSnippet(Map<String, Boolean> fieldIncludeInSnippet)
   {
      this.fieldIncludeInSnippet = fieldIncludeInSnippet;
   }



   /*******************************************************************************
    ** Fluent setter for fieldIncludeInSnippet
    *******************************************************************************/
   public QuickSearchableTableConfig withFieldIncludeInSnippet(Map<String, Boolean> fieldIncludeInSnippet)
   {
      this.fieldIncludeInSnippet = fieldIncludeInSnippet;
      return (this);
   }


   /*******************************************************************************
    ** Getter for enabled
    *******************************************************************************/
   public Boolean getEnabled()
   {
      return (this.enabled);
   }



   /*******************************************************************************
    ** Setter for enabled
    *******************************************************************************/
   public void setEnabled(Boolean enabled)
   {
      this.enabled = enabled;
   }



   /*******************************************************************************
    ** Fluent setter for enabled
    *******************************************************************************/
   public QuickSearchableTableConfig withEnabled(Boolean enabled)
   {
      this.enabled = enabled;
      return (this);
   }


   /*******************************************************************************
    ** Getter for quickSearchIndexId
    *******************************************************************************/
   public Integer getQuickSearchIndexId()
   {
      return (this.quickSearchIndexId);
   }



   /*******************************************************************************
    ** Setter for quickSearchIndexId
    *******************************************************************************/
   public void setQuickSearchIndexId(Integer quickSearchIndexId)
   {
      this.quickSearchIndexId = quickSearchIndexId;
   }



   /*******************************************************************************
    ** Fluent setter for quickSearchIndexId
    *******************************************************************************/
   public QuickSearchableTableConfig withQuickSearchIndexId(Integer quickSearchIndexId)
   {
      this.quickSearchIndexId = quickSearchIndexId;
      return (this);
   }


   /*******************************************************************************
    ** A field's role; BODY when none is set.
    *******************************************************************************/
   public QuickSearchFieldRole getRole(String fieldName)
   {
      return (QuickSearchFieldRole.fromName(fieldRoles == null ? null : fieldRoles.get(fieldName), QuickSearchFieldRole.BODY));
   }



   /*******************************************************************************
    ** A field's analyzer: the one set, else EXACT for an EXACT field, WORD for a
    ** KEYWORD field and PREFIX for any other.
    *******************************************************************************/
   public QuickSearchFieldAnalyzer getAnalyzer(String fieldName)
   {
      QuickSearchFieldRole     role     = getRole(fieldName);
      QuickSearchFieldAnalyzer fallback = role == QuickSearchFieldRole.EXACT ? QuickSearchFieldAnalyzer.EXACT
         : (role == QuickSearchFieldRole.KEYWORD ? QuickSearchFieldAnalyzer.WORD : QuickSearchFieldAnalyzer.PREFIX);
      if(role == QuickSearchFieldRole.EXACT)
      {
         return (QuickSearchFieldAnalyzer.EXACT);
      }
      return (QuickSearchFieldAnalyzer.fromName(fieldAnalyzers == null ? null : fieldAnalyzers.get(fieldName), fallback));
   }



   /*******************************************************************************
    ** Whether a field's text goes into the highlighted snippet (searchableText).
    ** Defaults to true, except for KEYWORD and EXACT fields and EXACT-analyzed
    ** fields, which are matched on their own instead.
    *******************************************************************************/
   public boolean isIncludedInSnippet(String fieldName)
   {
      if(getAnalyzer(fieldName) == QuickSearchFieldAnalyzer.EXACT)
      {
         return (false);
      }
      Boolean flag = fieldIncludeInSnippet == null ? null : fieldIncludeInSnippet.get(fieldName);
      if(flag != null)
      {
         return (flag);
      }
      QuickSearchFieldRole role = getRole(fieldName);
      return (role != QuickSearchFieldRole.KEYWORD && role != QuickSearchFieldRole.EXACT);
   }



   /*******************************************************************************
    ** Whether every field is plain: in the snippet with the PREFIX analyzer, so
    ** documents and queries look exactly as they did before roles existed.
    *******************************************************************************/
   public boolean isAllPlainFields()
   {
      for(String fieldName : searchableFields == null ? List.<String>of() : searchableFields)
      {
         if(!isIncludedInSnippet(fieldName) || getAnalyzer(fieldName) != QuickSearchFieldAnalyzer.PREFIX)
         {
            return (false);
         }
      }
      return (true);
   }

}

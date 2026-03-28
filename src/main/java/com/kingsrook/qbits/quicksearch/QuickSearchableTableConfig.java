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


/*******************************************************************************
 ** Configuration for a table that is searchable via Quick Search.
 **
 ** Describes which fields to index, optional per-field boost weights,
 ** optional per-field label-inclusion flags, basepull scheduling, and
 ** whether the table is enabled by default.
 *******************************************************************************/
public class QuickSearchableTableConfig
{
   private String              tableName;
   private List<String>        searchableFields;
   private Map<String, Integer> fieldWeights;
   private Map<String, Boolean> fieldIncludeLabels;
   private Integer             basepullIntervalMinutes;
   private String              basepullTimestampField;
   private Boolean             enabledByDefault;



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

}

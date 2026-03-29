/*
 * QBit Quick Search
 * Copyright (C) 2024-2025 QRun-IO, LLC
 * https://www.qrun.io | https://github.com/QRun-IO
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
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
   private String              primaryKeyField;
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

}

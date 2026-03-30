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


import java.util.Arrays;
import java.util.List;


/*******************************************************************************
 ** Table-level configuration for a config-driven searchable table.
 **
 ** Specifies the QQQ table name, the fields to index, basepull scheduling
 ** options, and an optional record label format for search result display.
 *******************************************************************************/
public class SearchableTableConfig
{
   private String                    tableName;
   private List<SearchableFieldConfig> fields;
   private Integer                   basepullIntervalMinutes;
   private String                    basepullTimestampField = "modifyDate";
   private Boolean                   enabledByDefault       = true;
   private String                    recordLabelFormat;
   private List<String>              recordLabelFields;



   /***************************************************************************
    ** Constructor that takes the required tableName and fields.
    ***************************************************************************/
   public SearchableTableConfig(String tableName, List<SearchableFieldConfig> fields)
   {
      this.tableName = tableName;
      this.fields = fields;
   }



   /***************************************************************************
    ** Convenience method that sets both recordLabelFormat and recordLabelFields.
    ***************************************************************************/
   public SearchableTableConfig withRecordLabelFormat(String format, String... fields)
   {
      this.recordLabelFormat = format;
      this.recordLabelFields = Arrays.asList(fields);
      return (this);
   }



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
   public SearchableTableConfig withTableName(String tableName)
   {
      this.tableName = tableName;
      return (this);
   }



   /***************************************************************************
    ** Getter for fields
    ***************************************************************************/
   public List<SearchableFieldConfig> getFields()
   {
      return (this.fields);
   }



   /***************************************************************************
    ** Setter for fields
    ***************************************************************************/
   public void setFields(List<SearchableFieldConfig> fields)
   {
      this.fields = fields;
   }



   /***************************************************************************
    ** Fluent setter for fields
    ***************************************************************************/
   public SearchableTableConfig withFields(List<SearchableFieldConfig> fields)
   {
      this.fields = fields;
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
   public SearchableTableConfig withBasepullIntervalMinutes(Integer basepullIntervalMinutes)
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
   public SearchableTableConfig withBasepullTimestampField(String basepullTimestampField)
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
   public SearchableTableConfig withEnabledByDefault(Boolean enabledByDefault)
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
    ** Getter for recordLabelFields
    ***************************************************************************/
   public List<String> getRecordLabelFields()
   {
      return (this.recordLabelFields);
   }



   /***************************************************************************
    ** Fluent setter for recordLabelFormat (individual field)
    ***************************************************************************/
   public SearchableTableConfig withRecordLabelFormatString(String recordLabelFormat)
   {
      this.recordLabelFormat = recordLabelFormat;
      return (this);
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
   public SearchableTableConfig withRecordLabelFields(List<String> recordLabelFields)
   {
      this.recordLabelFields = recordLabelFields;
      return (this);
   }

}

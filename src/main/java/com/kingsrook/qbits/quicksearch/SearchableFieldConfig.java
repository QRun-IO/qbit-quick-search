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


/*******************************************************************************
 ** Configuration for a single field to include in Quick Search indexed text.
 **
 ** Specifies the QQQ field name, an optional boost weight for search relevance
 ** ranking, and whether the field's label should be prefixed in the indexed text.
 *******************************************************************************/
public class SearchableFieldConfig
{
   private String  fieldName;
   private Integer weight       = 1;
   private Boolean includeLabel = false;



   /***************************************************************************
    ** Constructor that takes the required fieldName.
    ***************************************************************************/
   public SearchableFieldConfig(String fieldName)
   {
      this.fieldName = fieldName;
   }



   /***************************************************************************
    ** Getter for fieldName
    ***************************************************************************/
   public String getFieldName()
   {
      return (this.fieldName);
   }



   /***************************************************************************
    ** Setter for fieldName
    ***************************************************************************/
   public void setFieldName(String fieldName)
   {
      this.fieldName = fieldName;
   }



   /***************************************************************************
    ** Fluent setter for fieldName
    ***************************************************************************/
   public SearchableFieldConfig withFieldName(String fieldName)
   {
      this.fieldName = fieldName;
      return (this);
   }



   /***************************************************************************
    ** Getter for weight
    ***************************************************************************/
   public Integer getWeight()
   {
      return (this.weight);
   }



   /***************************************************************************
    ** Setter for weight
    ***************************************************************************/
   public void setWeight(Integer weight)
   {
      this.weight = weight;
   }



   /***************************************************************************
    ** Fluent setter for weight
    ***************************************************************************/
   public SearchableFieldConfig withWeight(Integer weight)
   {
      this.weight = weight;
      return (this);
   }



   /***************************************************************************
    ** Getter for includeLabel
    ***************************************************************************/
   public Boolean getIncludeLabel()
   {
      return (this.includeLabel);
   }



   /***************************************************************************
    ** Setter for includeLabel
    ***************************************************************************/
   public void setIncludeLabel(Boolean includeLabel)
   {
      this.includeLabel = includeLabel;
   }



   /***************************************************************************
    ** Fluent setter for includeLabel
    ***************************************************************************/
   public SearchableFieldConfig withIncludeLabel(Boolean includeLabel)
   {
      this.includeLabel = includeLabel;
      return (this);
   }

}

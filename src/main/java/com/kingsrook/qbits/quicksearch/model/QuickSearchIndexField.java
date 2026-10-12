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

package com.kingsrook.qbits.quicksearch.model;


import java.time.Instant;
import com.kingsrook.qqq.backend.core.model.data.QField;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;


/*******************************************************************************
 ** Entity bean for the quick_search_index_field table: one searchable field of
 ** one quick_search_index row. These rows are the source of truth for what is
 ** indexed and how it is boosted; code (@QuickSearchField, searchableTables)
 ** only seeds them (see QuickSearchConfigPrimer for the precedence rules).
 *******************************************************************************/
public class QuickSearchIndexField extends QRecordEntity
{
   public static final String TABLE_NAME = "quick_search_index_field";

   public static final String SOURCE_CODE  = "CODE";
   public static final String SOURCE_OWNER = "OWNER";

   @QField()
   private Integer id;
   @QField()
   private Integer quickSearchIndexId;
   @QField()
   private String fieldName;
   @QField()
   private Integer weight;
   @QField()
   private String role;
   @QField()
   private String analyzer;
   @QField()
   private Boolean includeInSnippet;
   @QField()
   private Boolean includeLabel;
   @QField()
   private Integer sortOrder;
   @QField()
   private String source;
   @QField()
   private Boolean ownerEdited;
   @QField()
   private Instant createDate;
   @QField()
   private Instant modifyDate;


   /*******************************************************************************
    ** Getter for id
    *******************************************************************************/
   public Integer getId()
   {
      return (this.id);
   }



   /*******************************************************************************
    ** Setter for id
    *******************************************************************************/
   public void setId(Integer id)
   {
      this.id = id;
   }



   /*******************************************************************************
    ** Fluent setter for id
    *******************************************************************************/
   public QuickSearchIndexField withId(Integer id)
   {
      this.id = id;
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
   public QuickSearchIndexField withQuickSearchIndexId(Integer quickSearchIndexId)
   {
      this.quickSearchIndexId = quickSearchIndexId;
      return (this);
   }


   /*******************************************************************************
    ** Getter for fieldName
    *******************************************************************************/
   public String getFieldName()
   {
      return (this.fieldName);
   }



   /*******************************************************************************
    ** Setter for fieldName
    *******************************************************************************/
   public void setFieldName(String fieldName)
   {
      this.fieldName = fieldName;
   }



   /*******************************************************************************
    ** Fluent setter for fieldName
    *******************************************************************************/
   public QuickSearchIndexField withFieldName(String fieldName)
   {
      this.fieldName = fieldName;
      return (this);
   }


   /*******************************************************************************
    ** Getter for weight
    *******************************************************************************/
   public Integer getWeight()
   {
      return (this.weight);
   }



   /*******************************************************************************
    ** Setter for weight
    *******************************************************************************/
   public void setWeight(Integer weight)
   {
      this.weight = weight;
   }



   /*******************************************************************************
    ** Fluent setter for weight
    *******************************************************************************/
   public QuickSearchIndexField withWeight(Integer weight)
   {
      this.weight = weight;
      return (this);
   }


   /*******************************************************************************
    ** Getter for role
    *******************************************************************************/
   public String getRole()
   {
      return (this.role);
   }



   /*******************************************************************************
    ** Setter for role
    *******************************************************************************/
   public void setRole(String role)
   {
      this.role = role;
   }



   /*******************************************************************************
    ** Fluent setter for role
    *******************************************************************************/
   public QuickSearchIndexField withRole(String role)
   {
      this.role = role;
      return (this);
   }


   /*******************************************************************************
    ** Getter for analyzer
    *******************************************************************************/
   public String getAnalyzer()
   {
      return (this.analyzer);
   }



   /*******************************************************************************
    ** Setter for analyzer
    *******************************************************************************/
   public void setAnalyzer(String analyzer)
   {
      this.analyzer = analyzer;
   }



   /*******************************************************************************
    ** Fluent setter for analyzer
    *******************************************************************************/
   public QuickSearchIndexField withAnalyzer(String analyzer)
   {
      this.analyzer = analyzer;
      return (this);
   }


   /*******************************************************************************
    ** Getter for includeInSnippet
    *******************************************************************************/
   public Boolean getIncludeInSnippet()
   {
      return (this.includeInSnippet);
   }



   /*******************************************************************************
    ** Setter for includeInSnippet
    *******************************************************************************/
   public void setIncludeInSnippet(Boolean includeInSnippet)
   {
      this.includeInSnippet = includeInSnippet;
   }



   /*******************************************************************************
    ** Fluent setter for includeInSnippet
    *******************************************************************************/
   public QuickSearchIndexField withIncludeInSnippet(Boolean includeInSnippet)
   {
      this.includeInSnippet = includeInSnippet;
      return (this);
   }


   /*******************************************************************************
    ** Getter for includeLabel
    *******************************************************************************/
   public Boolean getIncludeLabel()
   {
      return (this.includeLabel);
   }



   /*******************************************************************************
    ** Setter for includeLabel
    *******************************************************************************/
   public void setIncludeLabel(Boolean includeLabel)
   {
      this.includeLabel = includeLabel;
   }



   /*******************************************************************************
    ** Fluent setter for includeLabel
    *******************************************************************************/
   public QuickSearchIndexField withIncludeLabel(Boolean includeLabel)
   {
      this.includeLabel = includeLabel;
      return (this);
   }


   /*******************************************************************************
    ** Getter for sortOrder
    *******************************************************************************/
   public Integer getSortOrder()
   {
      return (this.sortOrder);
   }



   /*******************************************************************************
    ** Setter for sortOrder
    *******************************************************************************/
   public void setSortOrder(Integer sortOrder)
   {
      this.sortOrder = sortOrder;
   }



   /*******************************************************************************
    ** Fluent setter for sortOrder
    *******************************************************************************/
   public QuickSearchIndexField withSortOrder(Integer sortOrder)
   {
      this.sortOrder = sortOrder;
      return (this);
   }


   /*******************************************************************************
    ** Getter for source
    *******************************************************************************/
   public String getSource()
   {
      return (this.source);
   }



   /*******************************************************************************
    ** Setter for source
    *******************************************************************************/
   public void setSource(String source)
   {
      this.source = source;
   }



   /*******************************************************************************
    ** Fluent setter for source
    *******************************************************************************/
   public QuickSearchIndexField withSource(String source)
   {
      this.source = source;
      return (this);
   }


   /*******************************************************************************
    ** Getter for ownerEdited
    *******************************************************************************/
   public Boolean getOwnerEdited()
   {
      return (this.ownerEdited);
   }



   /*******************************************************************************
    ** Setter for ownerEdited
    *******************************************************************************/
   public void setOwnerEdited(Boolean ownerEdited)
   {
      this.ownerEdited = ownerEdited;
   }



   /*******************************************************************************
    ** Fluent setter for ownerEdited
    *******************************************************************************/
   public QuickSearchIndexField withOwnerEdited(Boolean ownerEdited)
   {
      this.ownerEdited = ownerEdited;
      return (this);
   }


   /*******************************************************************************
    ** Getter for createDate
    *******************************************************************************/
   public Instant getCreateDate()
   {
      return (this.createDate);
   }



   /*******************************************************************************
    ** Setter for createDate
    *******************************************************************************/
   public void setCreateDate(Instant createDate)
   {
      this.createDate = createDate;
   }



   /*******************************************************************************
    ** Fluent setter for createDate
    *******************************************************************************/
   public QuickSearchIndexField withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }


   /*******************************************************************************
    ** Getter for modifyDate
    *******************************************************************************/
   public Instant getModifyDate()
   {
      return (this.modifyDate);
   }



   /*******************************************************************************
    ** Setter for modifyDate
    *******************************************************************************/
   public void setModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
   }



   /*******************************************************************************
    ** Fluent setter for modifyDate
    *******************************************************************************/
   public QuickSearchIndexField withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }
}

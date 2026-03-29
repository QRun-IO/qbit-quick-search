/*
 * Copyright 2024 Kingsrook, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qbits.quicksearch.publisher;


import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** Data transfer object representing an event to index or delete a record in
 ** the OpenSearch quick-search index.
 *******************************************************************************/
public class IndexEvent
{

   private String           tableName;
   private String           recordId;
   private IndexEventAction action;
   private QRecord          record;


   /*******************************************************************************
    ** Getter for tableName.
    *******************************************************************************/
   public String getTableName()
   {
      return tableName;
   }


   /*******************************************************************************
    ** Setter for tableName.
    *******************************************************************************/
   public void setTableName(String tableName)
   {
      this.tableName = tableName;
   }


   /*******************************************************************************
    ** Fluent setter for tableName.
    *******************************************************************************/
   public IndexEvent withTableName(String tableName)
   {
      this.tableName = tableName;
      return this;
   }


   /*******************************************************************************
    ** Getter for recordId.
    *******************************************************************************/
   public String getRecordId()
   {
      return recordId;
   }


   /*******************************************************************************
    ** Setter for recordId.
    *******************************************************************************/
   public void setRecordId(String recordId)
   {
      this.recordId = recordId;
   }


   /*******************************************************************************
    ** Fluent setter for recordId.
    *******************************************************************************/
   public IndexEvent withRecordId(String recordId)
   {
      this.recordId = recordId;
      return this;
   }


   /*******************************************************************************
    ** Getter for action.
    *******************************************************************************/
   public IndexEventAction getAction()
   {
      return action;
   }


   /*******************************************************************************
    ** Setter for action.
    *******************************************************************************/
   public void setAction(IndexEventAction action)
   {
      this.action = action;
   }


   /*******************************************************************************
    ** Fluent setter for action.
    *******************************************************************************/
   public IndexEvent withAction(IndexEventAction action)
   {
      this.action = action;
      return this;
   }


   /*******************************************************************************
    ** Getter for record.
    *******************************************************************************/
   public QRecord getRecord()
   {
      return record;
   }


   /*******************************************************************************
    ** Setter for record.
    *******************************************************************************/
   public void setRecord(QRecord record)
   {
      this.record = record;
   }


   /*******************************************************************************
    ** Fluent setter for record.
    *******************************************************************************/
   public IndexEvent withRecord(QRecord record)
   {
      this.record = record;
      return this;
   }

}

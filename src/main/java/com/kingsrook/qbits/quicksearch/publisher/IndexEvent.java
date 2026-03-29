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

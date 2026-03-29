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

package com.kingsrook.qbits.quicksearch.opensearch;


import java.util.ArrayList;
import java.util.List;


/*******************************************************************************
 ** Accumulates success and failure counts from a bulk OpenSearch index operation.
 *******************************************************************************/
public class BulkIndexResult
{

   private Integer      successCount = 0;
   private Integer      failureCount = 0;
   private List<String> errors       = new ArrayList<>();



   /*******************************************************************************
    ** Increment the success counter by one.
    *******************************************************************************/
   public void addSuccess()
   {
      successCount = successCount + 1;
   }



   /*******************************************************************************
    ** Increment the failure counter by one and record the error message.
    *******************************************************************************/
   public void addFailure(String errorMessage)
   {
      failureCount = failureCount + 1;
      errors.add(errorMessage);
   }



   /*******************************************************************************
    ** Returns true when no failures have been recorded.
    *******************************************************************************/
   public Boolean isFullySuccessful()
   {
      return (failureCount == 0);
   }



   /*******************************************************************************
    ** Getter for successCount
    *******************************************************************************/
   public Integer getSuccessCount()
   {
      return (successCount);
   }



   /*******************************************************************************
    ** Fluent setter for successCount
    *******************************************************************************/
   public BulkIndexResult withSuccessCount(Integer successCount)
   {
      this.successCount = successCount;
      return (this);
   }



   /*******************************************************************************
    ** Getter for failureCount
    *******************************************************************************/
   public Integer getFailureCount()
   {
      return (failureCount);
   }



   /*******************************************************************************
    ** Fluent setter for failureCount
    *******************************************************************************/
   public BulkIndexResult withFailureCount(Integer failureCount)
   {
      this.failureCount = failureCount;
      return (this);
   }



   /*******************************************************************************
    ** Getter for errors
    *******************************************************************************/
   public List<String> getErrors()
   {
      return (errors);
   }



   /*******************************************************************************
    ** Fluent setter for errors
    *******************************************************************************/
   public BulkIndexResult withErrors(List<String> errors)
   {
      this.errors = errors;
      return (this);
   }

}

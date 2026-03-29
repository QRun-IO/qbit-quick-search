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

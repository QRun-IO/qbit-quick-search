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


import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Tests for BulkIndexResult.
 *******************************************************************************/
class BulkIndexResultTest
{

   /*******************************************************************************
    ** Test default initial values.
    *******************************************************************************/
   @Test
   void testDefaults()
   {
      BulkIndexResult result = new BulkIndexResult();
      assertThat(result.getSuccessCount()).isEqualTo(0);
      assertThat(result.getFailureCount()).isEqualTo(0);
      assertThat(result.getErrors()).isEmpty();
   }

   /*******************************************************************************
    ** Test addSuccess() increments successCount.
    *******************************************************************************/
   @Test
   void testAddSuccess()
   {
      BulkIndexResult result = new BulkIndexResult();
      result.addSuccess();
      result.addSuccess();
      result.addSuccess();

      assertThat(result.getSuccessCount()).isEqualTo(3);
      assertThat(result.getFailureCount()).isEqualTo(0);
   }

   /*******************************************************************************
    ** Test addFailure() increments failureCount and appends error message.
    *******************************************************************************/
   @Test
   void testAddFailure()
   {
      BulkIndexResult result = new BulkIndexResult();
      result.addFailure("Connection timeout");
      result.addFailure("Document too large");

      assertThat(result.getFailureCount()).isEqualTo(2);
      assertThat(result.getErrors()).containsExactly("Connection timeout", "Document too large");
      assertThat(result.getSuccessCount()).isEqualTo(0);
   }

   /*******************************************************************************
    ** Test isFullySuccessful() returns true when no failures.
    *******************************************************************************/
   @Test
   void testIsFullySuccessfulNoFailures()
   {
      BulkIndexResult result = new BulkIndexResult();
      result.addSuccess();
      result.addSuccess();

      assertThat(result.isFullySuccessful()).isTrue();
   }

   /*******************************************************************************
    ** Test isFullySuccessful() returns false when failures exist.
    *******************************************************************************/
   @Test
   void testIsFullySuccessfulWithFailures()
   {
      BulkIndexResult result = new BulkIndexResult();
      result.addSuccess();
      result.addFailure("Oops");

      assertThat(result.isFullySuccessful()).isFalse();
   }

   /*******************************************************************************
    ** Test isFullySuccessful() on empty (no ops yet) result.
    *******************************************************************************/
   @Test
   void testIsFullySuccessfulEmpty()
   {
      BulkIndexResult result = new BulkIndexResult();
      assertThat(result.isFullySuccessful()).isTrue();
   }

   /*******************************************************************************
    ** Test fluent setters return this.
    *******************************************************************************/
   @Test
   void testFluentSettersReturnThis()
   {
      BulkIndexResult result = new BulkIndexResult();
      assertThat(result.withSuccessCount(5)).isSameAs(result);
      assertThat(result.withFailureCount(2)).isSameAs(result);
      assertThat(result.withErrors(List.of("err1"))).isSameAs(result);
   }

   /*******************************************************************************
    ** Test fluent setters set values correctly.
    *******************************************************************************/
   @Test
   void testFluentSettersValues()
   {
      BulkIndexResult result = new BulkIndexResult()
         .withSuccessCount(10)
         .withFailureCount(3)
         .withErrors(List.of("e1", "e2", "e3"));

      assertThat(result.getSuccessCount()).isEqualTo(10);
      assertThat(result.getFailureCount()).isEqualTo(3);
      assertThat(result.getErrors()).containsExactly("e1", "e2", "e3");
   }

}

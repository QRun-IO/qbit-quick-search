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

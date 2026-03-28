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

package com.kingsrook.qbits.quicksearch.model;


import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Tests for QuickSearchIndexRun entity.
 *******************************************************************************/
class QuickSearchIndexRunTest
{

   /*******************************************************************************
    ** Test that TABLE_NAME constant has expected value.
    *******************************************************************************/
   @Test
   void testTableNameConstant()
   {
      assertThat(QuickSearchIndexRun.TABLE_NAME).isEqualTo("quickSearchIndexRun");
   }



   /*******************************************************************************
    ** Test that fluent setters return this (fluent chaining).
    *******************************************************************************/
   @Test
   void testFluentSettersReturnThis()
   {
      QuickSearchIndexRun entity = new QuickSearchIndexRun();
      Instant now = Instant.now();

      assertThat(entity.withId(1)).isSameAs(entity);
      assertThat(entity.withQuickSearchIndexId(10)).isSameAs(entity);
      assertThat(entity.withRunType("basepull")).isSameAs(entity);
      assertThat(entity.withStatus("running")).isSameAs(entity);
      assertThat(entity.withStartTime(now)).isSameAs(entity);
      assertThat(entity.withEndTime(now)).isSameAs(entity);
      assertThat(entity.withRecordsProcessed(500)).isSameAs(entity);
      assertThat(entity.withRecordsIndexed(490)).isSameAs(entity);
      assertThat(entity.withErrorCount(10)).isSameAs(entity);
      assertThat(entity.withErrorMessage("something failed")).isSameAs(entity);
      assertThat(entity.withCreateDate(now)).isSameAs(entity);
      assertThat(entity.withModifyDate(now)).isSameAs(entity);
   }



   /*******************************************************************************
    ** Test that getters return values set via fluent setters.
    *******************************************************************************/
   @Test
   void testGettersReturnSetValues()
   {
      Instant startTime = Instant.parse("2024-03-01T09:00:00Z");
      Instant endTime = Instant.parse("2024-03-01T09:05:00Z");
      Instant createDate = Instant.parse("2024-03-01T09:00:00Z");
      Instant modifyDate = Instant.parse("2024-03-01T09:05:30Z");

      QuickSearchIndexRun entity = new QuickSearchIndexRun()
         .withId(7)
         .withQuickSearchIndexId(3)
         .withRunType("fullReindex")
         .withStatus("completed")
         .withStartTime(startTime)
         .withEndTime(endTime)
         .withRecordsProcessed(1000)
         .withRecordsIndexed(998)
         .withErrorCount(2)
         .withErrorMessage("2 records failed validation")
         .withCreateDate(createDate)
         .withModifyDate(modifyDate);

      assertThat(entity.getId()).isEqualTo(7);
      assertThat(entity.getQuickSearchIndexId()).isEqualTo(3);
      assertThat(entity.getRunType()).isEqualTo("fullReindex");
      assertThat(entity.getStatus()).isEqualTo("completed");
      assertThat(entity.getStartTime()).isEqualTo(startTime);
      assertThat(entity.getEndTime()).isEqualTo(endTime);
      assertThat(entity.getRecordsProcessed()).isEqualTo(1000);
      assertThat(entity.getRecordsIndexed()).isEqualTo(998);
      assertThat(entity.getErrorCount()).isEqualTo(2);
      assertThat(entity.getErrorMessage()).isEqualTo("2 records failed validation");
      assertThat(entity.getCreateDate()).isEqualTo(createDate);
      assertThat(entity.getModifyDate()).isEqualTo(modifyDate);
   }



   /*******************************************************************************
    ** Test that all fields default to null on a new instance.
    *******************************************************************************/
   @Test
   void testDefaultsAreNull()
   {
      QuickSearchIndexRun entity = new QuickSearchIndexRun();

      assertThat(entity.getId()).isNull();
      assertThat(entity.getQuickSearchIndexId()).isNull();
      assertThat(entity.getRunType()).isNull();
      assertThat(entity.getStatus()).isNull();
      assertThat(entity.getStartTime()).isNull();
      assertThat(entity.getEndTime()).isNull();
      assertThat(entity.getRecordsProcessed()).isNull();
      assertThat(entity.getRecordsIndexed()).isNull();
      assertThat(entity.getErrorCount()).isNull();
      assertThat(entity.getErrorMessage()).isNull();
      assertThat(entity.getCreateDate()).isNull();
      assertThat(entity.getModifyDate()).isNull();
   }

}

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
 ** Tests for QuickSearchIndex entity.
 *******************************************************************************/
class QuickSearchIndexTest
{

   /*******************************************************************************
    ** Test that TABLE_NAME constant has expected value.
    *******************************************************************************/
   @Test
   void testTableNameConstant()
   {
      assertThat(QuickSearchIndex.TABLE_NAME).isEqualTo("quickSearchIndex");
   }



   /*******************************************************************************
    ** Test that fluent setters return this (fluent chaining).
    *******************************************************************************/
   @Test
   void testFluentSettersReturnThis()
   {
      QuickSearchIndex entity = new QuickSearchIndex();
      Instant now = Instant.now();

      assertThat(entity.withId(1)).isSameAs(entity);
      assertThat(entity.withTableName("myTable")).isSameAs(entity);
      assertThat(entity.withEnabled(true)).isSameAs(entity);
      assertThat(entity.withBasepullIntervalMinutes(60)).isSameAs(entity);
      assertThat(entity.withBasepullTimestampField("modifyDate")).isSameAs(entity);
      assertThat(entity.withSearchableFieldsJson("[]")).isSameAs(entity);
      assertThat(entity.withLastBasepullTime(now)).isSameAs(entity);
      assertThat(entity.withLastFullReindexTime(now)).isSameAs(entity);
      assertThat(entity.withRecordCount(100)).isSameAs(entity);
      assertThat(entity.withStatus("active")).isSameAs(entity);
      assertThat(entity.withCreateDate(now)).isSameAs(entity);
      assertThat(entity.withModifyDate(now)).isSameAs(entity);
   }



   /*******************************************************************************
    ** Test that getters return values set via fluent setters.
    *******************************************************************************/
   @Test
   void testGettersReturnSetValues()
   {
      Instant basepullTime = Instant.parse("2024-01-15T10:00:00Z");
      Instant reindexTime = Instant.parse("2024-01-16T12:00:00Z");
      Instant createDate = Instant.parse("2024-01-01T00:00:00Z");
      Instant modifyDate = Instant.parse("2024-01-17T08:30:00Z");

      QuickSearchIndex entity = new QuickSearchIndex()
         .withId(42)
         .withTableName("orderTable")
         .withEnabled(true)
         .withBasepullIntervalMinutes(30)
         .withBasepullTimestampField("lastModified")
         .withSearchableFieldsJson("[\"name\",\"description\"]")
         .withLastBasepullTime(basepullTime)
         .withLastFullReindexTime(reindexTime)
         .withRecordCount(9999)
         .withStatus("active")
         .withCreateDate(createDate)
         .withModifyDate(modifyDate);

      assertThat(entity.getId()).isEqualTo(42);
      assertThat(entity.getTableName()).isEqualTo("orderTable");
      assertThat(entity.getEnabled()).isTrue();
      assertThat(entity.getBasepullIntervalMinutes()).isEqualTo(30);
      assertThat(entity.getBasepullTimestampField()).isEqualTo("lastModified");
      assertThat(entity.getSearchableFieldsJson()).isEqualTo("[\"name\",\"description\"]");
      assertThat(entity.getLastBasepullTime()).isEqualTo(basepullTime);
      assertThat(entity.getLastFullReindexTime()).isEqualTo(reindexTime);
      assertThat(entity.getRecordCount()).isEqualTo(9999);
      assertThat(entity.getStatus()).isEqualTo("active");
      assertThat(entity.getCreateDate()).isEqualTo(createDate);
      assertThat(entity.getModifyDate()).isEqualTo(modifyDate);
   }



   /*******************************************************************************
    ** Test that all fields default to null on a new instance.
    *******************************************************************************/
   @Test
   void testDefaultsAreNull()
   {
      QuickSearchIndex entity = new QuickSearchIndex();

      assertThat(entity.getId()).isNull();
      assertThat(entity.getTableName()).isNull();
      assertThat(entity.getEnabled()).isNull();
      assertThat(entity.getBasepullIntervalMinutes()).isNull();
      assertThat(entity.getBasepullTimestampField()).isNull();
      assertThat(entity.getSearchableFieldsJson()).isNull();
      assertThat(entity.getLastBasepullTime()).isNull();
      assertThat(entity.getLastFullReindexTime()).isNull();
      assertThat(entity.getRecordCount()).isNull();
      assertThat(entity.getStatus()).isNull();
      assertThat(entity.getCreateDate()).isNull();
      assertThat(entity.getModifyDate()).isNull();
   }

}

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
package com.kingsrook.qbits.quicksearch.model;


import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Tests for QuickSearchIndex entity.
 *******************************************************************************/
class QuickSearchIndexTest
{

   /***************************************************************************
    ** Test TABLE_NAME constant.
    ***************************************************************************/
   @Test
   void testTableNameConstant()
   {
      assertThat(QuickSearchIndex.TABLE_NAME).isEqualTo("quickSearchIndex");
   }



   /***************************************************************************
    ** Test all getters and fluent setters.
    ***************************************************************************/
   @Test
   void testGettersAndSetters()
   {
      Instant now = Instant.now();

      QuickSearchIndex index = new QuickSearchIndex()
         .withId(1)
         .withTableName("orders")
         .withIsEnabled(true)
         .withBasepullIntervalMinutes(5)
         .withBasepullTimestampField("modifyDate")
         .withSearchableFieldsJson("[\"name\",\"description\"]")
         .withLastFullIndexTime(now)
         .withLastBasepullTime(now)
         .withIndexedRecordCount(100)
         .withCreateDate(now)
         .withModifyDate(now);

      assertThat(index.getId()).isEqualTo(1);
      assertThat(index.getTableName()).isEqualTo("orders");
      assertThat(index.getIsEnabled()).isTrue();
      assertThat(index.getBasepullIntervalMinutes()).isEqualTo(5);
      assertThat(index.getBasepullTimestampField()).isEqualTo("modifyDate");
      assertThat(index.getSearchableFieldsJson()).isEqualTo("[\"name\",\"description\"]");
      assertThat(index.getLastFullIndexTime()).isEqualTo(now);
      assertThat(index.getLastBasepullTime()).isEqualTo(now);
      assertThat(index.getIndexedRecordCount()).isEqualTo(100);
      assertThat(index.getCreateDate()).isEqualTo(now);
      assertThat(index.getModifyDate()).isEqualTo(now);
   }



   /***************************************************************************
    ** Test standard setters.
    ***************************************************************************/
   @Test
   void testStandardSetters()
   {
      Instant now = Instant.now();

      QuickSearchIndex index = new QuickSearchIndex();
      index.setId(2);
      index.setTableName("customers");
      index.setIsEnabled(false);
      index.setBasepullIntervalMinutes(10);
      index.setBasepullTimestampField("updatedAt");
      index.setSearchableFieldsJson("[]");
      index.setLastFullIndexTime(now);
      index.setLastBasepullTime(now);
      index.setIndexedRecordCount(50);
      index.setCreateDate(now);
      index.setModifyDate(now);

      assertThat(index.getId()).isEqualTo(2);
      assertThat(index.getTableName()).isEqualTo("customers");
      assertThat(index.getIsEnabled()).isFalse();
      assertThat(index.getBasepullIntervalMinutes()).isEqualTo(10);
      assertThat(index.getBasepullTimestampField()).isEqualTo("updatedAt");
      assertThat(index.getSearchableFieldsJson()).isEqualTo("[]");
      assertThat(index.getLastFullIndexTime()).isEqualTo(now);
      assertThat(index.getLastBasepullTime()).isEqualTo(now);
      assertThat(index.getIndexedRecordCount()).isEqualTo(50);
      assertThat(index.getCreateDate()).isEqualTo(now);
      assertThat(index.getModifyDate()).isEqualTo(now);
   }



   /***************************************************************************
    ** Test fluent setters return this.
    ***************************************************************************/
   @Test
   void testFluentSetters_returnThis()
   {
      QuickSearchIndex index = new QuickSearchIndex();
      Instant now = Instant.now();

      assertThat(index.withId(1)).isSameAs(index);
      assertThat(index.withTableName("t")).isSameAs(index);
      assertThat(index.withIsEnabled(true)).isSameAs(index);
      assertThat(index.withBasepullIntervalMinutes(5)).isSameAs(index);
      assertThat(index.withBasepullTimestampField("f")).isSameAs(index);
      assertThat(index.withSearchableFieldsJson("[]")).isSameAs(index);
      assertThat(index.withLastFullIndexTime(now)).isSameAs(index);
      assertThat(index.withLastBasepullTime(now)).isSameAs(index);
      assertThat(index.withIndexedRecordCount(0)).isSameAs(index);
      assertThat(index.withCreateDate(now)).isSameAs(index);
      assertThat(index.withModifyDate(now)).isSameAs(index);
   }

}

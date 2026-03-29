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

package com.kingsrook.qbits.quicksearch.publisher;


import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Tests for IndexEvent and IndexEventAction.
 *******************************************************************************/
class IndexEventTest
{

   /*******************************************************************************
    ** Test that IndexEventAction enum has INDEX and DELETE values.
    *******************************************************************************/
   @Test
   void testIndexEventActionEnumValues()
   {
      assertThat(IndexEventAction.INDEX).isNotNull();
      assertThat(IndexEventAction.DELETE).isNotNull();
      assertThat(IndexEventAction.values()).hasSize(2);
   }

   /*******************************************************************************
    ** Test that IndexEvent fluent setters return this and getters return values.
    *******************************************************************************/
   @Test
   void testIndexEventFluentSetters()
   {
      QRecord record = new QRecord();
      IndexEvent event = new IndexEvent()
         .withTableName("customers")
         .withRecordId("99")
         .withAction(IndexEventAction.INDEX)
         .withRecord(record);

      assertThat(event.getTableName()).isEqualTo("customers");
      assertThat(event.getRecordId()).isEqualTo("99");
      assertThat(event.getAction()).isEqualTo(IndexEventAction.INDEX);
      assertThat(event.getRecord()).isSameAs(record);
   }

   /*******************************************************************************
    ** Test that IndexEvent withX methods return this.
    *******************************************************************************/
   @Test
   void testIndexEventWithReturnsThis()
   {
      IndexEvent event = new IndexEvent();
      assertThat(event.withTableName("t")).isSameAs(event);
      assertThat(event.withRecordId("1")).isSameAs(event);
      assertThat(event.withAction(IndexEventAction.DELETE)).isSameAs(event);
      assertThat(event.withRecord(null)).isSameAs(event);
   }

   /*******************************************************************************
    ** Test that IndexEvent record field can be null.
    *******************************************************************************/
   @Test
   void testIndexEventRecordNullable()
   {
      IndexEvent event = new IndexEvent()
         .withTableName("orders")
         .withRecordId("1")
         .withAction(IndexEventAction.DELETE);

      assertThat(event.getRecord()).isNull();
   }

}

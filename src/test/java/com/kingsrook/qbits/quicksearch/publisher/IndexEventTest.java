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

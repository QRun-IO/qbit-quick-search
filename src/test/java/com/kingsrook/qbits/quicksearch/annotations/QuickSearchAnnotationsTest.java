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
package com.kingsrook.qbits.quicksearch.annotations;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Tests for Quick Search annotations.
 *******************************************************************************/
class QuickSearchAnnotationsTest
{

   /***************************************************************************
    ** Test QuickSearchable annotation has RUNTIME retention.
    ***************************************************************************/
   @Test
   void testQuickSearchable_hasRuntimeRetention()
   {
      Retention retention = QuickSearchable.class.getAnnotation(Retention.class);
      assertThat(retention).isNotNull();
      assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
   }



   /***************************************************************************
    ** Test QuickSearchable annotation targets TYPE.
    ***************************************************************************/
   @Test
   void testQuickSearchable_targetsType()
   {
      Target target = QuickSearchable.class.getAnnotation(Target.class);
      assertThat(target).isNotNull();
      assertThat(target.value()).contains(ElementType.TYPE);
   }



   /***************************************************************************
    ** Test QuickSearchable annotation attributes.
    ***************************************************************************/
   @Test
   void testQuickSearchable_annotatedClass()
   {
      QuickSearchable annotation = TestEntity.class.getAnnotation(QuickSearchable.class);
      assertThat(annotation).isNotNull();
      assertThat(annotation.tableName()).isEqualTo("testEntity");
      assertThat(annotation.basepullIntervalMinutes()).isEqualTo(10);
      assertThat(annotation.basepullTimestampField()).isEqualTo("modifyDate");
   }



   /***************************************************************************
    ** Test QuickSearchField annotation has RUNTIME retention.
    ***************************************************************************/
   @Test
   void testQuickSearchField_hasRuntimeRetention()
   {
      Retention retention = QuickSearchField.class.getAnnotation(Retention.class);
      assertThat(retention).isNotNull();
      assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
   }



   /***************************************************************************
    ** Test QuickSearchField annotation targets FIELD.
    ***************************************************************************/
   @Test
   void testQuickSearchField_targetsField()
   {
      Target target = QuickSearchField.class.getAnnotation(Target.class);
      assertThat(target).isNotNull();
      assertThat(target.value()).contains(ElementType.FIELD);
   }



   /***************************************************************************
    ** Test QuickSearchField annotation default weight.
    ***************************************************************************/
   @Test
   void testQuickSearchField_annotatedField() throws NoSuchFieldException
   {
      QuickSearchField annotation = TestEntity.class.getDeclaredField("name").getAnnotation(QuickSearchField.class);
      assertThat(annotation).isNotNull();
      assertThat(annotation.weight()).isEqualTo(2);
   }



   /***************************************************************************
    ** Test QuickSearchField annotation default weight value.
    ***************************************************************************/
   @Test
   void testQuickSearchField_defaultWeight() throws NoSuchFieldException
   {
      QuickSearchField annotation = TestEntity.class.getDeclaredField("description").getAnnotation(QuickSearchField.class);
      assertThat(annotation).isNotNull();
      assertThat(annotation.weight()).isEqualTo(1);
   }



   /***************************************************************************
    ** Sample annotated entity class for testing.
    ***************************************************************************/
   @QuickSearchable(tableName = "testEntity", basepullIntervalMinutes = 10, basepullTimestampField = "modifyDate")
   static class TestEntity
   {
      @QuickSearchField(weight = 2)
      private String name;

      @QuickSearchField
      private String description;
   }

}

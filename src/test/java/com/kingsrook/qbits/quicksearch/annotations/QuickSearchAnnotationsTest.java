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

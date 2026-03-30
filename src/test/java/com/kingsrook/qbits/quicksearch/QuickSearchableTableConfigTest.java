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
package com.kingsrook.qbits.quicksearch;


import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Tests for QuickSearchableTableConfig.
 *******************************************************************************/
class QuickSearchableTableConfigTest
{

   /***************************************************************************
    ** Test all fluent setters and getters including new map fields.
    ***************************************************************************/
   @Test
   void testGettersAndSetters()
   {
      List<String> fields = List.of("name", "description");
      Map<String, Integer> weights = Map.of("name", 10, "description", 5);
      Map<String, Boolean> includeLabels = Map.of("name", true, "description", false);

      QuickSearchableTableConfig config = new QuickSearchableTableConfig()
         .withTableName("orders")
         .withSearchableFields(fields)
         .withFieldWeights(weights)
         .withFieldIncludeLabels(includeLabels)
         .withBasepullIntervalMinutes(10)
         .withBasepullTimestampField("updatedAt")
         .withEnabledByDefault(false);

      assertThat(config.getTableName()).isEqualTo("orders");
      assertThat(config.getSearchableFields()).isEqualTo(fields);
      assertThat(config.getFieldWeights()).isEqualTo(weights);
      assertThat(config.getFieldIncludeLabels()).isEqualTo(includeLabels);
      assertThat(config.getBasepullIntervalMinutes()).isEqualTo(10);
      assertThat(config.getBasepullTimestampField()).isEqualTo("updatedAt");
      assertThat(config.getEnabledByDefault()).isFalse();
   }



   /***************************************************************************
    ** Test standard setters.
    ***************************************************************************/
   @Test
   void testStandardSetters()
   {
      List<String> fields = List.of("field1", "field2");
      Map<String, Integer> weights = Map.of("field1", 3);
      Map<String, Boolean> includeLabels = Map.of("field1", true);

      QuickSearchableTableConfig config = new QuickSearchableTableConfig();
      config.setTableName("customers");
      config.setSearchableFields(fields);
      config.setFieldWeights(weights);
      config.setFieldIncludeLabels(includeLabels);
      config.setBasepullIntervalMinutes(15);
      config.setBasepullTimestampField("modifyDate");
      config.setEnabledByDefault(true);

      assertThat(config.getTableName()).isEqualTo("customers");
      assertThat(config.getSearchableFields()).isEqualTo(fields);
      assertThat(config.getFieldWeights()).isEqualTo(weights);
      assertThat(config.getFieldIncludeLabels()).isEqualTo(includeLabels);
      assertThat(config.getBasepullIntervalMinutes()).isEqualTo(15);
      assertThat(config.getBasepullTimestampField()).isEqualTo("modifyDate");
      assertThat(config.getEnabledByDefault()).isTrue();
   }



   /***************************************************************************
    ** Test fluent setters return this.
    ***************************************************************************/
   @Test
   void testFluentSetters_returnThis()
   {
      QuickSearchableTableConfig config = new QuickSearchableTableConfig();

      assertThat(config.withTableName("test")).isSameAs(config);
      assertThat(config.withSearchableFields(List.of("a"))).isSameAs(config);
      assertThat(config.withFieldWeights(Map.of("a", 1))).isSameAs(config);
      assertThat(config.withFieldIncludeLabels(Map.of("a", true))).isSameAs(config);
      assertThat(config.withBasepullIntervalMinutes(5)).isSameAs(config);
      assertThat(config.withBasepullTimestampField("ts")).isSameAs(config);
      assertThat(config.withEnabledByDefault(true)).isSameAs(config);
   }



   /***************************************************************************
    ** Test null defaults for new fields.
    ***************************************************************************/
   @Test
   void testNewFields_defaultToNull()
   {
      QuickSearchableTableConfig config = new QuickSearchableTableConfig();

      assertThat(config.getFieldWeights()).isNull();
      assertThat(config.getFieldIncludeLabels()).isNull();
   }



   /***************************************************************************
    ** Test recordLabelFormat defaults to null.
    ***************************************************************************/
   @Test
   void testRecordLabelFormat_defaultsToNull()
   {
      QuickSearchableTableConfig config = new QuickSearchableTableConfig();

      assertThat(config.getRecordLabelFormat()).isNull();
      assertThat(config.getRecordLabelFields()).isNull();
   }



   /***************************************************************************
    ** Test recordLabelFormat fluent setter and getter round-trip.
    ***************************************************************************/
   @Test
   void testRecordLabelFormat_fluentSetterAndGetter()
   {
      QuickSearchableTableConfig config = new QuickSearchableTableConfig()
         .withRecordLabelFormat("%s - %s");

      assertThat(config.getRecordLabelFormat()).isEqualTo("%s - %s");
   }



   /***************************************************************************
    ** Test recordLabelFields fluent setter and getter round-trip.
    ***************************************************************************/
   @Test
   void testRecordLabelFields_fluentSetterAndGetter()
   {
      List<String> labelFields = List.of("firstName", "lastName");

      QuickSearchableTableConfig config = new QuickSearchableTableConfig()
         .withRecordLabelFields(labelFields);

      assertThat(config.getRecordLabelFields()).isEqualTo(labelFields);
   }



   /***************************************************************************
    ** Test recordLabelFormat and recordLabelFields fluent setters return this.
    ***************************************************************************/
   @Test
   void testRecordLabelSetters_returnThis()
   {
      QuickSearchableTableConfig config = new QuickSearchableTableConfig();

      assertThat(config.withRecordLabelFormat("%s")).isSameAs(config);
      assertThat(config.withRecordLabelFields(List.of("a"))).isSameAs(config);
   }

}

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
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Tests for SearchableTableConfig.
 *******************************************************************************/
class SearchableTableConfigTest
{

   /***************************************************************************
    ** Test that the constructor sets tableName and fields, and optional
    ** fields have correct defaults.
    ***************************************************************************/
   @Test
   void testConstructor_setsRequiredFieldsAndDefaults()
   {
      List<SearchableFieldConfig> fields = List.of(
         new SearchableFieldConfig("firstName"),
         new SearchableFieldConfig("lastName"));

      SearchableTableConfig config = new SearchableTableConfig("person", fields);

      assertThat(config.getTableName()).isEqualTo("person");
      assertThat(config.getFields()).hasSize(2);
      assertThat(config.getBasepullIntervalMinutes()).isNull();
      assertThat(config.getBasepullTimestampField()).isEqualTo("modifyDate");
      assertThat(config.getEnabledByDefault()).isTrue();
      assertThat(config.getRecordLabelFormat()).isNull();
      assertThat(config.getRecordLabelFields()).isNull();
   }



   /***************************************************************************
    ** Test all fluent setters and corresponding getters.
    ***************************************************************************/
   @Test
   void testFluentSetters_gettersReturnCorrectValues()
   {
      List<SearchableFieldConfig> fields = List.of(new SearchableFieldConfig("name"));
      List<SearchableFieldConfig> newFields = List.of(new SearchableFieldConfig("email"));

      SearchableTableConfig config = new SearchableTableConfig("orders", fields)
         .withTableName("customers")
         .withFields(newFields)
         .withBasepullIntervalMinutes(30)
         .withBasepullTimestampField("updatedAt")
         .withEnabledByDefault(false);

      assertThat(config.getTableName()).isEqualTo("customers");
      assertThat(config.getFields()).isSameAs(newFields);
      assertThat(config.getBasepullIntervalMinutes()).isEqualTo(30);
      assertThat(config.getBasepullTimestampField()).isEqualTo("updatedAt");
      assertThat(config.getEnabledByDefault()).isFalse();
   }



   /***************************************************************************
    ** Test that fluent setters return this for chaining.
    ***************************************************************************/
   @Test
   void testFluentSetters_returnThis()
   {
      List<SearchableFieldConfig> fields = List.of(new SearchableFieldConfig("a"));

      SearchableTableConfig config = new SearchableTableConfig("t", fields);

      assertThat(config.withTableName("t2")).isSameAs(config);
      assertThat(config.withFields(fields)).isSameAs(config);
      assertThat(config.withBasepullIntervalMinutes(10)).isSameAs(config);
      assertThat(config.withBasepullTimestampField("ts")).isSameAs(config);
      assertThat(config.withEnabledByDefault(false)).isSameAs(config);
   }



   /***************************************************************************
    ** Test withRecordLabelFormat convenience method sets both format and fields.
    ***************************************************************************/
   @Test
   void testWithRecordLabelFormat_setsBothFormatAndFields()
   {
      List<SearchableFieldConfig> fields = List.of(new SearchableFieldConfig("firstName"));

      SearchableTableConfig config = new SearchableTableConfig("person", fields)
         .withRecordLabelFormat("%s - %s", "firstName", "lastName");

      assertThat(config.getRecordLabelFormat()).isEqualTo("%s - %s");
      assertThat(config.getRecordLabelFields()).containsExactly("firstName", "lastName");
   }



   /***************************************************************************
    ** Test withRecordLabelFormat returns this for chaining.
    ***************************************************************************/
   @Test
   void testWithRecordLabelFormat_returnsThis()
   {
      List<SearchableFieldConfig> fields = List.of(new SearchableFieldConfig("a"));

      SearchableTableConfig config = new SearchableTableConfig("t", fields);
      SearchableTableConfig result = config.withRecordLabelFormat("%s", "a");

      assertThat(result).isSameAs(config);
   }



   /***************************************************************************
    ** Test standard setters update values correctly.
    ***************************************************************************/
   @Test
   void testStandardSetters_valuesRoundTrip()
   {
      List<SearchableFieldConfig> fields = List.of(new SearchableFieldConfig("a"));
      List<SearchableFieldConfig> newFields = List.of(new SearchableFieldConfig("b"));
      List<String> labelFields = List.of("x", "y");

      SearchableTableConfig config = new SearchableTableConfig("t", fields);
      config.setTableName("t2");
      config.setFields(newFields);
      config.setBasepullIntervalMinutes(20);
      config.setBasepullTimestampField("createDate");
      config.setEnabledByDefault(false);
      config.setRecordLabelFormat("%s/%s");
      config.setRecordLabelFields(labelFields);

      assertThat(config.getTableName()).isEqualTo("t2");
      assertThat(config.getFields()).isSameAs(newFields);
      assertThat(config.getBasepullIntervalMinutes()).isEqualTo(20);
      assertThat(config.getBasepullTimestampField()).isEqualTo("createDate");
      assertThat(config.getEnabledByDefault()).isFalse();
      assertThat(config.getRecordLabelFormat()).isEqualTo("%s/%s");
      assertThat(config.getRecordLabelFields()).isSameAs(labelFields);
   }

}

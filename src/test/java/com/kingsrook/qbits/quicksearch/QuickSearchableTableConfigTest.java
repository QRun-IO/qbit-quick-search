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
 ** Tests for QuickSearchableTableConfig.
 *******************************************************************************/
class QuickSearchableTableConfigTest
{

   /***************************************************************************
    ** Test all getters and fluent setters.
    ***************************************************************************/
   @Test
   void testGettersAndSetters()
   {
      List<String> fields = List.of("name", "description");

      QuickSearchableTableConfig config = new QuickSearchableTableConfig()
         .withTableName("orders")
         .withSearchableFields(fields)
         .withBasepullIntervalMinutes(10)
         .withBasepullTimestampField("updatedAt")
         .withEnabledByDefault(false);

      assertThat(config.getTableName()).isEqualTo("orders");
      assertThat(config.getSearchableFields()).isEqualTo(fields);
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

      QuickSearchableTableConfig config = new QuickSearchableTableConfig();
      config.setTableName("customers");
      config.setSearchableFields(fields);
      config.setBasepullIntervalMinutes(15);
      config.setBasepullTimestampField("modifyDate");
      config.setEnabledByDefault(true);

      assertThat(config.getTableName()).isEqualTo("customers");
      assertThat(config.getSearchableFields()).isEqualTo(fields);
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

      QuickSearchableTableConfig result = config
         .withTableName("test")
         .withSearchableFields(List.of("a"))
         .withBasepullIntervalMinutes(5)
         .withBasepullTimestampField("ts")
         .withEnabledByDefault(true);

      assertThat(result).isSameAs(config);
   }

}

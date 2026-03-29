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

}

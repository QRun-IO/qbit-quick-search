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


import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Tests for SearchableFieldConfig.
 *******************************************************************************/
class SearchableFieldConfigTest
{

   /***************************************************************************
    ** Test that the constructor sets fieldName and applies default values
    ** for weight (1) and includeLabel (false).
    ***************************************************************************/
   @Test
   void testConstructor_setsFieldNameAndDefaults()
   {
      SearchableFieldConfig config = new SearchableFieldConfig("email");

      assertThat(config.getFieldName()).isEqualTo("email");
      assertThat(config.getWeight()).isEqualTo(1);
      assertThat(config.getIncludeLabel()).isFalse();
   }



   /***************************************************************************
    ** Test that fluent setter for weight returns this and updates value.
    ***************************************************************************/
   @Test
   void testWithWeight_setsValueAndReturnsThis()
   {
      SearchableFieldConfig config = new SearchableFieldConfig("name");
      SearchableFieldConfig result = config.withWeight(5);

      assertThat(result).isSameAs(config);
      assertThat(config.getWeight()).isEqualTo(5);
   }



   /***************************************************************************
    ** Test that fluent setter for includeLabel returns this and updates value.
    ***************************************************************************/
   @Test
   void testWithIncludeLabel_setsValueAndReturnsThis()
   {
      SearchableFieldConfig config = new SearchableFieldConfig("name");
      SearchableFieldConfig result = config.withIncludeLabel(true);

      assertThat(result).isSameAs(config);
      assertThat(config.getIncludeLabel()).isTrue();
   }



   /***************************************************************************
    ** Test that fluent setter for fieldName returns this and updates value.
    ***************************************************************************/
   @Test
   void testWithFieldName_setsValueAndReturnsThis()
   {
      SearchableFieldConfig config = new SearchableFieldConfig("original");
      SearchableFieldConfig result = config.withFieldName("updated");

      assertThat(result).isSameAs(config);
      assertThat(config.getFieldName()).isEqualTo("updated");
   }



   /***************************************************************************
    ** Test that standard setters update values correctly.
    ***************************************************************************/
   @Test
   void testStandardSetters_valuesRoundTrip()
   {
      SearchableFieldConfig config = new SearchableFieldConfig("initial");
      config.setFieldName("changed");
      config.setWeight(10);
      config.setIncludeLabel(true);

      assertThat(config.getFieldName()).isEqualTo("changed");
      assertThat(config.getWeight()).isEqualTo(10);
      assertThat(config.getIncludeLabel()).isTrue();
   }



   /***************************************************************************
    ** Test chaining all fluent setters in one expression.
    ***************************************************************************/
   @Test
   void testFluentChaining_allSetters()
   {
      SearchableFieldConfig config = new SearchableFieldConfig("email")
         .withWeight(3)
         .withIncludeLabel(true);

      assertThat(config.getFieldName()).isEqualTo("email");
      assertThat(config.getWeight()).isEqualTo(3);
      assertThat(config.getIncludeLabel()).isTrue();
   }

}

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
 ** Tests for QuickSearchQBitContext.
 *******************************************************************************/
class QuickSearchQBitContextTest
{

   /***************************************************************************
    ** Test setConfig and getConfig.
    ***************************************************************************/
   @Test
   void testSetAndGetConfig()
   {
      QuickSearchQBitConfig config = new QuickSearchQBitConfig()
         .withBackendName("testBackend");

      QuickSearchQBitContext.setConfig(config);

      assertThat(QuickSearchQBitContext.getConfig()).isSameAs(config);
   }



   /***************************************************************************
    ** Test setDiscoveredTables and getDiscoveredTables.
    ***************************************************************************/
   @Test
   void testSetAndGetDiscoveredTables()
   {
      QuickSearchableTableConfig tableConfig = new QuickSearchableTableConfig()
         .withTableName("orders");
      List<QuickSearchableTableConfig> tables = List.of(tableConfig);

      QuickSearchQBitContext.setDiscoveredTables(tables);

      assertThat(QuickSearchQBitContext.getDiscoveredTables()).hasSize(1);
      assertThat(QuickSearchQBitContext.getDiscoveredTables().get(0).getTableName()).isEqualTo("orders");
   }

}

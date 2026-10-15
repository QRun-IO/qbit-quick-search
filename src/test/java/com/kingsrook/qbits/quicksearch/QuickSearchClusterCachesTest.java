/*
 * Copyright 2026 Kingsrook, LLC
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


import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import com.kingsrook.qqq.backend.core.cluster.ClusterBus;
import com.kingsrook.qqq.backend.core.cluster.ClusterBusInterface;
import com.kingsrook.qqq.backend.core.cluster.ClusterBusMetaData;
import com.kingsrook.qqq.backend.core.cluster.ClusterCacheAudit;
import com.kingsrook.qqq.backend.core.cluster.ClusterEvent;
import com.kingsrook.qqq.backend.core.cluster.QNodeRoles;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndexField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The Quick Search caches are correct on several nodes: every static cache is
 ** registered with ClusterCaches, and a config change on one node clears the
 ** config cache on the others.
 *******************************************************************************/
class QuickSearchClusterCachesTest extends BaseQuickSearchTest
{
   /** the "wire": events every node's transport sees */
   static final List<Consumer<ClusterEvent>> WIRE = new CopyOnWriteArrayList<>();



   /*******************************************************************************
    ** A transport over the static WIRE, so a "remote" publish reaches us.
    *******************************************************************************/
   public static class WireBus implements ClusterBusInterface
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void publish(ClusterEvent event)
      {
         WIRE.forEach(listener -> listener.accept(event));
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public AutoCloseable subscribe(Consumer<ClusterEvent> listener)
      {
         WIRE.add(listener);
         return (() -> WIRE.remove(listener));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void afterEach()
   {
      ClusterBus.close(QContext.getQInstance());
      WIRE.clear();
      QNodeRoles.set(null);
   }



   /*******************************************************************************
    ** Connect the current instance to the wire as node-a.
    *******************************************************************************/
   private static void connectAsNodeA()
   {
      QInstance instance = QContext.getQInstance();
      instance.withSupplementalMetaData(new ClusterBusMetaData().withBus(new QCodeReference(WireBus.class)));
      QNodeRoles.set(new QNodeRoles.Settings(EnumSet.allOf(QNodeRoles.Role.class), true, true, true, null, "node-a"));
      ClusterBus.connect(instance);
   }



   /*******************************************************************************
    ** A runtime with one code-declared table.
    *******************************************************************************/
   private static QuickSearchRuntime newRuntime()
   {
      ///////////////////////////////////////////////////////////////////////
      // without the field table the config comes from code, so the enabled //
      // flag is read from the index row and cached                         //
      ///////////////////////////////////////////////////////////////////////
      QContext.getQInstance().getTables().remove(QuickSearchIndexField.TABLE_NAME);
      QContext.getQInstance().getTable(QuickSearchIndex.TABLE_NAME).getAssociations().clear();

      QuickSearchRuntime runtime = new QuickSearchRuntime(new QuickSearchQBitConfig().withBackendName(TEST_BACKEND_NAME).withOpensearchHost("localhost").withOpensearchPort(9200).withOpensearchIndexName("t"));
      runtime.setDiscoveredTables(List.of(new QuickSearchableTableConfig().withTableName(TEST_ENTITY_TABLE).withPrimaryKeyField("id").withSearchableFields(List.of("name"))));
      return (runtime);
   }



   /*******************************************************************************
    ** Every static Memoization in the QBit is registered (shared or node-local).
    *******************************************************************************/
   @Test
   void testNoUnregisteredStaticCaches() throws Exception
   {
      assertThat(ClusterCacheAudit.unregisteredStaticCaches("com.kingsrook.qbits.quicksearch")).isEmpty();
   }



   /*******************************************************************************
    ** Another node's config invalidation clears this node's cached config.
    *******************************************************************************/
   @Test
   void testRemoteInvalidationClearsConfigCache()
   {
      connectAsNodeA();
      QuickSearchRuntime runtime = newRuntime();
      try
      {
         runtime.isTableEnabled(TEST_ENTITY_TABLE);
         assertThat(runtime.getCachedEnabledFlagCount()).isEqualTo(1);

         ClusterEvent remote = new ClusterEvent(ClusterEvent.TYPE_MEMO_INVALIDATE, QuickSearchRuntime.CONFIG_CACHE_NAME, null);
         remote.setOriginNodeId("node-b");
         WIRE.forEach(listener -> listener.accept(remote));

         assertThat(runtime.getCachedEnabledFlagCount()).isZero();
      }
      finally
      {
         runtime.close();
      }
   }



   /*******************************************************************************
    ** A config change here clears the cache here and tells the other nodes.
    *******************************************************************************/
   @Test
   void testInvalidateConfigPublishesToOtherNodes()
   {
      connectAsNodeA();
      List<ClusterEvent> sent = new CopyOnWriteArrayList<>();
      WIRE.add(sent::add);

      QuickSearchRuntime runtime = newRuntime();
      try
      {
         runtime.isTableEnabled(TEST_ENTITY_TABLE);
         runtime.invalidateConfig();

         assertThat(runtime.getCachedEnabledFlagCount()).isZero();
         assertThat(sent).anySatisfy(event ->
         {
            assertThat(event.getType()).isEqualTo(ClusterEvent.TYPE_MEMO_INVALIDATE);
            assertThat(event.getName()).isEqualTo(QuickSearchRuntime.CONFIG_CACHE_NAME);
            assertThat(event.getOriginNodeId()).isEqualTo("node-a");
         });
      }
      finally
      {
         runtime.close();
      }
   }
}

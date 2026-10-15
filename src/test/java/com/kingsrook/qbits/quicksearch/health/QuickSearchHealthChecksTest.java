/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2021-2026.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qbits.quicksearch.health;


import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.kingsrook.qbits.quicksearch.BaseQuickSearchTest;
import com.kingsrook.qbits.quicksearch.QuickSearchAuthMode;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitProducer;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.QuickSearchStartupMode;
import com.kingsrook.qbits.quicksearch.SearchableFieldConfig;
import com.kingsrook.qbits.quicksearch.api.QuickSearchStatus;
import com.kingsrook.qbits.quicksearch.api.QuickSearchStatus.IndexRow;
import com.kingsrook.qbits.quicksearch.api.QuickSearchStatus.State;
import com.kingsrook.qbits.quicksearch.config.QuickSearchConfigPrimer;
import com.kingsrook.qbits.quicksearch.diagnostics.StatusCache;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.health.HealthCategory;
import com.kingsrook.qqq.backend.core.health.HealthCheckInput;
import com.kingsrook.qqq.backend.core.health.HealthCheckKind;
import com.kingsrook.qqq.backend.core.health.HealthCheckMetaData;
import com.kingsrook.qqq.backend.core.health.HealthCheckOutcome;
import com.kingsrook.qqq.backend.core.health.HealthChecksMetaData;
import com.kingsrook.qqq.backend.core.health.HealthStatus;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSystemUserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;


/*******************************************************************************
 ** Registration and projections use memory records and mocked diagnostics only.
 *******************************************************************************/
class QuickSearchHealthChecksTest extends BaseQuickSearchTest
{
   private static final String SENTINEL = "S3NT1NEL-secret";
   private QInstance instance;
   private QuickSearchQBitConfig config;
   private QuickSearchRuntime runtime;
   private QuickSearchRuntime originalRuntime;
   private StatusCache cache;


   /*******************************************************************************
    ** Exercise the real producer, with credentials that must never enter outcomes.
    *******************************************************************************/
   @BeforeEach
   void setUpHealth() throws Exception
   {
      QInstance base = QContext.getQInstance();
      instance = new QInstance();
      instance.addBackend(base.getBackend(TEST_BACKEND_NAME));
      instance.addTable(base.getTable(TEST_ENTITY_TABLE));
      instance.withInstanceDefaultAuthentication(new QAuthenticationMetaData().withName("anonymous").withType(QAuthenticationType.FULLY_ANONYMOUS));
      QContext.init(instance, new QSystemUserSession());
      config = new QuickSearchQBitConfig().withBackendName(TEST_BACKEND_NAME).withStartupMode(QuickSearchStartupMode.DEGRADED)
         .withEnableRealTimeIndexing(false).withOpensearchUrl("https://unused.invalid")
         .withOpensearchIndexName("health-test").withSearchableTable(TEST_ENTITY_TABLE, List.of(new SearchableFieldConfig("name")))
         .withAuthMode(QuickSearchAuthMode.BASIC).withOpensearchUsername(SENTINEL).withOpensearchPassword(SENTINEL);
      new QuickSearchQBitProducer().withConfig(config).produce(instance);
      QuickSearchConfigPrimer.insertOne(config.getQuickSearchIndexTableName(), new QRecord().withValue("tableName", TEST_ENTITY_TABLE)
         .withValue("enabled", true).withValue("status", "ACTIVE").withValue("documentCount", 12));
      originalRuntime = config.getRuntime();
      runtime = spy(originalRuntime);
      cache = mock(StatusCache.class);
      doReturn(cache).when(runtime).getStatusCache();
      config.setRuntime(runtime);
   }



   /*******************************************************************************
    ** Release the producer's real worker even when tests replace the cache.
    *******************************************************************************/
   @AfterEach
   void closeHealth()
   {
      if(originalRuntime != null)
      {
         originalRuntime.close();
      }
   }



   /*******************************************************************************
    ** Static checks and dynamically resolved indexes have the specified metadata.
    *******************************************************************************/
   @Test
   void registration() throws Exception
   {
      QuickSearchHealthChecks.register(instance);
      HealthChecksMetaData registry = HealthChecksMetaData.ofOrWithNew(instance);
      assertThat(registry.getChecks()).extracting(HealthCheckMetaData::getName)
         .containsExactly("quick_search.opensearch", "quick_search.backlog");
      assertThat(registry.getSuppliers()).singleElement().satisfies(supplier ->
      {
         assertThat(supplier.getNamePrefix()).isEqualTo("quick_search.index");
         assertThat(supplier.getComponent()).isEqualTo("quick_search");
      });
      assertThat(registry.resolveAll(instance)).hasSize(3).allSatisfy(check ->
      {
         assertThat(check.getName()).matches("[a-z0-9]+([._-][a-z0-9]+)*");
         assertThat(check.getKind()).isEqualTo(HealthCheckKind.DEPENDENCY);
         assertThat(check.getCategory()).isEqualTo(HealthCategory.SEARCH);
         assertThat(check.getComponent()).isEqualTo("quick_search");
         assertThat(check.getIntervalSeconds()).isEqualTo(60);
         assertThat(check.getTimeoutMs()).isEqualTo(5000);
      });
      assertThat(registry.resolveAll(instance)).extracting(HealthCheckMetaData::getName).contains("quick_search.index.testentity");
      verifyNoInteractions(cache);
   }



   /*******************************************************************************
    ** Overall degradation from indexing must not downgrade a healthy cluster.
    *******************************************************************************/
   @ParameterizedTest
   @CsvSource({"OK,true,green,UP", "DEGRADED,true,green,UP", "DEGRADED,true,yellow,DEGRADED", "DOWN,true,red,DOWN",
      "DOWN,false,,DOWN", "DISABLED,,,UNKNOWN", "DEGRADED,,,DEGRADED", "OK,true,unexpected,DEGRADED"})
   void clusterStates(State state, Boolean reachable, String color, HealthStatus expected) throws Exception
   {
      snapshot(state, reachable, color, false, 0, 0, false, List.of(row(true, "ACTIVE", 12L, 12L, null, 0)));
      assertThat(run("opensearch").getStatus()).isEqualTo(expected);
   }



   /*******************************************************************************
    ** Boundary values, exhausted events, pause, truncation and missing measurements.
    *******************************************************************************/
   @ParameterizedTest
   @CsvSource({"OK,false,0,0,false,UP", "DEGRADED,false,100,0,false,UP", "DEGRADED,false,101,0,false,DEGRADED",
      "DEGRADED,false,0,1,false,DEGRADED", "DEGRADED,true,0,0,false,DEGRADED", "OK,false,0,0,true,DEGRADED",
      "DEGRADED,false,,0,false,DEGRADED", "DEGRADED,false,0,,false,DEGRADED", "DEGRADED,,0,0,false,DEGRADED",
      "DISABLED,false,0,0,false,UNKNOWN", "DOWN,false,0,0,false,UP"})
   void backlogStates(State state, Boolean paused, Integer pending, Integer exhausted, Boolean truncated, HealthStatus expected) throws Exception
   {
      snapshot(state, true, "green", paused, pending, exhausted, truncated, List.of());
      assertThat(run("backlog").getStatus()).isEqualTo(expected);
   }



   /*******************************************************************************
    ** Counts, index status, last errors and real-time errors each affect the check.
    *******************************************************************************/
   @ParameterizedTest
   @CsvSource({"true,ACTIVE,12,12,,0,UP", "false,ACTIVE,12,12,,0,UNKNOWN", "true,REBUILDING,12,12,,0,DEGRADED",
      "true,ACTIVE,12,13,,0,DEGRADED", "true,ACTIVE,12,12,error,0,DEGRADED", "true,ACTIVE,12,12,,2,DEGRADED",
      "true,ACTIVE,12,,,0,UP", "true,ACTIVE,,12,,,UP", "true,,12,12,,0,DEGRADED"})
   void indexStates(Boolean enabled, String state, Long documents, Long source, String error, Integer realTimeErrors, HealthStatus expected) throws Exception
   {
      snapshot(State.OK, true, "green", false, 0, 0, false, List.of(row(enabled, state, documents, source, error, realTimeErrors)));
      HealthCheckOutcome outcome = run("index.testentity");
      assertThat(outcome.getStatus()).isEqualTo(expected);
      if(realTimeErrors != null)
      {
         assertThat(outcome.getDetails().getValues()).containsEntry("realTimeErrorCount", realTimeErrors);
      }
   }



   /*******************************************************************************
    ** Supplier identities are refreshed; checks never retain old measurements.
    *******************************************************************************/
   @Test
   void dynamicRowsAndMissingSnapshot() throws Exception
   {
      HealthCheckMetaData check = check("index.testentity");
      snapshot(State.OK, true, "green", false, 0, 0, false, List.of(row(true, "ACTIVE", 12L, 12L, null, 0)));
      assertThat(check.getCheckInstance().run(input()).getStatus()).isEqualTo(HealthStatus.UP);
      snapshot(State.OK, true, "green", false, 0, 0, false, List.of());
      assertThat(check.getCheckInstance().run(input()).getStatus()).isEqualTo(HealthStatus.UNKNOWN);
      QuickSearchConfigPrimer.insertOne(config.getQuickSearchIndexTableName(), new QRecord().withValue("tableName", "New.Index-Name")
         .withValue("enabled", false).withValue("status", "ACTIVE"));
      assertThat(HealthChecksMetaData.ofOrWithNew(instance).resolveAll(instance)).extracting(HealthCheckMetaData::getName)
         .contains("quick_search.index.new.index-name");
   }



   /*******************************************************************************
    ** Raw outcomes omit poisoned diagnostics and exceptions; sanitize is package-private in core.
    *******************************************************************************/
   @Test
   void noSecrets() throws Exception
   {
      List<HealthCheckMetaData> checks = HealthChecksMetaData.ofOrWithNew(instance).resolveAll(instance);
      snapshot(State.DEGRADED, true, SENTINEL, true, 101, 1, false, List.of(row(true, SENTINEL, 12L, 13L, SENTINEL, 9)));
      for(HealthCheckMetaData check : checks)
      {
         assertClean(check.getCheckInstance().run(input()));
      }
      when(cache.get(eq(runtime), any(Instant.class))).thenThrow(new QException("Failure " + SENTINEL));
      for(HealthCheckMetaData check : checks)
      {
         HealthCheckOutcome outcome = check.getCheckInstance().run(input());
         assertThat(outcome.getStatus()).isEqualTo(HealthStatus.DOWN);
         assertClean(outcome);
      }
   }



   /*******************************************************************************
    ** An expired deadline does no diagnostic work; missing runtime is UNKNOWN.
    *******************************************************************************/
   @Test
   void expiredAndUnconfigured() throws Exception
   {
      for(HealthCheckMetaData check : HealthChecksMetaData.ofOrWithNew(instance).resolveAll(instance))
      {
         assertThat(check.getCheckInstance().run(input().withDeadline(Instant.now().minusSeconds(1))).getStatus()).isEqualTo(HealthStatus.DOWN);
         assertThat(check.getCheckInstance().run(input().withQInstance(new QInstance())).getStatus()).isEqualTo(HealthStatus.UNKNOWN);
      }
      assertThat(new QuickSearchHealthChecks().supply(new QInstance())).isEmpty();
      verifyNoInteractions(cache);
   }



   /*******************************************************************************
    ** The real cache also removes exception text from a failed diagnostic refresh.
    *******************************************************************************/
   @Test
   void realCacheFailureIsClean() throws Exception
   {
      doReturn(originalRuntime.getStatusCache()).when(runtime).getStatusCache();
      doThrow(new QException("Failed " + SENTINEL)).when(runtime).getClient();
      assertThat(run("opensearch").getStatus()).isEqualTo(HealthStatus.DOWN);
      for(HealthCheckMetaData check : HealthChecksMetaData.ofOrWithNew(instance).resolveAll(instance))
      {
         assertClean(check.getCheckInstance().run(input()));
      }
   }



   /*******************************************************************************
    ** A stuck refresh cannot hold another caller beyond its shorter deadline.
    *******************************************************************************/
   @Test
   void cacheWaitAndLockRespectDeadline() throws Exception
   {
      CountDownLatch entered = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      doReturn(originalRuntime.getStatusCache()).when(runtime).getStatusCache();
      org.mockito.Mockito.doAnswer(invocation ->
      {
         entered.countDown();
         release.await(5, TimeUnit.SECONDS);
         throw (new QException("Failed " + SENTINEL));
      }).when(runtime).getClient();
      try(var executor = Executors.newSingleThreadExecutor())
      {
         var first = executor.submit(() ->
         {
            QContext.init(instance, new QSystemUserSession());
            try
            {
               return (runtime.getStatusCache().get(runtime, Instant.now().plusSeconds(2)));
            }
            finally
            {
               QContext.clear();
            }
         });
         try
         {
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            Instant started = Instant.now();
            assertThatThrownBy(() -> runtime.getStatusCache().get(runtime, started.plusMillis(100))).isInstanceOf(QException.class);
            assertThat(Instant.now()).isBefore(started.plusSeconds(1));
         }
         finally
         {
            release.countDown();
         }
         first.get(3, TimeUnit.SECONDS);
      }
      assertThatThrownBy(() -> runtime.getStatusCache().get(runtime, Instant.now().minusSeconds(1))).isInstanceOf(QException.class);
   }



   /*******************************************************************************
    ** A short health deadline bounds the refresh wait as well as lock contention.
    *******************************************************************************/
   @Test
   void refreshWaitRespectsDeadline() throws Exception
   {
      CountDownLatch release = new CountDownLatch(1);
      doReturn(originalRuntime.getStatusCache()).when(runtime).getStatusCache();
      org.mockito.Mockito.doAnswer(invocation ->
      {
         release.await(5, TimeUnit.SECONDS);
         throw (new QException("Failed " + SENTINEL));
      }).when(runtime).getClient();
      try
      {
         HealthCheckMetaData check = check("opensearch");
         Instant started = Instant.now();
         HealthCheckOutcome outcome = check.getCheckInstance().run(input().withDeadline(started.plusMillis(100)));
         assertThat(Instant.now()).isBefore(started.plusSeconds(1));
         assertThat(outcome.getStatus()).isEqualTo(HealthStatus.DOWN);
         assertClean(outcome);
      }
      finally
      {
         release.countDown();
      }
   }



   /*******************************************************************************
    ** Supply a deterministic snapshot without creating a network transport.
    *******************************************************************************/
   private void snapshot(State state, Boolean reachable, String color, Boolean paused, Integer pending, Integer exhausted, Boolean truncated, List<IndexRow> rows) throws Exception
   {
      when(cache.get(eq(runtime), any(Instant.class))).thenReturn(new QuickSearchStatus(state, reachable, color, 1, 2, 0, paused, rows,
         pending, exhausted, truncated, false, null, SENTINEL, Instant.now(), "/unused"));
   }



   /*******************************************************************************
    ** A row whose identity is deliberately mixed case.
    *******************************************************************************/
   private IndexRow row(Boolean enabled, String state, Long documents, Long source, String error, Integer realTimeErrors)
   {
      return (new IndexRow(TEST_ENTITY_TABLE, enabled, state, documents, source, Instant.now(), Instant.now(), error, realTimeErrors));
   }



   /*******************************************************************************
    ** Resolve through the actual core registry, including supplier validation.
    *******************************************************************************/
   private HealthCheckMetaData check(String suffix) throws Exception
   {
      return (HealthChecksMetaData.ofOrWithNew(instance).resolveAll(instance).stream()
         .filter(check -> check.getName().equals("quick_search." + suffix)).findFirst().orElseThrow());
   }



   /*******************************************************************************
    ** Run one registered check with a bounded input.
    *******************************************************************************/
   private HealthCheckOutcome run(String suffix) throws Exception
   {
      return (check(suffix).getCheckInstance().run(input()));
   }



   /*******************************************************************************
    ** The runner always supplies an instance and absolute deadline.
    *******************************************************************************/
   private HealthCheckInput input()
   {
      return (new HealthCheckInput().withQInstance(instance).withDeadline(Instant.now().plusSeconds(5)));
   }



   /*******************************************************************************
    ** Assert the raw public boundary, without relying on core's later redaction.
    *******************************************************************************/
   private void assertClean(HealthCheckOutcome outcome)
   {
      assertThat(outcome.getMessage()).doesNotContain(SENTINEL);
      assertThat(outcome.getDetails().toJson()).doesNotContain(SENTINEL);
   }
}

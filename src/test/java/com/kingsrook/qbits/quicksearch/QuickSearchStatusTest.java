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

package com.kingsrook.qbits.quicksearch;


import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kingsrook.qbits.quicksearch.api.QuickSearchApi;
import com.kingsrook.qbits.quicksearch.api.QuickSearchStatus;
import com.kingsrook.qbits.quicksearch.api.QuickSearchStatusService;
import com.kingsrook.qbits.quicksearch.diagnostics.StatusDiagnostics;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;
import com.kingsrook.qbits.quicksearch.model.QuickSearchValidationRun;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.instances.AbstractQQQApplication;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QSystemUserSession;
import com.kingsrook.qqq.backend.core.model.session.QUser;
import com.kingsrook.qqq.backend.core.modules.authentication.QAuthenticationModuleCustomizerInterface;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryRecordStore;
import com.kingsrook.qqq.middleware.javalin.QApplicationJavalinServer;
import com.kingsrook.qqq.middleware.javalin.QJavalinImplementation;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Real bounded diagnostics over a memory backend and a loopback health endpoint.
 *******************************************************************************/
class QuickSearchStatusTest
{
   private HttpServer healthServer;
   private QuickSearchQBitConfig config;
   private QInstance instance;
   private volatile String health = "green";
   private volatile int requests;
   private CountDownLatch blocked;


   /*******************************************************************************
    ** Only cluster health is exposed: status must never scan the source/index.
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      MemoryRecordStore.getInstance().reset();
      healthServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      healthServer.createContext("/", exchange ->
      {
         requests++;
         if(blocked != null)
         {
            try
            {
               blocked.await(10, TimeUnit.SECONDS);
            }
            catch(InterruptedException e)
            {
               Thread.currentThread().interrupt();
            }
         }
         byte[] body = ("{\"status\":\"" + health + "\",\"number_of_nodes\":1,\"active_shards\":1,\"unassigned_shards\":0}").getBytes(StandardCharsets.UTF_8);
         exchange.getResponseHeaders().add("Content-Type", "application/json");
         exchange.sendResponseHeaders(200, body.length);
         exchange.getResponseBody().write(body);
         exchange.close();
      });
      healthServer.start();
      instance = new QInstance();
      instance.addBackend(new QBackendMetaData().withName("memory").withBackendType(MemoryBackendModule.class));
      instance.withInstanceDefaultAuthentication(new QAuthenticationMetaData().withName("anonymous").withType(QAuthenticationType.FULLY_ANONYMOUS));
      instance.addTable(new QTableMetaData().withName("product").withBackendName("memory").withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER)).withField(new QFieldMetaData("name", QFieldType.STRING)));
      QContext.init(instance, new QSystemUserSession());
      config = new QuickSearchQBitConfig().withBackendName("memory").withStartupMode(QuickSearchStartupMode.DEGRADED)
         .withOpensearchUrl("http://127.0.0.1:" + healthServer.getAddress().getPort()).withEnableRealTimeIndexing(false)
         .withOpensearchIndexName("status-test").withSearchableTable("product", List.of(new SearchableFieldConfig("name")));
      new QuickSearchQBitProducer().withConfig(config).produce(instance);
      insert(config.getQuickSearchIndexTableName(), new QRecord().withValue("tableName", "product").withValue("enabled", true)
         .withValue("status", "ACTIVE").withValue("documentCount", 12).withValue("lastBasepullTime", Instant.parse("2026-10-01T00:00:00Z")));
   }



   /*******************************************************************************
    ** Close the qbit worker, transport, and test server.
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      if(blocked != null)
      {
         blocked.countDown();
      }
      if(config != null && config.getRuntime() != null)
      {
         config.getRuntime().close();
      }
      healthServer.stop(0);
      QContext.clear();
      MemoryRecordStore.getInstance().reset();
   }



   /*******************************************************************************
    ** Cached values stay immutable and permissions are rechecked on cache hits.
    *******************************************************************************/
   @Test
   void cacheAndPermission() throws Exception
   {
      QuickSearchStatus first = QuickSearchStatusService.get();
      assertThat(first.state()).isEqualTo(QuickSearchStatus.State.OK);
      assertThat(first.clusterReachable()).isTrue();
      assertThat(first.indexes().get(0).documentCount()).isEqualTo(12);
      assertThat(first.indexes().get(0).sourceCount()).isNull();
      assertThat(first.homeHref()).isEqualTo(QuickSearchApi.HOME_HREF);
      assertThat(QuickSearchStatusService.get()).isSameAs(first);
      assertThat(requests).isEqualTo(1);
      assertThatThrownBy(() -> first.indexes().clear()).isInstanceOf(UnsupportedOperationException.class);
      QContext.init(instance, new QSession());
      assertThatThrownBy(QuickSearchStatusService::get).isInstanceOf(QPermissionDeniedException.class);
      assertThat(requests).isEqualTo(1);
   }



   /*******************************************************************************
    ** Saved validator counts and operational backlog cause degradation.
    *******************************************************************************/
   @Test
   void savedValidationAndBacklog() throws Exception
   {
      Instant measured = Instant.parse("2026-10-02T00:00:00Z");
      insert(QuickSearchValidationRun.TABLE_NAME, new QRecord().withValue("status", "WARN").withValue("endTime", measured)
         .withValue("reportJson", "{\"tableStats\":[{\"tableName\":\"product\",\"databaseCount\":20,\"indexCount\":12}]}"));
      insert(QuickSearchFailedEvent.TABLE_NAME, new QRecord().withValue("status", "PENDING"));
      insert(QuickSearchFailedEvent.TABLE_NAME, new QRecord().withValue("status", "EXHAUSTED"));
      QuickSearchStatus result = QuickSearchStatusService.get();
      assertThat(result.state()).isEqualTo(QuickSearchStatus.State.DEGRADED);
      assertThat(result.pending()).isEqualTo(1);
      assertThat(result.exhausted()).isEqualTo(1);
      assertThat(result.indexes().get(0).sourceCount()).isEqualTo(20);
      assertThat(result.indexes().get(0).countsCheckedAt()).isEqualTo(measured);
      assertThat(result.lastSuccessfulSync()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
   }



   /*******************************************************************************
    ** Reachability differs from healthy primaries; yellow is degraded, red down.
    *******************************************************************************/
   @Test
   void healthColorsAndOffline() throws Exception
   {
      health = "yellow";
      assertThat(StatusDiagnostics.read(config.getRuntime()).state()).isEqualTo(QuickSearchStatus.State.DEGRADED);
      health = "red";
      QuickSearchStatus red = StatusDiagnostics.read(config.getRuntime());
      assertThat(red.state()).isEqualTo(QuickSearchStatus.State.DOWN);
      assertThat(red.clusterReachable()).isTrue();
      healthServer.stop(0);
      QuickSearchStatus down = StatusDiagnostics.read(config.getRuntime());
      assertThat(down.state()).isEqualTo(QuickSearchStatus.State.DOWN);
      assertThat(down.clusterReachable()).isFalse();
   }



   /*******************************************************************************
    ** A blocked remote call has bounded caller latency and cannot grow a queue.
    *******************************************************************************/
   @Test
   void timeoutDoesNotQueueMoreWork() throws Exception
   {
      blocked = new CountDownLatch(1);
      long start = System.nanoTime();
      QuickSearchStatus result = QuickSearchStatusService.get();
      assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(3500);
      assertThat(result.state()).isEqualTo(QuickSearchStatus.State.DEGRADED);
      assertThat(result.clusterReachable()).isNull();
      assertThat(result.lastError()).contains("timed out");
      assertThat(QuickSearchStatusService.get()).isSameAs(result);
      assertThat(requests).isEqualTo(1);
      blocked.countDown();
      Thread.sleep(5100);
      QuickSearchStatus recovered = QuickSearchStatusService.get();
      assertThat(recovered.state()).isEqualTo(QuickSearchStatus.State.OK);
      assertThat(recovered.checkedAt()).isAfter(result.checkedAt());
      assertThat(requests).isEqualTo(2);
   }



   /*******************************************************************************
    ** Rate limiting uses the diagnostics limiter before launching remote work.
    *******************************************************************************/
   @Test
   void rateLimitBeforeRefresh() throws Exception
   {
      config.withDiagnosticRunsPerMinute(1);
      com.kingsrook.qbits.quicksearch.diagnostics.DiagnosticsSupport.checkRateLimit(config.getRuntime(), "STATUS");
      assertThatThrownBy(QuickSearchStatusService::get).hasMessageContaining("Too many");
      assertThat(requests).isZero();
   }



   /*******************************************************************************
    ** Bounds are explicit, not silent truncation or full counts of growing queues.
    *******************************************************************************/
   @Test
   void boundedTablesAndBacklog() throws Exception
   {
      List<QRecord> indexes = new java.util.ArrayList<>();
      for(int i = 0; i < 100; i++)
      {
         indexes.add(new QRecord().withValue("tableName", "table" + i).withValue("enabled", true).withValue("status", "ACTIVE"));
      }
      new InsertAction().execute(new InsertInput(config.getQuickSearchIndexTableName()).withRecords(indexes));
      List<QRecord> backlog = new java.util.ArrayList<>();
      for(int i = 0; i < 1001; i++)
      {
         backlog.add(new QRecord().withValue("status", "AWAITING_REINDEX"));
      }
      new InsertAction().execute(new InsertInput(QuickSearchFailedEvent.TABLE_NAME).withRecords(backlog));
      QuickSearchStatus status = QuickSearchStatusService.get();
      assertThat(status.tablesTruncated()).isTrue();
      assertThat(status.backlogTruncated()).isTrue();
      assertThat(status.indexes()).hasSize(100);
      assertThat(status.pending()).isEqualTo(1000);
      assertThat(status.state()).isEqualTo(QuickSearchStatus.State.DEGRADED);
   }



   /*******************************************************************************
    ** Separate QInstances cannot reuse each other's snapshots; links honor prefixes.
    *******************************************************************************/
   @Test
   void prefixedDisabledInstance() throws Exception
   {
      QuickSearchStatus first = QuickSearchStatusService.get();
      QInstance other = new QInstance();
      other.addBackend(new QBackendMetaData().withName("memory").withBackendType(MemoryBackendModule.class));
      other.addTable(instance.getTable("product"));
      other.withInstanceDefaultAuthentication(new QAuthenticationMetaData().withName("anonymous").withType(QAuthenticationType.FULLY_ANONYMOUS));
      QuickSearchQBitConfig otherConfig = new QuickSearchQBitConfig().withBackendName("memory").withTableNamePrefix("host_")
         .withStartupMode(QuickSearchStartupMode.DEGRADED).withOpensearchUrl("http://127.0.0.1:1")
         .withOpensearchIndexName("other").withEnableRealTimeIndexing(false)
         .withSearchableTable("product", List.of(new SearchableFieldConfig("name")));
      QContext.init(other, new QSystemUserSession());
      new QuickSearchQBitProducer().withConfig(otherConfig).produce(other);
      try
      {
         insert(otherConfig.getQuickSearchIndexTableName(), new QRecord().withValue("tableName", "product").withValue("enabled", false));
         QuickSearchStatus second = QuickSearchStatusService.get();
         assertThat(second).isNotSameAs(first);
         assertThat(second.state()).isEqualTo(QuickSearchStatus.State.DISABLED);
         assertThat(second.clusterReachable()).isNull();
         assertThat(second.homeHref()).isEqualTo("/app/host_quickSearchAdmin");
         assertThat(requests).isEqualTo(1);
         assertThat(second.toSchema()).isNotNull();
         assertThat(second.indexes().get(0).toSchema()).isNotNull();
      }
      finally
      {
         otherConfig.getRuntime().close();
         QContext.init(instance, new QSystemUserSession());
      }
   }



   /*******************************************************************************
    ** Strict route auditing, real authentication, 403 after cache population,
    ** and the same serialized snapshot through the production HTTP pipeline.
    *******************************************************************************/
   @Test
   void strictHttpContract() throws Exception
   {
      instance.withInstanceDefaultAuthentication(new QAuthenticationMetaData().withName("mock").withType(QAuthenticationType.MOCK)
         .withCustomizer(new QCodeReference(TestSessions.class)));
      int port;
      try(ServerSocket socket = new ServerSocket(0))
      {
         port = socket.getLocalPort();
      }
      QApplicationJavalinServer server = new QApplicationJavalinServer(new AbstractQQQApplication()
      {
         @Override
         public QInstance defineQInstance()
         {
            return (instance);
         }
      }).withPort(port).withServeFrontendNext(false).withServeFrontendMaterialDashboard(false)
         .withServeLegacyUnversionedMiddlewareAPI(false).withStrictUiApiRoutes(true);
      try(HttpClient client = HttpClient.newHttpClient())
      {
         server.start();
         String url = "http://127.0.0.1:" + port + "/qqq/v1/qbit/quick-search/status";
         String suffix = UUID.randomUUID().toString();
         var response = client.send(HttpRequest.newBuilder(URI.create(url)).header("Cookie", "sessionUUID=admin-" + suffix).GET().build(), HttpResponse.BodyHandlers.ofString());
         assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
         var json = new ObjectMapper().readTree(response.body());
         assertThat(json.path("homeHref").asText()).isEqualTo(QuickSearchApi.HOME_HREF);
         assertThat(json.path("checkedAt").isTextual()).isTrue();
         assertThat(json.path("indexes").isArray()).isTrue();
         java.util.Set<String> fields = new java.util.HashSet<>();
         json.fieldNames().forEachRemaining(fields::add);
         assertThat(fields).containsExactlyInAnyOrderElementsOf(new com.kingsrook.qbits.quicksearch.api.QuickSearchStatusSpec()
            .defineComponentSchemas().get("QuickSearchStatusV1").getProperties().keySet());
         var anonymous = client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
         // MOCK authentication supplies a session even without cookies; it has no admin permission.
         assertThat(anonymous.statusCode()).as(anonymous.body()).isEqualTo(403);
         var denied = client.send(HttpRequest.newBuilder(URI.create(url)).header("Cookie", "sessionUUID=reader-" + suffix).GET().build(), HttpResponse.BodyHandlers.ofString());
         assertThat(denied.statusCode()).as(denied.body()).isEqualTo(403);
         var openapi = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/qqq/v1/openapi.yaml")).GET().build(), HttpResponse.BodyHandlers.ofString());
         assertThat(openapi.body()).contains("/qqq/v1/qbit/quick-search/status", "QuickSearchStatusV1");
      }
      finally
      {
         server.stop();
         QJavalinImplementation.setSessionCookieHttpOnly(false);
      }
   }



   /*******************************************************************************
    ** Production mock-auth customization with distinct administrator and reader.
    *******************************************************************************/
   public static class TestSessions implements QAuthenticationModuleCustomizerInterface
   {
      /*******************************************************************************
       ** Grant only the administrator the existing qbit admin permission.
       *******************************************************************************/
      @Override
      public void customizeSession(QInstance instance, QSession session, Map<String, Object> context)
      {
         session.withUser(new QUser().withIdReference(session.getUuid()));
         if(session.getUuid().startsWith("admin-"))
         {
            session.withPermissions("quick_search_admin.hasAccess");
         }
      }
   }



   /*******************************************************************************
    ** Insert fixture metadata using the same backend actions as production.
    *******************************************************************************/
   private void insert(String table, QRecord row) throws Exception
   {
      new InsertAction().execute(new InsertInput(table).withRecords(List.of(row)));
   }
}

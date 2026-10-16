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

package com.kingsrook.qbits.quicksearch.opensearch;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.quicksearch.QuickSearchAuthMode;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient.ReplicaStatus;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Replica sizing and reconcile against a stub OpenSearch HTTP server.
 *******************************************************************************/
class QuickSearchReplicasTest
{
   private HttpServer   server;
   private List<String> requests;
   private List<String> bodies;
   private Integer      dataNodes;
   private String       settings;
   private Integer      settingsStatus;
   private Integer      putStatus;



   /*******************************************************************************
    ** Stub: _cluster/health reports dataNodes, GET _settings returns settings,
    ** PUT _settings returns putStatus; every request is recorded.
    *******************************************************************************/
   @BeforeEach
   void setUp() throws IOException
   {
      requests       = new ArrayList<>();
      bodies         = new ArrayList<>();
      dataNodes      = 1;
      settingsStatus = 200;
      putStatus      = 200;
      settings       = settingsBody("1", null);
      server         = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", this::handle);
      server.start();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      server.stop(0);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private void handle(HttpExchange exchange) throws IOException
   {
      String request = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
      requests.add(request);
      bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      int    status = 200;
      String body   = "{}";
      if(request.equals("GET /_cluster/health"))
      {
         body = dataNodes == null ? "{\"status\":\"green\"}" : "{\"status\":\"yellow\",\"number_of_data_nodes\":" + dataNodes + "}";
      }
      else if(request.startsWith("GET ") && request.endsWith("/_settings"))
      {
         status = settingsStatus;
         body   = status == 200 ? settings : "{\"error\":{\"reason\":\"nope\"}}";
      }
      else if(request.startsWith("PUT ") && request.endsWith("/_settings"))
      {
         status = putStatus;
         body   = status == 200 ? "{\"acknowledged\":true}" : "{\"error\":{\"reason\":\"blocked\"}}";
      }
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String settingsBody(String replicas, String autoExpand)
   {
      return ("{\"alias-v2-1\":{\"settings\":{\"index\":{\"number_of_shards\":\"1\",\"number_of_replicas\":\"" + replicas + "\""
         + (autoExpand == null ? "" : ",\"auto_expand_replicas\":\"" + autoExpand + "\"") + "}}}}");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private QuickSearchQBitConfig config()
   {
      return (new QuickSearchQBitConfig().withOpensearchUrl("http://127.0.0.1:" + server.getAddress().getPort()).withOpensearchIndexName("alias"));
   }



   /*******************************************************************************
    ** Automatic sizing never exceeds data nodes - 1 nor maxAutoReplicas.
    *******************************************************************************/
   @ParameterizedTest
   @CsvSource({"1,1,0", "2,1,1", "3,1,1", "3,2,2", "5,2,2", "0,1,0", "4,0,0"})
   void autoReplicas(int nodes, int max, int expected)
   {
      assertThat(QuickSearchOpenSearchClient.autoReplicas(nodes, max)).isEqualTo(expected);
   }



   /*******************************************************************************
    ** Zero config on one node: the replica is dropped, once.
    *******************************************************************************/
   @Test
   void singleNodeDropsReplicaIdempotently() throws Exception
   {
      try(QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(config()))
      {
         ReplicaStatus status = client.reconcileReplicas();
         assertThat(status).isEqualTo(new ReplicaStatus(0, 1, 1, false));
         assertThat(client.getReplicaStatus()).isEqualTo(status);
         assertThat(requests).contains("PUT /alias-v2-1/_settings");
         assertThat(bodies.get(requests.indexOf("PUT /alias-v2-1/_settings"))).isEqualTo("{\"index\":{\"number_of_replicas\":0}}");

         settings = settingsBody("0", null);
         requests.clear();
         assertThat(client.reconcileReplicas()).isEqualTo(new ReplicaStatus(0, 1, 0, false));
         assertThat(requests).noneMatch(request -> request.startsWith("PUT"));
      }
   }



   /*******************************************************************************
    ** Multi-node clusters keep (or get) one replica by default.
    *******************************************************************************/
   @Test
   void multiNodeKeepsOneReplica() throws Exception
   {
      dataNodes = 3;
      try(QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(config()))
      {
         assertThat(client.reconcileReplicas()).isEqualTo(new ReplicaStatus(1, 3, 0, false));
         assertThat(requests).noneMatch(request -> request.startsWith("PUT"));
      }
   }



   /*******************************************************************************
    ** An explicit indexReplicas wins and does not need cluster health.
    *******************************************************************************/
   @Test
   void explicitReplicasOverrideAuto() throws Exception
   {
      settings = settingsBody("0", null);
      try(QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(config().withIndexReplicas(2)))
      {
         assertThat(client.reconcileReplicas()).isEqualTo(new ReplicaStatus(2, null, 1, false));
         assertThat(requests).doesNotContain("GET /_cluster/health");
         assertThat(bodies.get(requests.indexOf("PUT /alias-v2-1/_settings"))).isEqualTo("{\"index\":{\"number_of_replicas\":2}}");
      }
   }



   /*******************************************************************************
    ** An operator's auto_expand_replicas is left alone.
    *******************************************************************************/
   @Test
   void autoExpandIsRespected() throws Exception
   {
      settings = settingsBody("1", "0-1");
      try(QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(config()))
      {
         assertThat(client.reconcileReplicas()).isEqualTo(new ReplicaStatus(0, 1, 0, false));
         assertThat(requests).noneMatch(request -> request.startsWith("PUT"));
      }
   }



   /*******************************************************************************
    ** Failures are recorded, never thrown.
    *******************************************************************************/
   @Test
   void failuresAreToleratedAndRecorded() throws Exception
   {
      putStatus = 403;
      try(QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(config()))
      {
         assertThat(client.reconcileReplicas()).isEqualTo(new ReplicaStatus(0, 1, 0, true));

         settingsStatus = 500;
         assertThat(client.reconcileReplicas()).isEqualTo(new ReplicaStatus(0, 1, 0, true));

         dataNodes = null;
         assertThat(client.reconcileReplicas()).isEqualTo(new ReplicaStatus(null, null, 0, true));
         assertThat(client.getReplicaStatus().failed()).isTrue();
      }
   }



   /*******************************************************************************
    ** An unreachable cluster is a recorded failure too.
    *******************************************************************************/
   @Test
   void unreachableIsRecorded() throws Exception
   {
      QuickSearchQBitConfig config = config();
      server.stop(0);
      try(QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(config.withConnectTimeoutMillis(500)))
      {
         assertThat(client.reconcileReplicas()).isEqualTo(new ReplicaStatus(null, null, 0, true));
      }
   }



   /*******************************************************************************
    ** OpenSearch Serverless manages replicas itself: nothing is sent.
    *******************************************************************************/
   @Test
   void serverlessIsNotManaged() throws Exception
   {
      QuickSearchQBitConfig config = config().withAuthMode(QuickSearchAuthMode.AWS_SIGV4).withAwsServiceName("aoss").withAwsRegion("us-east-1");
      try(QuickSearchOpenSearchClient client = new QuickSearchOpenSearchClient(config))
      {
         assertThat(client.reconcileReplicas()).isNull();
         assertThat(client.getReplicaStatus()).isNull();
         assertThat(requests).isEmpty();
      }
   }
}

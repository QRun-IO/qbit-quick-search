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


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.scheduleing.simple.SimpleSchedulerMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Validation rules added for 1.0: URLs, auth modes, TLS, index names, field
 ** existence, scheduling, secrets handling.
 *******************************************************************************/
class QuickSearchQBitConfigValidationTest
{

   /*******************************************************************************
    **
    *******************************************************************************/
   private QInstance instanceWithBackendAndCustomer()
   {
      QInstance qInstance = new QInstance();
      qInstance.addBackend(new QBackendMetaData().withName("memory").withBackendType(MemoryBackendModule.class));
      qInstance.addTable(new QTableMetaData()
         .withName("customer")
         .withBackendName("memory")
         .withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("name", QFieldType.STRING))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME)));
      return (qInstance);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private QuickSearchQBitConfig baseConfig()
   {
      return (new QuickSearchQBitConfig()
         .withBackendName("memory")
         .withOpensearchHost("localhost")
         .withOpensearchPort(9200)
         .withOpensearchIndexName("app-search")
         .withSearchableTable("customer", List.of(new SearchableFieldConfig("name"))));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private List<String> validate(QuickSearchQBitConfig config)
   {
      List<String> errors = new ArrayList<>();
      config.validate(instanceWithBackendAndCustomer(), errors);
      return (errors);
   }



   @Test
   void testUrl_replacesHostPortAndScheme()
   {
      QuickSearchQBitConfig config = baseConfig().withOpensearchHost(null).withOpensearchPort(null).withOpensearchUrl("https://search.example.com");
      assertThat(validate(config)).isEmpty();
      assertThat(config.getEffectiveScheme()).isEqualTo("https");
      assertThat(config.getEffectiveHost()).isEqualTo("search.example.com");
      assertThat(config.getEffectivePort()).isEqualTo(443);

      config.withOpensearchUrl("http://os.internal:9201");
      assertThat(config.getEffectivePort()).isEqualTo(9201);
      assertThat(config.getEffectiveScheme()).isEqualTo("http");
   }



   @Test
   void testUrl_invalid_addsError()
   {
      assertThat(validate(baseConfig().withOpensearchUrl("ftp://nope"))).anyMatch(e -> e.contains("http:// or https://"));
      assertThat(validate(baseConfig().withOpensearchUrl("https://"))).anyMatch(e -> e.contains("host"));
   }



   @Test
   void testUrl_withUserInfo_isRejectedWithoutEchoingIt()
   {
      List<String> errors = validate(baseConfig().withOpensearchHost(null).withOpensearchPort(null).withOpensearchUrl("https://admin:s3cret@search.example.com"));
      assertThat(errors).anyMatch(e -> e.contains("opensearchUrl must not contain credentials"));
      assertThat(errors).noneMatch(e -> e.contains("s3cret") || e.contains("admin"));

      assertThat(validate(baseConfig().withOpensearchUrl("https://admin@search.example.com"))).anyMatch(e -> e.contains("opensearchUrl must not contain credentials"));
   }



   @Test
   void testAuthMode_inferredFromCredentials()
   {
      assertThat(baseConfig().getEffectiveAuthMode()).isEqualTo(QuickSearchAuthMode.NONE);
      assertThat(baseConfig().withOpensearchUsername("u").withOpensearchPassword("p").getEffectiveAuthMode()).isEqualTo(QuickSearchAuthMode.BASIC);
      assertThat(baseConfig().withAuthMode(QuickSearchAuthMode.AWS_SIGV4).getEffectiveAuthMode()).isEqualTo(QuickSearchAuthMode.AWS_SIGV4);
   }



   @Test
   void testBasic_overPlainHttp_isRefusedUnlessAllowed()
   {
      QuickSearchQBitConfig config = baseConfig().withOpensearchUsername("u").withOpensearchPassword("p");
      assertThat(validate(config)).anyMatch(e -> e.contains("plain http"));

      assertThat(validate(config.withAllowPlaintextCredentials(true))).noneMatch(e -> e.contains("plain http"));
      assertThat(validate(baseConfig().withOpensearchUsername("u").withOpensearchPassword("p").withUseSsl(true))).noneMatch(e -> e.contains("plain http"));
   }



   @Test
   void testBasic_explicitWithoutCredentials_addsError()
   {
      assertThat(validate(baseConfig().withAuthMode(QuickSearchAuthMode.BASIC).withUseSsl(true))).anyMatch(e -> e.contains("required for authMode BASIC"));
   }



   @Test
   void testNone_withCredentials_addsError()
   {
      assertThat(validate(baseConfig().withAuthMode(QuickSearchAuthMode.NONE).withOpensearchUsername("u").withOpensearchPassword("p"))).anyMatch(e -> e.contains("authMode is NONE"));
   }



   @Test
   void testSigV4_requirementsAndDefaults()
   {
      QuickSearchQBitConfig config = baseConfig().withAuthMode(QuickSearchAuthMode.AWS_SIGV4).withAwsRegion("us-east-1");
      List<String> errors = validate(config);
      assertThat(errors).anyMatch(e -> e.contains("requires an https connection"));
      assertThat(config.getEffectiveAwsServiceName()).isEqualTo("es");

      errors = validate(config.withUseSsl(true).withOpensearchUsername("u").withOpensearchPassword("p"));
      assertThat(errors).anyMatch(e -> e.contains("not used with authMode AWS_SIGV4"));

      errors = validate(baseConfig().withAuthMode(QuickSearchAuthMode.AWS_SIGV4).withUseSsl(true).withAwsRegion("us-east-1").withAwsServiceName("bogus"));
      assertThat(errors).anyMatch(e -> e.contains("es or aoss"));

      errors = validate(baseConfig().withAuthMode(QuickSearchAuthMode.AWS_SIGV4).withUseSsl(true).withAwsRegion("us-east-1"));
      assertThat(errors).isEmpty();
   }



   @Test
   void testTls_insecureSkipVerify_onlyForLoopbackUnlessOverridden()
   {
      QuickSearchQBitConfig loopback = baseConfig().withTls(new QuickSearchTlsConfig().withInsecureSkipVerify(true));
      assertThat(validate(loopback)).isEmpty();

      QuickSearchQBitConfig remote = baseConfig().withOpensearchHost("search.example.com").withTls(new QuickSearchTlsConfig().withInsecureSkipVerify(true));
      assertThat(validate(remote)).anyMatch(e -> e.contains("insecureSkipVerify"));

      remote.getTls().withAllowInsecureInProduction(true);
      assertThat(validate(remote)).noneMatch(e -> e.contains("insecureSkipVerify"));
   }



   @Test
   void testTls_caAndTrustStoreTogether_addsError()
   {
      QuickSearchQBitConfig config = baseConfig().withTls(new QuickSearchTlsConfig().withCaCertificatePath("/ca.pem").withTrustStorePath("/ts.p12"));
      assertThat(validate(config)).anyMatch(e -> e.contains("not both"));
   }



   @Test
   void testIndexName_rules()
   {
      assertThat(QuickSearchQBitConfig.isValidIndexName("app-search_v2.1+x")).isTrue();
      assertThat(QuickSearchQBitConfig.isValidIndexName("App")).isFalse();
      assertThat(QuickSearchQBitConfig.isValidIndexName("_hidden")).isFalse();
      assertThat(QuickSearchQBitConfig.isValidIndexName("has space")).isFalse();
      assertThat(QuickSearchQBitConfig.isValidIndexName("a/b")).isFalse();
      assertThat(QuickSearchQBitConfig.isValidIndexName("..")).isFalse();
      assertThat(QuickSearchQBitConfig.isValidIndexName("a".repeat(256))).isFalse();

      assertThat(validate(baseConfig().withOpensearchIndexName("Bad Name"))).anyMatch(e -> e.contains("opensearchIndexName must be lowercase"));
   }



   @Test
   void testFieldExistence_checkedAgainstInstanceTables()
   {
      QuickSearchQBitConfig config = baseConfig().withSearchableTables(null)
         .withSearchableTable(new SearchableTableConfig("customer", List.of(new SearchableFieldConfig("name"), new SearchableFieldConfig("nope"))).withBasepullTimestampField("updatedAt"));
      List<String> errors = validate(config);
      assertThat(errors).anyMatch(e -> e.contains("[nope] does not exist on table [customer]"));
      assertThat(errors).anyMatch(e -> e.contains("basepullTimestampField [updatedAt] does not exist"));

      QuickSearchQBitConfig ok = baseConfig().withSearchableTables(null)
         .withSearchableTable(new SearchableTableConfig("customer", List.of(new SearchableFieldConfig("name"))).withBasepullTimestampField(null));
      assertThat(validate(ok)).isEmpty();
   }



   @Test
   void testTableName_withColon_addsError()
   {
      assertThat(validate(baseConfig().withSearchableTable("weird:name", List.of(new SearchableFieldConfig("x"))))).anyMatch(e -> e.contains("must not contain ':'"));
   }



   @Test
   void testScheduling_schedulerMustExist_andCronNeedsScheduler()
   {
      QInstance qInstance = instanceWithBackendAndCustomer();
      qInstance.addScheduler(new SimpleSchedulerMetaData().withName("sched"));

      List<String> errors = new ArrayList<>();
      baseConfig().withSchedulerName("missing").validate(qInstance, errors);
      assertThat(errors).anyMatch(e -> e.contains("Scheduler not found: missing"));

      errors = new ArrayList<>();
      baseConfig().withSchedulerName("sched").withReconcileCronExpression("0 0 3 * * ?").validate(qInstance, errors);
      assertThat(errors).isEmpty();

      errors = new ArrayList<>();
      baseConfig().withReconcileCronExpression("0 0 3 * * ?").validate(qInstance, errors);
      assertThat(errors).anyMatch(e -> e.contains("requires schedulerName"));

      ///////////////////////////////////////////////////////////////////////
      // an instance without schedulers cannot be checked; no error raised  //
      ///////////////////////////////////////////////////////////////////////
      assertThat(validate(baseConfig().withSchedulerName("anything"))).isEmpty();
   }



   @Test
   void testNumbers_mustBePositive()
   {
      List<String> errors = validate(baseConfig()
         .withConnectTimeoutMillis(0)
         .withResponseTimeoutMillis(-1)
         .withMaxConnections(0)
         .withMaxSearchLimit(0)
         .withBasepullRepeatSeconds(0)
         .withBasepullOverlapSeconds(-5)
         .withRunHistoryRetentionDays(0)
         .withMaxFieldLength(0));
      assertThat(errors).anyMatch(e -> e.startsWith("connectTimeoutMillis"));
      assertThat(errors).anyMatch(e -> e.startsWith("responseTimeoutMillis"));
      assertThat(errors).anyMatch(e -> e.startsWith("maxConnections"));
      assertThat(errors).anyMatch(e -> e.startsWith("maxSearchLimit"));
      assertThat(errors).anyMatch(e -> e.startsWith("basepullRepeatSeconds"));
      assertThat(errors).anyMatch(e -> e.startsWith("basepullOverlapSeconds"));
      assertThat(errors).anyMatch(e -> e.startsWith("runHistoryRetentionDays"));
      assertThat(errors).anyMatch(e -> e.startsWith("maxFieldLength"));
   }



   @Test
   void testValidate_nullInstance_isSafe()
   {
      List<String> errors = new ArrayList<>();
      baseConfig().validate(null, errors);
      assertThat(errors).isEmpty();
   }



   @Test
   void testDeprecatedScheduledFlag_setsBothNewFlags()
   {
      @SuppressWarnings("deprecation")
      QuickSearchQBitConfig config = baseConfig().withEnableScheduledProcesses(false);
      assertThat(config.getEnableBasepullProcess()).isFalse();
      assertThat(config.getEnableMaintenanceProcesses()).isFalse();
      assertThat(config.getEnableScheduledProcesses()).isFalse();

      config.withEnableBasepullProcess(true);
      assertThat(config.getEnableScheduledProcesses()).isFalse();
   }



   @Test
   void testToString_redactsSecrets()
   {
      String s = baseConfig().withOpensearchUsername("admin").withOpensearchPassword("hunter2").toString();
      assertThat(s).doesNotContain("hunter2").doesNotContain("admin").contains("[redacted]");
   }



   @Test
   void testDefaults()
   {
      QuickSearchQBitConfig config = new QuickSearchQBitConfig();
      assertThat(config.getStartupMode()).isEqualTo(QuickSearchStartupMode.FAIL_FAST);
      assertThat(config.getEnableBasepullProcess()).isTrue();
      assertThat(config.getEnableMaintenanceProcesses()).isTrue();
      assertThat(config.getConnectTimeoutMillis()).isEqualTo(5000);
      assertThat(config.getResponseTimeoutMillis()).isEqualTo(60000);
      assertThat(config.getMaxSearchLimit()).isEqualTo(100);
      assertThat(config.getBasepullOverlapSeconds()).isEqualTo(300);
      assertThat(config.getAdminPermissionRules().getPermissionBaseName()).isEqualTo(QuickSearchQBitConfig.DEFAULT_ADMIN_PERMISSION_BASE_NAME);
      assertThat(config.getApplyRecordSecurityLocks()).isTrue();
      assertThat(config.getDefaultBackendNameForTables()).isNull();
      assertThat(config.withBackendName("memory").getDefaultBackendNameForTables()).isEqualTo("memory");
   }

}

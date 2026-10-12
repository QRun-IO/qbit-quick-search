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

package com.kingsrook.qbits.quicksearch.diagnostics;


import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import com.kingsrook.qqq.backend.core.actions.processes.RunProcessAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.RecordSearchAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QUserFacingException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.search.RecordSearchInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryRecordStore;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitContext;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitProducer;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.QuickSearchStartupMode;
import com.kingsrook.qbits.quicksearch.SearchableFieldConfig;
import com.kingsrook.qbits.quicksearch.actions.QuickSearchRecordSearchProvider;
import com.kingsrook.qbits.quicksearch.metadata.QuickSearchDiagnosticsProcessMetaDataHelper;
import com.kingsrook.qbits.quicksearch.model.QuickSearchDiagnosticEvent;
import com.kingsrook.qbits.quicksearch.model.QuickSearchIndex;
import com.kingsrook.qbits.quicksearch.model.QuickSearchValidationRun;
import com.kingsrook.qbits.quicksearch.processes.diagnostics.AbstractDiagnosticToolStep;
import com.kingsrook.qbits.quicksearch.processes.diagnostics.SearchPlaygroundStep;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** The diagnostic tools without a reachable OpenSearch (the client points at a
 ** closed port), plus their pure parts: rate limiting, HTML escaping, target
 ** parsing, redaction, the path diagnosis branches, event recording and its
 ** cap, provider failure events and counters, the widget, the playground's
 ** honest failure output and its form loop through RunProcessAction.
 *******************************************************************************/
class DiagnosticsOfflineTest
{
   private static final String BACKEND   = "memory";
   private static final String PRODUCT   = "product";
   private static final String NOTE      = "note";
   private static final String NO_SEARCH = "plain";
   private static final String LOCKED    = "secret";

   private QuickSearchQBitConfig config;
   private QuickSearchRuntime    runtime;



   /*******************************************************************************
    ** An instance with an indexed product table, an unindexed note table with
    ** core searchFields, a table without searchFields, and a permissioned
    ** table; Quick Search produced in DEGRADED mode against a closed port.
    *******************************************************************************/
   @BeforeEach
   void setUp() throws QException
   {
      MemoryRecordStore.getInstance().reset();

      QInstance qInstance = new QInstance();
      qInstance.addBackend(new QBackendMetaData().withName(BACKEND).withBackendType(MemoryBackendModule.class));
      qInstance.withInstanceDefaultAuthentication(new QAuthenticationMetaData().withName("anonymous").withType(QAuthenticationType.FULLY_ANONYMOUS));
      qInstance.addTable(table(PRODUCT));
      qInstance.addTable(table(NOTE).withSearchFields("name"));
      qInstance.addTable(table(NO_SEARCH));
      qInstance.addTable(table(LOCKED).withSearchFields("name").withPermissionRules(new QPermissionRules().withLevel(PermissionLevel.READ_WRITE_PERMISSIONS)));
      QContext.init(qInstance, new QSession());

      config = new QuickSearchQBitConfig()
         .withStartupMode(QuickSearchStartupMode.DEGRADED)
         .withBackendName(BACKEND)
         .withOpensearchHost("127.0.0.1")
         .withOpensearchPort(1)
         .withConnectTimeoutMillis(500)
         .withResponseTimeoutMillis(1000)
         .withRecordSearchTimeoutMillis(1000)
         .withOpensearchIndexName("diag-offline")
         .withEnableRealTimeIndexing(false)
         .withDiagnosticRunsPerMinute(1000)
         .withSearchableTable(PRODUCT, List.of(new SearchableFieldConfig("name")));
      new QuickSearchQBitProducer().withConfig(config).produce(qInstance);
      runtime = config.getRuntime();

      new InsertAction().execute(new InsertInput(PRODUCT).withRecords(List.of(
         new QRecord().withValue("id", 1).withValue("name", "Blue Widget").withValue("modifyDate", Instant.now()),
         new QRecord().withValue("id", 2).withValue("name", "Red Gadget").withValue("modifyDate", Instant.now()))));
   }



   /*******************************************************************************
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      if(runtime != null)
      {
         runtime.close();
      }
      QContext.clear();
      QuickSearchQBitContext.clear();
      MemoryRecordStore.getInstance().reset();
   }



   /*******************************************************************************
    *******************************************************************************/
   private static QTableMetaData table(String name)
   {
      return (new QTableMetaData()
         .withName(name)
         .withLabel(name)
         .withBackendName(BACKEND)
         .withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("name", QFieldType.STRING))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME)));
   }



   /*******************************************************************************
    *******************************************************************************/
   private static List<QRecord> events() throws QException
   {
      QueryInput queryInput = new QueryInput(QuickSearchDiagnosticEvent.TABLE_NAME);
      return (new QueryAction().execute(queryInput).getRecords());
   }



   @Test
   void testRateLimiter_slidingMinute()
   {
      AtomicLong             now     = new AtomicLong(1_000_000L);
      DiagnosticsRateLimiter limiter = new DiagnosticsRateLimiter(now::get);

      assertThat(limiter.tryAcquire("a", 2)).isTrue();
      assertThat(limiter.tryAcquire("a", 2)).isTrue();
      assertThat(limiter.tryAcquire("a", 2)).isFalse();
      assertThat(limiter.tryAcquire("b", 2)).isTrue();

      now.addAndGet(60_000L);
      assertThat(limiter.tryAcquire("a", 2)).isTrue();
      assertThat(limiter.tryAcquire("a", null)).isTrue();
      assertThat(limiter.tryAcquire("a", 0)).isTrue();
   }



   @Test
   void testHtml_escapesAndKeepsHighlights()
   {
      assertThat(DiagnosticsHtml.escape("<script>alert('x')</script> & \"q\"")).isEqualTo("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; &quot;q&quot;");
      assertThat(DiagnosticsHtml.highlight("a <em>blue</em> <b>x</b>")).isEqualTo("a <mark>blue</mark> &lt;b&gt;x&lt;/b&gt;");
      assertThat(DiagnosticsHtml.prettyJson("{\"a\":1}")).contains("\"a\" : 1");
      assertThat(DiagnosticsHtml.prettyJson("not json")).isEqualTo("not json");
      assertThat(DiagnosticsHtml.prettyJson("\"" + "x".repeat(DiagnosticsHtml.MAX_JSON_CHARS + 10) + "\"")).contains("truncated at");

      String html = new DiagnosticsHtml().open().facts(java.util.Collections.singletonList(new String[] { "<k>", null })).pre("<v>").close().toString();
      assertThat(html).contains("&lt;k&gt;").contains("<em>none</em>").contains("&lt;v&gt;").contains("overflow-wrap:anywhere").doesNotContain("<k>");
   }



   @Test
   void testTargetParseAndTableLists()
   {
      assertThat(AbstractDiagnosticToolStep.Target.parse("blogPost:42")).isEqualTo(new AbstractDiagnosticToolStep.Target("blogPost", "42"));
      assertThat(AbstractDiagnosticToolStep.Target.parse("t:a:b")).isEqualTo(new AbstractDiagnosticToolStep.Target("t", "a:b"));
      assertThat(AbstractDiagnosticToolStep.Target.parse(":1")).isNull();
      assertThat(AbstractDiagnosticToolStep.Target.parse("t:")).isNull();
      assertThat(AbstractDiagnosticToolStep.Target.parse(null)).isNull();

      assertThat(SearchPlaygroundStep.parseTables(" a, b  c,a ")).containsExactly("a", "b", "c");
      assertThat(SearchPlaygroundStep.parseTables(null)).isEmpty();
   }



   @Test
   void testRedactResponse_hidesUnreadableHits()
   {
      String json = "{\"hits\":{\"hits\":[{\"_source\":{\"recordId\":\"1\",\"recordLabel\":\"ok\"},\"highlight\":{\"x\":[\"y\"]}},"
         + "{\"_source\":{\"recordId\":\"2\",\"recordLabel\":\"private\"},\"highlight\":{\"x\":[\"secret\"]}}]}}";
      String redacted = SearchPlayground.redactResponse(json, Set.of("1"));
      assertThat(redacted).contains("\"recordLabel\":\"ok\"").doesNotContain("private").doesNotContain("secret").doesNotContain("\"2\"").contains("redacted: 1 hit(s)");
      assertThat(SearchPlayground.redactResponse("nope", Set.of())).contains("could not be parsed");
   }



   /*******************************************************************************
    ** Each branch of the path diagnosis.
    *******************************************************************************/
   @Test
   void testPathDiagnosis_branches() throws QException
   {
      QInstance                           qInstance = QContext.getQInstance();
      List<SearchPathDiagnosis.TablePath> paths     = SearchPathDiagnosis.diagnose(qInstance, runtime, List.of(PRODUCT, NOTE, NO_SEARCH, LOCKED, "missing"), new QueryInput());

      assertThat(paths.get(0).path()).isEqualTo(SearchPathDiagnosis.PATH_OPENSEARCH);
      assertThat(paths.get(1).path()).isEqualTo(SearchPathDiagnosis.PATH_CORE);
      assertThat(paths.get(1).reason()).contains("does not index");
      assertThat(paths.get(2).path()).isEqualTo(SearchPathDiagnosis.PATH_NONE);
      assertThat(paths.get(2).reason()).contains("no core searchFields");
      assertThat(paths.get(3).path()).isEqualTo(SearchPathDiagnosis.PATH_NONE);
      assertThat(paths.get(3).reason()).contains("read permission");
      assertThat(paths.get(4).reason()).contains("no table");

      runtime.markRecordSearchFailed("connection refused");
      assertThat(SearchPathDiagnosis.diagnose(qInstance, runtime, List.of(PRODUCT), new QueryInput()).get(0).reason()).contains("backing off").contains("connection refused");
      runtime.clearRecordSearchBackoff();

      new InsertAction().execute(new InsertInput(QuickSearchIndex.TABLE_NAME).withRecord(new QRecord().withValue("tableName", PRODUCT).withValue("enabled", false)));
      runtime.invalidateEnabledCache();
      assertThat(SearchPathDiagnosis.diagnose(qInstance, runtime, List.of(PRODUCT), new QueryInput()).get(0).reason()).contains("disabled");
      runtime.invalidateEnabledCache();
      new UpdateAction().execute(new UpdateInput(QuickSearchIndex.TABLE_NAME).withRecord(new QRecord().withValue("id", 1).withValue("enabled", true)));
      runtime.invalidateEnabledCache();

      config.setServeCoreRecordSearch(false);
      assertThat(SearchPathDiagnosis.diagnose(qInstance, runtime, List.of(PRODUCT), new QueryInput()).get(0).reason()).contains("serveCoreRecordSearch is off");
      config.setServeCoreRecordSearch(true);

      qInstance.withRecordSearchProvider(new QCodeReference(DiagnosticsOfflineTest.class));
      assertThat(SearchPathDiagnosis.diagnose(qInstance, runtime, List.of(PRODUCT), new QueryInput()).get(0).reason()).contains("Another record search provider");
   }



   @Test
   void testRecorder_writesEventsAsSystemAndCapsVolume() throws QException
   {
      DiagnosticEventRecorder recorder = runtime.getDiagnosticEventRecorder();
      recorder.record(new QuickSearchDiagnosticEvent().withEventType(QuickSearchDiagnosticEvent.TYPE_TOOL_RUN).withSource("TEST").withSearchTerm("x".repeat(500)));
      recorder.flush(5000);

      List<QRecord> rows = events();
      assertThat(rows).hasSize(1);
      assertThat(rows.get(0).getValueString("hostName")).isNotBlank();
      assertThat(rows.get(0).getValueString("searchTerm")).hasSize(100);
      assertThat(rows.get(0).getValueInstant("createDate")).isNotNull();

      for(int i = 0; i < DiagnosticEventRecorder.MAX_EVENTS_PER_MINUTE + 5; i++)
      {
         recorder.record(new QuickSearchDiagnosticEvent().withEventType(QuickSearchDiagnosticEvent.TYPE_TOOL_RUN).withSource("FLOOD"));
      }
      recorder.flush(5000);
      assertThat(events()).hasSize(DiagnosticEventRecorder.MAX_EVENTS_PER_MINUTE);
      assertThat(recorder.getDroppedCount()).isEqualTo(6);
   }



   @Test
   void testRecorder_noTableIsANoOp()
   {
      QContext.getQInstance().getTables().remove(QuickSearchDiagnosticEvent.TABLE_NAME);
      DiagnosticEventRecorder recorder = runtime.getDiagnosticEventRecorder();
      recorder.record(new QuickSearchDiagnosticEvent().withEventType(QuickSearchDiagnosticEvent.TYPE_TOOL_RUN));
      recorder.flush(5000);
      assertThat(recorder.getFailedCount()).isZero();
   }



   @Test
   void testRateLimit_refusesAndRecords() throws QException
   {
      config.setDiagnosticRunsPerMinute(1);
      DiagnosticsSupport.checkRateLimit(runtime, DiagnosticsSupport.TOOL_PLAYGROUND);
      assertThatThrownBy(() -> DiagnosticsSupport.checkRateLimit(runtime, DiagnosticsSupport.TOOL_PLAYGROUND))
         .isInstanceOf(QUserFacingException.class).hasMessageContaining("Too many");
      runtime.getDiagnosticEventRecorder().flush(5000);
      assertThat(events()).anyMatch(r -> QuickSearchDiagnosticEvent.TYPE_RATE_LIMITED.equals(r.getValueString("eventType")));
   }



   /*******************************************************************************
    ** A global search with OpenSearch down: core falls back (results still
    ** come back), the provider counts and records the failure without the
    ** term, and claims during the backoff are counted.
    *******************************************************************************/
   @Test
   void testProviderFailure_recordedAndCounted() throws QException
   {
      List<?> results = new RecordSearchAction().execute(new RecordSearchInput().withSearchTerm("widget").withTableNames(List.of(PRODUCT))).getResults();
      assertThat(results).hasSize(1);

      assertThat(runtime.getRecordSearchFailureCount()).isEqualTo(1);
      assertThat(runtime.getLastRecordSearchFailureMessage()).isNotBlank();
      assertThat(runtime.getRecordSearchBackoffRemainingMillis()).isPositive();

      new RecordSearchAction().execute(new RecordSearchInput().withSearchTerm("widget").withTableNames(List.of(PRODUCT)));
      assertThat(runtime.getBackoffDeclinedClaimCount()).isEqualTo(1);

      runtime.getDiagnosticEventRecorder().flush(5000);
      QRecord event = events().stream().filter(r -> QuickSearchDiagnosticEvent.TYPE_SEARCH_FAILED.equals(r.getValueString("eventType"))).findFirst().orElseThrow();
      assertThat(event.getValueString("source")).isEqualTo(QuickSearchDiagnosticEvent.SOURCE_CORE_RECORD_SEARCH);
      assertThat(event.getValueString("searchTerm")).isNull();
      assertThat(event.getValueString("detail")).contains("term length 6");
      assertThat(event.getValueString("tableNames")).isEqualTo(PRODUCT);
      assertThat(event.getValueString("errorClass")).isNotBlank();
      assertThat(event.getValueString("errorMessage")).doesNotContainIgnoringCase("widget");
   }



   /*******************************************************************************
    ** QuickSearchAction's failure message names the term; the event never
    ** keeps it.
    *******************************************************************************/
   @Test
   void testQuickSearchActionFailure_termNeverStored() throws QException
   {
      assertThatThrownBy(() -> new com.kingsrook.qbits.quicksearch.actions.QuickSearchAction().execute(
         new com.kingsrook.qbits.quicksearch.actions.QuickSearchInput().withSearchTerm("Jane Doe-Smith")))
         .hasMessageContaining("Jane Doe-Smith");

      runtime.getDiagnosticEventRecorder().flush(5000);
      QRecord event = events().stream().filter(r -> QuickSearchDiagnosticEvent.SOURCE_QUICK_SEARCH_ACTION.equals(r.getValueString("source"))).findFirst().orElseThrow();
      assertThat(event.getValueString("searchTerm")).isNull();
      assertThat(event.getValueString("errorMessage")).contains("[search term]").doesNotContainIgnoringCase("jane").doesNotContainIgnoringCase("smith");
      assertThat(event.getValueString("detail")).contains("term length 14");
   }



   @Test
   void testRedactTerm()
   {
      assertThat(DiagnosticsSupport.redactTerm("Search failed for term [Blue Widget]: parse error near 'widget'", "blue widget"))
         .isEqualTo("Search failed for term [[search term]]: parse error near '[search term]'");
      assertThat(DiagnosticsSupport.redactTerm("no term here", "a b")).isEqualTo("no term here");
      assertThat(DiagnosticsSupport.redactTerm(null, "x")).isNull();
      assertThat(DiagnosticsSupport.redactTerm("x (y)", "(y)")).isEqualTo("x [search term]");
   }



   /*******************************************************************************
    ** The playground with OpenSearch down still reports honestly: the core
    ** fallback's results, the provider failure, and failed probes.
    *******************************************************************************/
   @Test
   void testPlayground_openSearchDown_reportsFailureAndFallback()
   {
      SearchPlayground.Report report = new SearchPlayground().run(runtime, new SearchPlayground.Input("widget", List.of(PRODUCT, NOTE), null, "product:1"), new QueryInput());

      assertThat(report.providerError()).isNotBlank();
      assertThat(report.coreError()).isNull();
      assertThat(report.coreResults().get(PRODUCT)).hasSize(1);
      assertThat(report.probes()).singleElement().satisfies(p -> assertThat(p.error()).isNotBlank());
      assertThat(report.termTokensError()).isNotBlank();
      assertThat(report.explainError()).isNotBlank();

      String html = SearchPlayground.render(report);
      assertThat(html).contains("FAIL").contains("falls back").contains("(#1)");
   }



   /*******************************************************************************
    ** The playground process through RunProcessAction: it stops at the form
    ** with intro HTML, and after a run the next step is the form again (with
    ** results), so the user can search again.
    *******************************************************************************/
   @Test
   void testPlaygroundProcess_loopsBackToTheForm() throws QException
   {
      String processName = QuickSearchDiagnosticsProcessMetaDataHelper.PLAYGROUND_PROCESS_NAME;

      RunProcessInput first = new RunProcessInput();
      first.setProcessName(processName);
      first.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.BREAK);
      RunProcessOutput firstOutput = new RunProcessAction().execute(first);
      assertThat(firstOutput.getProcessState().getNextStepName()).hasValue(AbstractDiagnosticToolStep.STEP_FORM);
      assertThat(firstOutput.getValues().get(AbstractDiagnosticToolStep.STEP_FORM + ".html").toString()).contains("Indexed tables").contains(PRODUCT);

      for(int run = 0; run < 2; run++)
      {
         RunProcessInput again = new RunProcessInput();
         again.setProcessName(processName);
         again.setProcessUUID(firstOutput.getProcessUUID());
         again.setStartAfterStep(AbstractDiagnosticToolStep.STEP_FORM);
         again.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.BREAK);
         again.addValue(SearchPlaygroundStep.FIELD_TERM, "widget");
         again.addValue(SearchPlaygroundStep.FIELD_TABLES, PRODUCT);
         RunProcessOutput output = new RunProcessAction().execute(again);
         assertThat(output.getProcessState().getNextStepName()).hasValue(AbstractDiagnosticToolStep.STEP_FORM);
         assertThat(output.getValues().get(AbstractDiagnosticToolStep.STEP_FORM + ".html").toString()).contains("Search playground").contains("widget");
      }

      runtime.getDiagnosticEventRecorder().flush(5000);
      assertThat(events()).filteredOn(r -> DiagnosticsSupport.TOOL_PLAYGROUND.equals(r.getValueString("source"))).hasSize(2);
   }



   /*******************************************************************************
    ** Validation with OpenSearch down: a FAIL report that says why, saved as
    ** a validation run.
    *******************************************************************************/
   @Test
   void testValidate_openSearchDown_savesFailReport() throws QException
   {
      IndexValidator        validator = new IndexValidator();
      IndexValidator.Report report    = validator.validate(runtime, List.of(), 10);
      assertThat(report.status()).isEqualTo(QuickSearchValidationRun.STATUS_FAIL);
      assertThat(report.findings()).anyMatch(f -> f.check().equals("Reachable") && f.message().contains("did not answer"));

      Integer runId = validator.persist(runtime, report, QuickSearchValidationRun.TRIGGER_MANUAL, "tester");
      assertThat(runId).isNotNull();
      QRecord row = new QueryAction().execute(new QueryInput(QuickSearchValidationRun.TABLE_NAME)).getRecords().get(0);
      assertThat(row.getValueString("reportHtml")).contains("did not answer");
      assertThat(row.getValueString("userName")).isEqualTo("tester");
      assertThat(IndexValidator.toJson(report)).contains("\"status\":\"FAIL\"");
      assertThat(row.getValueString("reportJson")).contains("\"status\":\"FAIL\"");
   }



   /*******************************************************************************
    ** The widget says plainly what it knows: the backoff, the recorded
    ** failure, and that no validation ran yet; then the latest validation.
    *******************************************************************************/
   @Test
   void testWidget_honestStates() throws QException
   {
      String empty = QuickSearchDiagnosticsWidgetRenderer.renderHtml(runtime);
      assertThat(empty).contains("Served by OpenSearch").contains("None recorded").contains("None yet");

      new RecordSearchAction().execute(new RecordSearchInput().withSearchTerm("widget").withTableNames(List.of(PRODUCT)));
      runtime.getDiagnosticEventRecorder().flush(5000);
      new InsertAction().execute(new InsertInput(QuickSearchValidationRun.TABLE_NAME).withRecordEntity(new QuickSearchValidationRun()
         .withStatus(QuickSearchValidationRun.STATUS_WARN).withTriggerType(QuickSearchValidationRun.TRIGGER_MANUAL).withSummary("WARN: 1 table(s)").withStartTime(Instant.now())));

      String html = QuickSearchDiagnosticsWidgetRenderer.renderHtml(runtime);
      assertThat(html).contains("Backing off").contains(QuickSearchDiagnosticEvent.SOURCE_CORE_RECORD_SEARCH).contains("WARN: 1 table(s)")
         .contains("/app/" + QuickSearchValidationRun.TABLE_NAME + "/1");

      assertThat(new QuickSearchRecordSearchProvider().claimsTable(QContext.getQInstance().getTable(PRODUCT))).isFalse();
   }

}

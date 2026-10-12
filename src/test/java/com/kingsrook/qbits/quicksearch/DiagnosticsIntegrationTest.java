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


import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.processes.RunProcessAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryRecordStore;
import com.kingsrook.qbits.quicksearch.diagnostics.AnalyzerTester;
import com.kingsrook.qbits.quicksearch.diagnostics.DiagnosticsHtml.Check;
import com.kingsrook.qbits.quicksearch.diagnostics.DiagnosticsHtml.Level;
import com.kingsrook.qbits.quicksearch.diagnostics.IndexValidator;
import com.kingsrook.qbits.quicksearch.diagnostics.RecordInspector;
import com.kingsrook.qbits.quicksearch.diagnostics.RecordReindexer;
import com.kingsrook.qbits.quicksearch.diagnostics.SearchPathDiagnosis;
import com.kingsrook.qbits.quicksearch.diagnostics.SearchPlayground;
import com.kingsrook.qbits.quicksearch.metadata.QuickSearchDiagnosticsProcessMetaDataHelper;
import com.kingsrook.qbits.quicksearch.model.QuickSearchValidationRun;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchDiagnosticsOperations;
import com.kingsrook.qbits.quicksearch.processes.FullReindexStep;
import com.kingsrook.qbits.quicksearch.processes.diagnostics.AbstractDiagnosticToolStep;
import com.kingsrook.qbits.quicksearch.processes.diagnostics.InspectRecordStep;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The diagnostic tools against a real OpenSearch (Testcontainers): the
 ** playground (paths, provider ids, global search results, scores, matched
 ** clauses, highlights, query DSL, explain, term tokens), the analyzer
 ** tester, record inspection (current, stale, missing, and why a term does
 ** or does not match), index validation (counts, missing, stale and orphaned
 ** documents, mapping, analyzers, leftover physical index, the saved
 ** report), reindexing records, and the tool processes end to end.
 ** Real-time indexing is off so the tests can make the index drift.
 *******************************************************************************/
@ExtendWith(RequiresDockerCondition.class)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DiagnosticsIntegrationTest
{
   private static final String BACKEND = "memory";
   private static final String PRODUCT = "diagProduct";
   private static final String INDEX   = "quick_search_diagnostics_it";

   @Container
   static GenericContainer<?> opensearch = new GenericContainer<>(System.getProperty("opensearch.test.image", "opensearchproject/opensearch:2.19.6"))
      .withExposedPorts(9200)
      .withEnv("discovery.type", "single-node")
      .withEnv("DISABLE_SECURITY_PLUGIN", "true")
      .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
      .waitingFor(Wait.forHttp("/_cluster/health").forStatusCode(200));

   private static QuickSearchQBitConfig config;
   private static QuickSearchRuntime    runtime;



   /*******************************************************************************
    ** Instance with one indexed product table; four products indexed.
    *******************************************************************************/
   @BeforeAll
   static void setUpAll() throws Exception
   {
      MemoryRecordStore.getInstance().reset();

      QInstance qInstance = new QInstance();
      qInstance.addBackend(new QBackendMetaData().withName(BACKEND).withBackendType(MemoryBackendModule.class));
      qInstance.withInstanceDefaultAuthentication(new QAuthenticationMetaData().withName("anonymous").withType(QAuthenticationType.FULLY_ANONYMOUS));
      qInstance.addTable(new QTableMetaData()
         .withName(PRODUCT)
         .withLabel("Product")
         .withBackendName(BACKEND)
         .withPrimaryKeyField("id")
         .withRecordLabelFormat("%s")
         .withRecordLabelFields("name")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("name", QFieldType.STRING))
         .withField(new QFieldMetaData("description", QFieldType.STRING))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME)));

      config = new QuickSearchQBitConfig()
         .withBackendName(BACKEND)
         .withOpensearchHost(opensearch.getHost())
         .withOpensearchPort(opensearch.getMappedPort(9200))
         .withOpensearchIndexName(INDEX)
         .withEnableRealTimeIndexing(false)
         .withDiagnosticRunsPerMinute(1000)
         .withSearchableTable(new SearchableTableConfig(PRODUCT, List.of(
            new SearchableFieldConfig("name").withWeight(3),
            new SearchableFieldConfig("description"))));

      new QuickSearchQBitProducer().withConfig(config).produce(qInstance);
      QContext.init(qInstance, new QSession());
      runtime = config.getRuntime();

      new InsertAction().execute(new InsertInput(PRODUCT).withRecords(List.of(
         product(1, "Blue Widget", "the original"),
         product(2, "Widget", "comes in blue and green"),
         product(3, "Red Widget", "not that colour"),
         product(4, "Gadget", "no match here"))));

      new FullReindexStep().run(new RunBackendStepInput(), new RunBackendStepOutput());
      runtime.getClient().refreshIndex();
   }



   /*******************************************************************************
    *******************************************************************************/
   @AfterAll
   static void tearDownAll()
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
   private static QRecord product(int id, String name, String description)
   {
      return (new QRecord().withValue("id", id).withValue("name", name).withValue("description", description).withValue("modifyDate", Instant.now()));
   }



   /*******************************************************************************
    *******************************************************************************/
   private static QuickSearchDiagnosticsOperations ops() throws Exception
   {
      return (new QuickSearchDiagnosticsOperations(runtime.getClient()));
   }



   /*******************************************************************************
    *******************************************************************************/
   private static Check check(List<Check> checks, String name)
   {
      return (checks.stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow(() -> new AssertionError("no check " + name + " in " + checks)));
   }



   @Test
   @Order(1)
   void testPlayground_fullPicture() throws Exception
   {
      SearchPlayground.Report report = new SearchPlayground().run(runtime, new SearchPlayground.Input("blue widget", List.of(PRODUCT), 5, PRODUCT + ":1"), new QueryInput());

      assertThat(report.paths()).singleElement().satisfies(p -> assertThat(p.path()).isEqualTo(SearchPathDiagnosis.PATH_OPENSEARCH));
      assertThat(report.providerError()).isNull();
      assertThat(report.providerIds().get(PRODUCT)).containsExactlyInAnyOrder("1", "2");
      assertThat(report.coreError()).isNull();
      assertThat(report.coreResults().get(PRODUCT)).extracting(r -> String.valueOf(r.getRecordId())).containsExactlyInAnyOrder("1", "2");
      assertThat(report.termTokens()).extracting(QuickSearchDiagnosticsOperations.AnalyzedToken::token).containsExactly("blue", "widget");

      SearchPlayground.TableProbe probe = report.probes().get(0);
      assertThat(probe.error()).isNull();
      assertThat(probe.probe().hits()).hasSize(2);
      QuickSearchDiagnosticsOperations.ProbeHit top = probe.probe().hits().get(0);
      assertThat(top.recordId()).isEqualTo("1");
      assertThat(top.matchedClauses()).contains("searchableText", "field:name");
      assertThat(top.highlights().values().stream().flatMap(List::stream).toList()).anyMatch(f -> f.contains("<em>"));
      assertThat(probe.readableIds()).contains("1", "2");
      assertThat(probe.shownIds()).contains("1", "2");
      assertThat(probe.probe().requestJson()).contains("\"_name\"").contains("searchableText");
      assertThat(probe.probe().responseJson()).contains("\"hits\"");

      assertThat(report.explain().documentFound()).isTrue();
      assertThat(report.explain().matched()).isTrue();
      assertThat(report.explain().score()).isPositive();
      assertThat(report.explain().explanationText()).isNotBlank();

      String html = SearchPlayground.render(report);
      assertThat(html).contains("Query DSL").contains("Raw response").contains("shown in global search").contains("<mark>").contains("Matched: searchableText, name");
   }



   @Test
   @Order(2)
   void testAnalyzer_tokensAndQueryMatch() throws Exception
   {
      AnalyzerTester.Result result = new AnalyzerTester().analyze(runtime, "Blue Widgets", "wid x");
      assertThat(result.indexTokens()).extracting(QuickSearchDiagnosticsOperations.AnalyzedToken::token).contains("bl", "blue", "wi", "wid", "widgets");
      assertThat(result.textAsQueryTokens()).extracting(QuickSearchDiagnosticsOperations.AnalyzedToken::token).containsExactly("blue", "widgets");
      assertThat(result.queryTokens()).extracting(QuickSearchDiagnosticsOperations.AnalyzedToken::token).containsExactly("wid", "x");

      String html = AnalyzerTester.render(result);
      assertThat(html).contains("never indexed (n-grams start at 2)").contains("At least one query token is missing");
      assertThat(AnalyzerTester.render(new AnalyzerTester().analyze(runtime, "Blue Widgets", "wid blu"))).contains("Every query token is found");
   }



   @Test
   @Order(3)
   void testInspect_currentRecordAndMatchReasons() throws Exception
   {
      RecordInspector.Inspection current = new RecordInspector().inspect(runtime, PRODUCT, "1", null, new QueryInput());
      assertThat(current.verdict()).isEqualTo(RecordInspector.VERDICT_CURRENT);
      assertThat(current.diffs()).allMatch(RecordInspector.FieldDiff::isSame);
      assertThat(current.indexed().version()).isNotNull();

      RecordInspector.Inspection matched = new RecordInspector().inspect(runtime, PRODUCT, "1", "blue", new QueryInput());
      assertThat(check(matched.matchChecks(), "Token \"blue\"").level()).isEqualTo(Level.OK);
      assertThat(check(matched.matchChecks(), "OpenSearch explain").level()).isEqualTo(Level.OK);
      assertThat(check(matched.matchChecks(), "Rank").level()).isEqualTo(Level.OK);

      RecordInspector.Inspection oneChar = new RecordInspector().inspect(runtime, PRODUCT, "1", "blue x", new QueryInput());
      assertThat(check(oneChar.matchChecks(), "Token \"x\"").message()).contains("One character");
      assertThat(check(oneChar.matchChecks(), "OpenSearch explain").level()).isEqualTo(Level.FAIL);

      RecordInspector.Inspection tooLong = new RecordInspector().inspect(runtime, PRODUCT, "1", "abcdefghijklmnopqrstuvwxy", new QueryInput());
      assertThat(check(tooLong.matchChecks(), "Token \"abcdefghijklmnopqrstuvwxy\"").message()).contains("Longer than 20");

      RecordInspector.Inspection absent = new RecordInspector().inspect(runtime, PRODUCT, "1", "gadget", new QueryInput());
      assertThat(check(absent.matchChecks(), "Token \"gadget\"").message()).contains("Not in this record");

      assertThat(RecordInspector.render(List.of(matched), "blue")).contains("Indexed and current").contains("Why does (or doesn&#39;t) this record match");

      RecordInspector.Inspection none = new RecordInspector().inspect(runtime, PRODUCT, "999", null, new QueryInput());
      assertThat(none.verdict()).isEqualTo(RecordInspector.VERDICT_NO_RECORD);
   }



   @Test
   @Order(4)
   void testInspect_staleAndMissing() throws Exception
   {
      new UpdateAction().execute(new UpdateInput(PRODUCT).withRecord(new QRecord().withValue("id", 3).withValue("name", "Crimson Widget").withValue("modifyDate", Instant.now())));
      new InsertAction().execute(new InsertInput(PRODUCT).withRecord(product(5, "Purple Widget", "brand new")));

      RecordInspector.Inspection stale = new RecordInspector().inspect(runtime, PRODUCT, "3", "crimson", new QueryInput());
      assertThat(stale.verdict()).isEqualTo(RecordInspector.VERDICT_STALE);
      assertThat(stale.diffs()).filteredOn(d -> !d.isSame()).extracting(RecordInspector.FieldDiff::name).contains("fieldValues.name", "searchableText", "recordLabel");
      assertThat(check(stale.matchChecks(), "Token \"crimson\"").level()).isEqualTo(Level.WARN);
      assertThat(check(stale.matchChecks(), "Indexed document").level()).isEqualTo(Level.WARN);

      RecordInspector.Inspection missing = new RecordInspector().inspect(runtime, PRODUCT, "5", "purple", new QueryInput());
      assertThat(missing.verdict()).isEqualTo(RecordInspector.VERDICT_MISSING);
      assertThat(check(missing.matchChecks(), "Indexed document").level()).isEqualTo(Level.FAIL);
   }



   @Test
   @Order(5)
   void testValidate_findsDriftOrphansAndLeftovers() throws Exception
   {
      runtime.getClient().indexDocuments(List.of(new OpenSearchDocument().withSourceTable(PRODUCT).withRecordId("99").withSearchableText("orphan")
         .withFieldValues(Map.of("name", "orphan")).withIndexedAt(Instant.now())), 10);
      runtime.getClient().refreshIndex();
      QuickSearchDiagnosticsOperations.RawResponse created = ops().raw("PUT", "/" + INDEX + "-v2-1", "{}");
      assertThat(created.isOk()).isTrue();

      try
      {
         IndexValidator         validator = new IndexValidator();
         IndexValidator.Report  report    = validator.validate(runtime, List.of(), 50);
         assertThat(report.status()).isEqualTo(QuickSearchValidationRun.STATUS_FAIL);

         IndexValidator.TableStats stats = report.tableStats().get(0);
         assertThat(stats.databaseCount()).isEqualTo(5);
         assertThat(stats.indexCount()).isEqualTo(5L);
         assertThat(stats.missingIds()).containsExactly("5");
         assertThat(stats.staleFields()).containsKey("3");
         assertThat(stats.extraIds()).containsExactly("99");

         assertThat(report.findings()).anyMatch(f -> f.check().equals("Mapping version") && f.level() == Level.OK);
         assertThat(report.findings()).anyMatch(f -> f.check().equals("Field mappings") && f.level() == Level.OK);
         assertThat(report.findings()).anyMatch(f -> f.check().equals("Analysis settings") && f.level() == Level.OK);
         assertThat(report.findings()).anyMatch(f -> f.check().equals("Alias") && f.level() == Level.OK);
         assertThat(report.findings()).anyMatch(f -> f.check().equals("Leftover index") && f.message().contains(INDEX + "-v2-1"));
         assertThat(report.findings()).anyMatch(f -> f.check().equals("Sampled documents") && f.level() == Level.FAIL && f.message().contains("99"));

         Integer runId = validator.persist(runtime, report, QuickSearchValidationRun.TRIGGER_MANUAL, "tester");
         assertThat(runId).isNotNull();
         QRecord row = new QueryAction().execute(new QueryInput(QuickSearchValidationRun.TABLE_NAME)).getRecords().get(0);
         assertThat(row.getValueString("status")).isEqualTo(QuickSearchValidationRun.STATUS_FAIL);
         assertThat(row.getValueString("reportHtml")).contains("Sampled records").contains("Stale fields in " + PRODUCT);
         assertThat(row.getValueString("reportJson")).contains("\"missingIds\":[\"5\"]");
         assertThat(row.getValueInteger("failCount")).isPositive();
      }
      finally
      {
         ops().raw("DELETE", "/" + INDEX + "-v2-1", null);
      }
   }



   @Test
   @Order(6)
   void testReindex_fixesStaleMissingAndOrphan() throws Exception
   {
      List<RecordReindexer.Outcome> outcomes = new RecordReindexer().reindex(runtime, PRODUCT, List.of("3", "5", "99"));
      assertThat(outcomes).extracting(RecordReindexer.Outcome::outcome)
         .containsExactly(RecordReindexer.OUTCOME_INDEXED, RecordReindexer.OUTCOME_INDEXED, RecordReindexer.OUTCOME_DELETED);

      assertThat(new RecordInspector().inspect(runtime, PRODUCT, "3", null, new QueryInput()).verdict()).isEqualTo(RecordInspector.VERDICT_CURRENT);
      assertThat(new RecordInspector().inspect(runtime, PRODUCT, "5", null, new QueryInput()).verdict()).isEqualTo(RecordInspector.VERDICT_CURRENT);
      assertThat(ops().getDocument(PRODUCT, "99").found()).isFalse();

      IndexValidator.Report report = new IndexValidator().validate(runtime, List.of(PRODUCT), 50);
      assertThat(report.findings()).noneMatch(f -> f.level() == Level.FAIL);
      assertThat(report.status()).isIn(QuickSearchValidationRun.STATUS_PASS, QuickSearchValidationRun.STATUS_WARN);
   }



   /*******************************************************************************
    ** The record actions and the validation as processes, through
    ** RunProcessAction (what the UI calls).
    *******************************************************************************/
   @Test
   @Order(7)
   void testProcesses_endToEnd() throws Exception
   {
      ///////////////////////////////////////////////////////////////////
      // inspect: stops at the form with the document already compared //
      ///////////////////////////////////////////////////////////////////
      RunProcessInput inspect = new RunProcessInput();
      inspect.setProcessName(QuickSearchDiagnosticsProcessMetaDataHelper.inspectProcessName(config, PRODUCT));
      inspect.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.BREAK);
      inspect.addValue("recordIds", "1");
      RunProcessOutput inspected = new RunProcessAction().execute(inspect);
      assertThat(inspected.getProcessState().getNextStepName()).hasValue(AbstractDiagnosticToolStep.STEP_FORM);
      assertThat(inspected.getValues().get("form.html").toString()).contains("Indexed and current");

      RunProcessInput inspectTerm = new RunProcessInput();
      inspectTerm.setProcessName(inspect.getProcessName());
      inspectTerm.setProcessUUID(inspected.getProcessUUID());
      inspectTerm.setStartAfterStep(AbstractDiagnosticToolStep.STEP_FORM);
      inspectTerm.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.BREAK);
      inspectTerm.addValue(InspectRecordStep.FIELD_TERM, "gadget");
      RunProcessOutput withTerm = new RunProcessAction().execute(inspectTerm);
      assertThat(withTerm.getProcessState().getNextStepName()).hasValue(AbstractDiagnosticToolStep.STEP_FORM);
      assertThat(withTerm.getValues().get("form.html").toString()).contains("Not in this record");

      ////////////////////////////////////////
      // reindex: confirm, then the outcome //
      ////////////////////////////////////////
      RunProcessInput reindex = new RunProcessInput();
      reindex.setProcessName(QuickSearchDiagnosticsProcessMetaDataHelper.reindexRecordsProcessName(config, PRODUCT));
      reindex.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.BREAK);
      reindex.addValue("recordIds", "2");
      RunProcessOutput confirm = new RunProcessAction().execute(reindex);
      assertThat(confirm.getProcessState().getNextStepName()).hasValue(AbstractDiagnosticToolStep.STEP_CONFIRM);
      assertThat(confirm.getValues().get("confirm.html").toString()).contains("1 record(s) of " + PRODUCT);

      RunProcessInput reindexRun = new RunProcessInput();
      reindexRun.setProcessName(reindex.getProcessName());
      reindexRun.setProcessUUID(confirm.getProcessUUID());
      reindexRun.setStartAfterStep(AbstractDiagnosticToolStep.STEP_CONFIRM);
      reindexRun.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.BREAK);
      RunProcessOutput reindexed = new RunProcessAction().execute(reindexRun);
      assertThat(reindexed.getValues().get("result.html").toString()).contains("1 reindexed");

      /////////////////////////////////////////////////////////////
      // validation as the scheduler runs it (frontend skipped) //
      /////////////////////////////////////////////////////////////
      RunProcessInput validate = new RunProcessInput();
      validate.setProcessName(QuickSearchDiagnosticsProcessMetaDataHelper.VALIDATE_PROCESS_NAME);
      validate.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.SKIP);
      RunProcessOutput validated = new RunProcessAction().execute(validate);
      assertThat(validated.getValues().get("validationRunId")).isNotNull();
      List<QRecord> runs = new QueryAction().execute(new QueryInput(QuickSearchValidationRun.TABLE_NAME)).getRecords();
      assertThat(runs).anyMatch(r -> QuickSearchValidationRun.TRIGGER_SCHEDULED.equals(r.getValueString("triggerType")));
   }

}

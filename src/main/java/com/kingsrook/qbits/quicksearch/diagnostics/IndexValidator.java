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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.diagnostics.DiagnosticsHtml.Level;
import com.kingsrook.qbits.quicksearch.model.QuickSearchFailedEvent;
import com.kingsrook.qbits.quicksearch.model.QuickSearchValidationRun;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchDiagnosticsOperations;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchDiagnosticsOperations.RawResponse;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import com.kingsrook.qbits.quicksearch.processes.AbstractIndexingStep;
import com.kingsrook.qbits.quicksearch.processes.IndexingUtils;
import com.kingsrook.qqq.backend.core.actions.tables.CountAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.CapturedContext;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.count.CountInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.session.QSystemUserSession;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Validate the Quick Search index against the database and its expected
 ** shape, and report every finding with a level (OK, INFO, WARN, FAIL):
 ** - cluster: reachable, cluster and index health, the alias pointing at one
 **   physical index, leftover physical indexes from interrupted rebuilds;
 ** - mapping and settings: mapping version, field types and analyzers of the
 **   fixed fields, the fieldValues dynamic template and every fieldValues.*
 **   field mapped as text, the two analyzers and the edge n-gram filter;
 ** - per table: database rows vs indexed documents, a sample of records
 **   (the lowest and highest primary keys) checked present and current in
 **   the index, a random sample of documents checked to still have a record,
 **   the quick_search_index row's status and last run, failed events waiting.
 ** Database reads run as the system user, so counts compare like with like
 ** (the index holds every row whatever the record locks). The report holds
 ** counts, ids and field names, never record values. Read-only, except that
 ** persist() saves the report as a quick_search_validation_run row.
 *******************************************************************************/
public class IndexValidator
{
   private static final QLogger LOG = QLogger.getLogger(IndexValidator.class);

   private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

   public static final String SCOPE_CLUSTER = "cluster";
   public static final String SCOPE_MAPPING = "mapping";

   private static final int MAX_LISTED_IDS = 25;



   /*******************************************************************************
    ** One finding.
    *******************************************************************************/
   public record Finding(String scope, String check, Level level, String message)
   {
   }



   /*******************************************************************************
    ** Per-table numbers.
    *******************************************************************************/
   public record TableStats(String tableName, Integer databaseCount, Long indexCount, Integer sampled, List<String> missingIds, Map<String, List<String>> staleFields,
                            Integer documentsSampled, List<String> extraIds)
   {
   }



   /*******************************************************************************
    ** A validation report.
    *******************************************************************************/
   public record Report(String status, Instant startTime, Instant endTime, Integer sampleSize, List<String> tableNames, List<Finding> findings, List<TableStats> tableStats)
   {
      /*******************************************************************************
       ** Count of findings at a level.
       *******************************************************************************/
      public int count(Level level)
      {
         return ((int) findings.stream().filter(f -> f.level() == level).count());
      }
   }



   /*******************************************************************************
    ** Validate the named tables (empty for every indexed table) with the given
    ** sample size per table.
    *******************************************************************************/
   public Report validate(QuickSearchRuntime runtime, List<String> requestedTables, int sampleSize) throws QException
   {
      Instant          start     = Instant.now();
      List<Finding>    findings  = new ArrayList<>();
      List<TableStats> stats     = new ArrayList<>();
      List<String>     tables    = resolveTables(runtime, requestedTables, findings);
      int              boundedN  = Math.max(1, Math.min(sampleSize, QuickSearchQBitConfig.MAX_VALIDATION_SAMPLE_SIZE));
      QInstance        qInstance = QContext.getQInstance();

      QContext.withTemporaryContext(new CapturedContext(qInstance, new QSystemUserSession()), () ->
      {
         QuickSearchDiagnosticsOperations ops;
         try
         {
            ops = new QuickSearchDiagnosticsOperations(runtime.getClient());
         }
         catch(QException e)
         {
            findings.add(new Finding(SCOPE_CLUSTER, "Client", Level.FAIL, "The OpenSearch client could not be built: " + e.getMessage()));
            return;
         }

         if(!checkCluster(runtime, ops, findings))
         {
            return;
         }
         checkMapping(ops, findings);
         checkSettings(ops, findings);

         for(String tableName : tables)
         {
            stats.add(checkTable(runtime, ops, runtime.getTableConfig(tableName), boundedN, findings));
         }
      });

      String status = findings.stream().anyMatch(f -> f.level() == Level.FAIL) ? QuickSearchValidationRun.STATUS_FAIL
         : findings.stream().anyMatch(f -> f.level() == Level.WARN) ? QuickSearchValidationRun.STATUS_WARN : QuickSearchValidationRun.STATUS_PASS;
      return (new Report(status, start, Instant.now(), boundedN, tables, findings, stats));
   }



   /*******************************************************************************
    ** The requested indexed tables (unknown ones reported), else every indexed
    ** table.
    *******************************************************************************/
   private static List<String> resolveTables(QuickSearchRuntime runtime, List<String> requested, List<Finding> findings)
   {
      List<String> tables = new ArrayList<>();
      if(CollectionUtils.nullSafeIsEmpty(requested))
      {
         runtime.getDiscoveredTables().forEach(t -> tables.add(t.getTableName()));
         return (tables);
      }
      for(String tableName : requested)
      {
         if(runtime.getTableConfig(tableName) == null)
         {
            findings.add(new Finding(tableName, "Configured", Level.WARN, "Quick Search does not index this table; skipped."));
         }
         else if(!tables.contains(tableName))
         {
            tables.add(tableName);
         }
      }
      return (tables);
   }



   /*******************************************************************************
    ** Cluster health, alias and physical indexes. Returns false when the index
    ** cannot be reached (the other checks are then skipped).
    *******************************************************************************/
   boolean checkCluster(QuickSearchRuntime runtime, QuickSearchDiagnosticsOperations ops, List<Finding> findings)
   {
      RawResponse health;
      try
      {
         health = ops.clusterHealth();
      }
      catch(QException e)
      {
         findings.add(new Finding(SCOPE_CLUSTER, "Reachable", Level.FAIL, "OpenSearch did not answer: " + e.getMessage()));
         return (false);
      }

      if(health.status() == 404)
      {
         findings.add(new Finding(SCOPE_CLUSTER, "Index", Level.FAIL, "The index alias [" + ops.getIndexName() + "] does not exist. Run the full reindex to create it."));
         return (false);
      }
      if(!health.isOk())
      {
         findings.add(new Finding(SCOPE_CLUSTER, "Reachable", Level.FAIL, "Cluster health returned status " + health.status() + ": " + QuickSearchDiagnosticsOperations.errorReason(health.body())));
         return (false);
      }

      findings.add(new Finding(SCOPE_CLUSTER, "Reachable", Level.OK, "OpenSearch answered (" + health.body().path("cluster_name").asText("cluster") + ", "
         + health.body().path("number_of_nodes").asInt() + " node(s))."));
      findings.add(healthFinding("Cluster health", health.body().path("status").asText("unknown")));

      Iterator<Map.Entry<String, JsonNode>> indexes = health.body().path("indices").fields();
      while(indexes.hasNext())
      {
         Map.Entry<String, JsonNode> index = indexes.next();
         findings.add(healthFinding("Index health " + index.getKey(), index.getValue().path("status").asText("unknown")));
      }

      try
      {
         Set<String> physical = runtime.getClient().getPhysicalIndexes();
         if(physical.isEmpty())
         {
            findings.add(new Finding(SCOPE_CLUSTER, "Alias", Level.WARN, "[" + ops.getIndexName() + "] is a concrete index, not an alias (created before 1.0). The next full reindex replaces it with an alias."));
         }
         else if(physical.size() > 1)
         {
            findings.add(new Finding(SCOPE_CLUSTER, "Alias", Level.FAIL, "The alias points at " + physical.size() + " physical indexes " + physical + "; searches and counts see documents twice. Run the full reindex."));
         }
         else
         {
            findings.add(new Finding(SCOPE_CLUSTER, "Alias", Level.OK, "The alias points at " + physical.iterator().next() + "."));
         }

         RawResponse cat = ops.physicalIndexes();
         if(cat.isOk() && cat.body() != null)
         {
            for(JsonNode row : cat.body())
            {
               String name = row.path("index").asText();
               if(!physical.contains(name))
               {
                  findings.add(new Finding(SCOPE_CLUSTER, "Leftover index", Level.WARN, "Physical index " + name + " (" + row.path("docs.count").asText("?") + " docs, "
                     + row.path("store.size").asText("?") + ") is not behind the alias: left by an interrupted rebuild. Delete it to free space."));
               }
            }
         }
      }
      catch(QException e)
      {
         findings.add(new Finding(SCOPE_CLUSTER, "Alias", Level.FAIL, "Could not read the alias: " + e.getMessage()));
      }

      return (true);
   }



   /*******************************************************************************
    *******************************************************************************/
   private static Finding healthFinding(String check, String status)
   {
      return (switch(status)
      {
         case "green" -> new Finding(SCOPE_CLUSTER, check, Level.OK, "green.");
         case "yellow" -> new Finding(SCOPE_CLUSTER, check, Level.WARN, "yellow: replica shards are unassigned (expected on a single-node cluster when the index asks for replicas).");
         default -> new Finding(SCOPE_CLUSTER, check, Level.FAIL, status + ": primary shards are unassigned; searches and writes may fail.");
      });
   }



   /*******************************************************************************
    ** Shared cluster health interpretation for validation and the host status API.
    *******************************************************************************/
   public static Level healthLevel(String status)
   {
      return (healthFinding("Health", status).level());
   }



   /*******************************************************************************
    ** The live mapping against the mapping this version creates.
    *******************************************************************************/
   void checkMapping(QuickSearchDiagnosticsOperations ops, List<Finding> findings)
   {
      RawResponse response;
      try
      {
         response = ops.mapping();
         QuickSearchDiagnosticsOperations.requireOk(response, "Mapping read failed");
      }
      catch(QException e)
      {
         findings.add(new Finding(SCOPE_MAPPING, "Mapping", Level.FAIL, e.getMessage()));
         return;
      }

      Iterator<Map.Entry<String, JsonNode>> indexes = response.body().fields();
      while(indexes.hasNext())
      {
         Map.Entry<String, JsonNode> index    = indexes.next();
         JsonNode                    mappings = index.getValue().path("mappings");
         String                      prefix   = index.getKey() + ": ";

         int version = mappings.path("_meta").path(QuickSearchOpenSearchClient.META_MAPPING_VERSION).asInt(1);
         findings.add(version >= QuickSearchOpenSearchClient.MAPPING_VERSION
            ? new Finding(SCOPE_MAPPING, "Mapping version", Level.OK, prefix + "version " + version + ".")
            : new Finding(SCOPE_MAPPING, "Mapping version", Level.FAIL, prefix + "version " + version + ", this release writes " + QuickSearchOpenSearchClient.MAPPING_VERSION + ". Run the full reindex."));

         JsonNode properties = mappings.path("properties");
         List<String> problems = new ArrayList<>();
         expectType(properties, "sourceTable", "keyword", problems);
         expectType(properties, "recordId", "keyword", problems);
         expectType(properties, "indexedAt", "date", problems);
         expectText(properties.path("searchableText"), "searchableText", problems);
         expectText(properties.path("recordLabel"), "recordLabel", problems);

         boolean template = false;
         for(JsonNode entry : mappings.path("dynamic_templates"))
         {
            template |= entry.has("fieldValuesAsText");
         }
         if(!template)
         {
            problems.add("the fieldValuesAsText dynamic template is missing, so new fields may be mapped as numbers or dates");
         }
         if(mappings.path("date_detection").asBoolean(true) || mappings.path("numeric_detection").asBoolean(false))
         {
            problems.add("date or numeric detection is on");
         }

         Iterator<Map.Entry<String, JsonNode>> fields = properties.path("fieldValues").path("properties").fields();
         int                                   count  = 0;
         while(fields.hasNext())
         {
            Map.Entry<String, JsonNode> field = fields.next();
            expectText(field.getValue(), "fieldValues." + field.getKey(), problems);
            count++;
         }

         findings.add(problems.isEmpty()
            ? new Finding(SCOPE_MAPPING, "Field mappings", Level.OK, prefix + "fixed fields and " + count + " fieldValues field(s) mapped as expected.")
            : new Finding(SCOPE_MAPPING, "Field mappings", Level.FAIL, prefix + String.join("; ", problems) + ". Run the full reindex to rebuild the mapping."));
      }
   }



   /*******************************************************************************
    *******************************************************************************/
   private static void expectType(JsonNode properties, String field, String type, List<String> problems)
   {
      String actual = properties.path(field).path("type").asText(null);
      if(!type.equals(actual))
      {
         problems.add(field + " is " + (actual == null ? "not mapped" : "mapped as " + actual) + ", expected " + type);
      }
   }



   /*******************************************************************************
    *******************************************************************************/
   private static void expectText(JsonNode mapping, String field, List<String> problems)
   {
      String type     = mapping.path("type").asText(null);
      String analyzer = mapping.path("analyzer").asText(null);
      String search   = mapping.path("search_analyzer").asText(null);
      if(!"text".equals(type))
      {
         problems.add(field + " is " + (type == null ? "not mapped" : "mapped as " + type) + ", expected text");
      }
      else if(!QuickSearchOpenSearchClient.INDEX_ANALYZER.equals(analyzer) || !QuickSearchOpenSearchClient.SEARCH_ANALYZER.equals(search))
      {
         problems.add(field + " uses analyzer " + analyzer + " / search analyzer " + search + ", expected " + QuickSearchOpenSearchClient.INDEX_ANALYZER + " / " + QuickSearchOpenSearchClient.SEARCH_ANALYZER);
      }
   }



   /*******************************************************************************
    ** The live analysis settings against the ones this version creates.
    *******************************************************************************/
   void checkSettings(QuickSearchDiagnosticsOperations ops, List<Finding> findings)
   {
      RawResponse response;
      try
      {
         response = ops.settings();
         QuickSearchDiagnosticsOperations.requireOk(response, "Settings read failed");
      }
      catch(QException e)
      {
         findings.add(new Finding(SCOPE_MAPPING, "Analysis settings", Level.FAIL, e.getMessage()));
         return;
      }

      Iterator<Map.Entry<String, JsonNode>> indexes = response.body().fields();
      while(indexes.hasNext())
      {
         Map.Entry<String, JsonNode> index    = indexes.next();
         JsonNode                    settings = index.getValue().path("settings").path("index");
         JsonNode                    analysis = settings.path("analysis");
         List<String>                problems = new ArrayList<>();

         JsonNode indexAnalyzer  = analysis.path("analyzer").path(QuickSearchOpenSearchClient.INDEX_ANALYZER);
         JsonNode searchAnalyzer = analysis.path("analyzer").path(QuickSearchOpenSearchClient.SEARCH_ANALYZER);
         JsonNode ngram          = analysis.path("filter").path("edge_ngram_filter");

         if(!"standard".equals(indexAnalyzer.path("tokenizer").asText()) || !List.of("lowercase", "edge_ngram_filter").equals(textList(indexAnalyzer.path("filter"))))
         {
            problems.add(QuickSearchOpenSearchClient.INDEX_ANALYZER + " is " + (indexAnalyzer.isMissingNode() ? "missing" : indexAnalyzer.toString()));
         }
         if(!"standard".equals(searchAnalyzer.path("tokenizer").asText()) || !List.of("lowercase").equals(textList(searchAnalyzer.path("filter"))))
         {
            problems.add(QuickSearchOpenSearchClient.SEARCH_ANALYZER + " is " + (searchAnalyzer.isMissingNode() ? "missing" : searchAnalyzer.toString()));
         }
         if(!"edge_ngram".equals(ngram.path("type").asText()) || ngram.path("min_gram").asInt() != 2 || ngram.path("max_gram").asInt() != 20)
         {
            problems.add("edge_ngram_filter is " + (ngram.isMissingNode() ? "missing" : ngram.toString()));
         }

         findings.add(problems.isEmpty()
            ? new Finding(SCOPE_MAPPING, "Analysis settings", Level.OK, index.getKey() + ": analyzers and edge n-grams (2 to 20) as expected; "
            + settings.path("number_of_shards").asText("?") + " shard(s), " + settings.path("number_of_replicas").asText("?") + " replica(s).")
            : new Finding(SCOPE_MAPPING, "Analysis settings", Level.FAIL, index.getKey() + ": " + String.join("; ", problems) + ". Run the full reindex."));
      }
   }



   /*******************************************************************************
    *******************************************************************************/
   private static List<String> textList(JsonNode array)
   {
      List<String> values = new ArrayList<>();
      array.forEach(v -> values.add(v.asText()));
      return (values);
   }



   /*******************************************************************************
    ** Counts, sampled freshness and orphans of one table, plus its index row
    ** and failed events.
    *******************************************************************************/
   TableStats checkTable(QuickSearchRuntime runtime, QuickSearchDiagnosticsOperations ops, QuickSearchableTableConfig tableConfig, int sampleSize, List<Finding> findings)
   {
      String tableName = tableConfig.getTableName();
      Integer databaseCount = null;
      Long    indexCount    = null;
      try
      {
         databaseCount = DiagnosticRecordReads.count(tableName);
         indexCount    = runtime.getClient().countDocumentsForTable(tableName);
         long difference = indexCount - databaseCount;
         long tolerance  = Math.max(5, databaseCount / 100);
         Level level = difference == 0 ? Level.OK : (Math.abs(difference) <= tolerance ? Level.WARN : Level.FAIL);
         findings.add(new Finding(tableName, "Counts", level, "database " + databaseCount + ", index " + indexCount
            + (difference == 0 ? "." : " (" + (difference > 0 ? difference + " more in the index" : (-difference) + " missing from the index") + "). Reconcile fixes counts.")));
      }
      catch(QException e)
      {
         findings.add(new Finding(tableName, "Counts", Level.FAIL, e.getMessage()));
      }

      List<String>              missing     = new ArrayList<>();
      Map<String, List<String>> staleFields = new LinkedHashMap<>();
      int                       sampled     = 0;
      try
      {
         Map<String, QRecord> sample = new LinkedHashMap<>();
         for(QRecord record : DiagnosticRecordReads.loadEdge(tableName, true, (sampleSize + 1) / 2))
         {
            sample.put(record.getValueString(tableConfig.getPrimaryKeyField()), record);
         }
         for(QRecord record : DiagnosticRecordReads.loadEdge(tableName, false, sampleSize / 2))
         {
            sample.putIfAbsent(record.getValueString(tableConfig.getPrimaryKeyField()), record);
         }
         sampled = sample.size();

         Map<String, JsonNode> indexed = ops.getDocuments(tableName, sample.keySet());
         for(Map.Entry<String, QRecord> entry : sample.entrySet())
         {
            JsonNode source = indexed.get(entry.getKey());
            if(source == null)
            {
               missing.add(entry.getKey());
               continue;
            }
            List<String> differing = differingFields(tableConfig, IndexingUtils.buildDocument(entry.getValue(), tableConfig), source);
            if(!differing.isEmpty())
            {
               staleFields.put(entry.getKey(), differing);
            }
         }

         findings.add(missing.isEmpty() && staleFields.isEmpty()
            ? new Finding(tableName, "Sampled records", Level.OK, sampled + " record(s) checked: all indexed and current.")
            : new Finding(tableName, "Sampled records", Level.FAIL, sampled + " record(s) checked: " + missing.size() + " missing" + listIds(missing) + ", "
            + staleFields.size() + " stale" + listIds(new ArrayList<>(staleFields.keySet())) + ". Reindex those records, or run reconcile for the table."));
      }
      catch(QException e)
      {
         findings.add(new Finding(tableName, "Sampled records", Level.FAIL, e.getMessage()));
      }

      List<String> extra            = new ArrayList<>();
      int          documentsSampled = 0;
      try
      {
         List<String> documentIds = ops.sampleRecordIds(tableName, sampleSize);
         documentsSampled = documentIds.size();
         Set<String> existing = new HashSet<>(DiagnosticRecordReads.existingIds(tableName, documentIds));
         for(String recordId : documentIds)
         {
            if(!existing.contains(recordId))
            {
               extra.add(recordId);
            }
         }
         findings.add(extra.isEmpty()
            ? new Finding(tableName, "Sampled documents", Level.OK, documentsSampled + " random document(s) checked: every one has its record.")
            : new Finding(tableName, "Sampled documents", Level.FAIL, documentsSampled + " random document(s) checked: " + extra.size() + " have no record (orphans)"
            + listIds(extra) + ". Reconcile removes them."));
      }
      catch(QException e)
      {
         findings.add(new Finding(tableName, "Sampled documents", Level.FAIL, e.getMessage()));
      }

      checkIndexRow(runtime, tableName, findings);
      checkFailedEvents(runtime, tableName, findings);

      return (new TableStats(tableName, databaseCount, indexCount, sampled, missing, staleFields, documentsSampled, extra));
   }



   /*******************************************************************************
    ** Names of the values that differ between an expected document and an
    ** indexed source (recordLabel, searchableText, fieldValues.x).
    *******************************************************************************/
   static List<String> differingFields(QuickSearchableTableConfig tableConfig, OpenSearchDocument expected, JsonNode source)
   {
      return (RecordInspector.diff(tableConfig, expected, new QuickSearchDiagnosticsOperations.DocumentView(true, null, null, source)).stream()
         .filter(d -> !d.isSame())
         .map(RecordInspector.FieldDiff::name)
         .toList());
   }



   /*******************************************************************************
    *******************************************************************************/
   private static void checkIndexRow(QuickSearchRuntime runtime, String tableName, List<Finding> findings)
   {
      try
      {
         String indexTable = runtime.getConfig().getQuickSearchIndexTableName();
         if(QContext.getQInstance().getTable(indexTable) == null)
         {
            return;
         }
         QueryInput queryInput = new QueryInput();
         queryInput.setTableName(indexTable);
         queryInput.setFilter(new QQueryFilter(new QFilterCriteria("tableName", QCriteriaOperator.EQUALS, tableName)));
         List<QRecord> rows = CollectionUtils.nonNullList(new QueryAction().execute(queryInput).getRecords());
         if(rows.isEmpty())
         {
            findings.add(new Finding(tableName, "Index row", Level.WARN, "No Quick Search Index row yet: no basepull, reconcile or reindex has run for this table."));
            return;
         }

         QRecord row     = rows.get(0);
         String  status  = row.getValueString("status");
         String  lastRun = row.getValueString("lastRunStatus");
         if(Boolean.FALSE.equals(row.getValueBoolean("enabled")))
         {
            findings.add(new Finding(tableName, "Index row", Level.INFO, "Disabled: Quick Search neither indexes nor searches this table, so differences are expected."));
         }
         if(AbstractIndexingStep.STATUS_NEEDS_REINDEX.equals(status))
         {
            findings.add(new Finding(tableName, "Index row", Level.WARN, "Status NEEDS_REINDEX: the configured fields changed since the last reindex. Rebuild the table."));
         }
         else if(AbstractIndexingStep.STATUS_REBUILDING.equals(status))
         {
            findings.add(new Finding(tableName, "Index row", Level.WARN, "Status REBUILDING: a full reindex is running. If it was killed, basepull returns the table to ACTIVE once its run records show no sign of life for fullReindexStaleMinutes."));
         }
         if(AbstractIndexingStep.RUN_FAILED.equals(lastRun))
         {
            findings.add(new Finding(tableName, "Last run", Level.WARN, "The last indexing run failed: " + row.getValueString("lastErrorMessage")));
         }
      }
      catch(Exception e)
      {
         LOG.info("Could not read the Quick Search Index row during validation", e, logPair("tableName", tableName));
      }
   }



   /*******************************************************************************
    *******************************************************************************/
   private static void checkFailedEvents(QuickSearchRuntime runtime, String tableName, List<Finding> findings)
   {
      try
      {
         String failedTable = runtime.getConfig().applyTablePrefix(QuickSearchFailedEvent.TABLE_NAME);
         if(QContext.getQInstance().getTable(failedTable) == null)
         {
            return;
         }
         CountInput countInput = new CountInput();
         countInput.setTableName(failedTable);
         countInput.setFilter(new QQueryFilter(new QFilterCriteria("tableName", QCriteriaOperator.EQUALS, tableName),
            new QFilterCriteria("status", QCriteriaOperator.IN, List.of(QuickSearchFailedEvent.STATUS_PENDING, QuickSearchFailedEvent.STATUS_EXHAUSTED))));
         Integer count = new CountAction().execute(countInput).getCount();
         if(count != null && count > 0)
         {
            findings.add(new Finding(tableName, "Failed events", Level.WARN, count + " real-time index event(s) failed and wait for replay (or are exhausted). See Quick Search Failed Event."));
         }
      }
      catch(Exception e)
      {
         LOG.info("Could not count failed events during validation", e, logPair("tableName", tableName));
      }
   }



   /*******************************************************************************
    ** Save a report as a quick_search_validation_run row (as the system user);
    ** returns its id, or null when the table is not configured or the insert
    ** fails (logged).
    *******************************************************************************/
   public Integer persist(QuickSearchRuntime runtime, Report report, String triggerType, String userName)
   {
      String tableName = runtime.getConfig().applyTablePrefix(QuickSearchValidationRun.TABLE_NAME);
      if(QContext.getQInstance() == null || QContext.getQInstance().getTable(tableName) == null)
      {
         return (null);
      }

      QuickSearchValidationRun run = new QuickSearchValidationRun()
         .withStatus(report.status())
         .withTriggerType(triggerType)
         .withUserName(userName)
         .withTableNames(DiagnosticEventRecorder.truncate(String.join(",", report.tableNames()), 1000))
         .withSampleSize(report.sampleSize())
         .withTablesChecked(report.tableStats().size())
         .withFailCount(report.count(Level.FAIL))
         .withWarnCount(report.count(Level.WARN))
         .withSummary(DiagnosticEventRecorder.truncate(summary(report), 250))
         .withReportHtml(render(report))
         .withReportJson(toJson(report))
         .withStartTime(report.startTime())
         .withEndTime(report.endTime())
         .withCreateDate(Instant.now());

      Integer[] id = new Integer[1];
      try
      {
         QContext.withTemporaryContext(new CapturedContext(QContext.getQInstance(), new QSystemUserSession()), () ->
         {
            InsertOutput output = new InsertAction().execute(new InsertInput(tableName).withRecordEntity(run));
            id[0] = output.getRecords().get(0).getValueInteger("id");
         });
      }
      catch(Exception e)
      {
         LOG.warn("Could not save the Quick Search validation report", e);
      }
      return (id[0]);
   }



   /*******************************************************************************
    ** One line: status and counts.
    *******************************************************************************/
   public static String summary(Report report)
   {
      return (report.status() + ": " + report.tableStats().size() + " table(s), " + report.count(Level.FAIL) + " failed check(s), " + report.count(Level.WARN) + " warning(s).");
   }



   /*******************************************************************************
    ** The report as JSON.
    *******************************************************************************/
   public static String toJson(Report report)
   {
      try
      {
         return (MAPPER.writeValueAsString(report));
      }
      catch(Exception e)
      {
         return ("{\"error\":\"report could not be serialized\"}");
      }
   }



   /*******************************************************************************
    ** The report as HTML: summary, then the findings grouped by scope.
    *******************************************************************************/
   public static String render(Report report)
   {
      DiagnosticsHtml html = new DiagnosticsHtml().open();
      Level overall = switch(report.status())
      {
         case QuickSearchValidationRun.STATUS_PASS -> Level.OK;
         case QuickSearchValidationRun.STATUS_WARN -> Level.WARN;
         default -> Level.FAIL;
      };
      html.heading("Index validation");
      html.status(overall, summary(report));
      html.paragraph("Ran " + report.startTime() + " to " + report.endTime() + ", " + report.sampleSize() + " sampled record(s) and document(s) per table. Database reads ran as the system user.");

      Map<String, List<DiagnosticsHtml.Check>> byScope = new LinkedHashMap<>();
      for(Finding finding : report.findings())
      {
         byScope.computeIfAbsent(finding.scope(), k -> new ArrayList<>()).add(new DiagnosticsHtml.Check(finding.check(), finding.level(), finding.message()));
      }
      for(Map.Entry<String, List<DiagnosticsHtml.Check>> scope : byScope.entrySet())
      {
         String heading = switch(scope.getKey())
         {
            case SCOPE_CLUSTER -> "Cluster and index";
            case SCOPE_MAPPING -> "Mapping and analyzers";
            default -> "Table " + scope.getKey();
         };
         html.subheading(heading);
         html.checks(scope.getValue());
      }

      for(TableStats stats : report.tableStats())
      {
         if(!stats.staleFields().isEmpty())
         {
            List<String> items = new ArrayList<>();
            stats.staleFields().entrySet().stream().limit(MAX_LISTED_IDS).forEach(e ->
               items.add("#" + DiagnosticsHtml.escape(e.getKey()) + ": " + DiagnosticsHtml.escape(String.join(", ", e.getValue()))));
            html.subheading("Stale fields in " + stats.tableName());
            html.rawList(items);
         }
      }
      return (html.close().toString());
   }



   /*******************************************************************************
    *******************************************************************************/
   private static String listIds(List<String> ids)
   {
      if(ids.isEmpty())
      {
         return ("");
      }
      return (" (" + String.join(", ", ids.subList(0, Math.min(MAX_LISTED_IDS, ids.size()))) + (ids.size() > MAX_LISTED_IDS ? ", ..." : "") + ")");
   }

}

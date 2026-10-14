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


import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import com.kingsrook.qbits.quicksearch.annotations.QuickSearchField;
import com.kingsrook.qbits.quicksearch.annotations.QuickSearchable;
import com.kingsrook.qbits.quicksearch.metadata.QuickSearchHelp;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.help.QHelpContent;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryRecordStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Guardrail (owner requirement 2026-10-14: "all tables should be populated and
 ** the qbit should be fully using them"). For every quick_search_* table:
 ** - runtime code (not just its model or metadata) reads or writes it, so no
 **   table exists only on paper;
 ** - it is either populated by the startup primer (QuickSearchConfigPrimer; this
 **   qbit ships no DDL or changelog, so the primer is its seed mechanism) or
 **   listed in STARTS_EMPTY with why it starts empty;
 ** - it has bundle help that says what it is for and how its rows get created.
 ** A new table fails this test until it is wired, seeded or explained.
 *******************************************************************************/
class QuickSearchTablesGuardrailTest
{
   private static final Path MAIN = Path.of("src", "main", "java", "com", "kingsrook", "qbits", "quicksearch");

   /*******************************************************************************
    ** Tables the primer fills from code on startup (QuickSearchSeededDefaultsTest
    ** proves it).
    *******************************************************************************/
   static final Map<String, String> PRIMED = Map.of(
      "quick_search_index", "one row per table code declares searchable",
      "quick_search_index_field", "one row per searchable field code declares",
      "quick_search_settings", "the single settings row (id 1), with the priming summary");

   /*******************************************************************************
    ** Tables that legitimately start empty, and where their rows come from.
    *******************************************************************************/
   static final Map<String, String> STARTS_EMPTY = Map.of(
      "quick_search_config_tombstone", "an admin removes a table or field that code still declares",
      "quick_search_index_run", "runtime: every build, basepull and reconcile",
      "quick_search_failed_event", "runtime: a real-time index update fails (empty is healthy)",
      "quick_search_diagnostic_event", "runtime: a search fails or a diagnostic tool runs",
      "quick_search_validation_run", "runtime: someone runs Validate Search Index");



   /*******************************************************************************
    ** A searchable entity for the produced instance.
    *******************************************************************************/
   @QuickSearchable(tableName = "person")
   static class PersonEntity
   {
      @QuickSearchField(weight = 3)
      private String name;
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @BeforeEach
   void setUp()
   {
      MemoryRecordStore.getInstance().reset();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      QContext.clear();
   }



   /*******************************************************************************
    ** Every table is used by runtime code.
    *******************************************************************************/
   @Test
   void everyTableIsUsedAtRuntime() throws Exception
   {
      Map<String, String> entityByTable = entityByTable();
      List<String>        runtimeSource;
      try(Stream<Path> files = Files.walk(MAIN))
      {
         runtimeSource = files.filter(p -> p.toString().endsWith(".java"))
            .filter(p -> !p.startsWith(MAIN.resolve("model")) && !p.startsWith(MAIN.resolve("metadata")))
            .map(QuickSearchTablesGuardrailTest::read).toList();
      }
      for(String table : quickSearchTables())
      {
         String entity = entityByTable.get(table);
         assertThat(entity).as("entity class for " + table).isNotNull();

         //////////////////////////////////////////////////////////////////////////
         // the entity, or the config's name accessor for its table (index and   //
         // index run are reached by their prefixed names)                       //
         //////////////////////////////////////////////////////////////////////////
         Pattern use = Pattern.compile("\\b" + entity + "(\\.TABLE_NAME|\\.class|\\(|::)|\\bget" + entity + "TableName\\(");
         assertThat(runtimeSource.stream().anyMatch(source -> use.matcher(source).find()))
            .as(table + " (" + entity + ") must be read or written by runtime code, not only declared").isTrue();
      }
   }



   /*******************************************************************************
    ** Every table is primed or explained.
    *******************************************************************************/
   @Test
   void everyTableIsPrimedOrExplained()
   {
      QInstance qInstance = producedInstance();
      for(String table : quickSearchTables())
      {
         assertThat(PRIMED.containsKey(table) || STARTS_EMPTY.containsKey(table)).as(table + " must be primed or listed in STARTS_EMPTY").isTrue();
         assertThat(PRIMED.containsKey(table) && STARTS_EMPTY.containsKey(table)).as(table + " cannot be both primed and listed as starting empty").isFalse();
      }
      assertThat(PRIMED.keySet()).allMatch(table -> qInstance.getTable(table) != null, "PRIMED names only real tables");
      assertThat(STARTS_EMPTY.keySet()).allMatch(table -> qInstance.getTable(table) != null, "STARTS_EMPTY names only real tables");
   }



   /*******************************************************************************
    ** Every table and the app have bundle help that says how rows get created.
    *******************************************************************************/
   @Test
   void everyTableHasEmptyStateHelp() throws Exception
   {
      QInstance qInstance = producedInstance();
      assertThat(QuickSearchHelp.entries()).allSatisfy((key, entry) -> assertThat(entry.path("summary").asText().length()).as(key).isLessThanOrEqualTo(120));
      for(String table : quickSearchTables())
      {
         QTableMetaData     meta = qInstance.getTable(table);
         List<QHelpContent> help = meta.getHelpContent() == null ? null : meta.getHelpContent().get(QuickSearchHelp.SLOT);
         assertThat(help).as(table + " table help").isNotEmpty();
         assertThat(help.get(0).getContent()).as(table + " help says how rows get created").contains("How rows get created");
      }
      assertThat(qInstance.getApp("quickSearchAdmin").getHelpContent()).containsKey(QuickSearchHelp.SLOT);
      assertThat(QuickSearchHelp.entries().keySet()).allMatch(key -> !key.startsWith("table:") || qInstance.getTable(key.substring(6, key.indexOf(';'))) != null,
         "help names only real tables");
   }



   /*******************************************************************************
    ** Help a host already wrote is kept; the bundle only fills empty slots.
    *******************************************************************************/
   @Test
   void hostHelpIsNotOverridden() throws Exception
   {
      QInstance      qInstance = producedInstance();
      QTableMetaData table     = qInstance.getTable("quick_search_index");
      table.getHelpContent().remove(QuickSearchHelp.SLOT);
      table.withHelpContent(QuickSearchHelp.SLOT, new QHelpContent("host words"));

      QuickSearchHelp.apply(qInstance, new QuickSearchQBitConfig());

      assertThat(table.getHelpContent().get(QuickSearchHelp.SLOT).get(0).getContent()).isEqualTo("host words");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private List<String> quickSearchTables()
   {
      List<String> tables = producedInstance().getTables().values().stream().map(QTableMetaData::getName).filter(n -> n.startsWith("quick_search_")).sorted().toList();
      assertThat(tables).hasSize(8);
      return (tables);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private QInstance producedInstance()
   {
      try
      {
         QInstance qInstance = new QInstance();
         qInstance.addBackend(new QBackendMetaData().withName("memory").withBackendType(MemoryBackendModule.class));
         qInstance.withInstanceDefaultAuthentication(new QAuthenticationMetaData().withName("anonymous").withType(QAuthenticationType.FULLY_ANONYMOUS));
         qInstance.addTable(new QTableMetaData().withName("person").withBackendName("memory").withPrimaryKeyField("id")
            .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
            .withField(new QFieldMetaData("name", QFieldType.STRING))
            .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME)));
         QContext.init(qInstance, new QSession());
         new QuickSearchQBitProducer().withConfig(new QuickSearchQBitConfig()
            .withStartupMode(QuickSearchStartupMode.DEGRADED)
            .withBackendName("memory")
            .withOpensearchHost("localhost")
            .withOpensearchPort(9200)
            .withOpensearchIndexName("guardrail")
            .withSearchableEntityClasses(List.of(PersonEntity.class))).produce(qInstance);
         return (qInstance);
      }
      catch(QException e)
      {
         throw (new IllegalStateException(e));
      }
   }



   /*******************************************************************************
    ** model/X.java declares TABLE_NAME = "x".
    *******************************************************************************/
   private static Map<String, String> entityByTable() throws Exception
   {
      Map<String, String> rs = new HashMap<>();
      Pattern             p  = Pattern.compile("TABLE_NAME\\s*=\\s*\"([a-z_]+)\"");
      try(Stream<Path> files = Files.list(MAIN.resolve("model")))
      {
         for(Path file : files.toList())
         {
            Matcher m = p.matcher(read(file));
            if(m.find())
            {
               rs.put(m.group(1), file.getFileName().toString().replace(".java", ""));
            }
         }
      }
      return (rs);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String read(Path path)
   {
      try
      {
         return (Files.readString(path, StandardCharsets.UTF_8));
      }
      catch(Exception e)
      {
         throw (new IllegalStateException(e));
      }
   }

}

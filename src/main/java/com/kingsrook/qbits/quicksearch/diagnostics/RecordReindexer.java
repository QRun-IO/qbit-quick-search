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


import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qqq.backend.core.context.CapturedContext;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.session.QSystemUserSession;
import com.kingsrook.qbits.quicksearch.QuickSearchRuntime;
import com.kingsrook.qbits.quicksearch.QuickSearchableTableConfig;
import com.kingsrook.qbits.quicksearch.diagnostics.DiagnosticsHtml.Level;
import com.kingsrook.qbits.quicksearch.opensearch.BulkIndexResult;
import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchDocument;
import com.kingsrook.qbits.quicksearch.opensearch.QuickSearchOpenSearchClient;
import com.kingsrook.qbits.quicksearch.processes.IndexingUtils;


/*******************************************************************************
 ** Reindex chosen records of one table now: each record the user can read is
 ** rebuilt from the database and written to the index (versioned, so a newer
 ** document is never overwritten); a record that no longer exists has its
 ** document removed; a record that exists but that the user cannot read is
 ** skipped. The one diagnostic tool that writes to the index; it never
 ** changes a record. A disabled table is refused.
 *******************************************************************************/
public class RecordReindexer
{
   public static final String OUTCOME_INDEXED = "INDEXED";
   public static final String OUTCOME_DELETED = "DELETED";
   public static final String OUTCOME_SKIPPED = "SKIPPED";
   public static final String OUTCOME_FAILED  = "FAILED";



   /*******************************************************************************
    ** What happened to one record.
    *******************************************************************************/
   public record Outcome(String recordId, String outcome, String message)
   {
   }



   /*******************************************************************************
    ** Reindex the records of a table by id.
    *******************************************************************************/
   public List<Outcome> reindex(QuickSearchRuntime runtime, String tableName, List<String> recordIds) throws QException
   {
      QuickSearchableTableConfig tableConfig = runtime.getTableConfig(tableName);
      if(tableConfig == null)
      {
         throw (new QException("Quick Search does not index table [" + tableName + "]"));
      }
      if(!runtime.isTableEnabled(tableName))
      {
         throw (new QException("Table [" + tableName + "] is disabled in Quick Search Index; enable it before reindexing its records"));
      }

      Set<String>          ids      = new LinkedHashSet<>(recordIds);
      Map<String, QRecord> readable = DiagnosticRecordReads.loadForIndexing(tableName, ids);
      Set<String>          validIds = new HashSet<>();
      DiagnosticRecordReads.typedIds(QContext.getQInstance().getTable(tableName), ids).forEach(id -> validIds.add(String.valueOf(id)));

      ////////////////////////////////////////////////////////////////////
      // ids the user cannot read: exists at all (system view)? if not, //
      // the record is gone and its document should go too              //
      ////////////////////////////////////////////////////////////////////
      List<String> unreadable = ids.stream().filter(id -> !readable.containsKey(id)).toList();
      Set<String>  existing   = new HashSet<>();
      if(!unreadable.isEmpty())
      {
         List<String> found = new ArrayList<>();
         QContext.withTemporaryContext(new CapturedContext(QContext.getQInstance(), new QSystemUserSession()), () ->
            found.addAll(DiagnosticRecordReads.existingIds(tableName, unreadable)));
         existing.addAll(found);
      }

      QuickSearchOpenSearchClient client   = runtime.getClient();
      List<Outcome>               outcomes = new ArrayList<>();

      List<OpenSearchDocument> docs = new ArrayList<>();
      for(String id : ids)
      {
         QRecord record = readable.get(id);
         if(record != null)
         {
            OpenSearchDocument doc = IndexingUtils.buildDocument(record, tableConfig);
            if(doc != null)
            {
               docs.add(doc);
            }
         }
      }

      BulkIndexResult indexResult = client.indexDocuments(docs, Math.max(1, runtime.getConfig().getBulkBatchSize() == null ? 500 : runtime.getConfig().getBulkBatchSize()));
      Set<String>     failedIds   = new HashSet<>();
      for(String failure : indexResult.getErrors())
      {
         ////////////////////////////////////////////////////
         // failures read "table:recordId: reason"          //
         ////////////////////////////////////////////////////
         int    reasonStart = failure.indexOf(": ");
         String documentId  = reasonStart < 0 ? failure : failure.substring(0, reasonStart);
         failedIds.add(documentId.substring(documentId.indexOf(':') + 1));
      }

      List<String> toDelete = new ArrayList<>();
      for(String id : ids)
      {
         if(readable.containsKey(id))
         {
            outcomes.add(failedIds.contains(id) ? new Outcome(id, OUTCOME_FAILED, "OpenSearch rejected the document; see the run's errors")
               : new Outcome(id, OUTCOME_INDEXED, "Rebuilt from the database and written to the index."));
         }
         else if(!validIds.contains(id))
         {
            outcomes.add(new Outcome(id, OUTCOME_SKIPPED, "Not a valid id for this table."));
         }
         else if(existing.contains(id))
         {
            outcomes.add(new Outcome(id, OUTCOME_SKIPPED, "The record exists but you cannot read it, so it was not reindexed."));
         }
         else
         {
            toDelete.add(OpenSearchDocument.buildDocumentId(tableName, id));
            outcomes.add(new Outcome(id, OUTCOME_DELETED, "The record no longer exists; its document (if any) was removed."));
         }
      }

      if(!toDelete.isEmpty())
      {
         BulkIndexResult deleteResult = client.deleteDocuments(toDelete, 500);
         if(!deleteResult.getErrors().isEmpty())
         {
            throw (new QException("Removing documents of deleted records failed: " + String.join("; ", deleteResult.getErrors())));
         }
      }

      if(!indexResult.getErrors().isEmpty())
      {
         outcomes.add(new Outcome("-", OUTCOME_FAILED, "Index errors: " + String.join("; ", indexResult.getErrors())));
      }

      client.refreshIndex();
      return (outcomes);
   }



   /*******************************************************************************
    ** Render outcomes as HTML.
    *******************************************************************************/
   public static String render(String tableName, List<Outcome> outcomes)
   {
      DiagnosticsHtml html = new DiagnosticsHtml().open();
      html.heading("Reindex in search: " + tableName);
      long indexed = outcomes.stream().filter(o -> OUTCOME_INDEXED.equals(o.outcome())).count();
      long deleted = outcomes.stream().filter(o -> OUTCOME_DELETED.equals(o.outcome())).count();
      long skipped = outcomes.stream().filter(o -> OUTCOME_SKIPPED.equals(o.outcome())).count();
      long failed  = outcomes.stream().filter(o -> OUTCOME_FAILED.equals(o.outcome())).count();
      html.status(failed > 0 ? Level.FAIL : (skipped > 0 ? Level.WARN : Level.OK),
         indexed + " reindexed, " + deleted + " removed (record gone), " + skipped + " skipped, " + failed + " failed. The documents are searchable now.");

      List<String> items = new ArrayList<>();
      for(Outcome outcome : outcomes)
      {
         Level level = switch(outcome.outcome())
         {
            case OUTCOME_INDEXED, OUTCOME_DELETED -> Level.OK;
            case OUTCOME_SKIPPED -> Level.WARN;
            default -> Level.FAIL;
         };
         items.add(DiagnosticsHtml.badge(level) + " #" + DiagnosticsHtml.escape(outcome.recordId()) + ": " + DiagnosticsHtml.escape(outcome.message()));
      }
      html.rawList(items);
      return (html.close().toString());
   }

}

# TypeSafe (Jev) candidates for Quick Search

Captured 2026-10-04. Not in 1.0 scope (see `docs/initiatives/quick-search-1.0.md`); a possible 1.x epic or companion extension. Nothing here is implemented.

Jev is TypeSafe's System One model: it returns typed answers (Choice, Score, Noul probability) over a `state` payload via `POST https://api.typesafe.ai/v1/systemone`. There is no Java SDK; the HTTP API is a single JSON POST, and the project already has httpclient5 and Jackson. Docs: https://docs.typesafe.ai/llms.txt

## How the project judges things today

| Concern | Current mechanism | Code |
|---|---|---|
| Ranking | BM25 over edge-ngram `searchableText` plus per-field `should` boosts with hand-set weights | `QuickSearchOpenSearchClient.search`, `buildFieldBoostQueries` |
| Routing | Optional `tableName` filter only; no logic decides which table a query is about | `QuickSearchInput`, `QuickSearchAction` |
| Query classification | Lowercase and whitespace collapse | `IndexingUtils.normalizeSearchText` |
| Index event routing | Deterministic INDEX / DELETE; no semantic step, no Jev fit | `IndexEventAction`, customizers, `ReconcileIndexStep` |

## Candidates, strongest first

1. **Table routing (one Choice).** State: the query plus a catalog of indexed tables (label, searchable field names). Question: which kind of record is this query looking for, one option per table plus "no specific table". Use the probability distribution as per-table boosts on `sourceTable`, or feed it to core `RecordSearchAction.tableNames` / `limitPerTable` once the Epic 4 provider exists. Confidence-gate: narrow only when confidence is high, otherwise search everything. Tiny state, cheap, fast. New capability, not a simplification.
2. **Re-ranking the OpenSearch shortlist (one Noul per candidate).** Over-fetch about three pages, ask "is this record what the query is asking for" with the query and each candidate's `recordLabel` and `fieldValues` in state (all candidates in one request as parallel questions), sort by probability, cut to `limit`. Pattern: https://docs.typesafe.ai/cookbooks/rerank_typesafe.md. Costs: a network hop on the search hot path (not for per-keystroke typeahead), and record data leaving the cluster (needs enterprise zero-data-retention terms for CRM or health-adjacent data). Only as an opt-in reranker behind a flag, explicit searches, first page.
3. **Highlight selection.** Choice over highlight fragment ids ("which snippet best shows why this matched") instead of joining all fragments with "...". Small win, same egress concern as item 2.
4. **Offline weight suggestion.** Dev-time scoring of sample records per field for how identifying each value is, to suggest `weight`. Not runtime.

## Rejected

- Query-shape classification (email, phone, order number, name, free text) to switch exact keyword vs ngram matching. Regexes do this deterministically; Jev's own jaggedness notes say to keep literal pattern work in code.

## Integration notes

- Put any client behind an interface (`SearchRouter`, `ResultReranker`) so the core QBit stays OpenSearch-only and the integration is optional. Dependency or build changes need owner approval.
- Jev 1.13 limits: 80 requests per second, 100K tokens per second, 32k tokens of state per question. Fine for routing; tight for reranking under load.
- Pin a versioned model id (`jev-1.13.0`) once confidence thresholds are tuned; aliases move.
- API key stays server-side in host configuration, never in the QBit's metadata.

## Recommendation

Prototype item 1 only, as a confidence-gated boost, and measure against the seeded qqq-all customer data before considering item 2.

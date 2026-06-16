# Dumont — Improvements & design rationale

> **What this file is:** the *design rationale* (the what & why) for **unshipped**
> Dumont work — the spec behind each backlog item. It is one of the roadmap
> documents; keep them in sync:
>
> | File | Holds | One line per |
> |---|---|---|
> | [ROADMAP.md](ROADMAP.md) | **Task status** (single source of truth) — `D##` backlog | task |
> | [STRATEGY.md](STRATEGY.md) | Positioning, moat, the bets, non-goals | — |
> | **IMPROVEMENTS.md** (this file) | Design rationale for active `D##` tasks | — |
>
> **Founding rule:** once a task ships, its detail is removed from here — `git log`
> is the authoritative history. Do not re-accrete shipped implementation reports.

**Status legend** (status itself lives in ROADMAP.md): ⏳ partial · 📋 designed · 💭 idea.

**Cross-references** use paths from the repo root; linked code may have moved —
verify against `git ls-files` before acting. This track is cross-repo: the demands
originate from the **cursarei → Viglet** migration and pair with Turing **Block R**
(`turing/2026.1/docs/IMPROVEMENTS.md` §XIX). See [DOCS_INDEX.md](DOCS_INDEX.md).

## Table of contents

- [I. The second bet — adoption & trust on top of ingestion](#i-the-second-bet--adoption--trust-on-top-of-ingestion)
- [II. Incremental fetch — re-crawl only what changed (D12)](#ii-incremental-fetch--re-crawl-only-what-changed-d12)
- [III. AI-assisted source inference (D13)](#iii-ai-assisted-source-inference-d13)
- [IV. Dry-run preview & schema diff (D14)](#iv-dry-run-preview--schema-diff-d14)
- [V. Field-coverage & drift monitoring (D15)](#v-field-coverage--drift-monitoring-d15)
- [VI. Field-level provenance (D16)](#vi-field-level-provenance-d16)
- [VII. Index-time embeddings (D17)](#vii-index-time-embeddings-d17)
- [VIII. Cross-source entity resolution & dedup (D18)](#viii-cross-source-entity-resolution--dedup-d18)
- [IX. Reusable recipe & taxonomy library (D19)](#ix-reusable-recipe--taxonomy-library-d19)
- [X. Cross-cutting decisions & restrictions](#x-cross-cutting-decisions--restrictions)

---

## I. The second bet — adoption & trust on top of ingestion

The roadmap's first bet (Tracks A–C) is **ingestion fidelity**: a structured-source
SPI, declarative strategies, schema-as-code grounding, crawler hardening and the
cursarei sidecar→native path. That bet answers *"can Dumont extract this source
correctly?"*.

It does **not** answer the two questions that actually gate value once the framework
exists:

1. **Adoption** — *"how cheaply can someone stand up a new source?"* A declarative
   manifest is only cheap if producing it is cheap. Today that still means a developer
   reading HTML and writing selectors. → **Track D** (D13 AI-assisted inference, D14
   dry-run preview).
2. **Trust** — *"can I rely on what came out, and prove where it came from?"* A
   connector that silently drops fields when a catalog changes layout, with no
   provenance on what it published, is not safe to feed a RAG answer. → **Track E**
   (D15 drift monitoring, D16 provenance).

Track F (semantics — embeddings, entity resolution, reusable taxonomy) is the
**differentiator** that turns the result from "a pipe" into the *Data Exchange*
platform the product name claims: it doesn't just move documents, it makes them
semantically richer and de-duplicated on the way through.

The ordering is deliberate. Tracks A–C are prerequisites; D13/D15 are the highest
leverage *next* steps because they make the framework adoptable and the sidecar→native
parity gate (D11) measurable rather than manual. Track F follows the RAG maturity on
the Turing side.

## II. Incremental fetch — re-crawl only what changed (D12)

**Problem.** The checksum reindex/skip strategy fires *after* the page body is fetched
— it saves an index write, not a network round-trip. On the cursarei workload (4,000
courses across 120+ catalogs) the dominant cost of a re-run is the fetching itself, and
most pages are unchanged between runs. A frequent `dumont.cron` schedule is unaffordable
under that model.

**Design.**
- Persist `ETag` and `Last-Modified` per content id (alongside the existing checksum
  state) and issue conditional GETs (`If-None-Match` / `If-Modified-Since`). A `304 Not
  Modified` ends the work for that id with **zero body transfer**.
- Where a `sitemap.xml` advertises `<lastmod>` (discovered via D06), compare it to the
  last-seen value and skip the URL **before any fetch**.
- This is strictly additive to the checksum path: checksum still guards the case of a
  server that returns `200` with an unchanged body and no validators.

**Touch points.** Fetch step in
[`DumWCPluginProcess`](../web-crawler/wc-plugin/src/main/java/com/viglet/dumont/connector/plugin/webcrawler/DumWCPluginProcess.java);
per-content-id state store. Sits naturally behind the D07 fetcher SPI so a swapped
fetcher inherits the conditional logic.

**Done.** A re-run of an unchanged source issues conditional requests and re-indexes ~0
documents; a `lastmod`-advertising source skips unchanged URLs without a body fetch.

## III. AI-assisted source inference (D13)

**Problem.** STRATEGY's promise is "index a new source = a declarative adapter + a field
manifest". But *writing* that manifest still requires a developer to read the page,
pick the strategy and author selectors + a `TurSNJobAttributeSpec`. That keeps onboarding
a dev task and caps adoption — the framework is necessary but not sufficient.

**Design.** A `POST /api/v2/connector/source/infer` (working name) that, given a URL:
1. Fetches a sample and classifies the shape into one of the D02 strategies (Sitemap /
   HtmlListing / TabularListing).
2. Proposes a **draft** strategy config and a draft field manifest (each field: name,
   `TurSEFieldType`, multi-valued, facet, mandatory).
3. Returns the draft to the React source wizard for a human to **edit and confirm**.

Reuses Turing's existing LLM surface — `TurSummaryAPI` / `ChatModel` over the configured
`RestClient` (same pattern as `DumConnectorSummaryApi`). **No new model wiring** in
Dumont.

**Hard constraints.**
- The draft **never auto-publishes**. A human confirms before any source is created or
  indexed. This is in the ROADMAP non-goals.
- Inference obeys the grounding contract: it may propose *omitting* a field it cannot
  ground; it must not propose a default/placeholder value.
- The LLM proposes *structure* (which selector, which field type), not *content* — the
  actual values always come from the live extraction, never the model.

**Touch points.** New API in `connector-app` `api/`; `RestClient` → Turing; React source
creation wizard in `dumont-react`.

**Done.** Pointing the wizard at a tabular catalog yields an editable strategy + field
draft that, after confirmation, indexes end-to-end.

## IV. Dry-run preview & schema diff (D14)

**Problem.** "Dry Scan" today tells you a crawl *ran*; it does not tell you *what the
index would look like afterward*. Re-configuring a live source (new selector, changed
schema) is therefore a blind write — you find out it broke coverage after it shipped.

**Design.** Promote Dry Scan to a **preview index**:
- Extract a bounded sample of N records.
- Validate each against the declared schema (D03) — flag fields that fail type/mandatory.
- Compute a **diff vs the current index**: new / changed / removed documents, and
  fields that would become newly-empty (the grounding-relevant case).
- Report per-field **coverage** over the sample.
- Write nothing.

This shares its coverage computation with D15 (same metric, different trigger: preview
vs scheduled run).

**Touch points.** `DumConnectorApi` / `DumConnectorMonitoringApi`; `dumont-react` source
page (extend the existing Dry Scan action + a results panel).

**Done.** A preview run writes nothing and reports per-field coverage plus an
add/change/remove count for the sample.

## V. Field-coverage & drift monitoring (D15)

**Problem.** The worst failure mode of a structured-source connector is **silent**: a
catalog changes its HTML, a selector stops matching, a field quietly goes to 0%
coverage, and nobody notices until search/RAG quality visibly degrades — long after the
run "succeeded". D09's failure report covers *total* failure (zero items); it does not
catch *partial* degradation.

**Design.**
- After each run, record per-field coverage % (fraction of documents in which the field
  is present — naturally available once D03's schema is in force).
- Keep a short history per source and flag **drift**: a field whose coverage drops beyond
  a threshold between consecutive runs.
- Surface via `DumConnectorMonitoringApi` (`/monitoring/indexing`) and a `dumont-react`
  panel built on `SectionCard`; reuse `AiSummaryPanel` (the shared Turing/Dumont
  abstraction) for an LLM-written health summary.

**Touch points.** `DumConnectorMonitoringApi`; coverage history store; `dumont-react`
monitoring/health panel.

**Done.** A fixture whose layout changes between two runs raises a drift flag naming the
affected fields; coverage history is queryable per source.

## VI. Field-level provenance (D16)

**Problem.** The grounding contract guarantees an *absent* field is omitted — good. But
for a *present* field there is no record of **where it came from**. When Dumont feeds a
RAG answer in Turing, the consumer cannot cite the source of a specific attribute, nor
audit why a field disappeared.

**Design.** For each published attribute, record: source, strategy, selector/path,
`last-seen` run, optional confidence. Carry a **compact** provenance into the
`TurSNJobItem` (compact because it travels with every document — a per-field map of
small tokens, not full lineage). This elevates the contract from "omit absent" to "every
present field is traceable to a source".

Pairs with Turing **Block R** which holds the citation/grounding consumer side.

**Touch points.** Extraction record model (Track A typed record); `TurSNJobItem`
emission; the enrichment merge in D18 consumes provenance.

**Done.** A published document carries per-field provenance; a field dropped because the
source stopped declaring it is visible as "last-seen: run N".

## VII. Index-time embeddings (D17)

**Problem.** Hybrid/semantic RAG on the Turing side needs embeddings. Generating them in
a separate pass over the indexed corpus is a second full traversal and a consistency
problem (corpus changes between the index pass and the embed pass).

**Design.** Generate embeddings as an **enrichment step at index time** — Dumont already
holds the full text in hand at that moment. Behind config + cache, exactly like the D04
LLM enrichment path (respect the same `llmCache*` discipline Turing's `TurLlmCacheService`
uses). The document and its vector ship together, consistent by construction.

**Touch points.** D04 enrichment stage; indexing plugins (`DumTuringIndexingPlugin` first);
Turing hybrid ranking consumer.

**Done.** A source emits embedding vectors alongside its documents; Turing hybrid ranking
consumes them. Pairs with `turing/2026.1/docs/RAG_HYBRID_CONFIG.md`.

## VIII. Cross-source entity resolution & dedup (D18)

**Problem.** The same real-world entity arrives more than once: the same course across
institution mirror sites, or one item under both a canonical URL and tracking-parameter
variants. Indexed naïvely, it becomes duplicate documents that pollute facets and split
RAG relevance.

**Design.** Collapse duplicates to a **canonical id** with a **grounding-safe merge**:
when two records disagree on a field, prefer the source that *declares* it; never
synthesize a merged value. Provenance (D16) drives the merge and records which source won
each field, so the result stays auditable.

**Touch points.** Enrichment stage (D04); provenance model (D16); canonical-id assignment
before publish.

**Done.** A fixture with the same entity from two sources indexes as one canonical
document with merged, provenance-tagged fields.

## IX. Reusable recipe & taxonomy library (D19)

**Problem.** D11 calls for "one shared `TabularListing` recipe → the SIGAA federals". If
that recipe is copied per source, the leverage evaporates: 50 institutions become 50
near-identical configs that drift apart and must be fixed one by one.

**Design.** Make recipes and canonical taxonomy maps **first-class versioned artifacts**,
applied by reference with per-source parameters. A source references `recipe: sigaa@3`
plus its parameters; bumping the recipe re-applies to every source that uses it. This is
what makes the leg-by-leg native migration (D11) scale instead of multiply.

**Touch points.** Strategy config model (D02); a recipe registry/store; taxonomy
normalization in the enrichment stage (D04).

**Done.** Two institutions index via the same versioned recipe with only per-source
parameters; bumping the recipe re-applies to both.

## X. Cross-cutting decisions & restrictions

These constrain every task above:

- **Grounding is non-negotiable, everywhere.** Inference (D13), enrichment, embeddings
  and entity-merge (Track F) may omit, normalize, classify, derive or merge — they may
  **never** fabricate a sourced attribute or fill an absent field with a placeholder.
- **Reuse Turing's LLM surface; do not re-wire models in Dumont.** D13 and the D04/D17
  LLM paths all go through `RestClient` → `TurSummaryAPI` / `ChatModel`, inheriting
  Turing's cache and Global Settings (`llmCacheEnabled`, `llmCacheTtlMs`,
  `llmCacheRegenerate`).
- **The web crawler stays usable unchanged.** Every Track A/D/E/F capability sits *above*
  or *beside* `DumWCPluginProcess` under the same `DumConnectorPlugin` SPI — not a rewrite
  of it.
- **No auto-publish from a model.** Anything an LLM proposes is a draft a human confirms.
- **Additive state, not migrations of meaning.** Conditional-fetch validators (D12),
  coverage history (D15) and provenance (D16) are new state alongside existing checksum
  state — they do not change what a "document" is.

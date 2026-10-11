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
- [IX. Cross-cutting decisions & restrictions](#ix-cross-cutting-decisions--restrictions)

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

The ordering is deliberate. Tracks A–C are prerequisites; D13/D14 (AI-assisted inference +
dry-run preview) opened the adoption layer, D15 (field-coverage & drift monitoring) made
partial degradation visible and the sidecar→native parity gate (D11) measurable rather than
manual, and D16 (field-level provenance) made every published field traceable — all shipped,
completing the trust track. Track F (semantics) follows the RAG maturity on the Turing side.

## IX. Cross-cutting decisions & restrictions

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

## XLI Structured sources over paged JSON APIs

### XLI.25 Jobs enrichers read transients (D117)

Found while shipping D97. A structured record now carries transient, never-indexed
attributes: `DumStructuredRecord.Builder.transientValue(key, value)`, read back through
`transients()`. An enricher writes one by returning `DumTransient.of(value)` in its
contribution map, and `DumTransient.of(null)` clears it. The enrichment chain and
`toBuilder()` carry transients forward, and publishing reads only `values()`.

The `jobs` module predates this: its location enricher reaches back into the source
through `DumJobsStructuredSource.posting(id)` to read the adapters' raw location text,
and the `locationUnresolved` note has no place on the record.

Expected: the adapters set `locationRaw` (and any other raw text an enricher normalizes,
such as the salary, contract and seniority inputs) as transients when they build each
record. The enrichers read `rec.transients()` and write `locationUnresolved` as a
`DumTransient`. `posting(id)` then goes away, and the enrichers no longer depend on the
concrete source class. With D98, `extract()` can also keep the adapter's city and state,
and the location enricher removes a city outside the stated UF with `DumClear.field()`
instead of writing both fields itself. With D101, the contract, salary and seniority
enrichers return `DumSourced.of(value, selector, confidence)` naming whether they read
the partner's field, the title or the text, and a partner value they only pass through
keeps its own provenance. With D103, the contract and seniority enrichers become
`DumFallbackEnricher` declarations and the salary enricher a `DumSplitEnricher`. With
D106, the occupation LLM fallback takes the optional `DumClassifierFactory` bean instead
of a `jobsOccupationClassifier` nobody declares.

### XLI.26 Jobs loads IBGE through the taxonomy loader (D118)

Found while shipping D99. `DumTaxonomyLoader.fromResource(loader, path)` in
connector-commons now reads a versioned taxonomy from a JSON resource. Each row's `key`
column maps to its `canonical` column, the other columns become attributes
(`DumTaxonomy.attributes(raw)`, `attributeTable(name)`), and the file's `source`,
`downloadedAt` and `count` are kept or checked. A taxonomy application can also write an
attribute instead of the canonical value (`DumTabularRecipe.Builder.taxonomyAttribute`).

The `jobs` module still loads the IBGE municipality list with its own code
(`DumIbgeMunicipalities`). It maps code to name and derives the UF from the first two
digits of the code, with a hand-kept prefix table.

Expected: `jobs/ibge-municipios.json` declares `"key": "id"`, `"canonical": "nome"` and
`"rows": "municipios"`, and the module registers `DumTaxonomyLoader.fromResource(...)`.
The UF is then read with `attribute(code, "uf")` instead of from the code prefix, so the
prefix table and the module's loader go away. The file's existing `uf`-versus-prefix
check can stay as a test over the loaded taxonomy.

### XLI.27 Occupation step on the commons classification store (D119)

D107 gave `DumClassificationEnricher` a `DumClassificationStore` (in-memory
`DumEnrichmentCache`, or the persistent `DumJpaClassificationStore` bean in
connector-app), a rolling daily cap counted in that store, and
`DumClassifier.classify(input, labels)` with `DumClassifier.match`.
`DumJobOccupationEnricher` in `jobs/jobs-plugin` still carries the workaround it was
built with: its own `job_occupation_cache` table, a cap counted in `job_call` under
`occupation-classifier`, and `DumJobOccupationLabels.code` as its label check. Two
caches and two caps now do one job, and a fix to either is not a fix to the other.

Expected: the occupation step keeps its dictionary, its `known()` read for tombstones
and its per-source counters, but asks the classifier through `classify(title, labels)`
and keeps answers and calls in the injected `DumClassificationStore`, keyed with the
taxonomy version so a newer CBO list is a miss. The label set stays the CBO codes; the
slug mapping stays in `DumCboFamilies`.

Open choice: whether to migrate the rows already in `job_occupation_cache` (answers paid
for once) or let them expire and re-ask under the daily cap. Dropping
`job_occupation_cache` and the `occupation-classifier` rows in `job_call` is part of the
change either way.

### XLI.28 Jobs deduplication on the commons resolution index (D120)

`DumJobDeduplicator` in `jobs/jobs-plugin` was written because D18 resolution ran only
in `crawl()`, in memory, with a field merge. D109 and D110 removed all three reasons.
`DumStructuredSourcePlugin` now resolves on every `indexAll` through a persistent
`DumResolutionIndex` (`DumResolutionIndexFactory`, table `dum_resolution_member`), holds
a source's members it no longer sees and keeps publishing their documents while another
member is live, and `survivorship()` can return `DumSurvivorship.wholeRecord(...)`.

The jobs module still carries its own copy of each: the `job_fingerprint` table, expired
rows kept so a source keeps publishing its group, and `DumJobPreference` as the
whole-record rule. Two indexes now decide what a shared posting publishes as.

Expected: the jobs plugin passes a `DumCanonicalKey` built from `DumJobFingerprint` and
the factory's index to its constructor, overrides `survivorship()` with `wholeRecord`
over `DumJobPreference.ORDER` adapted to `DumResolutionIndex.Member`, and drops
`job_fingerprint`.

Open points the move has to settle: the document id is `job-<hash>` today, while the
commons canonical id is the key's own text, so either the key yields that id or the
published URL rewrite moves with it. Vanished-group tombstones (TRA24) need to know when
a canonical id is dropped, which is D113's hook. The per-pair duplicate rate has no
counterpart in the commons run and stays a jobs summary. Since D111 the run-end sweep no
longer deletes a document another source of the provider still tracks, so held rows are
no longer what keeps a shared group indexed.

### XLI.29 Jobs post-run work on the run-end hooks (D121)

`DumJobsPlugin.indexAll` in `jobs/jobs-plugin` wraps `super.indexAll(source)` to reset
per-run counters before the run and, in a `finally`, to log the location, salary,
occupation and deduplication summaries and flush `DumJobTombstones`. It was written
because sources had no end-of-run hook; D115 added
`DumStructuredSource.onRunEnd(DumRunEnd)`, called after the sweep on every path, aborts
included, and D100 already gives enrichers `onRunEnd`, whose notes join the run record.

Expected: the jobs source flushes its tombstones from `onRunEnd`. The three enrichers
report their summaries through `DumEnricher.onRunEnd` and reset there, so the counts
reach the run record (D96) instead of only the log. The deduplicator's summary follows
whichever path D120 takes. Then `DumJobsPlugin` drops its `indexAll` override.

The resets the override does before a run need no hook: an enricher that resets in
`onRunEnd` starts every run clean, since `runEnd` is called once per run on every path.

### XLI.30 Jobs replay versus unchanged ids (D122)

Split out of D116's design. `DumJobsStructuredSource.discover()` reports this run's
fetch plus every stored open posting, and `extract()` rebuilds each stored one from
`DumJobSeen.payload`, so the sweep sees it again without a partner call. D104 added
`DumStructuredSource.unchangedIds()`: ids still open at the source that the loop keeps
alive without an extract.

The idea: return the stored open postings that this run did not fetch from
`unchangedIds()` instead of replaying them, and drop the payload replay through
`extract()`.

What has to be settled first, because the replay does more than keep ids alive:
- Enrichment runs on every replayed posting each run, so a taxonomy or dictionary change reaches stored postings today; kept-alive ids would keep their last indexed values until the partner returns them again.
- The deduplicator (`DumJobDeduplicator`, D120) reads the payloads to choose and publish a group's preferred member, and a group document can change when another source's member arrives, which a keep-alive cannot express.
- Tombstones (TRA24) name the occupation and title of an expired posting from the stored payload.

So the payload may stay even if the replay goes. This is a question for whoever owns the
jobs module, not a ready design.

## XLII Web-crawler hardening (real-world sources)

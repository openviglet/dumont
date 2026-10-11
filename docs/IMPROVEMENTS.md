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

### XLI.14 Jobs runs report their stops (D106)

Found while shipping D96. Structured sources can now return `runNotes()`
(connector-commons `DumStructuredSource`), and the plugin loop records them, with the
run's partial flag, on the run's indexing-stats row, which the console's Indexing Stats
table shows.

The `jobs` module predates that hook: when a paid-call budget is spent, or a partner
answers HTTP 429, its adapters (Adzuna, Jooble, BNE, JSearch) still only log a WARN
line. Its runs therefore still show up as complete runs with fewer documents, which is
exactly what D96 set out to stop.

Expected: `DumJobsStructuredSource` overrides `runNotes()` with one note per stop its
adapters hit (the budget that was spent, the page and HTTP status that failed), and
`discoveryComplete()` answers `false` for those runs so the loop finishes them
standalone and marks them partial. The adapters already compute each case for their WARN
lines, so the note is the same sentence, collected instead of only logged. With D100,
the unresolved-location count moves from the `DumJobsPlugin.indexAll` override to the
location enricher's `onRunEnd`, and the override goes away.

### XLI.15 Jobs enrichers read transients (D107)

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
instead of writing both fields itself.

### XLI.16 Jobs loads IBGE through the taxonomy loader (D108)

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

## XLII Web-crawler hardening (real-world sources)
### XLI.4 Keep-alive for incremental sources (D104)

Found while building the `jobs` module for trabalharei (TRA13). A job API bounded by a
call quota (Adzuna's default is 250 calls a day) can only fetch the newest postings each
run, while older postings stay open for weeks. The run-end sweep in
`DumConnectorContextImpl` deindexes every tracking row whose `transactionId` differs
from the session's, and a row only takes the new id when its document passes through
`addJobItem`. Returning an id from `discover()` is not enough: `extract()` must hand
back a full record for every still-open posting, so the module keeps each posting's last
payload in its own `job_seen` table only to replay it, and normalizes every record
through JSON so a replay and a fresh fetch produce the same checksum.

Expected: a keep-alive primitive on the structured-source path, for example `discover()`
returning ids marked "unchanged, keep" that stamp the session's transactionId on the
existing rows without an extract, so incremental sources need no payload store.

Workaround: `DumJobSeen.payload` in `jobs/jobs-plugin`.

### XLI.5 Build in a git worktree (D105)

Found while building the `jobs` module (trabalharei TRA13) in a git worktree of this
repo (`git worktree add`). `./mvnw -pl jobs/jobs-plugin -am test` fails before
compiling: `git-commit-id-plugin` on `dumont-commons` reports "Missing unknown <sha>",
because JGit cannot resolve HEAD through a worktree's `.git` file. Passing
`-Dmaven.gitcommitid.skip=true` makes the build green, and nothing in `agents.md` says
so.

Expected: the build works in a worktree, by switching the plugin to the native git
executable (`useNativeGit`) or setting `failOnNoGitDirectory=false` with a fallback, and
`agents.md` names the flag in the meantime.

Workaround: the jobs module was built with `-Dmaven.gitcommitid.skip=true`.

### XLI.11 Provenance from enrichers (D101)

Found while adding contract and salary enrichers to trabalharei's `jobs` module (TRA21).
`DumEnrichmentChain.merge` tags every enricher contribution with
`DumFieldProvenance.enrichment(name)` and leaves selector and confidence empty, though
the record type has those slots. The contract step cannot record whether a value came
from the partner's field, the title or the text, and a partner salary the enricher only
passes through (because of the D98 workaround) loses its source provenance.

Expected: `enrich()` returns a provenance per field (at least a selector and a
confidence), and a pass-through keeps the source's own provenance.

Workaround: none; provenance for enriched job fields names only the enricher.

### XLI.12 Drop only the invalid contribution (D102)

Found while adding the salary enricher to trabalharei's `jobs` module (TRA21), which
writes four fields at once (min, max, period, band). When any one contribution is
invalid, `DumEnrichmentChain.merge` keeps the record unchanged and drops every field
that enricher produced, logging a WARN that does not name the offending field.

Expected: an invalid contribution drops only that field, and the log names the field and
the reason.

Workaround: the salary enricher validates its own numbers before contributing.

### XLI.13 Fallback and one-to-many enricher shapes (D103)

Found while adding contract, seniority and salary enrichers to trabalharei's `jobs`
module (TRA21). `DumNormalizationEnricher` maps one field to one field. Two common
shapes need a hand-written enricher class each: an ordered fallback ("the partner's
field, else the title, else the text; the first input that decides wins") and one input
producing several output fields (salary text to min, max, period and band).

Expected: built-in enricher shapes for an ordered fallback over inputs and for a
one-to-many parser, configured by a recipe, so connectors declare them.

Workaround: `DumJobContractEnricher`, `DumJobSeniorityEnricher` and
`DumJobSalaryEnricher` in `jobs/jobs-plugin`.


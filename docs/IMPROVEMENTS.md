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

### XLI.17 Persistent cross-run resolution (D109)

Found while deduplicating jobs across sources for trabalharei (TRA23). The D18
cross-source resolve stage runs only inside `crawl()` and holds one call's records in
memory, so it never sees another run, and the per-source `indexAll` path the API uses
never reaches it. Sources that publish the same entity on different schedules (four job
APIs with their own quotas) cannot be resolved against each other at all.

Expected: resolution backed by a persistent canonical-id index (fingerprint to member
ids) that every run of every source of a provider consults, on both `crawl()` and
`indexAll`.

Workaround: `DumJobDeduplicator` with a `job_fingerprint` table in `jobs/jobs-plugin`.

### XLI.18 Pluggable survivorship (D110)

Found while deduplicating jobs across sources for trabalharei (TRA23).
`DumEntityResolver` / `DumRecordMerge` take no survivorship rule: the first contributor
wins and later sources fill missing fields, with the primary fixed by provider order. A
job site needs to keep one whole record chosen by a comparator (salary present, then the
longer description, then the partner that pays per click), and field-by-field filling
mixes one partner's apply link with another's text.

Expected: a pluggable survivorship strategy (pick one whole record by a comparator, or
merge field by field with per-field rules), declared per provider.

Workaround: `DumJobPreference` in `jobs/jobs-plugin`.

### XLI.19 Reference-counted deindex for shared documents (D111)

Found while deduplicating jobs across sources for trabalharei (TRA23). Dumont tracks
what each run published per (objectId, source). When several sources of one provider
publish the same document id, one source's run-end sweep sends a delete for an id it
tracked but did not publish this time, and the delete removes the document from the
index even though another source still tracks it. That source's next run sees an
unchanged checksum and does not resend, so the document is gone silently until its
content changes.

Expected: a reference-counted delete (deindex only when no source of the provider still
tracks the id) or an owner notion for shared documents.

Workaround: in `jobs/jobs-plugin`, every source holding a row in a group republishes the
group document while any member is open, keeping expired rows as placeholders; each
shared document is therefore sent once per source.

### XLI.20 Resolve path keeps the run lifecycle (D112)

Found while reading the D18 path for trabalharei's job deduplication (TRA23).
`resolveAndIndex` in `crawl()` skips the per-source safeguards that `indexAll` applies
(the zero-yield and failure-wave guards, the incremental-run handling, manifest
provisioning), and it finishes only the primary sources' sessions, so a source whose
records all merged into another source's is never swept.

Expected: the resolve path goes through the same per-source run lifecycle as `indexAll`
(guards, provisioning, session finish and sweep for every contributing source).

Workaround: none; the jobs module does not use the D18 path.

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

### XLI.6 Run notes from structured sources (D96)

Found while building trabalharei's paid JSearch adapter (trabalharei TRA19) in the
`jobs` module. `DumStructuredSource` (`connector-commons/.../spi/structured`) offers
only `discover()`, `extract()` and getters, so a source has no way to tell the run
record why a run ended early. When the monthly paid-call budget is spent, the adapter
can only log a WARN line; in Dumont's run history the run looks like a success with
fewer documents, and the same is true of a partner returning HTTP 429 or a quota refusal
on any source.

Expected: a way for a structured source to attach a status and short notes to the run
(for example `discover()` returning a result carrying `partial` plus a reason, or a
`runNotes()` the loop reads at the end), shown in the run record and the coverage view
(D15), so "stopped by budget" and "partner rate-limited" are visible without reading
logs.

Workaround: the jobs module logs one distinct WARN line per budget stop.

### XLI.7 Transient record attributes (D97)

Found while adding location normalization to the `jobs` module (trabalharei TRA20).
`DumStructuredRecord.Builder.set` rejects any key the schema does not declare, so a
source cannot hand an enricher working data that must not be indexed: the jobs adapters'
raw location text (`locationRaw`) and the enricher's `locationUnresolved` note. The
enricher instead reaches back into the source through a module-specific
`DumJobsStructuredSource.posting(id)` lookup, using the `source` argument of `enrich()`.

Expected: a record carries transient, non-indexed attributes (a separate map, or fields
the schema marks `indexed=false`) that enrichers read and the publish step drops.

Workaround: `DumJobsStructuredSource.posting(id)` in `jobs/jobs-plugin`.

### XLI.8 Enrichers can clear fields (D98)

Found while adding location normalization to the `jobs` module (trabalharei TRA20).
`DumEnrichmentChain.merge` applies the source's values first and ignores blank
contributions, so an enricher can add or overwrite a field but never remove one. A
canonicalizer must sometimes drop a value the source sent: a city that does not exist in
the stated UF keeps the UF and loses the city, and a remote posting has no city.

Expected: an enricher can clear a field explicitly (a tombstone value, or a
`remove(field)` on its contribution), applied by the merge.

Workaround: the jobs module's `extract()` leaves city and state off the record entirely
and lets the enricher write them, falling back to the adapter's values when the taxonomy
is missing.

### XLI.9 Tabular taxonomies from resources (D99)

Found while shipping the IBGE municipality list (5,571 rows of code, name, UF) as a D19
taxonomy for trabalharei's `jobs` module (TRA20). `DumTaxonomy` is a flat
string-to-string map and the recipe library has no loader for a classpath or file
resource, so a reference table with more than one attribute per row cannot be a
taxonomy. The module maps IBGE code to name and derives the UF from the code's first two
digits, checked against the file at load time, and loads the JSON with its own code.

Expected: taxonomies whose entries carry several attributes (or a tabular taxonomy
type), and a loader that registers a versioned taxonomy from a resource with its source
and download date.

Workaround: `DumIbgeMunicipalities` in `jobs/jobs-plugin`.

### XLI.10 Enricher end-of-run hook (D100)

Found while counting unresolved locations per source in trabalharei's `jobs` module
(TRA20). `DumEnricher` is called per record and has no end-of-run hook, so an enricher
that aggregates over a run (unresolved counts, coverage, rejected values) cannot emit
its summary. The jobs plugin overrides `indexAll` to log one line per source after the
run, which every enriching connector would have to repeat.

Expected: an `onRunEnd(source, context)` callback on enrichers, whose output joins the
run record once D96 gives runs a place for notes.

Workaround: `DumJobsPlugin.indexAll` override in `jobs/jobs-plugin`.

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

### XLI.14 LLM classifier for plugins (D106)

Found while adding occupation classification to trabalharei's `jobs` module (TRA22). The
only LLM classifier, `DumTuringClassifier`, lives in connector-app with no Spring bean
and is not on a plugin's compile classpath, and the `dumont.enrichment.llm.enabled` flag
the commons Javadoc names is wired nowhere. A plugin that wants an LLM fallback can only
look up a `DumClassifier` bean that no code declares, so in production the jobs module
classifies by its title dictionary alone.

Expected: a `DumClassifier` bean exposed to plugins when an LLM is configured (backed by
`DumTuringClassifier` or a provider), enabled by the documented flag.

Workaround: the jobs module looks up an optional `jobsOccupationClassifier` bean and
runs dictionary-only without it.

### XLI.15 Bounded closed-set classification (D107)

Found while adding occupation classification to trabalharei's `jobs` module (TRA22).
`DumClassificationEnricher` cannot classify into a fixed taxonomy at a bounded cost: its
cache is in memory only and keeps every empty result for the process lifetime (a call
refused by a cap would read as "no label"), it has no call or cost cap, and it publishes
whatever label comes back without checking it against a list. `DumClassifier.classify`
takes only the input string, so there is no closed-label contract: each caller rebuilds
the prompt with the whole label set (617 CBO families is about 10k tokens per call) and
validates the answer itself.

Expected: a classification enricher with a persistent cache keyed by a strong hash and
the taxonomy version, a daily call or cost cap that does not cache refusals, and a
`classify(input, labels)` contract that rejects answers outside the set.

Workaround: `DumJobOccupationEnricher` with `job_occupation_cache` and the cap in
`job_call`, in `jobs/jobs-plugin`.

### XLI.16 Collision-safe classifier cache key (D108)

Found while reviewing the classifiers for trabalharei's `jobs` module (TRA22).
`DumTuringClassifier` builds its Turing cache key as `prefix +
Integer.toHexString(input.hashCode())`, a 32-bit hash. Two different inputs whose
`String.hashCode()` collide share a key and get each other's cached answer, so a title
can silently receive another title's label.

Expected: a collision-resistant key (SHA-256 of the normalized input, plus the label set
or taxonomy version), with a test that two known colliding strings get separate entries
(for example "Aa" and "BB" share a Java hashCode).

Workaround: none needed in trabalharei yet (the jobs module keys its own cache by
SHA-256); filed because every other caller of the classifier is exposed.


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

## XLII Web-crawler hardening (real-world sources)
### XLI.2 Every connector module reaches the image (D91)

Found while planning trabalharei's ingestion on dumont.viglet.cloud (trabalharei TRA61).
The `Dockerfile` copies the aem, eds, db, web-crawler and assets plugin jars into
`/app/libs` (picked up through `LOADER_PATH=/app/libs`), but has no COPY line for
`catalog-plugin`, so the `ghcr.io/openviglet/dumont-ce` image that viglet/cloud runs
cannot use the catalog connector D10 shipped. Any later structured-source module (a
`jobs` module, say) would be missing the same way.

Expected: every connector module the build produces lands in the image, ideally from one
list or a glob in the build, so adding a module cannot silently leave it out. A test or
CI check that compares the modules declared in `pom.xml` with the jars in `/app/libs`
would keep it that way.

### XLI.1 Declarative JSON API source (D92)

Found while planning trabalharei's job ingestion (trabalharei roadmap, TRA13 and
TRA15-19): four job sources (Adzuna, Jooble, BNE, JSearch) are all paged JSON APIs, and
every one of them needs its own Java `DumStructuredSource` today.

What exists: `catalog-plugin` (D10) does a single GET of one URL with one optional auth
header, expects a bare array or a `{courses:[...]}` envelope, and maps fixed course
fields (`DumCatalogSchema`). It has no paging and no configurable mapping. The no-code
paths (D02 strategies, D13 source inference) target sitemaps, HTML listings and tables,
not JSON.

What a JSON API source needs, declared in a manifest or recipe rather than code:

- request: URL template, GET or POST with a JSON body, and credentials as a query
  parameter, header or path segment;
- paging: page, offset or cursor, a stop rule (empty page, total count, id already seen)
  and a page cap;
- a call budget per minute, day, week and month that ends the run cleanly at the cap;
- record path (`results`, `jobs`, `data.listVagas`) and a field mapping by JSON path,
  with an id field;
- an incremental mode for budget-bound sources: ids seen earlier stay alive until a
  per-record expiry, so the delta sweep does not deindex what this run did not fetch.

Workaround in the meantime: trabalharei writes one Java source per API in a `jobs`
module. Once this ships, those sources become recipes.

### XLI.3 Admin password from configuration (D93)

Found while adding Dumont to trabalharei's local compose (trabalharei TRA67, on
`ghcr.io/openviglet/dumont-ce:latest`). `DumUserOnStartup` seeds a password-less
`admin`, and the only way to set its password is the first-access setup (`POST
/api/setup`), the screen the console shows. Turing reads `TURING_ADMIN_PASSWORD` at boot
and skips that screen (`TurAdminPasswordService.bootstrapFromConfiguration`), so a
compose file or CI can start Turing with a usable login, but not Dumont.

Expected: a `dumont.admin.password` property (`DUMONT_ADMIN_PASSWORD`) applied at boot
while no password is stored, with the same minimum length and the same "ignored, already
configured" log as Turing, so both products are configured the same way.

Workaround: trabalharei's `scripts/turing-dev-setup.mjs` waits for Dumont and posts the
password to `/api/setup` itself.

### XLI.4 Keep-alive for incremental sources (D94)

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

### XLI.5 Build in a git worktree (D95)

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


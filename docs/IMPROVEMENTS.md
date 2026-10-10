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

### XLI.1 Declarative JSON API source (D92)

Found while planning trabalharei's job ingestion (TRA13, TRA15-19): Adzuna, Jooble, BNE
and JSearch are all paged JSON APIs.

Landed: `spi/structured/jsonapi` in connector-commons. `DumJsonApiManifest.parse` reads
the request (GET or POST, `{page}` `{offset}` `{size}` `{cursor}` `{secret:NAME}`),
paging (page, offset or cursor; empty or short page, total, repeated cursor or ids end
it), a per-run call cap and a per-minute pace, the record path and JSON-path field
mappings. `DumJsonApiSource` runs it. A walk a cap or a failed call cuts short reports
`discoveryComplete() == false`, and `DumStructuredSourcePlugin` then finishes
standalone.

Still to build:

- **Binding.** No plugin yet turns a stored manifest plus secrets into a source. Either a
  `json-api` mode on `catalog-plugin` (manifest text and secrets on `DumCatalogSource`,
  resolved in `DumCatalogSourceProvider`) or a small `jsonapi-plugin`; the catalog route
  reuses its console, import and API. Secrets stay out of the manifest and the export.
- **Budgets per day, week and month.** These need a counter that outlives the run (a JPA
  row per source and window); the run then caps its calls at what the windows have left.
- **Incremental mode.** A budget-bound source never sees everything in one run, so it
  finishes standalone and nothing is ever deindexed. Ids seen earlier should stay alive
  until a per-record expiry, and only expired ids are deindexed (`deIndexById`, D68).

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

### XLI.4 Every connector module reaches the packages (D94)

Found while shipping D91. The Docker images now collect every `<connector>/*-plugin` jar
by glob, but the other distribution paths still list plugins by hand, and each list has
drifted:

- `scripts/package-dist.sh` and `scripts/package-dist.bat` stage aem, db, web-crawler, assets
  and catalog, but not `eds/eds-plugin/target/eds-plugin.jar`, so `dumont-install.zip`, the
  NSIS installer and the AppImage that `publish.yml` builds from that stage ship without EDS.
- `scripts/installer/dumont.nsi` has one `File` section per plugin, also without EDS.
- `publish.yml` uploads only `dumont-connector.jar` and `aem-plugin.jar` as loose release
  assets; `upload_to_release.cmd` still names `db/db-app` and `filesystem/fs-connector`,
  modules that no longer exist.

Expected: the staging scripts iterate `*/*-plugin/target/*-plugin.jar` (each into
`connector/libs/<connector>/`) and fail when a plugin module produced no jar, the way
the root Dockerfile's staging loop does. The installer's per-connector sections are a
user choice, so EDS gets its own section rather than a glob there. Decide whether loose
release assets should carry every plugin or none beyond the installers, and retire
`upload_to_release.cmd` if `publish.yml` replaced it.

## XLII Web-crawler hardening (real-world sources)

### XLII.1 Incremental EDS runs keep what they skip (D95)

Found while shipping D92, by reading the code; not yet reproduced against a running
connector.

`DumEDSPluginProcess.start()` diffs discovery against `DumEDSCrawlState` and fetches
only pages whose `lastModified` changed (`filterUnchanged`). Unchanged pages are never
submitted as job items, so their `dum_indexing` rows keep the previous run's
`transactionId`. The `finally` block then calls
`connectorContext.finishIndexing(session, false)`, and the non-standalone finish runs
the delta sweep: `getShouldBeDeIndexedList` selects every row of the source whose
`transactionId` differs from this session's and queues a DELETE for each. On the second
incremental run, then, every unchanged page would be deindexed while its crawl state
still says it is current, and the next run would skip it again, leaving it out of the
index until its `lastModified` moves.

EDS already detects removals itself (`removeMissingPaths`, `removeNotFoundPaths` with
explicit DELETEs), so the sweep is redundant there. Expected: an incremental run
finishes standalone (`finishIndexing(session, true)`), and only `reindex` (crawl state
cleared, everything resubmitted) finishes with the sweep. A test should run `start()`
twice against a context that models the ledger and assert that the skipped page is not
deleted.

Related: `deleteContentsToBeDeIndexed` deletes ledger rows without the `standalone =
false` filter its DELETE selection uses, so rows written standalone lose their ledger
entry with no DELETE sent. Check whether that is intended while fixing this.

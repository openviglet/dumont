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

## XLIII Connector run control

### XLIII.2 Unquoted tenant predicate on H2 (D125)

Met in trabalharei (TRA25, Dumont-to-Turing publishing proof) on a local Dumont 2026.3
built from `e7aeb581`, embedded H2 (`jdbc:h2:file:./store/db/dumontDB`),
`DUMONT_MULTITENANT=false`.

Every structured run ends in
`DumConnectorIndexingService.countBySourceAndProviderSince`, from
`completeIndexingStats` in `DumConnectorContextImpl.finishIndexing`. Hibernate renders
the derived count as

    select count(dcim1_0."id") from "dum_indexing" dcim1_0
    where dcim1_0.tenant_id = ? and dcim1_0."source"=? ...

and H2 answers `Column "DCIM1_0.TENANT_ID" not found` (42122). With
`globally_quoted_identifiers: true` the column was created quoted, as lowercase
`"tenant_id"`, but the `@TenantId` predicate Hibernate adds is not quoted, so H2 folds
it to upper case. PostgreSQL folds unquoted names to lower case, which is why it only
shows on H2. The exception escapes `finishIndexing`, so the request answers 500 and the
run record is left half-written.

Expected: the tenant predicate matches the column on every supported database. Either
quote the `@TenantId` column name explicitly (`@Column(name = "\"tenant_id\"")`, the way
the other columns come out) or open H2 with `DATABASE_TO_LOWER=TRUE` by default. An IT
on the embedded H2 that finishes one structured run would have caught it.

Workaround in trabalharei: `docker/docker-compose.turing.yml` opens Dumont's H2 with
`;DATABASE_TO_LOWER=TRUE` through `SPRING_DATASOURCE_URL`, naming this id.

### XLIII.3 Abort cause lost behind a failing finish (D126)

Met in trabalharei (TRA25, Dumont-to-Turing publishing proof) on a local Dumont 2026.3
built from `e7aeb581`.

`DumStructuredSourcePlugin.run` catches a `RuntimeException` from the extraction loop
and calls `finishPartialRunStandalone(session, source, "aborted by an exception", e,
...)`. That method calls `recordRunOutcome` and `finishIndexing` first and logs the
cause only afterwards. When `finishIndexing` itself throws (here the H2 tenant column
failure filed beside this line), the log line carrying the cause is never reached. All
the log and the run record show is `Run outcome for source 'proof-adzuna': partial
(aborted by an exception)`, and the operator cannot tell what stopped the run: the job
source never even called its partner API.

Expected: the cause is logged (with its stack) before anything else in the finish path
runs, and the run record's notes carry its class and message next to "aborted by an
exception", so the console shows why without reading container logs. A failure inside
the finish path is logged on its own and does not replace the first one.

Workaround in trabalharei: none. The cause was found by removing the finish failure.

### XLIII.4 Structured job items carry no checksum (D127)

Met in trabalharei (TRA25, Dumont-to-Turing publishing proof) on a local Dumont 2026.3
built from `e7aeb581`, Turing built from `0256a122dd`.

The first `index/proof-adzuna/all` of a structured source publishes its 7 records and
Turing imports them. The second run, with nothing changed at the partner, aborts:

    NullPointerException: Cannot invoke "String.equals(Object)" because the return value
    of "TurSNJobItem.getChecksum()" is null
      at ReindexStrategy.lambda$canHandle$1(ReindexStrategy.java:92)
      at JobProcessingChain.process(JobProcessingChain.java:70)

`DumStructuredSourcePlugin.toJobItem` builds `new TurSNJobItem(CREATE, sites, locale,
attributes, specs)`, the constructor that leaves `checksum` null, so every ledger row of
a structured source holds `checksum: null` (visible in `monitoring/index/<source>`) and
the first run that finds a row calls `.equals` on null. Every structured source
therefore fails its second run, and nothing decides unchanged versus changed.

Expected: `toJobItem` stamps a checksum computed from what is published (attributes and
specs, in a stable order), so an unchanged record is skipped, a changed one is
reindexed, and `ReindexStrategy` treats a null on either side as changed instead of
throwing. An IT that runs one structured source twice would cover it.

Workaround in trabalharei: none. The jobs extension could override `toJobItem`, but the
fix belongs to every structured source; trabalharei's TRA25 waits on this line.

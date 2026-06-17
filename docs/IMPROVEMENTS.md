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
- [II. Connector convergence — one ingestion contract (Track G, D20–D23)](#ii-connector-convergence--one-ingestion-contract-track-g-d20d23)
- [III. AEM extraction model redesign (Track H, D24–D27)](#iii-aem-extraction-model-redesign-track-h-d24d27)
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

## II. Connector convergence — one ingestion contract (Track G, D20–D23)

**Problem.** Every benefit Tracks A/D/E/F deliver — grounding-by-construction (D03), dry-run
preview (D14), field-coverage & drift (D15), enrichment (D04), provenance (D16) — is wired into
the **structured SPI** (`DumStructuredSourcePlugin`: `schema()` + `discover()` + `extract(id)`).
The original connectors (AEM, DB, Assets, WC) predate it and each hand-roll their own
"discover → extract → publish" loop emitting a **loose `Map<String,Object>`** with `specs = null`.
Consequence: a DB or AEM source cannot get coverage/drift or a preview, because nothing declares
*which fields it should have* — coverage % is undefined without a schema. The connectors and the
framework are two parallel worlds.

**Why an adapter, not a rewrite.** The non-goal "the structured-source framework sits beside the
AEM/CMS connectors under the same SPI" is the target end-state, not a prohibition: bring each
connector **under** the SPI through a thin adapter that *reuses its existing extraction*, so we
add the trust/adoption surface without touching crawl logic, auth, delta, or politeness.

**Design.** The shared adapter pattern — wrap a connector's per-item mapping as a
`DumStructuredSource` (schema from its declared field config, `discover()` from its id enumeration,
`extract(id)` from its single-item fetch) so grounding/preview/coverage are inherited, not
re-implemented — shipped with the **DB pilot (D20)**; see [CHANGELOG.md](CHANGELOG.md). The
remaining connectors follow it (AEM shipped as **D21**, Assets as **D22**; see
[CHANGELOG.md](CHANGELOG.md)):
- **WC (D23)** is deliberately *not* converted — a generic link-spider has no enumerable typed
  record set; the framework was built to sit *above* it (D02 strategies are the structured path).
  It gets only a lightweight coverage signal off its declared `DumWCAttributeMapping` names, plus a
  documented migration path for structured sites still on WC.

**Touch points.** Per-connector `DumStructuredSource` facades (DB shipped in D20, AEM in D21,
Assets in D22); WC coverage seam reusing D15.

**Done.** Per task in [ROADMAP.md](ROADMAP.md). Net: AEM/DB/Assets sources index through the
structured plugin at parity with their legacy paths and gain coverage/drift + preview; WC reports
per-field coverage without a contract it cannot honor.

## III. AEM extraction model redesign (Track H, D24–D27)

**Problem.** The AEM connector ships *two* extraction paradigms: a declarative property→field
mapping (`DumAemPluginModel` → `targetAttr` ← `sourceAttr`) and a code-based content extractor
(`DumAemExtContentInterface` / `DumAemExtModelJsonBase`). Every serious deployment **abandons the
declarative one** and pours all logic into a single monolithic content-extractor — the Insper
portal's `DumAemExtInsperModelJson` is the canonical case: ~480 lines fusing template routing,
component-tree traversal, business derivation (`inscricaoAberta`, `dateSort`, `preco`,
`dateOfClassesText`, `alphabet`) and normalization (timezone, `html2Text`, PT months). When every
real source rejects the parameterized layer for a god-class, the parameterization is modelling the
wrong thing. That class is the indexing **Achilles' heel**: untestable in parts, un-reusable across
institutions, schema-less, and able to break **silently**.

**Root cause.** The unit of mapping is wrong. AEM content is a **tree of typed components grouped
by content type** (a news article, an event, a course/offer, an institutional page), not a bag of
JCR properties. A property→field table cannot express "flatten all contests across all banners,
filter by open enrolment, take the earliest future start date" — so it always degrades to code. And
`templateNameAliases()` collapsing 8 templates into 4 types is the domain quietly telling us the
real unit is the **content type**, not the source.

**Design.** Realign the *mapping* layer to the domain (crawl/auth/delta/navigator stay untouched),
on top of the contract D21 establishes:
- **Component-extractor SPI (D24).** Externalize the god-class into a registry of small,
  per-component extractors — the fluent `DumAemModelJsonQuery` API is *already* component-oriented,
  so this is mostly "stop inlining". Each extractor is independently unit-testable.
- **Schema-per-content-type + routing (D25).** A template routes to a content type with its own
  `DumStructuredSchema`; `extract(id)` picks schema + extractor composition by resolved type.
  Coverage/drift then reads per type.
- **Derivations → enrichers (D26).** The derived fields move to the D04 enrichment chain; the
  cross-cutting normalizers become shared. Extraction emits only *sourced* fields.
- **Versioned recipes (D27).** Component extractors + content-type schemas become versioned,
  parameterized artifacts (D19), so one "course-offer" recipe applies across institutions.

**Why incremental.** D21 (wrap as-is) delivers the contract + coverage at ~zero risk and *measures*
the pain first; each Track H task then peels one layer, deliverable on its own. The risk to avoid is
building a bespoke AEM-mapping framework before the structured SPI + D04 prove insufficient — which
is why D24 starts by refactoring the existing extension, not inventing a new engine.

**Touch points.** `aem-commons` ext SPI (`DumAemExtModelJsonBase`, `DumAemModelJsonQuery`,
`DumAemAttrMap`); the D21 AEM `DumStructuredSource` adapter; `DumStructuredSchema` per content type;
the D04 `DumEnrichmentChain`; the D19 recipe library; sample extension `DumAemExtInsperModelJson` as
the reference migration.

**Done.** Per task in [ROADMAP.md](ROADMAP.md). Net: the AEM Achilles'-heel god-class becomes a
composition of tested component extractors + typed per-type schemas + named enrichers, observable
(coverage/drift/provenance) and reusable across institutions via versioned recipes.

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

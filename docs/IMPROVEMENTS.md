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
- [III. AEM extraction model redesign (Track H, D26–D27)](#iii-aem-extraction-model-redesign-track-h-d26d27)
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

## III. AEM extraction model redesign (Track H, D26–D27)

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
on top of the contract D21 establishes and the content-type model D25 shipped (a template routes to
a content type with its own `DumStructuredSchema` + extractor composition; coverage/drift read per
type):
- **Derivations → enrichers (D26).** The derived fields move to the D04 enrichment chain; the
  cross-cutting normalizers become shared. Extraction emits only *sourced* fields.
- **Versioned recipes (D27).** Component extractors + content-type schemas become versioned,
  parameterized artifacts (D19), so one "course-offer" recipe applies across institutions.

**Why incremental.** D21 (wrap as-is) delivers the contract + coverage at ~zero risk and *measures*
the pain first; each Track H task then peels one layer, deliverable on its own. The risk to avoid is
building a bespoke AEM-mapping framework before the structured SPI + D04 prove insufficient — which
is why D24 (shipped) refactored the existing extension into a composed component-extractor SPI rather
than inventing a new engine, and the remaining tasks build on that seam.

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

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

## XIV Console build, lint and type gates

### XIV.2 The data-load pattern the linter refuses (D53)

Twenty-three surfaces load their data as `useEffect(() => { service.get().then(setX);
setIsNew(false) }, [id])`, and the rule is right that a `setState` in an effect body
causes cascading renders. D48 could not fix it: restructuring how twenty-three pages
fetch is a refactor with its own risk, not a side effect of turning a linter on, so
`eslint.config.js` names those files and holds the rest of the console to the rule.

The list is the debt, and it only pays down by being emptied. Each file leaves it in the
diff that fixes it, and the task finishes when the last line goes and the rule is an
error everywhere.

The pages share one pattern, so the fix is one decision applied twenty-three times
rather than twenty-three decisions.

### XIV.3 The any that spreads (D54)

Six files use `any`: the connector service's request and response payloads, the grid's
column plumbing, the locale and indexing-manager forms, the AEM wizard and the grid
adapter. Each is a contract someone has to read the backend to write down, which is why
D48 named them rather than typing them under a lint adoption.

Two of them matter more than the rest. `integration-connector.service.ts` is where the
console meets the connector API, so every caller downstream inherits `any` from it; and
`use-grid-adapter.ts` feeds `TurGridItem`, which the console-era grid renders
everywhere.

Finished when the list in `eslint.config.js` is empty and the rule is an error across
the console.

### XIV.4 Chunking that never happens (D55)

`vite.config.ts` declares a `manualChunks` splitting vendor code into `vendor-ui` and
`vendor-lib`, and Module Federation discards it on every build, saying so on stdout:
grouping its async-init-wrapped modules into one chunk creates circular dependencies
that hang the application.

So the config is dead, and the message explaining that it is dead prints on every build.
Both halves are the shape D51 removed from the census — a standing line a reader learns
to skip, sitting next to the ones that matter.

Deleting it changes no output, since none of it was applied. What it costs to keep is a
reviewer believing the console still controls its chunking, and a build log with one
more line nobody reads.

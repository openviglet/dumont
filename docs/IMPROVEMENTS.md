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

## XI The console adopts the shared bento layer

### XI.6 The chrome speaks, and not yet in this console's languages (D40)

The bento chrome the package owns speaks: the save bar says "Unsaved changes" and "Title
required", the palette names its sections, the rail labels its groups, the empty states
carry their own copy. Every one of those strings goes through `t()` with a
`defaultValue`, which is what VDS21 shipped — so a consumer with no translations renders
English rather than a raw key, and a consumer with translations gets its own languages.

Dumont has an `i18n` directory and a console that already speaks more than English. The
moment a bento rail or palette renders here, its chrome will be the one part of the page
that is not translated. English beside Portuguese in the same shell reads as a bug, and
it is the kind that ships because nobody notices their own default language.

Turing found the flip side of this in T989: two of its suites asserted the raw i18n key
and started failing the moment the package's component rendered its `defaultValue`
instead. The behaviour was correct; only the copy had changed. That is worth knowing
here because it says the strings really do arrive from the package and really can be
overridden.

Add the bento chrome namespace to this console's translations, in the languages it
already ships. Do it with the shell rather than after it, so no migrated surface is ever
the untranslated one.

### XI.7 A lint, since discipline is not an instrument (D41)

Nineteen names are declared in this console and exported by the design system under the
same names, across eleven files, and nothing anywhere fails because of it. They were
found by pointing the package's export-manifest lint at this repository, which is the
first time anyone counted.

Some of them are legitimate. `AppFooter` renders Dumont's own identity, and the user
context is bound to this product's auth service and model — those share a name and
nothing else, and the lint has an annotation for exactly that case, where the reason is
the point. `ui/table.tsx` is not legitimate and has its own line above. The rest are
console-era chrome that leaves when the console does.

What matters is not this list but the next one. Turing is the proof: an entire design
language grew inside that product while a design system sat next to it exporting console
chrome, because nothing ever failed when a component was re-declared instead of
imported. Dumont is about to put a large shared surface within reach, and the cheapest
path to one missing prop is a local copy.

Adopt the lint the package ships — `viglet-ds-check-duplicates`, which reads
`exports.json` from the installed package and ends each finding in the import that
replaces the copy — and run it in this repository's CI. Annotate what is deliberate with
its reason, so what fails is a new duplicate.

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

## XL Comments in the console source, and what misreads them

This console documents itself in its comments — most files here open with a paragraph
naming the incident the code exists for, and the census reads those same files. Two
things go wrong with that, both found while D88 retired the console-era chrome, and
neither is about what the code does.

### XL.1 The era reader still reads comments (D89)

`isBento` in `scripts/console-census.mjs` tests the file as it is on disk, so a
`chrome="bento"` written inside a comment counts as the declaration itself.
`src/BentoRoutes.tsx` is the live case: line 106 quotes the declaration while explaining
how a detail page migrates, and the reader calls the router bento-era on the strength of
that sentence. Nothing is miscounted today only because the page walk classifies
`*.page.tsx` and the router is not one.

D88 fixed exactly this in `isAdapter`, two functions above — a wrapper that had stopped
adapting still said `useSectionChrome` in its own doc comment, and every page behind it
stayed on console until the reader stripped comments first. `isBento` was left alone in
that diff because no page's era moved either way, which makes it latent rather than
absent.

The fix is the same one: `code(source)` before the test, in both the `chrome=` probe and
the import clause scan. The classifier half already has a case for this — "does not
count a commented-out declaration" — and the era half has none, which is how the two
drifted. Add the mirror case, asserting that a commented-out bento import and a quoted
`chrome="bento"` both read console.

Worth checking in the same pass whether `ERA_NEUTRAL` should still name
`AdaptiveEmptyState`: no such component has ever existed here, so that entry has never
matched anything.

### XL.2 Double-encoded comments, and the guard that would hold them (D90)

Twenty-four files under `dumont-react/src`, `test` and `scripts` hold UTF-8 that was
decoded once as Latin-1 and re-encoded, so an em-dash reads as three characters and an
accented letter as two. `src/app/nav.const.ts` carries twelve,
`test/console-census.test.ts` thirteen, and the six source forms eight apiece. The same
file can hold both spellings: `console-census.test.ts` has clean em-dashes in the middle
of a paragraph whose first and last lines are mangled, which says the damage came from
an editor that rewrote part of a file rather than from one bad save.

It is confined to comments. The locale JSON under `src/i18n/locales` is clean in both
languages and no user-facing string is affected — checked, because Portuguese is where
this would be visible and it is the first place to look.

That makes it cosmetic to a reader and not to a tool. An exact-match edit against one of
these files fails on any line carrying a mangled dash, which is what happened during D88
and cost a detour through a patch script. A codebase whose comments carry its reasoning
cannot have a third of its longest ones unquotable.

The fix is a re-encode of the affected byte sequences, which is mechanical and
reviewable as a diff with no code in it. The guard is what makes it stay fixed: a test
that walks the same roots and fails on `â€`, `Ã©`, `Ã§`, `Ã£` — the sequences that
cannot occur in correctly encoded text here — rather than a linter rule nobody
configures.

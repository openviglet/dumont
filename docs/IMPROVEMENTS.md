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

## XXV One buffer, many sessions (D69)

`JobItemBatchProcessor` is a singleton with one `TurSNJobItems buffer` field, and every
source's job items land in it. `flush(session)` drains the whole buffer and calls
`setSuccessStatus` on each item with that one session — writing `session.getSource()`
and `getTransactionId()` onto items produced by whichever source happened to be indexing
at the same moment.

Nothing in the buffer records which session put an item there, so this is not a race a
lock would close: the attribution is lost at `add` time and invented at `flush` time.
The auto-flush inside `add` has the same shape — it fires when the buffer reaches
`dumont.job.size`, under whichever session made the crossing call.

The result is a ledger that names the wrong source. A monitoring page filtered by source
shows another's documents, one transaction id groups two runs, and the index audit reads
a checksum it attributes to a source that never published it. That last one matters
most: the audit's premise is that the ledger says what *this* source published.

D68 widened the exposure rather than creating it — applying an orphan report flushes on
an operator's request, at a moment nobody chose, while a scheduled run may be
mid-buffer.

The fix is to carry the session with the item rather than pass one at drain time.
`DumJobItemWithSession` is already that shape and the queue uses it everywhere else.
Flushing then stamps each item with the session that produced it, and `flush(session)`
becomes a request to drain rather than a claim about ownership.

## XXVI A review nobody can see is not a review (D70)

D68 gave the audit's ghosts somewhere to wait: a dated report per source and site,
applied or discarded through `/api/v2/connector/orphans`. The reasoning was that
deletion should be a deliberate act by someone who read the evidence.

The console shows none of it. So the review that gates every deletion happens over curl,
against a JSON array of ids, by an operator who has to remember the report exists — and
the audit's warning in the log is the only thing that tells them. A step designed to be
deliberate becomes a step that is skipped, and the ghosts stay indexed exactly as they
did before D68.

What the surface owes is the judgement, not the data. Deciding whether a report
describes content that genuinely went away takes the count of ids against the count the
site indexes — a report naming most of the index is a failed discovery pass, not a
cleanup — plus when it was found, when it stops being applicable, and the ids. Apply and
discard are two buttons over endpoints that already exist.

Two things it must not do. It must not apply on a bulk selection across reports: a
report is per source and per site because that is the unit an operator can reason about.
And it must not hide an expired report, which is evidence that the audit found something
and nobody looked — the state worth seeing, not worth tidying away.

It belongs beside monitoring, where the other per-source evidence reads.

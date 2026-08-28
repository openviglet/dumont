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

## XXXVI The log that never started

Track E has covered a store that goes down and a row it refuses. What it has not covered
is the state before either: a store the connector never reached, and an endpoint that
cannot tell.

### XXXVI.1 Started, connected to nothing (D83)

D75 covered a store that goes down while running, D78 and D82 a row it refuses to
accept. Neither covers a store that was never reached at all, and that path is quieter
than both.

`DumMongoDBAppenderBase.start()` calls `super.start()` first and connects afterwards. So
when the URI is malformed or the driver rejects it, the appender is already marked
started while `collection` stays null. Every later `append` hits the `collection ==
null` guard and returns — correctly, in the sense that it does not throw, and
disastrously, in the sense that it never says anything. The only record is one
`addError` to Logback's status manager, which is not the application log and which
nobody reads unless they know to ask.

The result is an appender that is enabled, running, and permanently writing nothing, for
the life of the process. It cannot recover either: nothing retries the connection.

The Redis base gets the ordering right by accident of style — it calls `super.start()`
only after the client is built, so a failure leaves the appender unstarted and Logback
at least complains about appending to a stopped appender. That is better and still not
good: the complaint goes to the same status manager.

Two things are needed. Connect before declaring the appender started, so the two bases
agree; and report a startup failure through the application log the way
`DumLogWriteFailureReporter` reports a write failure, on the routing D78 already added.
A log that cannot start should be as loud as one that stops.

### XXXVI.2 A ping that asks the wrong component (D84)

D75 split `logging/status` into `configured` (what the properties say) and `enabled`
(what a ping says), so that a store which is named but unreachable could not report
itself healthy. The ping is honest about what it asks. It just asks the wrong component.

`DumLoggingAPI` builds its own client from `dumont.mongodb.uri` and pings that. The rows
are written by a Logback appender configured independently, from the same properties but
through `logback-spring-*.xml`, with its own client and its own lifecycle. The endpoint
therefore reports whether *the reader* can reach the store, and says nothing about
whether *the writer* ever did.

The two can disagree, and the interesting cases are exactly the ones an operator hits. A
profile that never activated the appender, a `dumont.logging.engine` naming an engine
whose `dumont.*.enabled` flag is false, or the startup failure D83 describes: in all
three the API pings successfully, answers `enabled: true`, and the history is empty
because nothing was ever written. The screen then shows a healthy log with no rows —
which is precisely the "failure that reads as an absence" this track spent D73 to D82
removing, reintroduced one level up.

What `status` should report is the writer's state, or both states distinctly. The
appenders know whether they started and whether their last write succeeded; that is the
fact the endpoint is being asked for. Worth taking with D83, which gives the appenders
somewhere to record it.

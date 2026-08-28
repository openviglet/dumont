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

## XXXV The general log, and what the indexing pair was fixed without

D73 to D79 went through the indexing appenders twice. The pair beside them writes the
server log, is attached to root, and was never read.

### XXXV.1 The same silence, on the log that holds everything (D82)

D78 gave the indexing appenders a throttled failure report and routed it away from the
store it complains about. It did not touch the general pair, and they carry the same two
defects it named.

`DumMongoDBAppender.append` calls `collection.insertOne` under no catch.
`DumRedisAppender` catches and logs at `INFO` with `e.getMessage()` alone. So the server
log — the one that holds every WARN and ERROR the connector has ever emitted — stops
recording the moment its store does, and says so either three times to Logback's status
manager or once per row at a level nobody greps.

This matters more than the indexing case, not less. These appenders are attached to
**root** in the `mongo` and `redis` profiles, so what stops is everything. And an
operator investigating an incident is exactly the person who will open that log, find it
ends an hour before the incident, and read the gap as quiet.

`DumLogWriteFailureReporter` already exists and is built for this: one line per outage,
a count while it lasts, a total on recovery. Both classes should hold one, and the
`com.viglet.dumont .commons.logging` routing D78 added to both profiles already sends
its output to `FILE` and `CONSOLE` rather than back into the failing store — so the
wiring this needs is done.

Worth doing with D80 and D81, which edit the same two methods.

### XXXV.3 Two queues, one of them unbounded (D81)

`DumRedisAppender` builds `Executors.newFixedThreadPool(2)` and submits every logging
event to it. The logback profile already wraps the appender in an `AsyncAppender`. So an
event crosses two queues, and only the first one is bounded.

`newFixedThreadPool` uses an unbounded `LinkedBlockingQueue`. The `AsyncAppender` in
front of it is configured `queueSize=512`, `discardingThreshold=0`, `neverBlock=true` —
a deliberate policy that says: buffer this much, and drop rather than stall the
application. That policy is now unreachable. The appender's `append` returns as soon as
the task is submitted, so the async queue always looks empty and never discards; the
real backlog accumulates in a queue with no ceiling and no metrics, and under a Redis
slowdown it grows until the heap says otherwise.

Shutdown has the mirror-image problem. `stop()` calls `executor.shutdown()`, which does
not wait, and then `super.stop()` closes the Redis client. Tasks still queued then run
against a closed pool and fail — so the log lines from the end of the run, which are the
ones an operator most often wants, are the ones most likely to be lost. They fail into
the `INFO` catch D82 is about.

The second executor should go. An `AsyncAppender` is already the right mechanism and it
is already configured; writing synchronously beneath it restores the bound and the
discard policy in one move. If a worker is genuinely wanted, it needs a bounded queue, a
rejection policy chosen on purpose, and an `awaitTermination` before the client closes.

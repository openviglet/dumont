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

## XVIII A journal under the webhook (D62)

`DumAemWebhookController` receives Adobe I/O CloudEvents over HTTP and is push-only.
Adobe I/O offers both directions — a push registration, and a **journal**: an ordered
list of the same events, pulled at the consumer's own cadence behind a cursor.

Push alone has no recovery. A webhook lost to a 502, a rolling restart, a network
partition or an expired certificate is gone — Adobe retries for a bounded window and
then drops it. The content it described stays at whatever version the index last saw,
and nothing in the connector knows, because a missed event leaves no trace.

The scheduled audit does eventually notice: `discoverContentIds` re-enumerates the
source and files a NOT_PROCESSED record for anything unqueued. But that is a full
re-discovery, it runs on a cron most deployments set to hours or leave off, and it
corrects nothing that was published and then unpublished between two runs.

A journal consumer closes the gap cheaply: persist the cursor, poll on a short interval,
and feed the same handler the webhook already feeds. The cursor makes recovery exact
rather than best-effort — replaying after downtime is a matter of not advancing it — and
journaling is enabled automatically on every Adobe Developer Console event registration,
so it costs no extra setup for the customer.

The two paths coexist: the webhook stays the low-latency route, the journal is the floor
under it.

## XIX Send the schema before the documents (D63)

`DumStructuredSchema` is Dumont's schema-as-code: an ordered, immutable declaration of
every field a structured source publishes. It already produces the
`TurSNJobAttributeSpec`s attached to each document and enforces the absent-versus-empty
grounding rule (D03) at the record boundary.

Nothing sends that declaration ahead of the documents. The connector starts indexing and
Turing's indexing-time auto-create path invents each field the first time a document
carries it, inferring the type from a single value. A field declared multi-valued but
arriving with one value in the first document is created single-valued. A field the
schema marks as a facet is created without it. An optional field absent from the early
documents does not exist until it appears — and is then guessed too.

Turing has accepted the declarative form since T382: `POST /api/sn/manifest` converges a
site and its field schema idempotently, rejects an undeclared breaking change with a
409, and offers a dry-run plan. Re-posting the same manifest is a no-op, so it is safe
to send on every run.

The missing step is the call itself, once per source before the first document, built
from the schema the connector already holds. It waits on a manifest client in
`turing-java-sdk`, filed there as T1030, since hand-rolling the HTTP would duplicate the
auth and CSRF the SDK already does for indexing. With it, configuring a source in Dumont
provisions the site in Turing.

## XX The third comparison the audit cannot make (D64)

`DumConnectorContentAuditTask` runs on a cron, calls `discoverContentIds` for each
source, files a NOT_PROCESSED record for anything the connector never queued, and drops
records whose environment no longer exists. It is the only thing in either product that
goes looking for missing content.

It compares two sets, and neither of them is the search index. The source is one;
`DumConnectorIndexingModel`, the connector's own ledger, is the other. So the audit
answers "did we queue everything the source has" and cannot answer "did everything we
queued actually land". A document the ledger marks processed whose write to Turing
failed, or one removed from the source whose delete never arrived, satisfies the audit
completely while the index is wrong.

That failure is silent and permanent. The index keeps answering queries — with a stale
document or a ghost among the results — and the only repair anyone has is a full
reindex: hours of embedding cost to fix a handful of records nobody can name.

The third comparison needs a read Turing does not offer today: a paged enumeration of
the ids the index holds for a source, with a version or checksum per id, filed there as
T1029. Once that exists, this task grows the comparison and reports the three
differences. Applying the repair stays here, since the connector already owns the queue
and the retry.

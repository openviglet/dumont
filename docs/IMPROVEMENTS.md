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

## XXI An exported source is not an importable one (D65)

`DumAemExchangeProcess` writes an export from a hand-built `DumAemSourceExchange`, and
that builder sets nine fields: id, endpoint, attributes, defaultLocale, localeClass,
indexConditionClass, authorSNSite, publishSNSite, author, publish and name.

The import side, `setSource`, reads seventeen. Everything the two do not have in common
is silently lost: `rootPath` and `contentType`, which are what scopes a source at all;
`username` and `password`; `authorURLPrefix` and `publishURLPrefix`; `oncePattern`;
`deltaClass` and `dependencyClass`; and the `localePaths` and `models` collections the
exchange bean declares and the exporter never fills.

So an export is not a backup and not a way to move a source between instances. Importing
one produces a source with a name, an endpoint and a field list, no scope, no
credentials, and no model bindings — it does not fail, it indexes nothing, and the
operator has to rediscover by hand what the original was configured with.

The gap is invisible because both halves compile and the round trip appears to work: the
source list shows the imported source, and the failure only surfaces on the first
indexing run.

The fix is to make the two directions one declaration rather than two lists that drifted
— the exporter should not be able to omit a field the importer reads. Whatever shape
that takes, the test is a round trip: export a fully configured source, import it into
an empty instance, and assert the two entities are equal field for field, including the
locale paths and the models.

## XXII The last synchronous write in an effect (D66)

`pnpm run lint` exits 1. `useConnectorProvider` writes state synchronously twice inside
its effect body — `setState({provider: null, loading: false})` on the no-id branch, and
`setState({provider: null, loading: true})` before the fetch — and
`react-hooks/set-state-in-effect` is an error.

The gate is documented as having no exemptions. Three rules carried named-file debt
lists until D53, D54 and D56 emptied each one, and each list was then deleted rather
than kept at zero, precisely so the next violation would fail the build instead of being
absorbed. This one is failing the build, which means the gate is working and the repair
is owed.

Both writes are the shape D56 established as the fix, not the loophole: neither is
state. `loading` is derivable from whether an answer for the current `integrationId` is
in hand, and the reset before the fetch exists only because the answer is not keyed by
the question it answers. Store the resolved provider against the id that produced it and
the effect has nothing to write on entry — an answer to another id is not this one's, so
no reset is needed and no stale render is possible.

Do not wrap either write in `void (async () => …)()`. The rule cannot see through it,
but the write is still synchronous, and the project treats that as a loophole rather
than a fix.

Done when `pnpm run lint` exits 0 with no new disable comment, and the hook still
reports `loading` correctly when `integrationId` changes mid-flight.

## XXIII The two delivery surfaces D61 left (D67)

D61 gave a source a fetch strategy and one publish-tier implementation: `.model.json` on
the delivery host, whose `:children` carries the traversal and whose page model carries
the metadata. That covers pages served by Core Components, which is most of a Sites
deployment and none of two others.

A source whose content type is a Content Fragment gets nothing from `.model.json`.
Neither does a `dam:Asset` source. Both are indexed today by pointing the connector at
author with a service user — the situation D61 exists to remove — and on AEM as a Cloud
Service that means an authoring credential held for content that is already published.

Adobe serves both from the publish tier. The Content Fragment OpenAPI lists and reads
fragments (`/adobe/sites/cf/fragments`), cursor-paginated, with the fragment's model and
field values in the response. The Assets Search API answers on the delivery host for
assets and their metadata. Neither is a Sling servlet and neither is dispatcher-blocked.

The work is two more implementations behind the strategy the seam already selects,
chosen by the source's content type rather than by a new setting: `DumAemDeliveryUtils`
becomes the page implementation beside a fragment one and an asset one. The translation
each owes is the same one D61 wrote — produce the node shape `DumAemObjectGeneric`
parses, so nothing downstream of the fetch changes.

Note the Assets Search API is a POST, and the connector's shared fetch seam is GET-only;
that seam has to grow before an asset source can use it.

## XXIV Naming a ghost is not removing it (D68)

D64 made the audit report three differences and repair two of them. The third, a
document still in the index after leaving the source, is logged with its id and left
alone.

That was the right default. An orphan is only an orphan if the discovery pass that
failed to mention it was complete, and a pass that under-reports — a flaky gateway, a
paginated listing that returned early — makes every unlisted id look like a ghost.
Deleting on that evidence is the failure D33 and D34 exist to prevent, one step further:
not skipping a deindex sweep but performing one against a lie.

But the operator is left with a warning naming ids and no supported way to act on it.
The repair is a manual deindex per id through the indexing manager, which is the state
D64 was meant to improve on.

What is missing is a deliberate step, not an automatic one: the audit persists the
orphan set it found, and an operator reviews and applies it. That shape is what makes
deletion safe — the evidence is dated and readable before anything is removed, and a
source that was merely unreachable yields a report an operator recognises and discards.

Two constraints. The apply path must reuse the deindex the connector already owns rather
than delete behind its back, so ledger and index stay in step. And an orphan set must
expire: applying a week-old report to a source since reindexed would delete live
content.

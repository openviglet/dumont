# Dumont — strategic documentation index

Suite of documents that guides the evolution of **Dumont**, the Viglet **connector**
layer (it crawls/ingests content from external sources and publishes it into
**Turing** for search, faceting and RAG). Same model as the sibling Turing docs
(`turing/2026.1/docs`) and the cursarei roadmap suite.

## Where to start

1. [STRATEGY.md](STRATEGY.md) — positioning, what Dumont is (and is not), the
   structured-source bet, and why cursarei is the proving ground (~5 min).
2. [ROADMAP.md](ROADMAP.md) — the executable backlog in tracks, with deliverables,
   dependencies and "done" criteria.
3. [IMPROVEMENTS.md](IMPROVEMENTS.md) — the design rationale (what & why) behind each
   active task, when you need the spec, not just the status.

## Documents

| Document | Holds | When to read |
|----------|-------|--------------|
| [STRATEGY.md](STRATEGY.md) | Positioning, moat, the two bets (ingestion + adoption/trust), non-goals | Understand the "why" |
| [ROADMAP.md](ROADMAP.md) | Tracks A–H with stable `D##` task IDs and done criteria — **active backlog only** | Decide the "what" and "when" |
| [CHANGELOG.md](CHANGELOG.md) | Shipped ledger — one line per shipped `D##` task (what + where + commit) | See what already shipped |
| [IMPROVEMENTS.md](IMPROVEMENTS.md) | Design rationale per active `D##` task | Implement the "how" |
| [STRUCTURED_VS_TRADITIONAL.md](STRUCTURED_VS_TRADITIONAL.md) | Conceptual reference: the two ways to model a source, when to use each, market direction | Understand the structured-source bet in depth |

## Workflow

- Each task has a **stable ID**: `D##`. Reference it in commits and issues
  (e.g. `feat: charset auto-detect in web-crawler (D05)`).
- Planned item → lives in **ROADMAP** as `[ ]`. IDs are never reused.
- A task that needs another done first carries an **`↳ Depends on:`** line.
- Design rationale (the what & why behind a task) → **IMPROVEMENTS.md**; remove a task's
  detail there once it ships (`git log` is authoritative history).
- **When a task ships → move its one-liner from ROADMAP to [CHANGELOG.md](CHANGELOG.md)**
  and delete it from ROADMAP (active backlog only). See `agents.md` → "Roadmap & docs
  maintenance".
- Strategy/positioning change → update **STRATEGY.md**.

## Companion docs in sibling repos

This track is cross-repo. The demands here originate from the **cursarei → Viglet**
migration and are mirrored in the products that consume/feed Dumont:

- **cursarei** — `docs/roadmap/ROADMAP.md`, track `V##` (the migration that drives
  these requirements; the structured catalog being indexed).
- **Turing** — `turing/2026.1/docs/ROADMAP.md`, **Block R** + `IMPROVEMENTS.md` §XIX
  (the indexing/search-side counterparts: conservative grounding, schema-as-code,
  hybrid SN ranking, similar-documents).

> **Grounding rule** (carried from cursarei): a connector must publish a field
> **only when the source declares it** — an absent field is *omitted*, never sent
> as an empty string. This is a correctness contract, not a nicety: empty strings
> pollute facets and break "field present?" logic downstream.

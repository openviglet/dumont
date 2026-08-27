# Dumont — Roadmap

Executable backlog in tracks. Each task has an objective, the touch points in the
code, and a "done" criterion. The positioning that motivates these tracks lives in
[STRATEGY.md](STRATEGY.md).

Legend: `[ ]` pending. Each task has a **stable ID** (`D##`); IDs are never reused.
A task that requires another done (or at least partial) first carries an
**`↳ Depends on:`** line; tasks without it have no hard prerequisite. The inverse
relations (what a task *enables*) live in the task text.

> **Origin.** This track is driven by the **cursarei → Viglet** migration (cursarei
> `docs/roadmap/ROADMAP.md`, track `V##`, task **V05** "Crawler → Dumont"). The
> indexing/search-side counterparts are in **Turing** (`turing/2026.1/docs`,
> **Block R** + `IMPROVEMENTS.md` §XIX). cursarei is the reference workload:
> 120+ Brazilian university catalogs, ~4,000 courses.

> **Grounding contract** (applies to every task that produces a document): publish a
> field **only when the source declares it** — an absent field is *omitted from the
> `TurSNJobItem`*, never sent as `""`. Empty strings pollute facets and break
> downstream "field present?" logic. Dumont never invents a value to fill a schema.

---

## Track A — Structured-source connector framework

> **Objective**: a first-class way to model sources whose value is their *structure*
> (tabular listings, per-item detail pages with a typed schema), **above** the
> generic web-crawler link-spider. Once this lands, indexing a new structured source
> is a small declarative adapter + a field manifest, not a bespoke stack.

> **Shipped (see [CHANGELOG.md](CHANGELOG.md)):** **D01** Structured-source SPI
> (`discover()/extract(id)→DumStructuredRecord` + schema-as-code + generic publish
> loop) · **D02** Declarative extraction strategies (Sitemap / HtmlListing /
> TabularListing) · **D03** Schema-as-code + conservative grounding enforced
> end-to-end · **D04** Pluggable enrichment stage (grounding-safe derive/normalize +
> config-gated, cached LLM classification). All enable the tasks below.

## Track B — Web-crawler hardening (real-world sources)

> **Objective**: close the gaps that make the current `DumWCPluginProcess` fail on
> ordinary non-English, JS-rendered or rate-limited sites — independent of cursarei.

> **Shipped (see [CHANGELOG.md](CHANGELOG.md)):** **D05** Charset auto-detect
> (HTTP `Content-Type` header / `<meta charset>` / BOM → UTF-8 default; enables D07) ·
> **D06** Sitemap & robots awareness (robots.txt → sitemap entry-point discovery +
> capped `Crawl-delay` / per-source request delay; enables D08, D12) · **D07** Pluggable
> fetcher SPI (`DumWCFetcher` seam + per-source selection + UA strategy + adaptive
> 429/403 back-off; enables D11, pairs with D12) · **D08** Bounded parallel crawl per
> source (thread-safe queue + bounded worker pool, configurable cap; per-host delay
> floor preserved via a shared rate gate) · **D09** Safe-run policy + failure report
> (a zero-yield run keeps the previously indexed data; every run logs a per-source
> report of blocks / dead URLs / other failures) · **D12** Incremental fetch
> (HTTP-conditional re-crawl: persisted `ETag`/`Last-Modified` + conditional GETs →
> `304` skip; sitemap `<lastmod>` skip *before* fetch; skipped pages kept alive as
> unchanged through the existing pipeline).

This track has no remaining pending tasks.

## Track C — cursarei reference connector

> **Objective**: prove the framework on the real workload, and honor the migration's
> fallback/parity discipline. This is the bridge (sidecar) → native path from
> [STRATEGY.md](STRATEGY.md).

> **Shipped (see [CHANGELOG.md](CHANGELOG.md)):** **D10** Catalog bridge (sidecar) connector
> (`catalog-plugin` over the structured SPI — ingests already-normalized catalog JSON per
> institution over HTTP, grounding-safe, mandatory `title`/`url`; the bridge that takes the Turing
> search/RAG wins live while the Python engine stays the anti-WAF fallback) · **D11** Leg-by-leg
> native migration (per-source **parity gate** `DumParityGate` — count within tolerance + canonical-URL
> coverage vs the Python run; the shared SIGAA-federal recipe as the first leva; a catalog source flips
> sidecar→native only when the gate passes, hardest anti-bot sources stay on the sidecar).

This track has no remaining pending tasks.

## Track D — Adoption & authoring (lower the cost of a new source)

> **Objective**: make "index a new structured source" a guided, minutes-long task —
> not a dev task. Track A turns a source into a declarative manifest; this track makes
> *producing and validating that manifest* cheap and safe. This is the adoption layer
> that turns the framework into a product capability — see [STRATEGY.md](STRATEGY.md)
> §"The second bet".

> **Shipped (see [CHANGELOG.md](CHANGELOG.md)):** **D13** AI-assisted source inference
> (URL → shape classification + editable strategy/field-manifest draft over Turing's LLM
> surface; a confirmed tabular draft materializes + indexes end-to-end, grounding-safe) ·
> **D14** Dry-run preview & schema diff (bounded sample → per-field coverage + schema/document
> diff vs the last indexed baseline, writes nothing).

This track has no remaining pending tasks.

## Track E — Data quality & trust (make Dumont trustworthy in production)

> **Objective**: surface silent degradation and make every published field auditable.
> This is what the parity gate (D11) and the RAG consumer both need — and the lever that
> moves Dumont from "a pipe that ran" to "a pipe you can trust".

> **Shipped (see [CHANGELOG.md](CHANGELOG.md)):** **D15** Field-coverage & drift monitoring
> (per-run per-field coverage captured at `indexAll`, persisted history per source, drift flag
> when a field's coverage drops sharply between two runs; surfaced on `DumConnectorMonitoringApi`
> + a `dumont-react` panel with an `AiSummaryPanel` health summary). **D16** Field-level provenance
> (each published field tagged with its origin and carried into the `TurSNJobItem` as a compact
> `dum_provenance` attribute; per-source last-seen history so a dropped field stays auditable).
> Complements D09.

- 📋 **D64** (deps: Turing T1029) **The content audit never compares against the search index** — DumConnectorContentAuditTask reconciles the source against its own ledger only, so a document that never landed in Turing stays invisible. → §XX

## Track F — Enrichment & semantics (the differentiator)

> **Objective**: turn D04's enrichment hook from "one derived field" into the layer that
> makes Dumont a *data exchange* platform rather than a pipe — semantics, dedup and reusable
> normalization. All grounding-safe: enrichment may normalize / classify / derive, **never**
> fabricate a sourced attribute.

> **Shipped (see [CHANGELOG.md](CHANGELOG.md)):** **D17** Embedding-quality for structured content
> (field-role projection onto Turing's canonical embedding keys + RAG-status visibility) · **D18**
> Cross-source entity resolution & dedup (canonical-id merge, grounding-safe) · **D19** Reusable
> recipe & taxonomy library (versioned `TabularListing` recipes + canonical taxonomy maps applied by
> reference; a recipe/taxonomy bump re-applies to every bound source). D19 accelerates D11 and ties
> into D27.

This track has no remaining pending tasks.

## Track G — Connector convergence (loose-map connectors → structured SPI)

> **Objective**: the Track A/D/E/F machinery — grounding-by-construction (D03), dry-run
> preview (D14), field-coverage & drift (D15), enrichment (D04), provenance (D16) — only
> reaches sources built on the structured SPI. AEM, DB and Assets each already do
> "discover ids → extract per-item fields → publish" by hand, just without a declared
> schema or the typed `extract(id)` contract. Bring each **under the same SPI via a thin
> adapter** (schema derived from the connector's own field config + `discover()` over its id
> enumeration + `extract(id)` over its single-item fetch) so they inherit that whole surface
> **without a rewrite**. This *realizes* the existing non-goal ("the framework sits beside
> the AEM/CMS connectors under the same SPI") — it adapts, never replaces. The web crawler
> stays the generic link-spider the framework sits *above* and is deliberately not converted
> (its lightweight coverage signal shipped as D23); structured sites it serves migrate to D02
> strategies.

> **Shipped (see [CHANGELOG.md](CHANGELOG.md)):** **D23** Web-crawler coverage adapter (no
> conversion — the WC connector's declared `DumWCAttributeMapping` names treated as an implicit
> field set + per-page field-presence signal into the D15 history, plus a documented WC → D02
> migration path).

This track has no remaining pending tasks.

## Non-goals (do NOT add as tasks)

- General-purpose open-web crawling at breadth (Scrapy/Playwright-farm scope). Dumont
  crawls **to feed Turing**; fidelity of structured sources is the goal, not coverage
  of the web.
- Any path that fabricates field values to satisfy a schema (violates the grounding
  contract).
- Replacing the AEM/CMS connectors — the structured-source framework sits beside them
  under the same SPI. (Restructuring a connector's *extraction/mapping* model under that SPI —
  Track G adapters, Track H's AEM redesign — is in-scope; ripping out its crawl/auth/delta/
  navigator is not.)
- **AI-assisted authoring that auto-publishes or guesses (D13).** Inference drafts a
  manifest a human confirms; it never indexes on its own and never fills a field it could
  not ground.
- **Enrichment that fabricates (Track F).** Embeddings, entity resolution and taxonomy
  normalization run as enrichment over sourced data — they may derive or merge, never
  invent a sourced attribute.

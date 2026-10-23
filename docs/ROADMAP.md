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

## Track H — AEM extraction model redesign (component-oriented, per-type, recipe-driven)

## Track J — Console surfaces move to bento chrome

## Track K — Frontend build and lint gates

## Track O — Structured sources over paged JSON APIs

## Track P — Connector run control

## Track Q — Builds from released dependencies

## Track R — API access and credential handling

## Track D — Adoption & authoring

## Track A — Structured-source connector framework

- ⏳ **D144** (deps: SDK custom-sort release) **A structured schema cannot declare custom facets or sorts, so the Turing manifest Dumont posts never carries them** — Custom sorts need a turing-java-sdk release with TurSNManifestCustomSort; 2026.3.6 has facets only. → §XLVIII

## Track S — The WordPress connector plugin

- 📋 **D145** (deps: —) **The WordPress plugin has no build, tests or release: no composer.json, no CI and no versioned artefact to install** — A site cannot pin a plugin version or install it with Composer or WP-CLI, so every consumer copies a folder and drifts from the source. → §XLIX.1
- 📋 **D146** (deps: D145) **Saving a post calls Turing synchronously up to three times, and a Turing outage blocks the editor or drops the change** — Three hooks index the same post with a 30s timeout and no retry, so an outage costs the editor time and loses the update silently. → §XLIX.2
- 📋 **D147** (deps: D145) **The plugin indexes only title, body, categories and tags; taxonomies and meta never become typed fields or facets** — A site with events, jobs or books cannot filter by date, area or author, and the SN site never learns the fields because the plugin does not provision them. → §XLIX.3
- 📋 **D148** (deps: D145) **Two WordPress sites indexing into one SN site overwrite each other, since every document id is wp-<post ID>** — A group of sites cannot share one unified search without silently replacing each other's documents. → §XLIX.4
- 📋 **D149** (deps: D146, D148) **There is no WP-CLI command, and nothing removes from Turing what was deleted while the plugin was off** — A first load, a reindex or a cleanup can only be done by clicking an admin button batch by batch, and stale documents stay searchable. → §XLIX.5
- 📋 **D150** (deps: D146, D147) **The settings page is a flat form with no setup flow, mapping view, index health or per-post index state** — An editor cannot tell whether a page is in the index, why it is not, or whether the connection works, so every doubt becomes a support call. → §XLIX.6
- 📋 **D151** (deps: D145) **The Turing URL and API key can only be set in wp-admin, and the key is stored in plain text in wp_options** — A containerised site configured from its environment cannot set the plugin up at deploy, and the secret lands in every database backup. → §XLIX.7
- 📋 **D152** (deps: D147, D151) **The search side is a PHP template that hijacks template_redirect and ignores block themes and the JS SDK** — A block theme loses its header, footer and styles on the results page, and no SDK feature (autocomplete, spell-check, answers) reaches the visitor. → §XLIX.8
- 📋 **D153** (deps: D149, D150, D152) **The WordPress connector docs describe Host, Port and Path settings and buttons the plugin does not have** — Someone following docs.viglet.org looks for fields that are not on the screen, the first page a WordPress adopter reads. → §XLIX.9

## Done when — D144

- **A declared custom sort converges on the site with the fields** The jobs schema
  declares "closing soonest"; a run against a fresh site leaves that sort on it, and a
  second run logs no customSortsChanged.

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

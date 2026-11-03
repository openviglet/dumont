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

## Track A — Structured-source connector framework

## Track S — The WordPress connector plugin

- 📋 **D227** (deps: D225 ✅, D226 ✅, WordPress.org: first review approved and SVN access granted) **A release tag publishes the zip on GitHub only, so the plugin cannot be found or updated from wp-admin** — Sites update plugins from the directory, and a hand-run SVN commit per release gets skipped. → §XLIX.83
- 📋 **D282** (deps: D276 ✅, D278 ✅, English video published on YouTube by Alexandre) **The video is not on the site, the docs, the README or the release** — A release asset downloads instead of streaming, so the pages embed YouTube and carry the transcript that search reads. → §XLIX.125
- 📋 **D287** (deps: Viglet Turing T1133: copilot reads a partitioned key's partition) **On an SN site shared by several WordPress sites, Build my kit and the copilot stage other sites' products** — Search filters by site key, the generative calls do not, so a kit can sell another site's stock. → §XLIX.128
- 📋 **D298** (deps: —) **The video script lays one language's narration over the other store's recording without a word** — The recording does not say which store it played, so only someone watching the result notices the mismatch. → §XLIX.141

## Track T — Releases and links match what the site describes

## Done when — D246

- **The released video is narrated and embedded on the site and docs** The plugin
  release carries story.mp4 with narration in English and pt_BR whose voice licence is
  recorded, and the site's WordPress page and the docs embed it with both caption
  tracks.

## Done when — D265

- **Every product and guide has a photo** npm run demo:photos reports every photo
  current and the seed draws none.

## Done when — D261

- **The English video is narrated too** The English takes are bought from the current
  script and story:video --language en carries them.

## Done when — D290

- **The project opens in DaVinci Resolve with no media offline** Import story.en.fcpxml
  in the Resolve version in use: every clip links, the chapter markers sit where
  chapters.txt says, and the timeline's length matches the MP4's to within one frame.
  Look at it in Resolve; a well-formed XML file is not the check.

## Done when — D292

- **The typed query and the answer's sources are readable at phone size** Frames from
  each chapter, scaled down to 390 px wide, show the query text and the answer legibly.
  The moves are transform keyframes in the Resolve project that can be edited there.

## Done when — D281

- **The walkthrough is narrated in a plain voice, one take per chapter** Each of the six
  chapters has a line in demo/narration and a current take, spoken over its action as
  the story's are; the voice is not Ana's.
- **The walkthrough is assembled into a captioned English video with cards**
  story-video.mjs (or a sibling) cuts walkthrough.webm into a 2-3 minute video with its
  cards, captions and chapter list, closing on the repository, the docs and the
  Playground.

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

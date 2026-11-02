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

- ⏳ **D144** (deps: SDK custom-sort release) **A structured schema cannot declare custom facets or sorts, so the Turing manifest Dumont posts never carries them** — Custom sorts need a turing-java-sdk release with TurSNManifestCustomSort; 2026.3.6 has facets only. → §XLVIII

## Track S — The WordPress connector plugin

- 📋 **D227** (deps: D225 ✅, D226 ✅, WordPress.org: first review approved and SVN access granted) **A release tag publishes the zip on GitHub only, so the plugin cannot be found or updated from wp-admin** — Sites update plugins from the directory, and a hand-run SVN commit per release gets skipped. → §XLIX.83
- 📋 **D276** (deps: D275 ✅, Turing demo image carrying T1190 and T1191) **Only the pt-BR video exists, and every page that will embed it is in English** — The footage differs by language, so English is its own recording, and the primary video the embeds and cuts start from. → §XLIX.119
- 📋 **D277** (deps: D275 ✅, Turing demo image carrying T1190 and T1191) **The pt-BR video shows Site: localhost in the kit chapter and has the old cards** — It is ready and paid for, and Portuguese searches face less competition, so it is published on its own once clean. → §XLIX.120
- 📋 **D279** (deps: D276, D278 ✅) **Each thing the plugin does is only findable inside one 7.5-minute video** — A clip per strong chapter is one more entry point per search someone makes, built from the same recording. → §XLIX.122
- 📋 **D280** (deps: D279) **The story is recorded only at a desktop viewport, which crops badly to 9:16** — A second scripted run at a phone viewport gives vertical cuts that show whole results, at no new narration cost. → §XLIX.123
- 📋 **D281** (deps: D276) **No video shows a developer how the plugin is installed, connected and indexed** — The story speaks to shop owners; whoever puts the plugin on a client site needs install, settings and insights instead. → §XLIX.124
- 📋 **D282** (deps: D276, D278 ✅, English video published on YouTube by Alexandre) **The video is not on the site, the docs, the README or the release** — A release asset downloads instead of streaming, so the pages embed YouTube and carry the transcript that search reads. → §XLIX.125

## Track T — Releases and links match what the site describes

## Done when — D144

- **A declared custom sort converges on the site with the fields** The jobs schema
  declares "closing soonest"; a run against a fresh site leaves that sort on it, and a
  second run logs no customSortsChanged.

## Done when — D182

- **The e2e job passes on a push, with linux baselines committed** A run of the job on
  GitHub passes, and the tests/e2e/snapshots/linux/ set it wrote on its first run was
  reviewed by eye and committed, so a later visual change fails the job.

## Done when — D190

- **Each site key holds its own partitioned API key** Provisioning stores a key bound to
  the site's partition; on a multisite stack (E2E_MULTISITE=1) site A's key never reads,
  lists or deletes a site B document.
- **Features Turing refuses on a partitioned key turn off and say so** Health names each
  one Turing answers 403 on such a key, and none falls back to an unbound key.

## Done when — D225

- **Contributors names the plugin owners' real wordpress.org accounts** Each name in
  readme.txt's Contributors line opens a profile on profiles.wordpress.org.
- **The readme passes the wordpress.org readme validator**
  wordpress.org/plugins/developers/readme-validator/ reports no error on src/readme.txt,
  and the zip holds nothing the directory forbids.

## Done when — D229

- **Plugin Check reports no warning, and the CI job fails on one** wp plugin check
  viglet-dumont lists nothing once the plugin and readme titles pass the trademark rule;
  the plugin-check job then treats warnings as failures.

## Done when — D246

- **The released video is narrated and embedded on the site and docs** The plugin
  release carries story.mp4 with narration in English and pt_BR whose voice licence is
  recorded, and the site's WordPress page and the docs embed it with both caption
  tracks.

## Done when — D196

- **turing-demo answers tarnfell** https://turing-demo.viglet.org/api/sn/tarnfell/search
  answers with the seeded products after the cloud host is deployed.

## Done when — D265

- **Every product and guide has a photo** npm run demo:photos reports every photo
  current and the seed draws none.

## Done when — D261

- **The English video is narrated too** The English takes are bought from the current
  script and story:video --language en carries them.

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

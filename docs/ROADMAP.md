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

- 🛠 **D171** (deps: D169 ✅, Turing T1143) **Authors get no help from Turing while writing: no excerpt draft, duplicate check, link ideas or audience fit** — The excerpt draft shipped; audience fit is still missing: no per-persona content-fit score shows for a post on a site whose agent has personas. → §XLIX.30
- 🛠 **D173** (deps: D162 ✅, D164 ✅) **WooCommerce products index as plain posts, with no price, stock or attribute facets and no shopping copilot** — Product cards sell from the results now; the catalog copilot and its add_to_cart action still have no block. → §XLIX.34
- 🛠 **D174** (deps: D169 ✅, D186) **No test exercises Turing's generative features against a real LLM: grounding, injection, speed and cost go unchecked** — The chat and answer checks run but find no source: retrieval misses the seeded posts until the manifest turns the site's GenAI on (D186). → §XLIX.26
- 📋 **D175** (deps: D169 ✅, D174 🛠) **Visitors cannot ask the site a question in conversation, though Turing's RAG chat answers from its posts** — A visitor with a question browses or leaves, while a cited chat over the site's own content is one block away. → §XLIX.27
- 🛠 **D178** (deps: D176 ✅) **A site owner cannot see top searches, zero-result queries or clicks from wp-admin** — The screen and widget shipped; the period's AI summary is missing, and nothing says when Turing's metrics are off. → §XLIX.32
- 📋 **D179** (deps: D166 ✅, D170 ✅, D171 🛠, D172 ✅, D175, D177 ✅, D178 🛠) **Turing's features on WordPress cannot be seen together, tried by a prospect or filmed for the docs** — Each block ships alone, so nobody sees what Turing brings to a WordPress site. → §XLIX.35
- 📋 **D183** (deps: D163 ✅) **The search block guesses a wildcard into instant searches, since Turing matches whole words only** — A phrase or field query typed into the box gets a stray asterisk, and wildcard terms lose stemming and highlight weight. → §XLIX.39
- 📋 **D186** (deps: D169 ✅) **Provisioning cannot turn a site's generative AI on, so answers stay off until a console visit** — A fresh install has chat but no cited answers, and the admin must find a setting in another product. → §XLIX.42
- 📋 **D187** (deps: D172 ✅) **Search tuning has no boosts: newer, sticky or chosen post types cannot be ranked up from wp-admin** — An events or news site cannot put recent posts first without Turing's console. → §XLIX.43
- 📋 **D188** (deps: D176 ✅) **Search abandonment is never reported, and GA4's own site-search reports stay empty** — A site cannot see who searched and left without opening anything, or read searches in GA4's built-in reports. → §XLIX.44
- 📋 **D190** (deps: D148 ✅, D177 ✅) **Each site of a shared SN site is kept apart by the plugin's own filters, and its key can still reach the other sites** — A filter the plugin forgets leaks another site's posts, and delete-all and Related page and over-fetch to filter by hand. → §XLIX.46
- 📋 **D191** (deps: —) **The default e2e suite fails on a stack started with an OpenAI key, since its AI-off answer spec finds AI on** — The spec inherits the site's AI state from the developer's shell and the Turing volume, so a green run depends on an unset key. → §XLIX.47
- 📋 **D192** (deps: —) **The settings page's selects draw their text under the dropdown arrow, so All Content on the Actions tab reads cut** — The plugin's own CSS overrides the right padding WordPress keeps for the arrow, on every select of the settings page. → §XLIX.48

## Done when — D144

- **A declared custom sort converges on the site with the fields** The jobs schema
  declares "closing soonest"; a run against a fresh site leaves that sort on it, and a
  second run logs no customSortsChanged.

## Done when — D171

- **An author drafts an excerpt and accepts it** Draft an excerpt calls Turing's summary
  through the plugin's REST route with the stored key, Use fills the excerpt field,
  nothing is saved unasked, and a spec covers it.
- **A site with personas shows each one's content-fit** When the SN site has personas,
  Assist shows a content-fit score per persona with its reasons, hidden when there are
  none.

## Done when — D174

- **The live suite's checks run and pass on a site with GenAI** On a stack whose SN site
  has its GenAI config, npm run test:e2e:ai runs the chat and answer specs rather than
  skipping them, and they pass.
- **Each run reports its tokens and dollars and holds a budget** The suite reads
  Turing's cost ledger before and after, reports tokens and dollars with p50 and p95
  time, and fails above a cost budget set in the config.

## Done when — D173

- **A product card shows its price and adds it to the cart** A woo-project spec searches
  the seeded jackets, reads the sale price on Rain jacket's card, presses Add to cart,
  and finds the product in WooCommerce's cart through the Store API.
- **The catalog copilot block puts a product it found in the cart** A live-LLM spec asks
  the copilot block for a jacket under a price, and its add_to_cart action leaves that
  product in the cart without a reload; skipped with the reason while D169 reports the
  copilot off.

## Done when — D178

- **A gap offers Add a synonym, and the dashboard shows the week's top five** An admin
  spec adds a synonym from a no-result row through Tuning (D172) and finds it there, and
  the dashboard widget lists the week's top and zero-result searches.
- **The period's AI summary shows, and metrics turned off are explained** A live-LLM
  spec reads a cached summary of the period from Turing's summary endpoint; with metrics
  off in Turing, the screen says so instead of showing an empty period.

## Done when — D182

- **The e2e job passes on a push, with linux baselines committed** A run of the job on
  GitHub passes, and the tests/e2e/snapshots/linux/ set it wrote on its first run was
  reviewed by eye and committed, so a later visual change fails the job.

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

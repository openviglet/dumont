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
- 🛠 **D174** (deps: D169 ✅, D186 ✅, Turing T1147) **No test exercises Turing's generative features against a real LLM: grounding, injection, speed and cost go unchecked** — Chat and answer checks find no source: Turing never embeds a site on the Default AI Agent, the binding D186 makes. → §XLIX.26
- 📋 **D175** (deps: D169 ✅, D174 🛠) **Visitors cannot ask the site a question in conversation, though Turing's RAG chat answers from its posts** — A visitor with a question browses or leaves, while a cited chat over the site's own content is one block away. → §XLIX.27
- 🛠 **D178** (deps: D176 ✅) **A site owner cannot see top searches, zero-result queries or clicks from wp-admin** — The screen and widget shipped; the period's AI summary is missing, and nothing says when Turing's metrics are off. → §XLIX.32
- 📋 **D179** (deps: D166 ✅, D170 ✅, D171 🛠, D172 ✅, D175, D177 ✅, D178 🛠, D194) **Turing's features on WordPress cannot be seen together, tried by a prospect or filmed for the docs** — Each block ships alone, so nobody sees what Turing brings to a WordPress site. → §XLIX.35
- 📋 **D187** (deps: D172 ✅) **Search tuning has no boosts: newer, sticky or chosen post types cannot be ranked up from wp-admin** — An events or news site cannot put recent posts first without Turing's console. → §XLIX.43
- 📋 **D188** (deps: D176 ✅) **Search abandonment is never reported, and GA4's own site-search reports stay empty** — A site cannot see who searched and left without opening anything, or read searches in GA4's built-in reports. → §XLIX.44
- 📋 **D190** (deps: D148 ✅, D177 ✅) **Each site of a shared SN site is kept apart by the plugin's own filters, and its key can still reach the other sites** — A filter the plugin forgets leaks another site's posts, and delete-all and Related page and over-fetch to filter by hand. → §XLIX.46
- 📋 **D191** (deps: —) **The default e2e suite fails on a stack started with an OpenAI key, since its AI-off answer spec finds AI on** — The spec inherits the site's AI state from the developer's shell and the Turing volume, so a green run depends on an unset key. → §XLIX.47
- 📋 **D192** (deps: —) **The settings page's selects draw their text under the dropdown arrow, so All Content on the Actions tab reads cut** — The plugin's own CSS overrides the right padding WordPress keeps for the arrow, on every select of the settings page. → §XLIX.48
- 📋 **D193** (deps: D173 ✅) **The demo stack runs on stock Twenty Twenty-Five, so nothing it shows looks like a store a buyer would trust** — A prospect judges the search by the site around it, and a bare theme makes Turing's results look like a test page. → §XLIX.49
- 📋 **D194** (deps: D173 ✅, D193) **The Woo seed holds a handful of test products, too few for facets, synonyms or a copilot to show anything** — Search only impresses over a catalog large and varied enough for a typo, a vague need or a filter to matter. → §XLIX.50
- 📋 **D195** (deps: D193, D194) **A visitor to the demo cannot tell what Turing adds, since nothing compares it with WordPress's own search** — The difference sells only when seen: the same query, native results beside Turing's, with each feature named where it acts. → §XLIX.51
- 💭 **D196** (deps: D203) **A prospect can see the demo store only by installing Docker, so a link in a pitch leads nowhere** — Marketing needs a URL anyone opens on a phone, and the store must survive strangers and an LLM bill without a person watching it. → §XLIX.52
- 📋 **D197** (deps: D203, D204) **dumont.viglet.org has a page for AEM but none for WordPress and WooCommerce, the plugin with the most to show** — The site is where a prospect lands, and it says nothing about the plugin, its search or the demo store that proves it. → §XLIX.53
- 📋 **D198** (deps: D173 ✅, D175, D186 ✅, D194) **The demo store shows Turing's search but not its generative AI: no cited answer, chat or copilot works on the catalog** — Grounded answers and a shopping assistant are what a prospect has not seen elsewhere, and the demo must show them working. → §XLIX.54
- 📋 **D199** (deps: D173 ✅, Turing: a query-to-facets call) **A shopper who types "light rain jacket under R$400, blue" gets keyword results, and the facets beside them stay unset** — People describe a need in a sentence; turning it into the site's own filters, and saying so, is search that understands. → §XLIX.55
- 📋 **D200** (deps: D173 ✅) **A shopper choosing between three jackets reads three product pages, and nothing lays them side by side** — Comparing is the step before buying, and a neutral summary of real differences is AI a shopper can trust. → §XLIX.56
- 📋 **D201** (deps: D173 ✅, D198) **A shopper planning a trip cannot ask for everything it needs; the copilot answers one product at a time** — Whole-basket help raises the order, and a staged kit of real, in-stock products is what no keyword search can build. → §XLIX.57
- 📋 **D202** (deps: D176 ✅) **The search block's empty state offers nothing to click, though the site knows what its visitors search for** — Chips built from real searches show the catalog's breadth in one glance, provided each one returns results. → §XLIX.58
- 📋 **D203** (deps: D195, D198, D199, D200, D201, D202, D178 🛠) **The demo's features are separate stops, and no single path walks a visitor from a vague need to a paid-for cart** — A story sells where a feature list does not: one shopper, one trip, each Turing feature the next step she needs. → §XLIX.59
- 📋 **D204** (deps: D203) **There is no demo video of the product, so a pitch, the site or a post cannot show the story in two minutes** — Most prospects will watch before they ever try, and a video replayed from the story spec stays true as the product changes. → §XLIX.60

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

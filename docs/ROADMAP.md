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

- ⏳ **D164** (deps: D160 ✅) **Facets are flat checkbox lists that vanish once used, crowd phones, and cannot filter a range** — Date and number fields still filter by exact value only: no range facets (last 7 days, 20 to 50) declared as Turing custom facets. → §XLIX.20
- 📋 **D165** (deps: D160 ✅) **The search URL keeps only the query, so filters, pages and sort are lost and Back undoes nothing** — A filtered search cannot be shared or reloaded, and there is no newest-first for events or news. → §XLIX.21
- 📋 **D166** (deps: D162 ✅, D169) **A question typed into search gets ten links and never Turing's cited answer** — Turing can answer from the site's own content with citations, and the block leaves that unused. → §XLIX.22
- 📋 **D167** (deps: D163 ✅) **The header's search still submits to WordPress search; Turing search is one page away** — Visitors start searching in the header, and get the slow page load instead of instant, faceted results. → §XLIX.23
- 📋 **D168** (deps: D162 ✅) **In the editor the search block is a grey placeholder with two settings and no layout or styles** — Editors cannot see what visitors get or shape it to the site, so the block looks foreign on most designs. → §XLIX.24
- 📋 **D169** (deps: —) **The plugin cannot tell which of Turing's AI features the site can use, nor turn them on** — Chat works while answers fail on the same site, and an AI block shipped blind shows visitors a broken widget. → §XLIX.25
- 📋 **D170** (deps: D164 ⏳, D165, Turing T1117) **A sentence typed into search is read as keywords, never as the type and date filters it names** — "Seminars about php from last year" returns everything mentioning those words instead of last year's seminars. → §XLIX.29
- 📋 **D171** (deps: D169) **Authors get no help from Turing while writing: no excerpt draft, duplicate check, link ideas or audience fit** — Authors repeat what the site already says and link by memory, while Turing knows the whole site. → §XLIX.30
- 📋 **D172** (deps: —) **Synonyms, promoted results and boosts can only be set in Turing's console, not from wp-admin** — A WordPress owner who sees a bad result has no way to fix it without a second login and a second tool. → §XLIX.33
- 📋 **D173** (deps: D162 ✅, D164 ⏳) **WooCommerce products index as plain posts, with no price, stock or attribute facets and no shopping copilot** — A shop cannot filter by price or stock, and Turing's catalog copilot and add-to-cart action go unused. → §XLIX.34
- 📋 **D174** (deps: D169) **No test exercises Turing's generative features against a real LLM: grounding, injection, speed and cost go unchecked** — An answer can invent a fact, follow a planted instruction or cost ten times more, and every test still passes. → §XLIX.26
- 📋 **D175** (deps: D169, D174) **Visitors cannot ask the site a question in conversation, though Turing's RAG chat answers from its posts** — A visitor with a question browses or leaves, while a cited chat over the site's own content is one block away. → §XLIX.27
- 📋 **D176** (deps: D162 ✅) **The search block never reports which result a visitor opened, to Turing or to Google Analytics** — The site has no click-through rate or abandonment signal, and Turing cannot learn from what visitors choose. → §XLIX.31
- 📋 **D177** (deps: D162 ✅) **Related posts come from tag overlap or another plugin, never from Turing's similar-documents search** — Readers get weak next reads, and the index that already knows which posts are alike goes unused. → §XLIX.28
- 📋 **D178** (deps: D176, Turing T1119) **A site owner cannot see top searches, zero-result queries or clicks from wp-admin** — The content gaps visitors reveal every day stay invisible, so nobody writes what they were looking for. → §XLIX.32
- 📋 **D179** (deps: D166, D170, D171, D172, D175, D177, D178) **Turing's features on WordPress cannot be seen together, tried by a prospect or filmed for the docs** — Each block ships alone, so nobody sees what Turing brings to a WordPress site. → §XLIX.35
- 📋 **D180** (deps: —) **Health's event log shows each result in the language it was written in, not the reader's** — A pt_BR admin reads "Fields provisioned" in English for any event a WP-CLI run or an English session wrote. → §XLIX.36
- 📋 **D181** (deps: —) **Under a classic theme the search block's text buttons render as dark boxes, not links** — Twenty Twenty-One's button styles beat the block's reset, so "Clear all filters" looks like a stray black button. → §XLIX.37
- 📋 **D182** (deps: D161 ✅, Turing T1113) **No CI job opens the plugin's screens: the browser suite runs only on a laptop** — A regression in the block, an admin screen, a theme or the pt_BR strings lands unseen until someone runs D161's suite by hand. → §XLIX.38
- 📋 **D183** (deps: D163 ✅, Turing T1120) **The search block guesses a wildcard into instant searches, since Turing matches whole words only** — A phrase or field query typed into the box gets a stray asterisk, and wildcard terms lose stemming and highlight weight. → §XLIX.39
- 📋 **D184** (deps: —) **The search block's counts have no singular: one match reads "1 results"** — The summary and the suggestion count are a single translated string, so every language gets the plural for one. → §XLIX.40

## Done when — D144

- **A declared custom sort converges on the site with the fields** The jobs schema
  declares "closing soonest"; a run against a fresh site leaves that sort on it, and a
  second run logs no customSortsChanged.

## Done when — D164

- **A mapped DATE or INT field offers range facets in the block** The manifest the
  plugin posts declares Turing custom facets for those fields, the block lists their
  ranges (last 7 days, this year; 0-20, 20-50), and a spec filters by one.

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

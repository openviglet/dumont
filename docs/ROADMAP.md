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
- ⏳ **D303** (deps: Turing T1223) **A site sharing an SN site can search only its own site key, never its sibling sites' documents** — Sibling keys wait on Turing T1223 (site_key values as alternatives): setting, seams, facet labels, badge, Related, navigate. → §XLIX.145
- 📋 **D310** (deps: Turing T1225) **WordPress documents carry no people or themes: who and what a post names never becomes a facet** — A site cannot be explored by person or theme, and name variants across sites and the repository stay split. → §XLIX.152
- ⏳ **D311** (deps: D305 ✅) **The instant overlay is a flat list of six results with no type, date, image, answer or zero state** — The answer strip with Continue in the assistant, a desktop preview pane, an editable Explore row and popular chips in the zero state, Tab into the preview and Ctrl+Enter. → §XLIX.153
- 📋 **D312** (deps: —) **The search answer and the Site assistant do not share context: a follow-up starts over** — A visitor who wants to ask one more thing after the answer loses the question, the answer and its sources. → §XLIX.154
- 📋 **D313** (deps: D305 ✅) **The Site assistant renders only text and citations: a Turing chat flow's choices, forms and slots cannot run in it** — Guided conversations built in Turing's console never reach a WordPress site, though the SDK already types them. → §XLIX.155
- 📋 **D316** (deps: —) **The WordPress plugin and its demo were shaped by a store: nothing has proved the path of a school or cultural institute** — Store-shaped names and defaults leak into a non-commerce site, and only a deliberate read finds them all at once. → §XLIX.156
- 📋 **D317** (deps: —) **A new plugin string ships untranslated in pt_BR and no gate notices: the pt-br spec checks only strings the .po has** — Nine admin strings reached pt_BR sites in English across three tasks, and one was caught only by chance. → §XLIX.157
- 📋 **D318** (deps: —) **A scoped search block's suggestions ignore its scope: the box suggests terms only content outside it holds** — A visitor in a box scoped to events picks a suggested term and finds nothing. → §XLIX.158
- 📋 **D319** (deps: —) **A PDF a page links but did not upload never reaches search, and a post leaving search keeps its PDFs until a sweep** — Forms linked from an office's page stay invisible, and an unpublished page's PDFs stay findable. → §XLIX.159

## Track T — Releases and links match what the site describes

## Track U — Academic repositories as sources (DSpace, OAI-PMH)

- 📋 **D314** (deps: D315) **No Dumont source reads DSpace: a university repository's theses and papers reach Turing only through HTML crawling** — Crawling loses authors, advisors, dates and collections, and DSpace is the repository most Brazilian universities run. → §LII.1
- 📋 **D315** (deps: —) **No Dumont source harvests OAI-PMH, the protocol every repository, OJS journal and catalogue already speaks** — Each academic system would need its own connector where one configurable harvester would serve them all. → §LII.2

## Done when — D303

- **Site A listing B finds B's documents; B finds only its own** The network spec runs
  it in every search mode: the search block, template mode and the assistant, with
  site_key declared as alternatives in the manifest (T1223).

## Done when — D311

- **A question in the immersive overlay shows Turing's answer on top** A spec asks a
  question on the AI stack and reads the answer strip above the groups, with a desktop
  preview of the highlighted result beside them.

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

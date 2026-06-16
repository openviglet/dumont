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
> (HTTP `Content-Type` header / `<meta charset>` / BOM → UTF-8 default). Enables D07.

- [ ] **D06** · **Sitemap & robots awareness** — discover entry points from
      `robots.txt` → `sitemap.xml` (instead of manual starting points only), and honor
      `Crawl-delay` (capped) plus a configurable per-source request delay. Done: a
      source with only a root URL discovers pages via its sitemap and spaces requests
      per the advertised/configured delay.
- [ ] **D07** · **Pluggable fetcher SPI** — extract the fetch step behind an interface
      so the default Jsoup fetcher can be swapped for a **TLS-fingerprint-impersonating**
      client or a **headless renderer** per source, with a configurable user-agent
      strategy and adaptive back-off on 429/403. Done: a source can select a non-default
      fetcher via config; the default path is unchanged.\
      ↳ **Depends on:** D05.
- [ ] **D08** · **Bounded parallel crawl per source** — replace the single
      `LinkedList` queue with a thread-safe queue + a bounded executor, with global
      politeness preserved (per-host delay floor). Done: a multi-page source crawls
      concurrently with a configurable worker cap and no per-host rate violation.\
      ↳ **Depends on:** D06.
- [ ] **D09** · **Safe-run policy + failure report** — a crawl that returns **zero
      indexable items keeps the previously indexed data** (no destructive overwrite —
      port cursarei `engine.py`'s guard), and every run emits a per-source failure
      report (status codes, blocks, dead URLs) instead of a silent empty result. Done:
      an injected total-failure run leaves the index intact and writes a failure report.
- [ ] **D12** · **Incremental fetch (HTTP-conditional re-crawl)** — re-crawl only what
      changed: persist `ETag` / `Last-Modified` per content id and issue conditional GETs
      (`If-None-Match` / `If-Modified-Since`); prefer `sitemap.xml` `<lastmod>` to skip
      unchanged URLs *before* fetching. Complements the existing checksum reindex/skip
      (which fires *after* the body is fetched) by avoiding the fetch entirely — decisive
      on the cursarei workload (4,000 courses) where most pages are unchanged between runs
      and makes a frequent `dumont.cron` schedule affordable. Done: a re-run of an
      unchanged source issues conditional requests and re-indexes ~0 documents; a source
      advertising `lastmod` skips unchanged URLs without a body fetch.\
      ↳ **Depends on:** D06. Pairs with D07.

## Track C — cursarei reference connector

> **Objective**: prove the framework on the real workload, and honor the migration's
> fallback/parity discipline. This is the bridge (sidecar) → native path from
> [STRATEGY.md](STRATEGY.md).

- [ ] **D10** · **Catalog bridge (sidecar) connector** — a `DumConnectorPlugin` that
      ingests **already-normalized** catalog records produced by cursarei's existing
      Python engine (one source per institution) and publishes `TurSNJobItem`s to the
      Turing SN site, applying the grounding contract. Lets the user-facing Turing wins
      (search/RAG, cursarei `V01–V04`) go live without first porting extraction to the
      JVM. Done: the full catalog indexes into Turing through Dumont; document count and
      a URL sample match the Python output for the same sources.\
      ↳ **Depends on:** D01, D03. Pairs with cursarei **V05.1** and Turing **T381**.
- [ ] **D11** · **Leg-by-leg native migration** — move institutions from the sidecar
      (D10) to **native declarative strategies** (D02), one leva at a time, starting
      with the SIGAA federals (one shared TabularListing recipe → highest leverage).
      Each leva flips only after a per-source **parity gate** (count + URL sample vs the
      Python run). Sources with the hardest anti-bot posture may remain on the sidecar
      indefinitely. Done (per leva): the leva indexes natively at parity and the Python
      leg is retired for those sources only.\
      ↳ **Depends on:** D02, D04, D07, D10. Pairs with cursarei **V05.3 / V07**.

## Track D — Adoption & authoring (lower the cost of a new source)

> **Objective**: make "index a new structured source" a guided, minutes-long task —
> not a dev task. Track A turns a source into a declarative manifest; this track makes
> *producing and validating that manifest* cheap and safe. This is the adoption layer
> that turns the framework into a product capability — see [STRATEGY.md](STRATEGY.md)
> §"The second bet".

- [ ] **D13** · **AI-assisted source inference** — given a URL, classify the source shape
      (Sitemap / HtmlListing / TabularListing — the D02 strategies) and propose a **draft**:
      a strategy config plus a field manifest (`TurSNJobAttributeSpec` — name, `TurSEFieldType`,
      multi-valued, facet). The draft is a starting point a human **edits and confirms**; it
      never auto-publishes. Reuses Turing's existing LLM surface (`TurSummaryAPI` / `ChatModel`)
      over the configured `RestClient` — no new model wiring. This is the move that makes the
      declarative framework actually cheap to adopt. Done: pointing the React source wizard at
      a tabular catalog yields an editable strategy + field draft that, after confirmation,
      indexes end-to-end. Grounding still holds — inference may omit, never fabricate.\
      ↳ **Depends on:** D01, D02, D03.
- [ ] **D14** · **Dry-run preview & schema diff** — promote the current "Dry Scan" to a
      preview index: extract a sample of N records, validate them against the declared schema
      (D03), and show **field coverage** plus a **diff of what would change** (new / changed /
      removed documents, newly-empty fields) before any write. De-risks both first onboarding
      and re-config of a live source. Done: a preview run writes nothing and reports per-field
      coverage plus an add/change/remove count for the sample.\
      ↳ **Depends on:** D03.

## Track E — Data quality & trust (make Dumont trustworthy in production)

> **Objective**: surface silent degradation and make every published field auditable.
> This is what the parity gate (D11) and the RAG consumer both need — and the lever that
> moves Dumont from "a pipe that ran" to "a pipe you can trust".

- [ ] **D15** · **Field-coverage & drift monitoring** — per source, track field-coverage %
      across runs and flag **drift**: a field whose coverage drops sharply between two runs is
      almost always a layout/selector break, today invisible until search quality degrades.
      Surface via `DumConnectorMonitoringApi` and a `dumont-react` panel (`SectionCard`), and
      reuse `AiSummaryPanel` for an LLM health summary. Done: a fixture whose layout changes
      between two runs raises a drift flag naming the affected fields; coverage history is
      queryable per source.\
      ↳ **Depends on:** D03. Complements D09.
- [ ] **D16** · **Field-level provenance** — for each published attribute, record where it
      came from (source, strategy, selector/path), its `last-seen` run and an optional
      confidence, and carry a compact provenance into the `TurSNJobItem` so the search/RAG
      side can **cite and audit**. Elevates the grounding contract from "omit absent" to
      "every present field is traceable to a source". Done: a published document carries
      per-field provenance; a field dropped because the source stopped declaring it is visible
      as "last-seen: run N".\
      ↳ **Depends on:** D03. Pairs with Turing **Block R** (citation / grounding).

## Track F — Enrichment & semantics (the differentiator)

> **Objective**: turn D04's enrichment hook from "one derived field" into the layer that
> makes Dumont a *data exchange* platform rather than a pipe — semantics, dedup and reusable
> normalization. All grounding-safe: enrichment may normalize / classify / derive, **never**
> fabricate a sourced attribute.

- [ ] **D17** · **Index-time embeddings** — generate embeddings as an enrichment step at index
      time (behind config + cache, like the LLM enrichment path), so hybrid/semantic RAG in
      Turing needs no separate reprocessing pass over the corpus. Done: a source emits embedding
      vectors alongside its documents; Turing hybrid ranking consumes them.\
      ↳ **Depends on:** D04. Pairs with Turing **Block R** / `RAG_HYBRID_CONFIG.md`.
- [ ] **D18** · **Cross-source entity resolution & dedup** — collapse the same real-world
      entity arriving from multiple sources/URLs (the same course across institution mirrors,
      one item under canonical + tracking URLs) to a **canonical id**, with a grounding-safe
      merge rule (prefer the source that declares a field; never invent). High leverage on the
      cursarei workload where the same program appears across catalogs. Done: a fixture with
      the same entity from two sources indexes as one canonical document with merged,
      provenance-tagged fields.\
      ↳ **Depends on:** D04, D16.
- [ ] **D19** · **Reusable recipe & taxonomy library** — version shared, parameterized recipes
      (the "one SIGAA `TabularListing` recipe → all federals" leverage D11 already calls for)
      and canonical taxonomy maps as **first-class versioned artifacts**, applied by reference
      instead of copied per source. Updating a recipe re-applies to every source that uses it.
      Done: two institutions index via the same versioned recipe with only per-source
      parameters; bumping the recipe re-applies to both. Directly accelerates D11.\
      ↳ **Depends on:** D02, D04.

---

## Non-goals (do NOT add as tasks)

- General-purpose open-web crawling at breadth (Scrapy/Playwright-farm scope). Dumont
  crawls **to feed Turing**; fidelity of structured sources is the goal, not coverage
  of the web.
- Any path that fabricates field values to satisfy a schema (violates the grounding
  contract).
- Replacing the AEM/CMS connectors — the structured-source framework sits beside them
  under the same SPI.
- **AI-assisted authoring that auto-publishes or guesses (D13).** Inference drafts a
  manifest a human confirms; it never indexes on its own and never fills a field it could
  not ground.
- **Enrichment that fabricates (Track F).** Embeddings, entity resolution and taxonomy
  normalization run as enrichment over sourced data — they may derive or merge, never
  invent a sourced attribute.

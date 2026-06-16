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
> + a `dumont-react` panel with an `AiSummaryPanel` health summary). Complements D09.

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
> stays the generic link-spider the framework sits *above*; structured sites it serves
> migrate to D02 strategies, and it gets only lightweight coverage telemetry.

- [ ] **D20** · **Structured-source adapter seam + DB pilot** — define the thin adapter that
      presents an existing connector's per-item mapping as a `DumStructuredSource`, running it
      through `DumStructuredSourcePlugin` so grounding / preview / coverage come for free. Prove
      it on the **DB connector** (the most naturally tabular): a declared column→field schema
      (`TurSEFieldType` + multiValued / facet / mandatory), an id-list query for `discover()`,
      and a single-row `extract(id)` (`… WHERE <pk> = ?`) — replacing today's all-rows batch loop
      (which leaves `discoverContentIds`/`indexById` unimplemented). Done: a DB source indexes
      through the structured plugin at parity with the legacy path, and its coverage history (D15)
      + dry-run preview (D14) work.\
      ↳ **Depends on:** D01, D03, D14, D15.
- [ ] **D21** · **AEM structured adapter** — reuse AEM's existing field-spec
      (`DumAemAttributeSpecification` already carries name / type / multiValued / facet / mandatory,
      a 1:1 map to `DumStructuredField`) to expose an AEM source as a `DumStructuredSource`:
      `schema()` wraps the attribute specs, `discover()` delegates to the existing path /
      QueryBuilder enumeration, `extract(id)` reuses `DumAemAttrProcess` field extraction decoupled
      from the author/publish job-item pipeline. Author/publish + delta semantics stay intact (the
      adapter extracts one environment; the loop drives environments). Done: an AEM source's
      coverage/drift + preview work and indexing stays at parity with the legacy author/publish flow.\
      ↳ **Depends on:** D20.
- [ ] **D22** · **Assets structured adapter** — declare the Assets fixed field set as schema-as-code
      (`id`, `url`, `title`, `date`, `type` + optional `fileSize` / `fileExtension` / `image`, and
      an optional Tika `content` field), with `discover()` = directory walk returning ids and
      `extract(id)` = resolve a single file → typed record (replacing the ad-hoc map and the
      unimplemented `discoverContentIds`/`indexById`). Done: an Assets source's coverage/drift +
      preview work; first-class field declarations replace the loose attribute map.\
      ↳ **Depends on:** D20.
- [ ] **D23** · **Web-crawler coverage adapter (no conversion)** — the WC connector stays the
      generic breadth link-spider (Track A objective + non-goals); a typed `discover()`/`extract(id)`
      contract a link-following crawl cannot satisfy is **not** the goal. Instead, treat the source's
      declared `DumWCAttributeMapping` names as an implicit field set and emit a per-page
      field-presence signal, so a WC source still gets D15 coverage/drift telemetry (and surfaces a
      mapping that silently stops matching) without pretending to be a structured source. Document the
      migration path for *structured* sites currently crawled by WC → D02 strategies. Done: a WC
      source reports per-field coverage across crawls and raises drift when a mapping breaks.\
      ↳ **Depends on:** D15. Complements D09.

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

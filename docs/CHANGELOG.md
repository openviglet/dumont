# Dumont — Shipped Ledger (CHANGELOG)

> Concise index of work that has shipped. **`git log` is the authoritative
> history** — this file is just a searchable map of *what* shipped and *where*
> the rationale lived. Full implementation detail was intentionally removed from
> the roadmap once shipped (see [ROADMAP.md](ROADMAP.md) + `agents.md` →
> "Roadmap & docs maintenance").
>
> Maintenance: when a task in [ROADMAP.md](ROADMAP.md) ships, move its one-line
> entry here (under its track), reference the commit if useful, **delete** it
> from `ROADMAP.md`, and remove its design subsection from `IMPROVEMENTS.md`.

## Track A — Structured-source connector framework

- **D01** — Structured-source SPI: `connector-commons` package
  `com.viglet.dumont.connector.commons.spi.structured` — `DumStructuredSource`
  (`discover()` + `extract(id) → DumStructuredRecord`), schema-as-code
  (`DumStructuredSchema` / `DumStructuredField` → `TurSNJobAttributeSpec`),
  grounding enforced at write time, and `DumStructuredSourcePlugin` (generic
  discover→extract→ground→publish loop). Proven by `DumStructuredSourcePluginTest`.
  (`52f1a5e`)
- **D02** — Declarative extraction strategies: `…spi.structured.strategy` —
  `DumSitemapStrategy` (sitemap-index following + pattern→level classification),
  `DumHtmlListingStrategy` (anchors under a selector → detail URLs),
  `DumTabularListingStrategy` (the SIGAA table shape — discover + extract in one),
  plus `DumHtmlFieldExtractor` / `DumFieldRule` and the `DumHtmlFetcher` seam.
  Each strategy unit-tested; tabular catalog indexed end-to-end by
  `DumTabularSourcePluginTest`. (`a63f11c`)
- **D03** — Conservative grounding enforced end-to-end: `connector-commons`
  `…commons.ground.DumGrounding` lifts the grounding rule (drop null / blank / empty)
  into one reusable op; `DumStructuredRecord` reuses its blank predicate, and
  `DumConnectorProcessQueue` grounds every `TurSNJobItem` at the single indexing
  boundary — so the loose-map connectors (web crawler, AEM, DB, assets) can no longer
  leak `""` into facets, not just the structured SPI. Proven by `DumGroundingTest` +
  `DumConnectorProcessQueueTest`. Pairs with Turing **T381/T382**.
- **D04** — Pluggable enrichment stage: `connector-commons`
  `…spi.structured.enrich` — `DumEnricher` SPI + `DumEnrichmentChain` (merges
  contributions through the schema-bound record builder, so enrichment is grounding-safe
  by construction and a failing enricher is isolated), applied in
  `DumStructuredSourcePlugin` between extract and publish. Two ready enrichers:
  `DumNormalizationEnricher` (deterministic derive/normalize — taxonomy map, region
  derivation) and `DumClassificationEnricher` (LLM label, behind `enabled` config + a
  `DumEnrichmentCache` TTL cache, never overrides sourced data). The LLM call sits behind
  the `DumClassifier` seam; `connector-app` `…enrich.DumTuringClassifier` is the
  production impl over Turing's `POST /api/v2/summary` (no new model wiring). Proven by
  `DumStructuredEnrichmentPluginTest` + the per-component tests and `DumTuringClassifierTest`.
  Enables D17/D18/D19.

## Track B — Web-crawler hardening (real-world sources)

- **D05** — Charset auto-detect: `wc-plugin` `DumWCPluginProcess.getHTML` no longer
  forces ISO-8859-1. It now fetches via `connection.execute()` and routes the body
  through `parseDocument(bytes, contentType, baseUri)`, which detects the charset in
  the same precedence Jsoup applies — HTTP `Content-Type` header (`charsetFromContentType`)
  → BOM / `<meta charset>` → UTF-8 default — keeping the detected charset on output so
  pt-BR accents survive serialization. Proven by `DumWCPluginProcessCharsetTest`
  (Latin-1 + UTF-8 fixtures, header-wins precedence). Enables D07.
- **D06** — Sitemap & robots awareness: `wc-plugin` `DumWCPluginProcess`, gated per source
  by `DumWCSource.enableSitemap`, fetches `robots.txt` and seeds entry points from its
  `Sitemap:` directives (falling back to `/sitemap.xml`), following `<sitemapindex>`
  documents up to `dumont.wc.sitemap.maxDepth` — so a source with only a root URL crawls
  via its sitemap. Requests are spaced by `effectiveDelayMs` = max(per-source
  `DumWCSource.crawlDelay`, advertised `Crawl-delay` capped at `dumont.wc.crawlDelay.maxMs`),
  and the queue now drains once after all seeds (fixing the sitemap-only case). Parsing
  split into testable `DumWCRobotsTxt` / `DumWCSitemap` records; proven by
  `DumWCRobotsTxtTest`, `DumWCSitemapTest`, `DumWCPluginProcessDelayTest`. Enables D08, D12.
- **D07** — Pluggable fetcher SPI: `wc-commons`
  `…webcrawler.commons.fetch` — `DumWCFetcher` seam (`fetch(DumWCFetchRequest) →
  DumWCFetchResponse`, raw bytes for D05 charset detection) + `DumWCFetchException`
  (carries the HTTP status so back-off is fetcher-agnostic) + `DumWCUserAgentStrategy`
  (`PER_SOURCE` default / `PER_REQUEST` rotation). `wc-plugin` `…webcrawler.fetch` —
  default `DumWCJsoupFetcher` (the unchanged jsoup posture) + `DumWCFetcherRegistry`
  (selects per source via `DumWCSource.fetcher`, else the `dumont.wc.fetcher` default;
  unknown → default). `DumWCPluginProcess` routes every fetch (page / robots.txt /
  sitemap) through the resolved fetcher, applies the UA strategy, and retries 429/403
  with exponential back-off (`backoffDelayMs`, `dumont.wc.backoff.*`) that also raises
  the steady-state per-request delay toward the cap. Default path unchanged. Proven by
  `DumWCFetcherRegistryTest`, `DumWCPluginProcessBackoffTest`, `DumWCUserAgentStrategyTest`.
  Enables D11; pairs with D12.
- **D08** — Bounded parallel crawl per source: `wc-plugin` `DumWCPluginProcess` drains the queue
  (now `ConcurrentLinkedQueue`; `visitedLinks`/`indexedLinks` → concurrent sets) across a bounded
  `Executors.newFixedThreadPool`, sized by `effectiveWorkers(DumWCSource.workers, dumont.wc.workers
  default 4)`. An `AtomicInteger` of in-flight links drives termination (queue empty + nothing
  processing → `CountDownLatch` releases the pool); a worker cap of `≤ 1` keeps the original
  single-threaded drain. Global politeness is preserved by `DumWCHostRateLimiter`, a shared per-host
  gate that reserves the next slot honoring the delay floor (max of per-source `crawlDelay` and
  robots `Crawl-delay`), so workers overlap fetch latency and parsing without a per-host rate
  violation; different hosts never wait on one another. Proven by `DumWCHostRateLimiterTest`,
  `DumWCPluginProcessWorkersTest`.
- **D09** — Safe-run policy + failure report: `wc-plugin` `DumWCPluginProcess` accumulates a
  per-source `DumWCCrawlReport` (indexed count + typed failures — `403`/`429` block, `404`/no-status
  dead URL, other) over the crawl. A run that produced no indexable item finishes as *standalone*
  (`finishIndexing(session, true)`), so `DumConnectorContextImpl` skips its destructive deindex pass
  and the previously indexed data survives a total failure (porting cursarei `engine.py`'s guard
  against a transient outage / fresh block wiping a good index); a run that indexed ≥1 item finishes
  normally so stale documents are still deindexed. Every run logs the report (`summary()`) so an
  empty result is never silent. Proven by `DumWCCrawlReportTest`, `DumWCPluginProcessSafeRunTest`.
  Complements D15.
- **D12** — Incremental fetch (HTTP-conditional re-crawl): the D07 fetch SPI now carries conditional
  validators — `DumWCFetchRequest.ifNoneMatch`/`ifModifiedSince` (sent as `If-None-Match` /
  `If-Modified-Since`) and `DumWCFetchResponse.etag`/`lastModified` + `notModified()` (304).
  `DumWCJsoupFetcher` sends the validators and reads the response headers; a 304 is a normal response
  (below jsoup's 400 error threshold). New per-(source,url) state — `DumWCCrawlState` +
  `DumWCCrawlStateRepository` + `DumWCCrawlStateService` (mirrors the EDS crawl-state service) — stores
  `etag`/`lastModified`/`sitemapLastmod`/`checksum`. `DumWCPluginProcess` loads it per crawl, issues
  conditional GETs from it, and on `304` skips re-indexing with no body transfer; where `DumWCSitemap`
  now parses `<lastmod>`, an unchanged value skips the URL *before any fetch*. A skipped page is
  re-emitted as an unchanged job item (its stored checksum routes through the existing
  `UnchangedStrategy`), refreshing its transaction id so it survives the D09 deindex pass without a
  re-index. `DumWCCrawlReport` gains an `unchanged` counter; a re-run where every page is `304` is no
  longer a total failure. Proven by `DumWCPluginProcessIncrementalTest`, `DumWCSitemapTest`. Builds on
  D06; pairs with D07.

## Track C — cursarei reference connector

- **D10** — Catalog bridge (sidecar) connector: new `catalog/catalog-plugin` module
  (`…connector.plugin.catalog`) — the first production consumer of the structured-source SPI.
  `DumCatalogPlugin` extends `DumStructuredSourcePlugin`; `DumCatalogStructuredSource` fetches one
  institution's already-normalized catalog JSON (a bare array or the `{courses:[…]}` envelope) over
  HTTP via the `DumCatalogFetcher` seam (`DumCatalogHttpFetcher` default), indexes records by the
  engine-assigned id, and maps each `Course` (camelCase keys, `DumCatalogSchema`) onto the schema —
  so the schema-bound builder enforces grounding (absent/blank field → omitted, never `""`) and
  `title`/`url` are mandatory (a record missing either is skipped). `DumCatalogSource` (JPA) + REST
  CRUD (`/api/v2/catalog/source`, `indexAll`, export/import) + first-run `./export` provisioning
  mirror the db/wc connectors; the plugin ships as a shaded `catalog-plugin.jar` (bundling org.json)
  loaded via `-Dloader.path`, with `bin/dumont-catalog.*` launchers and package-dist/installer
  wiring. The engine keeps its anti-WAF collection (Python stays the fallback); Dumont gets the
  search/RAG wins live without porting extraction to the JVM. Per-institution sources migrate to
  native declarative strategies one wave at a time (D11), gated on parity. Proven by
  `DumCatalogStructuredSourceTest` (mapping + grounding + envelope) and `DumCatalogPluginTest`
  (end-to-end discover→extract→ground→publish, mandatory-skip). Pairs with cursarei V05.1.
- **D11** — Leg-by-leg native migration: the per-source **parity gate** that lets a catalog source
  flip from the sidecar (Python) leg to a native declarative strategy, one leva at a time.
  `connector-commons` `…spi.structured.parity` — `DumParityGate` compares a candidate
  `DumStructuredSource` against a baseline on the two checks the migration needs (record count within
  `DumParityCriteria.countTolerance`, canonical-URL coverage ≥ `minUrlCoverage`) and returns a
  read-only `DumParityReport` (counts, coverage, bounded missing/extra URL samples, pass + reasons);
  connector-agnostic, it speaks only the structured SPI. `catalog-plugin` `…plugin.catalog.migration` —
  `DumSigaaFederalRecipe` registers the shared SIGAA-federal `TabularListing` recipe (the first, highest-
  leverage leva, on the D19 library, published on `DumCatalogSchema` so a native doc matches the sidecar
  it replaces); `DumCatalogSource` gains `migrationState` (SIDECAR default / NATIVE), `leva`,
  `nativeRecipe`, `nativeListingUrl`; `DumCatalogSourceProvider` resolves a NATIVE source to the recipe-
  materialized strategy (falling back to the sidecar + a log if the recipe is unregistered — D09 safe-run),
  else the D10 bridge. `DumCatalogParityService.migrateToNative` re-runs the gate and persists the flip
  **only** when it passes; `rollbackToSidecar` is always allowed. `DumCatalogSourceApi` exposes
  `GET /{id}/parity` (read-only gate) + `POST /{id}/migrate/{native,sidecar}`. Sources with the hardest
  anti-bot posture stay on the sidecar indefinitely. Proven by `DumParityGateTest` (parity / count+coverage
  fail / tolerant growth) and `DumCatalogMigrationTest` (gated flip, blocked degraded crawl, native
  resolution). ↳ Builds on D02, D04, D07, D10, D19. Pairs with cursarei V05.3 / V07.

## Track D — Adoption & authoring

- **D13** — AI-assisted source inference: `connector-commons`
  `…spi.structured.infer` — `DumSourceInferrer` fetches a sample, computes structural
  signals, and (behind the `DumSourceInferenceModel` seam — no model wiring in commons)
  classifies the source into a `DumSourceShape` (Sitemap / HtmlListing / TabularListing /
  Unknown) and proposes an editable `DumSourceDraft` (strategy config + a `DumInferredField`
  manifest → `TurSNJobAttributeSpec`). Grounding holds: it proposes structure (selectors,
  field types) not content, drops blank-named fields, and degrades to `UNKNOWN` on any
  failure rather than guessing. `DumSourceDraftMaterializer` turns a confirmed tabular draft
  into a live `DumStructuredSource` the generic D01 plugin indexes end-to-end. `connector-app`
  `…infer.DumTuringSourceInferenceModel` is the production model over Turing's `POST
  /api/v2/summary` (same path as `DumTuringClassifier`); `DumConnectorSourceInferenceApi`
  exposes `POST /api/v2/connector/source/infer` (propose) + `/infer/index` (index the
  human-confirmed draft — never auto-publishes). `dumont-react` source-inference wizard
  (`/source-inference`) renders the editable draft. Proven by `DumSourceInferrerTest`,
  `DumInferredSourceIndexingTest`, `DumTuringSourceInferenceModelTest`.
- **D14** — Dry-run preview & schema diff: `connector-commons`
  `…spi.structured.preview` — `DumStructuredPreviewer` extracts a bounded sample through the
  same `discover → extract → enrich` path, computes per-field coverage, reports records that
  would be skipped (missing a mandatory field), and diffs the would-be documents against the
  last indexed baseline (add / change / remove + newly-empty fields) plus a schema diff —
  writing nothing. The baseline is a compact `DumPreviewSnapshot` (schema fingerprint + per-id
  digest, no values) captured by `DumStructuredSourcePlugin` at the end of each full `indexAll`
  via a `DumPreviewSnapshotStore` (`DumInMemoryPreviewSnapshotStore` default); first onboarding
  (empty baseline) reads as all-added. `connector-app` `DumConnectorSourceInferenceApi` exposes
  `POST /api/v2/connector/source/infer/preview?sampleSize=N`; the `dumont-react`
  source-inference wizard gains a "Dry-run preview" action + a coverage / diff panel. Shares its
  coverage metric with D15. Proven by `DumStructuredPreviewerTest`,
  `DumStructuredPreviewPluginTest`.

## Track E — Data quality & trust

- **D15** — Field-coverage & drift monitoring: `connector-commons`
  `…spi.structured.coverage` — `DumCoverageMonitor` derives per-field coverage from the same
  full-run fingerprint D14 captures (no extra extraction pass) and flags **drift** (a field whose
  coverage drops ≥ `dumont.coverage.driftThreshold` points between two runs — the signal of a
  layout/selector break); `DumCoverageSnapshot` / `DumFieldCoverage` / `DumCoverageDrift` records +
  a `DumCoverageHistoryStore` SPI (`DumInMemoryCoverageHistoryStore` default). `DumStructuredSourcePlugin`
  appends a coverage snapshot to the history at the end of every `indexAll`. `connector-app`
  `DumJpaCoverageHistoryStore` (`dum_coverage_run`) persists the series per source;
  `DumConnectorMonitoringApi` exposes `coverage/{source}`, `coverage/{source}/drift`, and an LLM
  `coverage/{source}/summary` (via `DumCoverageHealthService` over Turing's `POST /api/v2/summary`).
  `dumont-react` `coverage` page renders the latest-run coverage, a drift banner naming affected
  fields, run history, and an `AiSummaryPanel` health summary. Proven by `DumCoverageMonitorTest`,
  `DumStructuredCoveragePluginTest`. Complements D09.
- **D16** — Field-level provenance: `connector-commons` `…spi.structured.provenance` —
  `DumFieldProvenance` (strategy, selector/path, confidence) is attached to a field as it is set on a
  `DumStructuredRecord` (provenance stays a faithful subset of the grounded values); the HTML/tabular
  extractors tag each value with its selector and the enrichment chain re-tags overridden fields. At
  publish, `DumProvenance` emits a compact reserved `dum_provenance` multi-valued attribute + spec
  (`field|source|strategy|selector|confidence` per present field) into every `TurSNJobItem` so the
  search/RAG side can cite/audit. `DumStructuredSourcePlugin` appends a `DumProvenanceSnapshot` to a
  per-source `DumProvenanceHistoryStore` (`DumInMemoryProvenanceHistoryStore` default) each `indexAll`;
  `DumProvenanceMonitor` reads the series back so a field a source stopped declaring is visible as
  last-seen N runs ago with the origin it used. `connector-app` `DumJpaProvenanceHistoryStore`
  (`dum_provenance_run`) persists the series per source; `DumConnectorMonitoringApi` exposes
  `provenance/{source}` and `provenance/{source}/last-seen`. Proven by
  `DumStructuredProvenancePluginTest`. Pairs with Turing **Block R** (citation / grounding).

## Track F — Enrichment & semantics

- **D17** — Embedding-quality for structured content (not re-embedding in Dumont): Turing already
  embeds at index time on the path Dumont uses (`POST /api/sn/import` → `TurSNGenAi.addDocuments`,
  when the site has RAG enabled), but its `partitionFields` only reads the canonical keys
  `title`/`abstract`/`text` — so content under custom field names embeds poorly. (a) `connector-commons`
  `DumEmbeddingRole` (NONE/TITLE/SUMMARY/BODY) on `DumStructuredField` + `DumStructuredSchema.title()`/
  `body()`; `DumStructuredSourcePlugin.toJobItem` **projects** a role-bearing field's value onto the
  canonical key (grounding-safe — present-only, `putIfAbsent` so the source wins, original field kept,
  no spec needed as the targets are core SN fields). (b) `connector-app` `DumConnectorGenAiStatusApi`
  (`GET /api/v2/connector/genai-status/{site}`) proxies Turing's `chat/enabled` (degrades to
  `UNREACHABLE`, never a false green); `dumont-react` `RagStatusBadge` flags non-RAG sites in the
  source form. (c) `docs/EMBEDDING_PARTITION_CONTRACT.md` documents the contract. Proven by
  `DumEmbeddingProjectionTest` + `DumConnectorGenAiStatusApiTest`. ↳ Builds on D04, D16.
- **D18** — Cross-source entity resolution & dedup: `connector-commons`
  `…spi.structured.resolve` — `DumCanonicalKey` (derives a stable canonical id from grounded
  natural-key fields, normalized; empty → record indexes unmerged, never on a guessed identity),
  `DumRecordMerge` (grounding-safe merge: first contributor wins and is never overwritten, a later
  source fills only gaps the canonical schema declares, every merged-in field re-tagged with the
  source that supplied it), `DumEntityResolver` (accumulates a crawl's records and collapses those
  sharing a canonical id into one `Entity` published under its primary source). Opt-in via a
  `DumCanonicalKey` constructor on `DumStructuredSourcePlugin`: `crawl()` switches to
  resolve-then-publish, so the same entity from two sources indexes as one document and the
  mandatory-field check runs on the *merged* record (a second source can satisfy it). Per-field
  source carried by a new `DumFieldProvenance.source` (D16), emitted in the `dum_provenance` token
  so the merged document stays auditable. Proven by `DumCrossSourceResolutionPluginTest` +
  `DumRecordMergeTest`. ↳ Builds on D04, D16.
- **D19** — Reusable recipe & taxonomy library: `connector-commons`
  `…spi.structured.recipe` — versions shared, parameterized extraction recipes and canonical
  taxonomy maps as first-class artifacts, applied **by reference** instead of copied per source.
  `DumRecipeRef` (`name@version`, bare name = latest), `DumTabularRecipe` (the SIGAA `TabularListing`
  shape — schema + row/column rules + taxonomy applications — captured once, built into a strategy per
  source via `strategy(fetcher, listingUrls)`), `DumTaxonomy` (versioned canonical map) applied through
  the library-backed `DumTaxonomyEnricher` (resolves its taxonomy from the library at apply time, so a
  taxonomy bump re-applies live, grounding-safe — unmapped label omitted). `DumRecipeLibrary` stores
  both keyed by name+version (latest = highest); `DumRecipeSourceProvider` binds each source to a
  recipe ref + tiny `DumRecipeParameters` (listing URLs, sites, locale) and materializes the
  `DumStructuredSource` on demand, so a recipe bump re-applies to every bound source on the next run
  while a pinned ref (`sigaa@1`) stays frozen. `DumEnrichmentChain.enrichers()` accessor lets the
  provider compose per-recipe chains. Proven by `DumRecipeLibraryPluginTest` (two institutions index
  via one recipe; recipe + taxonomy bumps re-apply to both; pinned ref unaffected). ↳ Builds on D02, D04.
  Accelerates D11; ties into D27.

## Track G — Connector convergence (loose-map connectors → structured SPI)

- **D20** — Structured-source adapter seam + DB pilot: the DB connector now indexes through the
  structured SPI instead of its all-rows batch loop. `db-plugin` `…plugin.db.source` —
  `DumDbStructuredSource` (`discover()` = id-list `SELECT <pk> FROM (<query>) sub`; `extract(id)` =
  single-row `… WHERE <pk> = ?`, reusing the user's query verbatim as a derived table so indexed rows
  match the legacy path), `DumDbSourceProvider`, and `DumDbRowMapper` (the per-row column/file/custom-
  class mapping, factored out of `DumDbPluginProcess`). `DumDbField` (`@Embeddable`) + `DumDbSource`
  `primaryKeyColumn` / `fields` declare a column→field schema (`TurSEFieldType` + multiValued / facet /
  mandatory / embeddingRole); a source with no declared fields falls back to a STRING schema derived
  from the query's result-set metadata (legacy parity, every column indexed). Column labels match
  fields case-insensitively (H2/Oracle upper vs Postgres lower). `DumDbPlugin` now extends
  `DumStructuredSourcePlugin` wired to the persistent coverage/preview/provenance stores, so D14 dry-run
  preview and D15 coverage/drift work for a DB source for free; `toJobItem` re-stamps the document
  `type` + `source_apps` (legacy parity). `DumDbPluginProcess` is reduced to the `@Async` `/{id}/indexAll`
  dispatcher; CRUD + export/import carry the new fields. Proven by `DumDbStructuredSourceTest`
  (H2 end-to-end: discover/extract, grounding + mandatory-skip, multi-valued split, coverage history,
  dry-run preview, derived-schema fallback). ↳ Builds on D01, D03, D14, D15.
- **D21** — AEM structured adapter: the AEM connector now indexes through the structured SPI, wrapping
  its existing extraction as-is. `aem-plugin` `…plugin.aem.source` — `DumAemStructuredSource` (bound to
  one `DumAemEnv`: `schema()` wraps the source's `DumAemAttributeSpecification`s 1:1 with
  `DumStructuredField`; `discover()` delegates to `AemNodeNavigator` path / QueryBuilder enumeration;
  `extract(id)` reuses `DumAemService.getTargetAttrValueMap` — the `DumAemAttrProcess` + monolithic
  content-extractor — decoupled from the author/publish job pipeline; the document id is the source's
  own `id` attribute, falling back to the path) and `DumAemStructuredSourceProvider` (resolves each
  source's enabled environment(s), author primary). `DumAemPlugin` now extends `DumStructuredSourcePlugin`
  wired to the persistent coverage/preview/provenance stores, so D14 dry-run preview and D15
  coverage/drift work for an AEM source for free. **Author/publish + delta stay intact**: `indexAll`
  drives every enabled environment under one session, `toJobItem` stamps each item's environment,
  per-record delta checksum and resolved locale (+ the `site` attribute), and the tracking-driven
  deindex sweep in `finishIndexing` removes stale/unpublished content as before; coverage/preview/
  provenance are captured once from the primary environment. Proven by `DumAemStructuredSourceTest`,
  `DumAemStructuredSourceProviderTest`, `DumAemPluginTest`. ↳ Builds on D20. Bridges to Track H.
  Parity follow-ups split out of this conversion: **D28** (dependency-graph reindex — structured
  `publish` currently emits no dependency set), **D29** (single-item deindex in structured
  `indexById`), **D30** (extraction parity harness), **D31** (parallel extraction, perf).

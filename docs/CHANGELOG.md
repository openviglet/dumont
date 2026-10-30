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
- **D63** — indexAll now posts the source's declared TurSNJobAttributeSpecs to Turing's manifest endpoint, once per site, before the first document, so fields exist as declared.
- **D144 (custom facets)** — A structured schema declares custom facets beside its fields, and provision posts them on the same manifest, so a posting-date facet needs no console step.

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
- **D95** — An incremental EDS run finishes standalone so the pages it skipped stay indexed; only a reindex, which resubmits every page, still ends with the sweep.

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
- **D65** — One MapStruct mapper now declares the exported field list for both directions with unmappedTargetPolicy=ERROR, so an unmapped field fails the build instead of vanishing from the export.
- **D143** — dumont.viglet.org/changelog.json now serves Dumont's releases through 2026.2.16, and www.viglet.org reads it in place of its hand-kept copy.
- **D230** — dumont-site/docs builds a Starlight area with Pagefind search into dist/docs, last in the site build; the Pages workflow asserts it (design §L.1 recorded in `dumont-site/README.md`).
- **D231** — /docs carries the site's nav and footer from src/lib/site-links.ts, and index cards are .astro components on the site's tokens (design §L.2 recorded in `dumont-site/README.md`).
- **D232** — The 20 docs-dumont pages, 15 images and 9 Mermaid diagrams build under /docs with their URL tails, and the links validator passes (design §L.3 recorded in `dumont-site/README.md`).
- **D233** — Every site, console and README docs link points at /docs; each docs page has a .md twin and sits in the sitemap, manifest and llms.txt (design §L.4 recorded in `dumont-site/README.md`).
- **D234** — The shipping skill and agents.md put a user-facing page in dumont-site/docs, same commit; vigletdocs says the docs moved (design §L.5 recorded in `.claude/skills/dumont-roadmap-docs/SKILL.md`).
- **D235** — All 20 docs.viglet.org/dumont pages redirect to their /docs counterpart, docs-dumont left the vigletdocs build, and Turing docs and blog link the new pages.

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
- **D33** — Safe finish on a partial / interrupted structured run: `DumBoundedExtraction.extractEach`
  now returns a `DumExtractionOutcome` (discovered / processed / interrupted) — the sequential loop
  bails on `Thread.isInterrupted()` and the parallel loop reports the break on `completion.take()`
  interrupt. `DumStructuredSourcePlugin#indexAll` and `DumAemPlugin#indexAllEnvironments` finish the
  session *standalone* (`finishIndexing(session, true)`, via the shared
  `finishPartialRunStandalone`) — skipping the tracking-driven deindex sweep and the baseline/coverage
  capture — when a run is interrupted partway or an exception escapes the per-environment loop, so a
  transient interruption (pool/JVM shutdown) can no longer treat not-yet-extracted ids as removed and
  wipe a still-valid index; a clean run deindexes stale content as before. Generalises D09's
  zero-yield safe-run to the structured path. Proven by `DumBoundedExtractionTest`,
  `DumStructuredSourcePluginTest`. ↳ Builds on D31. Complements D09.
- **D34** — Per-extract timeout + failure-wave guard for the structured loop (resilience):
  `DumBoundedExtraction.extractEach` gains an optional per-extract wall-clock bound — an
  `extract(id)` that hangs past the HTTP client's own timeout is cancelled and counted as failed
  (so one stalled `infinity.json` fetch can no longer stall the run on the unbounded
  `completion.take()`); when a timeout is set the run uses the pool even at concurrency 1. The
  returned `DumExtractionOutcome` now carries a `failed` count + `failureRatio()`; `extract`
  outcomes are classed present / empty (legitimate, not counted) / failed (threw or timed out).
  `DumStructuredSourcePlugin` (and `DumAemPlugin#indexAllEnvironments`) finish *standalone* via the
  shared `finishPartialRunStandalone` when the failure ratio of an otherwise-complete run meets
  `failureRatioThreshold()` (default 0.5; ≤ 0 disables), so a transient outage that dropped many
  ids preserves the prior index instead of mass-deindexing. AEM wires both from
  `dumont.reactive.extractTimeoutMs` (default 0) and `dumont.reactive.failureRatioThreshold`
  (default 0.5). Proven by `DumBoundedExtractionTest`, `DumStructuredSourcePluginTest`.
  ↳ Builds on D31. Complements D09, D33.
- **D62** — An Adobe I/O journal consumer pulls the same events behind a cursor persisted only after a page is dispatched, so a dropped webhook replays instead of going stale.
- **D64** — The audit now enumerates the index per site and reports missing, stale and orphaned ids, requeueing the first two; ghosts are named, never deleted.
- **D68** — The audit's orphans become a dated report reviewed and applied via /api/v2/connector/orphans, reusing the connector's own deindex; a fresh pass supersedes it and evidence past orphanTtl is refused.
- **D69** — The batch buffer holds each item with the session that produced it, so a drain stamps every SENT_TO_QUEUE entry per item instead of under whoever asked to flush. Proven by JobItemBatchProcessorTest.
- **D70** — An Orphan Reports surface on both trees lists each report with its id count against the site's, its dates and expiry, shows the ids, and applies or discards one at a time; an expired one is shown.
- **D71** — A DumJobTrail rides on each item across the queue and is stripped before indexing, and the six strategy call sites that held a session and discarded it now pass it, so every status names its source.
- **D72** — The indexing log filters by source and transactionId on both the Mongo and Redis engines, and a row written before the trail existed is returned unfiltered rather than dropped.
- **D73** — The Redis engine honours all seven filters. It was returning nothing at all: the shared serializer emitted Mongo shell ISODate, which no JSON reader accepts, so every query failed and answered empty.
- **D74** — The Redis parse moved inside the loop, so a row Jackson cannot read costs its own row and nothing else; the response carries an unreadable count and a warning names pre-2026.3.6 rows as the cause.
- **D75** — An unreachable store answers 503, not an empty page, and status splits into configured (the property) and enabled (a ping); the history tab follows the first, its body the second.
- **D76** — A shared MongoClient and a JedisPool are built once and held, so a status poll borrows rather than building a pool and a topology thread; an unset serverSelectionTimeoutMS is bounded to 5s.
- **D78** — Both appenders report a lost row at WARN, once per outage with a count and a recovery line, and the profiles route those diagnostics to FILE rather than into the store refusing writes.
- **D79** — The Mongo indexing appender holds one static mapper like its Redis counterpart, and append gained the test it never had: serialisation, per-row failure and the concurrency a shared mapper faces.
- **D80** — The hostname resolves once in a shared DumClusterNode instead of per event, and the Redis sibling's private cache folds onto it rather than the two carrying the same lines twice.
- **D81** — The appender's own fixed pool is gone, so the row is pushed on the caller's thread and the AsyncAppender's bound and discard policy govern the only queue there is.
- **D82** — Both general appenders report a lost row through the shared reporter at WARN, and its line now names the log as well as the store so a server-log outage is not read as an indexing one.
- 🗑 **D83** — abandoned: Verified false: Spring Boot throws on any Logback ERROR status, so a malformed URI fails the boot loudly, and a merely unreachable host starts fine and is reported at runtime by D82.
- **D84** — status gains a writing flag answered by walking the logback context for an attached appender that would accept a row, and the history tab says so instead of rendering an empty table.
- **D85** — logging.config is derived from dumont.logging.engine as the lowest-precedence source, so a custom file still wins, and two shipped profiles that name different engines are refused at startup.
- **D86** — Every profile now writes to console and file as well as its store, so the Dockerfiles no longer pin a console-only profile and a container can enable the indexing log.
- **D87** — The reader builds a pooled RedisClient with a stated maxTotal and maxWait, the same API the appender that writes the log uses, so the deprecated JedisPool and its unseen defaults are gone.

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
  Parity follow-ups split out of this conversion: **D28** (dependency-graph reindex), **D29** (single-item
  deindex in structured `indexById`), **D30** (extraction parity harness), **D31** (parallel extraction, perf).
- **D22** — Assets structured adapter: the filesystem Assets connector now indexes through the
  structured SPI instead of its bespoke directory-walk + loose attribute map. `assets-plugin`
  `…plugin.assets.source` — `DumAssetsStructuredSource` (the fixed field set declared as schema-as-code:
  `title`/`url` mandatory + `date`, plus optional `image`, `fileSize`, `fileExtension` and a Tika
  `content` field gated by the new `DumAssetsSource.contentField`; `discover()` = directory walk
  returning one id per file, skipping `.DS_Store`; `extract(id)` = resolve a single file → typed record,
  Tika content via `DumFileUtils`) and `DumAssetsSourceProvider`. `DumAssetsPlugin` now extends
  `DumStructuredSourcePlugin` wired to the persistent coverage/preview/provenance stores, so D14 dry-run
  preview and D15 coverage/drift work for an Assets source for free; `toJobItem` re-stamps the document
  `type` + `source_apps` (legacy parity), the document id is the prefix-replaced `url` with the optional
  `typeInId` prefix. The unimplemented `discoverContentIds`/`indexById` are now satisfied by the shared
  loop; `DumAssetsPluginProcess` is reduced to the `@Async` `/{id}/indexAll` dispatcher; new
  `filesystem(...)` provenance factory. Proven by `DumAssetsStructuredSourceTest` (temp-dir end-to-end:
  discover/extract + `.DS_Store` skip, grounding, type/source_apps stamp, Tika content, coverage history,
  dry-run preview). ↳ Builds on D20.
- **D23** — Web-crawler coverage adapter (no conversion): the WC connector stays the generic breadth
  link-spider — no typed `discover()`/`extract(id)`, no structured-source pretence. `wc-plugin`
  `DumWCCoverageAccumulator` treats the source's declared `DumWCAttributeMapping` names as an implicit
  field set and accumulates a thread-safe per-page field-presence signal over the D08 worker pool;
  `DumWCPluginProcess` records it (the extracted attribute keys per indexed page) and on `finished()`
  appends one `DumFieldCoverage` snapshot to the shared D15 `DumCoverageHistoryStore` keyed by the
  source id — skipped when the source declares no mappings or the run indexed nothing (so an
  all-304/failed run raises no spurious 0%-coverage drift). A WC source therefore reports per-field
  coverage across crawls on the existing `coverage/{source}` + `coverage/{source}/drift` endpoints and
  the generic coverage page, and a mapping that silently stops matching surfaces as a coverage drop —
  all without a contract a link-following crawl cannot honor. Migration path for *structured* sites
  still on WC → D02 strategies documented in
  [STRUCTURED_VS_TRADITIONAL.md](STRUCTURED_VS_TRADITIONAL.md) §9. Proven by
  `DumWCCoverageAccumulatorTest` + `DumWCPluginProcessCoverageTest`. ↳ Builds on D15. Complements D09.
- **D29** — Structured `indexById` deindex parity: a targeted single id whose `extract(id)` yields no
  record (a now-removed page — author 404 / ineligible — or a publish item no longer delivered) is again
  removed from the index instead of silently lingering until the next full `indexAll`. `aem-plugin`
  `DumAemPlugin.indexById` now drives each enabled environment with `ifPresentOrElse`, queuing an explicit
  `DELETE` `TurSNJobItem` (env-stamped, `id`/`source_apps` attrs, source sites + locale) through
  `addJobItem` on the empty branch — so a missing page deindexes from both author and publish while an
  undelivered publish item (author still present) deindexes from publish only, matching the legacy
  standalone behaviour. The explicit delete flows through the processing chain regardless of the
  `standalone=true` flag that suppresses the tracking-driven sweep, so `POST index/{name}` / `reindex/{name}`
  on a removed/unpublished id removes it. Proven by `DumAemPluginTest`. ↳ Builds on D21.
- **D30** — AEM extraction parity harness + data-path fix: a golden-fixture test locking the D21
  conversion's parity with the legacy job pipeline (no live AEM in CI), and the bug it surfaced.
  `aem-plugin` `DumAemExtractionParityTest` drives one `infinity.json` per content kind — `cq:Page`,
  Content Fragment, static file — through both the structured `DumAemStructuredSource.extract(id)` +
  `DumAemPlugin.toJobItem` path and the legacy `DumAemJobService.prepareIndexObject` extraction (real
  `DumAemService`/`DumAemAttrProcess`/delta/locale collaborators; `getInfinityJson` the only stubbed
  static), asserting the published `TurSNJobItem` has identical shape: content attributes, document id,
  `environment`, delta `checksum`, resolved locale (the structured-only `dum_provenance` token, D16, is
  excluded from the content comparison). **Fix:** `configureObjectDataPath` (CF → `data/master`, static
  file → `metadata`) was applied to the *generic* object in `prepareIndexObject`, then discarded when
  `indexObject` re-wrapped it in a fresh `DumAemObject` — so CF/static attributes under those sub-trees
  were never extracted (legacy *and* structured). It now lives on `DumAemService` and runs inside
  `getTargetAttrValueMap` — the single field-extraction boundary both paths share — so it applies to the
  object actually extracted, for author and publish alike, at parity by construction. Proven by the
  harness (a field living only under `data/master`/`metadata` is extracted identically on both paths; an
  unmapped sub-tree property never leaks). Fails on any future divergence. ↳ Builds on D21.
- **D31** — Parallel extraction for HTTP-heavy structured sources (perf): the shared structured loop
  extracted ids one at a time, so a large AEM source — where each `extract(id)` is a single
  `infinity.json` fetch — indexed markedly slower than the legacy reactive navigator that parallelised
  its tree traversal. `connector-commons` `…spi.structured.DumBoundedExtraction` restores that
  parallelism at the framework level: `extract(id)` runs on a bounded daemon pool (at most N in flight,
  so memory stays bounded) while the consumer — publish + snapshot/coverage/provenance capture — is
  invoked **serially on the calling thread**, so capture stays single-threaded and order-independent
  (every capture is keyed by record id, never position); a failed/empty extraction is logged and
  dropped, never fabricated. `DumStructuredSourcePlugin` gains an `extractionConcurrency()` hook
  (default `1` = sequential, every adapter unchanged) and drives `indexAll` through the extractor;
  `DumAemPlugin` overrides it from the legacy reactive controls (`dumont.reactive.indexing` gate +
  `dumont.reactive.parallelism` degree) and runs each environment's extraction through it, so the
  structured path matches the legacy reactive path's wall-clock envelope. AEM's per-id state
  (`checksums`/`locales`/`dependencies`) is already `ConcurrentHashMap` + `schema()`/`session()`
  synchronized, so parallel `extract` is safe. Proven by `DumBoundedExtractionTest` (parallel overlap,
  serial order-independent consume, dropped empty/failing ids). ↳ Builds on D21.

## Track H — AEM extraction model redesign (component-oriented, per-type, recipe-driven)

- **D24** — AEM component-extractor SPI: the monolithic model.json content extractor is replaced by a
  registry of small, single-concern extractors composed per page. `aem-commons`
  `…aem.commons.ext.DumAemComponentExtractor` (functional SPI: `contribute(model, query, aemObject,
  attrValues)`); `DumAemExtModelJsonBase` gains an overridable `componentExtractors()` and a default
  `extractAttributes` that composes them in order (legacy monolithic override still supported, so the
  change is backward-compatible). The reference monolith `DumAemExtSampleModelJson` is migrated to five
  registered extractors — `DumAemSamplePageExtractor`, `DumAemSampleParagraphExtractor`,
  `DumAemSampleTextExtractor`, `DumAemSampleImageExtractor`, `DumAemSampleContentFragmentExtractor` —
  sharing a `DumAemSampleItems` tree-walk/HTML-strip helper, at output parity with the god-class. Each
  extractor is now independently unit-tested in isolation (`DumAemSample*ExtractorTest`); the base
  composition is proven by `DumAemComponentExtractorCompositionTest`. ↳ Builds on D21.
- **D25** — Schema-per-content-type + template routing: replaces "one source = one schema" with a
  content-type model. `aem-commons` gains `DumAemContentType` (name + fields + extractor composition)
  and `DumAemContentTypeField`; `DumAemExtModelJsonBase` gains `contentTypes()` + `templateName(...)`,
  resolves a page's type via the existing alias map (`resolveContentType`), routes `extractAttributes`
  to only that type's composition (flat `componentExtractors()` fallback), and surfaces the resolved
  type under reserved `dum:contentType`. The AEM adapter `DumAemStructuredSource` builds a per-type
  `DumStructuredSchema` (source schema = union of attribute specs + every type's fields), binds each
  record to its resolved type's schema, and tags it (`DumStructuredRecord.contentType`). D15
  coverage/drift is now reported per type: `DumContentTypeCoverageRecorder` (connector-commons) buckets
  records by type and appends per-type history under `source::type`, additively to the source-level
  series (`DumAemPlugin`/`DumStructuredSourcePlugin` indexAll; enrichment preserves the tag). Reference
  `DumAemExtSampleModelJson` migrated to `article`/`event` content types. Tests:
  `DumAemContentTypeRoutingTest`, `DumContentTypeCoverageRecorderTest`, `DumAemStructuredSourceTest`
  (union schema). ↳ Builds on D24.
- **D26** — Derivations → enrichment chain: the business derivations the AEM god-class fused into
  extraction become reusable, named D04 enrichers, so `extract(id)` emits only *sourced* fields and
  every derived field is produced by a named enricher. connector-commons `…structured.enrich` gains
  `DumDistinctValuesEnricher` (`preco` aggregation), `DumBooleanAnyEnricher` (`inscricaoAberta`),
  `DumEarliestDateEnricher` (`dateSort`, future-boundary from an injected clock), `DumDateRangeTextEnricher`
  (`dateOfClassesText`), plus `DumNormalizationEnricher.transform(...)` for `alphabet` via the shared
  normalizers. The cross-cutting normalizations move to a stateless `…structured.normalize` package:
  `DumTextNormalizers` (accent-folded first letter, HTML→text over the connector's single Jsoup impl)
  and `DumDateNormalizers` (PT month abbreviations, zone-aware start-of-day, date-range label) — pure
  `Optional`-returning functions, so an un-normalizable input grounds away. Each rule is individually
  unit-tested (`DumTextNormalizersTest`, `DumDateNormalizersTest`, `DumDerivationEnrichersTest`), the
  latter including a full-chain reference proving the five derivations come only from named enrichers
  (provenance `enrichment:<name>`). The Insper plugin (separate repo) composes these into its chain.
  ↳ Builds on D25, D04.
- **D27** — Versioned AEM recipes: D25's per-content-type model (schema + component-extractor
  composition) is promoted to a first-class, versioned, parameterized artifact applied **by reference**
  instead of copied into a per-source god-class — the AEM-side counterpart of D19, the endgame of Track
  H ("one `course-offer` recipe → many institutions"). New `aem-commons` `…aem.commons.recipe` (declared
  without depending on connector-commons, the same boundary as `DumAemContentTypeField`): `DumAemRecipe`
  captures the shared shape (content-type name + field schema + an extractor-composition *factory*),
  `materialize(params)` binds it to one institution's `DumAemRecipeParameters` (template names + a tiny
  param bag — component resource-type prefix, …) producing a ready `DumAemContentType`; `DumAemRecipeRef`
  (`name@version`, bare = latest) + `DumAemRecipeLibrary` store recipes by name+version (latest = highest).
  A recipe-driven extension stays a thin `DumAemExtModelJsonBase`, delegating `contentTypes()` /
  `templateNameAliases()` to a `DumAemRecipeContentTypes` binding that materializes from the library's
  current state on every call — so registering a higher version re-applies to every institution tracking
  it by latest on the next extraction, while a pinned ref stays frozen. Proven by `DumAemRecipeLibraryTest`
  (two institutions index a shared content type via one recipe; a recipe bump re-applies a new field/
  extractor to both; pinned ref unaffected; unregistered binding skipped). ↳ Builds on D24, D25, D19.
- **D28** — AEM dependency-graph reindex (structured path): editing a shared XF/CF again reindexes every
  page that embeds it through the structured loop, dropped when D21 moved AEM onto it (published with an
  empty dependency set). The XF/CF harvest (`getDependencies` over infinity.json + model.json, restricted
  to `/content/experience-fragments/` and `/content/dam/` roots, dropping renditions/extensions, JCR
  sub-trees and self-refs) is lifted out of `DumAemJobService` into a shared `DumAemDependencyService`
  seam (gated by `dumont.dependencies.enabled` + the per-source `dependencyClass`). `DumAemStructuredSource`
  harvests the set per record during `extract(id)` (from the author payload, parity with the legacy
  `prepareIndexObject`) and exposes it via `dependencies(id)`; `DumAemPlugin#publish` emits it on the
  `DumJobItemWithSession` (replacing `Set.of()`), so it persists via `DumConnectorDependency` and resolves
  through `getObjectIdByDependency`. Proven by `DumAemDependencyServiceTest` (gating + filtering parity).
  ↳ Builds on D21.
- **D32** — AEM extractor concurrency contract + statelessness gate: D31 extracts ids in parallel while
  the content extractor is a cached singleton (`DumCustomClassCache`) reused across threads, so a
  `DumAemComponentExtractor` / monolithic `extractAttributes` holding per-page mutable instance state would
  corrupt output under `dumont.reactive.indexing=true`. The *stateless-during-extraction* contract is now
  documented on the `DumAemComponentExtractor` SPI (mirrored on `DumAemExtModelJsonBase`), and
  `DumAemExtractorConcurrencyTest` (aem-commons) enforces it: it drives the D24 composed SPI, the D25
  content-type-routed model, and a monolithic (Insper-shaped) extractor through sequential-vs-parallel
  extraction over one shared instance, asserting identical attribute maps — and proves the harness catches a
  deliberately stateful extractor (it diverges under parallel extraction). ↳ Builds on D31, D24.
- **D60** — two routes added to the bento tree, the two editors moved to the adaptive chrome, and three cases in bento-tree that mount the real app at both addresses
- **D61** — A per-source fetch strategy: DELIVERY discovers and reads via .model.json on the publish or delivery host, so a Cloud Service source needs no author credential; SLING keeps the author servlets.
- **D67** — One delivery surface per content kind, routed from the source's own contentType and subType: pages via model.json, fragments via the Content Fragment API, assets via Assets Search.
- **D123** — Dumont boots with aem-plugin.jar loaded: the IMS token and journal services mark their Spring constructor, and a test fails any AEM component Spring cannot construct.

## Track I — The console adopts the shared bento layer

- **D43** — copy-ds.cmd derives its destination from its own directory, so the copy reaches this checkout's node_modules, and refuses when npm has not run there instead of creating the path.
- **D35** — npm run census reads the package's own exports.json and prints 39 pages, 0 on bento, and 88 components as 37 shim, 8 fork, 43 own (design §XI.1 superseded: app-footer.tsx forks package's AppFooter).
- **D38** — ui/table.tsx is a one-line re-export of the package's eight Table primitives, byte-identical to what it declared, and no fork is left among the 33 ui files.
- **D44** — Both grids take the v8 names from v9's ./legacy subpath, so tsc -b and vite build are green and neither grid was rewritten.
- **D42** — vitest run mounts the shell, a list page and a form page against a stubbed HTTP boundary, and holds D35's census by name; dropping the footer turns the run red.
- **D36** — bento.css joins the Tailwind build (bento-glass, bento-grid, the rail gutter reach the bundle) and seven cases mount the rail, the mosaic and the form hero against the affordances D42 pins.
- **D37** — A bento tree serves under its own prefix and shell with the database source list migrated into it; the console route is untouched, nothing redirects, and six cases mount the real App to hold both.
- **D39** — One declaration in nav.const holds all 16 surfaces in the package's schema; the sidebar, the rail and a wired command palette derive from it, and manifest's wire shape is derived and pinned unchanged.
- **D40** — The package's bundles are now the base and Dumont's merge over them, so five namespaces its chrome reads stopped being dropped; a gate asserts every bento string differs between en and pt.
- **D41** — viglet-ds-check-duplicates runs on push and pull_request; the eleven standing collisions are annotated at their declaration with a reason, and a planted duplicate exits 1 in a test.

## Track J — Console surfaces move to bento chrome

- **D45** — Each section declares a hub path, one parameterised route renders it as a mosaic, and the rail lists both; home lists sections too, deleting its hand-written copy of what had migrated.
- **D46** — All six source lists serve in bento through one shared wrapper, every console route untouched; the census now reads era through a page's local imports and no longer counts a type-only import.
- **D47** — The six detail pages serve on the bento tree through a chrome declaration, so their 500-line forms are shared rather than forked, and a tile now keeps the reader inside the shell.
- **D88** — The chrome census reads dumont at zero console-era imports, and both header adapters render the bento hero on either tree (design §XL.1 recorded in `agents.md`).

## Track K — Frontend build and lint gates

- **D48** — A flat config runs ESLint for the first time: ten real defects fixed, including a missing effect cleanup, and the two structural classes carried as named files rather than a ceiling.
- **D49** — The DTS build extends the app tsconfig, not the root solution file, and emits outside the wiped output dir, so all four remotes ship declarations; a script checks the artefact, not the exit code.
- **D50** — validate.yml builds on every push and pull request, caches on pnpm rather than a lockfile that never existed, and takes the Sonar branch from the ref instead of the previous version.
- **D51** — The probe was right and the scan was wrong: chrome is now counted through a page's components, so StickySaveBar reads 1, Card 20 instead of 3, and no probe matches nothing.
- **D77** — A beforeAll warm-up pays the cold start once, so the first case went 18.7s to 2.9s, and a file-scoped 30s testTimeout replaces the global 10s here without spending the suite's hang detection.
- **D89** — Both halves of the census strip comments before reading era, so a file that quotes chrome="bento" or a bento import describes the layer rather than joining it.
- **D90** — Twenty-seven files read as written, and a property check holds them there: four password masks and a coverage arrow were mangled on screen, not only in comments (design §XL.2 recorded in `agents.md`).
- **D133** — pnpm-workspace.yaml overrides lift undici, adm-zip, postcss, nanoid, source-map-js, browserslist and baseline-browser-mapping above every alerted range.
- **D134 (unused direct xlsx dropped)** — dumont-react no longer declares xlsx, which no source imported, so its manifest stops carrying the two direct-dependency alerts.
- **D135** — The EDS export app joins the pnpm workspace on its siblings' dependency versions, so its stale npm lockfile is gone and the workspace overrides now reach it.

## Track L — Console build, lint and type gates

- **D52** — 56 aliases across 48 declarations are rewritten to relative specifiers after emit and the zip is repacked from the corrected tree; a gate fails on any that survive.
- **D53** — All twenty-five cleared and the named-file list deleted, so the rule is an error console-wide; the monitoring history got a test first, which found its jump-to-last-page never fired on a URL.
- **D54** — All 22 anys are typed from the contracts they stood for, so the rule is an error across the console and its list is gone rather than kept at zero.
- **D55** — The manualChunks block is gone and the build emits the same 25 chunks byte for byte, because Module Federation was already discarding it; its refusal no longer prints.

## Track M — Console effect correctness

- **D56** — All twenty-one arrays list what they read and the rule is an error; completing them exposed a list page building its service inline, which would have refetched forever.
- **D66** — useConnectorProvider stores the resolved provider against the id that produced it, so loading is derived and the two synchronous writes are gone; pnpm run lint exits 0.

## Track N — The web console

- **D57** — the console wizard under a bento chrome declaration, with a new AdaptivePageHeader picking SubPageHeader or BentoHero; Sources is the first complete section
- **D58** — seven console pages under a bento chrome declaration, with new AdaptivePageHeader and AdaptivePanel adapters; the monitored-item page moved too so the grid's links stay in the shell
- **D59** — the rules list reuses the shared entity list, both forms read the adaptive chrome, and the manager's bar became AdaptiveSaveBar so the StickySaveBar shim and its census probe are gone

## Track O — Structured sources over paged JSON APIs

- **D91** — Both images bundle every `<connector>/*-plugin` jar by glob, catalog included, and the root build fails when a plugin module produced no jar.
- **D92** — A paged JSON API indexes from a manifest stored on a catalog source, with day/week/month budgets that survive restarts and incremental runs that deindex only expired ids.
- **D93** — DUMONT_ADMIN_PASSWORD sets the admin password at boot while none is stored, through the same service the setup screen uses, so compose and CI start with a login.
- **D94** — The zip, installer and AppImage carry the EDS plugin with launchers, staging fails when a plugin module is left out, and releases upload every plugin jar.
- **D96** — A structured source returns run notes, and a run that stopped short is marked partial with its reasons on the stats row the console's Indexing Stats table shows.
- **D97** — A structured record carries transient attributes that enrichers read and write through DumTransient, and publishing never indexes them.
- **D98** — An enricher removes a field the source supplied by returning DumClear.field(), while a blank contribution still changes nothing.
- **D99** — A taxonomy loads from a JSON resource with per-row attributes and its source and download date, and an application can write an attribute such as a municipality's UF.
- **D100** — An enricher reports a per-run summary from onRunEnd, which also resets it, and its notes join the run outcome recorded on the stats row.
- **D101** — An enricher's contribution can name its selector and confidence through DumSourced, and a value it only passes through keeps the source's provenance.
- **D102** — An invalid enricher contribution drops only that field, the enricher's other fields still land, and the warning names the field and the reason.
- **D103** — DumFallbackEnricher declares an ordered fallback over inputs and DumSplitEnricher one input producing several fields, each value citing the input it came from.
- **D104** — A source returns unchangedIds(), and the loop stamps the run's transaction on their tracking rows, so the sweep keeps them without a replayed extract.
- **D105** — The Maven build works in a git worktree: git-commit-id-maven-plugin 9 reads HEAD through the worktree's .git file, and agents.md names the skip flag for tree without git.
- **D106** — With dumont.enrichment.llm.enabled=true, plugins get a DumClassifierFactory bean that builds Turing-backed classifiers for their classification enrichers.
- **D107** — DumClassificationEnricher keeps answers in a SHA-256-keyed store (persistent in connector-app), caps calls per rolling day, and publishes only labels of a closed set.
- **D108** — DumTuringClassifier keys Turing's cache by the SHA-256 of prompt and input, so colliding inputs such as "Aa" and "BB" no longer share an answer.
- **D109** — Resolution members persist in dum_resolution_member through DumRecordCodec, so a plugin built with the DumResolutionIndexFactory bean resolves across restarts.
- **D110** — A provider declares DumSurvivorship by overriding survivorship(): the D18 field merge, a field merge with per-field member order, or one member kept whole by a comparator.
- **D111** — The run-end sweep sends a delete only for the sites no other source of the provider still tracks the document on in that locale, so a shared document survives one source dropping it.
- **D112** — Resolution runs inside each source's indexAll since D109, so every contributing source gets its own guards, provisioning and swept finish; tests now pin both.
- **D113** — Each delete the connector queues under a session reaches DumStructuredSource.deindexed and the enrichers' onDeindexed with the published id; values stay the source's to keep.
- **D114** — Any structured source returns goneIds() and they are deindexed on every run that did not abort, beside the sweep, through the same path incremental expiry and resolution use.
- **D115** — DumStructuredSource.onRunEnd(DumRunEnd) is called once after every indexAll finishes, aborts included, saying whether the run was partial, swept, and why it stopped.
- **D116** — Each jobs adapter notes its budget stops and failed pages on the fetch context, and the source returns them as run notes on a run that still sweeps.
- **D117** — Contract and seniority are DumFallbackEnricher declarations, with a stopOn rule for ambiguous inputs, and every jobs enricher value cites the input it read.
- 🗑 **D118** — abandoned: The jobs module moved to the trabalharei repository on 2026-10-11, and this work continues there as TRA68.
- 🗑 **D119** — abandoned: The jobs module moved to the trabalharei repository on 2026-10-11, and this work continues there as TRA69.
- 🗑 **D120** — abandoned: The jobs module moved to the trabalharei repository on 2026-10-11, and this work continues there as TRA70.
- 🗑 **D121** — abandoned: The jobs module moved to the trabalharei repository on 2026-10-11, and this work continues there as TRA71.
- 🗑 **D122** — abandoned: The jobs module moved to the trabalharei repository on 2026-10-11, and this question continues there as TRA72.

## Track P — Connector run control

- **D124** — Index-all and reindex-all resolve the owning plugin before taking the run lock: an unowned name answers 404 with a reason, and a start that throws releases the lock (DumConnectorApi.startRun).
- **D125** — The default H2 URL opens with CASE_INSENSITIVE_IDENTIFIERS, so tenant-scoped reads find "tenant_id" on new and existing H2 files (DumTenantPredicateH2Test).
- **D126** — An aborted structured run logs its cause before finishing and records it as a note after the reason; a finish that also fails is logged apart and carries the cause as suppressed.
- **D127** — Structured job items carry a SHA-256 of what they publish unless the plugin stamps its own, so an unchanged rerun is skipped; ReindexStrategy reads a null checksum as changed.
- **D128** — A scheduled crawl starts each structured source through DumConnectorRunService, the API's start: under its run lock, tracked, and skipped with a log line while a run of it is in progress.
- **D129** — A scheduled crawl isolates each source and each plugin: one that throws outside extraction is logged with its name, its lock is released, and the crawl moves on.
- **D130** — A run can warn without being partial: sources and coverage drift raise warnings, the console marks the run, and dumont.alerts.webhook-url hears when a source starts warning.
- **D131 (grounded list extraction)** — DumExtractionEnricher fills a multi-valued field from a DumExtractorFactory extractor; only items found in the text as whole words publish, cached and capped like classification.
- **D132** — An AEM or structured run that ends with no session (source not found, no enabled environment) calls endRunWithoutSession, which releases its lock; a failed AEM discovery finishes standalone.
- **D137** — A source learns what a canonical document resolution dropped last published: DumResolutionRun keeps the survivorship result before the drop, and deindexed(id, lastPublished) receives it.
- **D138** — A batch Turing refuses clears its documents' ledger checksums, so the next run resends them, and the run record turns partial with a note naming the source and count.
- **D139** — A site the backend did not provision becomes a run warning naming the site and reason, so the run no longer reads clean; the timeout is Turing's T1081.
- **D136** — Each source's last run is scraped as dumont_run_* from /actuator/prometheus on the management port, seeded at startup, so a failed or stalled run can page.
- **D140** — A structured field declares a search weight from 1 to 100, and the site manifest Dumont sends carries it to Turing.

## Track Q — Builds from released dependencies

- **D141** — Dumont pins turing-java-sdk 2026.3.6, the first Central release carrying searchWeight (T1082), so it compiles without a locally installed Turing SDK.

## Track R — API access and credential handling

- **D142** — The unauthenticated status probe answers status and providers only; the console stopped reading a key from it, since session auth and Turing's proxy already supply one.

## Track S — The WordPress connector plugin

- **D145** — WordPress plugin 2.0.0 ships composer.json, readme.txt, PHPCS/PHPStan/PHPUnit CI and a release zip per wordpress-v* tag, Composer-installable.
- **D146** — A save only queues the post; WP-Cron or `wp dumont queue run` sends it with retry and backoff (design §XLIX.2 recorded in `wordpress/src/includes/class-viglet-dumont-queue.php`).
- **D147** — Taxonomies, meta and core fields map per type to typed Turing fields and facets, provisioned via the manifest (design §XLIX.3 recorded in `wordpress/src/includes/class-viglet-dumont-mapping.php`).
- **D148** — Ids carry a site key, so sites share one SN site; delete-all removes only that key, checked on a local Turing (design §XLIX.4 recorded in `wordpress/src/includes/class-viglet-dumont-indexer.php`).
- **D149** — wp dumont status, index, reindex and sweep (and REST) run the index from a script; checked on Turing (design §XLIX.5 recorded in `wordpress/src/includes/class-viglet-dumont-operations.php`).
- **D150** — The admin runs without a search engineer: setup, fields, health with events, per-post state, in pt_BR (design §XLIX.6 recorded in `wordpress/README.md`).
- **D154** — A Health page built with wp-scripts shows connection, counts per type and the queue, with retry and send now (design §XLIX.10 recorded in `wordpress/README.md`).
- **D155** — A Fields page edits the mapping per type, shows each field's state in Turing and previews a real document (design §XLIX.11 recorded in `wordpress/assets/admin/mapping.js`).
- **D156** — A Setup page tests the connection step by step, names what failed, creates the SN site and runs the index (design §XLIX.12 recorded in `wordpress/src/includes/class-viglet-dumont-diagnostics.php`).
- **D151** — Constants or env vars set the connection, read-only in the admin and never stored; the key is never echoed (design §XLIX.7 recorded in `wordpress/README.md`).
- **D152** — Search fits block themes: theme-native results, SDK modules, and a Turing search block with facets (design §XLIX.8 recorded in `wordpress/README.md`).
- **D157** — viglet-dumont/search ships the SDK configured for the site; a browser module searched Turing with it (design §XLIX.13 recorded in `wordpress/src/public/search.js`).
- **D153** — The WordPress guide and blog post describe the shipped plugin, with a screenshot per screen (vigletdocs 72e2d2e).
- **D158** — Content Overview counts only post types, so the total is right and no undefined card shows.
- **D159** — Playwright drives every admin screen and the site search against the stack, and found a lost facet bug (design §XLIX.15 recorded in `wordpress/README.md`).
- **D160** — The browser suite runs axe on every screen, the site at three viewports, screenshot baselines, speed budgets and keyboard paths; look gains --a11y (design §XLIX.16 recorded in `wordpress/README.md`).
- **D161** — Site specs run under classic and third-party themes, pt-br fails on English leaks, a multisite network checks per-site search; CI moved to D182 (design §XLIX.17 recorded in `wordpress/README.md`).
- **D162** — Results are cards (type, locale date, address, marked snippet, image), with skeleton, empty-with-latest-posts and retry-or-WordPress error states.
- **D163** — The search box is a WAI-ARIA combobox with instant prefix results, a clear button, focus kept in the box and removable recent searches, all keyboard-tested.
- **D164** — A mapped date or number field can offer range facets: Fields opts it in, the manifest declares a Turing custom facet, and the block filters by range with the range's label on the chip.
- **D165** — The URL holds query, filters, page (pg) and sort, Back and reload restore them, a sort and page-size setting exist, and refined URLs are noindex.
- **D167** — Ctrl/Cmd+K and the theme's Search block open an instant-search dialog with keyboard-chosen results; Enter goes to the full search page, a setting or found automatically.
- **D168** — The block sets filter place, list or grid, result parts and a style, and the editor previews Turing's real answer through the block's own markup; inner blocks moved to D185.
- **D169** — Health and wp dumont ai status show answer, chat, NL filters and copilot as on or off with Turing's reason; provisioning GenAI moved to D186 behind T1116.
- **D166** — An opt-in panel streams Turing's answer to a question with numbered sources and a helpful verdict, drawn only when sources are cited; the live-LLM run is D174's.
- **D171** — The editor panel scores the saved post for each audience persona of the site's agent, with the passages that miss and a rewrite; hidden on a site with none.
- **D172** — Search tuning manages synonyms (with filtered suggestions) and promoted results, previews a search before and after, and the editor promotes a post; boosts moved to D187.
- **D174** — The live-LLM suite passes whole on Turing with T1153, the copilot included; the teardown waits for the seeded posts to leave the index; a run costs about $0.002.
- **D176** — The search block and overlay report opened results to Turing and settled searches and clicks to the site's Google tag, opt-in and gated by consent (design §XLIX.31 recorded in `wordpress/README.md`).
- **D177** — A server-drawn Related content block, also placed after chosen post types, lists this site's similar posts, never itself, with clicks reported (design §XLIX.28 recorded in `wordpress/README.md`).
- **D180** — Events store a message key with its arguments and Health translates it when read, so a pt_BR admin reads events WP-CLI wrote in English in Portuguese.
- **D181** — The block's text buttons, chips and clear button keep their link and outline look under Twenty Twenty-One, with a focus ring of their own.
- **D184** — Counts carry their singular and plural forms and the page's plural rule picks one, so a single match reads 1 result, or 1 resultado in pt_BR.
- **D185** — The search block's box, filters, results and pages can become inner blocks a theme reorders, on one store; unarranged blocks keep the default order (design §XLIX.41 recorded in `wordpress/README.md`).
- **D189** — Health's event times and last run are formatted on the server in the site's formats and time zone and the reader's language.
- **D170** — With Suggest filters on, a sentence typed into the search block offers Turing's grounded nl-filters reading as chips; one click applies a filter and drops its words from the query.
- **D173** — An Ask the catalog block talks to Turing's catalog copilot; its cited products show the shop's price and go in the cart with a click, beside the search block's price facets and product cards.
- **D178** — Search insights summarizes the period through Turing's language model on request, cached and rewritable, beside its tables and the metrics-off notice.
- **D182 (CI job and Linux launcher)** — The WordPress workflow gains an e2e job that starts WordPress and Turing with bin/run-docker.sh, logs the Turing it ran against, runs the browser suite and keeps its artifacts.
- **D183** — The block and the overlay send as-you-type searches as typed, in Turing's instant mode: the last word matches as a prefix with no wildcard guessed, and half-typed words stay out of the search log.
- **D186** — A Generative AI setting (off by default) declares the manifest's genAi block, so provisioning turns a site's answers on; the Fields screen warns before it applies.
- **D187** — The Search tuning screen sets newer first, sticky posts first and a post type up or down, and a post stuck from Quick Edit is reindexed so the sticky boost sees it.
- **D191** — The AI-off answer spec replays Turing's own "not enabled" event, so the default suite no longer depends on whether the stack was started with an OpenAI key.
- **D205** — The Settings Search panel baseline includes D186's Generative AI section, and its six strings read in Portuguese in a pt_BR admin.
- **D206** — The Search insights spec finds its failed search on a stack with a day of runs logged, searching one fixed term three times.
- **D190 (delete-all and Related)** — Delete-all is one Turing delete by this site's key, and Related asks Turing for this site's neighbours only, three per post shown.
- **D192** — Every settings select keeps its text clear of WordPress's arrow, in English and pt_BR, and the content select lines up with Start Indexing.
- **D193** — The Tarnfell storefront scores Lighthouse 90+ on home and results, its hero is the search block, and a spec walks from search to cart by keyboard (design §XLIX.49 recorded in `wordpress/README.md`).
- **D211** — The admin build reads only assets/admin and stays flat across builds, and the zip script refuses a build folder holding anything nested.
- **D200** — Compared products get a neutral AI summary above their table, shown only when every figure in it is the table's; without GenAI the table stands alone.
- **D202** — Popular chips honour a Never show list from Settings, and a site with no history shows the admin's own list, each chip still checked to find something.
- **D207** — Each top search on Search insights reads up, down, steady or new against the period before; the live count was left out, its last minute empty on most sites.
- **D216** — Every summary the plugin asks Turing for names the SN site, so its spend counts in that site's cost summary and budget.
- **D215** — Search insights reads whether Turing keeps metrics from the click report it already fetches; the probe click runs only against a Turing older than 2026.3.6.
- **D214** — Turning GenAI on from the plugin keeps an agent chosen in Turing's console, and binds the Default AI Agent only to a site that has none.
- **D208** — A search block holding only its box sends Enter or a picked suggestion to the full search page; the demo hero now uses it.
- **D209** — A search page opened with a query gets its first results, facets and pages from the server, unrecorded in metrics, and shifts under 0.1 as its script answers.
- **D194** — The demo store's journal of 22 guides names its products, so one search returns a guide and then what it recommends, and seven curated searches are checked.
- **D195** — The demo's "How it works" tour marks each Turing feature on the page with a keyboard-reachable note in English or Portuguese that never covers it.
- **D210** — Related after posts skips a post whose block template or its parts place the block, so a block theme shows one (design §XLIX.66 recorded in `wordpress/src/includes/class-viglet-dumont-related.php`).
- **D212** — Related and copilot product cards offer Compare, ticking into the one list the search cards use, with the compare link once two are ticked.
- **D213** — A daily job asks Turing's LLM which popular searches are one want; the chips show each group once, under its most searched spelling.
  checked **Two spellings of one search show as one chip** A spec seeds two variants of one search and finds a single chip under the more searched one.
- **D219** — run-docker.cmd demo starts Tarnfell on its own WordPress (38082) and SN site Demo, so demo and tests run at once and never share documents (design §XLIX.75 recorded in `wordpress/README.md`).
- **D217** — With DEMO_LOCALE=pt_BR the demo's catalog, guides, facets and synonyms are Portuguese, and its curated searches are checked in that language.
- **D218** — The seed draws a WebP illustration per product colour and a landscape per guide (CC0, about 7 KB), so the grid and pages show pictures (design §XLIX.74 recorded in `wordpress/demo/seed/CREDITS.md`).
- **D220** — The demo specs run on the demo's own WordPress with no switch or reseed, test:e2e:demo runs them alone, and the cross-site clean-up is gone.
- **D221** — Global and demo setup compare WP_URL, WP_MS_URL and WP_DEMO_URL with the stack's siteurl and stop before seeding, naming both, when they differ.
- **D222** — Related after posts skips content filtered while an excerpt is built, so a product's generated summary is its own text and the box shows once.
- **D223** — Tarnfell prints its text through a tarnfell text domain with a pt_BR translation, so a Portuguese demo reads Portuguese throughout and the tour spec runs in it.
- **D188** — On SDK 2026.3.10 the search controllers report through the bus with consent and no cookie, GA4 gets search and select_content, and a search left behind is reported.
- **D224** — On SDK 2026.3.11 the controller tells a refinement from a new search, so the plugin's bus filter and settled flag are gone and the spec passes unchanged.
- **D175** — A Site assistant block, or a launcher on every page, opens a chat streaming Turing's answers from the site's posts with the posts it used, kept across pages.
- **D228** — npm run lint:js is clean again: the woo keyboard spec reads the focused element through the page's own document, as the fixtures do.
- **D179** — The demo Showcase, Health naming each feature's block, npm run e2e:tour and the vigletdocs Turing on WordPress page show Turing's WordPress features together.
- **D198** — The demo's generative AI is on, with the assistant on every page, a no-key notice, and curated prompts answered live from the guides they name.
- **D225 (readme sections)** — readme.txt has an External services section naming what goes to Turing and when, a Plugins > Add New install, and a link to the source.
- **D226** — The WordPress plugin workflow runs Plugin Check on the built package on every change and gates the release; today it reports no errors.
- **D229 (21 of 23 warnings)** — Plugin Check reports two warnings, both the name's trademark: template variables are prefixed and the two exclusions and the textdomain call are annotated.
- **D201** — Live kits on the demo stage only seeded products, mark what the shop cannot sell and reach the cart at WooCommerce's total (design §XLIX.57 recorded in `wordpress/tests/e2e/demo-ai/kit.spec.mjs`).
- **D199** — A size is read in code, grounded on the site's own values; the Tarnfell search page applies what a sentence names, and demo specs cover it (design §XLIX.55 recorded in `wordpress/README.md`).
- **D236** — The e2e seed installs a test-stack mu-plugin that hides core's update nag, so the admin axe checks pass whatever core has released (design §XLIX.86 recorded in `wordpress/README.md`).
- **D203 (story script and live spec)** — npm run e2e:story plays demo/story.md live, from popular chips to a paid order and the owner's insights, on a demo with a compare page and test checkout.

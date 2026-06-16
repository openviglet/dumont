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

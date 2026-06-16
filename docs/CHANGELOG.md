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

# Dumont EDS Plugin Sample

A working example of the Dumont **Edge Delivery Services (EDS)** connector plugin that
crawls and (re)indexes a public **Adobe Experience Manager** site —
[`https://main--wknd--hlxsites.aem.live/`](https://main--wknd--hlxsites.aem.live/) — into
Viglet Turing, with no AEM author/publish instance required.

## Overview

Unlike the AEM plugin (which talks to an AEM author/publish API), the EDS plugin treats an
AEM Edge Delivery / `aem.live` site as a plain published website:

1. **Discover** — reads the site's `query-index.json` spreadsheet (and, optionally, the
   `sitemap.xml`) to enumerate page paths.
2. **Fetch** — downloads each HTML page (virtual-thread fan-out, rate limited).
3. **Extract** — pulls `og:*` / `<meta>` metadata and the main body text via a CSS selector.
4. **Enrich** — derives facets from the URL path (e.g. `section`) and applies field mappings.
5. **Index** — pushes one `TurSNJobItem` per page to Turing, and emits `DELETE`s for pages
   that have disappeared since the previous crawl (incremental, checksum-based).

This sample targets the public **WKND** demo site, so you can run it end-to-end with only a
Turing instance — nothing to install on the Adobe side.

## What This Sample Includes

### Custom Extensions (`src/main/java`)

- **`DumEDSExtPathLocale`** — a `DumEDSExtLocaleInterface` that infers the page `Locale`
  from the first path segment (`/pt-br/...` → `pt-BR`). WKND is English-only, so the sample
  pins an explicit `locale` (`en_US`) instead of wiring this resolver; the class is kept as a
  reference for how a multi-locale EDS site would set `localeClass` (and leave `locale` unset).
- **`DumEDSExtMetaName`** — a `DumEDSExtInterface` reference implementation that reads a
  custom `<meta name="...">` tag (WKND exposes `<meta name="theme">`). Subclass and override
  `metaName()` to capture site-specific metadata the built-in extractor doesn't pick up.

### Pre-configured WKND Source (`scripts/wknd/export/wknd.json`)

Seeded automatically on first boot by `DumEDSOnStartupJpa`, which imports every `*.json` in
`$user.dir/export`.

## Prerequisites

1. **Java 21+** ☕
2. **Maven** (the `mvnw` wrapper is bundled) 📦
3. **Apache Ant** (for the `build.xml` packaging target) 🐜
4. **Viglet Turing** running and reachable (default `http://localhost:2700`) with a
   Semantic Navigation site named **`wknd`** and a valid API key.

> No AEM instance is needed — the sample reads the public `aem.live` site directly.

## Quick Start

```cmd
cd eds\eds-plugin-sample

REM Build the connector + EDS plugin + this sample, assemble dist\wknd, and run it
compile-and-run.cmd
```

`compile-and-run.cmd` runs `ant wknd` (which calls `mvnw clean install package -am`) and then
`dist\wknd\run.cmd`. The Ant target assembles:

```
dist/wknd/
├── dumont-connector.jar          # Spring Boot app (connector-app)
├── libs/eds-plugin.jar           # EDS connector plugin
├── plugins/eds-plugin-sample.jar # this sample's extensions
├── dumont-connector.properties   # runtime config (port 30140, turing.url/apiKey)
└── export/wknd.json              # seed source config (imported on first boot)
```

### Configure before running

Edit `scripts/wknd/dumont-connector.properties` (the source of truth — `dist/` is generated):

| Property | Default | Purpose |
|----------|---------|---------|
| `server.port` | `30140` | Connector port |
| `turing.url` | `http://localhost:2700` | Turing endpoint |
| `turing.apiKey` | _(set me)_ | Turing API key |
| `dumont.indexing.provider` | `turing` | `turing`, `solr`, or `elasticsearch` |

## Reindexing WKND

On first boot the seed in `export/wknd.json` is imported as an EDS source. To trigger a
crawl + index, call the EDS source API (`/api/v2/eds/source`):

```bash
# List sources to get the generated source id
curl -H "Key: <turing.apiKey>" http://localhost:30140/api/v2/eds/source

# Trigger discovery + (re)indexing for that source
curl -H "Key: <turing.apiKey>" http://localhost:30140/api/v2/eds/source/{id}/index
```

Re-running `/index` is incremental: pages whose `lastModified` is unchanged are skipped,
changed pages are re-fetched, and paths that have vanished are deleted from the index.

## How the WKND Source Is Configured

`export/wknd.json` (EDS exchange format — a list of `sources`):

```json
{
  "sources": [
    {
      "title": "WKND",
      "locale": "en_US",
      "baseUrl": "https://main--wknd--hlxsites.aem.live",
      "sitemapFallback": "/sitemap.xml",
      "bodySelector": "main",
      "facetPathPattern": "/{section}/**",
      "turSNSites": ["wknd"],
      "indexes": ["/query-index.json"],
      "excludePatterns": ["/drafts/**", "/experiments/**", "..."],
      "removeSelectors": ["header", "nav", "footer", "aside", ".breadcrumb"],
      "facets": [{ "name": "section", "ordinal": 0, "allowedValues": "adventures,magazine,authors,faqs,about-us,adventures-for-students" }],
      "fieldMappings": []
    }
  ]
}
```

| Field | Description |
|-------|-------------|
| `baseUrl` | Origin of the EDS site |
| `indexes` | EDS `query-index.json` spreadsheet(s) used for discovery |
| `sitemapFallback` | Sitemap merged into discovery to catch pages absent from the index |
| `bodySelector` | CSS selector for the main content (`main` on `aem.live`) |
| `removeSelectors` | Chrome elements stripped before body extraction |
| `excludePatterns` | Glob patterns dropping WKND's demo/draft noise (`/drafts/**`, `/experiments/**`, …) |
| `facetPathPattern` / `facets` | Derives a `section` facet from the first path segment |
| `locale` | Fixed locale applied to every document (`en_US`; WKND is English-only) |
| `turSNSites` | Target Turing Semantic Navigation site(s) — editable in the UI's General tab |
| `fieldMappings` | Optional `sourceField` → `targetField` renames (empty here) |

## Writing Your Own Extensions

1. Implement a commons interface:
   - `DumEDSExtLocaleInterface` — resolve a `Locale` per page (set `localeClass`).
   - `DumEDSExtInterface` — compute a custom attribute from the page `DumEDSContext`.
2. Package it in a plugin JAR under `dist/wknd/plugins/`.
3. Reference it from `wknd.json` (e.g. `localeClass`) — classes are loaded and cached via
   `DumCustomClassCache`, so implementations must be thread-safe.

## Troubleshooting

**Q: API returns HTTP 422**
A: The `Key` header doesn't match `turing.apiKey`. Set the same value in
`dumont-connector.properties` and the request header.

**Q: Nothing gets indexed**
A: Confirm the Turing SN site `wknd` exists, and that
`https://main--wknd--hlxsites.aem.live/query-index.json` is reachable from your network.

**Q: Too many/irrelevant pages indexed**
A: Tighten `excludePatterns` — WKND ships large `/drafts/**`, `/experiments/**`, and
`/mystique/**` trees that this sample already filters out.

**Q: Build fails**
A: Ensure Java 21+ and that `JAVA_HOME` points at it.

Enable verbose logging with `-Dlogging.level.com.viglet=DEBUG` (already on in the sample
properties).

## Links

- 📋 [GitHub Discussions](https://github.com/openviglet/dumont/discussions)
- 🐛 [Report Issues](https://github.com/openviglet/dumont/issues)
- 📖 [Main Documentation](../../README.adoc)

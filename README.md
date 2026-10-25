# Viglet Dumont: the open source Data Exchange Platform

![Viglet Dumont, Data Exchange Platform](.github/banner.png)

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![GitHub release](https://img.shields.io/github/release/openviglet/dumont.svg)](https://github.com/openviglet/dumont/releases)
[![Java 21+](https://img.shields.io/badge/Java-21%2B-orange)](https://openjdk.org/)
[![Spring Boot 4.x](https://img.shields.io/badge/Spring%20Boot-4.x-green)](https://spring.io/projects/spring-boot)

**Viglet Dumont** is an open source **Data Exchange Platform**: it connects your content
sources (Adobe Experience Manager, WordPress, databases, file systems and web sites) and
delivers their content to [Viglet Turing](https://github.com/openviglet/turing), Apache
Solr or Elasticsearch. It runs on your own servers, is built with Java 21 and Spring Boot,
and is extended through plugins.

> 🌐 **Product page:** [viglet.org/dumont](https://www.viglet.org/dumont/) ·
> 📚 **Documentation:** [docs.viglet.org/dumont](https://docs.viglet.org/dumont/) ·
> 🔎 **Search on top:** [Viglet Turing](https://github.com/openviglet/turing)

## Who it is for

Teams that need the content of a CMS, a database or a set of sites in a search index, and
kept there: new pages indexed, changed pages updated, removed pages taken out. If you are
adding search to an **Adobe AEM** site (on-premise, AMS or AEM as a Cloud Service) or to
**WordPress**, Dumont is the part that reads the content and keeps the index in step with it.

Dumont is the way in; [Viglet Turing](https://github.com/openviglet/turing) is the search
and AI chat on top. You can also use Dumont alone, with your own Solr or Elasticsearch.

## 🚀 Features 

- **Multi-Source Connectors**: Connect and index content from various data sources
  - Adobe Experience Manager (AEM) — on-premise, AMS and as a Cloud Service
  - Relational Databases (JDBC)
  - File Systems
  - Web Crawlers
  - WordPress
  
- **Flexible Search Engine Support**: Index content to your preferred search platform
  - Viglet Turing (default)
  - Apache Solr
  - Elasticsearch
  
- **Plugin Architecture**: Easily extend functionality with custom plugins

- **Structured Sources**: Model a source by its schema rather than as a link spider. The declared
  fields are sent to the search index ahead of the documents, so they exist as declared instead of
  being inferred from the first value seen; values are grounded rather than guessed, enrichment runs
  through the same schema, per-field coverage is tracked so a layout change cannot silently drop a
  field, and an interrupted run leaves the previous index intact.

- **Guided Onboarding**: Point the wizard at a URL and Dumont infers the source's shape and field
  manifest for you to correct. A dry run then shows what indexing *would* produce — per-field
  coverage, records that would be skipped, and a diff against the last run — writing nothing. Only
  a draft you confirm gets indexed; nothing is ever published on a guess.

- **Data Quality & Trust**: A run that reports success is not the same as an index you can trust.
  Per-field coverage is compared between runs, so a selector that stops matching raises a drift flag
  naming the field. Every published field carries where its value came from. An incomplete run never
  de-indexes what it did not reach. And a scheduled audit compares the source *and* the connector's
  ledger against the index itself: documents that never landed or went stale are requeued, and one
  still indexed after leaving the source is written into a dated report you review and apply —
  never deleted on the strength of a single discovery pass. Underneath all of it, an indexing log
  records every status a document passed through, attributed to the source and the run that caused
  it and queryable by either, so what happened can be checked rather than believed. And because a
  failure that looks like an absence is the way such a log lies, a store that cannot be reached
  answers `503` instead of the empty page a quiet connector would return, and a row it will not
  accept is reported rather than dropped in silence.

- **Web Console**: Configure sources, launch indexing and watch it run, in English or Portuguese.
  Built from the same component library as Viglet Turing and Viglet Shio, and mountable inside
  Turing as well as standalone.
  - Every screen that configures or monitors a connector is served in the *bento* interface — a
    fixed nav rail, section hubs and a `⌘K` command palette that reaches all of them. Sources and
    their forms, indexing rules and the indexing manager, monitoring, statistics, coverage,
    double-check, orphan reports, plugins, system info and AI insights.
  - The previous console is still served at `/admin`, unchanged and complete, so a bookmark into it
    keeps working. Sign-in, first-run setup and the user/group/role screens live there only.
  
- **Enterprise Ready**:
  - JMS message queue support
  - Scheduled batch processing
  - Delta/incremental indexing
  - REST API
  - Caching support

## 📦 Modules

| Module | Description |
|--------|-------------|
| `commons` | Shared utilities and common components |
| `spring` | Spring Boot integration and configuration |
| `connector` | Core connector application with indexing plugins, and the structured-source SPI (`connector-commons`) |
| `aem` | Adobe Experience Manager connector and plugin |
| `aem-commons` | AEM shared components |
| `db` | Database (JDBC) connector |
| `filesystem` | File system connector |
| `web-crawler` | Web crawler connector |
| `wordpress` | WordPress plugin: queued indexing into Turing, typed facets, WP-CLI, admin screens and a search block ([`wordpress/README.md`](wordpress/README.md)) |
| `dumont-react` | Web console (React + Vite), built into the connector's static resources |

## 🛠️ Prerequisites

- **Java 21+** ☕
- **Maven 3.6+** 📦
- **Spring Boot 4.x** 🍃
- **Node.js 26 + pnpm 11** ⚛️ — only to work on the console; the Maven build invokes them for you

## 🏗️ Building

```bash
# Clone the repository
git clone https://github.com/openviglet/dumont.git
cd dumont

# Build all modules
./mvnw clean install

# Build specific modules (skip tests)
./mvnw clean install -DskipTests
```

The console is built and bundled by the commands above. To work on it with hot reload:

```bash
cd dumont-react
pnpm install
pnpm run dev          # Vite dev server
pnpm test             # Vitest
pnpm run lint         # ESLint
pnpm run lint:ds      # fail on a component the design system already exports
pnpm run census       # which surfaces are on which chrome
```

Every push and pull request runs the Maven build (which builds the console) plus ESLint and the
design system duplicate check.

## ⚙️ Configuration

### Indexing Provider

Configure your preferred search engine in `application.yaml`:

```yaml
dumont:
  indexing:
    provider: turing  # Options: turing (default), solr, elasticsearch
```

#### Viglet Turing (Default)
```yaml
turing:
  url: http://localhost:2700
  apiKey: your-api-key
```

#### Apache Solr
```yaml
dumont:
  indexing:
    provider: solr
    solr:
      url: http://localhost:8983/solr
      collection: dumont
```

#### Elasticsearch
```yaml
dumont:
  indexing:
    provider: elasticsearch
    elasticsearch:
      url: http://localhost:9200
      index: dumont
      username: ~
      password: ~
```

## 📚 Documentation

- [Indexing Plugins Guide](connector/INDEXING_PLUGINS.md)
- [AEM Plugin Sample](aem/aem-plugin-sample/README.md)

## 🔌 Connectors

### AEM Connector

Index content from Adobe Experience Manager to your search engine. Each source picks the tier it reads: the author
servlets (`infinity.json`, QueryBuilder) for on-premise and AMS, or the publish delivery APIs (`model.json`) for AEM
as a Cloud Service, where the author servlets are not served. Extraction is declared per content type, optionally
from a versioned recipe shared across sites.

```bash
cd aem/aem-plugin-sample
./compile-and-run.cmd
```

### Database Connector

Import content from relational databases via JDBC.

```bash
cd db/db-sample
./mvnw exec:java
```

### Filesystem Connector

Index files and documents from local or network file systems.

```bash
cd filesystem/fs-connector
./turing-filesystem.sh --server http://localhost:2700 --api-key YOUR_KEY --source /path/to/files
```

### Web Crawler

Crawl and index web content.

```bash
cd web-crawler/wc-sample
./mvnw exec:java
```

## 🏛️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Data Sources                              │
│  ┌─────┐  ┌─────┐  ┌─────────┐  ┌───────────┐  ┌───────────┐   │
│  │ AEM │  │ DB  │  │ Files   │  │ Web Pages │  │ WordPress │   │
│  └──┬──┘  └──┬──┘  └────┬────┘  └─────┬─────┘  └─────┬─────┘   │
└─────┼────────┼──────────┼─────────────┼──────────────┼──────────┘
      │        │          │             │              │
      └────────┴──────────┴──────┬──────┴──────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │    Dumont Connector     │
                    │   (Spring Boot App)     │
                    │  ┌──────────────────┐   │
                    │  │  Message Queue   │   │
                    │  │      (JMS)       │   │
                    │  └────────┬─────────┘   │
                    │           │             │
                    │  ┌────────▼─────────┐   │
                    │  │ Indexing Plugin  │   │
                    │  └────────┬─────────┘   │
                    └───────────┼─────────────┘
                                │
      ┌─────────────────────────┼─────────────────────────┐
      │                         │                         │
┌─────▼─────┐           ┌───────▼───────┐         ┌──────▼──────┐
│  Viglet   │           │    Solr       │         │Elasticsearch│
│  Turing   │           │               │         │             │
└───────────┘           └───────────────┘         └─────────────┘
```

## ❓ FAQ

**Does Dumont work with AEM as a Cloud Service?**
Yes. Each source picks the tier it reads: the author servlets for on-premise and AMS, or the
publish delivery APIs (`model.json`) for AEM as a Cloud Service.

**Do I need Viglet Turing to use Dumont?**
No. Turing is the default target, but Dumont also indexes into Apache Solr and
Elasticsearch. Set `dumont.indexing.provider`, as shown under Configuration above.

**What happens when a page is removed from the source?**
Dumont does not de-index on a single pass that failed to see it. A scheduled audit compares
the source, the connector's ledger and the index, and writes what is still indexed after
leaving the source into a dated report that you review and apply.

**Is it free?**
Yes. Apache License 2.0, self-hosted.

## 🧩 Part of the Viglet suite

![The Viglet suite: Dumont, Turing and Shio](.github/viglet-suite.png)

Three open source products that work together, or each on its own:

| Product | What it does |
|---|---|
| **Viglet Dumont** · Data Exchange Platform | This repository: connects your sources and delivers their content to a search engine |
| [**Viglet Turing**](https://github.com/openviglet/turing) · Enterprise Search | Search, semantic navigation, RAG chat with citations and AI agents over that content, self-hosted |
| [**Viglet Shio**](https://github.com/openviglet/shio) · Agent-ready CMS | A CMS that people run from the console and AI agents run over MCP; it renders pages or delivers them over REST and GraphQL |

More at [viglet.org](https://www.viglet.org).

## 🤝 Contributing

Contributions are welcome: open an [issue](https://github.com/openviglet/dumont/issues) for
bugs and requests, or fork the repository and open a pull request.

## 📄 License

Apache License 2.0: see the [LICENSE](LICENSE) file.

---

Built by the [Viglet](https://www.viglet.org) Team ·
**[viglet.org/dumont](https://www.viglet.org/dumont/)**

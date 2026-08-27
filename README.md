# Viglet Dumont

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![GitHub release](https://img.shields.io/github/release/openviglet/dumont.svg)](https://github.com/openviglet/dumont/releases)
[![Java 21+](https://img.shields.io/badge/Java-21%2B-orange)](https://openjdk.org/)
[![Spring Boot 4.x](https://img.shields.io/badge/Spring%20Boot-4.x-green)](https://spring.io/projects/spring-boot)

**Viglet Dumont** is a powerful Data Exchange Platform (DEP) that enables seamless content indexing and search integration across multiple data sources and search engines. Built with Java and Spring Boot, Dumont provides a flexible, plugin-based architecture for enterprise content management and search solutions.

## 🚀 Features 

- **Multi-Source Connectors**: Connect and index content from various data sources
  - Adobe Experience Manager (AEM) — on-premise, AMS and as a Cloud Service
  - Relational Databases (JDBC)
  - File Systems
  - Web Crawlers
  - WordPress
  
- **Flexible Search Engine Support**: Index content to your preferred search platform
  - Viglet Turing ES (default)
  - Apache Solr
  - Elasticsearch
  
- **Plugin Architecture**: Easily extend functionality with custom plugins

- **Structured Sources**: Model a source by its schema rather than as a link spider. The declared
  fields are sent to the search index ahead of the documents, so they exist as declared instead of
  being inferred from the first value seen; values are grounded rather than guessed, enrichment runs
  through the same schema, per-field coverage is tracked so a layout change cannot silently drop a
  field, and an interrupted run leaves the previous index intact.

- **Data Quality & Trust**: A run that reports success is not the same as an index you can trust.
  Per-field coverage is compared between runs, so a selector that stops matching raises a drift flag
  naming the field. Every published field carries where its value came from. An incomplete run never
  de-indexes what it did not reach. And a scheduled audit compares the source *and* the connector's
  ledger against the index itself, reporting documents that never landed, went stale, or linger
  after leaving the source.

- **Web Console**: Configure sources, launch indexing and watch it run, in English or Portuguese.
  Built from the same component library as Viglet Turing ES and Viglet Shio, and mountable inside
  Turing as well as standalone.
  - Every screen that configures or monitors a connector is served in the *bento* interface — a
    fixed nav rail, section hubs and a `⌘K` command palette that reaches all of them. Sources and
    their forms, indexing rules and the indexing manager, monitoring, statistics, coverage,
    double-check, plugins, system info and AI insights.
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
| `wordpress` | WordPress plugin for content indexing |
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

#### Viglet Turing ES (Default)
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
│  Turing   │           │    Solr       │         │Elasticsearch│
│    ES     │           │               │         │             │
└───────────┘           └───────────────┘         └─────────────┘
```

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## 👨‍💻 Author

**Viglet Team** - [Viglet](https://viglet.org)

## 🔗 Links

- [GitHub Repository](https://github.com/openviglet/dumont)
- [Viglet Website](https://viglet.com)
- [Issue Tracker](https://github.com/openviglet/dumont/issues)

# Viglet Dumont

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![GitHub release](https://img.shields.io/github/release/openviglet/dumont.svg)](https://github.com/openviglet/dumont/releases)
[![Java 21+](https://img.shields.io/badge/Java-21%2B-orange)](https://openjdk.org/)
[![Spring Boot 4.x](https://img.shields.io/badge/Spring%20Boot-4.x-green)](https://spring.io/projects/spring-boot)

**Viglet Dumont** is a powerful Data Exchange Platform (DEP) that enables seamless content indexing and search integration across multiple data sources and search engines. Built with Java and Spring Boot, Dumont provides a flexible, plugin-based architecture for enterprise content management and search solutions.

## 🚀 Features 

- **Multi-Source Connectors**: Connect and index content from various data sources
  - Adobe Experience Manager (AEM)
  - Relational Databases (JDBC)
  - File Systems
  - Web Crawlers
  - WordPress
  
- **Flexible Search Engine Support**: Index content to your preferred search platform
  - Viglet Turing ES (default)
  - Apache Solr
  - Elasticsearch
  
- **Plugin Architecture**: Easily extend functionality with custom plugins
  
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
| `connector` | Core connector application with indexing plugins |
| `aem` | Adobe Experience Manager connector and plugin |
| `aem-commons` | AEM shared components |
| `db` | Database (JDBC) connector |
| `filesystem` | File system connector |
| `web-crawler` | Web crawler connector |
| `wordpress` | WordPress plugin for content indexing |

## 🛠️ Prerequisites

- **Java 21+** ☕
- **Maven 3.6+** 📦
- **Spring Boot 4.x** 🍃

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

Index content from Adobe Experience Manager to your search engine.

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

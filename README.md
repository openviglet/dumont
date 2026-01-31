# Viglet Dumont - Enterprise Search Connector

[![Build Status](https://github.com/openviglet/dumont/workflows/Java%20CI/badge.svg)](https://github.com/openviglet/dumont/actions)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/com.viglet.dumont/dumont.svg)](https://search.maven.org/artifact/com.viglet.dumont/dumont)

## 📋 Overview

Viglet Dumont is a powerful and scalable data extraction and indexing platform that connects various data sources to search engines like Solr, Elasticsearch, and Turing. It provides a flexible architecture for indexing content from multiple sources with support for incremental updates, dependency tracking, and real-time monitoring.

## ✨ Features

- 🔌 **Multiple Connectors**
  - Filesystem Connector
  - Database Connector
  - Adobe AEM Connector
  - Web Crawler Connector
  
- 🔍 **Search Engine Support**
  - Apache Solr
  - Elasticsearch
  - Viglet Turing
  
- 📊 **Advanced Capabilities**
  - Real-time monitoring and validation
  - Incremental indexing with checksum-based change detection
  - Dependency tracking and cascade updates
  - Asynchronous processing with JMS queues
  - RESTful API for management and monitoring
  
- 🚀 **Scalability**
  - Spring Boot-based microservices architecture
  - Async processing support
  - Caching with Spring Cache abstraction
  - JMS message queue integration

## 🏗️ Architecture

```
┌─────────────────┐
│  Data Sources   │
│  (FS, DB, AEM)  │
└────────┬────────┘
         │
         v
┌─────────────────┐
│   Connectors    │
│   (Plugins)     │
└────────┬────────┘
         │
         v
┌─────────────────┐
│ Processing Queue│
│     (JMS)       │
└────────┬────────┘
         │
         v
┌─────────────────┐
│  Indexing Layer │
│ (Solr/ES/Turing)│
└─────────────────┘
```

## 🚀 Quick Start

### Prerequisites

- Java 21 or higher
- Maven 3.8+
- Database (PostgreSQL, MySQL, or H2 for development)
- ActiveMQ or Artemis (for message queue)

### Installation

```bash
# Clone the repository
git clone https://github.com/openviglet/dumont.git
cd dumont

# Build the project
mvn clean install

# Run the connector application
cd connector/connector-app
mvn spring-boot:run
```

### Using Docker

```bash
# Build and run with Docker Compose
docker-compose up -d
```

The application will be available at `http://localhost:8080`

## 📖 Documentation

- [Improvements Plan](IMPROVEMENTS.md) - Comprehensive guide for scalability and best practices
- [Getting Started Guide](docs/getting-started.md) - Step-by-step setup guide
- [Configuration Guide](docs/configuration.md) - Configuration options
- [API Documentation](http://localhost:8080/swagger-ui.html) - Interactive API documentation
- [Architecture Overview](docs/architecture.md) - System architecture details
- [Indexing Plugins](connector/INDEXING_PLUGINS.md) - Custom plugin development

## 🔧 Configuration

Basic configuration in `application.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/dumont
    username: dumont
    password: your-password
  
dumont:
  dependencies:
    enabled: true
  indexing:
    provider: TURING  # Options: SOLR, ELASTICSEARCH, TURING
```

## 📡 API Endpoints

### Status
```bash
GET /api/v2/connector/status
```

### Index All Content
```bash
GET /api/v2/connector/index/{source}/all
```

### Index Specific Content
```bash
POST /api/v2/connector/index/{source}
Content-Type: application/json

["content-id-1", "content-id-2"]
```

### Validate Source
```bash
GET /api/v2/connector/validate/{source}
```

## 🔌 Connectors

### Filesystem Connector
Index files and documents from filesystem.

```bash
cd filesystem/fs-app
mvn spring-boot:run
```

### Database Connector
Index content from relational databases.

```bash
cd db/db-app
mvn spring-boot:run
```

### AEM Connector
Index content from Adobe Experience Manager.

```bash
cd aem/aem-app
mvn spring-boot:run
```

### Web Crawler
Crawl and index web content.

```bash
cd web-crawler/wc-app
mvn spring-boot:run
```

## 🧪 Testing

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=DumConnectorApiTest

# Run with coverage
mvn clean test jacoco:report
```

## 📦 Building

```bash
# Build all modules
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Build with coverage
mvn clean install -P coverage
```

## 🐳 Docker Deployment

```bash
# Build Docker image
docker build -t viglet/dumont:latest .

# Run with Docker Compose
docker-compose up -d

# View logs
docker-compose logs -f dumont
```

## 🤝 Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details.

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add: amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## 👥 Authors

- **Alexandre Oliveira** - *Initial work* - [alegauss](https://github.com/alegauss)

See also the list of [contributors](https://github.com/openviglet/dumont/contributors) who participated in this project.

## 🙏 Acknowledgments

- Viglet Team for the continuous development
- Open source community for contributions and feedback
- Apache Solr, Elasticsearch, and all the amazing tools we use

## 📞 Support

- 🐛 Report bugs: [GitHub Issues](https://github.com/openviglet/dumont/issues)
- 💬 Discussions: [GitHub Discussions](https://github.com/openviglet/dumont/discussions)
- 📧 Email: support@viglet.com
- 🌐 Website: [https://viglet.com](https://viglet.com)

## 🗺️ Roadmap

See [IMPROVEMENTS.md](IMPROVEMENTS.md) for the detailed roadmap including:
- Architecture improvements with design patterns
- Scalability enhancements
- Security improvements
- Observability and monitoring
- Community growth initiatives

---

Made with ❤️ by [Viglet](https://viglet.com)

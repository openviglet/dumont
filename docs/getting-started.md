# Getting Started with Viglet Dumont

This guide will help you get started with Viglet Dumont Enterprise Search Connector.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Installation](#installation)
3. [Configuration](#configuration)
4. [Running the Application](#running-the-application)
5. [First Steps](#first-steps)
6. [Common Use Cases](#common-use-cases)
7. [Troubleshooting](#troubleshooting)

## Prerequisites

Before you begin, ensure you have the following installed:

- **Java 21 or higher**: Download from [Adoptium](https://adoptium.net/)
- **Maven 3.8+**: Download from [Maven](https://maven.apache.org/)
- **Database**: PostgreSQL 14+ (recommended) or MySQL 8+
- **Message Queue**: ActiveMQ Artemis or Apache ActiveMQ
- **Search Engine**: One of:
  - Apache Solr 9+
  - Elasticsearch 8+
  - Viglet Turing

### Verify Prerequisites

```bash
# Check Java version
java -version

# Check Maven version
mvn -version

# Check PostgreSQL
psql --version
```

## Installation

### Option 1: Build from Source

```bash
# Clone the repository
git clone https://github.com/openviglet/dumont.git
cd dumont

# Build the project
mvn clean install

# This will take a few minutes on first run
```

### Option 2: Using Docker

```bash
# Clone the repository
git clone https://github.com/openviglet/dumont.git
cd dumont

# Start all services with Docker Compose
docker-compose up -d
```

### Option 3: Download Release

```bash
# Download the latest release
wget https://github.com/openviglet/dumont/releases/latest/download/dumont-connector.jar

# Run the application
java -jar dumont-connector.jar
```

## Configuration

### Database Setup

#### PostgreSQL

```sql
-- Create database
CREATE DATABASE dumont;

-- Create user
CREATE USER dumont WITH PASSWORD 'your-password';

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE dumont TO dumont;
```

#### MySQL

```sql
-- Create database
CREATE DATABASE dumont CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Create user
CREATE USER 'dumont'@'localhost' IDENTIFIED BY 'your-password';

-- Grant privileges
GRANT ALL PRIVILEGES ON dumont.* TO 'dumont'@'localhost';
FLUSH PRIVILEGES;
```

### Application Configuration

Create or edit `connector/connector-app/src/main/resources/application.yaml`:

```yaml
spring:
  application:
    name: dumont-connector
  
  # Database Configuration
  datasource:
    url: jdbc:postgresql://localhost:5432/dumont
    username: dumont
    password: your-password
    driver-class-name: org.postgresql.Driver
  
  # JPA Configuration
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
  
  # JMS Configuration
  artemis:
    mode: native
    broker-url: tcp://localhost:61616
    user: admin
    password: admin

# Dumont Configuration
dumont:
  dependencies:
    enabled: true
  
  indexing:
    provider: SOLR  # Options: SOLR, ELASTICSEARCH, TURING

# Indexing Provider Configurations
solr:
  url: http://localhost:8983/solr

elasticsearch:
  url: http://localhost:9200

turing:
  url: http://localhost:2700

# Actuator Configuration
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
```

### Environment Variables

You can also use environment variables:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/dumont
export SPRING_DATASOURCE_USERNAME=dumont
export SPRING_DATASOURCE_PASSWORD=your-password
export DUMONT_INDEXING_PROVIDER=SOLR
export SOLR_URL=http://localhost:8983/solr
```

## Running the Application

### Using Maven

```bash
cd connector/connector-app
mvn spring-boot:run
```

### Using JAR

```bash
cd connector/connector-app/target
java -jar dumont-connector-2026.1.4.jar
```

### Using Docker

```bash
docker-compose up -d dumont
```

### Verify Application is Running

```bash
# Check health endpoint
curl http://localhost:8080/actuator/health

# Expected response:
# {"status":"UP"}
```

## First Steps

### 1. Verify Status

```bash
curl http://localhost:8080/api/v2/connector/status
```

Expected response:
```json
{
  "status": "ok"
}
```

### 2. Index Sample Content

```bash
# Index all content from a source
curl http://localhost:8080/api/v2/connector/index/my-source/all

# Response:
# {"status":"sent"}
```

### 3. Index Specific Items

```bash
curl -X POST http://localhost:8080/api/v2/connector/index/my-source \
  -H "Content-Type: application/json" \
  -d '["item-1", "item-2", "item-3"]'
```

### 4. Validate Content

```bash
curl http://localhost:8080/api/v2/connector/validate/my-source
```

Response shows missing and extra content:
```json
{
  "missing": ["item-4", "item-5"],
  "extra": ["old-item-1"]
}
```

### 5. Monitor Indexing

```bash
curl http://localhost:8080/api/v2/connector/monitoring/index/my-source
```

## Common Use Cases

### Use Case 1: Index Files from Filesystem

1. Configure filesystem connector:

```yaml
dumont:
  filesystem:
    paths:
      - /data/documents
      - /data/pdfs
    include-patterns:
      - "*.pdf"
      - "*.docx"
      - "*.txt"
    exclude-patterns:
      - "*.tmp"
      - ".*"
```

2. Run the filesystem connector:

```bash
cd filesystem/fs-app
mvn spring-boot:run
```

### Use Case 2: Index Database Content

1. Configure database connector:

```yaml
dumont:
  database:
    connections:
      - name: mydb
        url: jdbc:mysql://localhost:3306/myapp
        username: user
        password: pass
        query: "SELECT id, title, content, updated_at FROM articles"
        id-column: id
        checksum-columns:
          - content
          - updated_at
```

2. Run the database connector:

```bash
cd db/db-app
mvn spring-boot:run
```

### Use Case 3: Crawl Website

1. Configure web crawler:

```yaml
dumont:
  webcrawler:
    seeds:
      - https://example.com
    max-depth: 3
    max-pages: 1000
    politeness-delay: 1000
```

2. Run the web crawler:

```bash
cd web-crawler/wc-app
mvn spring-boot:run
```

## Troubleshooting

### Issue: Application won't start

**Problem**: Port 8080 already in use

**Solution**: Change the port in application.yaml:
```yaml
server:
  port: 8081
```

### Issue: Database connection error

**Problem**: Cannot connect to PostgreSQL

**Solution**: 
1. Verify PostgreSQL is running: `pg_isready`
2. Check connection details in application.yaml
3. Verify firewall rules
4. Check PostgreSQL logs

### Issue: No items being indexed

**Problem**: Content is not appearing in search engine

**Solution**:
1. Check logs: `tail -f logs/dumont.log`
2. Verify JMS is running: `curl http://localhost:8161`
3. Check indexing status: `curl http://localhost:8080/api/v2/connector/monitoring/index/your-source`
4. Verify search engine is accessible

### Issue: Out of Memory Error

**Problem**: Java heap space error

**Solution**: Increase JVM memory:
```bash
export JAVA_OPTS="-Xms512m -Xmx2048m"
java $JAVA_OPTS -jar dumont-connector.jar
```

### Common Log Messages

| Log Message | Meaning | Action |
|------------|---------|--------|
| `Connection refused` | Service not available | Check if all services are running |
| `Duplicate key violation` | Content already indexed | Normal for re-indexing |
| `Transaction rolled back` | Database error | Check database logs |
| `Queue is full` | Too many items in queue | Increase queue size or slow down input |

## Next Steps

- Read the [Configuration Guide](configuration.md) for advanced settings
- Check the [API Documentation](http://localhost:8080/swagger-ui.html)
- Review [Architecture Overview](architecture.md)
- Join the community on [GitHub Discussions](https://github.com/openviglet/dumont/discussions)

## Getting Help

- 📖 [Documentation](../README.md)
- 💬 [GitHub Discussions](https://github.com/openviglet/dumont/discussions)
- 🐛 [Report Issues](https://github.com/openviglet/dumont/issues)
- 📧 Email: support@viglet.com

---

Last updated: 2026-01-04

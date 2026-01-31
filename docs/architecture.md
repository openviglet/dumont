# Dumont Architecture Overview

This document provides a comprehensive overview of the Viglet Dumont architecture.

## Table of Contents

1. [System Architecture](#system-architecture)
2. [Component Overview](#component-overview)
3. [Data Flow](#data-flow)
4. [Design Patterns](#design-patterns)
5. [Scalability](#scalability)
6. [Technology Stack](#technology-stack)

## System Architecture

Dumont follows a modular, microservices-based architecture designed for scalability and extensibility.

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Data Sources Layer                        │
├──────────────┬──────────────┬──────────────┬───────────────────┤
│  Filesystem  │   Database   │  Web Pages   │  AEM/CMS Content  │
└──────┬───────┴──────┬───────┴──────┬───────┴───────┬───────────┘
       │              │              │               │
       └──────────────┴──────────────┴───────────────┘
                           │
                           v
         ┌─────────────────────────────────┐
         │    Connector Layer (Plugins)     │
         │  - FS Connector                  │
         │  - DB Connector                  │
         │  - Web Crawler                   │
         │  - AEM Connector                 │
         └────────────┬────────────────────┘
                      │
                      v
         ┌─────────────────────────────────┐
         │    Processing & Queue Layer      │
         │  - JMS Message Queue             │
         │  - Async Processing              │
         │  - Batch Processing              │
         │  - Transformation                │
         └────────────┬────────────────────┘
                      │
                      v
         ┌─────────────────────────────────┐
         │      Indexing Strategy Layer     │
         │  - Solr Strategy                 │
         │  - Elasticsearch Strategy        │
         │  - Turing Strategy               │
         └────────────┬────────────────────┘
                      │
                      v
         ┌─────────────────────────────────┐
         │    Search Engine Layer           │
         ├─────────┬─────────┬──────────────┤
         │  Solr   │  Elastic│   Turing     │
         └─────────┴─────────┴──────────────┘
```

### Component Architecture

```
dumont/
├── dumont-api/              # REST API Gateway
│   ├── Controllers
│   ├── DTOs
│   └── Validators
│
├── dumont-core/             # Core Business Logic
│   ├── Services
│   ├── Domain Models
│   └── Business Rules
│
├── dumont-connectors/       # Data Source Connectors
│   ├── FileSystemConnector
│   ├── DatabaseConnector
│   ├── WebCrawlerConnector
│   └── AEMConnector
│
├── dumont-indexing/         # Indexing Providers
│   ├── SolrProvider
│   ├── ElasticsearchProvider
│   └── TuringProvider
│
├── dumont-persistence/      # Data Persistence
│   ├── Repositories
│   ├── Entities
│   └── Migrations
│
└── dumont-commons/          # Shared Components
    ├── Utils
    ├── DTOs
    └── Constants
```

## Component Overview

### 1. Connector Layer

Responsible for extracting data from various sources.

**Components:**
- **Filesystem Connector**: Scans and extracts files
- **Database Connector**: Queries databases for content
- **Web Crawler**: Crawls and extracts web content
- **AEM Connector**: Integrates with Adobe Experience Manager

**Key Features:**
- Pluggable architecture
- Incremental processing
- Change detection (checksum-based)
- Error handling and retry logic

### 2. Processing Layer

Handles content transformation and queuing.

**Components:**
- **JMS Queue**: ActiveMQ Artemis message broker
- **Content Processor**: Transforms and enriches content
- **Batch Processor**: Handles bulk operations
- **Scheduling**: Cron-based job scheduling

**Key Features:**
- Asynchronous processing
- Message persistence
- Dead letter queue for failures
- Backpressure handling

### 3. Indexing Layer

Manages indexing to different search engines.

**Components:**
- **Strategy Pattern**: Provider selection
- **Indexing Service**: Core indexing logic
- **Provider Adapters**: Engine-specific implementations
- **Status Tracking**: Indexing history and status

**Key Features:**
- Multi-provider support
- Bulk indexing
- Transactional indexing
- Dependency tracking

### 4. Persistence Layer

Manages application state and metadata.

**Components:**
- **Indexing Repository**: Tracks indexed content
- **Configuration Repository**: Stores connector configs
- **Rule Repository**: Manages indexing rules

**Key Features:**
- JPA/Hibernate
- Multi-database support
- Connection pooling (HikariCP)
- Transaction management

### 5. API Layer

Exposes REST APIs for management and monitoring.

**Components:**
- **Connector API**: Trigger indexing operations
- **Monitoring API**: Track indexing status
- **Configuration API**: Manage settings
- **Health API**: Health checks and metrics

**Key Features:**
- RESTful design
- OpenAPI/Swagger documentation
- Versioning support
- Authentication/Authorization

## Data Flow

### Indexing Flow

1. **Trigger**: User/scheduler triggers indexing
   ```
   GET /api/v2/connector/index/{source}/all
   ```

2. **Connector Execution**: 
   - Connector fetches data from source
   - Validates and transforms content
   - Calculates checksums for change detection

3. **Queue Submission**:
   - Creates `TurSNJobItems` with content
   - Submits to JMS queue (`dumont.indexing.queue`)
   - Returns acknowledgment to caller

4. **Async Processing**:
   - `DumConnectorProcessQueue` receives message
   - Validates content structure
   - Prepares for indexing

5. **Strategy Selection**:
   - `IndexingStrategyResolver` selects provider
   - Based on configuration (SOLR/ES/TURING)

6. **Indexing Execution**:
   - Provider-specific strategy indexes content
   - Updates indexing metadata
   - Tracks dependencies

7. **Status Update**:
   - Updates `DumConnectorIndexingModel`
   - Sets status (INDEXED/FAILED/IGNORED)
   - Logs metrics

### Incremental Indexing Flow

1. **Change Detection**:
   ```java
   boolean changed = service.isChecksumDifferent(jobItem);
   ```

2. **Decision**:
   - If changed → Update index
   - If unchanged → Skip (status: IGNORED)
   - If new → Add to index

3. **Dependency Tracking**:
   ```java
   List<String> dependents = service.findByDependencies(
       source, provider, referenceIds
   );
   ```

4. **Cascade Update**:
   - Identifies dependent content
   - Triggers re-indexing of dependents

## Design Patterns

### 1. Strategy Pattern

Used for indexing provider selection.

```java
public interface IndexingStrategy {
    void process(TurSNJobItems items);
    boolean supports(String provider);
}

@Component
public class SolrIndexingStrategy implements IndexingStrategy {
    // Solr-specific implementation
}
```

### 2. Repository Pattern

Abstracts data access.

```java
public interface DumConnectorIndexingRepository 
    extends JpaRepository<DumConnectorIndexingModel, Integer> {
    // Custom query methods
}
```

### 3. Builder Pattern

For object construction (via Lombok).

```java
@Builder
public class DumConnectorIndexingModel {
    // Fields
}

// Usage
DumConnectorIndexingModel model = DumConnectorIndexingModel.builder()
    .objectId("123")
    .source("mySource")
    .build();
```

### 4. Observer Pattern (Future)

For event-driven notifications.

```java
public interface IndexingEventListener {
    void onIndexingComplete(IndexingEvent event);
    void onIndexingError(IndexingEvent event);
}
```

### 5. Facade Pattern (Proposed)

Simplify complex subsystem interactions.

```java
@Service
public class DumConnectorFacade {
    public IndexingResult indexContent(IndexingRequest request) {
        // Orchestrate multiple services
    }
}
```

## Scalability

### Horizontal Scaling

Dumont can be scaled horizontally:

1. **Multiple Instances**:
   ```yaml
   # Kubernetes deployment with replicas
   replicas: 3
   ```

2. **Load Balancing**:
   - API requests distributed across instances
   - JMS consumers compete for messages

3. **Database Sharding** (Future):
   - Partition by provider or source
   - Reduces contention

### Vertical Scaling

Optimize resource usage:

1. **Thread Pools**:
   ```yaml
   spring.task.execution.pool:
     core-size: 10
     max-size: 50
   ```

2. **Connection Pooling**:
   ```yaml
   spring.datasource.hikari:
     maximum-pool-size: 20
   ```

3. **JVM Tuning**:
   ```bash
   -Xms2g -Xmx4g -XX:+UseG1GC
   ```

### Performance Optimizations

1. **Batch Processing**:
   - Process items in batches of 100
   - Reduces database round-trips

2. **Caching**:
   - Cache indexing status
   - Cache connector configurations
   - TTL-based expiration

3. **Async Operations**:
   - Non-blocking I/O
   - CompletableFuture for async ops

## Technology Stack

### Core Technologies

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Java | 21 |
| Framework | Spring Boot | 4.0.1 |
| Build Tool | Maven | 3.8+ |
| ORM | Hibernate | 7.x |

### Data Storage

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Database | PostgreSQL | Primary storage |
| Cache | Caffeine | In-memory caching |
| Message Queue | ActiveMQ Artemis | Async processing |

### Search Engines

| Engine | Version | Protocol |
|--------|---------|----------|
| Apache Solr | 9.x | HTTP/REST |
| Elasticsearch | 8.x | HTTP/REST |
| Viglet Turing | Latest | HTTP/REST |

### Observability

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Metrics | Micrometer | Application metrics |
| Logging | SLF4J + Logback | Structured logging |
| Tracing | OpenTelemetry | Distributed tracing |
| Monitoring | Prometheus + Grafana | Dashboards |

### Development Tools

| Tool | Purpose |
|------|---------|
| Lombok | Reduce boilerplate |
| MapStruct | DTO mapping (future) |
| JUnit 5 | Unit testing |
| Mockito | Mocking framework |
| Testcontainers | Integration tests |

## Security Architecture

### Authentication

- Spring Security
- OAuth2/JWT support
- API key authentication

### Authorization

- Role-based access control (RBAC)
- Method-level security with `@PreAuthorize`

### Data Protection

- Encrypted connections (TLS)
- Secrets management
- Input validation and sanitization

## Deployment Architecture

### Development Environment

```
Developer Machine
├── Docker Compose
│   ├── Dumont Connector
│   ├── PostgreSQL
│   ├── ActiveMQ
│   └── Solr
```

### Production Environment

```
Kubernetes Cluster
├── Dumont Pods (3 replicas)
├── PostgreSQL StatefulSet
├── ActiveMQ StatefulSet
├── Elasticsearch Cluster
├── Ingress (Load Balancer)
└── Persistent Volumes
```

## Monitoring and Health

### Health Checks

- **Liveness**: `/actuator/health/liveness`
- **Readiness**: `/actuator/health/readiness`
- **Custom**: Database, JMS, Search Engine

### Metrics

- **Application**: Indexing rate, queue size
- **JVM**: Memory, GC, threads
- **Database**: Connection pool, query time
- **HTTP**: Request rate, response time

### Alerting

- High error rate
- Queue backlog
- System resource exhaustion
- Search engine unavailability

## Future Enhancements

See [IMPROVEMENTS.md](../IMPROVEMENTS.md) for detailed roadmap:

1. **Phase 1**: Foundation (Documentation, Tests, CI/CD)
2. **Phase 2**: Architecture (Design Patterns, Refactoring)
3. **Phase 3**: Scalability (Async, Caching, K8s)
4. **Phase 4**: Observability (Metrics, Tracing, Dashboards)
5. **Phase 5**: Security (Auth, Audit, Encryption)
6. **Phase 6**: Community (Examples, Tutorials, Events)

---

**Version**: 2026.1.4  
**Last Updated**: 2026-01-04  
**Authors**: Viglet Team

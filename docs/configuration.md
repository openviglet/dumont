# Configuration Guide

This guide covers all configuration options for Viglet Dumont.

## Table of Contents

1. [Application Configuration](#application-configuration)
2. [Database Configuration](#database-configuration)
3. [Message Queue Configuration](#message-queue-configuration)
4. [Indexing Providers](#indexing-providers)
5. [Connector Specific](#connector-specific)
6. [Security Configuration](#security-configuration)
7. [Performance Tuning](#performance-tuning)
8. [Monitoring and Observability](#monitoring-and-observability)

## Application Configuration

### Basic Configuration

```yaml
spring:
  application:
    name: dumont-connector
  
  profiles:
    active: prod

server:
  port: 8080
  compression:
    enabled: true
  
  # Graceful shutdown
  shutdown: graceful
```

### Logging Configuration

```yaml
logging:
  level:
    root: INFO
    com.viglet.dumont: DEBUG
    org.springframework: WARN
  
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  
  file:
    name: logs/dumont.log
    max-size: 10MB
    max-history: 30
```

## Database Configuration

### PostgreSQL (Recommended)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/dumont
    username: dumont
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      connection-test-query: SELECT 1
      pool-name: DumontHikariPool
  
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        jdbc:
          batch_size: 20
        order_inserts: true
        order_updates: true
        format_sql: true
        use_sql_comments: false
```

### MySQL

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/dumont?useSSL=false&serverTimezone=UTC
    username: dumont
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  
  jpa:
    database-platform: org.hibernate.dialect.MySQLDialect
    hibernate:
      ddl-auto: update
```

### H2 (Development Only)

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:dumont
    driver-class-name: org.h2.Driver
  
  h2:
    console:
      enabled: true
      path: /h2-console
  
  jpa:
    hibernate:
      ddl-auto: create-drop
```

## Message Queue Configuration

### ActiveMQ Artemis

```yaml
spring:
  artemis:
    mode: native
    broker-url: tcp://localhost:61616
    user: admin
    password: ${ARTEMIS_PASSWORD}
    
  jms:
    template:
      default-destination: dumont.indexing.queue
      delivery-mode: persistent
      priority: 5
      time-to-live: 3600000
```

### Apache ActiveMQ Classic

```yaml
spring:
  activemq:
    broker-url: tcp://localhost:61616
    user: admin
    password: ${ACTIVEMQ_PASSWORD}
    pool:
      enabled: true
      max-connections: 10
```

### Embedded Broker (Development)

```yaml
spring:
  artemis:
    mode: embedded
    embedded:
      enabled: true
      persistent: false
```

## Indexing Providers

### Apache Solr

```yaml
dumont:
  indexing:
    provider: SOLR

solr:
  url: http://localhost:8983/solr
  collection: dumont
  commit-within: 1000
  connection-timeout: 5000
  socket-timeout: 10000
  max-connections: 100
  max-connections-per-host: 20
```

### Elasticsearch

```yaml
dumont:
  indexing:
    provider: ELASTICSEARCH

elasticsearch:
  url: http://localhost:9200
  index: dumont
  username: elastic
  password: ${ES_PASSWORD}
  connection-timeout: 5000
  socket-timeout: 30000
  max-retry-timeout: 60000
```

### Viglet Turing

```yaml
dumont:
  indexing:
    provider: TURING

turing:
  url: http://localhost:2700
  api-key: ${TURING_API_KEY}
  site: default
  connection-timeout: 5000
```

## Connector Specific

### Filesystem Connector

```yaml
dumont:
  filesystem:
    enabled: true
    paths:
      - /data/documents
      - /mnt/shared
    
    include-patterns:
      - "*.pdf"
      - "*.docx"
      - "*.txt"
      - "*.html"
    
    exclude-patterns:
      - "*.tmp"
      - ".*"
      - "~*"
    
    max-file-size: 10485760  # 10MB
    batch-size: 100
    parallel-threads: 4
    
    # File watching for real-time updates
    watch:
      enabled: true
      poll-interval: 5000
    
    # Content extraction
    extraction:
      pdf:
        enabled: true
        ocr: false
      office:
        enabled: true
```

### Database Connector

```yaml
dumont:
  database:
    enabled: true
    connections:
      - name: main-db
        url: jdbc:mysql://localhost:3306/myapp
        username: reader
        password: ${READER_PASSWORD}
        driver: com.mysql.cj.jdbc.Driver
        
        # Indexing configuration
        query: |
          SELECT 
            id, 
            title, 
            content, 
            author,
            created_at,
            updated_at 
          FROM articles 
          WHERE published = true
        
        id-column: id
        checksum-columns:
          - content
          - updated_at
        
        # Incremental indexing
        incremental:
          enabled: true
          timestamp-column: updated_at
          last-run-tracking: true
        
        # Batch processing
        fetch-size: 1000
        batch-size: 100
```

### Web Crawler

```yaml
dumont:
  webcrawler:
    enabled: true
    
    # Seed URLs
    seeds:
      - https://example.com
      - https://docs.example.com
    
    # Crawl limits
    max-depth: 5
    max-pages: 10000
    max-pages-per-domain: 5000
    
    # Politeness
    politeness-delay: 1000  # ms
    respect-robots-txt: true
    user-agent: "DumontBot/1.0"
    
    # URL filtering
    include-url-patterns:
      - "^https://example\\.com/docs/.*"
    exclude-url-patterns:
      - ".*/admin/.*"
      - ".*/login.*"
    
    # Content extraction
    extract-metadata: true
    follow-redirects: true
    max-redirect-hops: 3
```

### AEM Connector

```yaml
dumont:
  aem:
    enabled: true
    
    # AEM instance
    host: http://localhost:4502
    username: admin
    password: ${AEM_PASSWORD}
    
    # Content paths
    content-paths:
      - /content/mysite
      - /content/dam
    
    # Replication filter
    replicate-only-published: true
    
    # Asset handling
    include-assets: true
    asset-renditions:
      - original
      - web
```

## Security Configuration

### Basic Authentication

```yaml
spring:
  security:
    user:
      name: admin
      password: ${ADMIN_PASSWORD}
```

### OAuth2 / JWT

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.example.com
          jwk-set-uri: https://auth.example.com/.well-known/jwks.json

dumont:
  security:
    jwt:
      secret: ${JWT_SECRET}
      expiration: 86400000  # 24 hours
```

### API Key Authentication

```yaml
dumont:
  security:
    api-key:
      enabled: true
      header-name: X-API-Key
      keys:
        - name: service1
          key: ${SERVICE1_API_KEY}
        - name: service2
          key: ${SERVICE2_API_KEY}
```

## Performance Tuning

### Thread Pool Configuration

```yaml
spring:
  task:
    execution:
      pool:
        core-size: 10
        max-size: 50
        queue-capacity: 100
        keep-alive: 60s
      thread-name-prefix: dumont-exec-
    
    scheduling:
      pool:
        size: 5
      thread-name-prefix: dumont-sched-
```

### Caching Configuration

```yaml
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=1000,expireAfterWrite=10m

dumont:
  cache:
    indexing-status:
      max-size: 10000
      expire-after-write: 5m
    connector-config:
      max-size: 100
      expire-after-write: 30m
```

### JVM Options

```bash
JAVA_OPTS="-Xms1g -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UseStringDeduplication \
  -XX:+OptimizeStringConcat \
  -Djava.security.egd=file:/dev/./urandom"
```

## Monitoring and Observability

### Actuator Configuration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,loggers
      base-path: /actuator
  
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
    
    metrics:
      enabled: true
  
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
      environment: ${spring.profiles.active}
```

### Custom Metrics

```yaml
dumont:
  metrics:
    enabled: true
    export-interval: 60s
    
    # Custom metric tags
    tags:
      datacenter: us-east-1
      version: ${project.version}
```

### Tracing Configuration

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans

spring:
  sleuth:
    sampler:
      probability: 1.0
```

## Environment-Specific Profiles

### Development Profile

`application-dev.yml`:
```yaml
spring:
  jpa:
    show-sql: true
  
logging:
  level:
    com.viglet.dumont: DEBUG
```

### Production Profile

`application-prod.yml`:
```yaml
spring:
  jpa:
    show-sql: false

logging:
  level:
    com.viglet.dumont: INFO

dumont:
  security:
    enabled: true
```

## Configuration via Environment Variables

All properties can be overridden with environment variables:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://prod-db:5432/dumont
export SPRING_DATASOURCE_PASSWORD=secret
export DUMONT_INDEXING_PROVIDER=SOLR
export SOLR_URL=http://solr-cluster:8983/solr
```

## Configuration Validation

Dumont validates configuration at startup. Check logs for:

```
INFO  c.v.d.c.DumConnectorApplication - Starting DumConnectorApplication
INFO  c.v.d.c.DumConnectorApplication - The following profiles are active: prod
INFO  c.v.d.c.config.DumontConfigValidator - Configuration validated successfully
```

## External Configuration

Load configuration from external file:

```bash
java -jar dumont.jar --spring.config.location=/etc/dumont/application.yml
```

Or use config server:

```yaml
spring:
  cloud:
    config:
      uri: http://config-server:8888
      name: dumont-connector
      profile: prod
```

---

Last updated: 2026-01-04

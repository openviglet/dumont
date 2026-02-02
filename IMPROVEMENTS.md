# Plano de Melhorias para o Projeto Dumont

## Sumário Executivo

Este documento apresenta melhorias para tornar o projeto **Viglet Dumont** mais escalável, seguir melhores práticas de desenvolvimento e aumentar sua adoção pela comunidade open source.

## Índice

1. [Melhorias de Arquitetura](#1-melhorias-de-arquitetura)
2. [Design Patterns Aplicáveis](#2-design-patterns-aplicáveis)
3. [Melhorias de Escalabilidade](#3-melhorias-de-escalabilidade)
4. [Qualidade de Código e Melhores Práticas](#4-qualidade-de-código-e-melhores-práticas)
5. [Documentação e Comunidade](#5-documentação-e-comunidade)
6. [DevOps e CI/CD](#6-devops-e-cicd)
7. [Segurança](#7-segurança)
8. [Observabilidade e Monitoramento](#8-observabilidade-e-monitoramento)
9. [Roadmap de Implementação](#9-roadmap-de-implementação)

---

## 1. Melhorias de Arquitetura

### 1.1 Separação de Responsabilidades

**Problema Atual**: Algumas classes têm múltiplas responsabilidades (ex: `DumConnectorApi` gerencia tanto validação quanto indexação).

**Solução Proposta**:
```
connector-app/
├── api/
│   ├── validation/
│   │   └── DumConnectorValidationApi.java
│   ├── indexing/
│   │   └── DumConnectorIndexingApi.java
│   └── monitoring/
│       └── DumConnectorMonitoringApi.java
```

### 1.2 Camada de Abstração para Providers

**Problema Atual**: Lógica específica de providers (Solr, Elasticsearch, Turing) está dispersa.

**Solução Proposta**: Criar uma interface comum para todos os providers de indexação.

```java
// Nova estrutura
public interface IndexingProvider {
    String getProviderName();
    void index(TurSNJobItems items);
    void deleteById(String id);
    boolean healthCheck();
}

@Component
public class IndexingProviderFactory {
    public IndexingProvider getProvider(String providerType) {
        // Factory Pattern implementation
    }
}
```

### 1.3 Modularização Melhorada

**Estrutura Proposta**:
```
dumont/
├── dumont-api/           # API Gateway
├── dumont-core/          # Core business logic
├── dumont-providers/     # Indexing providers
│   ├── solr-provider/
│   ├── elasticsearch-provider/
│   └── turing-provider/
├── dumont-connectors/    # Data connectors
│   ├── filesystem-connector/
│   ├── database-connector/
│   ├── aem-connector/
│   └── webcrawler-connector/
└── dumont-commons/       # Shared utilities
```

---

## 2. Design Patterns Aplicáveis

### 2.1 Strategy Pattern (Alta Prioridade)

**Aplicação**: `DumConnectorProcessQueue.java`

**Código Atual**:
```java
@JmsListener(destination = CONNECTOR_INDEXING_QUEUE)
public void receiveAndSendToDumont(TurSNJobItems turSNJobItems) {
    // Lógica de processamento
    indexingPlugin.index(turSNJobItems);
}
```

**Código Proposto**:
```java
public interface IndexingStrategy {
    void process(TurSNJobItems items);
    boolean supports(String provider);
}

@Component
public class SolrIndexingStrategy implements IndexingStrategy {
    @Override
    public void process(TurSNJobItems items) {
        // Solr specific logic
    }
    
    @Override
    public boolean supports(String provider) {
        return "SOLR".equalsIgnoreCase(provider);
    }
}

@Component
public class IndexingStrategyResolver {
    private final List<IndexingStrategy> strategies;
    
    public IndexingStrategy resolve(String provider) {
        return strategies.stream()
            .filter(s -> s.supports(provider))
            .findFirst()
            .orElseThrow(() -> new UnsupportedProviderException(provider));
    }
}
```

### 2.2 Builder Pattern (Aplicação Existente)

**Melhorar**: `DumConnectorIndexingModel`

O projeto já usa Lombok `@Builder`, mas pode ser melhorado com validação:

```java
@Entity
@Data
@Builder
public class DumConnectorIndexingModel {
    // ... fields ...
    
    public static class DumConnectorIndexingModelBuilder {
        public DumConnectorIndexingModel build() {
            validateRequiredFields();
            return new DumConnectorIndexingModel(/* ... */);
        }
        
        private void validateRequiredFields() {
            if (objectId == null || source == null) {
                throw new IllegalStateException("Required fields missing");
            }
        }
    }
}
```

### 2.3 Observer Pattern

**Aplicação**: Sistema de notificações de indexação

**Código Proposto**:
```java
public interface IndexingEventListener {
    void onIndexingStart(IndexingEvent event);
    void onIndexingComplete(IndexingEvent event);
    void onIndexingError(IndexingEvent event);
}

@Component
public class IndexingEventPublisher {
    private final List<IndexingEventListener> listeners;
    
    public void publishIndexingComplete(String objectId, String source) {
        IndexingEvent event = IndexingEvent.builder()
            .objectId(objectId)
            .source(source)
            .timestamp(Instant.now())
            .build();
        listeners.forEach(l -> l.onIndexingComplete(event));
    }
}

@Component
public class MetricsIndexingListener implements IndexingEventListener {
    @Override
    public void onIndexingComplete(IndexingEvent event) {
        // Update metrics
    }
}
```

### 2.4 Repository Pattern (Melhorar Existente)

**Aplicação**: `DumConnectorIndexingRepository`

**Adicionar**: Specification Pattern para queries complexas

```java
public class DumConnectorIndexingSpecifications {
    
    public static Specification<DumConnectorIndexingModel> hasSource(String source) {
        return (root, query, cb) -> cb.equal(root.get("source"), source);
    }
    
    public static Specification<DumConnectorIndexingModel> hasProvider(String provider) {
        return (root, query, cb) -> cb.equal(root.get("provider"), provider);
    }
    
    public static Specification<DumConnectorIndexingModel> modifiedAfter(Date date) {
        return (root, query, cb) -> 
            cb.greaterThan(root.get("modificationDate"), date);
    }
}

// Uso
repository.findAll(
    hasSource("mySource")
        .and(hasProvider("SOLR"))
        .and(modifiedAfter(yesterday))
);
```

### 2.5 Chain of Responsibility Pattern

**Aplicação**: Pipeline de processamento de documentos

**Código Proposto**:
```java
public abstract class DocumentProcessor {
    protected DocumentProcessor next;
    
    public void setNext(DocumentProcessor processor) {
        this.next = processor;
    }
    
    public abstract void process(TurSNJobItem item);
    
    protected void processNext(TurSNJobItem item) {
        if (next != null) {
            next.process(item);
        }
    }
}

@Component
public class ValidationProcessor extends DocumentProcessor {
    @Override
    public void process(TurSNJobItem item) {
        // Validate document
        if (isValid(item)) {
            processNext(item);
        }
    }
}

@Component
public class EnrichmentProcessor extends DocumentProcessor {
    @Override
    public void process(TurSNJobItem item) {
        // Enrich document
        enrichDocument(item);
        processNext(item);
    }
}

@Component
public class IndexingProcessor extends DocumentProcessor {
    @Override
    public void process(TurSNJobItem item) {
        // Index document
        indexDocument(item);
        processNext(item);
    }
}
```

### 2.6 Facade Pattern

**Aplicação**: Simplificar API complexa do connector

**Código Proposto**:
```java
@Service
public class DumConnectorFacade {
    private final DumConnectorIndexingService indexingService;
    private final DumConnectorSolrService solrService;
    private final DumConnectorPlugin plugin;
    private final DumConnectorValidationService validationService;
    
    public IndexingResult indexContent(IndexingRequest request) {
        // Simplified API that orchestrates multiple services
        validationService.validate(request);
        List<String> contentIds = request.getContentIds();
        plugin.indexById(request.getSource(), contentIds);
        return indexingService.getIndexingStatus(contentIds);
    }
    
    public ValidationResult validateAndIndex(IndexingRequest request) {
        ValidationResult validation = validationService.validateSource(request.getSource());
        if (validation.isValid()) {
            return IndexingResult.fromValidation(indexContent(request));
        }
        return IndexingResult.failed(validation.getErrors());
    }
}
```

### 2.7 Template Method Pattern

**Aplicação**: Base para todos os conectores

**Código Proposto**:
```java
public abstract class AbstractConnector {
    
    public final void execute(ConnectorContext context) {
        try {
            initialize(context);
            validateConfiguration();
            List<Document> documents = fetchDocuments();
            List<Document> processed = processDocuments(documents);
            indexDocuments(processed);
            cleanup();
        } catch (Exception e) {
            handleError(e);
        }
    }
    
    protected abstract void initialize(ConnectorContext context);
    protected abstract List<Document> fetchDocuments();
    protected abstract List<Document> processDocuments(List<Document> docs);
    
    // Hook methods with default implementations
    protected void validateConfiguration() {
        // Default validation
    }
    
    protected void cleanup() {
        // Default cleanup
    }
    
    private void indexDocuments(List<Document> documents) {
        // Common indexing logic
    }
}

@Component
public class FileSystemConnector extends AbstractConnector {
    @Override
    protected void initialize(ConnectorContext context) {
        // FS-specific initialization
    }
    
    @Override
    protected List<Document> fetchDocuments() {
        // Fetch files from filesystem
    }
    
    @Override
    protected List<Document> processDocuments(List<Document> docs) {
        // Process file metadata
    }
}
```

---

## 3. Melhorias de Escalabilidade

### 3.1 Processamento Assíncrono Aprimorado

**Implementar**: Configuração de pools de threads customizável

```java
@Configuration
@EnableAsync
public class AsyncConfiguration implements AsyncConfigurer {
    
    @Override
    @Bean(name = "indexingExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("indexing-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}

@Service
public class AsyncIndexingService {
    
    @Async("indexingExecutor")
    public CompletableFuture<IndexingResult> indexAsync(TurSNJobItems items) {
        // Async indexing logic
        return CompletableFuture.completedFuture(result);
    }
}
```

### 3.2 Caching Strategy

**Implementar**: Cache em múltiplos níveis

```java
@Configuration
@EnableCaching
public class CacheConfiguration {
    
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            "indexingStatus", 
            "connectorConfig", 
            "providerMetadata"
        );
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .recordStats());
        return cacheManager;
    }
}

@Service
public class DumConnectorIndexingService {
    
    @Cacheable(value = "indexingStatus", key = "#objectId")
    public IndexingStatus getIndexingStatus(String objectId) {
        // Expensive operation cached
    }
    
    @CacheEvict(value = "indexingStatus", key = "#objectId")
    public void updateIndexingStatus(String objectId, IndexingStatus status) {
        // Update and evict cache
    }
}
```

### 3.3 Batching e Bulk Operations

**Melhorar**: Processamento em lote

```java
@Service
public class BulkIndexingService {
    private static final int BATCH_SIZE = 100;
    
    public void bulkIndex(List<TurSNJobItem> items) {
        Lists.partition(items, BATCH_SIZE)
            .parallelStream()
            .forEach(batch -> processBatch(batch));
    }
    
    private void processBatch(List<TurSNJobItem> batch) {
        // Process batch efficiently
        indexingService.saveAll(batch);
    }
}
```

### 3.4 Database Connection Pooling

**Adicionar**: Configuração otimizada do HikariCP

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      connection-test-query: SELECT 1
      pool-name: DumontHikariPool
```

### 3.5 Particionamento de Dados

**Implementar**: Particionamento por provider e data

```java
@Entity
@Table(name = "dum_connector_indexing")
@Data
public class DumConnectorIndexingModel {
    
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Integer id;
    
    private String provider;
    
    @Column(name = "created_date")
    @Temporal(TemporalType.DATE)
    private Date createdDate;
    
    // Suggestion: Add database partitioning by provider and created_date
    // Postgres: PARTITION BY RANGE (created_date)
    // MySQL: PARTITION BY HASH(provider_id) PARTITIONS 4
}
```

### 3.6 Rate Limiting

**Implementar**: Controle de taxa de indexação

```java
@Component
public class RateLimitingInterceptor implements HandlerInterceptor {
    private final RateLimiter rateLimiter = RateLimiter.create(100.0); // 100 req/sec
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                            HttpServletResponse response, 
                            Object handler) {
        if (rateLimiter.tryAcquire()) {
            return true;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        return false;
    }
}
```

---

## 4. Qualidade de Código e Melhores Práticas

### 4.1 Cobertura de Testes

**Problema**: Apenas 6 testes encontrados no projeto.

**Solução**:

```
Estrutura de testes proposta:
src/test/java/
├── unit/                    # Testes unitários
│   ├── service/
│   ├── api/
│   └── util/
├── integration/             # Testes de integração
│   ├── repository/
│   └── api/
└── performance/             # Testes de performance
    └── load/
```

**Exemplo de teste unitário**:
```java
@ExtendWith(MockitoExtension.class)
class DumConnectorIndexingServiceTest {
    
    @Mock
    private DumConnectorIndexingRepository repository;
    
    @InjectMocks
    private DumConnectorIndexingService service;
    
    @Test
    @DisplayName("Should save indexing model successfully")
    void testSaveIndexingModel() {
        // Given
        DumJobItemWithSession jobItem = createTestJobItem();
        
        // When
        service.save(jobItem, DumIndexingStatus.INDEXED);
        
        // Then
        verify(repository, times(1)).save(any());
    }
}
```

**Exemplo de teste de integração**:
```java
@SpringBootTest
@AutoConfigureMockMvc
class DumConnectorApiIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void testIndexAllEndpoint() throws Exception {
        mockMvc.perform(get("/api/v2/connector/index/test-source/all"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("sent"));
    }
}
```

### 4.2 Validação de Entrada

**Implementar**: Bean Validation (JSR-380)

```java
@RestController
@RequestMapping("/api/v2/connector")
@Validated
public class DumConnectorApi {
    
    @PostMapping("index/{name}")
    public ResponseEntity<Map<String, String>> indexContentId(
            @PathVariable @NotBlank @Size(min = 1, max = 100) String name,
            @RequestBody @Valid @NotEmpty List<@NotBlank String> contentId) {
        plugin.indexById(name, contentId);
        return ResponseEntity.ok(statusSent());
    }
}

@Data
@Builder
public class IndexingRequest {
    @NotBlank(message = "Source cannot be blank")
    @Size(min = 1, max = 255)
    private String source;
    
    @NotEmpty(message = "Content IDs cannot be empty")
    private List<@NotBlank String> contentIds;
    
    @Pattern(regexp = "SOLR|ELASTICSEARCH|TURING")
    private String provider;
}
```

### 4.3 Exception Handling Global

**Implementar**: Tratamento centralizado de exceções

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex) {
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.NOT_FOUND.value())
            .message(ex.getMessage())
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.toList());
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.BAD_REQUEST.value())
            .message("Validation failed")
            .errors(errors)
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.badRequest().body(error);
    }
}
```

### 4.4 Logging Estruturado

**Melhorar**: Usar logging estruturado com contexto

```java
@Slf4j
@Component
public class DumConnectorProcessQueue {
    
    @JmsListener(destination = CONNECTOR_INDEXING_QUEUE)
    public void receiveAndSendToDumont(TurSNJobItems turSNJobItems) {
        MDC.put("provider", indexingPlugin.getProviderName());
        MDC.put("jobId", turSNJobItems.getJobId());
        
        log.info("Processing indexing job", 
            kv("itemCount", turSNJobItems.size()),
            kv("provider", indexingPlugin.getProviderName()));
        
        try {
            // Processing logic
            log.info("Job processed successfully");
        } catch (Exception e) {
            log.error("Error processing job", e);
        } finally {
            MDC.clear();
        }
    }
}
```

### 4.5 Code Quality Tools

**Adicionar ao pom.xml**:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-checkstyle-plugin</artifactId>
    <version>3.3.1</version>
    <configuration>
        <configLocation>checkstyle.xml</configLocation>
    </configuration>
</plugin>

<plugin>
    <groupId>com.github.spotbugs</groupId>
    <artifactId>spotbugs-maven-plugin</artifactId>
    <version>4.8.3.0</version>
</plugin>

<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <configuration>
        <rules>
            <rule>
                <element>PACKAGE</element>
                <limits>
                    <limit>
                        <counter>LINE</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.80</minimum>
                    </limit>
                </limits>
            </rule>
        </rules>
    </configuration>
</plugin>
```

### 4.6 API Versioning

**Melhorar**: Estratégia consistente de versionamento

```java
@RestController
@RequestMapping("/api/v2/connector")
public class DumConnectorApiV2 {
    // Current implementation
}

@RestController
@RequestMapping("/api/v3/connector")
public class DumConnectorApiV3 {
    // Future improvements with breaking changes
}

// Configuration
@Configuration
public class ApiVersioningConfig {
    @Bean
    public WebMvcConfigurer versioningConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new ApiVersionInterceptor());
            }
        };
    }
}
```

---

## 5. Documentação e Comunidade

### 5.1 README.md Principal

**Criar**: `/README.md` completo

```markdown
# Viglet Dumont - Enterprise Search Connector

[![Build Status](https://github.com/openviglet/dumont/workflows/Java%20CI/badge.svg)](https://github.com/openviglet/dumont/actions)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/com.viglet.dumont/dumont.svg)](https://search.maven.org/artifact/com.viglet.dumont/dumont)

## Overview

Viglet Dumont is a powerful data extraction and indexing platform that connects various data sources to search engines like Solr, Elasticsearch, and Turing.

## Features

- 🔌 Multiple connectors (Filesystem, Database, AEM, Web Crawler)
- 🔍 Support for Solr, Elasticsearch, and Turing
- 📊 Real-time monitoring and validation
- 🚀 Scalable architecture with async processing
- 🔄 Incremental indexing with change detection

## Quick Start

### Prerequisites
- Java 21+
- Maven 3.8+
- Database (PostgreSQL/MySQL)

### Installation

\`\`\`bash
git clone https://github.com/openviglet/dumont.git
cd dumont
mvn clean install
\`\`\`

### Running

\`\`\`bash
cd connector/connector-app
mvn spring-boot:run
\`\`\`

## Documentation

- [Getting Started Guide](docs/getting-started.md)
- [Configuration Guide](docs/configuration.md)
- [API Documentation](docs/api.md)
- [Architecture Overview](docs/architecture.md)
- [Contributing Guidelines](CONTRIBUTING.md)

## Community

- GitHub Issues: [Report bugs or request features](https://github.com/openviglet/dumont/issues)
- Discussions: [Join the conversation](https://github.com/openviglet/dumont/discussions)

## License

Apache License 2.0 - see [LICENSE](LICENSE) for details.
```

### 5.2 CONTRIBUTING.md

**Criar**: Guia de contribuição

```markdown
# Contributing to Viglet Dumont

Thank you for your interest in contributing!

## Development Setup

1. Fork the repository
2. Clone your fork: \`git clone https://github.com/YOUR-USERNAME/dumont.git\`
3. Create a branch: \`git checkout -b feature/my-feature\`
4. Make your changes
5. Run tests: \`mvn test\`
6. Commit: \`git commit -m "Add: my feature"\`
7. Push: \`git push origin feature/my-feature\`
8. Create a Pull Request

## Code Style

- Follow Java coding conventions
- Use Lombok annotations appropriately
- Write unit tests for new features
- Update documentation

## Commit Message Format

\`\`\`
<type>: <subject>

<body>
\`\`\`

Types: feat, fix, docs, style, refactor, test, chore

## Pull Request Process

1. Update the README.md with details of changes
2. Ensure all tests pass
3. Update documentation
4. Get approval from maintainers
```

### 5.3 Documentação de API

**Melhorar**: OpenAPI/Swagger

```java
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Viglet Dumont Connector API",
        version = "2026.1.4",
        description = "Data extraction and indexing platform API",
        contact = @Contact(
            name = "Viglet",
            url = "https://viglet.com",
            email = "support@viglet.com"
        ),
        license = @License(
            name = "Apache 2.0",
            url = "https://www.apache.org/licenses/LICENSE-2.0"
        )
    ),
    servers = {
        @Server(url = "http://localhost:8080", description = "Development"),
        @Server(url = "https://api.viglet.com", description = "Production")
    }
)
public class OpenApiConfig {
}

@RestController
@RequestMapping("/api/v2/connector")
@Tag(name = "Connector", description = "Connector management endpoints")
public class DumConnectorApi {
    
    @Operation(
        summary = "Index all content",
        description = "Triggers indexing for all content from a specific source"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Indexing triggered successfully"),
        @ApiResponse(responseCode = "404", description = "Source not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("index/{name}/all")
    public ResponseEntity<Map<String, String>> indexAll(
            @Parameter(description = "Source name", required = true)
            @PathVariable String name) {
        plugin.indexAll(name);
        return ResponseEntity.ok(statusSent());
    }
}
```

### 5.4 Arquitetura e Diagramas

**Criar**: `/docs/architecture.md`

```markdown
# Dumont Architecture

## System Overview

\`\`\`
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
\`\`\`

## Components

### Connectors
- Filesystem Connector
- Database Connector
- AEM Connector
- Web Crawler

### Processing
- Message Queue (JMS)
- Async Processing
- Batch Processing

### Storage
- Indexing metadata (JPA/Hibernate)
- Document tracking
- Dependency management
```

### 5.5 Exemplos e Tutoriais

**Criar**: `/docs/examples/`

```
examples/
├── basic-filesystem-connector/
│   ├── README.md
│   ├── application.yml
│   └── docker-compose.yml
├── database-connector/
│   ├── README.md
│   └── sample-config.yml
└── custom-plugin/
    ├── README.md
    └── src/
```

---

## 6. DevOps e CI/CD

### 6.1 Docker Support

**Criar**: `/Dockerfile`

```dockerfile
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="Viglet <support@viglet.com>"
LABEL description="Viglet Dumont Connector"

WORKDIR /app

COPY connector/connector-app/target/dumont-connector.jar /app/dumont.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/dumont.jar"]
```

**Criar**: `/docker-compose.yml`

```yaml
version: '3.8'

services:
  dumont:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/dumont
      - SPRING_DATASOURCE_USERNAME=dumont
      - SPRING_DATASOURCE_PASSWORD=dumont123
    depends_on:
      - db
      - activemq
    networks:
      - dumont-network

  db:
    image: postgres:16-alpine
    environment:
      - POSTGRES_DB=dumont
      - POSTGRES_USER=dumont
      - POSTGRES_PASSWORD=dumont123
    volumes:
      - dumont-db:/var/lib/postgresql/data
    networks:
      - dumont-network

  activemq:
    image: apache/activemq-artemis:2.32.0
    ports:
      - "61616:61616"
      - "8161:8161"
    networks:
      - dumont-network

  solr:
    image: solr:9
    ports:
      - "8983:8983"
    networks:
      - dumont-network

volumes:
  dumont-db:

networks:
  dumont-network:
    driver: bridge
```

### 6.2 Kubernetes Support

**Criar**: `/k8s/deployment.yaml`

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: dumont-connector
  labels:
    app: dumont
spec:
  replicas: 3
  selector:
    matchLabels:
      app: dumont
  template:
    metadata:
      labels:
        app: dumont
    spec:
      containers:
      - name: dumont
        image: viglet/dumont:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "kubernetes"
        - name: SPRING_DATASOURCE_URL
          valueFrom:
            secretKeyRef:
              name: dumont-secrets
              key: database-url
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "2000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: dumont-service
spec:
  selector:
    app: dumont
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
  type: LoadBalancer
```

### 6.3 CI/CD Enhancements

**Melhorar**: `.github/workflows/maven.yml`

```yaml
name: Java CI/CD

on:
  push:
    branches: [ main, 2026.1 ]
  pull_request:
    branches: [ main, 2026.1 ]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 21
      uses: actions/setup-java@v4
      with:
        java-version: '21'
        distribution: 'temurin'
        cache: maven
    
    - name: Build with Maven
      run: mvn -B clean install
    
    - name: Run Tests
      run: mvn -B test
    
    - name: Code Coverage
      run: mvn -B jacoco:report
    
    - name: Upload Coverage to Codecov
      uses: codecov/codecov-action@v3
      with:
        files: ./target/site/jacoco/jacoco.xml
    
    - name: SonarCloud Scan
      env:
        SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
      run: mvn -B sonar:sonar
    
    - name: Build Docker Image
      if: github.ref == 'refs/heads/main'
      run: docker build -t viglet/dumont:${{ github.sha }} .
    
    - name: Push Docker Image
      if: github.ref == 'refs/heads/main'
      run: |
        echo ${{ secrets.DOCKER_PASSWORD }} | docker login -u ${{ secrets.DOCKER_USERNAME }} --password-stdin
        docker push viglet/dumont:${{ github.sha }}
        docker tag viglet/dumont:${{ github.sha }} viglet/dumont:latest
        docker push viglet/dumont:latest
```

### 6.4 Release Automation

**Criar**: `.github/workflows/release.yml`

```yaml
name: Release

on:
  push:
    tags:
      - 'v*'

jobs:
  release:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 21
      uses: actions/setup-java@v4
      with:
        java-version: '21'
        distribution: 'temurin'
    
    - name: Build Release
      run: mvn -B clean package -P release
    
    - name: Create Release
      uses: actions/create-release@v1
      env:
        GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
      with:
        tag_name: ${{ github.ref }}
        release_name: Release ${{ github.ref }}
        draft: false
        prerelease: false
    
    - name: Upload Release Assets
      uses: actions/upload-release-asset@v1
      env:
        GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
      with:
        upload_url: ${{ steps.create_release.outputs.upload_url }}
        asset_path: ./connector/connector-app/target/dumont-connector.jar
        asset_name: dumont-connector.jar
        asset_content_type: application/java-archive
```

---

## 7. Segurança

### 7.1 Authentication & Authorization

**Implementar**: Spring Security

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/v2/connector/**")
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/v2/connector/**").hasRole("CONNECTOR_USER")
                .requestMatchers("/api/v2/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            );
        return http.build();
    }
}

@Service
public class DumConnectorIndexingService {
    
    @PreAuthorize("hasRole('CONNECTOR_USER')")
    public void deleteByProvider(String provider) {
        dumConnectorIndexingRepository.deleteByProvider(provider);
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteAll() {
        dumConnectorIndexingRepository.deleteAll();
    }
}
```

### 7.2 Secrets Management

**Implementar**: Externalizar secrets

```yaml
# application.yml
spring:
  config:
    import: optional:configtree:/run/secrets/

# Docker Compose with secrets
services:
  dumont:
    secrets:
      - db-password
      - api-key
      
secrets:
  db-password:
    file: ./secrets/db_password.txt
  api-key:
    file: ./secrets/api_key.txt
```

```java
@Configuration
@ConfigurationProperties(prefix = "dumont.security")
@Data
public class SecurityProperties {
    private String apiKey;
    private String encryptionKey;
    private boolean enableAuthentication = true;
}
```

### 7.3 Input Sanitization

**Implementar**: Proteção contra injection

```java
@Component
public class InputSanitizer {
    
    public String sanitizeFilePath(String input) {
        // Prevent directory traversal
        return input.replaceAll("\\.\\.", "")
                   .replaceAll("[^a-zA-Z0-9/._-]", "");
    }
    
    public String sanitizeSqlInput(String input) {
        // Basic SQL injection prevention (prefer PreparedStatements)
        return input.replaceAll("['\"\\\\]", "");
    }
}

@RestController
public class DumConnectorApi {
    
    private final InputSanitizer sanitizer;
    
    @GetMapping("index/{name}/all")
    public ResponseEntity<Map<String, String>> indexAll(@PathVariable String name) {
        String sanitized = sanitizer.sanitizeFilePath(name);
        plugin.indexAll(sanitized);
        return ResponseEntity.ok(statusSent());
    }
}
```

### 7.4 Dependency Scanning

**Adicionar**: OWASP Dependency Check

```xml
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>9.0.9</version>
    <configuration>
        <failBuildOnCVSS>7</failBuildOnCVSS>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### 7.5 Audit Logging

**Implementar**: Rastreamento de alterações

```java
@Entity
@EntityListeners(AuditingEntityListener.class)
@Data
public class DumConnectorIndexingModel {
    
    @CreatedBy
    @Column(updatable = false)
    private String createdBy;
    
    @CreatedDate
    @Column(updatable = false)
    private Instant createdDate;
    
    @LastModifiedBy
    private String lastModifiedBy;
    
    @LastModifiedDate
    private Instant lastModifiedDate;
}

@Configuration
@EnableJpaAuditing
public class AuditingConfiguration {
    
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext())
            .map(SecurityContext::getAuthentication)
            .filter(Authentication::isAuthenticated)
            .map(Authentication::getName);
    }
}
```

---

## 8. Observabilidade e Monitoramento

### 8.1 Metrics com Micrometer

**Implementar**: Métricas customizadas

```java
@Service
public class DumConnectorIndexingService {
    private final MeterRegistry meterRegistry;
    private final Counter indexingCounter;
    private final Timer indexingTimer;
    
    public DumConnectorIndexingService(
            DumConnectorIndexingRepository repository,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
        this.indexingCounter = Counter.builder("dumont.indexing.count")
            .description("Number of documents indexed")
            .tag("provider", "all")
            .register(meterRegistry);
        this.indexingTimer = Timer.builder("dumont.indexing.duration")
            .description("Duration of indexing operations")
            .register(meterRegistry);
    }
    
    public void save(DumJobItemWithSession jobItem, DumIndexingStatus status) {
        indexingTimer.record(() -> {
            DumConnectorIndexingModel indexing = createDumConnectorIndexing(jobItem, status);
            if (indexing != null) {
                repository.save(indexing);
                indexingCounter.increment();
                meterRegistry.gauge("dumont.indexing.queue.size", 
                    repository.count());
            }
        });
    }
}
```

### 8.2 Distributed Tracing

**Implementar**: OpenTelemetry/Zipkin

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-zipkin</artifactId>
</dependency>
```

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
```

```java
@Component
public class TracingInterceptor implements HandlerInterceptor {
    private final Tracer tracer;
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                            HttpServletResponse response, 
                            Object handler) {
        Span span = tracer.nextSpan().name("connector-request");
        span.tag("http.method", request.getMethod());
        span.tag("http.path", request.getRequestURI());
        span.start();
        request.setAttribute("tracing.span", span);
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, 
                               HttpServletResponse response, 
                               Object handler, 
                               Exception ex) {
        Span span = (Span) request.getAttribute("tracing.span");
        if (span != null) {
            span.tag("http.status", String.valueOf(response.getStatus()));
            span.end();
        }
    }
}
```

### 8.3 Health Checks

**Melhorar**: Health checks detalhados

```java
@Component
public class IndexingProviderHealthIndicator implements HealthIndicator {
    private final DumIndexingPlugin indexingPlugin;
    
    @Override
    public Health health() {
        try {
            boolean isHealthy = indexingPlugin.healthCheck();
            if (isHealthy) {
                return Health.up()
                    .withDetail("provider", indexingPlugin.getProviderName())
                    .withDetail("status", "Connected")
                    .build();
            } else {
                return Health.down()
                    .withDetail("provider", indexingPlugin.getProviderName())
                    .withDetail("status", "Disconnected")
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}

@Component
public class DatabaseHealthIndicator implements HealthIndicator {
    private final DataSource dataSource;
    
    @Override
    public Health health() {
        try (Connection conn = dataSource.getConnection()) {
            return Health.up()
                .withDetail("database", conn.getMetaData().getDatabaseProductName())
                .withDetail("activeConnections", getActiveConnections())
                .build();
        } catch (SQLException e) {
            return Health.down()
                .withException(e)
                .build();
        }
    }
}
```

### 8.4 Application Monitoring Dashboard

**Configurar**: Grafana Dashboard

```json
{
  "dashboard": {
    "title": "Dumont Connector Monitoring",
    "panels": [
      {
        "title": "Indexing Rate",
        "targets": [
          {
            "expr": "rate(dumont_indexing_count_total[5m])"
          }
        ]
      },
      {
        "title": "Indexing Duration",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, dumont_indexing_duration_seconds_bucket)"
          }
        ]
      },
      {
        "title": "Queue Size",
        "targets": [
          {
            "expr": "dumont_indexing_queue_size"
          }
        ]
      },
      {
        "title": "Error Rate",
        "targets": [
          {
            "expr": "rate(dumont_indexing_errors_total[5m])"
          }
        ]
      }
    ]
  }
}
```

### 8.5 Alerting

**Implementar**: Alertas Prometheus

```yaml
groups:
  - name: dumont_alerts
    rules:
      - alert: HighIndexingErrorRate
        expr: rate(dumont_indexing_errors_total[5m]) > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High indexing error rate"
          description: "Error rate is {{ $value }} errors/second"
      
      - alert: IndexingQueueBacklog
        expr: dumont_indexing_queue_size > 1000
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Large queue backlog"
          description: "Queue size is {{ $value }} items"
      
      - alert: ConnectorDown
        expr: up{job="dumont-connector"} == 0
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Dumont connector is down"
```

---

## 9. Roadmap de Implementação

### Fase 1: Fundação (1-2 meses)

**Prioridade Alta**:
1. ✅ Criar README.md principal
2. ✅ Adicionar CONTRIBUTING.md
3. ✅ Implementar testes unitários básicos (cobertura mínima 60%)
4. ✅ Adicionar Docker support
5. ✅ Configurar CI/CD melhorado
6. ✅ Implementar tratamento global de exceções
7. ✅ Adicionar validação de entrada

**Entregáveis**:
- Documentação básica completa
- Pipeline CI/CD funcional
- Cobertura de testes >60%
- Docker e docker-compose funcionais

### Fase 2: Arquitetura (2-3 meses)

**Prioridade Alta**:
1. ✅ Implementar Strategy Pattern para providers
2. ✅ Adicionar Facade Pattern para API
3. ✅ Implementar Chain of Responsibility para processamento
4. ✅ Melhorar separação de responsabilidades nas APIs
5. ✅ Adicionar caching strategy
6. ✅ Implementar rate limiting

**Entregáveis**:
- Código refatorado com design patterns
- Performance melhorada
- APIs mais limpas e organizadas

### Fase 3: Escalabilidade (2-3 meses)

**Prioridade Média**:
1. ✅ Implementar processamento assíncrono avançado
2. ✅ Adicionar batching e bulk operations
3. ✅ Otimizar connection pooling
4. ✅ Implementar particionamento de dados
5. ✅ Adicionar Kubernetes support
6. ✅ Melhorar configuração de caching

**Entregáveis**:
- Sistema preparado para alta carga
- Deployment em Kubernetes
- Performance otimizada

### Fase 4: Observabilidade (1-2 meses)

**Prioridade Média**:
1. ✅ Implementar métricas customizadas
2. ✅ Adicionar distributed tracing
3. ✅ Melhorar health checks
4. ✅ Configurar dashboards Grafana
5. ✅ Implementar alerting
6. ✅ Adicionar logging estruturado

**Entregáveis**:
- Dashboards de monitoramento
- Sistema de alertas
- Tracing distribuído funcional

### Fase 5: Segurança (1-2 meses)

**Prioridade Alta**:
1. ✅ Implementar autenticação e autorização
2. ✅ Adicionar secrets management
3. ✅ Implementar input sanitization
4. ✅ Adicionar dependency scanning
5. ✅ Implementar audit logging
6. ✅ Realizar security audit

**Entregáveis**:
- Sistema seguro end-to-end
- Audit trail completo
- Certificações de segurança

### Fase 6: Comunidade (Contínuo)

**Prioridade Alta**:
1. ✅ Criar exemplos e tutoriais
2. ✅ Escrever guias de uso
3. ✅ Adicionar API documentation (OpenAPI)
4. ✅ Criar vídeos tutoriais
5. ✅ Engajar com a comunidade
6. ✅ Organizar eventos/webinars

**Entregáveis**:
- Documentação completa e acessível
- Exemplos práticos
- Comunidade ativa

---

## Métricas de Sucesso

### KPIs Técnicos
- ✅ Cobertura de testes > 80%
- ✅ Performance: Indexação de 1000 docs/sec
- ✅ Disponibilidade: 99.9% uptime
- ✅ Tempo de resposta API < 100ms (p95)
- ✅ Zero vulnerabilidades críticas

### KPIs de Comunidade
- ⭐ 500+ stars no GitHub (primeiro ano)
- 🍴 100+ forks
- 💬 Comunidade ativa (>10 contributors)
- 📊 1000+ downloads/mês
- 📝 10+ artigos/tutoriais da comunidade

### KPIs de Qualidade
- 📊 SonarQube: Rating A
- 🐛 Bugs críticos: 0
- 🔒 Security vulnerabilities: 0
- 📖 Documentação: 100% cobertura de APIs
- ⚡ Performance: -50% latência

---

## Conclusão

Este plano de melhorias transforma o Viglet Dumont em um projeto enterprise-ready com:

1. **Arquitetura Escalável**: Design patterns modernos, código limpo e modular
2. **Alta Performance**: Processamento assíncrono, caching inteligente, otimizações
3. **Segurança Robusta**: Autenticação, autorização, audit logging
4. **Observabilidade**: Métricas, tracing, alertas, dashboards
5. **Comunidade Forte**: Documentação completa, exemplos, engajamento

### Próximos Passos Imediatos

1. **Criar branch `feature/improvements`**
2. **Implementar Fase 1 (Fundação)**
3. **Solicitar feedback da comunidade**
4. **Iterar baseado no feedback**
5. **Release v3.0 com melhorias**

### Recursos Necessários

- **Desenvolvimento**: 2-3 desenvolvedores full-time
- **DevOps**: 1 engenheiro DevOps
- **Documentação**: 1 technical writer
- **Comunidade**: 1 community manager

### Timeline Geral

- **Fase 1-2**: 3-4 meses (Q1 2026)
- **Fase 3-4**: 3-4 meses (Q2 2026)
- **Fase 5-6**: 2-3 meses (Q3 2026)
- **Total**: 8-11 meses

---

**Última atualização**: 2026-01-04  
**Versão**: 1.0  
**Autor**: Viglet Team  
**Status**: Proposta Inicial

# Basic Filesystem Connector Example

This example demonstrates how to use the Dumont Filesystem Connector to index files.

## Overview

The filesystem connector scans directories and indexes files into your search engine. It supports:
- Multiple directory paths
- File pattern matching (include/exclude)
- Automatic content extraction
- Incremental indexing with change detection

## Prerequisites

- Dumont Connector running
- Access to filesystem paths to index
- Solr, Elasticsearch, or Turing search engine running

## Configuration

Create `application.yml`:

```yaml
spring:
  application:
    name: dumont-fs-connector
  
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
  
  jpa:
    hibernate:
      ddl-auto: create-drop

dumont:
  filesystem:
    # Base paths to scan
    paths:
      - /data/documents
      - /data/shared
    
    # File patterns to include
    include-patterns:
      - "*.pdf"
      - "*.docx"
      - "*.txt"
      - "*.html"
      - "*.md"
    
    # File patterns to exclude
    exclude-patterns:
      - "*.tmp"
      - "*.bak"
      - ".*"        # Hidden files
      - "~*"        # Backup files
    
    # Indexing configuration
    batch-size: 100
    max-file-size: 10485760  # 10MB
    
    # Source name for tracking
    source-name: filesystem-docs
  
  # Indexing provider
  indexing:
    provider: SOLR

# Solr configuration
solr:
  url: http://localhost:8983/solr
  collection: documents
```

## Directory Structure

```
/data/
├── documents/
│   ├── report-2024.pdf
│   ├── manual.docx
│   └── notes.txt
└── shared/
    ├── presentation.pdf
    └── readme.md
```

## Running

```bash
# Navigate to filesystem connector
cd filesystem/fs-app

# Run with custom configuration
mvn spring-boot:run -Dspring.config.location=application.yml

# Or run JAR
java -jar target/dumont-fs-app.jar --spring.config.location=application.yml
```

## API Usage

### Start Indexing

```bash
# Index all files
curl http://localhost:8080/api/v2/filesystem/index/all

# Index specific path
curl http://localhost:8080/api/v2/filesystem/index/path \
  -H "Content-Type: application/json" \
  -d '{"path": "/data/documents"}'
```

### Monitor Progress

```bash
# Check indexing status
curl http://localhost:8080/api/v2/connector/monitoring/index/filesystem-docs
```

## Docker Compose

Create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  dumont-fs:
    image: viglet/dumont-filesystem:latest
    volumes:
      - /data:/data:ro
      - ./application.yml:/app/config/application.yml
    environment:
      - SPRING_CONFIG_LOCATION=/app/config/application.yml
    ports:
      - "8080:8080"
    depends_on:
      - solr
  
  solr:
    image: solr:9
    ports:
      - "8983:8983"
    command:
      - solr-precreate
      - documents
```

Run:
```bash
docker-compose up -d
```

## Advanced Configuration

### Custom Metadata Extraction

Create custom metadata extractor:

```java
@Component
public class CustomFileMetadataExtractor implements FileMetadataExtractor {
    
    @Override
    public Map<String, Object> extract(File file) {
        Map<String, Object> metadata = new HashMap<>();
        
        // Custom metadata logic
        metadata.put("file_name", file.getName());
        metadata.put("file_size", file.length());
        metadata.put("modified_date", new Date(file.lastModified()));
        
        // Extract custom attributes
        if (file.getName().contains("report")) {
            metadata.put("document_type", "report");
        }
        
        return metadata;
    }
}
```

### File Type Specific Processors

```yaml
dumont:
  filesystem:
    processors:
      pdf:
        extract-images: true
        ocr-enabled: true
      docx:
        extract-tables: true
      html:
        follow-links: false
```

## Monitoring

### Logs

```bash
# View logs
tail -f logs/dumont-fs.log

# Search for errors
grep ERROR logs/dumont-fs.log
```

### Metrics

```bash
# Check health
curl http://localhost:8080/actuator/health

# View metrics
curl http://localhost:8080/actuator/metrics

# Indexed files count
curl http://localhost:8080/actuator/metrics/dumont.fs.indexed.count
```

## Troubleshooting

### Files Not Being Indexed

1. Check file permissions:
```bash
ls -la /data/documents
```

2. Verify patterns match:
```bash
# Test pattern matching
find /data/documents -name "*.pdf"
```

3. Check logs for errors:
```bash
grep "ERROR\|WARN" logs/dumont-fs.log
```

### Performance Issues

1. Reduce batch size:
```yaml
dumont:
  filesystem:
    batch-size: 50
```

2. Exclude large files:
```yaml
dumont:
  filesystem:
    max-file-size: 5242880  # 5MB
```

3. Add more threads:
```yaml
dumont:
  filesystem:
    parallel-threads: 4
```

## Next Steps

- Configure incremental indexing with file watchers
- Set up scheduled re-indexing
- Implement custom content extractors
- Add file-type specific processors

## Resources

- [Configuration Guide](../../docs/configuration.md)
- [API Documentation](http://localhost:8080/swagger-ui.html)
- [GitHub Repository](https://github.com/openviglet/dumont)

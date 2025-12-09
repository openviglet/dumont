# Dumont Indexing Plugins

Dumont supports multiple search engines for content indexing through a plugin-based architecture. This allows you to index content to different search platforms based on your infrastructure needs.

## Supported Indexing Providers

### 1. Viglet Turing ES (Default)
The default indexing provider. If no provider is specified, Dumont will use Turing ES.

**Configuration:**
```yaml
dumont:
  indexing:
    provider: turing  # or omit this line to use default

turing:
  url: http://localhost:2700
  apiKey: your-api-key
```

### 2. Apache Solr
Index content directly to Apache Solr.

**Configuration:**
```yaml
dumont:
  indexing:
    provider: solr
    solr:
      url: http://localhost:8983/solr
      collection: dumont
```

### 3. Elasticsearch
Index content directly to Elasticsearch.

**Configuration:**
```yaml
dumont:
  indexing:
    provider: elasticsearch
    elasticsearch:
      url: http://localhost:9200
      index: dumont
      # Optional authentication
      username: your-username  # or use ~ for null
      password: your-password  # or use ~ for null
```

## How It Works

The indexing plugin system uses Spring's conditional bean loading (`@ConditionalOnProperty`) to activate the appropriate plugin based on the `dumont.indexing.provider` configuration.

When content is sent to the indexing queue:
1. The `DumConnectorProcessQueue` receives job items from the message queue
2. It delegates the indexing operation to the configured `DumIndexingPlugin`
3. The plugin handles the transformation and indexing to the target search engine
4. Each plugin properly manages its client connections with cleanup on shutdown

## Implementation Details

### Plugin Interface
All indexing plugins implement the `DumIndexingPlugin` interface:

```java
public interface DumIndexingPlugin {
    void index(TurSNJobItems turSNJobItems);
    String getProviderName();
}
```

### Available Plugins
- `DumTuringIndexingPlugin` - Viglet Turing ES implementation
- `DumSolrIndexingPlugin` - Apache Solr implementation  
- `DumElasticsearchIndexingPlugin` - Elasticsearch implementation

## Adding a New Indexing Provider

To add support for a new search engine:

1. Create a new class implementing `DumIndexingPlugin`
2. Add the `@Component` annotation
3. Use `@ConditionalOnProperty` to specify the provider name:
   ```java
   @ConditionalOnProperty(name = "dumont.indexing.provider", havingValue = "your-provider")
   ```
4. Implement the `index()` method to transform and send data to your search engine
5. Implement the `getProviderName()` method to return your provider's name
6. Add a `@PreDestroy` method to properly close any client connections
7. Update the application.yaml with configuration properties for your provider

## Migration Guide

### From Turing-only to Plugin-based

If you're upgrading from a version that only supported Turing ES, no changes are needed. The default behavior remains the same - content will be indexed to Turing ES.

To switch to a different provider, simply update your `application.yaml`:

```yaml
dumont:
  indexing:
    provider: solr  # or elasticsearch
    solr:
      url: http://your-solr-server:8983/solr
      collection: your-collection
```

## Troubleshooting

### Plugin not being loaded
Check the application logs for a message like:
```
DumConnectorProcessQueue initialized with indexing provider: <PROVIDER_NAME>
```

If you see a different provider than expected, verify:
- The `dumont.indexing.provider` property is set correctly
- The required configuration properties for your provider are present
- No typos in the provider name (it's case-sensitive)

### Connection errors
Each plugin logs its initialization. Look for messages like:
- `Initialized Turing indexing plugin with URL: ...`
- `Initialized Solr indexing plugin with URL: ... and collection: ...`
- `Initialized Elasticsearch indexing plugin with URL: ... and index: ...`

Check that the URLs and credentials are correct, and the target service is accessible.

## Dependencies

The following dependencies are included:
- **Turing Java SDK**: Always included (for Turing provider)
- **Apache Solr SolrJ** (9.10.0): For Solr support
- **Elasticsearch Java Client** (8.17.0): For Elasticsearch support

All dependencies are included in the build, so switching providers doesn't require rebuilding the application.

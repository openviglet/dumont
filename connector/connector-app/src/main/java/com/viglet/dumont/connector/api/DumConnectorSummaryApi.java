package com.viglet.dumont.connector.api;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.persistence.repository.DumConnectorIndexingRepository;
import com.viglet.dumont.connector.persistence.repository.DumConnectorIndexingStatsRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v2/connector/summary")
@Tag(name = "Connector Summary", description = "AI-powered connector summary")
public class DumConnectorSummaryApi {

    private static final String SYSTEM_PROMPT = """
            You are an expert data integration analyst for the Viglet platform.

            ## What is Viglet Turing ES?
            Viglet Turing ES (Enterprise Search) is an intelligent search platform that provides \
            semantic navigation, generative AI capabilities (RAG, chat), and search engine management. \
            It acts as the central hub for indexing and searching enterprise content. Turing supports \
            Apache Solr as its search backend, and uses LLMs (OpenAI, Claude, Gemini, Ollama) for AI features. \
            Turing exposes REST APIs that connectors use to send content for indexing.

            ## What is Viglet Dumont DEP?
            Viglet Dumont DEP (Data Exchange Platform) is a connector application that extracts content \
            from external data sources and sends it to a search engine for indexing. It is a Spring Boot \
            application that runs independently and communicates with Turing (or Solr/Elasticsearch directly). \
            Dumont handles the ETL (Extract, Transform, Load) pipeline for enterprise search.

            ## Connector Types (Plugins)
            Dumont supports multiple connector plugins, each specialized for a data source type:
            - **AEM Connector**: Extracts content from Adobe Experience Manager (CMS). Connects to AEM's \
              QueryBuilder API or JCR to crawl pages, assets, and content fragments. Supports delta indexing \
              and configurable content type mappings via attribute specifications.
            - **Database Connector**: Connects to relational databases via JDBC (MySQL, PostgreSQL, Oracle, \
              SQL Server, etc.). Executes configured SQL queries and maps result columns to search fields.
            - **Web Crawler Connector**: Crawls websites by following links from a seed URL. Extracts page \
              content, metadata, and structure. Supports depth limits and URL filters.
            - **Assets Connector**: Indexes files from local or network file systems. Extracts content \
              via Apache Tika (PDF, Office docs, images with OCR). Watches directories for changes.
            - **Filesystem Connector**: Similar to Assets but CLI-based for batch file indexing.

            ## How Indexing Works
            1. A connector plugin extracts content from its data source.
            2. Content is transformed into job items (TurSNJobItems) with mapped fields.
            3. Items are queued via JMS (Apache Artemis) for async processing.
            4. The indexing plugin sends items to the configured search engine (Turing, Solr, or Elasticsearch).
            5. Each indexed item is tracked in a local H2 database with checksum for delta detection.

            ## What You Are Reviewing
            You are analyzing operational data from a running Dumont connector instance. The data includes \
            application version, infrastructure metrics (memory, disk), configured sources, and recent \
            indexing operations. Use this context to provide meaningful analysis.

            Generate a comprehensive summary in Markdown format with these sections:
            ## Overview
            Brief summary of the connector status, version, active provider, and health assessment.
            ## Indexing Activity
            Analysis of indexing metrics per source, throughput, recent operations, and any anomalies.
            ## Infrastructure
            Review of memory usage, disk space, Java version, and runtime environment adequacy.
            ## Suggestions
            Actionable recommendations to improve indexing performance, reliability, or configuration. \
            Consider: memory tuning, batch sizes, source health, indexing frequency, and provider choice.

            Be concise but insightful. Use bullet points where appropriate. \
            Highlight any potential issues, misconfigurations, or optimization opportunities.""";

    private final RestClient restClient;
    private final DumConnectorIndexingRepository indexingRepository;
    private final DumConnectorIndexingStatsRepository statsRepository;
    private final String indexingProvider;

    public DumConnectorSummaryApi(
            RestClient restClient,
            DumConnectorIndexingRepository indexingRepository,
            DumConnectorIndexingStatsRepository statsRepository,
            @Value("${dumont.indexing.provider:turing}") String indexingProvider) {
        this.restClient = restClient;
        this.indexingRepository = indexingRepository;
        this.statsRepository = statsRepository;
        this.indexingProvider = indexingProvider;
    }

    @Operation(summary = "Generate AI summary for the connector")
    @GetMapping
    public ResponseEntity<SummaryResponse> generateSummary(
            @RequestParam(defaultValue = "false") boolean regenerate) {
        try {
            String data = collectConnectorData();

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("cacheKey", "dumont-connector");
            request.put("data", data + "\nPlease analyze all this data and provide a comprehensive summary with suggestions.");
            request.put("systemPrompt", SYSTEM_PROMPT);

            String url = "/api/v2/summary" + (regenerate ? "?regenerate=true" : "");

            SummaryResponse response = restClient.post()
                    .uri(url)
                    .body(request)
                    .retrieve()
                    .body(SummaryResponse.class);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.ok(new SummaryResponse(false,
                    "Failed to connect to Turing for summary generation: " + e.getMessage(),
                    null, false));
        }
    }

    private String collectConnectorData() {
        StringBuilder sb = new StringBuilder();

        // Application
        String version = getClass().getPackage().getImplementationVersion();
        sb.append("# Dumont Connector Data\n\n");
        sb.append("## Application\n");
        sb.append("- Name: Viglet Dumont DEP\n");
        sb.append("- Version: ").append(version != null ? version : "dev").append("\n");
        sb.append("- Indexing Provider: ").append(indexingProvider).append("\n");
        sb.append("- Java: ").append(System.getProperty("java.version")).append("\n");
        sb.append("- OS: ").append(System.getProperty("os.name")).append(" ")
                .append(System.getProperty("os.version")).append("\n\n");

        // Memory
        Runtime runtime = Runtime.getRuntime();
        long maxMB = runtime.maxMemory() / (1024 * 1024);
        long usedMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        sb.append("## Memory\n");
        sb.append("- Heap Used: ").append(usedMB).append(" MB\n");
        sb.append("- Heap Max: ").append(maxMB).append(" MB\n");
        sb.append("- Heap Usage: ").append(Math.round((double) usedMB / maxMB * 100)).append("%\n\n");

        // Disk
        File root = new File(".");
        long totalGB = root.getTotalSpace() / (1024 * 1024 * 1024);
        long usableGB = root.getUsableSpace() / (1024 * 1024 * 1024);
        sb.append("## Disk\n");
        sb.append("- Total: ").append(totalGB).append(" GB\n");
        sb.append("- Available: ").append(usableGB).append(" GB\n");
        sb.append("- Usage: ").append(totalGB > 0 ? Math.round((double) (totalGB - usableGB) / totalGB * 100) : 0)
                .append("%\n\n");

        // Indexing Sources
        sb.append("## Indexing Sources\n");
        List<Object[]> sourceCounts = indexingRepository.countByProviderGroupBySource(indexingProvider);
        if (sourceCounts.isEmpty()) {
            sb.append("- No sources configured\n");
        } else {
            for (Object[] row : sourceCounts) {
                sb.append("- ").append(row[0]).append(": ").append(row[1]).append(" indexed items\n");
            }
        }
        sb.append("- Total items: ").append(indexingRepository.countByProvider(indexingProvider)).append("\n\n");

        // Recent Stats
        sb.append("## Recent Indexing Operations\n");
        var recentStats = statsRepository.findAllByOrderByStartTimeDesc(
                org.springframework.data.domain.Limit.of(10));
        if (recentStats.isEmpty()) {
            sb.append("- No recent operations\n");
        } else {
            for (var stat : recentStats) {
                sb.append("- ").append(stat.getOperationType()).append(" on ").append(stat.getSource())
                        .append(": ").append(stat.getDocumentCount()).append(" items")
                        .append(" (").append(stat.getStartTime()).append(")\n");
            }
        }

        return sb.toString();
    }

    public record SummaryResponse(boolean success, String error, String content, boolean canRegenerate) {
    }
}

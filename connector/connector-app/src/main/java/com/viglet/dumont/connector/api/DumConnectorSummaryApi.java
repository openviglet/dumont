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
            You are an expert data integration analyst reviewing a Dumont connector instance. \
            Analyze the data provided and generate a comprehensive summary in Markdown format. \
            Include these sections:
            ## Overview
            Brief summary of the connector status, version, and configuration.
            ## Indexing Activity
            Analysis of indexing metrics, sources, and recent operations.
            ## Infrastructure
            Review of memory, disk, and runtime environment.
            ## Suggestions
            Actionable recommendations to improve performance and reliability.

            Be concise but insightful. Use bullet points where appropriate. \
            Highlight any potential issues or misconfigurations.""";

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
        List<String> sources = indexingRepository.findAllSources(indexingProvider);
        if (sources.isEmpty()) {
            sb.append("- No sources configured\n");
        } else {
            for (String source : sources) {
                List<Object[]> counts = indexingRepository.countByProviderGroupBySource(indexingProvider);
                long count = counts.stream()
                        .filter(c -> source.equals(c[0]))
                        .map(c -> (Long) c[1])
                        .findFirst().orElse(0L);
                sb.append("- ").append(source).append(": ").append(count).append(" indexed items\n");
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

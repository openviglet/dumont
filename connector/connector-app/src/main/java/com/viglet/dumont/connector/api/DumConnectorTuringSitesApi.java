package com.viglet.dumont.connector.api;

import java.util.Collections;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v2/connector/turing-sn-sites")
@Tag(name = "Turing SN Sites", description = "List Semantic Navigation sites from Turing")
@Slf4j
public class DumConnectorTuringSitesApi {

    private final RestClient restClient;

    public DumConnectorTuringSitesApi(RestClient restClient) {
        this.restClient = restClient;
    }

    public record TuringSNSite(String id, String name, String description) {
    }

    public record TuringSNSiteField(String id, String name, String type) {
    }

    @Operation(summary = "List available Turing SN sites")
    @GetMapping
    public List<TuringSNSite> listSites() {
        try {
            return restClient.get()
                    .uri("/api/sn")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (Exception e) {
            log.error("Failed to fetch Turing SN sites: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Lists the fields of a Turing SN site through the connector, so the UI can
     * read them in standalone mode too — a direct call to Turing's
     * {@code /api/sn/{id}/field/ext} only resolves when the UI is embedded in
     * Turing.
     */
    @Operation(summary = "List fields of a Turing SN site")
    @GetMapping("/{siteId}/fields")
    public List<TuringSNSiteField> listFields(@PathVariable String siteId) {
        try {
            return restClient.get()
                    .uri("/api/sn/{siteId}/field/ext", siteId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (Exception e) {
            log.error("Failed to fetch fields of Turing SN site {}: {}", siteId, e.getMessage());
            return Collections.emptyList();
        }
    }
}

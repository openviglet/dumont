package com.viglet.dumont.connector.api;

import java.util.Collections;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@CrossOrigin(origins = "*")
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
}

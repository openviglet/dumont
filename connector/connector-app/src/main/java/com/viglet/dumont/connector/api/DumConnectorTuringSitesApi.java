package com.viglet.dumont.connector.api;

import java.util.Collections;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

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

    /** Why the sites could not be listed: {@code unreachable}, {@code unauthorized}, {@code not_found} or {@code error}. */
    public record SitesUnavailable(String reason, Integer status, String message) {
    }

    /**
     * Lists Turing's SN sites. When Turing cannot answer, the reply is a {@code 502} naming why
     * (D342), never an empty list: "no sites" and "Turing is down" need different actions, and an
     * empty list made the form say the first when the truth was the second. Not 401 on a refused
     * key, which would send the Turing host to its login page.
     */
    @Operation(summary = "List available Turing SN sites")
    @GetMapping
    public ResponseEntity<Object> listSites() {
        try {
            List<TuringSNSite> sites = restClient.get()
                    .uri("/api/sn")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return ResponseEntity.ok(sites == null ? List.of() : sites);
        } catch (RestClientException e) {
            SitesUnavailable unavailable = unavailable(e);
            log.error("Failed to fetch Turing SN sites ({}): {}", unavailable.reason(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(unavailable);
        }
    }

    static SitesUnavailable unavailable(RestClientException e) {
        if (e instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            String reason = switch (status) {
                case 401, 403 -> "unauthorized";
                case 404 -> "not_found";
                default -> "error";
            };
            return new SitesUnavailable(reason, status, e.getMessage());
        }
        if (e instanceof ResourceAccessException) {
            return new SitesUnavailable("unreachable", null, e.getMessage());
        }
        return new SitesUnavailable("error", null, e.getMessage());
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

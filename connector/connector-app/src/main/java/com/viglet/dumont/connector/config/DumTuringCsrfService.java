package com.viglet.dumont.connector.config;

import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class DumTuringCsrfService {

    private final RestClient restClient;

    public DumTuringCsrfService(RestClient restClient) {
        this.restClient = restClient;
    }

    public CsrfToken fetch() {
        Map<String, String> csrf = restClient.get()
                .uri("/api/csrf")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (csrf == null || !csrf.containsKey("token")) {
            throw new IllegalStateException("Failed to fetch CSRF token from Turing");
        }
        return new CsrfToken(csrf.get("token"), csrf.get("headerName"));
    }

    public record CsrfToken(String token, String headerName) {
    }
}

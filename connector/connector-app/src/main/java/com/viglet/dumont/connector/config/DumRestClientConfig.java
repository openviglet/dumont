package com.viglet.dumont.connector.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class DumRestClientConfig {
    public static final String KEY = "Key";
    @NotNull
    private final String turingUrl;
    @NotNull
    private final String turingApiKey;

    public DumRestClientConfig(@Value("${turing.url:http://localhost:2700}") @NotNull String turingUrl,
            @Value("${turing.apiKey}") @NotNull String turingApiKey) {
        this.turingUrl = turingUrl;
        this.turingApiKey = turingApiKey;
    }

    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                .requestFactory(new BufferingClientHttpRequestFactory(
                        new HttpComponentsClientHttpRequestFactory()))
                .baseUrl(turingUrl)
                .defaultHeader(KEY, turingApiKey)
                .requestInterceptor(tracingInterceptor())
                .build();
    }

    private ClientHttpRequestInterceptor tracingInterceptor() {
        return (HttpRequest request, byte[] body, ClientHttpRequestExecution execution) -> {
            if (log.isTraceEnabled()) {
                log.trace("HTTP --> {} {} headers={} body={}", request.getMethod(), request.getURI(),
                        request.getHeaders(), new String(body, StandardCharsets.UTF_8));
            }
            ClientHttpResponse response = execution.execute(request, body);
            if (log.isTraceEnabled()) {
                String responseBody = readBody(response);
                log.trace("HTTP <-- {} {} headers={} body={}", response.getStatusCode(),
                        request.getURI(), response.getHeaders(), responseBody);
            }
            return response;
        };
    }

    private String readBody(ClientHttpResponse response) {
        try {
            return StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<unreadable: " + e.getMessage() + ">";
        }
    }
}

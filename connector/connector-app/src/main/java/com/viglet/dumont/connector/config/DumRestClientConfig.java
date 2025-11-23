package com.viglet.dumont.connector.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.lang.NonNull;
import org.springframework.web.client.RestClient;

@Configuration
public class DumRestClientConfig {
    public static final String KEY = "Key";
    @NonNull
    private final String turingUrl;
    @NonNull
    private final String turingApiKey;

    public DumRestClientConfig(@Value("${turing.url:http://localhost:2700}") @NonNull String turingUrl,
            @Value("${turing.apiKey}") @NonNull String turingApiKey) {
        this.turingUrl = turingUrl;
        this.turingApiKey = turingApiKey;
    }

    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory())
                .baseUrl(turingUrl)
                .defaultHeader(KEY, turingApiKey)
                .build();
    }
}
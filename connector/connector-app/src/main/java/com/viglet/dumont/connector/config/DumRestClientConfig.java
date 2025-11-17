package com.viglet.dumont.connector.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class DumRestClientConfig {
    public static final String KEY = "Key";
    private final String dumontUrl;
    private final String dumontApiKey;

    public DumRestClientConfig(@Value("${dumont.url:http://localhost:2700}") String dumontUrl,
            @Value("${dumont.apiKey}") String dumontApiKey) {
        this.dumontUrl = dumontUrl;
        this.dumontApiKey = dumontApiKey;
    }

    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory())
                .baseUrl(dumontUrl)
                .defaultHeader(KEY, dumontApiKey)
                .build();
    }
}
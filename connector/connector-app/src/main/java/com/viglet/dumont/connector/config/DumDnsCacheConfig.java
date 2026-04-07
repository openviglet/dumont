package com.viglet.dumont.connector.config;

import java.security.Security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class DumDnsCacheConfig {

    @Value("${dumont.dns.cache.ttl:60}")
    private String dnsCacheTtl;

    @Value("${dumont.dns.cache.negative.ttl:0}")
    private String dnsCacheNegativeTtl;

    @PostConstruct
    public void configureDnsCache() {
        Security.setProperty("networkaddress.cache.ttl", dnsCacheTtl);
        Security.setProperty("networkaddress.cache.negative.ttl", dnsCacheNegativeTtl);
        log.info("DNS cache configured: ttl={}, negative.ttl={}", dnsCacheTtl, dnsCacheNegativeTtl);
    }
}

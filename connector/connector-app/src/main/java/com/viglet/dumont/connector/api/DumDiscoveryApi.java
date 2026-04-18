/*
 *
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */

package com.viglet.dumont.connector.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.dumont.connector.properties.DumAuthConfigProperties;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Discovery endpoint — exposes runtime feature flags the frontend needs to
 * decide how to render (multi-tenant switcher, OAuth2 providers, self
 * registration toggle). Mirrors {@code TurDiscoveryAPI}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v2/discovery")
@Tag(name = "Discovery API", description = "Runtime feature flags for the frontend")
public class DumDiscoveryApi {

    private final DumAuthConfigProperties configProperties;
    private final Optional<ClientRegistrationRepository> clientRegistrationRepository;

    @Value("${dumont.multi-tenant:false}")
    private boolean multiTenant;

    public DumDiscoveryApi(DumAuthConfigProperties configProperties,
            Optional<ClientRegistrationRepository> clientRegistrationRepository) {
        this.configProperties = configProperties;
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @GetMapping
    public Map<String, Object> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("product", "Viglet Dumont DEP");
        info.put("multiTenant", multiTenant);
        info.put("keycloak", configProperties.isKeycloak());
        info.put("authThirdparty", configProperties.getAuthentication() == null
                || configProperties.getAuthentication().isThirdparty());
        info.put("selfRegistration", configProperties.getAuthentication() != null
                && configProperties.getAuthentication().isNewUser());
        info.put("oauth2Providers", resolveOAuth2Providers());
        return info;
    }

    private List<String> resolveOAuth2Providers() {
        if (clientRegistrationRepository.isEmpty()) {
            return List.of();
        }
        var repo = clientRegistrationRepository.get();
        if (repo instanceof Iterable<?> iterable) {
            List<String> providers = new ArrayList<>();
            for (Object obj : iterable) {
                if (obj instanceof ClientRegistration registration) {
                    providers.add(registration.getRegistrationId());
                }
            }
            return providers;
        }
        return List.of();
    }
}

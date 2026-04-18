/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.dumont.connector.service.keycloak;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.viglet.dumont.connector.properties.DumAuthConfigProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Calls the Keycloak Admin REST API to list users and groups for the realm
 * configured under {@code spring.security.oauth2.client.provider.keycloak.issuer-uri}.
 * Uses the current user's OIDC access token for authentication.
 *
 * @since 2026.2.14
 */
@Slf4j
@Service
public class DumKeycloakAdminService {

    private final DumAuthConfigProperties configProperties;
    private final Optional<OAuth2AuthorizedClientService> authorizedClientService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final String issuerUri;

    public DumKeycloakAdminService(DumAuthConfigProperties configProperties,
                                   Optional<OAuth2AuthorizedClientService> authorizedClientService,
                                   @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri:}") String issuerUri) {
        this.configProperties = configProperties;
        this.authorizedClientService = authorizedClientService;
        this.issuerUri = issuerUri;
    }

    public boolean isEnabled() {
        return configProperties.isKeycloak() && StringUtils.hasText(issuerUri);
    }

    public List<DumKeycloakUserDto> listUsers() {
        URI uri = UriComponentsBuilder.fromUriString(adminBaseUrl())
                .path("/users")
                .queryParam("max", 200)
                .queryParam("briefRepresentation", true)
                .build().encode().toUri();
        DumKeycloakUserDto[] users = exchange(uri, HttpMethod.GET, DumKeycloakUserDto[].class);
        return users == null ? Collections.emptyList() : Arrays.asList(users);
    }

    public Optional<DumKeycloakUserDto> findUserByUsername(String username) {
        URI uri = UriComponentsBuilder.fromUriString(adminBaseUrl())
                .path("/users")
                .queryParam("username", username)
                .queryParam("exact", true)
                .build().encode().toUri();
        DumKeycloakUserDto[] users = exchange(uri, HttpMethod.GET, DumKeycloakUserDto[].class);
        if (users == null || users.length == 0) return Optional.empty();
        DumKeycloakUserDto base = users[0];
        List<String> groups = listUserGroupNames(base.id());
        return Optional.of(new DumKeycloakUserDto(
                base.id(), base.username(), base.firstName(), base.lastName(), base.email(),
                base.enabled(), base.emailVerified(), base.createdTimestamp(),
                groups, base.realmRoles()));
    }

    public List<DumKeycloakGroupDto> listGroups() {
        URI uri = UriComponentsBuilder.fromUriString(adminBaseUrl())
                .path("/groups")
                .queryParam("max", 200)
                .build().encode().toUri();
        DumKeycloakGroupDto[] groups = exchange(uri, HttpMethod.GET, DumKeycloakGroupDto[].class);
        return groups == null ? Collections.emptyList() : Arrays.asList(groups);
    }

    public Optional<DumKeycloakGroupDto> findGroupById(String id) {
        URI uri = UriComponentsBuilder.fromUriString(adminBaseUrl())
                .path("/groups/{id}")
                .build().expand(id).encode().toUri();
        try {
            DumKeycloakGroupDto group = exchange(uri, HttpMethod.GET, DumKeycloakGroupDto.class);
            if (group == null) return Optional.empty();
            return Optional.of(group.withMembers(listGroupMembers(id)));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) return Optional.empty();
            throw e;
        }
    }

    private List<DumKeycloakUserDto> listGroupMembers(String groupId) {
        URI uri = UriComponentsBuilder.fromUriString(adminBaseUrl())
                .path("/groups/{id}/members")
                .queryParam("max", 200)
                .build().expand(groupId).encode().toUri();
        DumKeycloakUserDto[] members = exchange(uri, HttpMethod.GET, DumKeycloakUserDto[].class);
        return members == null ? Collections.emptyList() : Arrays.asList(members);
    }

    private List<String> listUserGroupNames(String userId) {
        URI uri = UriComponentsBuilder.fromUriString(adminBaseUrl())
                .path("/users/{id}/groups")
                .build().expand(userId).encode().toUri();
        DumKeycloakGroupDto[] groups = exchange(uri, HttpMethod.GET, DumKeycloakGroupDto[].class);
        if (groups == null) return Collections.emptyList();
        return Arrays.stream(groups).map(DumKeycloakGroupDto::name).toList();
    }

    private <T> T exchange(URI uri, HttpMethod method, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(currentAccessToken());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<T> response = restTemplate.exchange(uri, method, new HttpEntity<>(headers), responseType);
        return response.getBody();
    }

    private String currentAccessToken() {
        if (authorizedClientService.isEmpty()) {
            throw new IllegalStateException("OAuth2 client is not configured");
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof OAuth2AuthenticationToken oauthToken)) {
            throw new IllegalStateException("Current user is not authenticated via OAuth2");
        }
        OAuth2AuthorizedClient client = authorizedClientService.get()
                .loadAuthorizedClient(oauthToken.getAuthorizedClientRegistrationId(), oauthToken.getName());
        if (client == null || client.getAccessToken() == null) {
            throw new IllegalStateException("No OAuth2 access token available for current user");
        }
        return client.getAccessToken().getTokenValue();
    }

    private String adminBaseUrl() {
        if (!StringUtils.hasText(issuerUri)) {
            throw new IllegalStateException("spring.security.oauth2.client.provider.keycloak.issuer-uri is not configured");
        }
        URI uri = URI.create(issuerUri);
        String path = uri.getPath() == null ? "" : uri.getPath();
        int realmsIdx = path.indexOf("/realms/");
        if (realmsIdx < 0) {
            throw new IllegalStateException("issuer-uri does not contain '/realms/<name>': " + issuerUri);
        }
        String basePath = path.substring(0, realmsIdx);
        String realm = path.substring(realmsIdx + "/realms/".length());
        if (realm.endsWith("/")) {
            realm = realm.substring(0, realm.length() - 1);
        }
        return uri.getScheme() + "://" + uri.getAuthority() + basePath + "/admin/realms/" + realm;
    }
}

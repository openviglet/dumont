/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.viglet.dumont.connector.service.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import com.viglet.dumont.connector.properties.DumAuthConfigProperties;

class DumKeycloakAdminServiceTest {

    private static final String ISSUER = "http://keycloak.invalid/realms/dumont";

    private DumAuthConfigProperties configProperties;
    private OAuth2AuthorizedClientService authorizedClientService;
    private RestTemplate restTemplate;
    private DumKeycloakAdminService service;

    @BeforeEach
    void setUp() {
        configProperties = mock(DumAuthConfigProperties.class);
        authorizedClientService = mock(OAuth2AuthorizedClientService.class);
        restTemplate = mock(RestTemplate.class);
        service = new DumKeycloakAdminService(configProperties,
                Optional.of(authorizedClientService), ISSUER);
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        authenticateOAuth2();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateOAuth2() {
        OAuth2AuthenticationToken token = mock(OAuth2AuthenticationToken.class);
        lenient().when(token.getAuthorizedClientRegistrationId()).thenReturn("keycloak");
        lenient().when(token.getName()).thenReturn("alice");
        OAuth2AuthorizedClient client = mock(OAuth2AuthorizedClient.class);
        OAuth2AccessToken accessToken = mock(OAuth2AccessToken.class);
        lenient().when(accessToken.getTokenValue()).thenReturn("token-123");
        lenient().when(client.getAccessToken()).thenReturn(accessToken);
        lenient().when(authorizedClientService.loadAuthorizedClient("keycloak", "alice"))
                .thenReturn(client);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(token);
        SecurityContextHolder.setContext(context);
    }

    private static DumKeycloakUserDto user(String id, String username) {
        return new DumKeycloakUserDto(id, username, null, null, null, true, true, null, null, null);
    }

    private static DumKeycloakGroupDto group(String id, String name) {
        return new DumKeycloakGroupDto(id, name, null, null, null, null);
    }

    @Test
    void isEnabledRequiresKeycloakAndIssuer() {
        when(configProperties.isKeycloak()).thenReturn(true);
        assertThat(service.isEnabled()).isTrue();

        when(configProperties.isKeycloak()).thenReturn(false);
        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    void listUsersReturnsMappedArray() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(DumKeycloakUserDto[].class)))
                .thenReturn(ResponseEntity.ok(new DumKeycloakUserDto[] {user("u1", "alice")}));

        assertThat(service.listUsers()).singleElement()
                .satisfies(u -> assertThat(u.username()).isEqualTo("alice"));
    }

    @Test
    void listGroupsReturnsMappedArray() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(DumKeycloakGroupDto[].class)))
                .thenReturn(ResponseEntity.ok(new DumKeycloakGroupDto[] {group("g1", "Editors")}));

        assertThat(service.listGroups()).hasSize(1);
    }

    @Test
    void findUserByUsernameMergesGroupNames() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(DumKeycloakUserDto[].class)))
                .thenReturn(ResponseEntity.ok(new DumKeycloakUserDto[] {user("u1", "alice")}));
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(DumKeycloakGroupDto[].class)))
                .thenReturn(ResponseEntity.ok(new DumKeycloakGroupDto[] {group("g1", "Editors")}));

        Optional<DumKeycloakUserDto> result = service.findUserByUsername("alice");

        assertThat(result).isPresent();
        assertThat(result.get().groups()).containsExactly("Editors");
    }

    @Test
    void findUserByUsernameEmptyWhenNoMatch() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(DumKeycloakUserDto[].class)))
                .thenReturn(ResponseEntity.ok(new DumKeycloakUserDto[0]));

        assertThat(service.findUserByUsername("ghost")).isEmpty();
    }

    @Test
    void findGroupByIdMergesMembers() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(DumKeycloakGroupDto.class)))
                .thenReturn(ResponseEntity.ok(group("g1", "Editors")));
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(DumKeycloakUserDto[].class)))
                .thenReturn(ResponseEntity.ok(new DumKeycloakUserDto[] {user("u1", "alice")}));

        Optional<DumKeycloakGroupDto> result = service.findGroupById("g1");

        assertThat(result).isPresent();
        assertThat(result.get().members()).hasSize(1);
    }

    @Test
    void findGroupByIdEmptyOn404() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(DumKeycloakGroupDto.class)))
                .thenThrow(new RestClientResponseException("Not Found", HttpStatusCode.valueOf(404),
                        "Not Found", null, null, null));

        assertThat(service.findGroupById("missing")).isEmpty();
    }

    @Test
    void accessTokenFailsWhenOAuthClientServiceAbsent() {
        DumKeycloakAdminService noClient =
                new DumKeycloakAdminService(configProperties, Optional.empty(), ISSUER);
        ReflectionTestUtils.setField(noClient, "restTemplate", restTemplate);
        assertThatThrownBy(noClient::listUsers).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void accessTokenFailsWhenNotOAuthAuthenticated() {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("u", "p", List.of()));
        SecurityContextHolder.setContext(context);
        assertThatThrownBy(service::listUsers).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void adminBaseUrlFailsForIssuerWithoutRealms() {
        DumKeycloakAdminService bad = new DumKeycloakAdminService(configProperties,
                Optional.of(authorizedClientService), "http://keycloak.invalid/no-realm");
        ReflectionTestUtils.setField(bad, "restTemplate", restTemplate);
        assertThatThrownBy(bad::listUsers).isInstanceOf(IllegalStateException.class);
    }
}

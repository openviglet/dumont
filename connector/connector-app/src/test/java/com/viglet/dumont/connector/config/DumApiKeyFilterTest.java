/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.viglet.dumont.connector.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.FilterChain;

class DumApiKeyFilterTest {

    private final DumApiKeyFilter filter = new DumApiKeyFilter("secret");

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingKeyPassesThroughWithoutAuthenticating() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void wrongKeyReturns422AndDoesNotContinue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Key", "wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(response.getContentAsString()).contains("Invalid or missing API Key");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void correctKeyAuthenticatesAsAdminAndContinues() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Key", "secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @Test
    void correctKeyViaQueryParameterAlsoAuthenticates() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("Key", "secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void shouldNotFilterPublicAndNonApiPaths() {
        assertThat(filter.shouldNotFilter(requestTo("/index.html"))).isTrue();
        assertThat(filter.shouldNotFilter(requestTo("/api/v2/connector/status"))).isTrue();
        assertThat(filter.shouldNotFilter(requestTo("/api/login"))).isTrue();
        assertThat(filter.shouldNotFilter(requestTo("/api/v2/aem/webhook/wknd"))).isTrue();
    }

    @Test
    void shouldFilterSecuredApiPaths() {
        assertThat(filter.shouldNotFilter(requestTo("/api/v2/connector/index/wknd/all")))
                .isFalse();
    }

    private static MockHttpServletRequest requestTo(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }
}

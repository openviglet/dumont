/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.dumont.connector.config.security;

import java.io.IOException;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Ensures the CSRF token is rendered into response cookies and exposed
 * via the Access-Control-Expose-Headers header for cross-origin React
 * frontends.
 *
 * @since 2026.2.14
 */
public class DumCsrfCookieFilter extends OncePerRequestFilter {

    private static final String EXPOSE_HEADERS = "Access-Control-Expose-Headers";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
        if (csrfToken != null) {
            String token = csrfToken.getToken();
            response.setHeader(csrfToken.getHeaderName(), token);

            String exposeHeaders = response.getHeader(EXPOSE_HEADERS);
            if (!StringUtils.hasText(exposeHeaders)) {
                response.setHeader(EXPOSE_HEADERS, csrfToken.getHeaderName());
            } else if (!exposeHeaders.contains(csrfToken.getHeaderName())) {
                response.setHeader(EXPOSE_HEADERS, exposeHeaders + ", " + csrfToken.getHeaderName());
            }
        }
        filterChain.doFilter(request, response);
    }
}

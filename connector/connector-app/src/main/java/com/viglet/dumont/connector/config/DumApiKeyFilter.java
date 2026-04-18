/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.dumont.connector.config;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates the {@code Key} header against {@code turing.apiKey} for machine-to-machine
 * calls (typically Turing → Dumont). Preserves the original semantics so Turing
 * consuming Dumont as a React Module Federation remote keeps working, while
 * allowing cookie/session-based login from the admin UI to flow through:
 * <ul>
 *   <li>Matching {@code Key} header → authenticate the caller as a synthetic admin
 *   so downstream endpoints secured with Spring Security still work.</li>
 *   <li>Missing {@code Key} header → pass through so that cookie/session-based
 *   login (from the admin UI) can authenticate the request.</li>
 *   <li>Present but wrong {@code Key} header → HTTP <b>422</b>, keeping the
 *   legacy contract that prevents Turing from redirecting to a login page.</li>
 * </ul>
 */
@Component
public class DumApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "Key";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String API_KEY_PRINCIPAL = "api-key";

    private final String apiKey;

    public DumApiKeyFilter(@Value("${turing.apiKey}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestKey = request.getHeader(API_KEY_HEADER);
        if (requestKey == null) {
            requestKey = request.getParameter(API_KEY_HEADER);
        }

        if (requestKey != null) {
            if (!apiKey.equals(requestKey)) {
                response.setStatus(422);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Invalid or missing API Key\"}");
                return;
            }
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        API_KEY_PRINCIPAL, null,
                        List.of(new SimpleGrantedAuthority(ROLE_ADMIN)));
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api")
                || uri.startsWith("/api/v2/ping")
                || uri.startsWith("/api/v2/discovery")
                || uri.startsWith("/api/v2/aem/status")
                || uri.startsWith("/api/v2/aem/index/")
                || uri.startsWith("/api/v2/connector/status")
                || uri.startsWith("/api/csrf")
                || uri.startsWith("/api/setup")
                || uri.startsWith("/api/login")
                || uri.startsWith("/api/v2/user/current")
                || uri.startsWith("/logout");
    }
}

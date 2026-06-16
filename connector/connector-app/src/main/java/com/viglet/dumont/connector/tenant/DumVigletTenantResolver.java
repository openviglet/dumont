/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.dumont.connector.tenant;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.core.tenancy.VigletTenantResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * T398 / §XIV.9 — the {@link VigletTenantResolver} SPI carrying <em>only</em>
 * Dumont's resolution sources; the shared {@code VigletTenantResolutionFilter}
 * owns the gating (suspended → 403, non-member → 403, platform-admin bypass,
 * always-clear).
 *
 * <p>Resolution precedence, highest first:
 * <ol>
 *   <li>the session attribute {@link DumTenantFilter#TENANT_SESSION_ATTR} (set by a
 *       future tenant-switch endpoint);</li>
 *   <li>the {@code X-Dumont-Tenant} HTTP header — the channel Turing → Dumont
 *       machine calls use to scope an indexing request to a tenant (those calls
 *       authenticate as {@code ROLE_ADMIN} via {@code DumApiKeyFilter}, so the
 *       shared gate's platform-admin bypass lets them through);</li>
 *   <li>the OIDC/OAuth2 {@code tenant} or {@code workspace_id} claim.</li>
 * </ol>
 *
 * <p>When none resolve, this returns {@link Resolution#none()} and the shared
 * filter binds the default {@code dumont} tenant — so a single-tenant install
 * behaves exactly as before.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class DumVigletTenantResolver implements VigletTenantResolver {

    /** Header carrying the target tenant of a machine (Turing → Dumont) call. */
    public static final String TENANT_HEADER = "X-Dumont-Tenant";

    private static final String TENANT_CLAIM = "tenant";
    private static final String WORKSPACE_ID_CLAIM = "workspace_id";

    @Override
    public Resolution resolve(HttpServletRequest request, Authentication authentication) {
        // An explicit tenant switch is held on the session.
        String sessionTenant = sessionTenant(request);
        if (StringUtils.hasText(sessionTenant)) {
            return Resolution.of(sessionTenant);
        }
        // A machine caller (or an explicit client) names the tenant via header.
        String headerTenant = request.getHeader(TENANT_HEADER);
        if (StringUtils.hasText(headerTenant)) {
            return Resolution.of(headerTenant);
        }
        // An OIDC/OAuth2 principal may carry its tenant/workspace as a claim.
        String claim = tenantClaim(authentication);
        if (StringUtils.hasText(claim)) {
            return Resolution.of(claim);
        }
        return Resolution.none();
    }

    private static String sessionTenant(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(DumTenantFilter.TENANT_SESSION_ATTR);
        return value instanceof String tenant ? tenant : null;
    }

    private static String tenantClaim(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            return firstClaim(oidcUser.getClaims().get(TENANT_CLAIM),
                    oidcUser.getClaims().get(WORKSPACE_ID_CLAIM));
        }
        if (principal instanceof OAuth2User oAuth2User) {
            return firstClaim(oAuth2User.getAttributes().get(TENANT_CLAIM),
                    oAuth2User.getAttributes().get(WORKSPACE_ID_CLAIM));
        }
        return null;
    }

    private static String firstClaim(Object primary, Object fallback) {
        if (primary != null) {
            return primary.toString();
        }
        return fallback != null ? fallback.toString() : null;
    }
}

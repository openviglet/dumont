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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.viglet.core.tenancy.VigletPlatformAdminService;
import com.viglet.core.tenancy.VigletTenantContext;

/**
 * T398 / §XIV.9 — Dumont's cross-tenant privilege gate: the {@code viglet-core}
 * tenancy kernel ({@link VigletPlatformAdminService}) exposed as a Dumont
 * {@code @Service} bean, giving Dumont the audited {@code runForTenant} /
 * {@code runAsSystem} primitives for free.
 *
 * <p>Dumont's existing admin authority is {@link #ROLE_ADMIN} — granted to local
 * admins and to Turing → Dumont machine calls authenticated by
 * {@code DumApiKeyFilter}. This overrides {@link #isPlatformAdmin()} to honour
 * <em>either</em> {@code ROLE_ADMIN} or the shared {@link #ROLE_PLATFORM_ADMIN},
 * so the shared resolution-filter gate and teardown service treat Dumont admins
 * (and machine callers) as platform admins, exactly as the legacy {@code Key}
 * header contract intended.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Service
public class DumPlatformAdminService extends VigletPlatformAdminService {

    /** Dumont's existing platform-admin authority (the {@code DumApiKeyFilter} machine role). */
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    public DumPlatformAdminService(VigletTenantContext tenantContext) {
        super(tenantContext);
    }

    @Override
    public boolean isPlatformAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String role = authority.getAuthority();
            if (ROLE_PLATFORM_ADMIN.equals(role) || ROLE_ADMIN.equals(role)) {
                return true;
            }
        }
        return false;
    }
}

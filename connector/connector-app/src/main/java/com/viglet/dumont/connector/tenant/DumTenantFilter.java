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

import org.springframework.stereotype.Component;

import com.viglet.core.tenancy.VigletTenantContext;
import com.viglet.core.tenancy.VigletTenantResolutionFilter;

/**
 * T398 / §XIV.9 — resolves the <em>current tenant</em> for each request and binds
 * it to the tenant context for the request thread.
 *
 * <p>A thin Dumont-named bean over the shared {@link VigletTenantResolutionFilter}:
 * it supplies the lifted gate (suspended → 403, non-member → 403, platform-admin
 * bypass, always-clear — the cross-tenant-leak surface) with Dumont's own
 * resolution sources via {@link DumVigletTenantResolver} and Dumont's registry via
 * {@link DumVigletTenantStore}. Registered <strong>after</strong> the
 * authentication filters in {@code DumWebConfiguration}; the whole filter is a
 * no-op when {@code dumont.multi-tenant=false} (the injected
 * {@link VigletTenantContext} reports tenancy disabled), so a single-tenant install
 * behaves exactly as before.
 *
 * <p>Being a {@code @Component} extending {@link VigletTenantResolutionFilter}, this
 * bean satisfies the core auto-configuration's {@code @ConditionalOnMissingBean}, so
 * the generic shared filter bean never also registers. Spring Boot would otherwise
 * auto-register a {@code OncePerRequestFilter} bean at the end of the servlet chain;
 * {@code OncePerRequestFilter}'s once-per-request guard makes that copy a no-op once
 * the in-chain instance (added in {@code DumWebConfiguration}) has run.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class DumTenantFilter extends VigletTenantResolutionFilter {

    /** Session attribute set by a future {@code POST /tenant/{name}/switch} endpoint. */
    public static final String TENANT_SESSION_ATTR = "DUM_TENANT";

    public DumTenantFilter(VigletTenantContext vigletTenantContext,
            DumVigletTenantStore store,
            DumVigletTenantResolver resolver,
            DumPlatformAdminService platformAdminService) {
        super(vigletTenantContext, store, resolver, platformAdminService);
    }
}

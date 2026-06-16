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

import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.viglet.core.tenancy.VigletTenantContext;
import com.viglet.core.tenancy.VigletTenantSlugValidator;
import com.viglet.dumont.connector.persistence.model.tenant.DumTenant;
import com.viglet.dumont.connector.properties.DumAuthConfigProperties;

/**
 * T398 / §XIV.9 — Dumont's tenancy wiring onto the shared
 * {@code viglet-core-tenancy} surface. The shared services
 * ({@code VigletTenantMembershipService}, {@code VigletQuotaService},
 * {@code VigletTenantTeardownService}, {@code VigletInfraTenantScope}, and the
 * {@code VigletTenantResolutionFilter} base) auto-configure themselves the moment
 * Dumont's SPI beans ({@link DumVigletTenantStore}, {@link DumVigletTenantResolver},
 * {@link DumVigletResourceCounter}, {@link DumPlatformAdminService}) are present.
 *
 * <p>This class supplies the two product policies the shared surface needs that
 * have no safe neutral default for Dumont:
 * <ul>
 *   <li>the {@link VigletTenantContext} kernel, wired from {@code dumont.multi-tenant}
 *       and the reserved default tenant {@code dumont} (satisfying the core
 *       auto-configuration's {@code @ConditionalOnMissingBean} so the generic
 *       {@code viglet.tenancy.*} bean never activates) — its tenant state is the
 *       same thread-local the static {@link DumTenantContext} facade exposes;</li>
 *   <li>the slug validator's Dumont-specific reserved name {@code dumont}.</li>
 * </ul>
 *
 * <p>Dumont is greenfield, so it keeps the no-op {@code VigletTenantProvisioner.NONE}
 * and {@code VigletTenantPurger.NONE} defaults: there are no extra bytes to seed on
 * signup, and a torn-down tenant's {@code @TenantId} connector rows become invisible
 * once its catalog row is gone (mirroring Shio's documented teardown behaviour).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Configuration(proxyBeanMethods = false)
public class DumTenancyConfiguration {

    /**
     * The shared tenancy kernel, wired from {@code dumont.multi-tenant} and the
     * reserved default tenant. {@link VigletTenantContext} holds its tenant/system
     * state in thread-locals, so this bean and the static {@link DumTenantContext}
     * facade observe the same per-request tenant regardless of instance identity.
     */
    @Bean
    public VigletTenantContext vigletTenantContext(DumAuthConfigProperties properties) {
        return new VigletTenantContext(properties.isMultiTenant(), DumTenant.DEFAULT_TENANT_NAME);
    }

    /**
     * The shared slug validator with Dumont's extra reserved label on top of the
     * platform-neutral defaults — so signup rejects {@code dumont}. Overrides the
     * auto-config default ({@code @ConditionalOnMissingBean}).
     */
    @Bean
    public VigletTenantSlugValidator vigletTenantSlugValidator() {
        return new VigletTenantSlugValidator(Set.of("dumont"));
    }
}

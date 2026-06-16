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

import com.viglet.core.tenancy.VigletTenantContext;
import com.viglet.dumont.connector.persistence.model.tenant.DumTenant;

/**
 * Thread-bound current-tenant holder. A thin static facade over the shared
 * {@link VigletTenantContext} kernel (viglet-core) so Dumont runs the <b>same</b>
 * tenant-context implementation as Turing and Shio (T398 / §XIV.9).
 *
 * <p>The underlying tenant/system-mode state is thread-local, so a single static
 * holder preserves per-thread isolation. The Spring-managed {@link VigletTenantContext}
 * bean (declared in {@code DumTenancyConfiguration}) and this facade observe the
 * same per-request tenant regardless of instance identity, because the state lives
 * in the {@code ThreadLocal}, not the instance.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumTenantContext {

    public static final String DEFAULT_TENANT = DumTenant.DEFAULT_TENANT_NAME;

    /**
     * The shared kernel instance. A single static holder is correct: the
     * underlying tenant/system-mode state is thread-local, so per-thread
     * isolation is preserved regardless of the holder being a singleton.
     */
    private static final VigletTenantContext DELEGATE = new VigletTenantContext(true, DEFAULT_TENANT);

    private DumTenantContext() {
    }

    public static void setTenantId(String tenantId) {
        DELEGATE.setCurrentTenant(tenantId);
    }

    public static String getTenantId() {
        return DELEGATE.getCurrentTenant();
    }

    public static void clear() {
        DELEGATE.clear();
    }

    /** The shared kernel for callers needing {@code resolveCurrentTenant}/system-mode/{@code runAs}. */
    public static VigletTenantContext context() {
        return DELEGATE;
    }
}

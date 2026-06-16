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

import java.util.Locale;

import org.springframework.stereotype.Component;

import com.viglet.core.tenancy.VigletPlanLimits;
import com.viglet.core.tenancy.VigletResourceCounter;
import com.viglet.dumont.connector.persistence.repository.DumConnectorIndexingRepository;

/**
 * T398 / §XIV.9 — the {@link VigletResourceCounter} SPI feeding the shared
 * {@code VigletQuotaService}: Dumont supplies the per-plan limits and the current
 * per-tenant counts; the compare-and-throw enforcement (HTTP 402) is shared.
 *
 * <p>The resource key {@value #INDEXINGS} is the opaque contract between this
 * counter and Dumont's quota call sites. The count comes from the
 * {@code @TenantId}-filtered indexing repository, so {@code count()} is already
 * scoped to the current tenant.
 *
 * <p>Plan mapping (mirrors the Turing/Shio convention): the {@code FREE} plan is
 * bounded; any other (paid) plan is unlimited until a richer billing model
 * lands, so a paying tenant is never blocked by accident.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class DumVigletResourceCounter implements VigletResourceCounter {

    /** The number of indexed objects a tenant holds. */
    public static final String INDEXINGS = "indexings";

    /** The bounded FREE plan: 10 000 indexed objects. */
    private static final VigletPlanLimits FREE = VigletPlanLimits.of(INDEXINGS, 10_000L);

    private final DumConnectorIndexingRepository dumConnectorIndexingRepository;

    public DumVigletResourceCounter(DumConnectorIndexingRepository dumConnectorIndexingRepository) {
        this.dumConnectorIndexingRepository = dumConnectorIndexingRepository;
    }

    @Override
    public long count(String tenantId, String resourceKey) {
        return switch (resourceKey) {
            case INDEXINGS -> dumConnectorIndexingRepository.count();
            default -> 0L;
        };
    }

    @Override
    public VigletPlanLimits limitsFor(String plan) {
        if (plan != null && "FREE".equals(plan.trim().toUpperCase(Locale.ROOT))) {
            return FREE;
        }
        return VigletPlanLimits.UNLIMITED;
    }
}

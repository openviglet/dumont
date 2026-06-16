/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.dumont.connector.persistence.model.tenant;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Persistent tenant record — the catalog of tenants that can exist in one
 * shared Dumont JVM (T398 / §XIV.9, Dumont's adoption of the shared
 * {@code viglet-core-tenancy} surface).
 *
 * <p>Following the Shio model, each row's {@link #name} is the tenant
 * identifier carried by Hibernate's {@code @TenantId} discriminator on the
 * connector content entities (the value {@code DumTenantContext} resolves per
 * request), <em>not</em> the surrogate UUID primary key. The reserved name
 * {@link #DEFAULT_TENANT_NAME} ("dumont") is the single-tenant default and is
 * seeded at startup ({@code DumTenantOnStartup}); it cannot be renamed or
 * deleted.
 *
 * <p>Deliberately <b>not</b> {@code @TenantId}-scoped — this is the global
 * catalog an admin lists across all tenants, queried before the request tenant
 * is known.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Getter
@Setter
@Entity
@Table(name = "dum_tenant")
public class DumTenant implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The reserved default tenant — the single-tenant identity, always present. */
    public static final String DEFAULT_TENANT_NAME = "dumont";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /**
     * The tenant identifier (the value of the {@code tenant_id} discriminator on
     * content rows). Unique; immutable once created. Doubles as the slug.
     */
    @Column(name = "name", length = 255, nullable = false, unique = true)
    private String name;

    @Column(name = "description", length = 510)
    private String description;

    @Column(name = "enabled")
    private boolean enabled = true;

    /**
     * Lifecycle state (Turing/Shio parity). {@code SUSPENDED} keeps the tenant's
     * data but blocks its members at {@code DumTenantFilter} with HTTP 403.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16)
    private DumTenantStatus status = DumTenantStatus.ACTIVE;

    /**
     * Billing/plan identifier (e.g. {@code FREE}, {@code PRO}). Free-form rather
     * than an enum so plans can evolve without a migration; the shared
     * {@code VigletQuotaService} maps it to per-tenant create limits via
     * {@code DumVigletResourceCounter}.
     */
    @Column(name = "plan", length = 50)
    private String plan = "FREE";

    @Column(name = "owner", length = 255)
    private String owner;

    @Column(name = "created_date")
    private Instant createdDate;

    /** True for the reserved {@code dumont} default tenant (never rename/delete). */
    public boolean isDefaultTenant() {
        return DEFAULT_TENANT_NAME.equals(name);
    }

    /** Whether this tenant is currently suspended (members blocked). */
    public boolean isSuspended() {
        return status == DumTenantStatus.SUSPENDED;
    }
}

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
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * A user's membership in a tenant (T398 / §XIV.9, Turing/Shio parity). Joins a
 * principal ({@link #username}) to a {@link DumTenant} with a
 * {@link DumTenantRole} and a {@link DumTenantMembershipStatus}.
 *
 * <p>One membership per (tenant, username). The username is a plain string (the
 * principal name / OIDC {@code preferred_username}), not a FK to {@code DumUser},
 * so a tenant can carry memberships for users provisioned lazily on first login.
 * Deliberately <b>not</b> {@code @TenantId}-scoped — it is queried <em>before</em>
 * the request tenant is known (to resolve which tenants a user may enter), exactly
 * like the {@link DumTenant} catalog itself.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Getter
@Setter
@Entity
@Table(name = "dum_tenant_membership",
        uniqueConstraints = @UniqueConstraint(name = "UK_dum_tenant_membership",
                columnNames = { "tenant_id", "username" }))
public class DumTenantMembership implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private DumTenant tenant;

    @Column(name = "username", length = 255, nullable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 16, nullable = false)
    private DumTenantRole role = DumTenantRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private DumTenantMembershipStatus status = DumTenantMembershipStatus.ACTIVE;

    @Column(name = "created_date")
    private Instant createdDate;

    /** Whether this membership currently grants access. */
    public boolean isActive() {
        return status == DumTenantMembershipStatus.ACTIVE;
    }
}

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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.core.tenancy.VigletTenantMembershipStatus;
import com.viglet.core.tenancy.VigletTenantRef;
import com.viglet.core.tenancy.VigletTenantRole;
import com.viglet.core.tenancy.VigletTenantStatus;
import com.viglet.core.tenancy.VigletTenantStore;
import com.viglet.dumont.connector.persistence.model.tenant.DumTenant;
import com.viglet.dumont.connector.persistence.model.tenant.DumTenantMembership;
import com.viglet.dumont.connector.persistence.model.tenant.DumTenantMembershipStatus;
import com.viglet.dumont.connector.persistence.model.tenant.DumTenantRole;
import com.viglet.dumont.connector.persistence.model.tenant.DumTenantStatus;
import com.viglet.dumont.connector.persistence.repository.tenant.DumTenantMembershipRepository;
import com.viglet.dumont.connector.persistence.repository.tenant.DumTenantRepository;

/**
 * T398 / §XIV.9 — the {@link VigletTenantStore} SPI over Dumont's
 * {@code DumTenant} / {@code DumTenantMembership} registry, expressed through the
 * neutral {@link VigletTenantRef} so the lifted shared services
 * ({@code VigletTenantMembershipService}, {@code VigletTenantResolutionFilter},
 * {@code VigletQuotaService}, {@code VigletTenantTeardownService}) never bind to
 * Dumont's JPA entities.
 *
 * <p>This is Dumont's greenfield bridge into the cross-product tenancy parity
 * work: the duplicated <em>logic</em> (idempotent signup, the resolution gate,
 * teardown) lives once in {@code viglet-core-tenancy} and now drives Dumont too,
 * while the duplicated <em>rows</em> (Dumont's own entity and id strategy) stay
 * here behind this one interface.
 *
 * <p>Like Shio, <strong>Dumont's discriminator is the tenant {@code name}</strong>
 * (the value Hibernate's {@code @TenantId} stamps on connector content rows), not
 * the entity's surrogate UUID primary key. The neutral {@link VigletTenantRef#id()
 * id} therefore maps to {@link DumTenant#getName()} (and the slug, since Dumont has
 * no separate slug), so the shared services canonicalize, bind and scope by the
 * same value {@code DumTenantContext} uses.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class DumVigletTenantStore implements VigletTenantStore {

    private final DumTenantRepository dumTenantRepository;
    private final DumTenantMembershipRepository dumTenantMembershipRepository;

    public DumVigletTenantStore(DumTenantRepository dumTenantRepository,
            DumTenantMembershipRepository dumTenantMembershipRepository) {
        this.dumTenantRepository = dumTenantRepository;
        this.dumTenantMembershipRepository = dumTenantMembershipRepository;
    }

    // --- reads -------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<VigletTenantRef> findById(String tenantId) {
        return dumTenantRepository.findByName(tenantId).map(DumVigletTenantStore::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VigletTenantRef> findBySlug(String slug) {
        // Dumont identifies a tenant by its name; the name doubles as the slug.
        return dumTenantRepository.findByName(slug).map(DumVigletTenantStore::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VigletTenantRef> findAll() {
        return dumTenantRepository.findAll().stream().map(DumVigletTenantStore::toRef).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActiveMember(String tenantId, String username) {
        return dumTenantMembershipRepository.findByTenant_NameAndUsername(tenantId, username)
                .map(DumTenantMembership::isActive)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOwnedBy(String tenantId, String username) {
        return dumTenantMembershipRepository.findByTenant_NameAndUsername(tenantId, username)
                .filter(m -> m.getRole() == DumTenantRole.OWNER)
                .isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VigletTenantRef> activeTenantsOf(String username) {
        return dumTenantMembershipRepository.findByUsername(username).stream()
                .filter(DumTenantMembership::isActive)
                .map(DumTenantMembership::getTenant)
                .map(DumVigletTenantStore::toRef)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VigletTenantRef> ownedTenantOf(String username) {
        return dumTenantMembershipRepository.findByUsername(username).stream()
                .filter(m -> m.isActive() && m.getRole() == DumTenantRole.OWNER)
                .map(DumTenantMembership::getTenant)
                .map(DumVigletTenantStore::toRef)
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public int membershipCount(String tenantId) {
        return (int) dumTenantMembershipRepository.countByTenant_Name(tenantId);
    }

    // --- writes ------------------------------------------------------------

    @Override
    @Transactional
    public VigletTenantRef createTenant(String slug, String name, String plan) {
        DumTenant tenant = new DumTenant();
        tenant.setName(slug); // Dumont's name is the discriminator (== slug)
        tenant.setDescription(name);
        tenant.setEnabled(true);
        tenant.setStatus(DumTenantStatus.ACTIVE);
        tenant.setCreatedDate(Instant.now());
        if (plan != null) {
            tenant.setPlan(plan);
        }
        return toRef(dumTenantRepository.saveAndFlush(tenant));
    }

    @Override
    @Transactional
    public void addMembership(String tenantId, String username, VigletTenantRole role,
            VigletTenantMembershipStatus status) {
        DumTenant tenant = dumTenantRepository.findByName(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tenant: " + tenantId));
        DumTenantMembership membership = new DumTenantMembership();
        membership.setTenant(tenant);
        membership.setUsername(username);
        membership.setRole(toDumRole(role));
        membership.setStatus(toDumMembershipStatus(status));
        membership.setCreatedDate(Instant.now());
        dumTenantMembershipRepository.save(membership);
    }

    @Override
    @Transactional
    public void updateStatus(String tenantId, VigletTenantStatus status) {
        DumTenant tenant = dumTenantRepository.findByName(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tenant: " + tenantId));
        tenant.setStatus(toDumStatus(status));
        dumTenantRepository.saveAndFlush(tenant);
    }

    @Override
    @Transactional
    public int deleteTenant(String tenantId) {
        DumTenant tenant = dumTenantRepository.findByName(tenantId).orElse(null);
        if (tenant == null) {
            return 0;
        }
        int memberships = (int) dumTenantMembershipRepository.countByTenant_Name(tenantId);
        // Memberships FK the tenant row — remove them first.
        dumTenantMembershipRepository.deleteByTenant_Id(tenant.getId());
        dumTenantRepository.delete(tenant);
        return memberships;
    }

    // --- mapping -----------------------------------------------------------

    static VigletTenantRef toRef(DumTenant tenant) {
        return new VigletTenantRef(tenant.getName(), tenant.getName(), tenant.getName(),
                toVigletStatus(tenant.getStatus()), tenant.getPlan());
    }

    private static VigletTenantStatus toVigletStatus(DumTenantStatus status) {
        return status == DumTenantStatus.SUSPENDED
                ? VigletTenantStatus.SUSPENDED
                : VigletTenantStatus.ACTIVE;
    }

    private static DumTenantStatus toDumStatus(VigletTenantStatus status) {
        return status == VigletTenantStatus.SUSPENDED
                ? DumTenantStatus.SUSPENDED
                : DumTenantStatus.ACTIVE;
    }

    private static DumTenantRole toDumRole(VigletTenantRole role) {
        return switch (role) {
            case OWNER -> DumTenantRole.OWNER;
            case ADMIN -> DumTenantRole.ADMIN;
            case MEMBER -> DumTenantRole.MEMBER;
        };
    }

    private static DumTenantMembershipStatus toDumMembershipStatus(VigletTenantMembershipStatus status) {
        return switch (status) {
            case ACTIVE -> DumTenantMembershipStatus.ACTIVE;
            case INVITED -> DumTenantMembershipStatus.INVITED;
            case SUSPENDED -> DumTenantMembershipStatus.SUSPENDED;
        };
    }
}

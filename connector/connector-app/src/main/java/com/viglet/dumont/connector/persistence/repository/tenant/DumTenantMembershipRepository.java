/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.dumont.connector.persistence.repository.tenant;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.viglet.dumont.connector.persistence.model.tenant.DumTenantMembership;

/**
 * Repository for tenant memberships (T398 / §XIV.9). Like
 * {@link DumTenantRepository}, {@link DumTenantMembership} is not
 * {@code @TenantId}-scoped (it is queried before the request tenant is known),
 * so these see every tenant.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Repository
public interface DumTenantMembershipRepository extends JpaRepository<DumTenantMembership, String> {

    /** Join-fetches the tenant so callers can read it after the session closes. */
    @Query("select m from DumTenantMembership m join fetch m.tenant where m.username = :username")
    List<DumTenantMembership> findByUsername(@Param("username") String username);

    Optional<DumTenantMembership> findByTenant_IdAndUsername(String tenantId, String username);

    Optional<DumTenantMembership> findByTenant_NameAndUsername(String tenantName, String username);

    /** Count the memberships of a tenant by name — feeds the shared teardown dry-run plan. */
    long countByTenant_Name(String tenantName);

    /** Remove all memberships of a tenant — called by teardown before deleting the tenant row. */
    void deleteByTenant_Id(String tenantId);
}

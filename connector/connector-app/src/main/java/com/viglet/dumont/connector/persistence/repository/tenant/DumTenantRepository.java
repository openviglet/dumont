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
import org.springframework.stereotype.Repository;

import com.viglet.dumont.connector.persistence.model.tenant.DumTenant;

/**
 * Repository for the tenant catalog (T398 / §XIV.9). {@link DumTenant} is not
 * {@code @TenantId}-scoped, so these queries see every tenant.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Repository
public interface DumTenantRepository extends JpaRepository<DumTenant, String> {

    Optional<DumTenant> findByName(String name);

    boolean existsByName(String name);

    List<DumTenant> findAllByOrderByNameAsc();
}

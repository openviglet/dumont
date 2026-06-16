/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.dumont.connector.onstartup;

import java.time.Instant;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.dumont.connector.persistence.model.tenant.DumTenant;
import com.viglet.dumont.connector.persistence.model.tenant.DumTenantStatus;
import com.viglet.dumont.connector.persistence.repository.tenant.DumTenantRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Ensures the reserved default tenant ({@link DumTenant#DEFAULT_TENANT_NAME}) row
 * always exists in the catalog (T398 / §XIV.9). Runs on every boot so existing
 * installs gain the row when the {@code dum_tenant} table is first created.
 * Idempotent: a no-op once the row exists. {@link DumTenant} is not
 * {@code @TenantId}-scoped, so this is independent of the request tenant context.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
@Order(0)
@Slf4j
public class DumTenantOnStartup implements ApplicationRunner {

    private final DumTenantRepository dumTenantRepository;

    public DumTenantOnStartup(DumTenantRepository dumTenantRepository) {
        this.dumTenantRepository = dumTenantRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!dumTenantRepository.existsByName(DumTenant.DEFAULT_TENANT_NAME)) {
            DumTenant tenant = new DumTenant();
            tenant.setName(DumTenant.DEFAULT_TENANT_NAME);
            tenant.setDescription("Default single-tenant workspace");
            tenant.setEnabled(true);
            tenant.setStatus(DumTenantStatus.ACTIVE);
            tenant.setPlan("FREE");
            tenant.setOwner("system");
            tenant.setCreatedDate(Instant.now());
            dumTenantRepository.save(tenant);
            log.info("Seeded default tenant '{}'", DumTenant.DEFAULT_TENANT_NAME);
        }
    }
}

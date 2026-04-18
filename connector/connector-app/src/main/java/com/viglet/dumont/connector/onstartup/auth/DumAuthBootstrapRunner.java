/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.dumont.connector.onstartup.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the full auth bootstrap (roles → groups → admin user) before the
 * application starts serving requests. Ordered to execute before
 * {@link DumPrivilegeOnStartup} so the admin role exists.
 *
 * @since 2026.2.14
 */
@Slf4j
@Component
@Transactional
@Order(0)
public class DumAuthBootstrapRunner implements ApplicationRunner {

    private final DumRoleOnStartup dumRoleOnStartup;
    private final DumGroupOnStartup dumGroupOnStartup;
    private final DumUserOnStartup dumUserOnStartup;

    public DumAuthBootstrapRunner(DumRoleOnStartup dumRoleOnStartup,
                                  DumGroupOnStartup dumGroupOnStartup,
                                  DumUserOnStartup dumUserOnStartup) {
        this.dumRoleOnStartup = dumRoleOnStartup;
        this.dumGroupOnStartup = dumGroupOnStartup;
        this.dumUserOnStartup = dumUserOnStartup;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Bootstrapping authentication model ...");
        dumRoleOnStartup.createDefaultRows();
        dumGroupOnStartup.createDefaultRows();
        dumUserOnStartup.createDefaultRows();
        log.info("Authentication bootstrap complete.");
    }
}

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

import java.time.Instant;
import java.util.Collections;

import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.persistence.model.auth.DumUser;
import com.viglet.dumont.connector.persistence.repository.auth.DumGroupRepository;
import com.viglet.dumont.connector.persistence.repository.auth.DumUserRepository;

/**
 * Seeds the default (password-less) "admin" user. The password is set by
 * {@code /api/setup} on first access.
 *
 * @since 2026.2.14
 */
@Component
public class DumUserOnStartup {
    private final DumUserRepository dumUserRepository;
    private final DumGroupRepository dumGroupRepository;

    public DumUserOnStartup(DumUserRepository dumUserRepository, DumGroupRepository dumGroupRepository) {
        this.dumUserRepository = dumUserRepository;
        this.dumGroupRepository = dumGroupRepository;
    }

    public void createDefaultRows() {
        if (dumUserRepository.findAll().isEmpty()) {
            dumUserRepository.save(DumUser.builder()
                    .email("admin@localhost.local")
                    .firstName("Admin")
                    .lastLogin(Instant.now())
                    .lastName("Administrator")
                    .realm("default")
                    .username("admin")
                    .enabled(1)
                    .dumGroups(Collections.singletonList(dumGroupRepository.findByName("Administrator")))
                    .build());
        }
    }
}

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

import java.util.ArrayList;
import java.util.Collections;

import com.viglet.dumont.connector.persistence.model.auth.DumGroup;
import com.viglet.dumont.connector.persistence.model.auth.DumUser;
import com.viglet.dumont.connector.persistence.repository.auth.DumGroupRepository;
import com.viglet.dumont.connector.persistence.repository.auth.DumUserRepository;
import com.viglet.dumont.connector.properties.DumAuthConfigProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * When Keycloak is enabled, ensures the admin user identified by
 * {@code dumont.keycloak-admin-id} belongs to the Administrator group.
 *
 * @since 2026.2.14
 */
@Slf4j
@Component
@Transactional
@Order(20)
public class DumKeycloakAdminOnStartup implements ApplicationRunner {

    private static final String ADMINISTRATOR = "Administrator";

    private final DumAuthConfigProperties configProperties;
    private final DumUserRepository dumUserRepository;
    private final DumGroupRepository dumGroupRepository;

    public DumKeycloakAdminOnStartup(DumAuthConfigProperties configProperties,
                                     DumUserRepository dumUserRepository,
                                     DumGroupRepository dumGroupRepository) {
        this.configProperties = configProperties;
        this.dumUserRepository = dumUserRepository;
        this.dumGroupRepository = dumGroupRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!configProperties.isKeycloak()) return;
        String adminId = configProperties.getKeycloakAdminId();
        if (!StringUtils.hasText(adminId)) return;

        DumGroup adminGroup = dumGroupRepository.findByName(ADMINISTRATOR);
        if (adminGroup == null) {
            log.warn("Administrator group not found. Run initial setup first.");
            return;
        }

        DumUser user = dumUserRepository.findByUsername(adminId);
        if (user == null) {
            user = DumUser.builder()
                    .username(adminId)
                    .firstName("Keycloak")
                    .lastName("Admin")
                    .realm("keycloak")
                    .enabled(1)
                    .dumGroups(Collections.singletonList(adminGroup))
                    .build();
            dumUserRepository.save(user);
            log.info("Created Keycloak admin user '{}' with Administrator group.", adminId);
        } else {
            var groups = dumGroupRepository.findByDumUsersContaining(user);
            boolean hasAdmin = groups.stream().anyMatch(g -> ADMINISTRATOR.equals(g.getName()));
            if (!hasAdmin) {
                var groupList = new ArrayList<>(groups);
                groupList.add(adminGroup);
                user.setDumGroups(groupList);
                dumUserRepository.save(user);
                log.info("Added Administrator group to Keycloak user '{}'.", adminId);
            }
        }
    }
}

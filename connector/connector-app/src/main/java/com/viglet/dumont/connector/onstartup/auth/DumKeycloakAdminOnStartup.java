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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

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

        DumGroup adminGroup = dumGroupRepository.findByName(ADMINISTRATOR);
        if (adminGroup == null) {
            log.warn("Administrator group not found. Run initial setup first.");
            return;
        }

        // Bootstrap admin + Client Silos admin-emails (deduped, blanks ignored).
        Set<String> adminIds = new LinkedHashSet<>();
        if (StringUtils.hasText(configProperties.getKeycloakAdminId())) {
            adminIds.add(configProperties.getKeycloakAdminId());
        }
        if (StringUtils.hasText(configProperties.getAdminEmails())) {
            Arrays.stream(configProperties.getAdminEmails().split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .forEach(adminIds::add);
        }

        for (String adminId : adminIds) {
            ensureAdministrator(adminId, adminGroup);
        }
    }

    /** Idempotently ensure {@code username} exists and is in the Administrator group. */
    private void ensureAdministrator(String username, DumGroup adminGroup) {
        DumUser user = dumUserRepository.findByUsername(username);
        if (user == null) {
            user = DumUser.builder()
                    .username(username)
                    .firstName("Keycloak")
                    .lastName("Admin")
                    .realm("keycloak")
                    .enabled(1)
                    .dumGroups(Collections.singletonList(adminGroup))
                    .build();
            dumUserRepository.save(user);
            log.info("Created Keycloak admin user '{}' with Administrator group.", username);
        } else {
            var groups = dumGroupRepository.findByDumUsersContaining(user);
            boolean hasAdmin = groups.stream().anyMatch(g -> ADMINISTRATOR.equals(g.getName()));
            if (!hasAdmin) {
                var groupList = new ArrayList<>(groups);
                groupList.add(adminGroup);
                user.setDumGroups(groupList);
                dumUserRepository.save(user);
                log.info("Added Administrator group to Keycloak user '{}'.", username);
            }
        }
    }
}

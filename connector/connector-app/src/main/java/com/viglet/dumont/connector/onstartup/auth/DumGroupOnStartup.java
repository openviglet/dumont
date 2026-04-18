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

import java.util.Collections;

import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.persistence.model.auth.DumGroup;
import com.viglet.dumont.connector.persistence.model.auth.DumRole;
import com.viglet.dumont.connector.persistence.repository.auth.DumGroupRepository;
import com.viglet.dumont.connector.persistence.repository.auth.DumRoleRepository;

/**
 * Seeds the default "Administrator" and "User" groups.
 *
 * @since 2026.2.14
 */
@Component
public class DumGroupOnStartup {
    private final DumGroupRepository dumGroupRepository;
    private final DumRoleRepository dumRoleRepository;

    public DumGroupOnStartup(DumGroupRepository dumGroupRepository, DumRoleRepository dumRoleRepository) {
        this.dumGroupRepository = dumGroupRepository;
        this.dumRoleRepository = dumRoleRepository;
    }

    public void createDefaultRows() {
        if (dumGroupRepository.findAll().isEmpty()) {
            DumRole adminRole = dumRoleRepository.findByName("ROLE_ADMIN");
            DumGroup adminGroup = new DumGroup();
            adminGroup.setName("Administrator");
            adminGroup.setDescription("Administrator Group");
            adminGroup.setDumRoles(Collections.singletonList(adminRole));
            dumGroupRepository.save(adminGroup);

            DumRole userRole = dumRoleRepository.findByName("ROLE_USER");
            DumGroup userGroup = new DumGroup();
            userGroup.setName("User");
            userGroup.setDescription("User Group");
            userGroup.setDumRoles(Collections.singletonList(userRole));
            dumGroupRepository.save(userGroup);
        }
    }
}

/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.dumont.connector.config.security;

import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.persistence.model.auth.DumGroup;
import com.viglet.dumont.connector.persistence.model.auth.DumRole;
import com.viglet.dumont.connector.persistence.model.auth.DumUser;
import com.viglet.dumont.connector.persistence.repository.auth.DumGroupRepository;
import com.viglet.dumont.connector.persistence.repository.auth.DumPrivilegeRepository;
import com.viglet.dumont.connector.persistence.repository.auth.DumRoleRepository;
import com.viglet.dumont.connector.persistence.repository.auth.DumUserRepository;
import com.viglet.dumont.connector.properties.DumAuthConfigProperties;

/**
 * Resolves Spring Security authorities from the local database.
 * When permissions are disabled, grants ROLE_ADMIN and all privileges.
 * When enabled, resolves user → groups → roles → privileges.
 *
 * @since 2026.2.14
 */
@Component
public class DumAuthorityResolver {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final DumUserRepository dumUserRepository;
    private final DumGroupRepository dumGroupRepository;
    private final DumRoleRepository dumRoleRepository;
    private final DumPrivilegeRepository dumPrivilegeRepository;
    private final DumAuthConfigProperties configProperties;

    public DumAuthorityResolver(DumUserRepository dumUserRepository,
                                DumGroupRepository dumGroupRepository,
                                DumRoleRepository dumRoleRepository,
                                DumPrivilegeRepository dumPrivilegeRepository,
                                DumAuthConfigProperties configProperties) {
        this.dumUserRepository = dumUserRepository;
        this.dumGroupRepository = dumGroupRepository;
        this.dumRoleRepository = dumRoleRepository;
        this.dumPrivilegeRepository = dumPrivilegeRepository;
        this.configProperties = configProperties;
    }

    public void resolve(String username, Set<GrantedAuthority> authorities) {
        if (!configProperties.isPermissions()) {
            grantAllAuthorities(authorities);
        } else if (username != null) {
            resolveFromDatabase(username, authorities);
        }
    }

    private void grantAllAuthorities(Set<GrantedAuthority> authorities) {
        authorities.add(new SimpleGrantedAuthority(ROLE_ADMIN));
        dumPrivilegeRepository.findAll().forEach(p -> authorities.add(new SimpleGrantedAuthority(p.getName())));
    }

    private void resolveFromDatabase(String username, Set<GrantedAuthority> authorities) {
        DumUser dumUser = dumUserRepository.findByUsername(username);
        if (dumUser != null) {
            Set<DumGroup> groups = dumGroupRepository.findByDumUsersContaining(dumUser);
            for (DumGroup group : groups) {
                Set<DumRole> roles = dumRoleRepository.findByDumGroupsContaining(group);
                for (DumRole role : roles) {
                    authorities.add(new SimpleGrantedAuthority(role.getName()));
                    for (var privilege : role.getDumPrivileges()) {
                        authorities.add(new SimpleGrantedAuthority(privilege.getName()));
                    }
                }
            }
        }
    }
}

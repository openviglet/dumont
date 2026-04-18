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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.viglet.dumont.connector.persistence.model.auth.DumGroup;
import com.viglet.dumont.connector.persistence.model.auth.DumRole;
import com.viglet.dumont.connector.persistence.model.auth.DumUser;
import com.viglet.dumont.connector.persistence.repository.auth.DumGroupRepository;
import com.viglet.dumont.connector.persistence.repository.auth.DumPrivilegeRepository;
import com.viglet.dumont.connector.persistence.repository.auth.DumRoleRepository;
import com.viglet.dumont.connector.persistence.repository.auth.DumUserRepository;
import com.viglet.dumont.connector.properties.DumAuthConfigProperties;

/**
 * Loads user details for Spring Security authentication from the local database.
 *
 * @since 2026.2.14
 */
@Service("customUserDetailsService")
public class DumCustomUserDetailsService implements UserDetailsService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private final DumUserRepository dumUserRepository;
    private final DumRoleRepository dumRoleRepository;
    private final DumGroupRepository dumGroupRepository;
    private final DumPrivilegeRepository dumPrivilegeRepository;
    private final DumAuthConfigProperties configProperties;

    public DumCustomUserDetailsService(DumUserRepository dumUserRepository,
            DumRoleRepository dumRoleRepository,
            DumGroupRepository dumGroupRepository,
            DumPrivilegeRepository dumPrivilegeRepository,
            DumAuthConfigProperties configProperties) {
        this.dumUserRepository = dumUserRepository;
        this.dumRoleRepository = dumRoleRepository;
        this.dumGroupRepository = dumGroupRepository;
        this.dumPrivilegeRepository = dumPrivilegeRepository;
        this.configProperties = configProperties;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        DumUser dumUser = dumUserRepository.findByUsername(username);
        if (null == dumUser) {
            throw new UsernameNotFoundException("No user present with username: " + username);
        }
        List<String> authorities = configProperties.isPermissions() ? resolveAuthorities(dumUser) : allAuthorities();
        return new DumCustomUserDetails(dumUser, authorities);
    }

    private List<String> resolveAuthorities(DumUser dumUser) {
        Set<DumGroup> groups = dumGroupRepository.findByDumUsersContaining(dumUser);
        Set<DumRole> roles = new HashSet<>();
        for (DumGroup group : groups) {
            roles.addAll(dumRoleRepository.findByDumGroupsContaining(group));
        }
        List<String> authorities = new ArrayList<>();
        for (DumRole role : roles) {
            authorities.add(role.getName());
            for (var privilege : role.getDumPrivileges()) {
                authorities.add(privilege.getName());
            }
        }
        return authorities;
    }

    private List<String> allAuthorities() {
        List<String> authorities = new ArrayList<>();
        authorities.add(ROLE_ADMIN);
        dumPrivilegeRepository.findAll().forEach(p -> authorities.add(p.getName()));
        return authorities;
    }
}

/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.dumont.connector.api.auth;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.dumont.connector.bean.DumCurrentUser;
import com.viglet.dumont.connector.persistence.model.auth.DumGroup;
import com.viglet.dumont.connector.persistence.model.auth.DumPrivilege;
import com.viglet.dumont.connector.persistence.model.auth.DumUser;
import com.viglet.dumont.connector.persistence.repository.auth.DumGroupRepository;
import com.viglet.dumont.connector.persistence.repository.auth.DumPrivilegeRepository;
import com.viglet.dumont.connector.persistence.repository.auth.DumRoleRepository;
import com.viglet.dumont.connector.persistence.repository.auth.DumUserRepository;
import com.viglet.dumont.connector.properties.DumAuthConfigProperties;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Session-based login endpoint, mirroring {@code TurLoginAPI}. Consumed by the
 * standalone Dumont login form.
 *
 * @since 2026.2.14
 */
@RestController
@RequestMapping("/api/login")
@Tag(name = "Login", description = "Session-based authentication")
public class DumLoginAPI {

    private static final String ADMINISTRATOR = "Administrator";

    private final AuthenticationManager authenticationManager;
    private final DumUserRepository dumUserRepository;
    private final DumGroupRepository dumGroupRepository;
    private final DumRoleRepository dumRoleRepository;
    private final DumPrivilegeRepository dumPrivilegeRepository;
    private final DumAuthConfigProperties configProperties;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public DumLoginAPI(AuthenticationManager authenticationManager,
            DumUserRepository dumUserRepository,
            DumGroupRepository dumGroupRepository,
            DumRoleRepository dumRoleRepository,
            DumPrivilegeRepository dumPrivilegeRepository,
            DumAuthConfigProperties configProperties) {
        this.authenticationManager = authenticationManager;
        this.dumUserRepository = dumUserRepository;
        this.dumGroupRepository = dumGroupRepository;
        this.dumRoleRepository = dumRoleRepository;
        this.dumPrivilegeRepository = dumPrivilegeRepository;
        this.configProperties = configProperties;
    }

    public record LoginRequest(String username, String password) {}

    @PostMapping
    public ResponseEntity<DumCurrentUser> login(@RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute("SPRING_SECURITY_CONTEXT", context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        DumUser dumUser = dumUserRepository.findByUsername(request.username());
        boolean isAdmin = !configProperties.isPermissions();
        if (!isAdmin && dumUser.getDumGroups() != null) {
            for (DumGroup group : dumUser.getDumGroups()) {
                if (ADMINISTRATOR.equals(group.getName())) {
                    isAdmin = true;
                    break;
                }
            }
        }

        DumCurrentUser currentUser = new DumCurrentUser();
        currentUser.setUsername(dumUser.getUsername());
        currentUser.setFirstName(dumUser.getFirstName());
        currentUser.setLastName(dumUser.getLastName());
        currentUser.setAdmin(isAdmin);
        currentUser.setEmail(dumUser.getEmail());
        currentUser.setAvatarUrl(dumUser.getAvatarUrl());
        currentUser.setHasAvatar(dumUser.getAvatarUrl() != null);
        currentUser.setPrivileges(resolvePrivileges(dumUser));

        return ResponseEntity.ok(currentUser);
    }

    private List<String> resolvePrivileges(DumUser dumUser) {
        if (!configProperties.isPermissions()) {
            return dumPrivilegeRepository.findAll().stream()
                    .map(DumPrivilege::getName)
                    .toList();
        }
        var groups = dumGroupRepository.findByDumUsersContaining(dumUser);
        var privileges = new LinkedHashSet<String>();
        for (var group : groups) {
            for (var role : dumRoleRepository.findByDumGroupsContaining(group)) {
                for (var privilege : role.getDumPrivileges()) {
                    privileges.add(privilege.getName());
                }
            }
        }
        return new ArrayList<>(privileges);
    }
}

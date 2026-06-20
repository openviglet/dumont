/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.viglet.dumont.connector.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.viglet.dumont.connector.bean.DumCurrentUser;
import com.viglet.dumont.connector.persistence.dto.auth.DumUserDto;
import com.viglet.dumont.connector.persistence.mapper.auth.DumUserMapper;
import com.viglet.dumont.connector.persistence.model.auth.DumUser;
import com.viglet.dumont.connector.persistence.repository.auth.DumGroupRepository;
import com.viglet.dumont.connector.persistence.repository.auth.DumPrivilegeRepository;
import com.viglet.dumont.connector.persistence.repository.auth.DumRoleRepository;
import com.viglet.dumont.connector.persistence.repository.auth.DumUserRepository;
import com.viglet.dumont.connector.properties.DumAuthConfigProperties;
import com.viglet.dumont.connector.properties.DumAuthenticationProperty;

@ExtendWith(MockitoExtension.class)
class DumUserAPITest {

    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private DumUserRepository userRepository;
    @Mock
    private DumGroupRepository groupRepository;
    @Mock
    private DumRoleRepository roleRepository;
    @Mock
    private DumAuthConfigProperties configProperties;
    @Mock
    private DumPrivilegeRepository privilegeRepository;

    private final DumUserMapper userMapper = new DumUserMapper();
    private DumUserAPI api;

    @BeforeEach
    void setUp() {
        api = new DumUserAPI(passwordEncoder, userRepository, groupRepository, roleRepository,
                configProperties, userMapper, privilegeRepository);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(String username, boolean admin) {
        var authorities = admin ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                : AuthorityUtils.NO_AUTHORITIES;
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, authorities));
        SecurityContextHolder.setContext(context);
    }

    @Test
    void listDelegatesToMapper() {
        when(userRepository.findAll()).thenReturn(List.of(
                DumUser.builder().username("alice").build()));
        assertThat(api.dumUserList()).hasSize(1);
    }

    @Test
    void currentReturnsNullWhenUnauthenticated() {
        assertThat(api.dumUserCurrent()).isNull();
    }

    @Test
    void currentReturnsNullForAnonymous() {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new AnonymousAuthenticationToken("k", "anonymous",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        SecurityContextHolder.setContext(context);
        assertThat(api.dumUserCurrent()).isNull();
    }

    @Test
    void currentReturnsRegularUserProfile() {
        authenticate("alice", false);
        when(configProperties.isPermissions()).thenReturn(false);
        when(userRepository.findByUsername("alice")).thenReturn(
                DumUser.builder().username("alice").firstName("Alice").avatarUrl("/a.png").build());
        when(privilegeRepository.findAll()).thenReturn(List.of());

        DumCurrentUser current = api.dumUserCurrent();

        assertThat(current.getUsername()).isEqualTo("alice");
        assertThat(current.isAdmin()).isTrue();
        assertThat(current.isHasAvatar()).isTrue();
    }

    @Test
    void editForbiddenForOtherUserWhenNotAdmin() {
        authenticate("bob", false);
        assertThat(api.dumUserEdit("alice").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void editReturnsUserWhenAdmin() {
        authenticate("admin", true);
        when(userRepository.findByUsername("alice"))
                .thenReturn(DumUser.builder().username("alice").build());
        when(groupRepository.findByDumUsersContaining(any())).thenReturn(java.util.Set.of());

        assertThat(api.dumUserEdit("alice").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void updateAppliesEditableFieldsForSelf() {
        authenticate("alice", false);
        DumUser existing = DumUser.builder().username("alice").build();
        when(userRepository.findByUsername("alice")).thenReturn(existing);
        DumUserDto dto = new DumUserDto();
        dto.setFirstName("Alicia");
        dto.setPassword("newpass");
        when(passwordEncoder.encode("newpass")).thenReturn("hashed");

        api.dumUserUpdate("alice", dto);

        assertThat(existing.getFirstName()).isEqualTo("Alicia");
        assertThat(existing.getPassword()).isEqualTo("hashed");
        verify(userRepository).save(existing);
    }

    @Test
    void deleteRefusesAdminAccountButRemovesOthers() {
        assertThat(api.dumUserDelete("admin")).isFalse();
        verify(userRepository, never()).deleteByUsername("admin");

        assertThat(api.dumUserDelete("bob")).isTrue();
        verify(userRepository).deleteByUsername("bob");
    }

    @Test
    void addEncodesPasswordAndSaves() {
        DumUserDto dto = new DumUserDto();
        dto.setUsername("carol");
        dto.setPassword("pw");
        when(passwordEncoder.encode("pw")).thenReturn("hashed");

        api.dumUserAdd(dto);

        verify(userRepository).save(any(DumUser.class));
    }

    @Test
    void registerRejectedWhenSelfRegistrationDisabled() {
        when(configProperties.getAuthentication()).thenReturn(null);
        var response = api.dumUserRegister(
                new DumUserAPI.RegisterRequest("u", "p", "f", "l", "e@x.invalid"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void registerRejectsExistingUsername() {
        DumAuthenticationProperty auth = new DumAuthenticationProperty();
        auth.setNewUser(true);
        when(configProperties.getAuthentication()).thenReturn(auth);
        when(userRepository.findByUsername("u"))
                .thenReturn(DumUser.builder().username("u").build());

        var response = api.dumUserRegister(
                new DumUserAPI.RegisterRequest("u", "p", "f", "l", "e@x.invalid"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void registerCreatesNewUser() {
        DumAuthenticationProperty auth = new DumAuthenticationProperty();
        auth.setNewUser(true);
        when(configProperties.getAuthentication()).thenReturn(auth);
        when(userRepository.findByUsername("newbie")).thenReturn(null);
        when(passwordEncoder.encode("pw")).thenReturn("hashed");
        when(groupRepository.findByName("User")).thenReturn(null);

        var response = api.dumUserRegister(
                new DumUserAPI.RegisterRequest("newbie", "pw", "New", "Bie", "n@x.invalid"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userRepository).save(any(DumUser.class));
    }

    @Test
    void structureReturnsEmptyDto() {
        assertThat(api.dumUserStructure()).isNotNull();
    }

    @Test
    void updateAvatarUrlForSelf() {
        authenticate("alice", false);
        when(userRepository.findByUsername("alice"))
                .thenReturn(DumUser.builder().username("alice").build());

        assertThat(api.updateAvatarUrl("alice", java.util.Map.of("avatarUrl", "/x.png"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userRepository).save(any(DumUser.class));
    }

    @Test
    void updateAvatarUrlNotFound() {
        authenticate("admin", true);
        when(userRepository.findByUsername("ghost")).thenReturn(null);
        assertThat(api.updateAvatarUrl("ghost", null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}

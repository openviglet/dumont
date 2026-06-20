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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.viglet.dumont.connector.bean.DumCurrentUser;
import com.viglet.dumont.connector.persistence.dto.auth.DumUserDto;
import com.viglet.dumont.connector.persistence.mapper.auth.DumUserMapper;
import com.viglet.dumont.connector.persistence.model.auth.DumGroup;
import com.viglet.dumont.connector.persistence.model.auth.DumPrivilege;
import com.viglet.dumont.connector.persistence.model.auth.DumRole;
import com.viglet.dumont.connector.persistence.model.auth.DumUser;
import com.viglet.dumont.connector.persistence.repository.auth.DumGroupRepository;
import com.viglet.dumont.connector.persistence.repository.auth.DumPrivilegeRepository;
import com.viglet.dumont.connector.persistence.repository.auth.DumRoleRepository;
import com.viglet.dumont.connector.persistence.repository.auth.DumUserRepository;
import com.viglet.dumont.connector.properties.DumAuthConfigProperties;

/**
 * Covers the OAuth2 sign-in path of {@link DumUserAPI} (upsert on first/return login, name
 * resolution, admin-via-group detection and permission-based privilege resolution).
 */
@ExtendWith(MockitoExtension.class)
class DumUserAPIOAuth2Test {

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

    private void authenticateOAuth2(OAuth2User user) {
        OAuth2AuthenticationToken token = mock(OAuth2AuthenticationToken.class);
        lenient().when(token.getPrincipal()).thenReturn(user);
        lenient().when(token.getAuthorizedClientRegistrationId()).thenReturn("keycloak");
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(token);
        SecurityContextHolder.setContext(context);
    }

    private OAuth2User oauthUser(String username, String given, String family, String name,
            String email, String picture) {
        OAuth2User user = mock(OAuth2User.class);
        lenient().when(user.getName()).thenReturn(username);
        lenient().when(user.<String>getAttribute("preferred_username")).thenReturn(username);
        lenient().when(user.<String>getAttribute("given_name")).thenReturn(given);
        lenient().when(user.<String>getAttribute("family_name")).thenReturn(family);
        lenient().when(user.<String>getAttribute("name")).thenReturn(name);
        lenient().when(user.<String>getAttribute("email")).thenReturn(email);
        lenient().when(user.<String>getAttribute("picture")).thenReturn(picture);
        return user;
    }

    @Test
    void oauth2FirstLoginCreatesUserWithGivenFamilyName() {
        authenticateOAuth2(oauthUser("alice", "Alice", "Liddell", null, "alice@x.invalid", "/a.png"));
        when(userRepository.findByUsername("alice")).thenReturn(null);
        when(configProperties.isPermissions()).thenReturn(false);
        when(privilegeRepository.findAll()).thenReturn(List.of(privilege("READ")));

        DumCurrentUser current = api.dumUserCurrent();

        assertThat(current.getUsername()).isEqualTo("alice");
        assertThat(current.getFirstName()).isEqualTo("Alice");
        assertThat(current.getLastName()).isEqualTo("Liddell");
        assertThat(current.isAdmin()).isTrue();
        assertThat(current.getRealm()).isEqualTo("keycloak");
        assertThat(current.getPrivileges()).containsExactly("READ");
        verify(userRepository).save(any(DumUser.class));
    }

    @Test
    void oauth2ReturningUserSplitsFullNameAndResolvesAdminViaGroup() {
        authenticateOAuth2(oauthUser("bob", null, null, "Bob Builder", "bob@x.invalid", null));
        DumUser existing = DumUser.builder().username("bob").build();
        when(userRepository.findByUsername("bob")).thenReturn(existing);
        when(configProperties.isPermissions()).thenReturn(true);
        DumGroup admins = new DumGroup();
        admins.setName("Administrator");
        when(groupRepository.findByDumUsersContaining(existing)).thenReturn(Set.of(admins));
        DumRole role = new DumRole();
        role.setDumPrivileges(List.of(privilege("MANAGE")));
        when(roleRepository.findByDumGroupsContaining(admins)).thenReturn(Set.of(role));

        DumCurrentUser current = api.dumUserCurrent();

        assertThat(current.getFirstName()).isEqualTo("Bob");
        assertThat(current.getLastName()).isEqualTo("Builder");
        assertThat(current.isAdmin()).isTrue();
        assertThat(current.getPrivileges()).containsExactly("MANAGE");
        assertThat(existing.getEmail()).isEqualTo("bob@x.invalid");
    }

    @Test
    void regularUserIsAdminWhenInAdministratorGroupWithPermissions() {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("carol", null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        SecurityContextHolder.setContext(context);

        DumGroup admins = new DumGroup();
        admins.setName("Administrator");
        DumUser carol = DumUser.builder().username("carol").build();
        carol.setDumGroups(List.of(admins));
        when(userRepository.findByUsername("carol")).thenReturn(carol);
        when(configProperties.isPermissions()).thenReturn(true);
        when(groupRepository.findByDumUsersContaining(carol)).thenReturn(Set.of(admins));
        when(roleRepository.findByDumGroupsContaining(admins)).thenReturn(Set.of());

        DumCurrentUser current = api.dumUserCurrent();

        assertThat(current.isAdmin()).isTrue();
        assertThat(current.getPrivileges()).isEmpty();
    }

    @Test
    void updateAssignsGroupsWhenCallerIsAdmin() {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        SecurityContextHolder.setContext(context);

        DumUser existing = DumUser.builder().username("dave").build();
        when(userRepository.findByUsername("dave")).thenReturn(existing);
        DumUserDto dto = new DumUserDto();
        DumGroup group = new DumGroup();
        group.setName("Editors");
        dto.setDumGroups(List.of(group));

        api.dumUserUpdate("dave", dto);

        assertThat(existing.getDumGroups()).extracting(DumGroup::getName).containsExactly("Editors");
        verify(userRepository).save(existing);
    }

    @Test
    void registerAssignsUserGroupWhenPresent() {
        var auth = new com.viglet.dumont.connector.properties.DumAuthenticationProperty();
        auth.setNewUser(true);
        when(configProperties.getAuthentication()).thenReturn(auth);
        when(userRepository.findByUsername("erin")).thenReturn(null);
        when(passwordEncoder.encode("pw")).thenReturn("hashed");
        DumGroup userGroup = new DumGroup();
        userGroup.setName("User");
        when(groupRepository.findByName("User")).thenReturn(userGroup);

        var response = api.dumUserRegister(
                new DumUserAPI.RegisterRequest("erin", "pw", "Erin", "R", "erin@x.invalid"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userRepository).save(any(DumUser.class));
    }

    @Test
    void registerRejectsMissingCredentials() {
        var auth = new com.viglet.dumont.connector.properties.DumAuthenticationProperty();
        auth.setNewUser(true);
        when(configProperties.getAuthentication()).thenReturn(auth);

        var response =
                api.dumUserRegister(new DumUserAPI.RegisterRequest("", "", null, null, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static DumPrivilege privilege(String name) {
        DumPrivilege privilege = new DumPrivilege();
        privilege.setName(name);
        return privilege;
    }
}

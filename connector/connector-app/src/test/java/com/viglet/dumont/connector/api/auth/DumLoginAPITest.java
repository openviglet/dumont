/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.viglet.dumont.connector.api.auth.DumLoginAPI.LoginRequest;
import com.viglet.dumont.connector.bean.DumCurrentUser;
import com.viglet.dumont.connector.persistence.model.auth.DumGroup;
import com.viglet.dumont.connector.persistence.model.auth.DumPrivilege;
import com.viglet.dumont.connector.persistence.model.auth.DumRole;
import com.viglet.dumont.connector.persistence.model.auth.DumUser;
import com.viglet.dumont.connector.persistence.repository.auth.DumGroupRepository;
import com.viglet.dumont.connector.persistence.repository.auth.DumPrivilegeRepository;
import com.viglet.dumont.connector.persistence.repository.auth.DumRoleRepository;
import com.viglet.dumont.connector.persistence.repository.auth.DumUserRepository;
import com.viglet.dumont.connector.properties.DumAuthConfigProperties;

@ExtendWith(MockitoExtension.class)
class DumLoginAPITest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private DumUserRepository userRepository;
    @Mock
    private DumGroupRepository groupRepository;
    @Mock
    private DumRoleRepository roleRepository;
    @Mock
    private DumPrivilegeRepository privilegeRepository;
    @Mock
    private DumAuthConfigProperties configProperties;

    private DumLoginAPI api;

    @BeforeEach
    void setUp() {
        api = new DumLoginAPI(authenticationManager, userRepository, groupRepository, roleRepository,
                privilegeRepository, configProperties);
        when(authenticationManager.authenticate(any())).thenReturn(mock(Authentication.class));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static DumUser user() {
        return DumUser.builder().username("alice").firstName("Alice").lastName("Smith")
                .email("alice@example.invalid").avatarUrl("/a.png").build();
    }

    private static DumPrivilege privilege(String name) {
        DumPrivilege privilege = new DumPrivilege();
        privilege.setName(name);
        return privilege;
    }

    @Test
    void loginWithPermissionsDisabledMakesAdminAndReturnsAllPrivileges() {
        when(configProperties.isPermissions()).thenReturn(false);
        when(userRepository.findByUsername("alice")).thenReturn(user());
        when(privilegeRepository.findAll()).thenReturn(List.of(privilege("READ")));

        DumCurrentUser result = api.login(new LoginRequest("alice", "pw"),
                new MockHttpServletRequest(), new MockHttpServletResponse()).getBody();

        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.isAdmin()).isTrue();
        assertThat(result.isHasAvatar()).isTrue();
        assertThat(result.getPrivileges()).containsExactly("READ");
    }

    @Test
    void loginWithPermissionsResolvesPrivilegesViaGroupsAndRoles() {
        when(configProperties.isPermissions()).thenReturn(true);
        DumGroup adminGroup = new DumGroup();
        adminGroup.setName("Administrator");
        DumUser user = user();
        user.setDumGroups(Set.of(adminGroup));
        when(userRepository.findByUsername("alice")).thenReturn(user);

        DumRole role = new DumRole();
        role.setDumPrivileges(Set.of(privilege("INDEX_WRITE")));
        when(groupRepository.findByDumUsersContaining(user)).thenReturn(Set.of(adminGroup));
        when(roleRepository.findByDumGroupsContaining(adminGroup)).thenReturn(Set.of(role));

        DumCurrentUser result = api.login(new LoginRequest("alice", "pw"),
                new MockHttpServletRequest(), new MockHttpServletResponse()).getBody();

        assertThat(result.isAdmin()).isTrue();
        assertThat(result.getPrivileges()).contains("INDEX_WRITE");
    }
}

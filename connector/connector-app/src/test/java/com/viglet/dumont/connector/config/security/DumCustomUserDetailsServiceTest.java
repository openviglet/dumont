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

package com.viglet.dumont.connector.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

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
class DumCustomUserDetailsServiceTest {

    @Mock
    private DumUserRepository userRepository;
    @Mock
    private DumRoleRepository roleRepository;
    @Mock
    private DumGroupRepository groupRepository;
    @Mock
    private DumPrivilegeRepository privilegeRepository;
    @Mock
    private DumAuthConfigProperties configProperties;

    private DumCustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new DumCustomUserDetailsService(userRepository, roleRepository, groupRepository,
                privilegeRepository, configProperties);
    }

    private static DumPrivilege privilege(String name) {
        DumPrivilege p = new DumPrivilege();
        p.setName(name);
        return p;
    }

    @Test
    void unknownUserThrows() {
        when(userRepository.findByUsername("ghost")).thenReturn(null);
        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void permissionsDisabledGrantsAdminAndAllPrivileges() {
        when(userRepository.findByUsername("alice"))
                .thenReturn(DumUser.builder().username("alice").password("x").enabled(1).build());
        when(configProperties.isPermissions()).thenReturn(false);
        when(privilegeRepository.findAll()).thenReturn(List.of(privilege("READ")));

        UserDetails details = service.loadUserByUsername("alice");

        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN", "READ");
    }

    @Test
    void permissionsEnabledResolvesFromGroupsAndRoles() {
        DumUser user = DumUser.builder().username("alice").password("x").enabled(1).build();
        when(userRepository.findByUsername("alice")).thenReturn(user);
        when(configProperties.isPermissions()).thenReturn(true);
        DumGroup group = new DumGroup();
        group.setName("Editors");
        DumRole role = new DumRole();
        role.setName("EDITOR");
        role.setDumPrivileges(Set.of(privilege("INDEX_WRITE")));
        when(groupRepository.findByDumUsersContaining(user)).thenReturn(Set.of(group));
        when(roleRepository.findByDumGroupsContaining(group)).thenReturn(Set.of(role));

        UserDetails details = service.loadUserByUsername("alice");

        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .contains("EDITOR", "INDEX_WRITE");
    }
}

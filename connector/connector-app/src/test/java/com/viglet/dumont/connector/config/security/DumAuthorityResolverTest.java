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
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;

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
class DumAuthorityResolverTest {

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

    private DumAuthorityResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DumAuthorityResolver(userRepository, groupRepository, roleRepository,
                privilegeRepository, configProperties);
    }

    private static DumPrivilege privilege(String name) {
        DumPrivilege p = new DumPrivilege();
        p.setName(name);
        return p;
    }

    private static Set<String> names(Set<GrantedAuthority> authorities) {
        Set<String> result = new HashSet<>();
        authorities.forEach(a -> result.add(a.getAuthority()));
        return result;
    }

    @Test
    void permissionsDisabledGrantsAdminAndAllPrivileges() {
        when(configProperties.isPermissions()).thenReturn(false);
        when(privilegeRepository.findAll()).thenReturn(List.of(privilege("READ")));
        Set<GrantedAuthority> authorities = new HashSet<>();

        resolver.resolve("alice", authorities);

        assertThat(names(authorities)).contains("ROLE_ADMIN", "READ");
    }

    @Test
    void permissionsEnabledResolvesFromDatabase() {
        when(configProperties.isPermissions()).thenReturn(true);
        DumUser user = DumUser.builder().username("alice").build();
        when(userRepository.findByUsername("alice")).thenReturn(user);
        DumGroup group = new DumGroup();
        group.setName("Editors");
        DumRole role = new DumRole();
        role.setName("EDITOR");
        role.setDumPrivileges(Set.of(privilege("INDEX_WRITE")));
        when(groupRepository.findByDumUsersContaining(user)).thenReturn(Set.of(group));
        when(roleRepository.findByDumGroupsContaining(group)).thenReturn(Set.of(role));
        Set<GrantedAuthority> authorities = new HashSet<>();

        resolver.resolve("alice", authorities);

        assertThat(names(authorities)).contains("EDITOR", "INDEX_WRITE");
    }

    @Test
    void permissionsEnabledWithNullUsernameAddsNothing() {
        when(configProperties.isPermissions()).thenReturn(true);
        Set<GrantedAuthority> authorities = new HashSet<>();

        resolver.resolve(null, authorities);

        assertThat(authorities).isEmpty();
    }

    @Test
    void permissionsEnabledWithUnknownUserAddsNothing() {
        when(configProperties.isPermissions()).thenReturn(true);
        when(userRepository.findByUsername("ghost")).thenReturn(null);
        Set<GrantedAuthority> authorities = new HashSet<>();

        resolver.resolve("ghost", authorities);

        assertThat(authorities).isEmpty();
    }
}

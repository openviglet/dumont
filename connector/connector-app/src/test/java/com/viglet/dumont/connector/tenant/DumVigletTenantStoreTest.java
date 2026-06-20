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

package com.viglet.dumont.connector.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.core.tenancy.VigletTenantMembershipStatus;
import com.viglet.core.tenancy.VigletTenantRole;
import com.viglet.core.tenancy.VigletTenantStatus;
import com.viglet.dumont.connector.persistence.model.tenant.DumTenant;
import com.viglet.dumont.connector.persistence.model.tenant.DumTenantMembership;
import com.viglet.dumont.connector.persistence.model.tenant.DumTenantMembershipStatus;
import com.viglet.dumont.connector.persistence.model.tenant.DumTenantRole;
import com.viglet.dumont.connector.persistence.model.tenant.DumTenantStatus;
import com.viglet.dumont.connector.persistence.repository.tenant.DumTenantMembershipRepository;
import com.viglet.dumont.connector.persistence.repository.tenant.DumTenantRepository;

@ExtendWith(MockitoExtension.class)
class DumVigletTenantStoreTest {

    @Mock
    private DumTenantRepository tenantRepository;
    @Mock
    private DumTenantMembershipRepository membershipRepository;

    private DumVigletTenantStore store;

    @BeforeEach
    void setUp() {
        store = new DumVigletTenantStore(tenantRepository, membershipRepository);
    }

    private static DumTenant tenant(String name) {
        DumTenant tenant = new DumTenant();
        tenant.setId(name + "-id");
        tenant.setName(name);
        tenant.setStatus(DumTenantStatus.ACTIVE);
        tenant.setPlan("free");
        return tenant;
    }

    private static DumTenantMembership membership(DumTenantRole role,
            DumTenantMembershipStatus status) {
        DumTenantMembership m = new DumTenantMembership();
        m.setTenant(tenant("acme"));
        m.setUsername("alice");
        m.setRole(role);
        m.setStatus(status);
        return m;
    }

    @Test
    void findByIdAndSlugMapToRef() {
        when(tenantRepository.findByName("acme")).thenReturn(Optional.of(tenant("acme")));
        assertThat(store.findById("acme")).isPresent();
        assertThat(store.findBySlug("acme")).isPresent();
    }

    @Test
    void findAllMapsEveryTenant() {
        when(tenantRepository.findAll()).thenReturn(List.of(tenant("a"), tenant("b")));
        assertThat(store.findAll()).hasSize(2);
    }

    @Test
    void isActiveMemberReflectsMembershipState() {
        when(membershipRepository.findByTenant_NameAndUsername("acme", "alice"))
                .thenReturn(Optional.of(membership(DumTenantRole.MEMBER,
                        DumTenantMembershipStatus.ACTIVE)));
        assertThat(store.isActiveMember("acme", "alice")).isTrue();

        when(membershipRepository.findByTenant_NameAndUsername("acme", "bob"))
                .thenReturn(Optional.empty());
        assertThat(store.isActiveMember("acme", "bob")).isFalse();
    }

    @Test
    void isOwnedByChecksOwnerRole() {
        when(membershipRepository.findByTenant_NameAndUsername("acme", "alice"))
                .thenReturn(Optional.of(membership(DumTenantRole.OWNER,
                        DumTenantMembershipStatus.ACTIVE)));
        assertThat(store.isOwnedBy("acme", "alice")).isTrue();

        when(membershipRepository.findByTenant_NameAndUsername("acme", "bob"))
                .thenReturn(Optional.of(membership(DumTenantRole.MEMBER,
                        DumTenantMembershipStatus.ACTIVE)));
        assertThat(store.isOwnedBy("acme", "bob")).isFalse();
    }

    @Test
    void activeAndOwnedTenantsOfFilterByState() {
        when(membershipRepository.findByUsername("alice")).thenReturn(List.of(
                membership(DumTenantRole.OWNER, DumTenantMembershipStatus.ACTIVE)));
        assertThat(store.activeTenantsOf("alice")).hasSize(1);
        assertThat(store.ownedTenantOf("alice")).isPresent();
    }

    @Test
    void membershipCountDelegates() {
        when(membershipRepository.countByTenant_Name("acme")).thenReturn(3L);
        assertThat(store.membershipCount("acme")).isEqualTo(3);
    }

    @Test
    void createTenantPersistsAndReturnsRef() {
        when(tenantRepository.saveAndFlush(any(DumTenant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        assertThat(store.createTenant("acme", "ACME Inc", "pro")).isNotNull();
        verify(tenantRepository).saveAndFlush(any(DumTenant.class));
    }

    @Test
    void addMembershipRequiresKnownTenant() {
        when(tenantRepository.findByName("acme")).thenReturn(Optional.of(tenant("acme")));
        store.addMembership("acme", "alice", VigletTenantRole.ADMIN,
                VigletTenantMembershipStatus.ACTIVE);
        verify(membershipRepository).save(any(DumTenantMembership.class));

        when(tenantRepository.findByName("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> store.addMembership("ghost", "alice", VigletTenantRole.MEMBER,
                VigletTenantMembershipStatus.INVITED)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateStatusRequiresKnownTenant() {
        when(tenantRepository.findByName("acme")).thenReturn(Optional.of(tenant("acme")));
        store.updateStatus("acme", VigletTenantStatus.SUSPENDED);
        verify(tenantRepository).saveAndFlush(any(DumTenant.class));

        when(tenantRepository.findByName("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> store.updateStatus("ghost", VigletTenantStatus.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteTenantRemovesMembershipsThenTenant() {
        DumTenant tenant = tenant("acme");
        when(tenantRepository.findByName("acme")).thenReturn(Optional.of(tenant));
        when(membershipRepository.countByTenant_Name("acme")).thenReturn(2L);

        assertThat(store.deleteTenant("acme")).isEqualTo(2);
        verify(membershipRepository).deleteByTenant_Id("acme-id");
        verify(tenantRepository).delete(tenant);
    }

    @Test
    void deleteUnknownTenantReturnsZero() {
        when(tenantRepository.findByName("ghost")).thenReturn(Optional.empty());
        assertThat(store.deleteTenant("ghost")).isZero();
    }
}

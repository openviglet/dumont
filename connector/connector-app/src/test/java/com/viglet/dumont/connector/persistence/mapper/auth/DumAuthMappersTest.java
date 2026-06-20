/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.viglet.dumont.connector.persistence.mapper.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.persistence.dto.auth.DumGroupDto;
import com.viglet.dumont.connector.persistence.dto.auth.DumPrivilegeDto;
import com.viglet.dumont.connector.persistence.dto.auth.DumRoleDto;
import com.viglet.dumont.connector.persistence.model.auth.DumGroup;
import com.viglet.dumont.connector.persistence.model.auth.DumPrivilege;
import com.viglet.dumont.connector.persistence.model.auth.DumRole;

class DumAuthMappersTest {

    private final DumGroupMapper groupMapper = new DumGroupMapper();
    private final DumRoleMapper roleMapper = new DumRoleMapper();
    private final DumPrivilegeMapper privilegeMapper = new DumPrivilegeMapper();

    @Test
    void groupMapperRoundTripAndNulls() {
        DumGroup group = new DumGroup();
        group.setId("g1");
        group.setName("Editors");
        group.setDescription("content editors");

        DumGroupDto dto = groupMapper.toDto(group);
        assertThat(dto.getId()).isEqualTo("g1");
        assertThat(dto.getName()).isEqualTo("Editors");
        assertThat(groupMapper.toEntity(dto).getDescription()).isEqualTo("content editors");

        assertThat(groupMapper.toDto(null)).isNull();
        assertThat(groupMapper.toEntity(null)).isNull();
        assertThat(groupMapper.toDtoList(null)).isEmpty();
        assertThat(groupMapper.toDtoSet(null)).isEmpty();
        assertThat(groupMapper.toDtoList(List.of(group))).hasSize(1);
        assertThat(groupMapper.toDtoSet(Set.of(group))).hasSize(1);
    }

    @Test
    void roleMapperRoundTripAndNulls() {
        DumRole role = new DumRole();
        role.setId("r1");
        role.setName("ADMIN");
        role.setDescription("administrators");

        DumRoleDto dto = roleMapper.toDto(role);
        assertThat(dto.getName()).isEqualTo("ADMIN");
        assertThat(roleMapper.toEntity(dto).getId()).isEqualTo("r1");

        assertThat(roleMapper.toDto(null)).isNull();
        assertThat(roleMapper.toEntity(null)).isNull();
        assertThat(roleMapper.toDtoList(null)).isEmpty();
        assertThat(roleMapper.toDtoSet(null)).isEmpty();
        assertThat(roleMapper.toDtoList(List.of(role))).hasSize(1);
        assertThat(roleMapper.toDtoSet(Set.of(role))).hasSize(1);
    }

    @Test
    void privilegeMapperRoundTripAndNulls() {
        DumPrivilege privilege = new DumPrivilege();
        privilege.setId("p1");
        privilege.setName("INDEX_WRITE");
        privilege.setDescription("write to index");
        privilege.setCategory("indexing");

        DumPrivilegeDto dto = privilegeMapper.toDto(privilege);
        assertThat(dto.getCategory()).isEqualTo("indexing");
        assertThat(privilegeMapper.toEntity(dto).getName()).isEqualTo("INDEX_WRITE");

        assertThat(privilegeMapper.toDto(null)).isNull();
        assertThat(privilegeMapper.toEntity(null)).isNull();
        assertThat(privilegeMapper.toDtoList(null)).isEmpty();
        assertThat(privilegeMapper.toDtoSet(null)).isEmpty();
        assertThat(privilegeMapper.toDtoList(List.of(privilege))).hasSize(1);
        assertThat(privilegeMapper.toDtoSet(Set.of(privilege))).hasSize(1);
    }
}

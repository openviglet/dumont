/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.dumont.connector.persistence.mapper.auth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.persistence.dto.auth.DumRoleDto;
import com.viglet.dumont.connector.persistence.model.auth.DumRole;

/**
 * Manual mapper between {@link DumRole} and {@link DumRoleDto}.
 *
 * @since 2026.2.14
 */
@Component
public class DumRoleMapper {

    public DumRoleDto toDto(DumRole entity) {
        if (entity == null) return null;
        DumRoleDto dto = new DumRoleDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setDumPrivileges(entity.getDumPrivileges());
        return dto;
    }

    public DumRole toEntity(DumRoleDto dto) {
        if (dto == null) return null;
        DumRole entity = new DumRole();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setDumPrivileges(dto.getDumPrivileges());
        return entity;
    }

    public List<DumRoleDto> toDtoList(List<DumRole> entities) {
        List<DumRoleDto> result = new ArrayList<>();
        if (entities != null) {
            for (DumRole r : entities) result.add(toDto(r));
        }
        return result;
    }

    public Set<DumRoleDto> toDtoSet(Set<DumRole> entities) {
        Set<DumRoleDto> result = new HashSet<>();
        if (entities != null) {
            for (DumRole r : entities) result.add(toDto(r));
        }
        return result;
    }
}

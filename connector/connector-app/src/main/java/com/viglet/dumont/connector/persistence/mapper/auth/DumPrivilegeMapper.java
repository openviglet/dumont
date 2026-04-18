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

import com.viglet.dumont.connector.persistence.dto.auth.DumPrivilegeDto;
import com.viglet.dumont.connector.persistence.model.auth.DumPrivilege;

/**
 * Manual mapper between {@link DumPrivilege} and {@link DumPrivilegeDto}.
 *
 * @since 2026.2.14
 */
@Component
public class DumPrivilegeMapper {

    public DumPrivilegeDto toDto(DumPrivilege entity) {
        if (entity == null) return null;
        DumPrivilegeDto dto = new DumPrivilegeDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setCategory(entity.getCategory());
        return dto;
    }

    public DumPrivilege toEntity(DumPrivilegeDto dto) {
        if (dto == null) return null;
        DumPrivilege entity = new DumPrivilege();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setCategory(dto.getCategory());
        return entity;
    }

    public List<DumPrivilegeDto> toDtoList(List<DumPrivilege> entities) {
        List<DumPrivilegeDto> result = new ArrayList<>();
        if (entities != null) {
            for (DumPrivilege p : entities) result.add(toDto(p));
        }
        return result;
    }

    public Set<DumPrivilegeDto> toDtoSet(Set<DumPrivilege> entities) {
        Set<DumPrivilegeDto> result = new HashSet<>();
        if (entities != null) {
            for (DumPrivilege p : entities) result.add(toDto(p));
        }
        return result;
    }
}

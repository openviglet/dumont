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

import com.viglet.dumont.connector.persistence.dto.auth.DumGroupDto;
import com.viglet.dumont.connector.persistence.model.auth.DumGroup;

/**
 * Manual mapper between {@link DumGroup} and {@link DumGroupDto}.
 *
 * @since 2026.2.14
 */
@Component
public class DumGroupMapper {

    public DumGroupDto toDto(DumGroup entity) {
        if (entity == null) return null;
        DumGroupDto dto = new DumGroupDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setDumRoles(entity.getDumRoles());
        dto.setDumUsers(entity.getDumUsers());
        return dto;
    }

    public DumGroup toEntity(DumGroupDto dto) {
        if (dto == null) return null;
        DumGroup entity = new DumGroup();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setDumRoles(dto.getDumRoles());
        entity.setDumUsers(dto.getDumUsers());
        return entity;
    }

    public List<DumGroupDto> toDtoList(List<DumGroup> entities) {
        List<DumGroupDto> result = new ArrayList<>();
        if (entities != null) {
            for (DumGroup g : entities) result.add(toDto(g));
        }
        return result;
    }

    public Set<DumGroupDto> toDtoSet(Set<DumGroup> entities) {
        Set<DumGroupDto> result = new HashSet<>();
        if (entities != null) {
            for (DumGroup g : entities) result.add(toDto(g));
        }
        return result;
    }
}

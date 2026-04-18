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

import com.viglet.dumont.connector.persistence.dto.auth.DumUserDto;
import com.viglet.dumont.connector.persistence.model.auth.DumUser;

/**
 * Manual mapper between {@link DumUser} and {@link DumUserDto}.
 *
 * @since 2026.2.14
 */
@Component
public class DumUserMapper {

    public DumUserDto toDto(DumUser entity) {
        if (entity == null) return null;
        DumUserDto dto = new DumUserDto();
        dto.setUsername(entity.getUsername());
        dto.setEmail(entity.getEmail());
        dto.setFirstName(entity.getFirstName());
        dto.setLastName(entity.getLastName());
        dto.setLastLogin(entity.getLastLogin());
        dto.setPassword(entity.getPassword());
        dto.setRealm(entity.getRealm());
        dto.setEnabled(entity.getEnabled());
        dto.setAvatarUrl(entity.getAvatarUrl());
        dto.setDumGroups(entity.getDumGroups());
        return dto;
    }

    public DumUser toEntity(DumUserDto dto) {
        if (dto == null) return null;
        DumUser entity = new DumUser();
        entity.setUsername(dto.getUsername());
        entity.setEmail(dto.getEmail());
        entity.setFirstName(dto.getFirstName());
        entity.setLastName(dto.getLastName());
        entity.setLastLogin(dto.getLastLogin());
        entity.setPassword(dto.getPassword());
        entity.setRealm(dto.getRealm());
        entity.setEnabled(dto.getEnabled());
        entity.setAvatarUrl(dto.getAvatarUrl());
        entity.setDumGroups(dto.getDumGroups());
        return entity;
    }

    public List<DumUserDto> toDtoList(List<DumUser> entities) {
        List<DumUserDto> result = new ArrayList<>();
        if (entities != null) {
            for (DumUser u : entities) result.add(toDto(u));
        }
        return result;
    }

    public Set<DumUserDto> toDtoSet(Set<DumUser> entities) {
        Set<DumUserDto> result = new HashSet<>();
        if (entities != null) {
            for (DumUser u : entities) result.add(toDto(u));
        }
        return result;
    }
}

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

import com.viglet.dumont.connector.persistence.dto.auth.DumUserDto;
import com.viglet.dumont.connector.persistence.model.auth.DumUser;

class DumUserMapperTest {

    private final DumUserMapper mapper = new DumUserMapper();

    private static DumUser sampleUser() {
        DumUser user = new DumUser();
        user.setUsername("tester");
        user.setEmail("tester@example.invalid");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setRealm("dumont");
        user.setEnabled(1);
        user.setAvatarUrl("/avatar.png");
        return user;
    }

    @Test
    void toDtoMapsAllScalarFields() {
        DumUserDto dto = mapper.toDto(sampleUser());

        assertThat(dto.getUsername()).isEqualTo("tester");
        assertThat(dto.getEmail()).isEqualTo("tester@example.invalid");
        assertThat(dto.getFirstName()).isEqualTo("Test");
        assertThat(dto.getRealm()).isEqualTo("dumont");
        assertThat(dto.getEnabled()).isEqualTo(1);
    }

    @Test
    void toEntityMapsAllScalarFields() {
        DumUserDto dto = mapper.toDto(sampleUser());
        DumUser entity = mapper.toEntity(dto);

        assertThat(entity.getUsername()).isEqualTo("tester");
        assertThat(entity.getLastName()).isEqualTo("User");
        assertThat(entity.getAvatarUrl()).isEqualTo("/avatar.png");
    }

    @Test
    void nullInputsMapToNull() {
        assertThat(mapper.toDto(null)).isNull();
        assertThat(mapper.toEntity(null)).isNull();
    }

    @Test
    void listAndSetMappingHandleNullAndElements() {
        assertThat(mapper.toDtoList(null)).isEmpty();
        assertThat(mapper.toDtoSet(null)).isEmpty();

        assertThat(mapper.toDtoList(List.of(sampleUser()))).singleElement()
                .satisfies(d -> assertThat(d.getUsername()).isEqualTo("tester"));
        assertThat(mapper.toDtoSet(Set.of(sampleUser()))).hasSize(1);
    }
}

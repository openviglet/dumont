/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.dumont.connector.service.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Lightweight projection of a Keycloak group returned by the Admin REST API.
 *
 * @since 2026.2.14
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DumKeycloakGroupDto(
        String id,
        String name,
        String path,
        List<String> realmRoles,
        List<DumKeycloakGroupDto> subGroups,
        List<DumKeycloakUserDto> members
) {
    public DumKeycloakGroupDto withMembers(List<DumKeycloakUserDto> newMembers) {
        return new DumKeycloakGroupDto(id, name, path, realmRoles, subGroups, newMembers);
    }
}

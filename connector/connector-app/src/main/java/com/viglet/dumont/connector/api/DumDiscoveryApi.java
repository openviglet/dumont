/*
 *
 * Copyright (C) 2016-2025 the original author or authors.
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

package com.viglet.dumont.connector.api;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Discovery endpoint — exposes runtime feature flags the frontend needs to
 * decide how to render (e.g. the Viglet app switcher only appears in
 * multi-tenant deployments).
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v2/discovery")
@Tag(name = "Discovery API", description = "Runtime feature flags for the frontend")
public class DumDiscoveryApi {

    @Value("${dumont.multi-tenant:false}")
    private boolean multiTenant;

    @GetMapping
    public Map<String, Object> info() {
        return Map.of(
                "product", "Viglet Dumont DEP",
                "multiTenant", multiTenant);
    }
}

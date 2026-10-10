/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.dumont.connector.api.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.dumont.connector.service.auth.DumAdminPasswordService;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * First-access setup endpoint. Allows setting the admin password when it has
 * not been configured yet. Once the admin password is set, the mutation
 * endpoint returns 403. Mirrors {@code TurSetupAPI}; the password rules live in
 * {@link DumAdminPasswordService}, shared with the {@code DUMONT_ADMIN_PASSWORD}
 * boot bootstrap (D93).
 *
 * @since 2026.2.14
 */
@RestController
@RequestMapping("/api/setup")
@Tag(name = "Setup", description = "First-access setup")
public class DumSetupAPI {

    private final DumAdminPasswordService adminPasswordService;

    public DumSetupAPI(DumAdminPasswordService adminPasswordService) {
        this.adminPasswordService = adminPasswordService;
    }

    public record SetupStatus(boolean required) {}

    public record SetupRequest(String password) {}

    @GetMapping
    public ResponseEntity<SetupStatus> status() {
        return ResponseEntity.ok(new SetupStatus(adminPasswordService.isSetupRequired()));
    }

    @PostMapping
    public ResponseEntity<Void> setAdminPassword(@RequestBody SetupRequest request) {
        if (!adminPasswordService.isSetupRequired()) {
            return ResponseEntity.status(403).build();
        }
        try {
            adminPasswordService.setPassword(request.password());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok().build();
    }
}

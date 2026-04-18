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

import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.dumont.connector.persistence.model.auth.DumUser;
import com.viglet.dumont.connector.persistence.repository.auth.DumUserRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * First-access setup endpoint. Allows setting the admin password when it has
 * not been configured yet. Once the admin password is set, the mutation
 * endpoint returns 403. Mirrors {@code TurSetupAPI}.
 *
 * @since 2026.2.14
 */
@RestController
@RequestMapping("/api/setup")
@Tag(name = "Setup", description = "First-access setup")
public class DumSetupAPI {

    private static final String ADMIN = "admin";
    private static final int PASSWORD_MINIMUM_SIZE = 6;

    private final DumUserRepository dumUserRepository;
    private final PasswordEncoder passwordEncoder;

    public DumSetupAPI(DumUserRepository dumUserRepository, PasswordEncoder passwordEncoder) {
        this.dumUserRepository = dumUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public record SetupStatus(boolean required) {}

    public record SetupRequest(String password) {}

    @GetMapping
    public ResponseEntity<SetupStatus> status() {
        return ResponseEntity.ok(new SetupStatus(isSetupRequired()));
    }

    @PostMapping
    public ResponseEntity<Void> setAdminPassword(@RequestBody SetupRequest request) {
        if (!isSetupRequired()) {
            return ResponseEntity.status(403).build();
        }
        if (!StringUtils.hasText(request.password())
                || request.password().trim().length() < PASSWORD_MINIMUM_SIZE) {
            return ResponseEntity.badRequest().build();
        }
        DumUser admin = dumUserRepository.findByUsername(ADMIN);
        if (admin == null) {
            admin = DumUser.builder()
                    .username(ADMIN)
                    .firstName("Admin")
                    .lastName("Administrator")
                    .email("admin@localhost.local")
                    .realm("default")
                    .enabled(1)
                    .lastLogin(Instant.now())
                    .build();
        }
        admin.setPassword(passwordEncoder.encode(request.password().trim()));
        dumUserRepository.save(admin);
        return ResponseEntity.ok().build();
    }

    private boolean isSetupRequired() {
        DumUser admin = dumUserRepository.findByUsername(ADMIN);
        return admin == null || !StringUtils.hasText(admin.getPassword());
    }
}

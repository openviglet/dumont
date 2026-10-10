/*
 *
 * Copyright (C) 2016-2026 the original author or authors.
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

package com.viglet.dumont.connector.service.auth;

import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.dumont.connector.persistence.model.auth.DumUser;
import com.viglet.dumont.connector.persistence.repository.auth.DumUserRepository;
import com.viglet.dumont.connector.properties.DumAuthConfigProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Single owner of the local {@code admin} password (roadmap <strong>D93</strong>): the first-access
 * setup screen ({@code POST /api/setup}) and the headless startup bootstrap
 * ({@code DUMONT_ADMIN_PASSWORD}) both go through here, so they share the validation, the "only
 * while unset" rule and the encoder. Mirrors Turing's {@code TurAdminPasswordService}, so both
 * products start configured the same way.
 *
 * <p>
 * The bootstrap never overwrites a stored password: an operator who set it in the console keeps it,
 * and restarting a long-lived container that still carries the variable changes nothing.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class DumAdminPasswordService {

    public static final String ADMIN = "admin";
    public static final int PASSWORD_MINIMUM_SIZE = 6;

    private final DumUserRepository dumUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final DumAuthConfigProperties configProperties;

    public DumAdminPasswordService(DumUserRepository dumUserRepository, PasswordEncoder passwordEncoder,
            DumAuthConfigProperties configProperties) {
        this.dumUserRepository = dumUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.configProperties = configProperties;
    }

    /** {@code true} while the {@code admin} account has no usable password. */
    public boolean isSetupRequired() {
        DumUser admin = dumUserRepository.findByUsername(ADMIN);
        return admin == null || !StringUtils.hasText(admin.getPassword());
    }

    /**
     * Encodes and stores the {@code admin} password, creating the account when a fresh install has
     * not seeded it yet.
     *
     * @throws IllegalArgumentException when the password is blank or shorter than
     *                                  {@value #PASSWORD_MINIMUM_SIZE} characters
     */
    public void setPassword(String rawPassword) {
        String password = rawPassword == null ? null : rawPassword.trim();
        if (!StringUtils.hasText(password) || password.length() < PASSWORD_MINIMUM_SIZE) {
            throw new IllegalArgumentException(
                    "The admin password must be at least " + PASSWORD_MINIMUM_SIZE + " characters long.");
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
        admin.setPassword(passwordEncoder.encode(password));
        dumUserRepository.save(admin);
    }

    /**
     * Applies {@code dumont.admin.password} ({@code DUMONT_ADMIN_PASSWORD}) so a container or CI
     * start has a usable admin login and skips the setup screen. Idempotent and boot-safe: a blank
     * value, an already-configured password or a too-short value only logs, never fails startup.
     */
    public void bootstrapFromConfiguration() {
        String configured = configProperties.getAdmin() == null ? null
                : configProperties.getAdmin().getPassword();
        if (!StringUtils.hasText(configured)) {
            return;
        }
        if (!isSetupRequired()) {
            log.info("DUMONT_ADMIN_PASSWORD ignored: the admin password is already configured. "
                    + "Change it in the console (or clear the stored password) instead.");
            return;
        }
        if (configured.trim().length() < PASSWORD_MINIMUM_SIZE) {
            log.warn("DUMONT_ADMIN_PASSWORD ignored: the admin password must be at least {} characters long. "
                    + "Falling back to the first-access setup screen.", PASSWORD_MINIMUM_SIZE);
            return;
        }
        setPassword(configured);
        log.info("Admin password set from DUMONT_ADMIN_PASSWORD — the first-access setup screen is skipped.");
    }
}

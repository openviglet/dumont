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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.viglet.dumont.connector.persistence.model.auth.DumUser;
import com.viglet.dumont.connector.persistence.repository.auth.DumUserRepository;
import com.viglet.dumont.connector.properties.DumAuthConfigProperties;

/** The admin password from configuration (roadmap D93), applied only while none is stored. */
@ExtendWith(MockitoExtension.class)
class DumAdminPasswordServiceTest {

    @Mock
    private DumUserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private DumAuthConfigProperties properties;
    private DumAdminPasswordService service;

    @BeforeEach
    void setUp() {
        properties = new DumAuthConfigProperties();
        service = new DumAdminPasswordService(userRepository, passwordEncoder, properties);
    }

    @Test
    void theConfiguredPasswordLandsOnThePasswordlessSeededAdmin() {
        DumUser admin = DumUser.builder().username("admin").build();
        when(userRepository.findByUsername("admin")).thenReturn(admin);
        when(passwordEncoder.encode("s3cret-pass")).thenReturn("hashed");
        properties.getAdmin().setPassword("  s3cret-pass  ");

        service.bootstrapFromConfiguration();

        assertThat(admin.getPassword()).isEqualTo("hashed");
        verify(userRepository).save(admin);
        assertThat(service.isSetupRequired()).isFalse();
    }

    @Test
    void aStoredPasswordIsNeverOverwritten() {
        when(userRepository.findByUsername("admin"))
                .thenReturn(DumUser.builder().username("admin").password("set-in-console").build());
        properties.getAdmin().setPassword("s3cret-pass");

        service.bootstrapFromConfiguration();

        verify(userRepository, never()).save(any());
    }

    @Test
    void aTooShortOrBlankValueIsIgnoredWithoutFailingTheBoot() {
        properties.getAdmin().setPassword("123");
        when(userRepository.findByUsername("admin")).thenReturn(DumUser.builder().username("admin").build());
        service.bootstrapFromConfiguration();

        properties.getAdmin().setPassword("   ");
        service.bootstrapFromConfiguration();

        properties.getAdmin().setPassword(null);
        service.bootstrapFromConfiguration();

        verify(userRepository, never()).save(any());
    }

    @Test
    void setPasswordRefusesAShortPassword() {
        assertThatThrownBy(() -> service.setPassword("12345"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 6");
    }
}

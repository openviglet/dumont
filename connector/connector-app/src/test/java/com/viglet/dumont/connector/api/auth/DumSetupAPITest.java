/*
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

package com.viglet.dumont.connector.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.viglet.dumont.connector.api.auth.DumSetupAPI.SetupRequest;
import com.viglet.dumont.connector.persistence.model.auth.DumUser;
import com.viglet.dumont.connector.persistence.repository.auth.DumUserRepository;
import com.viglet.dumont.connector.properties.DumAuthConfigProperties;
import com.viglet.dumont.connector.service.auth.DumAdminPasswordService;

@ExtendWith(MockitoExtension.class)
class DumSetupAPITest {

    @Mock
    private DumUserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private DumSetupAPI api;

    @BeforeEach
    void setUp() {
        api = new DumSetupAPI(new DumAdminPasswordService(userRepository, passwordEncoder,
                new DumAuthConfigProperties()));
    }

    @Test
    void statusRequiredWhenNoAdmin() {
        when(userRepository.findByUsername("admin")).thenReturn(null);
        assertThat(api.status().getBody().required()).isTrue();
    }

    @Test
    void statusNotRequiredWhenAdminHasPassword() {
        when(userRepository.findByUsername("admin"))
                .thenReturn(DumUser.builder().username("admin").password("hash").build());
        assertThat(api.status().getBody().required()).isFalse();
    }

    @Test
    void setPasswordForbiddenWhenSetupNotRequired() {
        when(userRepository.findByUsername("admin"))
                .thenReturn(DumUser.builder().username("admin").password("hash").build());
        assertThat(api.setAdminPassword(new SetupRequest("longenough")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(userRepository, never()).save(any());
    }

    @Test
    void setPasswordRejectsTooShortPassword() {
        when(userRepository.findByUsername("admin")).thenReturn(null);
        assertThat(api.setAdminPassword(new SetupRequest("123")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void setPasswordCreatesAdminWhenAbsent() {
        when(userRepository.findByUsername("admin")).thenReturn(null);
        when(passwordEncoder.encode(any())).thenReturn("hashed");

        assertThat(api.setAdminPassword(new SetupRequest("secret123")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        verify(userRepository).save(any(DumUser.class));
    }

    @Test
    void setPasswordUpdatesExistingPasswordlessAdmin() {
        DumUser admin = DumUser.builder().username("admin").build();
        when(userRepository.findByUsername("admin")).thenReturn(admin);
        when(passwordEncoder.encode(any())).thenReturn("hashed");

        api.setAdminPassword(new SetupRequest("secret123"));

        assertThat(admin.getPassword()).isEqualTo("hashed");
        verify(userRepository).save(admin);
    }
}

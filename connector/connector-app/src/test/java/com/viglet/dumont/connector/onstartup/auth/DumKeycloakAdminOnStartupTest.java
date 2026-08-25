/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.viglet.dumont.connector.onstartup.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.persistence.model.auth.DumGroup;
import com.viglet.dumont.connector.persistence.model.auth.DumUser;
import com.viglet.dumont.connector.persistence.repository.auth.DumGroupRepository;
import com.viglet.dumont.connector.persistence.repository.auth.DumUserRepository;
import com.viglet.dumont.connector.properties.DumAuthConfigProperties;

@ExtendWith(MockitoExtension.class)
class DumKeycloakAdminOnStartupTest {

    @Mock
    private DumAuthConfigProperties configProperties;
    @Mock
    private DumUserRepository userRepository;
    @Mock
    private DumGroupRepository groupRepository;

    private DumKeycloakAdminOnStartup runner;

    @BeforeEach
    void setUp() {
        runner = new DumKeycloakAdminOnStartup(configProperties, userRepository, groupRepository);
    }

    private static DumGroup adminGroup() {
        DumGroup group = new DumGroup();
        group.setName("Administrator");
        return group;
    }

    @Test
    void doesNothingWhenKeycloakDisabled() {
        when(configProperties.isKeycloak()).thenReturn(false);
        runner.run(null);
        verify(userRepository, never()).save(any());
    }

    @Test
    void doesNothingWhenNoAdminIdConfigured() {
        when(configProperties.isKeycloak()).thenReturn(true);
        when(groupRepository.findByName("Administrator")).thenReturn(adminGroup());
        when(configProperties.getKeycloakAdminId()).thenReturn("  ");
        when(configProperties.getAdminEmails()).thenReturn("  ");
        runner.run(null);
        verify(userRepository, never()).save(any());
    }

    @Test
    void warnsAndStopsWhenNoAdministratorGroup() {
        when(configProperties.isKeycloak()).thenReturn(true);
        when(groupRepository.findByName("Administrator")).thenReturn(null);
        runner.run(null);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createsAdminUserWhenAbsent() {
        when(configProperties.isKeycloak()).thenReturn(true);
        when(configProperties.getKeycloakAdminId()).thenReturn("kc-admin");
        when(groupRepository.findByName("Administrator")).thenReturn(adminGroup());
        when(userRepository.findByUsername("kc-admin")).thenReturn(null);

        runner.run(null);

        verify(userRepository).save(any(DumUser.class));
    }

    @Test
    void addsAdministratorGroupToExistingUserMissingIt() {
        when(configProperties.isKeycloak()).thenReturn(true);
        when(configProperties.getKeycloakAdminId()).thenReturn("kc-admin");
        when(groupRepository.findByName("Administrator")).thenReturn(adminGroup());
        DumUser existing = DumUser.builder().username("kc-admin").build();
        when(userRepository.findByUsername("kc-admin")).thenReturn(existing);
        lenient().when(groupRepository.findByDumUsersContaining(existing)).thenReturn(Set.of());

        runner.run(null);

        verify(userRepository).save(existing);
    }
}

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

package com.viglet.dumont.connector.plugin.jobs.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.viglet.dumont.connector.plugin.jobs.api.dto.DumJobSourceRequest;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSource;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobSeenRepository;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobSourceRepository;

@ExtendWith(MockitoExtension.class)
class DumJobSourceApiTest {

    @Mock
    private DumJobSourceRepository repository;
    @Mock
    private DumJobSeenRepository seenRepository;

    private DumJobSourceApi api;

    @BeforeEach
    void setUp() {
        api = new DumJobSourceApi(repository, seenRepository);
    }

    private static DumJobSource withCredentials() {
        return DumJobSource.builder().id("a").name("adzuna-br").sourceType("adzuna")
                .appId("real-app-id").appKey("real-app-key").apiKey("real-api-key")
                .apiUrl("https://api.adzuna.com").turSNSites(new HashSet<>(List.of("trabalharei")))
                .build();
    }

    @Test
    void listAndGetMaskCredentials() {
        DumJobSource stored = withCredentials();
        when(repository.findAll()).thenReturn(List.of(stored));
        when(repository.findById("a")).thenReturn(Optional.of(stored));

        for (DumJobSource read : List.of(api.jobSourceList().getFirst(), api.jobSourceGet("a"))) {
            assertThat(read.getAppId()).isEqualTo(DumJobSourceApi.MASK);
            assertThat(read.getAppKey()).isEqualTo(DumJobSourceApi.MASK);
            assertThat(read.getApiKey()).isEqualTo(DumJobSourceApi.MASK);
            assertThat(read.getApiUrl()).isEqualTo("https://api.adzuna.com");
            assertThat(read.getName()).isEqualTo("adzuna-br");
            assertThat(read.getTurSNSites()).containsExactly("trabalharei");
        }
        // Masking works on a copy: the stored entity keeps the real keys.
        assertThat(stored.getAppKey()).isEqualTo("real-app-key");
    }

    @Test
    void unsetCredentialsReadAsNullNotMask() {
        when(repository.findById("a"))
                .thenReturn(Optional.of(DumJobSource.builder().id("a").name("jooble").build()));
        DumJobSource read = api.jobSourceGet("a");
        assertThat(read.getApiKey()).isNull();
        assertThat(read.getAppKey()).isNull();
    }

    @Test
    void getMissingReturnsEmptyAndStructureDefaultsLocale() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThat(api.jobSourceGet("missing").getName()).isNull();
        assertThat(api.jobSourceStructure().getLocale()).isEqualTo("pt_BR");
    }

    @Test
    void updateWithMaskOrNullKeepsStoredCredentialsAndNewValueReplacesThem() {
        DumJobSource stored = withCredentials();
        when(repository.findById("a")).thenReturn(Optional.of(stored));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DumJobSourceRequest req = new DumJobSourceRequest();
        req.setName("adzuna-br");
        req.setSourceType("adzuna");
        req.setAppId(DumJobSourceApi.MASK);
        req.setAppKey(null);
        req.setApiKey("new-api-key");
        req.setCapPerDay(250);
        req.setEnabled(false);

        ResponseEntity<DumJobSource> response = api.jobSourceUpdate("a", req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stored.getAppId()).isEqualTo("real-app-id");
        assertThat(stored.getAppKey()).isEqualTo("real-app-key");
        assertThat(stored.getApiKey()).isEqualTo("new-api-key");
        assertThat(stored.getCapPerDay()).isEqualTo(250);
        assertThat(stored.isEnabled()).isFalse();
        assertThat(stored.getLocale()).isEqualTo("pt_BR");
        assertThat(response.getBody().getApiKey()).isEqualTo(DumJobSourceApi.MASK);
    }

    @Test
    void emptyStringClearsACredential() {
        DumJobSource stored = withCredentials();
        when(repository.findById("a")).thenReturn(Optional.of(stored));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DumJobSourceRequest req = new DumJobSourceRequest();
        req.setApiKey("");

        api.jobSourceUpdate("a", req);

        assertThat(stored.getApiKey()).isNull();
        assertThat(stored.getAppKey()).isEqualTo("real-app-key");
    }

    @Test
    void updateRejectsMismatchedIdAndReportsMissing() {
        DumJobSourceRequest req = new DumJobSourceRequest();
        req.setId("other");
        assertThat(api.jobSourceUpdate("a", req).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(repository, never()).save(any());

        when(repository.findById("gone")).thenReturn(Optional.empty());
        assertThat(api.jobSourceUpdate("gone", new DumJobSourceRequest()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createMasksTheEchoAndStoresTheKey() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DumJobSourceRequest req = new DumJobSourceRequest();
        req.setName("jsearch");
        req.setSourceType("jsearch");
        req.setApiKey("rapid-key");
        req.setTurSNSites(new HashSet<>(List.of("trabalharei")));

        DumJobSource created = api.jobSourceAdd(req);

        assertThat(created.getApiKey()).isEqualTo(DumJobSourceApi.MASK);
        assertThat(created.isEnabled()).isTrue();
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(
                saved -> "rapid-key".equals(saved.getApiKey())));
    }

    @Test
    void deleteDropsTheSeenHistory() {
        when(repository.findById("a")).thenReturn(Optional.of(withCredentials()));
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThat(api.jobSourceDelete("a")).isTrue();
        assertThat(api.jobSourceDelete("missing")).isFalse();
        verify(seenRepository).deleteBySourceId("a");
    }
}

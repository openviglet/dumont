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

package com.viglet.dumont.connector.plugin.jobs.api;

import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.dumont.connector.plugin.jobs.api.dto.DumJobSourceRequest;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSource;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobCallRepository;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobSeenRepository;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobSourceRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * CRUD for job sources (trabalharei <strong>TRA13</strong>). Every response is a detached copy with
 * the credentials replaced by {@link #MASK}: a key is written through this API but never read back.
 */
@RestController
@RequestMapping("/api/v2/jobs/source")
@Tag(name = "Job Source", description = "Job-board partner API source")
public class DumJobSourceApi {

    /** What a set credential reads as. Sending it back on update keeps the stored value. */
    public static final String MASK = "********";

    private final DumJobSourceRepository sourceRepository;
    private final DumJobSeenRepository seenRepository;
    private final DumJobCallRepository callRepository;

    public DumJobSourceApi(DumJobSourceRepository sourceRepository,
            DumJobSeenRepository seenRepository, DumJobCallRepository callRepository) {
        this.sourceRepository = sourceRepository;
        this.seenRepository = seenRepository;
        this.callRepository = callRepository;
    }

    @GetMapping
    @Operation(summary = "List all Job Sources")
    public List<DumJobSource> jobSourceList() {
        return sourceRepository.findAll().stream().map(DumJobSourceApi::masked).toList();
    }

    @GetMapping("/structure")
    @Operation(summary = "Job Source structure")
    public DumJobSource jobSourceStructure() {
        DumJobSource source = new DumJobSource();
        source.setLocale(DumJobSource.DEFAULT_LOCALE);
        return source;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Show a Job Source")
    public DumJobSource jobSourceGet(@PathVariable String id) {
        return sourceRepository.findById(id).map(DumJobSourceApi::masked).orElse(new DumJobSource());
    }

    @Transactional
    @PutMapping("/{id}")
    @Operation(summary = "Update a Job Source")
    public ResponseEntity<DumJobSource> jobSourceUpdate(@PathVariable String id,
            @RequestBody DumJobSourceRequest jobSource) {
        if (jobSource.getId() != null && !id.equals(jobSource.getId())) {
            return ResponseEntity.badRequest().build();
        }
        return sourceRepository.findById(id).map(existing -> {
            applyToEntity(existing, jobSource);
            return ResponseEntity.ok(masked(sourceRepository.save(existing)));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Transactional
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a Job Source")
    public boolean jobSourceDelete(@PathVariable String id) {
        return sourceRepository.findById(id).map(source -> {
            seenRepository.deleteBySourceId(source.getId());
            callRepository.deleteBySourceId(source.getId());
            sourceRepository.delete(source);
            return true;
        }).orElse(false);
    }

    @PostMapping
    @Operation(summary = "Create a Job Source")
    public DumJobSource jobSourceAdd(@RequestBody DumJobSourceRequest jobSource) {
        DumJobSource entity = new DumJobSource();
        applyToEntity(entity, jobSource);
        return masked(sourceRepository.save(entity));
    }

    private static void applyToEntity(DumJobSource existing, DumJobSourceRequest jobSource) {
        existing.setName(jobSource.getName());
        existing.setDescription(jobSource.getDescription());
        existing.setSourceType(jobSource.getSourceType());
        if (jobSource.getEnabled() != null) {
            existing.setEnabled(jobSource.getEnabled());
        }
        applyCredential(jobSource.getAppId(), existing::setAppId);
        applyCredential(jobSource.getAppKey(), existing::setAppKey);
        applyCredential(jobSource.getApiKey(), existing::setApiKey);
        existing.setApiUrl(jobSource.getApiUrl());
        existing.setLocale(jobSource.getLocale() == null || jobSource.getLocale().isBlank()
                ? DumJobSource.DEFAULT_LOCALE
                : jobSource.getLocale());
        existing.setCapPerMinute(jobSource.getCapPerMinute());
        existing.setCapPerDay(jobSource.getCapPerDay());
        existing.setCapPerWeek(jobSource.getCapPerWeek());
        existing.setCapPerMonth(jobSource.getCapPerMonth());
        existing.setSettings(jobSource.getSettings() == null || jobSource.getSettings().isBlank()
                ? null
                : jobSource.getSettings());
        if (existing.getTurSNSites() == null) {
            existing.setTurSNSites(new HashSet<>());
        }
        existing.getTurSNSites().clear();
        if (jobSource.getTurSNSites() != null) {
            existing.getTurSNSites().addAll(jobSource.getTurSNSites());
        }
    }

    /** {@code null} or the mask keeps the stored credential; an empty string clears it. */
    private static void applyCredential(String value, Consumer<String> setter) {
        if (value == null || MASK.equals(value)) {
            return;
        }
        setter.accept(value.isEmpty() ? null : value);
    }

    /** A detached copy of {@code source} whose set credentials read as {@link #MASK}. */
    static DumJobSource masked(DumJobSource source) {
        return source.toBuilder()
                .appId(mask(source.getAppId()))
                .appKey(mask(source.getAppKey()))
                .apiKey(mask(source.getApiKey()))
                .turSNSites(source.getTurSNSites() == null ? new HashSet<>()
                        : new HashSet<>(source.getTurSNSites()))
                .build();
    }

    private static String mask(String credential) {
        return credential == null || credential.isEmpty() ? null : MASK;
    }
}

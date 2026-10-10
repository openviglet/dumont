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

package com.viglet.dumont.connector.plugin.catalog.export;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.dumont.connector.plugin.catalog.persistence.model.DumCatalogSource;
import com.viglet.dumont.connector.plugin.catalog.persistence.repository.DumCatalogSourceRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Export / import of catalog-bridge sources as a flat JSON array, so a set of institution sources can
 * be provisioned (e.g. on first run) or moved between instances. Import upserts by source name.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class DumCatalogExchangeProcess {

    private static final String NAME = "name";
    private static final String DESCRIPTION = "description";
    private static final String CATALOG_URL = "catalogUrl";
    private static final String AUTH_HEADER_NAME = "authHeaderName";
    private static final String AUTH_HEADER_VALUE = "authHeaderValue";
    private static final String LOCALE = "locale";
    private static final String SN_SITES = "turSNSites";
    private static final String JSON_API_MANIFEST = "jsonApiManifest";
    private static final String JSON_API_SECRETS = "jsonApiSecrets";

    private final DumCatalogSourceRepository sourceRepository;

    public DumCatalogExchangeProcess(DumCatalogSourceRepository sourceRepository) {
        this.sourceRepository = sourceRepository;
    }

    /** Serialize every configured source as a JSON array. */
    public String exportJson() {
        JSONArray array = new JSONArray();
        for (DumCatalogSource source : sourceRepository.findAll()) {
            JSONObject json = new JSONObject();
            json.put(NAME, source.getName());
            json.put(DESCRIPTION, source.getDescription());
            json.put(CATALOG_URL, source.getCatalogUrl());
            json.put(AUTH_HEADER_NAME, source.getAuthHeaderName());
            json.put(AUTH_HEADER_VALUE, source.getAuthHeaderValue());
            json.put(LOCALE, source.getLocale());
            json.put(SN_SITES, new JSONArray(source.getTurSNSites()));
            // The manifest travels (D92); its secrets never do — an export is a file people share.
            if (source.getJsonApiManifest() != null && !source.getJsonApiManifest().isBlank()) {
                json.put(JSON_API_MANIFEST, asJson(source.getJsonApiManifest()));
            }
            array.put(json);
        }
        return array.toString(2);
    }

    @Transactional
    public void importFromFile(File file) {
        try {
            importFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.error("Failed to read catalog export file {}: {}", file, e.getMessage());
        }
    }

    @Transactional
    public void importFromMultipartFile(MultipartFile file) {
        try {
            importFromString(new String(file.getBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.error("Failed to read uploaded catalog export: {}", e.getMessage());
        }
    }

    /** Parse a JSON array of sources and upsert each by name; non-catalog payloads are ignored. */
    @Transactional
    public void importFromString(String payload) {
        String trimmed = payload == null ? "" : payload.trim();
        if (!trimmed.startsWith("[")) {
            return;
        }
        JSONArray array = new JSONArray(trimmed);
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            if (json == null || !(json.has(CATALOG_URL) || json.has(JSON_API_MANIFEST))) {
                continue;
            }
            upsert(json);
        }
    }

    private void upsert(JSONObject json) {
        String name = json.optString(NAME, null);
        if (name == null || name.isBlank()) {
            return;
        }
        DumCatalogSource source = sourceRepository.findByName(name).orElseGet(DumCatalogSource::new);
        source.setName(name);
        source.setDescription(json.optString(DESCRIPTION, source.getDescription()));
        source.setCatalogUrl(json.optString(CATALOG_URL, source.getCatalogUrl()));
        source.setAuthHeaderName(json.optString(AUTH_HEADER_NAME, source.getAuthHeaderName()));
        source.setAuthHeaderValue(json.optString(AUTH_HEADER_VALUE, source.getAuthHeaderValue()));
        source.setLocale(json.optString(LOCALE, source.getLocale()));
        source.setTurSNSites(siteNames(json.optJSONArray(SN_SITES)));
        // Either an embedded object or its text; secrets are accepted on import so a first-boot
        // provisioning file can carry them, though an export never writes them.
        if (json.has(JSON_API_MANIFEST)) {
            source.setJsonApiManifest(json.get(JSON_API_MANIFEST).toString());
        }
        if (json.has(JSON_API_SECRETS)) {
            source.setJsonApiSecrets(json.get(JSON_API_SECRETS).toString());
        }
        sourceRepository.save(source);
        log.info("Imported catalog source '{}'", name);
    }

    /** The manifest as a JSON object, so an export stays readable; its text when it does not parse. */
    private static Object asJson(String manifest) {
        try {
            return new JSONObject(manifest);
        } catch (JSONException e) {
            return manifest;
        }
    }

    private static Collection<String> siteNames(JSONArray array) {
        List<String> sites = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                sites.add(array.getString(i));
            }
        }
        return sites;
    }
}

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

package com.viglet.dumont.connector.plugin.catalog.api.dto;

import java.util.Collection;
import java.util.HashSet;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for {@code DumCatalogSourceApi} create/update (SonarQube S4684) — a plain POJO
 * mirroring the writable fields of {@code DumCatalogSource} so the persistent entity is never bound
 * directly from the request body. {@code migrationState} is intentionally omitted: the leg flip is
 * gated by the parity endpoints, never set through a plain update. {@code id} is accepted only to
 * validate the path variable on update.
 */
@Getter
@Setter
@NoArgsConstructor
public class DumCatalogSourceRequest {

    private String id;
    private String name;
    private String description;
    private String catalogUrl;
    private String authHeaderName;
    private String authHeaderValue;
    private String locale;
    private String leva;
    private String nativeRecipe;
    private String nativeListingUrl;
    private String jsonApiManifest;
    private String jsonApiSecrets;
    private Collection<String> turSNSites = new HashSet<>();
}

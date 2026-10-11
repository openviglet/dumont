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

package com.viglet.dumont.connector.plugin.jobs.api.dto;

import java.util.Collection;
import java.util.HashSet;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for {@code DumJobSourceApi} create/update (SonarQube S4684) — a plain POJO
 * mirroring the writable fields of {@code DumJobSource} so the persistent entity is never bound
 * directly from the request body. {@code id} is accepted only to validate the path variable on
 * update.
 *
 * <p>
 * Credentials ({@code appId}, {@code appKey}, {@code apiKey}) are write-only: a {@code null} value,
 * or the mask the API returns on reads, leaves the stored credential unchanged, so a form that
 * round-trips a masked source never overwrites the real key with the mask. An empty string clears it.
 */
@Getter
@Setter
@NoArgsConstructor
public class DumJobSourceRequest {

    private String id;
    private String name;
    private String description;
    private String sourceType;
    private Boolean enabled;
    private String appId;
    private String appKey;
    private String apiKey;
    private String apiUrl;
    private String locale;
    private Integer capPerMinute;
    private Integer capPerDay;
    private Integer capPerWeek;
    private Integer capPerMonth;
    private String settings;
    private Collection<String> turSNSites = new HashSet<>();
}

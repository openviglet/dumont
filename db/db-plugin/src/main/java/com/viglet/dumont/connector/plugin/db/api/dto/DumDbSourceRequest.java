/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.plugin.db.api.dto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import com.viglet.dumont.connector.plugin.db.persistence.model.DumDbField;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for {@code DumDbSourceApi} create/update endpoints (SonarQube S4684) — a plain
 * POJO that mirrors the writable fields of {@code DumDbSource} so the persistent entity is never
 * bound directly from the request body. The {@code fields} collection holds {@code DumDbField}
 * {@code @Embeddable} value objects (not entities). The {@code id} is accepted only to validate the
 * path variable on update; it is never applied to the entity (created ids are generated).
 */
@Getter
@Setter
@NoArgsConstructor
public class DumDbSourceRequest {

    private String id;
    private String name;
    private String description;
    private String driver;
    private String url;
    private String dbUsername;
    private String dbPassword;
    private String query;
    private String site;
    private String locale;
    private String contentType;
    private int chunk;
    private boolean typeInId;
    private String multiValuedSeparator;
    private String multiValuedFields;
    private String removeHtmlTagsFields;
    private String filePathField;
    private String fileContentField;
    private String fileSizeField;
    private String customClassName;
    private long maxContentMegaByteSize;
    private String encoding;
    private boolean showOutput;
    private boolean deindexBeforeImporting;
    private String primaryKeyColumn;
    private List<DumDbField> fields = new ArrayList<>();
    private Collection<String> turSNSites = new HashSet<>();
}

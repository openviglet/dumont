/*
 *
 * Copyright (C) 2016-2026 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.plugin.aem.api.dto;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemAttributeSpecification;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemPluginModel;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSourceLocalePath;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for {@code DumAemSourceApi} create/update (SonarQube S4684) — an aggregate POJO
 * mirroring the writable fields of {@code DumAemSource} so the persistent root entity is never bound
 * directly from the request body. The child collections stay typed as their config sub-entities
 * (intra-aggregate rows the controller re-parents through {@code wireModelHierarchy} on save); only
 * the root binding is decoupled. {@code id} is accepted only to validate the path variable on update.
 */
@Getter
@Setter
@NoArgsConstructor
public class DumAemSourceRequest {

    private String id;
    private String name;
    private String endpoint;
    private String username;
    private String password;
    private String rootPath;
    private String contentType;
    private String subType;
    private String oncePattern;
    private Locale defaultLocale;
    private String localeClass;
    private String deltaClass;
    private String dependencyClass;
    private boolean author;
    private boolean publish;
    private String authorSNSite;
    private String publishSNSite;
    private String authorURLPrefix;
    private String publishURLPrefix;
    private Collection<DumAemSourceLocalePath> localePaths = new HashSet<>();
    private Collection<DumAemAttributeSpecification> attributeSpecifications = new HashSet<>();
    private Collection<DumAemPluginModel> models = new HashSet<>();
}

/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
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

package com.viglet.dumont.connector.plugin.aem.export.bean;

import lombok.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class DumAemSourceExchange {
    private String id;
    private String name;
    private Locale defaultLocale;
    private String localeClass;
    private String deltaClass;
    private String dependencyClass;
    private String indexConditionClass;
    private String endpoint;
    private String oncePattern;
    private String username;
    private String password;
    private String rootPath;
    private String contentType;
    private boolean author;
    private boolean publish;
    private String authorSNSite;
    private String publishSNSite;
    private String authorURLPrefix;
    private String publishURLPrefix;

    @Builder.Default
    private Collection<DumAemAttribExchange> attributes = new HashSet<>();
    @Builder.Default
    private Collection<DumAemModelExchange> models = new HashSet<>();
    @Builder.Default
    private Collection<DumAemSourceLocalePathExchange> localePaths = new HashSet<>();

    // Roadmap DU17: Jackson creates this bean without the builder defaults, so a file that leaves
    // a collection out reads as empty here rather than failing the import on a null.

    public Collection<DumAemAttribExchange> getAttributes() {
        if (attributes == null) {
            attributes = new HashSet<>();
        }
        return attributes;
    }

    public Collection<DumAemModelExchange> getModels() {
        if (models == null) {
            models = new HashSet<>();
        }
        return models;
    }

    public Collection<DumAemSourceLocalePathExchange> getLocalePaths() {
        if (localePaths == null) {
            localePaths = new HashSet<>();
        }
        return localePaths;
    }
}

/*
 * Copyright (C) 2016-2024 the original author or authors.
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

package com.viglet.dumont.connector.plugin.db.export.bean;

import lombok.*;

import java.util.Collection;
import java.util.HashSet;

@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class DumDbSourceExchange {
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

    @Builder.Default
    private Collection<String> turSNSites = new HashSet<>();
}

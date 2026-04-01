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

package com.viglet.dumont.connector.plugin.db.persistence.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;

import com.viglet.dumont.spring.jpa.DumUuid;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "db_source")
public class DumDbSource implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @DumUuid
    @Column(name = "id", nullable = false)
    private String id;

    @Column
    private String name;

    @Column
    private String description;

    @Column(name = "db_driver")
    private String driver;

    @Column(name = "db_url")
    private String url;

    @Column(name = "db_username")
    private String dbUsername;

    @Column(name = "db_password")
    private String dbPassword;

    @Column(length = 4000)
    private String query;

    @Column
    private String site;

    @Column
    private String locale;

    @Column
    private String contentType;

    @Column
    private int chunk;

    @Column
    private boolean typeInId;

    @Column
    private String multiValuedSeparator;

    @Column
    private String multiValuedFields;

    @Column
    private String removeHtmlTagsFields;

    @Column
    private String filePathField;

    @Column
    private String fileContentField;

    @Column
    private String fileSizeField;

    @Column
    private String customClassName;

    @Column
    private long maxContentMegaByteSize;

    @Column
    private String encoding;

    @Column
    private boolean showOutput;

    @Column
    private boolean deindexBeforeImporting;

    @Builder.Default
    @ElementCollection(targetClass = String.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "db_sn_site", joinColumns = @JoinColumn(name = "source_id"))
    @Column(name = "sn_site", nullable = false)
    private Collection<String> turSNSites = new HashSet<>();
}

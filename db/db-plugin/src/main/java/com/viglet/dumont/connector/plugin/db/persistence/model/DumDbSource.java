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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
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
    @VigletAssignableUuidGenerator
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

    /**
     * The query column (the {@code AS} alias) that identifies a row — used by the structured-source
     * SPI to enumerate ids ({@code SELECT <pk> FROM (<query>) sub}) and to fetch a single row
     * ({@code … WHERE <pk> = ?}). Defaults to {@code id} when unset.
     */
    @Column
    private String primaryKeyColumn;

    /**
     * Declared column&rarr;field schema (roadmap <strong>D20</strong>). When empty, the schema is
     * derived from the query's result-set metadata so legacy sources keep indexing at parity.
     */
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "db_source_field", joinColumns = @JoinColumn(name = "source_id"))
    @OrderColumn(name = "field_order")
    private List<DumDbField> fields = new ArrayList<>();

    @Builder.Default
    @ElementCollection(targetClass = String.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "db_sn_site", joinColumns = @JoinColumn(name = "source_id"))
    @Column(name = "sn_site", nullable = false)
    private Collection<String> turSNSites = new HashSet<>();
}

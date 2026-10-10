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

package com.viglet.dumont.connector.plugin.jobs.persistence.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

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

/**
 * One partner job API configured as a source (trabalharei <strong>TRA13</strong>): which adapter
 * runs it ({@link #sourceType}), the credentials it authenticates with, the Turing SN sites it
 * publishes to, and the call caps the partner's plan allows. The credentials are write-only through
 * the API — {@code DumJobSourceApi} masks them on every read.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "job_source")
public class DumJobSource implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The locale a source publishes in when none is configured. */
    public static final String DEFAULT_LOCALE = "pt_BR";

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", nullable = false)
    private String id;

    @Column
    private String name;

    @Column
    private String description;

    /** The adapter id that runs this source: {@code adzuna}, {@code jooble}, {@code bne} or {@code jsearch}. */
    @Column(name = "source_type", length = 32)
    private String sourceType;

    @Builder.Default
    @Column
    private boolean enabled = true;

    /** Application id (Adzuna {@code app_id}). Credential — masked on read. */
    @Column(name = "app_id")
    private String appId;

    /** Application key (Adzuna {@code app_key}). Credential — masked on read. */
    @Column(name = "app_key")
    private String appKey;

    /** API key (Jooble, BNE, JSearch/RapidAPI). Credential — masked on read. */
    @Column(name = "api_key")
    private String apiKey;

    /** Base URL of the partner API, when it differs from the adapter's default. */
    @Column(name = "api_url", length = 2000)
    private String apiUrl;

    @Builder.Default
    @Column
    private String locale = DEFAULT_LOCALE;

    /** Maximum partner API calls per rolling minute; {@code null} or {@code <= 0} means uncapped. */
    @Column(name = "cap_per_minute")
    private Integer capPerMinute;

    /** Maximum partner API calls per rolling day; {@code null} or {@code <= 0} means uncapped. */
    @Column(name = "cap_per_day")
    private Integer capPerDay;

    /** Maximum partner API calls per rolling week; {@code null} or {@code <= 0} means uncapped. */
    @Column(name = "cap_per_week")
    private Integer capPerWeek;

    /** Maximum partner API calls per rolling 30 days; {@code null} or {@code <= 0} means uncapped. */
    @Column(name = "cap_per_month")
    private Integer capPerMonth;

    @Builder.Default
    @ElementCollection(targetClass = String.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "job_sn_site", joinColumns = @JoinColumn(name = "source_id"))
    @Column(name = "sn_site", nullable = false)
    private Collection<String> turSNSites = new HashSet<>();
}

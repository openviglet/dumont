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

package com.viglet.dumont.connector.plugin.jobs;

import java.util.List;
import java.util.Set;

import com.viglet.dumont.connector.commons.spi.structured.DumEmbeddingRole;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * The schema-as-code for a trabalharei job posting (trabalharei <strong>TRA13</strong>). It mirrors
 * the trabalharei {@code JOB_FIELDS} contract field for field, so every key an adapter emits maps 1:1
 * to a Turing attribute spec and the grounding contract is enforced by {@link DumStructuredSchema}.
 *
 * <p>
 * {@link #ID} is part of {@link #FIELDS} (the contract the parity test pins) but it is carried as the
 * document id by the indexing loop, so — as in the catalog schema — it is <em>not</em> redeclared as
 * a schema field. {@code title}, {@code source}, {@code url}, {@code applyUrl}, {@code publishedAt}
 * and {@code modificationDate} are mandatory: a posting without any of them is skipped rather than
 * published as a partial document.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumJobsSchema {

    private DumJobsSchema() {
    }

    /** Job record field names — the trabalharei {@code JOB_FIELDS} keys. */
    public static final String ID = "id";
    public static final String TITLE = "title";
    public static final String TEXT = "text";
    public static final String ABSTRACT = "abstract";
    public static final String COMPANY = "company";
    public static final String CITY = "city";
    public static final String STATE = "state";
    public static final String REMOTE = "remote";
    public static final String CONTRACT = "contract";
    public static final String SENIORITY = "seniority";
    public static final String SALARY_MIN = "salaryMin";
    public static final String SALARY_MAX = "salaryMax";
    public static final String SALARY_PERIOD = "salaryPeriod";
    public static final String SALARY_BAND = "salaryBand";
    public static final String OCCUPATION = "occupation";
    public static final String SOURCE = "source";
    public static final String URL = "url";
    public static final String APPLY_URL = "applyUrl";
    public static final String PUBLISHED_AT = "publishedAt";
    public static final String EXPIRES_AT = "expiresAt";
    public static final String MODIFICATION_DATE = "modificationDate";

    /** The full trabalharei {@code JOB_FIELDS} contract, in order — {@link #ID} included. */
    public static final List<String> FIELDS = List.of(ID, TITLE, TEXT, ABSTRACT, COMPANY, CITY, STATE,
            REMOTE, CONTRACT, SENIORITY, SALARY_MIN, SALARY_MAX, SALARY_PERIOD, SALARY_BAND, OCCUPATION,
            SOURCE, URL, APPLY_URL, PUBLISHED_AT, EXPIRES_AT, MODIFICATION_DATE);

    /** The faceted fields (navigation filters on the job site). */
    public static final Set<String> FACETS = Set.of(CITY, STATE, REMOTE, CONTRACT, SENIORITY,
            SALARY_BAND, OCCUPATION, SOURCE, PUBLISHED_AT);

    private static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder()
            .field(DumStructuredField.builder(TITLE).type(TurSEFieldType.TEXT).mandatory(true)
                    .embeddingRole(DumEmbeddingRole.TITLE)
                    .description("Job title").build())
            .body(TEXT)
            .text(ABSTRACT)
            .string(COMPANY)
            .facet(CITY)
            .facet(STATE)
            .facet(REMOTE)
            .facet(CONTRACT)
            .facet(SENIORITY)
            .field(DumStructuredField.builder(SALARY_MIN).type(TurSEFieldType.FLOAT).build())
            .field(DumStructuredField.builder(SALARY_MAX).type(TurSEFieldType.FLOAT).build())
            .string(SALARY_PERIOD)
            .facet(SALARY_BAND)
            .facet(OCCUPATION)
            .field(DumStructuredField.builder(SOURCE).type(TurSEFieldType.STRING).multiValued(true)
                    .facet(true).mandatory(true)
                    .description("Partner API the posting came from (adzuna, jooble, ...)").build())
            .field(DumStructuredField.builder(URL).type(TurSEFieldType.STRING).mandatory(true)
                    .description("Absolute URL of the public job detail page").build())
            .field(DumStructuredField.builder(APPLY_URL).type(TurSEFieldType.STRING).mandatory(true)
                    .description("Absolute URL where the candidate applies").build())
            .field(DumStructuredField.builder(PUBLISHED_AT).type(TurSEFieldType.DATE).facet(true)
                    .mandatory(true).description("When the partner published the posting").build())
            .field(DumStructuredField.builder(EXPIRES_AT).type(TurSEFieldType.DATE).build())
            .field(DumStructuredField.builder(MODIFICATION_DATE).type(TurSEFieldType.DATE)
                    .mandatory(true).build())
            .build();

    /** The shared, immutable job schema. */
    public static DumStructuredSchema schema() {
        return SCHEMA;
    }
}

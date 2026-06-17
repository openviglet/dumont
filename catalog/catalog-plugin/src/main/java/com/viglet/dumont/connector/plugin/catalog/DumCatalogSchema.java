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

package com.viglet.dumont.connector.plugin.catalog;

import java.util.List;

import com.viglet.dumont.connector.commons.spi.structured.DumEmbeddingRole;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * The schema-as-code for a cursarei {@code Course} record (roadmap <strong>D10</strong> /
 * cursarei <strong>V05.0</strong>). It mirrors the public {@code Course} contract the external
 * engine serializes (camelCase keys), so every field a source declares maps 1:1 to a Turing
 * attribute spec and the grounding contract is enforced by {@link DumStructuredSchema}.
 *
 * <p>
 * The record {@code id} is the cursarei course id and is carried as the document id by the indexing
 * loop, so it is <em>not</em> redeclared here. {@code title} and {@code url} are mandatory — a record
 * without either is skipped rather than published as a partial document, keeping the "official public
 * source" rule and the URL-sample parity check honest.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumCatalogSchema {

    private DumCatalogSchema() {
    }

    /** Catalog record field names — the camelCase keys the external engine emits. */
    public static final String TITLE = "title";
    public static final String INSTITUTION = "institution";
    public static final String LEVEL = "level";
    public static final String MODALITY = "modality";
    public static final String AREA = "area";
    public static final String URL = "url";
    public static final String DESCRIPTION = "description";
    public static final String CITY = "city";
    public static final String STATE = "state";
    public static final String DURATION = "duration";
    public static final String HIGHLIGHT = "highlight";
    public static final String DESCRIPTION_AI = "descriptionAI";
    public static final String PRICE_FROM = "priceFrom";
    public static final String PRICE_TO = "priceTo";
    public static final String APPLICATION_DEADLINE = "applicationDeadline";
    public static final String START_DATE = "startDate";
    public static final String CAPACITY = "capacity";
    public static final String MEC_RATING = "mecRating";
    public static final String CAPES_SCORE = "capesScore";
    public static final String FACULTY = "faculty";
    public static final String TARGET_AUDIENCE = "targetAudience";
    public static final String PREREQUISITES = "prerequisites";
    public static final String SKILLS = "skills";
    public static final String CAREER_OUTCOMES = "careerOutcomes";
    public static final String LANGUAGE = "language";
    public static final String AUDIENCE_SUMMARY = "audienceSummary";
    public static final String LEARNING_SUMMARY = "learningSummary";

    /** Every field declared by the catalog schema, in {@code Course} declaration order. */
    public static final List<String> FIELDS = List.of(TITLE, INSTITUTION, LEVEL, MODALITY, AREA, URL,
            DESCRIPTION, CITY, STATE, DURATION, HIGHLIGHT, DESCRIPTION_AI, PRICE_FROM, PRICE_TO,
            APPLICATION_DEADLINE, START_DATE, CAPACITY, MEC_RATING, CAPES_SCORE, FACULTY,
            TARGET_AUDIENCE, PREREQUISITES, SKILLS, CAREER_OUTCOMES, LANGUAGE, AUDIENCE_SUMMARY,
            LEARNING_SUMMARY);

    private static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder()
            .field(DumStructuredField.builder(TITLE).type(TurSEFieldType.TEXT).mandatory(true)
                    .embeddingRole(DumEmbeddingRole.TITLE)
                    .description("Course title").build())
            .facet(INSTITUTION)
            .facet(LEVEL)
            .facet(MODALITY)
            .facet(AREA)
            .field(DumStructuredField.builder(URL).type(TurSEFieldType.STRING).mandatory(true)
                    .description("Absolute URL of the public course detail page").build())
            .body(DESCRIPTION)
            .facet(CITY)
            .facet(STATE)
            .string(DURATION)
            .field(DumStructuredField.builder(HIGHLIGHT).type(TurSEFieldType.BOOL).build())
            .field(DumStructuredField.builder(DESCRIPTION_AI).type(TurSEFieldType.BOOL).build())
            .string(PRICE_FROM)
            .string(PRICE_TO)
            .field(DumStructuredField.builder(APPLICATION_DEADLINE).type(TurSEFieldType.DATE).build())
            .field(DumStructuredField.builder(START_DATE).type(TurSEFieldType.DATE).build())
            .field(DumStructuredField.builder(CAPACITY).type(TurSEFieldType.INT).build())
            .field(DumStructuredField.builder(MEC_RATING).type(TurSEFieldType.FLOAT).build())
            .field(DumStructuredField.builder(CAPES_SCORE).type(TurSEFieldType.FLOAT).build())
            .facet(FACULTY)
            .text(TARGET_AUDIENCE)
            .text(PREREQUISITES)
            .facet(SKILLS)
            .facet(CAREER_OUTCOMES)
            .facet(LANGUAGE)
            .field(DumStructuredField.builder(AUDIENCE_SUMMARY).type(TurSEFieldType.TEXT)
                    .multiValued(true).build())
            .field(DumStructuredField.builder(LEARNING_SUMMARY).type(TurSEFieldType.TEXT)
                    .multiValued(true).build())
            .build();

    /** The shared, immutable catalog schema. */
    public static DumStructuredSchema schema() {
        return SCHEMA;
    }
}

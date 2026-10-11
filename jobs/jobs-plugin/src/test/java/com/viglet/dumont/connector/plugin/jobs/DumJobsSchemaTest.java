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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.DumEmbeddingRole;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/** Pins the schema to the trabalharei {@code JOB_FIELDS} contract (parity with the site). */
class DumJobsSchemaTest {

    private final DumStructuredSchema schema = DumJobsSchema.schema();

    @Test
    void fieldsAreExactlyTheTrabalhareiContractInOrder() {
        assertThat(DumJobsSchema.FIELDS).containsExactly("id", "title", "text", "abstract",
                "company", "city", "state", "remote", "contract", "seniority", "salaryMin",
                "salaryMax", "salaryPeriod", "salaryBand", "occupation", "source", "url", "applyUrl",
                "publishedAt", "expiresAt", "modificationDate");
    }

    @Test
    void schemaDeclaresEveryContractFieldButTheDocumentId() {
        assertThat(schema.fields()).extracting(DumStructuredField::name)
                .containsExactlyElementsOf(DumJobsSchema.FIELDS.subList(1, DumJobsSchema.FIELDS.size()));
        assertThat(schema.hasField(DumJobsSchema.ID)).isFalse();
    }

    @Test
    void facetsAreExactlyTheNavigationFilters() {
        assertThat(schema.fields()).filteredOn(DumStructuredField::facet)
                .extracting(DumStructuredField::name)
                .containsExactlyInAnyOrder("city", "state", "remote", "contract", "seniority",
                        "salaryBand", "occupation", "source", "publishedAt");
        assertThat(DumJobsSchema.FACETS).containsExactlyInAnyOrder("city", "state", "remote",
                "contract", "seniority", "salaryBand", "occupation", "source", "publishedAt");
    }

    @Test
    void mandatoryFields() {
        assertThat(schema.mandatoryFieldNames()).containsExactly("title", "source", "url",
                "applyUrl", "publishedAt", "modificationDate");
    }

    @Test
    void declaredTypes() {
        assertThat(type("title")).isEqualTo(TurSEFieldType.TEXT);
        assertThat(type("text")).isEqualTo(TurSEFieldType.TEXT);
        assertThat(type("abstract")).isEqualTo(TurSEFieldType.TEXT);
        assertThat(type("company")).isEqualTo(TurSEFieldType.STRING);
        assertThat(type("salaryMin")).isEqualTo(TurSEFieldType.FLOAT);
        assertThat(type("salaryMax")).isEqualTo(TurSEFieldType.FLOAT);
        assertThat(type("salaryPeriod")).isEqualTo(TurSEFieldType.STRING);
        assertThat(type("url")).isEqualTo(TurSEFieldType.STRING);
        assertThat(type("applyUrl")).isEqualTo(TurSEFieldType.STRING);
        assertThat(type("publishedAt")).isEqualTo(TurSEFieldType.DATE);
        assertThat(type("expiresAt")).isEqualTo(TurSEFieldType.DATE);
        assertThat(type("modificationDate")).isEqualTo(TurSEFieldType.DATE);
        assertThat(schema.field("title").orElseThrow().embeddingRole())
                .isEqualTo(DumEmbeddingRole.TITLE);
        assertThat(schema.field("text").orElseThrow().embeddingRole())
                .isEqualTo(DumEmbeddingRole.BODY);
    }

    private TurSEFieldType type(String name) {
        return schema.field(name).orElseThrow().type();
    }
}

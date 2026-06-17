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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.plugin.catalog.fetch.DumCatalogFetcher;
import com.viglet.dumont.connector.plugin.catalog.persistence.model.DumCatalogSource;
import com.viglet.dumont.connector.plugin.catalog.source.DumCatalogStructuredSource;

/** Maps cursarei {@code Course} JSON onto the catalog schema, honoring the grounding contract. */
class DumCatalogStructuredSourceTest {

    private static final String ADMIN_ID = "ufmg-administracao";

    private static final String CATALOG_JSON = """
            [
              {
                "id": "ufmg-administracao",
                "title": "Administração",
                "institution": "UFMG",
                "level": "Graduação",
                "modality": "Presencial",
                "area": "Negócios e Gestão",
                "url": "https://ufmg.br/cursos/admin",
                "description": "Curso de Administração.",
                "city": "Belo Horizonte",
                "state": "MG",
                "highlight": true,
                "capacity": 120,
                "mecRating": 4.5,
                "faculty": ["Prof. A", "Prof. B"],
                "skills": ["gestão", "liderança"],
                "audienceSummary": ["Para gestores", "Para empreendedores"]
              },
              {
                "id": "ufmg-online",
                "title": "Curso Online",
                "url": "https://ufmg.br/online",
                "description": "",
                "city": null
              },
              {
                "title": "Sem identificador",
                "url": "https://ufmg.br/sem-id"
              }
            ]
            """;

    private DumCatalogStructuredSource source(String json) {
        DumCatalogSource entity = new DumCatalogSource();
        entity.setName("ufmg");
        entity.setCatalogUrl("https://example/ufmg.json");
        entity.setLocale("pt_BR");
        DumCatalogFetcher fetcher = s -> json;
        return new DumCatalogStructuredSource(entity, DumCatalogSchema.schema(), fetcher);
    }

    @Test
    void discoverSkipsRecordsWithoutAnId() {
        assertThat(source(CATALOG_JSON).discover())
                .containsExactly(ADMIN_ID, "ufmg-online");
    }

    @Test
    void localeParsedFromSource() {
        assertThat(source(CATALOG_JSON).getLocale()).isEqualTo(Locale.of("pt", "BR"));
    }

    @Test
    void extractMapsDeclaredFieldsWithCorrectTypes() {
        DumStructuredRecord rec = source(CATALOG_JSON).extract(ADMIN_ID).orElseThrow();

        assertThat(rec.id()).isEqualTo(ADMIN_ID);
        // Facets are multi-valued, so a single source value is grounded into a one-element list.
        assertThat(rec.values()).containsEntry(DumCatalogSchema.TITLE, "Administração")
                .containsEntry(DumCatalogSchema.INSTITUTION, List.of("UFMG"))
                .containsEntry(DumCatalogSchema.URL, "https://ufmg.br/cursos/admin")
                .containsEntry(DumCatalogSchema.STATE, List.of("MG"))
                .containsEntry(DumCatalogSchema.HIGHLIGHT, true)
                .containsEntry(DumCatalogSchema.CAPACITY, 120);
        assertThat(rec.values().get(DumCatalogSchema.FACULTY))
                .isEqualTo(List.of("Prof. A", "Prof. B"));
        assertThat(rec.values().get(DumCatalogSchema.AUDIENCE_SUMMARY))
                .isEqualTo(List.of("Para gestores", "Para empreendedores"));
    }

    @Test
    void absentAndBlankFieldsAreOmitted() {
        DumStructuredRecord rec = source(CATALOG_JSON).extract("ufmg-online").orElseThrow();

        // Blank description and JSON-null city are dropped; only the supplied fields survive.
        assertThat(rec.values()).containsOnlyKeys(DumCatalogSchema.TITLE, DumCatalogSchema.URL);
        assertThat(rec.missingMandatoryFields()).isEmpty();
    }

    @Test
    void acceptsMergedCatalogEnvelope() {
        String envelope = """
                { "count": 1, "institutions": ["UFMG"], "courses": [
                  { "id": "x", "title": "T", "url": "https://x" }
                ] }
                """;
        assertThat(source(envelope).discover()).containsExactly("x");
    }
}

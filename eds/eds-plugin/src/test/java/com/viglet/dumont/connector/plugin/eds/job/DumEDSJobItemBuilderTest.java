/*
 * Copyright (C) 2016-2024 the original author or authors.
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

package com.viglet.dumont.connector.plugin.eds.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.eds.commons.domain.DumEDSFetchedPage;
import com.viglet.dumont.connector.plugin.eds.enrichment.DumEDSFacetDeriver;
import com.viglet.dumont.connector.plugin.eds.enrichment.DumEDSPathPatternMatcher;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSFacet;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSFieldMapping;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSSource;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;

class DumEDSJobItemBuilderTest {

    private static final String FIELD_TITLE = "title";
    private static final String FIELD_DESCRIPTION = "description";
    private static final String TAG_PT_BR = "pt-BR";
    private static final String TAG_EN_US = "en-US";
    private static final String PATH_PF = "/pt-br/pf";
    private static final String LAST_MOD_GMT = "Wed, 01 May 2026 12:00:00 GMT";

    private final DumEDSJobItemBuilder builder = new DumEDSJobItemBuilder(
            new DumEDSFacetDeriver(new DumEDSPathPatternMatcher()));

    @Test
    void buildsAttributesWithIdUrlPathAndFacets() {
        DumEDSSource source = testSource();
        DumEDSFetchedPage page = new DumEDSFetchedPage(
                "/pt-br/pf/cartao-de-credito",
                Map.of(FIELD_TITLE, "Cartão", FIELD_DESCRIPTION, "Cartão de crédito sem anuidade"),
                "Body content here",
                LAST_MOD_GMT,
                null,
                null);

        TurSNJobItem item = builder.build(source, page);

        assertThat(item.getTurSNJobAction()).isEqualTo(TurSNJobAction.CREATE);
        assertThat(item.getSiteNames()).containsExactly("example-pt-br");
        assertThat(item.getLocale()).isEqualTo(Locale.forLanguageTag(TAG_PT_BR));

        Map<String, Object> attrs = item.getAttributes();
        assertThat(attrs).containsEntry("id", "https://example.invalid/pt-br/pf/cartao-de-credito");
        assertThat(attrs).containsEntry("url", "https://example.invalid/pt-br/pf/cartao-de-credito");
        assertThat(attrs).containsEntry("path", "/pt-br/pf/cartao-de-credito");
        assertThat(attrs).containsEntry(FIELD_TITLE, "Cartão");
        assertThat(attrs).containsEntry(FIELD_DESCRIPTION, "Cartão de crédito sem anuidade");
        assertThat(attrs).containsEntry("body", "Body content here");
        assertThat(attrs).containsEntry("lastModified", LAST_MOD_GMT);
        assertThat(attrs).containsEntry("locale", "pt-br");
        assertThat(attrs).containsEntry("audience", "pf");
        assertThat(attrs).containsEntry("section", "cartao-de-credito");
        assertThat(attrs).containsEntry("source_apps", "EDS");
    }

    @Test
    void checksumIsStableAcrossRebuildsForSameContent() {
        // Spec test case 6: re-running the crawl without changes should produce
        // the same checksum (and therefore zero updates via UnchangedStrategy).
        DumEDSSource source = testSource();
        DumEDSFetchedPage page = new DumEDSFetchedPage(
                PATH_PF,
                Map.of(FIELD_TITLE, "PF", FIELD_DESCRIPTION, "Welcome to Example"),
                "Conta digital, pix, investimentos e cartão.",
                LAST_MOD_GMT,
                null,
                null);

        TurSNJobItem first = builder.build(source, page);
        TurSNJobItem second = builder.build(source, page);

        assertThat(first.getChecksum()).isEqualTo(second.getChecksum());
        assertThat(first.getChecksum()).isNotBlank();
    }

    @Test
    void checksumChangesWhenAnyFieldChanges() {
        DumEDSSource source = testSource();
        DumEDSFetchedPage original = new DumEDSFetchedPage(
                PATH_PF,
                Map.of(FIELD_TITLE, "Original"),
                "Original body",
                null, null, null);
        DumEDSFetchedPage titleChanged = new DumEDSFetchedPage(
                PATH_PF,
                Map.of(FIELD_TITLE, "Changed"),
                "Original body",
                null, null, null);
        DumEDSFetchedPage bodyChanged = new DumEDSFetchedPage(
                PATH_PF,
                Map.of(FIELD_TITLE, "Original"),
                "Updated body",
                null, null, null);

        String originalChecksum = builder.build(source, original).getChecksum();
        assertThat(originalChecksum).isNotEqualTo(builder.build(source, titleChanged).getChecksum());
        assertThat(originalChecksum).isNotEqualTo(builder.build(source, bodyChanged).getChecksum());
    }

    @Test
    void applyFieldMappingRenamesAttributes() {
        DumEDSSource source = testSource();
        source.getFieldMappings().add(new DumEDSFieldMapping("title", "page_title", source));
        source.getFieldMappings().add(new DumEDSFieldMapping("body", "content", source));

        DumEDSFetchedPage page = new DumEDSFetchedPage("/x",
                Map.of(FIELD_TITLE, "T"), "B", null, null, null);

        Map<String, Object> attrs = builder.build(source, page).getAttributes();

        assertThat(attrs).containsEntry("page_title", "T")
                .containsEntry("content", "B")
                .doesNotContainKey("title")
                .doesNotContainKey("body");
    }

    @Test
    void perPageLocaleIsUsedWhenSourceLocaleNotSet() {
        DumEDSSource source = testSource();
        source.setLocale(null);  // force the page-level fallback path
        DumEDSFetchedPage page = new DumEDSFetchedPage("/en-us/foo",
                Map.of(FIELD_TITLE, "T"), "B", null, null,
                Locale.forLanguageTag(TAG_EN_US));

        TurSNJobItem item = builder.build(source, page);

        assertThat(item.getLocale()).isEqualTo(Locale.forLanguageTag(TAG_EN_US));
    }

    @Test
    void sourceLocaleWinsOverPageLocale() {
        DumEDSSource source = testSource();
        // source.locale is "pt-BR" via testSource() helper
        DumEDSFetchedPage page = new DumEDSFetchedPage("/foo",
                Map.of(FIELD_TITLE, "T"), "B", null, null,
                Locale.forLanguageTag(TAG_EN_US));

        TurSNJobItem item = builder.build(source, page);

        assertThat(item.getLocale()).isEqualTo(Locale.forLanguageTag(TAG_PT_BR));
    }

    @Test
    void blankAndNullValuesAreNotEmitted() {
        DumEDSSource source = testSource();
        DumEDSFetchedPage page = new DumEDSFetchedPage("/x",
                Map.of(FIELD_TITLE, "T"), "", null, null, null);

        Map<String, Object> attrs = builder.build(source, page).getAttributes();

        assertThat(attrs).doesNotContainKey("body");
        assertThat(attrs).doesNotContainKey("lastModified");
    }

    private static DumEDSSource testSource() {
        DumEDSSource source = new DumEDSSource();
        source.setId("test-source");
        source.setTitle("example");
        source.setBaseUrl("https://example.invalid");
        source.setLocale(Locale.forLanguageTag(TAG_PT_BR));
        source.setFacetPathPattern("/{locale}/{audience}/{section}/**");
        source.getTurSNSites().add("example-pt-br");
        source.getFacets().add(new DumEDSFacet("locale", 0, null, source));
        source.getFacets().add(new DumEDSFacet("audience", 1, "pf,pj", source));
        source.getFacets().add(new DumEDSFacet("section", 2, null, source));
        return source;
    }
}

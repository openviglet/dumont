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

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.plugin.eds.enrichment.DumEDSFacetDeriver;
import com.viglet.dumont.connector.plugin.eds.enrichment.DumEDSPathPatternMatcher;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSSource;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;

class DumEDSJobItemBuilderDeleteTest {

    private final DumEDSJobItemBuilder builder = new DumEDSJobItemBuilder(
            new DumEDSFacetDeriver(new DumEDSPathPatternMatcher()));

    @Test
    void buildDeleteEmitsMinimalAttributesAndDeleteAction() {
        DumEDSSource source = source();

        TurSNJobItem item = builder.buildDelete(source, "/pt-br/pf/old-page");

        assertThat(item.getTurSNJobAction()).isEqualTo(TurSNJobAction.DELETE);
        assertThat(item.getAttributes())
                .containsEntry("id", "https://example.invalid/pt-br/pf/old-page")
                .containsEntry("source_apps", "EDS");
        assertThat(item.getChecksum()).isNull();
        assertThat(item.getSiteNames()).containsExactly("example-pt-br");
    }

    @Test
    void buildDeleteUsesEffectiveLocaleWhenSourceLocaleNull() {
        DumEDSSource source = source();
        source.setLocale(null);

        TurSNJobItem item = builder.buildDelete(source, "/x");

        assertThat(item.getLocale()).isEqualTo(Locale.US);
    }

    private static DumEDSSource source() {
        DumEDSSource source = new DumEDSSource();
        source.setId("test-source");
        source.setBaseUrl("https://example.invalid");
        source.setLocale(Locale.forLanguageTag("pt-BR"));
        source.getTurSNSites().add("example-pt-br");
        return source;
    }
}

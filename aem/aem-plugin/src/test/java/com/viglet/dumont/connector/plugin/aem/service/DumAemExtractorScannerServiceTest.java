/*
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

package com.viglet.dumont.connector.plugin.aem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pf4j.PluginManager;
import org.springframework.beans.factory.ObjectProvider;

import com.viglet.dumont.connector.plugin.aem.service.DumAemExtractorScannerService.ExtractorClass;

class DumAemExtractorScannerServiceTest {

    private DumAemExtractorScannerService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ObjectProvider<PluginManager> provider = mock(ObjectProvider.class);
        // no live PF4J plugin manager -> the plugin-extension leg is skipped
        when(provider.getIfAvailable()).thenReturn(null);
        service = new DumAemExtractorScannerService(provider);
        service.scanAll();
    }

    @Test
    void listReturnsClasspathExtractorsForKnownCategory() {
        List<ExtractorClass> attributes = service.list("attribute", null);
        // The classpath scan must not fail and must surface well-formed attribute extractors.
        assertThat(attributes).isNotEmpty().allSatisfy(e -> {
            assertThat(e.fqcn()).isNotBlank();
            assertThat(e.category()).isEqualTo("attribute");
        });
    }

    @Test
    void listDefaultsToAttributeWhenCategoryNull() {
        assertThat(service.list(null, null)).isNotNull();
    }

    @Test
    void listIsEmptyForUnknownCategory() {
        assertThat(service.list("does-not-exist", null)).isEmpty();
    }

    @Test
    void listAppliesQueryFilter() {
        List<ExtractorClass> all = service.list("attribute", null);
        if (!all.isEmpty()) {
            String token = all.get(0).simpleName().substring(0, 1);
            assertThat(service.list("attribute", token))
                    .allMatch(e -> e.simpleName().toLowerCase().contains(token.toLowerCase())
                            || e.fqcn().toLowerCase().contains(token.toLowerCase()));
        }
        // a query that matches nothing returns an empty list
        assertThat(service.list("attribute", "zzz-no-such-extractor-zzz")).isEmpty();
    }
}

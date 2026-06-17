/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.plugin.aem.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.aem.commons.bean.DumAemEnv;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemAttributeSpecification;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import com.viglet.dumont.connector.plugin.aem.service.DumAemSessionService;
import com.viglet.turing.commons.se.field.TurSEFieldType;

@ExtendWith(MockitoExtension.class)
@DisplayName("DumAemStructuredSource Tests (D21)")
class DumAemStructuredSourceTest {

    @Mock
    private DumAemSessionService sessionService;
    @Mock
    private DumAemSession session;
    @Mock
    private DumAemConfiguration configuration;

    private DumAemSource source(DumAemAttributeSpecification... specs) {
        return DumAemSource.builder()
                .name("portal")
                .defaultLocale(Locale.of("pt", "BR"))
                .attributeSpecifications(Set.of(specs))
                .build();
    }

    private DumAemStructuredSource adapter(DumAemSource source, DumAemEnv env) {
        return new DumAemStructuredSource(source, env, sessionService, null, null, null, null, false);
    }

    @Test
    @DisplayName("schema() wraps the source's attribute specifications 1:1")
    void schemaWrapsAttributeSpecs() {
        DumAemAttributeSpecification title = DumAemAttributeSpecification.builder()
                .name("title").type(TurSEFieldType.TEXT).mandatory(true).build();
        DumAemAttributeSpecification tags = DumAemAttributeSpecification.builder()
                .name("tags").type(TurSEFieldType.STRING).multiValued(true).facet(true).build();
        DumAemStructuredSource adapter = adapter(source(title, tags), DumAemEnv.AUTHOR);

        DumStructuredSchema schema = adapter.schema();

        Map<String, DumStructuredField> byName = schema.fields().stream()
                .collect(Collectors.toMap(DumStructuredField::name, f -> f));
        assertEquals(2, byName.size());
        assertEquals(TurSEFieldType.TEXT, byName.get("title").type());
        assertTrue(byName.get("title").mandatory());
        assertTrue(byName.get("tags").multiValued());
        assertTrue(byName.get("tags").facet());
        assertEquals(List.of("title"), schema.mandatoryFieldNames());
    }

    @Test
    @DisplayName("getSiteNames() returns the SN site of the bound environment")
    void siteNamesPerEnvironment() {
        DumAemAttributeSpecification spec = DumAemAttributeSpecification.builder().name("title").build();
        DumAemSource source = source(spec);
        lenient().when(sessionService.getDumAemSession(source, false)).thenReturn(session);
        lenient().when(session.getConfiguration()).thenReturn(configuration);
        when(configuration.getAuthorSNSite()).thenReturn("authorSite");
        when(configuration.getPublishSNSite()).thenReturn("publishSite");

        assertEquals(List.of("authorSite"),
                List.copyOf(adapter(source, DumAemEnv.AUTHOR).getSiteNames()));
        assertEquals(List.of("publishSite"),
                List.copyOf(adapter(source, DumAemEnv.PUBLISHING).getSiteNames()));
    }

    @Test
    @DisplayName("getLocale() defaults to the source's locale and provider is AEM")
    void localeAndProvider() {
        DumAemAttributeSpecification spec = DumAemAttributeSpecification.builder().name("title").build();
        DumAemStructuredSource adapter = adapter(source(spec), DumAemEnv.AUTHOR);

        assertEquals(Locale.of("pt", "BR"), adapter.getLocale());
        assertEquals("AEM", adapter.getProviderName());
    }
}

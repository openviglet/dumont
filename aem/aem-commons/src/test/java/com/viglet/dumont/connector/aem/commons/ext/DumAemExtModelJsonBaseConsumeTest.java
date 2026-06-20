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

package com.viglet.dumont.connector.aem.commons.ext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.bean.DumAemAttrMap;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEnv;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.utils.DumAemCommonsUtils;

/**
 * Covers the {@code consume} template method of {@link DumAemExtModelJsonBase}: model.json fetch +
 * deserialization, author/publish URL routing and the {@code lastModifiedDate} fallback. The HTTP
 * call ({@link DumAemCommonsUtils#getResponseBody}) is stubbed via {@code mockStatic}.
 */
class DumAemExtModelJsonBaseConsumeTest {

    /** Minimal model.json bean. */
    static class Model {
        public String title;
    }

    /** Concrete extractor that surfaces the model title and exposes the static date helper. */
    static class Ext extends DumAemExtModelJsonBase<Model> {
        @Override
        protected Class<Model> getModelClass() {
            return Model.class;
        }

        @Override
        protected List<DumAemComponentExtractor<Model>> componentExtractors() {
            return List.of((m, q, o, attrs) -> {
                if (m.title != null) {
                    attrs.set("title", m.title);
                }
            });
        }

        java.util.Date exposeLastModified(DumAemObject aemObject) {
            return lastModifiedDate(aemObject);
        }
    }

    private final Ext ext = new Ext();

    private DumAemConfiguration config(String url, String publishPrefix) {
        return DumAemConfiguration.builder().url(url).publishURLPrefix(publishPrefix).build();
    }

    private DumAemObject aemObject(DumAemEnv env) {
        DumAemObject aemObject = mock(DumAemObject.class);
        lenient().when(aemObject.getPath()).thenReturn("/content/x");
        lenient().when(aemObject.getEnvironment()).thenReturn(env);
        return aemObject;
    }

    @Test
    void consumeExtractsAttributesFromModelJson() {
        try (MockedStatic<DumAemCommonsUtils> mocked = mockStatic(DumAemCommonsUtils.class)) {
            mocked.when(() -> DumAemCommonsUtils.getResponseBody(anyString(), any(), eq(false)))
                    .thenReturn(Optional.of("{\"title\":\"Hello\"}"));

            DumAemAttrMap attrs =
                    ext.consume(aemObject(DumAemEnv.AUTHOR), config("http://author", null));

            assertThat(attrs).containsKey("title");
        }
    }

    @Test
    void consumeReturnsEmptyWhenBodyAbsent() {
        try (MockedStatic<DumAemCommonsUtils> mocked = mockStatic(DumAemCommonsUtils.class)) {
            mocked.when(() -> DumAemCommonsUtils.getResponseBody(anyString(), any(), eq(false)))
                    .thenReturn(Optional.empty());

            assertThat(ext.consume(aemObject(DumAemEnv.AUTHOR), config("http://author", null)))
                    .isEmpty();
        }
    }

    @Test
    void consumeReturnsEmptyOnInvalidJson() {
        try (MockedStatic<DumAemCommonsUtils> mocked = mockStatic(DumAemCommonsUtils.class)) {
            mocked.when(() -> DumAemCommonsUtils.getResponseBody(anyString(), any(), eq(false)))
                    .thenReturn(Optional.of("definitely not json"));

            assertThat(ext.consume(aemObject(DumAemEnv.AUTHOR), config("http://author", null)))
                    .isEmpty();
        }
    }

    @Test
    void consumeRoutesPublishUrlWithCacheBuster() {
        try (MockedStatic<DumAemCommonsUtils> mocked = mockStatic(DumAemCommonsUtils.class)) {
            mocked.when(() -> DumAemCommonsUtils.getResponseBody(anyString(), any(), eq(false)))
                    .thenReturn(Optional.of("{\"title\":\"Pub\"}"));

            ext.consume(aemObject(DumAemEnv.PUBLISHING), config("http://author", "http://publish"));

            mocked.verify(() -> DumAemCommonsUtils.getResponseBody(
                    argThat(url -> url.startsWith("http://publish/content/x.model.json?t=")), any(),
                    eq(false)));
        }
    }

    @Test
    void lastModifiedDateFallsBackToCreatedDate() {
        Calendar created = new GregorianCalendar(2024, Calendar.JANUARY, 1, 10, 0, 0);
        DumAemObject withCreatedOnly = mock(DumAemObject.class);
        lenient().when(withCreatedOnly.getLastModified()).thenReturn(null);
        lenient().when(withCreatedOnly.getCreatedDate()).thenReturn(created);

        assertThat(ext.exposeLastModified(withCreatedOnly)).isEqualTo(created.getTime());

        Calendar modified = new GregorianCalendar(2024, Calendar.FEBRUARY, 2, 12, 0, 0);
        DumAemObject withModified = mock(DumAemObject.class);
        lenient().when(withModified.getLastModified()).thenReturn(modified);

        assertThat(ext.exposeLastModified(withModified)).isEqualTo(modified.getTime());
    }
}

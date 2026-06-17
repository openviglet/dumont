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

package com.viglet.dumont.connector.aem.commons.ext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.bean.DumAemAttrMap;

/**
 * Verifies {@link DumAemExtModelJsonBase} content-type routing (roadmap <strong>D25</strong>): a
 * template resolves through the alias map to a content type, and only that type's
 * {@link DumAemContentType#extractors() extractor composition} runs; an unknown template falls back
 * to the flat {@link DumAemExtModelJsonBase#componentExtractors()}.
 */
@DisplayName("DumAemExtModelJsonBase content-type routing (D25)")
class DumAemContentTypeRoutingTest {

    private static final DumAemComponentExtractor<String> ARTICLE_BODY =
            (m, q, o, attrs) -> attrs.set("articleBody", m + "-article");
    private static final DumAemComponentExtractor<String> EVENT_DATE =
            (m, q, o, attrs) -> attrs.set("eventDate", m + "-event");
    private static final DumAemComponentExtractor<String> FALLBACK =
            (m, q, o, attrs) -> attrs.set("fallback", m + "-flat");

    /** A typed extension whose String "model" is the raw template name. */
    private static class TypedExtractor extends DumAemExtModelJsonBase<String> {
        @Override
        protected Class<String> getModelClass() {
            return String.class;
        }

        @Override
        protected Map<String, String> templateNameAliases() {
            return Map.of("news", "article", "happening", "event");
        }

        @Override
        protected String templateName(String model, DumAemObject aemObject) {
            return model;
        }

        @Override
        protected List<DumAemContentType<String>> contentTypes() {
            return List.of(
                    DumAemContentType.<String>builder("article")
                            .field(DumAemContentTypeField.text("articleBody"))
                            .extractor(ARTICLE_BODY).build(),
                    DumAemContentType.<String>builder("event")
                            .field(DumAemContentTypeField.text("eventDate"))
                            .extractor(EVENT_DATE).build());
        }

        @Override
        protected List<DumAemComponentExtractor<String>> componentExtractors() {
            return List.of(FALLBACK);
        }

        DumAemAttrMap run(String model) {
            DumAemAttrMap attrs = new DumAemAttrMap();
            extractAttributes(model, null, null, attrs);
            return attrs;
        }
    }

    private final TypedExtractor extractor = new TypedExtractor();

    @Test
    @DisplayName("Should run only the resolved type's composition (article)")
    void shouldRunArticleComposition() {
        DumAemAttrMap attrs = extractor.run("news");

        assertEquals("news-article", attrs.get("articleBody").getFirst());
        assertTrue(attrs.containsKey("articleBody"));
        assertNull(attrs.get("eventDate"));
        assertNull(attrs.get("fallback"));
    }

    @Test
    @DisplayName("Should run only the resolved type's composition (event)")
    void shouldRunEventComposition() {
        DumAemAttrMap attrs = extractor.run("happening");

        assertEquals("happening-event", attrs.get("eventDate").getFirst());
        assertNull(attrs.get("articleBody"));
        assertNull(attrs.get("fallback"));
    }

    @Test
    @DisplayName("Should fall back to the flat composition for an unknown template")
    void shouldFallBackForUnknownTemplate() {
        DumAemAttrMap attrs = extractor.run("landing-page");

        assertEquals("landing-page-flat", attrs.get("fallback").getFirst());
        assertNull(attrs.get("articleBody"));
        assertNull(attrs.get("eventDate"));
    }

    @Test
    @DisplayName("resolveContentType maps a template through the alias map")
    void shouldResolveContentType() {
        assertEquals("article", extractor.resolveContentType("news", null));
        assertEquals("event", extractor.resolveContentType("happening", null));
        // A template not in the alias map resolves to itself (still its own content type).
        assertEquals("landing-page", extractor.resolveContentType("landing-page", null));
        assertNull(extractor.resolveContentType("", null));
    }
}

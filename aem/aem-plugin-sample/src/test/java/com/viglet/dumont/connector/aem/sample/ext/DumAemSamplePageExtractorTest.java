/*
 * Copyright (C) 2016-2025 the original author or authors.
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

package com.viglet.dumont.connector.aem.sample.ext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.aem.commons.bean.DumAemAttrMap;
import com.viglet.dumont.connector.aem.sample.beans.DumAemSampleModel;

@DisplayName("DumAemSamplePageExtractor Tests")
class DumAemSamplePageExtractorTest {

    private final DumAemSamplePageExtractor extractor = new DumAemSamplePageExtractor();

    @Test
    @DisplayName("Should map page-level fields from model root")
    void shouldMapPageLevelFields() {
        DumAemSampleModel model = new DumAemSampleModel();
        model.setFragmentPath("/content/fragments/a");
        model.setTitle("Home");
        model.setDescription("A page");
        model.setLanguage("en");
        model.setTemplateName("page");

        DumAemAttrMap attrs = new DumAemAttrMap();
        extractor.contribute(model, null, null, attrs);

        assertEquals("/content/fragments/a", attrs.get(DumAemExtSampleModelJson.FRAGMENT_PATH).getFirst());
        assertEquals("Home", attrs.get(DumAemExtSampleModelJson.PAGE_TITLE).getFirst());
        assertEquals("A page", attrs.get(DumAemExtSampleModelJson.PAGE_DESCRIPTION).getFirst());
        assertEquals("en", attrs.get(DumAemExtSampleModelJson.PAGE_LANGUAGE).getFirst());
        assertEquals("page", attrs.get(DumAemExtSampleModelJson.PAGE_TEMPLATE).getFirst());
    }

    @Test
    @DisplayName("Should set last-modified date when present")
    void shouldSetLastModifiedWhenPresent() {
        DumAemSampleModel model = new DumAemSampleModel();
        long epoch = 1_700_000_000_000L;
        model.setLastModifiedDate(epoch);

        DumAemAttrMap attrs = new DumAemAttrMap();
        extractor.contribute(model, null, null, attrs);

        assertTrue(attrs.containsKey(DumAemExtSampleModelJson.PAGE_LAST_MODIFIED));
        // Dates are stored in ISO-instant (UTC) form by TurMultiValue.
        assertEquals("2023-11-14T22:13:20Z",
                attrs.get(DumAemExtSampleModelJson.PAGE_LAST_MODIFIED).getFirst());
    }

    @Test
    @DisplayName("Should omit last-modified date when absent")
    void shouldOmitLastModifiedWhenAbsent() {
        DumAemSampleModel model = new DumAemSampleModel();
        model.setTitle("No date");

        DumAemAttrMap attrs = new DumAemAttrMap();
        extractor.contribute(model, null, null, attrs);

        assertFalse(attrs.containsKey(DumAemExtSampleModelJson.PAGE_LAST_MODIFIED));
    }

    @Test
    @DisplayName("Should skip null page fields")
    void shouldSkipNullFields() {
        DumAemSampleModel model = new DumAemSampleModel();
        model.setTitle("Only title");

        DumAemAttrMap attrs = new DumAemAttrMap();
        extractor.contribute(model, null, null, attrs);

        assertTrue(attrs.containsKey(DumAemExtSampleModelJson.PAGE_TITLE));
        assertFalse(attrs.containsKey(DumAemExtSampleModelJson.FRAGMENT_PATH));
    }
}

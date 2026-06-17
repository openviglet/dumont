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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.aem.commons.bean.DumAemAttrMap;
import com.viglet.dumont.connector.aem.sample.beans.DumAemSampleModel;
import com.viglet.dumont.connector.aem.sample.beans.DumAemSampleModel.DumAemSampleModelElement;
import com.viglet.dumont.connector.aem.sample.beans.DumAemSampleModel.DumAemSampleModelItem;

@DisplayName("DumAemSampleContentFragmentExtractor Tests")
class DumAemSampleContentFragmentExtractorTest {

    private final DumAemSampleContentFragmentExtractor extractor =
            new DumAemSampleContentFragmentExtractor();

    @Test
    @DisplayName("Should collect titles, models and HTML-stripped element values from CF items")
    void shouldCollectContentFragments() {
        DumAemSampleModelElement element = new DumAemSampleModelElement();
        element.setValue("<p>Body</p>");
        DumAemSampleModelElement blankElement = new DumAemSampleModelElement();
        blankElement.setValue("  ");

        Map<String, DumAemSampleModelElement> elements = new LinkedHashMap<>();
        elements.put("e1", element);
        elements.put("e2", blankElement);

        DumAemSampleModelItem cf = new DumAemSampleModelItem();
        cf.setType("my-app/components/contentfragment");
        cf.setTitle("CF Title");
        cf.setModel("article");
        cf.setElements(elements);

        DumAemSampleModel model = new DumAemSampleModel();
        model.setItems(map("cf", cf));

        DumAemAttrMap attrs = new DumAemAttrMap();
        extractor.contribute(model, null, null, attrs);

        assertEquals(List.of("CF Title"), attrs.get(DumAemExtSampleModelJson.CONTENT_FRAGMENT_TITLES));
        assertEquals(List.of("article"), attrs.get(DumAemExtSampleModelJson.CONTENT_FRAGMENT_MODELS));
        assertEquals(List.of("Body"), attrs.get(DumAemExtSampleModelJson.CONTENT_FRAGMENT_ELEMENTS));
    }

    @Test
    @DisplayName("Should ignore non content-fragment items")
    void shouldIgnoreNonContentFragments() {
        DumAemSampleModelItem text = new DumAemSampleModelItem();
        text.setType("my-app/components/text");
        text.setTitle("Not a CF");

        DumAemSampleModel model = new DumAemSampleModel();
        model.setItems(map("t", text));

        DumAemAttrMap attrs = new DumAemAttrMap();
        extractor.contribute(model, null, null, attrs);

        assertTrue(attrs.get(DumAemExtSampleModelJson.CONTENT_FRAGMENT_TITLES).isEmpty());
        assertTrue(attrs.get(DumAemExtSampleModelJson.CONTENT_FRAGMENT_MODELS).isEmpty());
        assertTrue(attrs.get(DumAemExtSampleModelJson.CONTENT_FRAGMENT_ELEMENTS).isEmpty());
    }

    private static Map<String, DumAemSampleModelItem> map(String key, DumAemSampleModelItem item) {
        Map<String, DumAemSampleModelItem> m = new LinkedHashMap<>();
        m.put(key, item);
        return m;
    }
}

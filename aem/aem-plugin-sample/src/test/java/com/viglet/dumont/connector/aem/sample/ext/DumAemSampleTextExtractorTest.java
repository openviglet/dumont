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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.aem.commons.bean.DumAemAttrMap;
import com.viglet.dumont.connector.aem.sample.beans.DumAemSampleModel;
import com.viglet.dumont.connector.aem.sample.beans.DumAemSampleModel.DumAemSampleModelItem;

@DisplayName("DumAemSampleTextExtractor Tests")
class DumAemSampleTextExtractorTest {

    private final DumAemSampleTextExtractor extractor = new DumAemSampleTextExtractor();

    @Test
    @DisplayName("Should collect HTML-stripped non-blank text fields")
    void shouldCollectTexts() {
        DumAemSampleModelItem withText = new DumAemSampleModelItem();
        withText.setText("<span>Hello</span>");
        DumAemSampleModelItem blank = new DumAemSampleModelItem();
        blank.setText("   ");

        Map<String, DumAemSampleModelItem> items = new LinkedHashMap<>();
        items.put("a", withText);
        items.put("b", blank);

        DumAemSampleModel model = new DumAemSampleModel();
        model.setItems(items);

        DumAemAttrMap attrs = new DumAemAttrMap();
        extractor.contribute(model, null, null, attrs);

        assertEquals(List.of("Hello"), attrs.get(DumAemExtSampleModelJson.TEXTS));
    }

    @Test
    @DisplayName("Should contribute nothing when model has no items")
    void shouldContributeNothingWhenNoItems() {
        DumAemSampleModel model = new DumAemSampleModel();

        DumAemAttrMap attrs = new DumAemAttrMap();
        extractor.contribute(model, null, null, attrs);

        assertFalse(attrs.containsKey(DumAemExtSampleModelJson.TEXTS));
    }

    @Test
    @DisplayName("Should set empty value when items exist without text")
    void shouldSetEmptyWhenNoText() {
        Map<String, DumAemSampleModelItem> items = new LinkedHashMap<>();
        items.put("a", new DumAemSampleModelItem());

        DumAemSampleModel model = new DumAemSampleModel();
        model.setItems(items);

        DumAemAttrMap attrs = new DumAemAttrMap();
        extractor.contribute(model, null, null, attrs);

        assertTrue(attrs.containsKey(DumAemExtSampleModelJson.TEXTS));
        assertTrue(attrs.get(DumAemExtSampleModelJson.TEXTS).isEmpty());
    }
}

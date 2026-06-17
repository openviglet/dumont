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
import com.viglet.turing.client.sn.TurMultiValue;

@DisplayName("DumAemSampleParagraphExtractor Tests")
class DumAemSampleParagraphExtractorTest {

    private final DumAemSampleParagraphExtractor extractor = new DumAemSampleParagraphExtractor();

    @Test
    @DisplayName("Should collect HTML-stripped paragraphs from nested items")
    void shouldCollectParagraphs() {
        DumAemSampleModelItem child = new DumAemSampleModelItem();
        child.setParagraphs(List.of("<p>Deep nested</p>"));

        DumAemSampleModelItem parent = new DumAemSampleModelItem();
        parent.setParagraphs(List.of("<b>Hello</b> world", "   "));
        parent.setItems(itemMap("child", child));

        DumAemSampleModel model = new DumAemSampleModel();
        model.setItems(itemMap("parent", parent));

        DumAemAttrMap attrs = new DumAemAttrMap();
        extractor.contribute(model, null, null, attrs);

        TurMultiValue paragraphs = attrs.get(DumAemExtSampleModelJson.PARAGRAPHS);
        assertEquals(List.of("Hello world", "Deep nested"), paragraphs);
    }

    @Test
    @DisplayName("Should set an empty value when items exist but no paragraphs")
    void shouldSetEmptyWhenNoParagraphs() {
        DumAemSampleModel model = new DumAemSampleModel();
        model.setItems(itemMap("only", new DumAemSampleModelItem()));

        DumAemAttrMap attrs = new DumAemAttrMap();
        extractor.contribute(model, null, null, attrs);

        assertTrue(attrs.containsKey(DumAemExtSampleModelJson.PARAGRAPHS));
        assertTrue(attrs.get(DumAemExtSampleModelJson.PARAGRAPHS).isEmpty());
    }

    @Test
    @DisplayName("Should contribute nothing when model has no items")
    void shouldContributeNothingWhenNoItems() {
        DumAemSampleModel model = new DumAemSampleModel();

        DumAemAttrMap attrs = new DumAemAttrMap();
        extractor.contribute(model, null, null, attrs);

        assertFalse(attrs.containsKey(DumAemExtSampleModelJson.PARAGRAPHS));
    }

    private static Map<String, DumAemSampleModelItem> itemMap(String key, DumAemSampleModelItem item) {
        Map<String, DumAemSampleModelItem> map = new LinkedHashMap<>();
        map.put(key, item);
        return map;
    }
}

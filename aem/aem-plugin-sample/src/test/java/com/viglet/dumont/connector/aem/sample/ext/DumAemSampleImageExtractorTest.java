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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.aem.commons.bean.DumAemAttrMap;
import com.viglet.dumont.connector.aem.sample.beans.DumAemSampleModel;
import com.viglet.dumont.connector.aem.sample.beans.DumAemSampleModel.DumAemSampleModelItem;

@DisplayName("DumAemSampleImageExtractor Tests")
class DumAemSampleImageExtractorTest {

    private final DumAemSampleImageExtractor extractor = new DumAemSampleImageExtractor();

    @Test
    @DisplayName("Should collect non-blank image src values, src kept verbatim")
    void shouldCollectImages() {
        DumAemSampleModelItem img = new DumAemSampleModelItem();
        img.setSrc("/content/dam/a.png");
        DumAemSampleModelItem blank = new DumAemSampleModelItem();
        blank.setSrc("  ");

        Map<String, DumAemSampleModelItem> items = new LinkedHashMap<>();
        items.put("a", img);
        items.put("b", blank);

        DumAemSampleModel model = new DumAemSampleModel();
        model.setItems(items);

        DumAemAttrMap attrs = new DumAemAttrMap();
        extractor.contribute(model, null, null, attrs);

        assertEquals(List.of("/content/dam/a.png"), attrs.get(DumAemExtSampleModelJson.IMAGES));
    }

    @Test
    @DisplayName("Should contribute nothing when model has no items")
    void shouldContributeNothingWhenNoItems() {
        DumAemSampleModel model = new DumAemSampleModel();

        DumAemAttrMap attrs = new DumAemAttrMap();
        extractor.contribute(model, null, null, attrs);

        assertFalse(attrs.containsKey(DumAemExtSampleModelJson.IMAGES));
    }
}

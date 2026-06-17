/*
 *
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

package com.viglet.dumont.connector.aem.sample.ext;

import static com.viglet.dumont.connector.aem.sample.ext.DumAemExtSampleModelJson.CONTENT_FRAGMENT_ELEMENTS;
import static com.viglet.dumont.connector.aem.sample.ext.DumAemExtSampleModelJson.CONTENT_FRAGMENT_MODELS;
import static com.viglet.dumont.connector.aem.sample.ext.DumAemExtSampleModelJson.CONTENT_FRAGMENT_TITLES;

import java.util.ArrayList;
import java.util.List;

import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.bean.DumAemAttrMap;
import com.viglet.dumont.connector.aem.commons.ext.DumAemComponentExtractor;
import com.viglet.dumont.connector.aem.commons.ext.DumAemModelJsonQuery;
import com.viglet.dumont.connector.aem.sample.beans.DumAemSampleModel;
import com.viglet.dumont.connector.aem.sample.beans.DumAemSampleModel.DumAemSampleModelElement;
import com.viglet.dumont.connector.aem.sample.beans.DumAemSampleModel.DumAemSampleModelItem;

/**
 * Collects content-fragment titles, models and element values from every
 * {@code contentfragment} item in the model tree.
 */
public class DumAemSampleContentFragmentExtractor
        implements DumAemComponentExtractor<DumAemSampleModel> {

    @Override
    public void contribute(DumAemSampleModel model, DumAemModelJsonQuery query,
            DumAemObject aemObject, DumAemAttrMap attrValues) {
        if (model.getItems() == null) {
            return;
        }
        List<String> titles = new ArrayList<>();
        List<String> models = new ArrayList<>();
        List<String> elements = new ArrayList<>();
        DumAemSampleItems.forEach(model, item -> collectFragment(item, titles, models, elements));
        attrValues.setAll(CONTENT_FRAGMENT_TITLES, titles)
                .setAll(CONTENT_FRAGMENT_MODELS, models)
                .setAll(CONTENT_FRAGMENT_ELEMENTS, elements);
    }

    private static void collectFragment(DumAemSampleModelItem item, List<String> titles,
            List<String> models, List<String> elements) {
        String type = item.getType();
        if (type == null || !type.contains("contentfragment")) {
            return;
        }
        if (item.getTitle() != null && !item.getTitle().isBlank()) {
            titles.add(item.getTitle());
        }
        if (item.getModel() != null && !item.getModel().isBlank()) {
            models.add(item.getModel());
        }
        if (item.getElements() == null) {
            return;
        }
        for (DumAemSampleModelElement element : item.getElements().values()) {
            if (element.getValue() != null && !element.getValue().isBlank()) {
                elements.add(DumAemSampleItems.stripHtml(element.getValue()));
            }
        }
    }
}

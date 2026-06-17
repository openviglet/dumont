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

import static com.viglet.dumont.connector.aem.sample.ext.DumAemExtSampleModelJson.TEXTS;

import java.util.ArrayList;
import java.util.List;

import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.bean.DumAemAttrMap;
import com.viglet.dumont.connector.aem.commons.ext.DumAemComponentExtractor;
import com.viglet.dumont.connector.aem.commons.ext.DumAemModelJsonQuery;
import com.viglet.dumont.connector.aem.sample.beans.DumAemSampleModel;

/**
 * Collects the (HTML-stripped, non-blank) {@code text} field from every item in
 * the model tree.
 */
public class DumAemSampleTextExtractor implements DumAemComponentExtractor<DumAemSampleModel> {

    @Override
    public void contribute(DumAemSampleModel model, DumAemModelJsonQuery query,
            DumAemObject aemObject, DumAemAttrMap attrValues) {
        if (model.getItems() == null) {
            return;
        }
        List<String> texts = new ArrayList<>();
        DumAemSampleItems.forEach(model, item -> {
            if (item.getText() != null && !item.getText().isBlank()) {
                texts.add(DumAemSampleItems.stripHtml(item.getText()));
            }
        });
        attrValues.setAll(TEXTS, texts);
    }
}

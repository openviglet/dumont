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

import static com.viglet.dumont.connector.aem.sample.ext.DumAemExtSampleModelJson.FRAGMENT_PATH;
import static com.viglet.dumont.connector.aem.sample.ext.DumAemExtSampleModelJson.PAGE_DESCRIPTION;
import static com.viglet.dumont.connector.aem.sample.ext.DumAemExtSampleModelJson.PAGE_LANGUAGE;
import static com.viglet.dumont.connector.aem.sample.ext.DumAemExtSampleModelJson.PAGE_LAST_MODIFIED;
import static com.viglet.dumont.connector.aem.sample.ext.DumAemExtSampleModelJson.PAGE_TEMPLATE;
import static com.viglet.dumont.connector.aem.sample.ext.DumAemExtSampleModelJson.PAGE_TITLE;

import java.util.Date;

import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.bean.DumAemAttrMap;
import com.viglet.dumont.connector.aem.commons.ext.DumAemComponentExtractor;
import com.viglet.dumont.connector.aem.commons.ext.DumAemModelJsonQuery;
import com.viglet.dumont.connector.aem.sample.beans.DumAemSampleModel;

/**
 * Contributes the page-level fields (fragment path, title, description,
 * language, template and last-modified date) from the model root.
 */
public class DumAemSamplePageExtractor implements DumAemComponentExtractor<DumAemSampleModel> {

    @Override
    public void contribute(DumAemSampleModel model, DumAemModelJsonQuery query,
            DumAemObject aemObject, DumAemAttrMap attrValues) {
        attrValues.set(FRAGMENT_PATH, model.getFragmentPath())
                .set(PAGE_TITLE, model.getTitle())
                .set(PAGE_DESCRIPTION, model.getDescription())
                .set(PAGE_LANGUAGE, model.getLanguage())
                .set(PAGE_TEMPLATE, model.getTemplateName());

        if (model.getLastModifiedDate() != null) {
            attrValues.set(PAGE_LAST_MODIFIED, new Date(model.getLastModifiedDate()));
        }
    }
}

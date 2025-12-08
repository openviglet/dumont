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

import java.io.IOException;

import com.viglet.dumont.connector.aem.commons.DumAemCommonsUtils;
import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.bean.DumAemTargetAttrValueMap;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.ext.DumAemExtContentInterface;
import com.viglet.dumont.connector.aem.sample.beans.DumAemSampleModel;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DumAemExtSampleModelJson implements DumAemExtContentInterface {
    public static final String FRAGMENT_PATH = "fragmentPath";
    public static final String MODEL_JSON_EXTENSION = ".model.json";

    @Override
    public DumAemTargetAttrValueMap consume(DumAemObject aemObject,
            DumAemConfiguration dumAemSourceContext) {
        log.debug("Executing DumAemExtSampleModelJson");
        String url = dumAemSourceContext.getUrl() + aemObject.getPath() + MODEL_JSON_EXTENSION;

        try {
            return DumAemCommonsUtils
                    .getResponseBody(url, dumAemSourceContext, DumAemSampleModel.class, false)
                    .map(model -> {
                        DumAemTargetAttrValueMap attrValues = new DumAemTargetAttrValueMap();
                        getFragmentData(attrValues, model);
                        return attrValues;
                    })
                    .orElseGet(DumAemTargetAttrValueMap::new);
        } catch (IOException e) {
            log.error("Error consuming AEM model JSON from: {}", url, e);
            return new DumAemTargetAttrValueMap();
        }
    }

    private static void getFragmentData(DumAemTargetAttrValueMap attrValues,
            DumAemSampleModel model) {
        attrValues.addWithSingleValue(FRAGMENT_PATH, model.getFragmentPath(), true);
    }
}

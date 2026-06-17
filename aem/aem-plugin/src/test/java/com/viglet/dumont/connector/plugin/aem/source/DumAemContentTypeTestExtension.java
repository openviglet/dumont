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

package com.viglet.dumont.connector.plugin.aem.source;

import java.util.List;

import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.ext.DumAemComponentExtractor;
import com.viglet.dumont.connector.aem.commons.ext.DumAemContentType;
import com.viglet.dumont.connector.aem.commons.ext.DumAemContentTypeField;
import com.viglet.dumont.connector.aem.commons.ext.DumAemExtModelJsonBase;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * A public, no-arg test extension declaring two content types (roadmap <strong>D25</strong>), so the
 * custom-class cache can instantiate it by name and {@link DumAemStructuredSource} can read its
 * per-type schemas. Not an AEM extension at runtime — a fixture for the adapter test.
 */
public class DumAemContentTypeTestExtension extends DumAemExtModelJsonBase<String> {

    @Override
    protected Class<String> getModelClass() {
        return String.class;
    }

    @Override
    protected String templateName(String model, DumAemObject aemObject) {
        return model;
    }

    @Override
    protected List<DumAemContentType<String>> contentTypes() {
        DumAemComponentExtractor<String> noop = (m, q, o, attrs) -> { /* fixture */ };
        return List.of(
                DumAemContentType.<String>builder("article")
                        .fields(
                                DumAemContentTypeField.text("articleTitle").asMandatory(),
                                DumAemContentTypeField.text("articleBody"))
                        .extractor(noop).build(),
                DumAemContentType.<String>builder("event")
                        .fields(
                                DumAemContentTypeField.text("articleTitle").asMandatory(),
                                DumAemContentTypeField.of("eventDate", TurSEFieldType.DATE))
                        .extractor(noop).build());
    }
}

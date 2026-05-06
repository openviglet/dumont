/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
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

import org.pf4j.Extension;

import com.viglet.dumont.connector.aem.commons.DumAemObjectGeneric;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.ext.DumAemExtDependencyInterface;

/**
 * Sample business rule for deciding whether a given WKND content should have
 * its {@code /content} dependencies (collected from infinity.json and
 * model.json) extracted and indexed.
 * <p>
 * Rule in this example: skip dependency extraction for FAQ pages under
 * {@code /content/wknd/**\/faqs} — they are leaf content that only references
 * static assets and do not benefit from reverse-indexing. Every other page
 * under WKND opts in.
 */
@Extension
public class DumAemExtSampleDependency implements DumAemExtDependencyInterface {

    private static final String FAQS_PATH_SEGMENT = "/faqs";

    @Override
    public boolean consume(DumAemObjectGeneric aemObject,
            DumAemConfiguration dumAemConfiguration) {
        String path = aemObject.getPath();
        if (path == null) {
            return true;
        }
        return !path.contains(FAQS_PATH_SEGMENT);
    }
}

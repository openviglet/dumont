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

import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;

import com.viglet.dumont.connector.aem.commons.DumAemObjectGeneric;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.ext.DumAemExtIndexConditionInterface;

/**
 * Sample business rule for deciding whether a given content should be indexed
 * at all, based on its {@code cq:template}
 * ({@link DumAemObjectGeneric#getTemplate()}).
 * <p>
 * Rule in this example: skip any content whose template name is {@code teste},
 * so test/scratch pages never reach the search index. Every other template
 * opts in. AEM usually stores {@code cq:template} as a full path
 * (e.g. {@code /conf/wknd/settings/wcm/templates/teste}), so the last path
 * segment is compared as well as the raw value.
 * <p>
 * Wire it by setting {@code indexConditionClass} on the AEM source to this
 * class' fully-qualified name.
 */
@Extension
public class DumAemExtSampleIndexCondition implements DumAemExtIndexConditionInterface {

    private static final String EXCLUDED_TEMPLATE = "teste";

    @Override
    public boolean consume(DumAemObjectGeneric aemObject,
            DumAemConfiguration dumAemConfiguration) {
        String template = aemObject.getTemplate();
        if (StringUtils.isBlank(template)) {
            return true;
        }
        return !EXCLUDED_TEMPLATE.equals(StringUtils.substringAfterLast(template, "/"))
                && !EXCLUDED_TEMPLATE.equals(template);
    }
}

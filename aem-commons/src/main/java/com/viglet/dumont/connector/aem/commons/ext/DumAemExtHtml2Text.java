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
package com.viglet.dumont.connector.aem.commons.ext;

import com.viglet.dumont.commons.utils.DumCommonsUtils;
import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemSourceAttr;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemTargetAttr;
import com.viglet.turing.client.sn.TurMultiValue;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DumAemExtHtml2Text implements DumAemExtAttributeInterface {
    private static final String EMPTY_STRING = "";

    @Override
    public TurMultiValue consume(DumAemTargetAttr dumAemTargetAttr,
            DumAemSourceAttr dumAemSourceAttr, DumAemObject aemObject,
            DumAemConfiguration dumAemConfiguration) {
        log.debug("Executing DumAemExtHtml2Text");
        if (dumAemSourceAttr.getName() != null && aemObject != null
                && aemObject.getAttributes() != null
                && aemObject.getAttributes().containsKey(dumAemSourceAttr.getName())
                && aemObject.getAttributes().get(dumAemSourceAttr.getName()) != null) {
            return TurMultiValue.singleItem(DumCommonsUtils.html2Text(
                    aemObject.getAttributes().get(dumAemSourceAttr.getName()).toString()));
        }
        return TurMultiValue.singleItem(EMPTY_STRING);
    }
}

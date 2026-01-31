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

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import org.jspecify.annotations.NonNull;

import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.bean.DumAemContentTag;
import com.viglet.dumont.connector.aem.commons.bean.DumAemContentTags;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemSourceAttr;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemTargetAttr;
import com.viglet.dumont.connector.aem.commons.utils.DumAemCommonsUtils;
import com.viglet.turing.client.sn.TurMultiValue;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DumAemExtContentTags implements DumAemExtAttributeInterface {
    public static final String TAGS_JSON_EXTENSION = "/jcr:content.tags.json";

    @Override
    public TurMultiValue consume(DumAemTargetAttr dumAemTargetAttr,
            DumAemSourceAttr dumAemSourceAttr, DumAemObject aemObject,
            DumAemConfiguration dumAemConfiguration) {
        log.debug("Executing DumAemExtContentTags");
        return new TurMultiValue(new TurMultiValue(getTags(aemObject, dumAemConfiguration)
                .map(t -> t.getTags().stream().map(DumAemContentTag::getTagID).toList())
                .orElse(Collections.emptyList())));
    }

    public static Optional<@NonNull DumAemContentTags> getTags(DumAemObject aemObject,
            DumAemConfiguration dumAemSourceContext) {
        String url = dumAemSourceContext.getUrl() + aemObject.getPath() + TAGS_JSON_EXTENSION;
        try {
            return DumAemCommonsUtils.getResponseBody(url, dumAemSourceContext, DumAemContentTags.class,
                    true);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return Optional.empty();
        }
    }
}

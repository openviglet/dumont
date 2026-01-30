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

import java.net.URI;
import java.net.URISyntaxException;

import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemSourceAttr;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemTargetAttr;
import com.viglet.turing.client.sn.TurMultiValue;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DumAemExtContentUrl implements DumAemExtUrlAttributeInterface {
    public static String getURL(DumAemObject aemObject,
            DumAemConfiguration dumAemConfiguration) {
        return String.format("%s%s.html", aemObject.getUrlPrefix(dumAemConfiguration),
                aemObject.getPath());
    }

    @Override
    public TurMultiValue consume(DumAemTargetAttr dumAemTargetAttr,
            DumAemSourceAttr dumAemSourceAttr, DumAemObject aemObject,
            DumAemConfiguration dumAemConfiguration) {
        log.debug("Executing DumAemExtContentUrl");
        return TurMultiValue.singleItem(getURL(aemObject, dumAemConfiguration));
    }

    @Override
    public String getIdFromUrl(String url, DumAemConfiguration dumAemConfiguration) {
        try {
            URI uri = new URI(url);
            // getPath() ignores Query Strings (?) and Fragments (#) automatically
            String path = uri.getPath();

            if (path == null || path.isEmpty()) {
                return "/";
            }

            int lastSlash = path.lastIndexOf('/');
            int lastDot = path.lastIndexOf('.');

            // Ensure we only remove the extension if the dot is part of the filename
            if (lastDot > lastSlash) {
                return path.substring(0, lastDot);
            }

            return path;
        } catch (URISyntaxException e) {
            return "Invalid URL";
        }
    }
}

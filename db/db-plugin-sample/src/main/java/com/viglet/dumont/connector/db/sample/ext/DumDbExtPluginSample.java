/*
 * Copyright (C) 2016-2024 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.db.sample.ext;

import com.viglet.dumont.connector.db.ext.DumDbExtCustomImpl;

import java.sql.Connection;
import java.util.Map;

/**
 * Sample custom implementation for the DB Plugin.
 * <p>
 * This class demonstrates how to create a custom row processor that modifies
 * database record attributes before they are sent to the indexing engine.
 * </p>
 * <p>
 * To use this class, set the "Custom Class Name" field in the DB Source
 * configuration UI to: {@code com.viglet.dumont.connector.db.sample.ext.DumDbExtPluginSample}
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public class DumDbExtPluginSample implements DumDbExtCustomImpl {

    private static final String TITLE = "title";
    private static final String DESCRIPTION = "description";
    private static final String SOURCE_APPS = "sourceApps";

    @Override
    public Map<String, Object> run(Connection connection, Map<String, Object> attributes) {
        prefixTitle(attributes);
        addDefaultDescription(attributes);
        tagSourceApp(attributes);
        return attributes;
    }

    private void prefixTitle(Map<String, Object> attributes) {
        if (attributes.containsKey(TITLE)) {
            attributes.replace(TITLE, String.format("[DB Sample] %s", attributes.get(TITLE)));
        }
    }

    private void addDefaultDescription(Map<String, Object> attributes) {
        if (!attributes.containsKey(DESCRIPTION) || attributes.get(DESCRIPTION) == null) {
            Object title = attributes.get(TITLE);
            if (title != null) {
                attributes.put(DESCRIPTION, String.format("Auto-generated description for: %s", title));
            }
        }
    }

    private void tagSourceApp(Map<String, Object> attributes) {
        attributes.put(SOURCE_APPS, "dumont-jdbc-sample");
    }
}

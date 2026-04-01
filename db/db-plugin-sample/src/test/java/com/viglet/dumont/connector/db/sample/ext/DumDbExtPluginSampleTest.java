/*
 * Copyright (C) 2016-2025 the original author or authors.
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

package com.viglet.dumont.connector.db.sample.ext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.db.ext.DumDbExtCustomImpl;

@DisplayName("DumDbExtPluginSample Tests")
class DumDbExtPluginSampleTest {

    private DumDbExtPluginSample pluginSample;

    @BeforeEach
    void setUp() {
        pluginSample = new DumDbExtPluginSample();
    }

    @Nested
    @DisplayName("Title Prefix Tests")
    class TitlePrefixTests {

        @Test
        @DisplayName("Should prefix title with [DB Sample]")
        void shouldPrefixTitle() {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("title", "My Article");

            Map<String, Object> result = pluginSample.run(null, attributes);

            assertEquals("[DB Sample] My Article", result.get("title"));
        }

        @Test
        @DisplayName("Should not fail when title is missing")
        void shouldNotFailWhenTitleMissing() {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("id", "123");

            Map<String, Object> result = pluginSample.run(null, attributes);

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("Description Tests")
    class DescriptionTests {

        @Test
        @DisplayName("Should add default description when missing")
        void shouldAddDefaultDescription() {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("title", "My Article");

            Map<String, Object> result = pluginSample.run(null, attributes);

            assertEquals("Auto-generated description for: [DB Sample] My Article",
                    result.get("description"));
        }

        @Test
        @DisplayName("Should not overwrite existing description")
        void shouldNotOverwriteExistingDescription() {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("title", "My Article");
            attributes.put("description", "Existing description");

            Map<String, Object> result = pluginSample.run(null, attributes);

            assertEquals("Existing description", result.get("description"));
        }
    }

    @Nested
    @DisplayName("Source App Tag Tests")
    class SourceAppTests {

        @Test
        @DisplayName("Should tag sourceApps as dumont-jdbc-sample")
        void shouldTagSourceApps() {
            Map<String, Object> attributes = new HashMap<>();

            Map<String, Object> result = pluginSample.run(null, attributes);

            assertEquals("dumont-jdbc-sample", result.get("sourceApps"));
        }
    }

    @Nested
    @DisplayName("Interface Implementation Tests")
    class InterfaceTests {

        @Test
        @DisplayName("Should implement DumDbExtCustomImpl")
        void shouldImplementDumDbExtCustomImpl() {
            assertTrue(pluginSample instanceof DumDbExtCustomImpl);
        }
    }
}

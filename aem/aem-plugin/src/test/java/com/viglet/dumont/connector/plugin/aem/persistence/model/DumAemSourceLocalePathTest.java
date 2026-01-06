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

package com.viglet.dumont.connector.plugin.aem.persistence.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DumAemSourceLocalePath Tests")
class DumAemSourceLocalePathTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should build locale path with all fields")
        void shouldBuildLocalePathWithAllFields() {
            DumAemSource source = DumAemSource.builder()
                    .id("source-id")
                    .name("test-source")
                    .build();

            DumAemSourceLocalePath localePath = DumAemSourceLocalePath.builder()
                    .id("path-id")
                    .locale(Locale.US)
                    .path("/content/mysite/en")
                    .dumAemSource(source)
                    .build();

            assertEquals("path-id", localePath.getId());
            assertEquals(Locale.US, localePath.getLocale());
            assertEquals("/content/mysite/en", localePath.getPath());
            assertNotNull(localePath.getDumAemSource());
        }

        @Test
        @DisplayName("Should build locale path with minimal fields")
        void shouldBuildLocalePathWithMinimalFields() {
            DumAemSourceLocalePath localePath = DumAemSourceLocalePath.builder()
                    .path("/content/test")
                    .build();

            assertEquals("/content/test", localePath.getPath());
            assertNull(localePath.getId());
            assertNull(localePath.getLocale());
        }
    }

    @Nested
    @DisplayName("NoArgs Constructor Tests")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("Should create locale path with default values")
        void shouldCreateLocalePathWithDefaultValues() {
            DumAemSourceLocalePath localePath = new DumAemSourceLocalePath();

            assertNull(localePath.getId());
            assertNull(localePath.getLocale());
            assertNull(localePath.getPath());
            assertNull(localePath.getDumAemSource());
        }
    }

    @Nested
    @DisplayName("Getter and Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Should set and get id")
        void shouldSetAndGetId() {
            DumAemSourceLocalePath localePath = new DumAemSourceLocalePath();
            localePath.setId("new-id");

            assertEquals("new-id", localePath.getId());
        }

        @Test
        @DisplayName("Should set and get locale")
        void shouldSetAndGetLocale() {
            DumAemSourceLocalePath localePath = new DumAemSourceLocalePath();
            localePath.setLocale(Locale.FRANCE);

            assertEquals(Locale.FRANCE, localePath.getLocale());
        }

        @Test
        @DisplayName("Should set and get path")
        void shouldSetAndGetPath() {
            DumAemSourceLocalePath localePath = new DumAemSourceLocalePath();
            localePath.setPath("/content/new/path");

            assertEquals("/content/new/path", localePath.getPath());
        }

        @Test
        @DisplayName("Should set and get source")
        void shouldSetAndGetSource() {
            DumAemSourceLocalePath localePath = new DumAemSourceLocalePath();
            DumAemSource source = DumAemSource.builder().name("test").build();
            localePath.setDumAemSource(source);

            assertEquals(source, localePath.getDumAemSource());
        }
    }

    @Nested
    @DisplayName("toBuilder Tests")
    class ToBuilderTests {

        @Test
        @DisplayName("Should create builder from existing locale path")
        void shouldCreateBuilderFromExistingLocalePath() {
            DumAemSourceLocalePath original = DumAemSourceLocalePath.builder()
                    .id("original-id")
                    .locale(Locale.US)
                    .path("/content/original")
                    .build();

            DumAemSourceLocalePath copy = original.toBuilder()
                    .path("/content/modified")
                    .build();

            assertEquals("original-id", copy.getId());
            assertEquals(Locale.US, copy.getLocale());
            assertEquals("/content/modified", copy.getPath());
        }
    }

    @Nested
    @DisplayName("Serializable Tests")
    class SerializableTests {

        @Test
        @DisplayName("Should be serializable")
        void shouldBeSerializable() {
            DumAemSourceLocalePath localePath = DumAemSourceLocalePath.builder()
                    .path("/content/test")
                    .build();

            assertTrue(localePath instanceof java.io.Serializable);
        }
    }
}

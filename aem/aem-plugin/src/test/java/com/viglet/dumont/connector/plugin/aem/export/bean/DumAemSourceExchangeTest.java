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

package com.viglet.dumont.connector.plugin.aem.export.bean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DumAemSourceExchange Tests")
class DumAemSourceExchangeTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should create instance with builder")
        void shouldCreateInstanceWithBuilder() {
            DumAemSourceExchange exchange = DumAemSourceExchange.builder()
                    .id("source-id")
                    .name("Source Name")
                    .defaultLocale(Locale.US)
                    .localeClass("com.example.LocaleClass")
                    .deltaClass("com.example.DeltaClass")
                    .endpoint("http://localhost:4502")
                    .oncePattern("*.json")
                    .username("admin")
                    .password("password")
                    .rootPath("/content/mysite")
                    .contentType("cq:Page")
                    .author(true)
                    .publish(false)
                    .authorSNSite("authorSite")
                    .publishSNSite("publishSite")
                    .authorURLPrefix("http://author")
                    .publishURLPrefix("http://publish")
                    .build();

            assertEquals("source-id", exchange.getId());
            assertEquals("Source Name", exchange.getName());
            assertEquals(Locale.US, exchange.getDefaultLocale());
            assertEquals("com.example.LocaleClass", exchange.getLocaleClass());
            assertEquals("com.example.DeltaClass", exchange.getDeltaClass());
            assertEquals("http://localhost:4502", exchange.getEndpoint());
            assertEquals("*.json", exchange.getOncePattern());
            assertEquals("admin", exchange.getUsername());
            assertEquals("password", exchange.getPassword());
            assertEquals("/content/mysite", exchange.getRootPath());
            assertEquals("cq:Page", exchange.getContentType());
            assertTrue(exchange.isAuthor());
            assertFalse(exchange.isPublish());
            assertEquals("authorSite", exchange.getAuthorSNSite());
            assertEquals("publishSite", exchange.getPublishSNSite());
            assertEquals("http://author", exchange.getAuthorURLPrefix());
            assertEquals("http://publish", exchange.getPublishURLPrefix());
        }

        @Test
        @DisplayName("Should initialize collections with empty HashSet by default")
        void shouldInitializeCollectionsWithEmptyHashSetByDefault() {
            DumAemSourceExchange exchange = DumAemSourceExchange.builder().build();

            assertNotNull(exchange.getAttributes());
            assertNotNull(exchange.getModels());
            assertNotNull(exchange.getLocalePaths());
            assertTrue(exchange.getAttributes().isEmpty());
            assertTrue(exchange.getModels().isEmpty());
            assertTrue(exchange.getLocalePaths().isEmpty());
        }

        @Test
        @DisplayName("Should support toBuilder")
        void shouldSupportToBuilder() {
            DumAemSourceExchange original = DumAemSourceExchange.builder()
                    .id("original-id")
                    .name("Original Name")
                    .build();

            DumAemSourceExchange modified = original.toBuilder()
                    .name("Modified Name")
                    .build();

            assertEquals("original-id", modified.getId());
            assertEquals("Modified Name", modified.getName());
        }
    }

    @Nested
    @DisplayName("No Args Constructor Tests")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("Should create instance with no args constructor")
        void shouldCreateInstanceWithNoArgsConstructor() {
            DumAemSourceExchange exchange = new DumAemSourceExchange();

            assertNotNull(exchange);
            assertNull(exchange.getId());
            assertNull(exchange.getName());
        }
    }

    @Nested
    @DisplayName("Getter and Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Should set and get id")
        void shouldSetAndGetId() {
            DumAemSourceExchange exchange = new DumAemSourceExchange();
            exchange.setId("test-id");

            assertEquals("test-id", exchange.getId());
        }

        @Test
        @DisplayName("Should set and get name")
        void shouldSetAndGetName() {
            DumAemSourceExchange exchange = new DumAemSourceExchange();
            exchange.setName("Test Source");

            assertEquals("Test Source", exchange.getName());
        }

        @Test
        @DisplayName("Should set and get defaultLocale")
        void shouldSetAndGetDefaultLocale() {
            DumAemSourceExchange exchange = new DumAemSourceExchange();
            exchange.setDefaultLocale(Locale.GERMANY);

            assertEquals(Locale.GERMANY, exchange.getDefaultLocale());
        }

        @Test
        @DisplayName("Should set and get attributes")
        void shouldSetAndGetAttributes() {
            DumAemSourceExchange exchange = new DumAemSourceExchange();
            HashSet<DumAemAttribExchange> attributes = new HashSet<>();
            attributes.add(DumAemAttribExchange.builder().name("attr1").build());
            exchange.setAttributes(attributes);

            assertEquals(1, exchange.getAttributes().size());
        }

        @Test
        @DisplayName("Should set and get models")
        void shouldSetAndGetModels() {
            DumAemSourceExchange exchange = new DumAemSourceExchange();
            HashSet<DumAemModelExchange> models = new HashSet<>();
            models.add(DumAemModelExchange.builder().type("cq:Page").build());
            exchange.setModels(models);

            assertEquals(1, exchange.getModels().size());
        }

        @Test
        @DisplayName("Should set and get localePaths")
        void shouldSetAndGetLocalePaths() {
            DumAemSourceExchange exchange = new DumAemSourceExchange();
            HashSet<DumAemSourceLocalePathExchange> localePaths = new HashSet<>();
            localePaths.add(DumAemSourceLocalePathExchange.builder()
                    .locale(Locale.US)
                    .path("/content/en")
                    .build());
            exchange.setLocalePaths(localePaths);

            assertEquals(1, exchange.getLocalePaths().size());
        }

        @Test
        @DisplayName("Should set and get author flag")
        void shouldSetAndGetAuthorFlag() {
            DumAemSourceExchange exchange = new DumAemSourceExchange();
            exchange.setAuthor(true);

            assertTrue(exchange.isAuthor());
        }

        @Test
        @DisplayName("Should set and get publish flag")
        void shouldSetAndGetPublishFlag() {
            DumAemSourceExchange exchange = new DumAemSourceExchange();
            exchange.setPublish(true);

            assertTrue(exchange.isPublish());
        }
    }
}

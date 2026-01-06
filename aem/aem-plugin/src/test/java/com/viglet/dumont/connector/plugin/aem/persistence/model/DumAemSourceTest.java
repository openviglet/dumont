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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DumAemSource Tests")
class DumAemSourceTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should build source with all fields")
        void shouldBuildSourceWithAllFields() {
            DumAemSource source = DumAemSource.builder()
                    .id("source-123")
                    .name("aem-source")
                    .endpoint("http://localhost:4502")
                    .username("admin")
                    .password("admin")
                    .rootPath("/content/mysite")
                    .contentType("cq:Page")
                    .subType("page")
                    .oncePattern("*/jcr:content/cq:lastModified")
                    .defaultLocale(Locale.US)
                    .localeClass("com.example.LocaleHandler")
                    .deltaClass("com.example.DeltaHandler")
                    .author(true)
                    .publish(true)
                    .authorSNSite("author-site")
                    .publishSNSite("publish-site")
                    .authorURLPrefix("http://author.example.com")
                    .publishURLPrefix("http://publish.example.com")
                    .build();

            assertEquals("source-123", source.getId());
            assertEquals("aem-source", source.getName());
            assertEquals("http://localhost:4502", source.getEndpoint());
            assertEquals("admin", source.getUsername());
            assertEquals("admin", source.getPassword());
            assertEquals("/content/mysite", source.getRootPath());
            assertEquals("cq:Page", source.getContentType());
            assertEquals("page", source.getSubType());
            assertEquals(Locale.US, source.getDefaultLocale());
            assertTrue(source.isAuthor());
            assertTrue(source.isPublish());
            assertEquals("author-site", source.getAuthorSNSite());
            assertEquals("publish-site", source.getPublishSNSite());
        }

        @Test
        @DisplayName("Should build source with minimal fields")
        void shouldBuildSourceWithMinimalFields() {
            DumAemSource source = DumAemSource.builder()
                    .name("test-source")
                    .build();

            assertEquals("test-source", source.getName());
            assertNull(source.getId());
            assertNull(source.getEndpoint());
            assertFalse(source.isAuthor());
            assertFalse(source.isPublish());
            assertNotNull(source.getLocalePaths());
            assertNotNull(source.getAttributeSpecifications());
            assertNotNull(source.getModels());
        }

        @Test
        @DisplayName("Should initialize collections with default empty sets")
        void shouldInitializeCollectionsWithDefaultEmptySets() {
            DumAemSource source = DumAemSource.builder().build();

            assertNotNull(source.getLocalePaths());
            assertTrue(source.getLocalePaths().isEmpty());
            assertNotNull(source.getAttributeSpecifications());
            assertTrue(source.getAttributeSpecifications().isEmpty());
            assertNotNull(source.getModels());
            assertTrue(source.getModels().isEmpty());
        }
    }

    @Nested
    @DisplayName("NoArgs Constructor Tests")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("Should create source with default values")
        void shouldCreateSourceWithDefaultValues() {
            DumAemSource source = new DumAemSource();

            assertNull(source.getId());
            assertNull(source.getName());
            assertNull(source.getEndpoint());
            assertFalse(source.isAuthor());
            assertFalse(source.isPublish());
        }
    }

    @Nested
    @DisplayName("Getter and Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Should set and get id")
        void shouldSetAndGetId() {
            DumAemSource source = new DumAemSource();
            source.setId("new-id");

            assertEquals("new-id", source.getId());
        }

        @Test
        @DisplayName("Should set and get name")
        void shouldSetAndGetName() {
            DumAemSource source = new DumAemSource();
            source.setName("new-name");

            assertEquals("new-name", source.getName());
        }

        @Test
        @DisplayName("Should set and get endpoint")
        void shouldSetAndGetEndpoint() {
            DumAemSource source = new DumAemSource();
            source.setEndpoint("http://newhost:4502");

            assertEquals("http://newhost:4502", source.getEndpoint());
        }

        @Test
        @DisplayName("Should set and get credentials")
        void shouldSetAndGetCredentials() {
            DumAemSource source = new DumAemSource();
            source.setUsername("testuser");
            source.setPassword("testpass");

            assertEquals("testuser", source.getUsername());
            assertEquals("testpass", source.getPassword());
        }

        @Test
        @DisplayName("Should set and get locale paths")
        void shouldSetAndGetLocalePaths() {
            DumAemSource source = new DumAemSource();
            source.setLocalePaths(new HashSet<>());

            assertNotNull(source.getLocalePaths());
        }

        @Test
        @DisplayName("Should set and get author/publish flags")
        void shouldSetAndGetAuthorPublishFlags() {
            DumAemSource source = new DumAemSource();
            source.setAuthor(true);
            source.setPublish(false);

            assertTrue(source.isAuthor());
            assertFalse(source.isPublish());
        }
    }

    @Nested
    @DisplayName("toBuilder Tests")
    class ToBuilderTests {

        @Test
        @DisplayName("Should create builder from existing source")
        void shouldCreateBuilderFromExistingSource() {
            DumAemSource original = DumAemSource.builder()
                    .id("original-id")
                    .name("original-name")
                    .endpoint("http://original:4502")
                    .build();

            DumAemSource copy = original.toBuilder()
                    .name("modified-name")
                    .build();

            assertEquals("original-id", copy.getId());
            assertEquals("modified-name", copy.getName());
            assertEquals("http://original:4502", copy.getEndpoint());
        }
    }

    @Nested
    @DisplayName("ToString Tests")
    class ToStringTests {

        @Test
        @DisplayName("Should have toString implementation")
        void shouldHaveToStringImplementation() {
            DumAemSource source = DumAemSource.builder()
                    .name("test-source")
                    .build();

            String result = source.toString();

            assertNotNull(result);
            assertTrue(result.contains("test-source"));
        }
    }

    @Nested
    @DisplayName("Serializable Tests")
    class SerializableTests {

        @Test
        @DisplayName("Should be serializable")
        void shouldBeSerializable() {
            DumAemSource source = DumAemSource.builder()
                    .name("test")
                    .build();

            assertTrue(source instanceof java.io.Serializable);
        }
    }
}

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

package com.viglet.dumont.connector.aem.commons.context;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DumAemConfiguration Tests")
class DumAemConfigurationTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should build configuration with all fields")
        void shouldBuildConfigurationWithAllFields() {
            DumAemConfiguration config = DumAemConfiguration.builder()
                    .id("test-id")
                    .url("http://localhost:4502")
                    .username("admin")
                    .password("admin")
                    .rootPath("/content/mysite")
                    .contentType("cq:Page")
                    .subType("article")
                    .siteName("My Site")
                    .defaultLocale(Locale.ENGLISH)
                    .providerName("AEM Provider")
                    .authorURLPrefix("https://author.example.com")
                    .publishURLPrefix("https://publish.example.com")
                    .oncePattern("/content/once.*")
                    .author(true)
                    .publish(true)
                    .authorSNSite("author-site")
                    .publishSNSite("publish-site")
                    .localePaths(new HashSet<>())
                    .build();

            assertEquals("test-id", config.getId());
            assertEquals("http://localhost:4502", config.getUrl());
            assertEquals("admin", config.getUsername());
            assertEquals("admin", config.getPassword());
            assertEquals("/content/mysite", config.getRootPath());
            assertEquals("cq:Page", config.getContentType());
            assertEquals("article", config.getSubType());
            assertEquals("My Site", config.getSiteName());
            assertEquals(Locale.ENGLISH, config.getDefaultLocale());
            assertEquals("AEM Provider", config.getProviderName());
            assertEquals("https://author.example.com", config.getAuthorURLPrefix());
            assertEquals("https://publish.example.com", config.getPublishURLPrefix());
            assertEquals("/content/once.*", config.getOncePattern());
            assertTrue(config.isAuthor());
            assertTrue(config.isPublish());
            assertEquals("author-site", config.getAuthorSNSite());
            assertEquals("publish-site", config.getPublishSNSite());
        }

        @Test
        @DisplayName("Should have empty locale paths by default")
        void shouldHaveEmptyLocalePathsByDefault() {
            DumAemConfiguration config = DumAemConfiguration.builder().build();

            assertNotNull(config.getLocalePaths());
            assertTrue(config.getLocalePaths().isEmpty());
        }
    }

    @Nested
    @DisplayName("Copy Constructor Tests")
    class CopyConstructorTests {

        @Test
        @DisplayName("Should copy all fields from another configuration")
        void shouldCopyAllFieldsFromAnotherConfiguration() {
            DumAemConfiguration original = DumAemConfiguration.builder()
                    .id("original-id")
                    .url("http://localhost:4502")
                    .username("admin")
                    .password("admin123")
                    .rootPath("/content/site")
                    .contentType("cq:Page")
                    .subType("page")
                    .siteName("Original Site")
                    .defaultLocale(Locale.FRENCH)
                    .providerName("Provider")
                    .authorURLPrefix("https://author.com")
                    .publishURLPrefix("https://publish.com")
                    .oncePattern("/once.*")
                    .author(true)
                    .publish(false)
                    .authorSNSite("author")
                    .publishSNSite("publish")
                    .localePaths(new HashSet<>())
                    .build();

            DumAemConfiguration copy = new DumAemConfiguration(original);

            assertEquals(original.getId(), copy.getId());
            assertEquals(original.getUrl(), copy.getUrl());
            assertEquals(original.getUsername(), copy.getUsername());
            assertEquals(original.getPassword(), copy.getPassword());
            assertEquals(original.getRootPath(), copy.getRootPath());
            assertEquals(original.getContentType(), copy.getContentType());
            assertEquals(original.getSubType(), copy.getSubType());
            assertEquals(original.getSiteName(), copy.getSiteName());
            assertEquals(original.getDefaultLocale(), copy.getDefaultLocale());
            assertEquals(original.getProviderName(), copy.getProviderName());
            assertEquals(original.getAuthorURLPrefix(), copy.getAuthorURLPrefix());
            assertEquals(original.getPublishURLPrefix(), copy.getPublishURLPrefix());
            assertEquals(original.getOncePattern(), copy.getOncePattern());
            assertEquals(original.isAuthor(), copy.isAuthor());
            assertEquals(original.isPublish(), copy.isPublish());
            assertEquals(original.getAuthorSNSite(), copy.getAuthorSNSite());
            assertEquals(original.getPublishSNSite(), copy.getPublishSNSite());
        }
    }

    @Nested
    @DisplayName("ToBuilder Tests")
    class ToBuilderTests {

        @Test
        @DisplayName("Should create builder from existing configuration")
        void shouldCreateBuilderFromExistingConfiguration() {
            DumAemConfiguration original = DumAemConfiguration.builder()
                    .id("original")
                    .url("http://localhost:4502")
                    .build();

            DumAemConfiguration modified = original.toBuilder()
                    .id("modified")
                    .build();

            assertEquals("modified", modified.getId());
            assertEquals("http://localhost:4502", modified.getUrl());
        }
    }

    @Nested
    @DisplayName("NoArgs Constructor Tests")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("Should create configuration with default values")
        void shouldCreateConfigurationWithDefaultValues() {
            DumAemConfiguration config = new DumAemConfiguration();

            assertNull(config.getId());
            assertNull(config.getUrl());
        }
    }

    @Nested
    @DisplayName("ToString Tests")
    class ToStringTests {

        @Test
        @DisplayName("Should have toString implementation")
        void shouldHaveToStringImplementation() {
            DumAemConfiguration config = DumAemConfiguration.builder()
                    .id("test")
                    .build();

            String result = config.toString();

            assertNotNull(result);
            assertTrue(result.contains("test"));
        }
    }

    @Nested
    @DisplayName("Getter Tests")
    class GetterTests {

        private DumAemConfiguration config;

        @BeforeEach
        void setUp() {
            config = DumAemConfiguration.builder()
                    .id("test-id")
                    .url("http://test.com")
                    .username("user")
                    .password("pass")
                    .rootPath("/content")
                    .contentType("cq:Page")
                    .subType("sub")
                    .siteName("Site")
                    .defaultLocale(Locale.GERMAN)
                    .providerName("Provider")
                    .authorURLPrefix("http://author")
                    .publishURLPrefix("http://publish")
                    .oncePattern("pattern")
                    .author(true)
                    .publish(false)
                    .authorSNSite("authSN")
                    .publishSNSite("pubSN")
                    .build();
        }

        @Test
        @DisplayName("Should get id")
        void shouldGetId() {
            assertEquals("test-id", config.getId());
        }

        @Test
        @DisplayName("Should get url")
        void shouldGetUrl() {
            assertEquals("http://test.com", config.getUrl());
        }

        @Test
        @DisplayName("Should get username")
        void shouldGetUsername() {
            assertEquals("user", config.getUsername());
        }

        @Test
        @DisplayName("Should get password")
        void shouldGetPassword() {
            assertEquals("pass", config.getPassword());
        }

        @Test
        @DisplayName("Should get root path")
        void shouldGetRootPath() {
            assertEquals("/content", config.getRootPath());
        }

        @Test
        @DisplayName("Should get content type")
        void shouldGetContentType() {
            assertEquals("cq:Page", config.getContentType());
        }

        @Test
        @DisplayName("Should get sub type")
        void shouldGetSubType() {
            assertEquals("sub", config.getSubType());
        }

        @Test
        @DisplayName("Should get site name")
        void shouldGetSiteName() {
            assertEquals("Site", config.getSiteName());
        }

        @Test
        @DisplayName("Should get default locale")
        void shouldGetDefaultLocale() {
            assertEquals(Locale.GERMAN, config.getDefaultLocale());
        }

        @Test
        @DisplayName("Should get provider name")
        void shouldGetProviderName() {
            assertEquals("Provider", config.getProviderName());
        }

        @Test
        @DisplayName("Should get author URL prefix")
        void shouldGetAuthorURLPrefix() {
            assertEquals("http://author", config.getAuthorURLPrefix());
        }

        @Test
        @DisplayName("Should get publish URL prefix")
        void shouldGetPublishURLPrefix() {
            assertEquals("http://publish", config.getPublishURLPrefix());
        }

        @Test
        @DisplayName("Should get once pattern")
        void shouldGetOncePattern() {
            assertEquals("pattern", config.getOncePattern());
        }

        @Test
        @DisplayName("Should check is author")
        void shouldCheckIsAuthor() {
            assertTrue(config.isAuthor());
        }

        @Test
        @DisplayName("Should check is publish")
        void shouldCheckIsPublish() {
            assertFalse(config.isPublish());
        }

        @Test
        @DisplayName("Should get author SN site")
        void shouldGetAuthorSNSite() {
            assertEquals("authSN", config.getAuthorSNSite());
        }

        @Test
        @DisplayName("Should get publish SN site")
        void shouldGetPublishSNSite() {
            assertEquals("pubSN", config.getPublishSNSite());
        }
    }
}

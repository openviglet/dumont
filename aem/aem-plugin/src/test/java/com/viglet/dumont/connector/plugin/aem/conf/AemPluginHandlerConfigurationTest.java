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

package com.viglet.dumont.connector.plugin.aem.conf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.aem.commons.context.DumAemLocalePathContext;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSourceLocalePath;

@DisplayName("AemPluginHandlerConfiguration Tests")
class AemPluginHandlerConfigurationTest {

    private DumAemSource dumAemSource;
    private AemPluginHandlerConfiguration configuration;

    @BeforeEach
    void setUp() {
        dumAemSource = new DumAemSource();
        dumAemSource.setName("test-source");
        dumAemSource.setEndpoint("http://localhost:4502");
        dumAemSource.setUsername("admin");
        dumAemSource.setPassword("admin");
        dumAemSource.setContentType("cq:Page");
        dumAemSource.setSubType("page");
        dumAemSource.setRootPath("/content/test");
        dumAemSource.setDefaultLocale(Locale.ENGLISH);
        dumAemSource.setAuthorURLPrefix("http://author.example.com");
        dumAemSource.setPublishURLPrefix("http://publish.example.com");
        dumAemSource.setOncePattern("/content/once");
        dumAemSource.setAuthor(true);
        dumAemSource.setPublish(true);
        dumAemSource.setAuthorSNSite("author-site");
        dumAemSource.setPublishSNSite("publish-site");

        configuration = new AemPluginHandlerConfiguration(dumAemSource);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create configuration from DumAemSource")
        void shouldCreateConfigurationFromSource() {
            assertNotNull(configuration);
        }

        @Test
        @DisplayName("Should set default provider name")
        void shouldSetDefaultProviderName() {
            assertEquals("AEM", configuration.getProviderName());
        }
    }

    @Nested
    @DisplayName("CMS Host Tests")
    class CmsHostTests {

        @Test
        @DisplayName("Should return CMS host from source endpoint")
        void shouldReturnCmsHost() {
            assertEquals("http://localhost:4502", configuration.getCmsHost());
        }
    }

    @Nested
    @DisplayName("CMS Credentials Tests")
    class CmsCredentialsTests {

        @Test
        @DisplayName("Should return CMS username")
        void shouldReturnCmsUsername() {
            assertEquals("admin", configuration.getCmsUsername());
        }

        @Test
        @DisplayName("Should return CMS password")
        void shouldReturnCmsPassword() {
            assertEquals("admin", configuration.getCmsPassword());
        }
    }

    @Nested
    @DisplayName("CMS Group Tests")
    class CmsGroupTests {

        @Test
        @DisplayName("Should return CMS group from source name")
        void shouldReturnCmsGroup() {
            assertEquals("test-source", configuration.getCmsGroup());
        }
    }

    @Nested
    @DisplayName("Content Type Tests")
    class ContentTypeTests {

        @Test
        @DisplayName("Should return CMS content type")
        void shouldReturnCmsContentType() {
            assertEquals("cq:Page", configuration.getCmsContentType());
        }

        @Test
        @DisplayName("Should return CMS sub type")
        void shouldReturnCmsSubType() {
            assertEquals("page", configuration.getCmsSubType());
        }
    }

    @Nested
    @DisplayName("Root Path Tests")
    class RootPathTests {

        @Test
        @DisplayName("Should return CMS root path")
        void shouldReturnCmsRootPath() {
            assertEquals("/content/test", configuration.getCmsRootPath());
        }
    }

    @Nested
    @DisplayName("Locale Tests")
    class LocaleTests {

        @Test
        @DisplayName("Should return default locale")
        void shouldReturnDefaultLocale() {
            assertEquals(Locale.ENGLISH, configuration.getDefaultLocale());
        }
    }

    @Nested
    @DisplayName("URL Prefix Tests")
    class UrlPrefixTests {

        @Test
        @DisplayName("Should return author URL prefix")
        void shouldReturnAuthorUrlPrefix() {
            assertEquals("http://author.example.com", configuration.getAuthorURLPrefix());
        }

        @Test
        @DisplayName("Should return publish URL prefix")
        void shouldReturnPublishUrlPrefix() {
            assertEquals("http://publish.example.com", configuration.getPublishURLPrefix());
        }
    }

    @Nested
    @DisplayName("Once Pattern Tests")
    class OncePatternTests {

        @Test
        @DisplayName("Should return once pattern path")
        void shouldReturnOncePatternPath() {
            assertEquals("/content/once", configuration.getOncePatternPath());
        }
    }

    @Nested
    @DisplayName("Author/Publish Tests")
    class AuthorPublishTests {

        @Test
        @DisplayName("Should return true when author is enabled")
        void shouldReturnTrueWhenAuthorEnabled() {
            assertTrue(configuration.isAuthor());
        }

        @Test
        @DisplayName("Should return true when publish is enabled")
        void shouldReturnTrueWhenPublishEnabled() {
            assertTrue(configuration.isPublish());
        }

        @Test
        @DisplayName("Should return false when author is disabled")
        void shouldReturnFalseWhenAuthorDisabled() {
            dumAemSource.setAuthor(false);
            configuration = new AemPluginHandlerConfiguration(dumAemSource);

            assertFalse(configuration.isAuthor());
        }

        @Test
        @DisplayName("Should return false when publish is disabled")
        void shouldReturnFalseWhenPublishDisabled() {
            dumAemSource.setPublish(false);
            configuration = new AemPluginHandlerConfiguration(dumAemSource);

            assertFalse(configuration.isPublish());
        }
    }

    @Nested
    @DisplayName("SN Site Tests")
    class SnSiteTests {

        @Test
        @DisplayName("Should return author SN site")
        void shouldReturnAuthorSnSite() {
            assertEquals("author-site", configuration.getAuthorSNSite());
        }

        @Test
        @DisplayName("Should return publish SN site")
        void shouldReturnPublishSnSite() {
            assertEquals("publish-site", configuration.getPublishSNSite());
        }
    }

    @Nested
    @DisplayName("Locale Paths Tests")
    class LocalePathsTests {

        @Test
        @DisplayName("Should return empty collection when no locale paths set")
        void shouldReturnEmptyWhenNoLocalePaths() {
            Collection<DumAemLocalePathContext> locales = configuration.getLocales();

            assertNotNull(locales);
            assertTrue(locales.isEmpty());
        }

        @Test
        @DisplayName("Should return locale paths when set on source")
        void shouldReturnLocalePathsWhenSet() {
            HashSet<DumAemSourceLocalePath> paths = new HashSet<>();
            paths.add(DumAemSourceLocalePath.builder().locale(Locale.ENGLISH).path("/content/en").build());
            paths.add(DumAemSourceLocalePath.builder().locale(Locale.FRENCH).path("/content/fr").build());
            dumAemSource.setLocalePaths(paths);
            configuration = new AemPluginHandlerConfiguration(dumAemSource);

            Collection<DumAemLocalePathContext> locales = configuration.getLocales();

            assertNotNull(locales);
            assertEquals(2, locales.size());
        }
    }

    @Nested
    @DisplayName("Null Value Handling Tests")
    class NullValueHandlingTests {

        @Test
        @DisplayName("Should handle null default locale")
        void shouldHandleNullDefaultLocale() {
            dumAemSource.setDefaultLocale(null);
            configuration = new AemPluginHandlerConfiguration(dumAemSource);

            assertNull(configuration.getDefaultLocale());
        }

        @Test
        @DisplayName("Should handle null author URL prefix")
        void shouldHandleNullAuthorUrlPrefix() {
            dumAemSource.setAuthorURLPrefix(null);
            configuration = new AemPluginHandlerConfiguration(dumAemSource);

            assertNull(configuration.getAuthorURLPrefix());
        }

        @Test
        @DisplayName("Should handle null publish URL prefix")
        void shouldHandleNullPublishUrlPrefix() {
            dumAemSource.setPublishURLPrefix(null);
            configuration = new AemPluginHandlerConfiguration(dumAemSource);

            assertNull(configuration.getPublishURLPrefix());
        }
    }
}

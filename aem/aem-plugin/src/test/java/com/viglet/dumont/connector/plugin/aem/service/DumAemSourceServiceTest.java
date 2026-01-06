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

package com.viglet.dumont.connector.plugin.aem.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemPluginSystem;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemPluginSystemRepository;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemSourceRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("DumAemSourceService Tests")
class DumAemSourceServiceTest {

    @Mock
    private DumAemSourceRepository dumAemSourceRepository;

    @Mock
    private DumAemPluginSystemRepository dumAemPluginSystemRepository;

    private DumAemSourceService dumAemSourceService;

    @BeforeEach
    void setUp() {
        dumAemSourceService = new DumAemSourceService(
                dumAemSourceRepository,
                dumAemPluginSystemRepository);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create service with repositories")
        void shouldCreateServiceWithRepositories() {
            assertNotNull(dumAemSourceService);
        }
    }

    @Nested
    @DisplayName("getAllSources Tests")
    class GetAllSourcesTests {

        @Test
        @DisplayName("Should return all sources from repository")
        void shouldReturnAllSourcesFromRepository() {
            DumAemSource source1 = createMockSource("source1");
            DumAemSource source2 = createMockSource("source2");
            when(dumAemSourceRepository.findAll()).thenReturn(List.of(source1, source2));

            List<DumAemSource> result = dumAemSourceService.getAllSources();

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("Should return empty list when no sources")
        void shouldReturnEmptyListWhenNoSources() {
            when(dumAemSourceRepository.findAll()).thenReturn(List.of());

            List<DumAemSource> result = dumAemSourceService.getAllSources();

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("getDumAemSourceByName Tests")
    class GetDumAemSourceByNameTests {

        @Test
        @DisplayName("Should return source when found by name")
        void shouldReturnSourceWhenFoundByName() {
            DumAemSource source = createMockSource("test-source");
            when(dumAemSourceRepository.findByName("test-source"))
                    .thenReturn(Optional.of(source));

            Optional<DumAemSource> result = dumAemSourceService.getDumAemSourceByName("test-source");

            assertTrue(result.isPresent());
            assertEquals("test-source", result.get().getName());
        }

        @Test
        @DisplayName("Should return empty when source not found by name")
        void shouldReturnEmptyWhenSourceNotFoundByName() {
            when(dumAemSourceRepository.findByName("non-existent"))
                    .thenReturn(Optional.empty());

            Optional<DumAemSource> result = dumAemSourceService.getDumAemSourceByName("non-existent");

            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("getDumAemSourceById Tests")
    class GetDumAemSourceByIdTests {

        @Test
        @DisplayName("Should return source when found by ID")
        void shouldReturnSourceWhenFoundById() {
            DumAemSource source = createMockSource("test-source");
            when(dumAemSourceRepository.findById("source-id"))
                    .thenReturn(Optional.of(source));

            Optional<DumAemSource> result = dumAemSourceService.getDumAemSourceById("source-id");

            assertTrue(result.isPresent());
        }

        @Test
        @DisplayName("Should return empty when source not found by ID")
        void shouldReturnEmptyWhenSourceNotFoundById() {
            when(dumAemSourceRepository.findById("non-existent-id"))
                    .thenReturn(Optional.empty());

            Optional<DumAemSource> result = dumAemSourceService.getDumAemSourceById("non-existent-id");

            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("Should return empty when ID is null")
        void shouldReturnEmptyWhenIdIsNull() {
            Optional<DumAemSource> result = dumAemSourceService.getDumAemSourceById(null);

            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("getDumConnectorSession Tests")
    class GetDumConnectorSessionTests {

        @Test
        @DisplayName("Should create connector session from source")
        void shouldCreateConnectorSessionFromSource() {
            DumAemSource source = createMockSource("test-source");

            DumConnectorSession result = dumAemSourceService.getDumConnectorSession(source);

            assertNotNull(result);
            assertEquals("test-source", result.getSource());
        }

        @Test
        @DisplayName("Should set locale from source")
        void shouldSetLocaleFromSource() {
            DumAemSource source = createMockSource("test-source");
            source.setDefaultLocale(Locale.GERMAN);

            DumConnectorSession result = dumAemSourceService.getDumConnectorSession(source);

            assertEquals(Locale.GERMAN, result.getLocale());
        }
    }

    @Nested
    @DisplayName("getProviderName Tests")
    class GetProviderNameTests {

        @Test
        @DisplayName("Should return AEM as static provider name")
        void shouldReturnAemAsStaticProviderName() {
            assertEquals("AEM", DumAemSourceService.getProviderName());
        }
    }

    @Nested
    @DisplayName("getDumAemConfiguration Tests")
    class GetDumAemConfigurationTests {

        @Test
        @DisplayName("Should create configuration from source")
        void shouldCreateConfigurationFromSource() {
            DumAemSource source = createMockSource("test-source");

            DumAemConfiguration result = dumAemSourceService.getDumAemConfiguration(source);

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("isOnce Tests")
    class IsOnceTests {

        @Test
        @DisplayName("Should return true when once flag is set")
        void shouldReturnTrueWhenOnceFlagIsSet() {
            DumAemSource source = createMockSource("test-source");
            DumAemConfiguration config = dumAemSourceService.getDumAemConfiguration(source);
            DumAemPluginSystem pluginSystem = new DumAemPluginSystem();
            pluginSystem.setBooleanValue(true);
            when(dumAemPluginSystemRepository.findByConfig(any()))
                    .thenReturn(Optional.of(pluginSystem));

            boolean result = dumAemSourceService.isOnce(config);

            assertTrue(result);
        }

        @Test
        @DisplayName("Should return false when once flag is not set")
        void shouldReturnFalseWhenOnceFlagNotSet() {
            DumAemSource source = createMockSource("test-source");
            DumAemConfiguration config = dumAemSourceService.getDumAemConfiguration(source);
            when(dumAemPluginSystemRepository.findByConfig(any()))
                    .thenReturn(Optional.empty());

            boolean result = dumAemSourceService.isOnce(config);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("isPublish Tests")
    class IsPublishTests {

        @Test
        @DisplayName("Should return true when publish enabled and site set")
        void shouldReturnTrueWhenPublishEnabledAndSiteSet() {
            DumAemSource source = createMockSource("test-source");
            source.setPublish(true);
            source.setPublishSNSite("publish-site");
            DumAemConfiguration config = dumAemSourceService.getDumAemConfiguration(source);

            boolean result = dumAemSourceService.isPublish(config);

            assertTrue(result);
        }

        @Test
        @DisplayName("Should return false when publish disabled")
        void shouldReturnFalseWhenPublishDisabled() {
            DumAemSource source = createMockSource("test-source");
            source.setPublish(false);
            source.setPublishSNSite("publish-site");
            DumAemConfiguration config = dumAemSourceService.getDumAemConfiguration(source);

            boolean result = dumAemSourceService.isPublish(config);

            assertFalse(result);
        }

        @Test
        @DisplayName("Should return false when publish site not set")
        void shouldReturnFalseWhenPublishSiteNotSet() {
            DumAemSource source = createMockSource("test-source");
            source.setPublish(true);
            source.setPublishSNSite("");
            DumAemConfiguration config = dumAemSourceService.getDumAemConfiguration(source);

            boolean result = dumAemSourceService.isPublish(config);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("isAuthor Tests")
    class IsAuthorTests {

        @Test
        @DisplayName("Should return true when author enabled and site set")
        void shouldReturnTrueWhenAuthorEnabledAndSiteSet() {
            DumAemSource source = createMockSource("test-source");
            source.setAuthor(true);
            source.setAuthorSNSite("author-site");
            DumAemConfiguration config = dumAemSourceService.getDumAemConfiguration(source);

            boolean result = dumAemSourceService.isAuthor(config);

            assertTrue(result);
        }

        @Test
        @DisplayName("Should return false when author disabled")
        void shouldReturnFalseWhenAuthorDisabled() {
            DumAemSource source = createMockSource("test-source");
            source.setAuthor(false);
            source.setAuthorSNSite("author-site");
            DumAemConfiguration config = dumAemSourceService.getDumAemConfiguration(source);

            boolean result = dumAemSourceService.isAuthor(config);

            assertFalse(result);
        }

        @Test
        @DisplayName("Should return false when author site not set")
        void shouldReturnFalseWhenAuthorSiteNotSet() {
            DumAemSource source = createMockSource("test-source");
            source.setAuthor(true);
            source.setAuthorSNSite("");
            DumAemConfiguration config = dumAemSourceService.getDumAemConfiguration(source);

            boolean result = dumAemSourceService.isAuthor(config);

            assertFalse(result);
        }
    }

    private DumAemSource createMockSource(String name) {
        DumAemSource source = new DumAemSource();
        source.setName(name);
        source.setEndpoint("http://localhost:4502");
        source.setUsername("admin");
        source.setPassword("admin");
        source.setContentType("cq:Page");
        source.setRootPath("/content/test");
        source.setDefaultLocale(Locale.ENGLISH);
        return source;
    }
}

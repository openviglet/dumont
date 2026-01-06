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

package com.viglet.dumont.connector.plugin.aem.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.plugin.aem.DumAemPluginProcess;
import com.viglet.dumont.connector.plugin.aem.api.DumAemPathList;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import com.viglet.dumont.connector.plugin.aem.service.DumAemService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemSourceService;

@ExtendWith(MockitoExtension.class)
@DisplayName("DumAemPlugin Tests")
class DumAemPluginTest {

    @Mock
    private DumAemPluginProcess dumAemPluginProcess;

    @Mock
    private DumAemSourceService dumAemSourceService;

    @Mock
    private DumAemService dumAemService;

    private DumAemPlugin dumAemPlugin;

    @BeforeEach
    void setUp() {
        dumAemPlugin = new DumAemPlugin(
                dumAemPluginProcess,
                dumAemSourceService,
                dumAemService);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create plugin with dependencies")
        void shouldCreatePluginWithDependencies() {
            assertNotNull(dumAemPlugin);
        }
    }

    @Nested
    @DisplayName("crawl Tests")
    class CrawlTests {

        @Test
        @DisplayName("Should index all sources when crawling")
        void shouldIndexAllSourcesWhenCrawling() {
            DumAemSource source1 = mock(DumAemSource.class);
            DumAemSource source2 = mock(DumAemSource.class);
            when(dumAemSourceService.getAllSources()).thenReturn(List.of(source1, source2));

            dumAemPlugin.crawl();

            verify(dumAemSourceService).getAllSources();
            verify(dumAemPluginProcess).indexAll(source1);
            verify(dumAemPluginProcess).indexAll(source2);
        }

        @Test
        @DisplayName("Should handle empty source list")
        void shouldHandleEmptySourceList() {
            when(dumAemSourceService.getAllSources()).thenReturn(List.of());

            dumAemPlugin.crawl();

            verify(dumAemSourceService).getAllSources();
        }
    }

    @Nested
    @DisplayName("indexAll Tests")
    class IndexAllTests {

        @Test
        @DisplayName("Should call indexAllByNameAsync with source name")
        void shouldCallIndexAllByNameAsync() {
            String sourceName = "test-source";

            dumAemPlugin.indexAll(sourceName);

            verify(dumAemPluginProcess).indexAllByNameAsync(sourceName);
        }
    }

    @Nested
    @DisplayName("indexById Tests")
    class IndexByIdTests {

        @Test
        @DisplayName("Should create path list and send to index")
        void shouldCreatePathListAndSendToIndex() {
            String source = "test-source";
            List<String> contentIds = List.of("/content/test/page1", "/content/test/page2");

            dumAemPlugin.indexById(source, contentIds);

            verify(dumAemPluginProcess).sentToIndexStandalone(anyString(), any(DumAemPathList.class));
        }

        @Test
        @DisplayName("Should handle single content ID")
        void shouldHandleSingleContentId() {
            String source = "test-source";
            List<String> contentIds = List.of("/content/test/page1");

            dumAemPlugin.indexById(source, contentIds);

            verify(dumAemPluginProcess).sentToIndexStandalone(anyString(), any(DumAemPathList.class));
        }

        @Test
        @DisplayName("Should handle empty content ID list")
        void shouldHandleEmptyContentIdList() {
            String source = "test-source";
            List<String> contentIds = List.of();

            dumAemPlugin.indexById(source, contentIds);

            verify(dumAemPluginProcess).sentToIndexStandalone(anyString(), any(DumAemPathList.class));
        }
    }

    @Nested
    @DisplayName("getProviderName Tests")
    class GetProviderNameTests {

        @Test
        @DisplayName("Should return provider name from service")
        void shouldReturnProviderNameFromService() {
            when(dumAemService.getProviderName()).thenReturn("AEM");

            String providerName = dumAemPlugin.getProviderName();

            assertEquals("AEM", providerName);
            verify(dumAemService).getProviderName();
        }
    }
}

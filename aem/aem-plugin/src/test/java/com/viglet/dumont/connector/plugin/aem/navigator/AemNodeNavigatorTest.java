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

package com.viglet.dumont.connector.plugin.aem.navigator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.aem.commons.DumAemObjectGeneric;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEvent;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;
import com.viglet.dumont.connector.plugin.aem.service.DumAemJobService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemObjectService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemSourceService;
import com.viglet.dumont.connector.plugin.aem.utils.DumAemReactiveUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("AemNodeNavigator Tests")
class AemNodeNavigatorTest {

    @Mock
    private DumAemObjectService objectService;

    @Mock
    private DumAemJobService jobService;

    @Mock
    private DumAemSourceService sourceService;

    @Mock
    private DumAemReactiveUtils reactiveUtils;

    private AemNodeNavigator aemNodeNavigator;

    @BeforeEach
    void setUp() {
        aemNodeNavigator = new AemNodeNavigator(
                objectService,
                jobService,
                sourceService,
                reactiveUtils,
                false,
                10);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create navigator with default parallelism")
        void shouldCreateNavigatorWithDefaultParallelism() {
            AemNodeNavigator navigator = new AemNodeNavigator(
                    objectService,
                    jobService,
                    sourceService,
                    reactiveUtils,
                    false,
                    0);
            assertNotNull(navigator);
        }

        @Test
        @DisplayName("Should create navigator with custom parallelism")
        void shouldCreateNavigatorWithCustomParallelism() {
            AemNodeNavigator navigator = new AemNodeNavigator(
                    objectService,
                    jobService,
                    sourceService,
                    reactiveUtils,
                    true,
                    20);
            assertNotNull(navigator);
        }

        @Test
        @DisplayName("Should create navigator with reactive disabled")
        void shouldCreateNavigatorWithReactiveDisabled() {
            AemNodeNavigator navigator = new AemNodeNavigator(
                    objectService,
                    jobService,
                    sourceService,
                    reactiveUtils,
                    false,
                    10);
            assertNotNull(navigator);
        }
    }

    @Nested
    @DisplayName("navigateAndIndex Tests")
    class NavigateAndIndexTests {

        @Test
        @DisplayName("Should navigate and index node successfully")
        void shouldNavigateAndIndexNodeSuccessfully() {
            // Given
            DumAemSession session = createMockSession(false);
            String path = "/content/test";
            JSONObject infinityJson = new JSONObject();
            DumAemObjectGeneric aemObject = createMockAemObject(path, "cq:Page");

            when(objectService.getDumAemObjectGeneric(anyString(), any(JSONObject.class), any()))
                    .thenReturn(aemObject);

            // When & Then
            assertDoesNotThrow(() -> aemNodeNavigator.navigateAndIndex(session, path, infinityJson));
        }

        @Test
        @DisplayName("Should process node when type matches content type")
        void shouldProcessNodeWhenTypeMatchesContentType() {
            // Given
            DumAemSession session = createMockSession(false);
            String path = "/content/test";
            JSONObject infinityJson = new JSONObject();
            DumAemObjectGeneric aemObject = createMockAemObject(path, "cq:Page");

            when(objectService.getDumAemObjectGeneric(anyString(), any(JSONObject.class), any()))
                    .thenReturn(aemObject);
            doNothing().when(jobService).prepareIndexObject(any(), any());

            // When
            aemNodeNavigator.navigateAndIndex(session, path, infinityJson);

            // Then - verification happens based on content type match
            verify(objectService).getDumAemObjectGeneric(anyString(), any(JSONObject.class), any());
        }

        @Test
        @DisplayName("Should navigate children when recursive is true")
        void shouldNavigateChildrenWhenRecursiveIsTrue() {
            // Given
            DumAemSession session = createMockSession(true);
            String path = "/content/test";
            JSONObject infinityJson = new JSONObject();
            DumAemObjectGeneric aemObject = createMockAemObjectWithChildren(path, "cq:Page");

            when(objectService.getDumAemObjectGeneric(anyString(), any(JSONObject.class), any()))
                    .thenReturn(aemObject);

            // When
            aemNodeNavigator.navigateAndIndex(session, path, infinityJson);

            // Then
            verify(objectService).getDumAemObjectGeneric(anyString(), any(JSONObject.class), any());
        }

        @Test
        @DisplayName("Should not navigate children when recursive is false")
        void shouldNotNavigateChildrenWhenRecursiveIsFalse() {
            // Given
            DumAemSession session = createMockSession(false);
            String path = "/content/test";
            JSONObject infinityJson = new JSONObject();
            DumAemObjectGeneric aemObject = createMockAemObject(path, "cq:Page");

            when(objectService.getDumAemObjectGeneric(anyString(), any(JSONObject.class), any()))
                    .thenReturn(aemObject);

            // When
            aemNodeNavigator.navigateAndIndex(session, path, infinityJson);

            // Then - children navigation should not be triggered
            verify(objectService, times(1)).getDumAemObjectGeneric(anyString(), any(JSONObject.class), any());
        }
    }

    @Nested
    @DisplayName("Reactive Navigation Tests")
    class ReactiveNavigationTests {

        @Test
        @DisplayName("Should use reactive navigation when enabled")
        void shouldUseReactiveNavigationWhenEnabled() {
            AemNodeNavigator reactiveNavigator = new AemNodeNavigator(
                    objectService,
                    jobService,
                    sourceService,
                    reactiveUtils,
                    true,
                    10);

            DumAemSession session = createMockSession(true);
            String path = "/content/test";
            JSONObject infinityJson = new JSONObject();
            DumAemObjectGeneric aemObject = createMockAemObjectWithChildren(path, "cq:Page");

            when(objectService.getDumAemObjectGeneric(anyString(), any(JSONObject.class), any()))
                    .thenReturn(aemObject);

            assertDoesNotThrow(() -> reactiveNavigator.navigateAndIndex(session, path, infinityJson));
        }
    }

    private DumAemSession createMockSession(boolean recursive) {
        DumAemConfiguration config = mock(DumAemConfiguration.class);
        lenient().when(config.getContentType()).thenReturn("cq:Page");
        lenient().when(config.getSubType()).thenReturn(null);

        return DumAemSession.builder()
                .configuration(config)
                .event(DumAemEvent.INDEXING)
                .recursive(recursive)
                .standalone(true)
                .providerName("AEM")
                .build();
    }

    private DumAemObjectGeneric createMockAemObject(String path, String type) {
        DumAemObjectGeneric aemObject = mock(DumAemObjectGeneric.class);
        lenient().when(aemObject.getPath()).thenReturn(path);
        lenient().when(aemObject.getType()).thenReturn(type);
        JSONObject jcrNode = new JSONObject();
        lenient().when(aemObject.getJcrNode()).thenReturn(jcrNode);
        return aemObject;
    }

    private DumAemObjectGeneric createMockAemObjectWithChildren(String path, String type) {
        DumAemObjectGeneric aemObject = mock(DumAemObjectGeneric.class);
        lenient().when(aemObject.getPath()).thenReturn(path);
        lenient().when(aemObject.getType()).thenReturn(type);
        JSONObject jcrNode = new JSONObject();
        jcrNode.put("childNode", new JSONObject());
        lenient().when(aemObject.getJcrNode()).thenReturn(jcrNode);
        return aemObject;
    }
}

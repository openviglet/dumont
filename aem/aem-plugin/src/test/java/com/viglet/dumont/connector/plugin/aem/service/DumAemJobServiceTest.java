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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.DumAemObjectGeneric;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEnv;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEvent;
import com.viglet.dumont.connector.aem.commons.bean.DumAemTargetAttrValueMap;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemContentMapping;
import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;

@ExtendWith(MockitoExtension.class)
@DisplayName("DumAemJobService Tests")
class DumAemJobServiceTest {

    @Mock
    private DumAemService dumAemService;

    @Mock
    private DumConnectorContext dumConnectorContext;

    @Mock
    private DumAemContentDefinitionService dumAemContentDefinitionService;

    @Mock
    private DumAemContentMappingService dumAemContentMappingService;

    private DumAemJobService service;

    @BeforeEach
    void setUp() {
        service = new DumAemJobService(dumAemService, dumConnectorContext, dumAemContentDefinitionService);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create service with dependencies")
        void shouldCreateServiceWithDependencies() {
            assertNotNull(service);
        }
    }

    @Nested
    @DisplayName("deIndexJob Tests")
    class DeIndexJobTests {

        @Test
        @DisplayName("Should create deIndex job with correct action")
        void shouldCreateDeIndexJobWithCorrectAction() {
            // Given
            DumAemSession session = createMockSession();
            List<String> sites = List.of("site1");
            Locale locale = Locale.ENGLISH;
            String objectId = "/content/test/page";
            String environment = "AUTHOR";

            // When
            TurSNJobItem result = service.deIndexJob(session, sites, locale, objectId, environment);

            // Then
            assertNotNull(result);
            assertEquals(TurSNJobAction.DELETE, result.getTurSNJobAction());
            assertEquals(environment, result.getEnvironment());
        }

        @Test
        @DisplayName("Should create deIndex job from indexing DTO")
        void shouldCreateDeIndexJobFromIndexingDTO() {
            // Given
            DumAemSession session = createMockSession();
            DumConnectorIndexing indexingDTO = DumConnectorIndexing.builder()
                    .sites(List.of("site1"))
                    .locale(Locale.ENGLISH)
                    .objectId("/content/test")
                    .environment("PUBLISHING")
                    .build();

            // When
            TurSNJobItem result = service.deIndexJob(session, indexingDTO);

            // Then
            assertNotNull(result);
            assertEquals(TurSNJobAction.DELETE, result.getTurSNJobAction());
        }
    }

    @Nested
    @DisplayName("getTurSNJobItem Tests")
    class GetTurSNJobItemTests {

        @Test
        @DisplayName("Should create TurSNJobItem with CREATE action")
        void shouldCreateTurSNJobItemWithCreateAction() {
            // Given
            DumAemSession session = createMockSession();
            DumAemObject aemObject = createMockAemObject();
            Locale locale = Locale.ENGLISH;
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("title", "Test Title");

            when(dumAemContentDefinitionService.getDeltaDate(any(), any(), any()))
                    .thenReturn(new Date());

            // When
            TurSNJobItem result = service.getTurSNJobItem(session, aemObject, locale, attributes);

            // Then
            assertNotNull(result);
            assertEquals(TurSNJobAction.CREATE, result.getTurSNJobAction());
        }

        @Test
        @DisplayName("Should set checksum from delta date")
        void shouldSetChecksumFromDeltaDate() {
            // Given
            DumAemSession session = createMockSession();
            DumAemObject aemObject = createMockAemObject();
            Locale locale = Locale.ENGLISH;
            Map<String, Object> attributes = new HashMap<>();
            Date deltaDate = new Date(1234567890L);

            when(dumAemContentDefinitionService.getDeltaDate(any(), any(), any()))
                    .thenReturn(deltaDate);

            // When
            TurSNJobItem result = service.getTurSNJobItem(session, aemObject, locale, attributes);

            // Then
            assertNotNull(result);
            assertEquals(String.valueOf(deltaDate.getTime()), result.getChecksum());
        }
    }

    @Nested
    @DisplayName("indexObject Tests")
    class IndexObjectTests {

        @Test
        @DisplayName("Should index for author environment when configured")
        void shouldIndexForAuthorEnvironmentWhenConfigured() {
            // Given
            DumAemSession session = createMockSessionForAuthor();
            DumAemObjectGeneric aemObjectGeneric = createMockAemObjectGeneric();

            when(dumAemService.getTargetAttrValueMap(any(), any()))
                    .thenReturn(new DumAemTargetAttrValueMap());
            when(dumAemContentDefinitionService.getDeltaDate(any(), any(), any()))
                    .thenReturn(new Date());
            when(dumConnectorContext.addJobItem(any())).thenReturn(true);

            // When
            service.indexObject(session, aemObjectGeneric);

            // Then
            verify(dumConnectorContext, times(1)).addJobItem(any());
        }

        @Test
        @DisplayName("Should index for publish environment when configured and delivered")
        void shouldIndexForPublishEnvironmentWhenConfiguredAndDelivered() {
            // Given
            DumAemSession session = createMockSessionForPublish();
            DumAemObjectGeneric aemObjectGeneric = createMockAemObjectGenericDelivered();

            lenient().when(dumAemService.getTargetAttrValueMap(any(), any()))
                    .thenReturn(new DumAemTargetAttrValueMap());
            lenient().when(dumAemContentDefinitionService.getDeltaDate(any(), any(), any()))
                    .thenReturn(new Date());
            lenient().when(dumConnectorContext.addJobItem(any())).thenReturn(true);

            // When
            service.indexObject(session, aemObjectGeneric);

            // Then
            verify(dumConnectorContext, times(1)).addJobItem(any());
        }
    }

    @Nested
    @DisplayName("createDeIndexJobAndSendToConnectorQueue Tests")
    class CreateDeIndexJobTests {

        @Test
        @DisplayName("Should not create deIndex job when no indexing items found")
        void shouldNotCreateDeIndexJobWhenNoIndexingItemsFound() {
            // Given
            DumAemSession session = createMockSession();
            String contentId = "/content/test";

            when(dumConnectorContext.getIndexingItem(anyString(), any(), anyString()))
                    .thenReturn(Collections.emptyList());

            // When
            service.createDeIndexJobAndSendToConnectorQueue(session, contentId);

            // Then
            verify(dumConnectorContext, times(0)).addJobItem(any());
        }
    }

    private DumAemSession createMockSession() {
        DumAemConfiguration config = mock(DumAemConfiguration.class);
        lenient().when(config.isAuthor()).thenReturn(false);
        lenient().when(config.isPublish()).thenReturn(false);

        return DumAemSession.builder()
                .configuration(config)
                .event(DumAemEvent.INDEXING)
                .standalone(true)
                .providerName("AEM")
                .sites(Collections.singletonList("site1"))
                .contentMapping(DumAemContentMapping.builder().build())
                .attributeSpecs(Collections.emptyList())
                .build();
    }

    private DumAemSession createMockSessionForAuthor() {
        DumAemConfiguration config = mock(DumAemConfiguration.class);
        lenient().when(config.isAuthor()).thenReturn(true);
        lenient().when(config.isPublish()).thenReturn(false);
        lenient().when(config.getAuthorSNSite()).thenReturn("author-site");

        return DumAemSession.builder()
                .configuration(config)
                .event(DumAemEvent.INDEXING)
                .standalone(true)
                .providerName("AEM")
                .sites(Collections.singletonList("author-site"))
                .contentMapping(DumAemContentMapping.builder().build())
                .attributeSpecs(Collections.emptyList())
                .build();
    }

    private DumAemSession createMockSessionForPublish() {
        DumAemConfiguration config = mock(DumAemConfiguration.class);
        lenient().when(config.isAuthor()).thenReturn(false);
        lenient().when(config.isPublish()).thenReturn(true);
        lenient().when(config.getPublishSNSite()).thenReturn("publish-site");

        return DumAemSession.builder()
                .configuration(config)
                .event(DumAemEvent.INDEXING)
                .standalone(true)
                .providerName("AEM")
                .sites(Collections.singletonList("publish-site"))
                .contentMapping(DumAemContentMapping.builder().build())
                .attributeSpecs(Collections.emptyList())
                .build();
    }

    private DumAemObject createMockAemObject() {
        DumAemObject aemObject = mock(DumAemObject.class);
        lenient().when(aemObject.getPath()).thenReturn("/content/test");
        lenient().when(aemObject.getEnvironment()).thenReturn(DumAemEnv.AUTHOR);
        lenient().when(aemObject.getDependencies()).thenReturn(Collections.emptySet());
        return aemObject;
    }

    private DumAemObjectGeneric createMockAemObjectGeneric() {
        JSONObject jcrNode = new JSONObject();
        jcrNode.put("jcr:primaryType", "cq:Page");
        JSONObject jcrContent = new JSONObject();
        jcrContent.put("jcr:title", "Test Title");
        jcrNode.put("jcr:content", jcrContent);
        return new DumAemObjectGeneric("/content/test", jcrNode);
    }

    private DumAemObjectGeneric createMockAemObjectGenericDelivered() {
        JSONObject jcrNode = new JSONObject();
        jcrNode.put("jcr:primaryType", "cq:Page");
        JSONObject jcrContent = new JSONObject();
        jcrContent.put("jcr:title", "Test Title");
        jcrContent.put("cq:lastReplicated", "2024-01-01T00:00:00.000Z");
        jcrContent.put("cq:lastReplicationAction", "Activate");
        jcrNode.put("jcr:content", jcrContent);
        return new DumAemObjectGeneric("/content/test", jcrNode);
    }
}

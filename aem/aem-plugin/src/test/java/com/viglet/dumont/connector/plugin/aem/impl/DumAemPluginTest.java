/*
 * Copyright (C) 2016-2026 the original author or authors.
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

import static com.viglet.turing.client.sn.TurSNConstants.ID_ATTR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.aem.commons.DumAemObjectGeneric;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEnv;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.utils.DumAemCommonsUtils;
import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.plugin.DumConnectorPlugin.EnvironmentInfo;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumCoverageHistoryStore;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumPreviewSnapshotStore;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumProvenanceHistoryStore;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import com.viglet.dumont.connector.plugin.aem.service.DumAemObjectService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemSessionService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemSourceService;
import com.viglet.dumont.connector.plugin.aem.source.DumAemStructuredSource;
import com.viglet.dumont.connector.plugin.aem.source.DumAemStructuredSourceProvider;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;

@ExtendWith(MockitoExtension.class)
@DisplayName("DumAemPlugin Tests (structured-source SPI, D21)")
class DumAemPluginTest {

    @Mock
    private DumConnectorContext dumConnectorContext;
    @Mock
    private DumAemStructuredSourceProvider provider;
    @Mock
    private DumAemSourceService dumAemSourceService;
    @Mock
    private DumAemSessionService dumAemSessionService;
    @Mock
    private DumAemObjectService dumAemObjectService;
    @Mock
    private DumPreviewSnapshotStore previewSnapshotStore;
    @Mock
    private DumCoverageHistoryStore coverageHistoryStore;
    @Mock
    private DumProvenanceHistoryStore provenanceHistoryStore;

    private DumAemPlugin dumAemPlugin;

    @BeforeEach
    void setUp() {
        dumAemPlugin = new DumAemPlugin(dumConnectorContext, provider, dumAemSourceService,
                dumAemSessionService, dumAemObjectService, previewSnapshotStore, coverageHistoryStore,
                provenanceHistoryStore, false, 1, 0L, 0.5);
    }

    @Test
    @DisplayName("Should report the AEM provider name")
    void shouldReportProviderName() {
        when(provider.getProviderName()).thenReturn("AEM");

        assertNotNull(dumAemPlugin);
        assertEquals("AEM", dumAemPlugin.getProviderName());
    }

    @Test
    @DisplayName("indexAll should be a no-op when the source is not found")
    void shouldNoOpWhenSourceNotFound() {
        when(dumAemSourceService.getDumAemSourceByName("missing")).thenReturn(Optional.empty());

        dumAemPlugin.indexAll("missing");

        verify(dumConnectorContext, never()).finishIndexing(org.mockito.ArgumentMatchers.any(
                DumConnectorSession.class), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    @DisplayName("indexAll should not index a source with no enabled environment")
    void shouldNotIndexSourceWithoutEnabledEnvironment() {
        DumAemSource source = DumAemSource.builder().name("no-env").build();
        when(dumAemSourceService.getDumAemSourceByName("no-env")).thenReturn(Optional.of(source));
        when(provider.enabledEnvironments(source)).thenReturn(List.of());

        dumAemPlugin.indexAll("no-env");

        verify(dumConnectorContext, never()).finishIndexing(org.mockito.ArgumentMatchers.any(
                DumConnectorSession.class), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    @DisplayName("indexById should deindex a missing id from every enabled environment (D29)")
    void shouldDeIndexMissingIdAcrossEnvironments() {
        DumAemSource source = DumAemSource.builder().name("src").build();
        when(dumAemSourceService.getDumAemSourceByName("src")).thenReturn(Optional.of(source));
        when(provider.enabledEnvironments(source))
                .thenReturn(List.of(DumAemEnv.AUTHOR, DumAemEnv.PUBLISHING));
        when(provider.getProviderName()).thenReturn("AEM");

        DumAemStructuredSource authorAdapter = missingAdapter(DumAemEnv.AUTHOR, "author-site");
        DumAemStructuredSource publishAdapter = missingAdapter(DumAemEnv.PUBLISHING, "publish-site");
        when(provider.adapter(source, DumAemEnv.AUTHOR)).thenReturn(authorAdapter);
        when(provider.adapter(source, DumAemEnv.PUBLISHING)).thenReturn(publishAdapter);
        when(dumConnectorContext.addJobItem(any(DumJobItemWithSession.class))).thenReturn(true);

        dumAemPlugin.indexById("src", List.of("/content/p1"));

        ArgumentCaptor<DumJobItemWithSession> captor =
                ArgumentCaptor.forClass(DumJobItemWithSession.class);
        verify(dumConnectorContext, times(2)).addJobItem(captor.capture());
        List<DumJobItemWithSession> jobs = captor.getAllValues();
        jobs.forEach(job -> {
            TurSNJobItem item = job.turSNJobItem();
            assertEquals(TurSNJobAction.DELETE, item.getTurSNJobAction());
            assertEquals("/content/p1", item.getAttributes().get(ID_ATTR));
            assertTrue(job.standalone());
        });
        assertEquals(DumAemEnv.AUTHOR.toString(), jobs.get(0).turSNJobItem().getEnvironment());
        assertEquals(DumAemEnv.PUBLISHING.toString(), jobs.get(1).turSNJobItem().getEnvironment());
        // The tracking-driven sweep stays suppressed; the explicit deletes carry the removal.
        verify(dumConnectorContext).finishIndexing(any(DumConnectorSession.class), org.mockito
                .ArgumentMatchers.eq(true));
    }

    @Test
    @DisplayName("indexById should not deindex when the environment has no SN site")
    void shouldNotDeIndexWhenNoSite() {
        DumAemSource source = DumAemSource.builder().name("src").build();
        when(dumAemSourceService.getDumAemSourceByName("src")).thenReturn(Optional.of(source));
        when(provider.enabledEnvironments(source)).thenReturn(List.of(DumAemEnv.AUTHOR));

        DumAemStructuredSource authorAdapter = missingAdapter(DumAemEnv.AUTHOR, null);
        when(provider.adapter(source, DumAemEnv.AUTHOR)).thenReturn(authorAdapter);

        dumAemPlugin.indexById("src", List.of("/content/p1"));

        verify(dumConnectorContext, never()).addJobItem(any(DumJobItemWithSession.class));
        verify(dumConnectorContext).finishIndexing(any(DumConnectorSession.class), org.mockito
                .ArgumentMatchers.eq(true));
    }

    @Test
    @DisplayName("resolveLocale should resolve via locale-by-path")
    void resolveLocaleResolvesByPath() {
        DumAemSource source = DumAemSource.builder().name("src").build();
        DumAemConfiguration config = mock(DumAemConfiguration.class);
        DumAemSession session = mock(DumAemSession.class);
        when(dumAemSourceService.getDumAemSourceByName("src")).thenReturn(Optional.of(source));
        when(dumAemSessionService.getDumAemSession(source, false)).thenReturn(session);
        when(session.getConfiguration()).thenReturn(config);

        try (MockedStatic<DumAemCommonsUtils> mocked = mockStatic(DumAemCommonsUtils.class)) {
            mocked.when(() -> DumAemCommonsUtils.getLocaleByPath(config, "/content/p1"))
                    .thenReturn(Locale.US);

            assertThat(dumAemPlugin.resolveLocale("src", "/content/p1")).isEqualTo(Locale.US);
        }
    }

    @Test
    @DisplayName("resolveLocale should be null when the source is unknown")
    void resolveLocaleNullWhenSourceMissing() {
        when(dumAemSourceService.getDumAemSourceByName("missing")).thenReturn(Optional.empty());

        assertThat(dumAemPlugin.resolveLocale("missing", "/content/p1")).isNull();
    }

    @Test
    @DisplayName("resolveEnvironments should list author + publish when the page is delivered")
    void resolveEnvironmentsListsAuthorAndPublish() {
        DumAemSource source = DumAemSource.builder().name("src").build();
        DumAemConfiguration config = mock(DumAemConfiguration.class);
        DumAemSession session = mock(DumAemSession.class);
        when(dumAemSourceService.getDumAemSourceByName("src")).thenReturn(Optional.of(source));
        when(dumAemSessionService.getDumAemSession(source, false)).thenReturn(session);
        when(session.getConfiguration()).thenReturn(config);
        when(config.isAuthor()).thenReturn(true);
        when(config.getAuthorSNSite()).thenReturn("author-site");
        when(config.isPublish()).thenReturn(true);
        when(config.getPublishSNSite()).thenReturn("publish-site");

        DumAemObjectGeneric aemObject = mock(DumAemObjectGeneric.class);
        when(aemObject.isDelivered()).thenReturn(true);
        when(dumAemObjectService.getDumAemObjectGeneric(eq("/content/p1"), any(), any()))
                .thenReturn(aemObject);

        try (MockedStatic<DumAemCommonsUtils> mocked = mockStatic(DumAemCommonsUtils.class)) {
            mocked.when(() -> DumAemCommonsUtils.getInfinityJson(eq("/content/p1"), eq(config),
                    eq(false))).thenReturn(Optional.of(new JSONObject()));

            List<EnvironmentInfo> environments =
                    dumAemPlugin.resolveEnvironments("src", "/content/p1");

            assertThat(environments).extracting(EnvironmentInfo::environment)
                    .containsExactly(DumAemEnv.AUTHOR.toString(), DumAemEnv.PUBLISHING.toString());
        }
    }

    @Test
    @DisplayName("resolveEnvironments should be empty when the source is unknown")
    void resolveEnvironmentsEmptyWhenSourceMissing() {
        when(dumAemSourceService.getDumAemSourceByName("missing")).thenReturn(Optional.empty());

        assertThat(dumAemPlugin.resolveEnvironments("missing", "/content/p1")).isEmpty();
    }

    /** An adapter bound to {@code environment} whose extraction yields no record (404/undelivered). */
    private DumAemStructuredSource missingAdapter(DumAemEnv environment, String site) {
        DumAemStructuredSource adapter = org.mockito.Mockito.mock(DumAemStructuredSource.class);
        when(adapter.extract("/content/p1")).thenReturn(Optional.empty());
        when(adapter.getSiteNames()).thenReturn(site != null ? List.of(site) : List.of());
        if (site != null) {
            when(adapter.getLocale()).thenReturn(Locale.US);
            when(adapter.getEnvironment()).thenReturn(environment);
        }
        return adapter;
    }
}

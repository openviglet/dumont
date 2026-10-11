/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.scheduled;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.commons.plugin.DumConnectorPlugin;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingStatsModel.OperationType;
import com.viglet.dumont.connector.plugin.DumConnectorPluginRegistry;
import com.viglet.dumont.connector.service.DumConnectorConfigVarService;
import com.viglet.dumont.connector.service.DumConnectorIndexingService;
import com.viglet.dumont.connector.service.DumConnectorRunService;

/** A scheduled crawl starts each named source under its run lock (D128), each one isolated (D129). */
@ExtendWith(MockitoExtension.class)
class DumConnectorScheduledCrawlTest {

    @Mock
    private DumConnectorPluginRegistry pluginRegistry;
    @Mock
    private DumConnectorConfigVarService configVarService;
    @Mock
    private DumConnectorIndexingService indexingService;
    @Mock
    private DumConnectorPlugin structured;
    @Mock
    private DumConnectorPlugin selfCrawling;

    private DumConnectorScheduledTasks tasks;

    @BeforeEach
    void setUp() {
        tasks = new DumConnectorScheduledTasks(pluginRegistry, configVarService,
                new DumConnectorRunService(indexingService));
        when(configVarService.hasNotFirstTime()).thenReturn(false);
    }

    @Test
    void eachNamedSourceStartsUnderItsLockAndARunningOneIsSkipped() {
        when(pluginRegistry.connectorPlugins()).thenReturn(List.of(structured));
        when(structured.scheduledSources()).thenReturn(List.of("free", "busy"));
        when(structured.getProviderName()).thenReturn("JOBS");
        when(indexingService.tryStartProcessing("free")).thenReturn(true);
        when(indexingService.tryStartProcessing("busy")).thenReturn(false);

        tasks.executeWebCrawler();

        verify(indexingService).trackIndexingStart("free", "JOBS", OperationType.INDEX_ALL);
        verify(structured).indexAll("free");
        verify(structured, never()).indexAll("busy");
        verify(structured, never()).crawl();
    }

    @Test
    void aSourceThatThrowsDoesNotStopTheNextOne() {
        when(pluginRegistry.connectorPlugins()).thenReturn(List.of(structured));
        when(structured.scheduledSources()).thenReturn(List.of("broken", "fine"));
        when(structured.getProviderName()).thenReturn("JOBS");
        when(indexingService.tryStartProcessing(anyString())).thenReturn(true);
        doThrow(new IllegalStateException("finish failed")).when(structured).indexAll("broken");

        tasks.executeWebCrawler();

        verify(structured).indexAll("fine");
        verify(indexingService).finishProcessing("broken");
    }

    @Test
    void aPluginThatThrowsDoesNotStopTheNextOne() {
        when(pluginRegistry.connectorPlugins()).thenReturn(List.of(selfCrawling, structured));
        when(selfCrawling.scheduledSources()).thenReturn(List.of());
        when(selfCrawling.getProviderName()).thenReturn("WEB-CRAWLER");
        doThrow(new IllegalStateException("crawler down")).when(selfCrawling).crawl();
        when(structured.scheduledSources()).thenReturn(List.of("fine"));
        when(structured.getProviderName()).thenReturn("JOBS");
        when(indexingService.tryStartProcessing("fine")).thenReturn(true);

        tasks.executeWebCrawler();

        verify(structured).indexAll("fine");
    }

    @Test
    void aPluginNamingNoSourcesCrawlsOnItsOwn() {
        when(pluginRegistry.connectorPlugins()).thenReturn(List.of(selfCrawling));
        when(selfCrawling.scheduledSources()).thenReturn(List.of());

        tasks.executeWebCrawler();

        verify(selfCrawling).crawl();
        verify(indexingService, never()).tryStartProcessing(anyString());
    }
}

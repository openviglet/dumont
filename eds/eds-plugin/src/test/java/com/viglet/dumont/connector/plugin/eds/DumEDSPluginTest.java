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

package com.viglet.dumont.connector.plugin.eds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSSource;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSSourceRepository;
import com.viglet.dumont.connector.plugin.eds.state.DumEDSCrawlStateService;

/**
 * D349: an EDS source is named at the connector level by the id its runs file the ledger under,
 * and the title written by earlier callers still resolves.
 */
class DumEDSPluginTest {

    private final DumEDSSourceRepository repository = mock(DumEDSSourceRepository.class);
    private final DumEDSPluginProcess process = mock(DumEDSPluginProcess.class);
    private final DumEDSCrawlStateService crawlState = mock(DumEDSCrawlStateService.class);
    private final DumEDSSource source = new DumEDSSource();
    private DumEDSPlugin plugin;

    @BeforeEach
    void setUp() {
        source.setId("eds-7");
        source.setTitle("Docs site");
        when(repository.findById("eds-7")).thenReturn(Optional.of(source));
        when(repository.findById("Docs site")).thenReturn(Optional.empty());
        when(repository.findByTitle("Docs site")).thenReturn(Optional.of(source));
        when(repository.findById("nobody")).thenReturn(Optional.empty());
        when(repository.findByTitle("nobody")).thenReturn(Optional.empty());
        plugin = new DumEDSPlugin(repository, process, crawlState);
    }

    @Test
    void theIdAndTheTitleBothNameTheSource() {
        assertThat(plugin.ownsSource("eds-7")).isTrue();
        assertThat(plugin.ownsSource("Docs site")).isTrue();
        assertThat(plugin.ownsSource("nobody")).isFalse();
    }

    @Test
    void clearingByIdResetsTheSourcesCrawlState() {
        plugin.resetState("eds-7");

        verify(crawlState).deleteAll(source);
    }

    @Test
    void indexAllByIdStartsTheSource() {
        plugin.indexAll("eds-7");

        verify(process).start(source);
    }

    @Test
    void anUnknownNameTouchesNothing() {
        plugin.resetState("nobody");
        plugin.indexAll("nobody");

        verify(crawlState, never()).deleteAll(any());
        verify(process, never()).start(any());
    }
}

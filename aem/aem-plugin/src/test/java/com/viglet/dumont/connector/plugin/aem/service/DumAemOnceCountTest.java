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

package com.viglet.dumont.connector.plugin.aem.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.utils.DumAemCommonsUtils;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemPluginSystem;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemPluginSystemRepository;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemSourceRepository;

/**
 * D344: the once pattern takes effect, and counts. With {@code onceTimes} 2, the pattern's paths
 * are indexed in the first two full runs and skipped from the third; a reset starts again.
 */
class DumAemOnceCountTest {

    private final Map<String, DumAemPluginSystem> rows = new HashMap<>();
    private final DumAemSource source = DumAemSource.builder().id("wknd-id").name("wknd")
            .oncePattern("/content/archive/.*").onceTimes(2).build();
    private final DumAemConfiguration config = DumAemConfiguration.builder().id("wknd-id")
            .oncePattern("/content/archive/.*").build();
    private DumAemSourceService service;

    @BeforeEach
    void setUp() {
        DumAemPluginSystemRepository systemRepository = mock(DumAemPluginSystemRepository.class);
        when(systemRepository.findByConfig(anyString()))
                .thenAnswer(call -> Optional.ofNullable(rows.get(call.<String>getArgument(0))));
        when(systemRepository.save(any())).thenAnswer(call -> {
            DumAemPluginSystem row = call.getArgument(0);
            rows.put(row.getConfig(), row);
            return row;
        });
        doAnswer(call -> rows.remove(call.<DumAemPluginSystem>getArgument(0).getConfig()))
                .when(systemRepository).delete(any());
        DumAemSourceRepository sourceRepository = mock(DumAemSourceRepository.class);
        when(sourceRepository.findById("wknd-id")).thenReturn(Optional.of(source));
        service = new DumAemSourceService(sourceRepository, systemRepository, null, false, "");
    }

    /** What the navigator decides for a path, exactly as AemNodeNavigator.shouldProcessNode does. */
    private boolean indexes(String path) {
        return !service.isOnce(config) || DumAemCommonsUtils.isNotOnceConfig(path, config);
    }

    @Test
    void thePatternsPathsAreIndexedInTheFirstTwoRunsAndSkippedInTheThird() {
        assertTrue(indexes("/content/archive/2019"), "run 1");
        service.recordFullRun(source);
        assertTrue(indexes("/content/archive/2019"), "run 2");
        service.recordFullRun(source);
        assertFalse(indexes("/content/archive/2019"), "run 3 skips the pattern's paths");
        assertTrue(indexes("/content/news/today"), "and still indexes every other path");
        assertEquals(new DumAemSourceService.OnceProgress(2, 2), service.onceProgress(source));
    }

    @Test
    void aResetStartsTheCountAgain() {
        service.recordFullRun(source);
        service.recordFullRun(source);

        service.resetOnce(source);

        assertTrue(indexes("/content/archive/2019"));
        assertEquals(new DumAemSourceService.OnceProgress(0, 2), service.onceProgress(source));
    }

    @Test
    void aSourceWithNoPatternCountsNothing() {
        DumAemSource plain = DumAemSource.builder().id("plain").name("plain").build();

        service.recordFullRun(plain);

        assertTrue(rows.isEmpty());
    }

    @Test
    void aFlagWrittenBeforeTheCountStillMeansDone() {
        rows.put("wknd-id/once", new DumAemPluginSystem().setConfig("wknd-id/once").setBooleanValue(true));

        assertFalse(indexes("/content/archive/2019"));
    }
}

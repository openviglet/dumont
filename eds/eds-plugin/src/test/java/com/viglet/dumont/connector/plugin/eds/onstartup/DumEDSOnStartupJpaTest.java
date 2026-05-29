/*
 * Copyright (C) 2016-2024 the original author or authors.
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

package com.viglet.dumont.connector.plugin.eds.onstartup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.plugin.eds.export.DumEDSExchangeProcess;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSConfigVar;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSConfigVarRepository;

/**
 * Drives {@link DumEDSOnStartupJpa#run} against a temporary {@code $user.dir}
 * to confirm:
 *
 * <ul>
 *   <li>JSON seeds in {@code export/} get piped to {@link DumEDSExchangeProcess#importFromFile}</li>
 *   <li>The {@code FIRST_TIME} marker is written after a successful pass</li>
 *   <li>Subsequent runs short-circuit when the marker exists</li>
 *   <li>Missing {@code export/} directory is not an error</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DumEDSOnStartupJpaTest {

    @Mock
    private DumEDSConfigVarRepository configVarRepository;

    @Mock
    private DumEDSExchangeProcess exchangeProcess;

    private DumEDSOnStartupJpa onStartup;
    private String originalUserDir;

    @BeforeEach
    void setUp() {
        onStartup = new DumEDSOnStartupJpa(configVarRepository, exchangeProcess);
        originalUserDir = System.getProperty("user.dir");
    }

    @AfterEach
    void tearDown() {
        if (originalUserDir != null) {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void importsSeedFilesAndMarksFirstTimeDone(@TempDir Path tempDir) throws Exception {
        Path exportDir = tempDir.resolve("export");
        Files.createDirectories(exportDir);
        Files.writeString(exportDir.resolve("baseline.json"), "{ \"sources\": [] }",
                StandardCharsets.UTF_8);
        Files.writeString(exportDir.resolve("operators.json"), "{ \"sources\": [] }",
                StandardCharsets.UTF_8);
        // Decoy non-json file — must be ignored by the *.json glob.
        Files.writeString(exportDir.resolve("README.txt"), "operator notes",
                StandardCharsets.UTF_8);
        System.setProperty("user.dir", tempDir.toString());

        when(configVarRepository.findById(DumEDSOnStartupJpa.FIRST_TIME))
                .thenReturn(Optional.empty());

        onStartup.run(null);

        verify(exchangeProcess, times(2)).importFromFile(any(File.class));

        ArgumentCaptor<DumEDSConfigVar> marker = ArgumentCaptor.forClass(DumEDSConfigVar.class);
        verify(configVarRepository).save(marker.capture());
        assertThat(marker.getValue().getId()).isEqualTo(DumEDSOnStartupJpa.FIRST_TIME);
        assertThat(marker.getValue().getValue()).isEqualTo("true");
    }

    @Test
    void shortCircuitsWhenFirstTimeMarkerAlreadyPresent(@TempDir Path tempDir) throws Exception {
        Path exportDir = tempDir.resolve("export");
        Files.createDirectories(exportDir);
        Files.writeString(exportDir.resolve("seed.json"), "{ \"sources\": [] }",
                StandardCharsets.UTF_8);
        System.setProperty("user.dir", tempDir.toString());

        when(configVarRepository.findById(DumEDSOnStartupJpa.FIRST_TIME))
                .thenReturn(Optional.of(new DumEDSConfigVar()));

        onStartup.run(null);

        verify(exchangeProcess, never()).importFromFile(any(File.class));
        verify(configVarRepository, never()).save(any());
    }

    @Test
    void marksFirstTimeEvenWhenExportDirectoryAbsent(@TempDir Path tempDir) {
        // No /export directory under tempDir.
        System.setProperty("user.dir", tempDir.toString());

        when(configVarRepository.findById(DumEDSOnStartupJpa.FIRST_TIME))
                .thenReturn(Optional.empty());

        onStartup.run(null);

        verify(exchangeProcess, never()).importFromFile(any(File.class));
        verify(configVarRepository, times(1)).save(any());
    }
}

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

package com.viglet.dumont.connector.plugin.webcrawler.onstartup;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import com.viglet.dumont.connector.plugin.webcrawler.export.DumWCExchangeProcess;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCConfigVar;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCConfigVarRepository;

class DumWCOnStartupJpaTest {

    private final DumWCConfigVarRepository configVarRepository =
            Mockito.mock(DumWCConfigVarRepository.class);
    private final DumWCExchangeProcess exchangeProcess = Mockito.mock(DumWCExchangeProcess.class);

    private final DumWCOnStartupJpa runner =
            new DumWCOnStartupJpa(configVarRepository, exchangeProcess);

    @Test
    void skipsWhenAlreadyConfigured() {
        when(configVarRepository.findById("FIRST_TIME"))
                .thenReturn(Optional.of(new DumWCConfigVar()));

        runner.run(null);

        verify(configVarRepository, never()).save(any());
    }

    @Test
    void marksFirstTimeAndImportsExports(@TempDir Path tempDir) throws Exception {
        String previousUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toAbsolutePath().toString());
        try {
            Path exportDir = Files.createDirectories(tempDir.resolve("export"));
            Files.writeString(exportDir.resolve("sources.json"), "{}", StandardCharsets.UTF_8);
            when(configVarRepository.findById("FIRST_TIME")).thenReturn(Optional.empty());

            runner.run(null);

            verify(exchangeProcess).importFromFile(any());
            verify(configVarRepository).save(any(DumWCConfigVar.class));
        } finally {
            System.setProperty("user.dir", previousUserDir);
        }
    }
}

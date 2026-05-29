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

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.dumont.connector.plugin.eds.export.DumEDSExchangeProcess;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSConfigVar;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSConfigVarRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Runs once on the first boot of the EDS plugin: reads every {@code *.json}
 * file in {@code $user.dir/export} and pipes it through {@link
 * DumEDSExchangeProcess#importFromFile(File)} so operators can ship a baseline
 * configuration alongside the binary.
 *
 * <p>A {@code FIRST_TIME} marker in the {@code eds_config} table prevents
 * re-import on subsequent restarts — operators wishing to re-seed must clear
 * that row manually (or delete the H2 store).
 */
@Slf4j
@Component
@Transactional
public class DumEDSOnStartupJpa implements ApplicationRunner {

    public static final String FIRST_TIME = "FIRST_TIME";

    private final DumEDSConfigVarRepository configVarRepository;
    private final DumEDSExchangeProcess exchangeProcess;

    public DumEDSOnStartupJpa(DumEDSConfigVarRepository configVarRepository,
            DumEDSExchangeProcess exchangeProcess) {
        this.configVarRepository = configVarRepository;
        this.exchangeProcess = exchangeProcess;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (configVarRepository.findById(FIRST_TIME).isPresent()) {
            return;
        }

        log.info("EDS first-time setup: scanning {}/export for *.json seed files",
                System.getProperty("user.dir"));
        String exportPath = System.getProperty("user.dir") + File.separator + "export";
        Path dir = Paths.get(exportPath);
        if (!Files.isDirectory(dir)) {
            log.info("EDS export directory absent; nothing to seed.");
            markFirstTimeDone();
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path exportFile : stream) {
                File asFile = exportFile.toFile();
                if (asFile.exists()) {
                    log.info("EDS importing seed file: {}", asFile.getName());
                    exchangeProcess.importFromFile(asFile);
                }
            }
        } catch (IOException e) {
            log.error("EDS first-time setup I/O failure: {}", e.getMessage(), e);
        }
        markFirstTimeDone();
        log.info("EDS first-time setup complete.");
    }

    private void markFirstTimeDone() {
        DumEDSConfigVar marker = new DumEDSConfigVar();
        marker.setId(FIRST_TIME);
        marker.setPath("/system");
        marker.setValue("true");
        configVarRepository.save(marker);
    }
}

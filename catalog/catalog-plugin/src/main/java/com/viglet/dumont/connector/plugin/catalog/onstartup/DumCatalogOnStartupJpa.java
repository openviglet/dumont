/*
 *
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

package com.viglet.dumont.connector.plugin.catalog.onstartup;

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

import com.viglet.dumont.connector.plugin.catalog.export.DumCatalogExchangeProcess;
import com.viglet.dumont.connector.plugin.catalog.persistence.model.DumCatalogConfigVar;
import com.viglet.dumont.connector.plugin.catalog.persistence.repository.DumCatalogConfigVarRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * One-time provisioning of catalog sources on first run: import any source export found under the
 * {@code ./export} directory. Idempotent via the {@code FIRST_TIME} config marker.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
@Transactional
public class DumCatalogOnStartupJpa implements ApplicationRunner {
    public static final String FIRST_TIME = "FIRST_TIME";

    private final DumCatalogConfigVarRepository configVarRepository;
    private final DumCatalogExchangeProcess exchangeProcess;

    public DumCatalogOnStartupJpa(DumCatalogConfigVarRepository configVarRepository,
            DumCatalogExchangeProcess exchangeProcess) {
        this.configVarRepository = configVarRepository;
        this.exchangeProcess = exchangeProcess;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (configVarRepository.findById(FIRST_TIME).isPresent()) {
            return;
        }
        String exportPath = System.getProperty("user.dir") + File.separator + "export";
        Path dir = Paths.get(exportPath);
        if (Files.isDirectory(dir)) {
            log.info("Reading catalog export directory: {}", exportPath);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
                for (Path exportFile : stream) {
                    if (exportFile.toFile().exists()) {
                        exchangeProcess.importFromFile(exportFile.toFile());
                    }
                }
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
        setFirstTime();
    }

    private void setFirstTime() {
        DumCatalogConfigVar configVar = new DumCatalogConfigVar();
        configVar.setId(FIRST_TIME);
        configVar.setPath("/system");
        configVar.setValue("true");
        configVarRepository.save(configVar);
    }
}

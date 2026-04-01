/*
 * Copyright (C) 2016-2024 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.plugin.db.onstartup;

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

import com.viglet.dumont.connector.plugin.db.export.DumDbExchangeProcess;
import com.viglet.dumont.connector.plugin.db.persistence.model.DumDbConfigVar;
import com.viglet.dumont.connector.plugin.db.persistence.repository.DumDbConfigVarRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Transactional
public class DumDbOnStartupJpa implements ApplicationRunner {
    public static final String FIRST_TIME = "FIRST_TIME";

    private final DumDbConfigVarRepository dumDbConfigVarRepository;
    private final DumDbExchangeProcess dumDbExchangeProcess;

    public DumDbOnStartupJpa(DumDbConfigVarRepository dumDbConfigVarRepository,
            DumDbExchangeProcess dumDbExchangeProcess) {
        this.dumDbConfigVarRepository = dumDbConfigVarRepository;
        this.dumDbExchangeProcess = dumDbExchangeProcess;
    }

    @Override
    public void run(ApplicationArguments arg0) {
        if (this.dumDbConfigVarRepository.findById(FIRST_TIME).isPresent())
            return;

        log.info("First Time Configuration ...");
        String exportPath = System.getProperty("user.dir") + File.separator + "export";
        log.info("Reading export directory: {}", exportPath);
        Path dir = Paths.get(exportPath);
        String pattern = "*.json";
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, pattern)) {
            for (Path exportFile : stream) {
                if (!exportFile.toFile().exists())
                    continue;

                dumDbExchangeProcess.importFromFile(exportFile.toFile());
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        setFirstTime();
        log.info("Configuration finished.");
    }

    private void setFirstTime() {
        DumDbConfigVar dumConfigVar = new DumDbConfigVar();
        dumConfigVar.setId(FIRST_TIME);
        dumConfigVar.setPath("/system");
        dumConfigVar.setValue("true");
        this.dumDbConfigVarRepository.save(dumConfigVar);
    }
}

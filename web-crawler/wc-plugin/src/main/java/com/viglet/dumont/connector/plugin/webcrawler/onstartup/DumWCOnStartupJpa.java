package com.viglet.dumont.connector.plugin.webcrawler.onstartup;

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

import com.viglet.dumont.connector.plugin.webcrawler.export.DumWCExchangeProcess;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCConfigVar;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCConfigVarRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Transactional
public class DumWCOnStartupJpa implements ApplicationRunner {
    public static final String FIRST_TIME = "FIRST_TIME";
    private final DumWCConfigVarRepository dumWCConfigVarRepository;
    private final DumWCExchangeProcess dumWCExchangeProcess;

    public DumWCOnStartupJpa(DumWCConfigVarRepository dumWCConfigVarRepository,
            DumWCExchangeProcess dumWCExchangeProcess) {
        this.dumWCConfigVarRepository = dumWCConfigVarRepository;
        this.dumWCExchangeProcess = dumWCExchangeProcess;
    }

    @Override
    public void run(ApplicationArguments arg0) {
        if (this.dumWCConfigVarRepository.findById(FIRST_TIME).isPresent())
            return;

        log.info("First Time Configuration ...");
        String exportPath = System.getProperty("user.dir") + File.separator + "export";
        log.info("Reading export directory: {}", exportPath);
        Path dir = Paths.get(exportPath);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path exportFile : stream) {
                if (exportFile.toFile().exists()) {
                    dumWCExchangeProcess.importFromFile(exportFile.toFile());
                }
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        setFirstTime();
        log.info("Configuration finished.");
    }

    private void setFirstTime() {
        DumWCConfigVar dumConfigVar = new DumWCConfigVar();
        dumConfigVar.setId(FIRST_TIME);
        dumConfigVar.setPath("/system");
        dumConfigVar.setValue("true");
        this.dumWCConfigVarRepository.save(dumConfigVar);
    }
}

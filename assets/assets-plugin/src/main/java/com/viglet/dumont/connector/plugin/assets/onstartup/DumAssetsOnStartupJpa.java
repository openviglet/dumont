package com.viglet.dumont.connector.plugin.assets.onstartup;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.dumont.connector.plugin.assets.export.DumAssetsExchangeProcess;
import com.viglet.dumont.connector.plugin.assets.persistence.model.DumAssetsConfigVar;
import com.viglet.dumont.connector.plugin.assets.persistence.repository.DumAssetsConfigVarRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j @Component @Transactional
public class DumAssetsOnStartupJpa implements ApplicationRunner {
    public static final String FIRST_TIME = "FIRST_TIME";
    private final DumAssetsConfigVarRepository configVarRepository;
    private final DumAssetsExchangeProcess exchangeProcess;

    public DumAssetsOnStartupJpa(DumAssetsConfigVarRepository configVarRepository,
            DumAssetsExchangeProcess exchangeProcess) {
        this.configVarRepository = configVarRepository;
        this.exchangeProcess = exchangeProcess;
    }

    @Override
    public void run(ApplicationArguments arg0) {
        if (configVarRepository.findById(FIRST_TIME).isPresent()) return;
        log.info("First Time Configuration ...");
        String exportPath = System.getProperty("user.dir") + File.separator + "export";
        log.info("Reading export directory: {}", exportPath);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(exportPath), "*.json")) {
            for (Path exportFile : stream) {
                if (exportFile.toFile().exists()) exchangeProcess.importFromFile(exportFile.toFile());
            }
        } catch (IOException e) { log.error(e.getMessage(), e); }
        configVarRepository.save(new DumAssetsConfigVar(FIRST_TIME, "/system", "true"));
        log.info("Configuration finished.");
    }
}

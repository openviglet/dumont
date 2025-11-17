package com.viglet.dumont.connector.plugin.webcrawler.onstartup;

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
        if (this.dumWCConfigVarRepository.findById(FIRST_TIME).isEmpty()) {
            log.info("First Time Configuration ...");
            Path exportFile = Paths.get("export/export.json");
            if (exportFile.toFile().exists()) {
                dumWCExchangeProcess.importFromFile(exportFile.toFile());
            }
            setFirstTIme();
            log.info("Configuration finished.");
        }
    }

    private void setFirstTIme() {
        DumWCConfigVar dumConfigVar = new DumWCConfigVar();
        dumConfigVar.setId(FIRST_TIME);
        dumConfigVar.setPath("/system");
        dumConfigVar.setValue("true");
        this.dumWCConfigVarRepository.save(dumConfigVar);
    }
}

package com.viglet.dumont.connector.onstartup;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.dumont.connector.service.DumConnectorConfigVarService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Transactional
public class DumConnectorOnStartup implements ApplicationRunner {
    private final DumConnectorConfigVarService configVarService;

    public DumConnectorOnStartup(DumConnectorConfigVarService configVarService) {
        this.configVarService = configVarService;
    }

    @Override
    public void run(ApplicationArguments arg0) {
        if (configVarService.hasNotFirstTime()) {
            log.info("First Time Configuration ...");
            setFirstTIme();
            log.info("Configuration finished.");
        }
    }

    private void setFirstTIme() {
        configVarService.saveFirstTime();
    }

}

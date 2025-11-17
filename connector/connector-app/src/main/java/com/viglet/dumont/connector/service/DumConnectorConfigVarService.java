package com.viglet.dumont.connector.service;

import org.springframework.stereotype.Service;

import com.viglet.dumont.connector.persistence.model.DumConnectorConfigVarModel;
import com.viglet.dumont.connector.persistence.repository.DumConnectorConfigVarRepository;

@Service
public class DumConnectorConfigVarService {
    private final DumConnectorConfigVarRepository dumConnectorConfigVarRepository;
    public static final String FIRST_TIME = "FIRST_TIME";

    public DumConnectorConfigVarService(DumConnectorConfigVarRepository dumConnectorConfigVarRepository) {
        this.dumConnectorConfigVarRepository = dumConnectorConfigVarRepository;
    }

    public boolean hasNotFirstTime() {
        return dumConnectorConfigVarRepository.findById(FIRST_TIME).isEmpty();
    }

    public void save(DumConnectorConfigVarModel dumConfigVar) {
        this.dumConnectorConfigVarRepository.save(dumConfigVar);
    }

    public void saveFirstTime() {
        DumConnectorConfigVarModel dumConfigVar = new DumConnectorConfigVarModel();
        dumConfigVar.setId(FIRST_TIME);
        dumConfigVar.setPath("/system");
        dumConfigVar.setValue("true");
        save(dumConfigVar);
    }
}

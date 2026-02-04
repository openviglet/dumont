package com.viglet.dumont.connector.plugin.aem.service;

import org.json.JSONObject;
import org.springframework.stereotype.Service;

import com.viglet.dumont.connector.aem.commons.DumAemObjectGeneric;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEvent;

@Service
public class DumAemObjectService {
    public DumAemObjectGeneric getDumAemObjectGeneric(String path, JSONObject infinityJson,
            DumAemEvent dumAemEvent) {
        return new DumAemObjectGeneric(path, infinityJson, dumAemEvent);
    }

}

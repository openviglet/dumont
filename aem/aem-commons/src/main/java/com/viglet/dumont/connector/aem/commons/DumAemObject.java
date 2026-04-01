package com.viglet.dumont.connector.aem.commons;

import org.json.JSONObject;

import com.viglet.dumont.connector.aem.commons.bean.DumAemEnv;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
@ToString
public class DumAemObject extends DumAemObjectGeneric {
    private DumAemEnv environment;

    public DumAemObject(String nodePath, JSONObject jcrNode, DumAemEnv environment) {
        super(nodePath, jcrNode);
        this.environment = environment;
    }

    public DumAemObject(DumAemObjectGeneric dumAemObjectGeneric,
            DumAemEnv environment) {
        super(dumAemObjectGeneric.getPath(), dumAemObjectGeneric.getJcrNode());
        this.environment = environment;
    }

    public String getUrlPrefix(DumAemConfiguration configuration) {
        return getEnvironment().equals(DumAemEnv.AUTHOR) ? configuration.getAuthorURLPrefix()
                : configuration.getPublishURLPrefix();
    }

    public String getSNSite(DumAemConfiguration configuration) {
        return getEnvironment().equals(DumAemEnv.AUTHOR) ? configuration.getAuthorSNSite()
                : configuration.getPublishSNSite();
    }

}

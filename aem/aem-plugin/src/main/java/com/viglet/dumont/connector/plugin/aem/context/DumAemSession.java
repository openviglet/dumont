package com.viglet.dumont.connector.plugin.aem.context;

import java.util.List;

import com.viglet.dumont.connector.aem.commons.bean.DumAemEvent;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemContentMapping;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemModel;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.turing.client.sn.job.TurSNAttributeSpec;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
public class DumAemSession extends DumConnectorSession {
    private DumAemConfiguration configuration;
    private DumAemModel model;
    private DumAemContentMapping contentMapping;
    private DumAemEvent event;
    private boolean standalone;
    private boolean indexChildren;
    private List<TurSNAttributeSpec> attributeSpecs;
}

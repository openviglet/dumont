package com.viglet.dumont.aem.server.core.services;

import com.viglet.dumont.aem.server.config.DumAemIndexerConfig;
import lombok.Getter;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.Designate;

@Component(immediate = true)
@Designate(ocd = DumAemIndexerConfig.class)
public class DumAemIndexerServiceImpl implements DumAemIndexerService {

    @Getter
    private DumAemIndexerConfig config;

    @Activate
    public void activate(DumAemIndexerConfig config) {
        this.config = config;
    }
}

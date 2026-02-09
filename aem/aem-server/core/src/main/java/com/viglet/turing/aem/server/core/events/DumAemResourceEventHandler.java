package com.viglet.dumont.aem.server.core.events;

import com.viglet.dumont.aem.server.core.events.beans.DumAemEvent;
import com.viglet.dumont.aem.server.core.events.utils.DumAemEventUtils;
import com.viglet.dumont.aem.server.core.services.DumAemIndexerService;
import lombok.extern.slf4j.Slf4j;
import org.apache.sling.api.SlingConstants;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import java.util.*;

@Slf4j
@Component(service = EventHandler.class, immediate = true, property = {
        Constants.SERVICE_DESCRIPTION + "=Listen to the assets changes",
        EventConstants.EVENT_TOPIC + "=org/apache/sling/api/resource/Resource/*"
})
public class DumAemResourceEventHandler implements EventHandler {
    public static final String DAM_ASSET = "dam:Asset";
    public static final String DAM_ASSET_CONTENT = "dam:AssetContent";
    public static final String CONTENT = "/content";
    public static final String JCR_CONTENT = "/jcr:content";
    @Reference
    private DumAemIndexerService dumAemIndexerService;

    @Override
    public void handleEvent(Event event) {
        String path = (String) event.getProperty(SlingConstants.PROPERTY_PATH);
        String resourceType = (String) event.getProperty(SlingConstants.PROPERTY_RESOURCE_TYPE);
        log.info("Dumont Log Resource Event: path={}, resourceType={}", path, resourceType);
        if (path == null || resourceType == null) {
            return;
        }
        if (path.contains(JCR_CONTENT)) {
            path = path.replace(JCR_CONTENT, "");
        }
        if (!isAssetEvent(path, resourceType))
            return;
        // Index the asset path to update the search engine with the latest changes in
        // DAM assets.
        DumAemEventUtils.index(dumAemIndexerService.getConfig(), path, DumAemEvent.INDEXING);
    }

    protected boolean isAssetEvent(String path, String resourceType) {
        return ((Objects.equals(resourceType, DAM_ASSET) || Objects.equals(resourceType, DAM_ASSET_CONTENT)) &&
                path.startsWith(CONTENT));
    }

}

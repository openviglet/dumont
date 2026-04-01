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

import java.util.Objects;

@Slf4j
@Component(service = EventHandler.class, immediate = true, property = {
        Constants.SERVICE_DESCRIPTION + "=Listen to the assets changes",
        EventConstants.EVENT_TOPIC + "=org/apache/sling/api/resource/Resource/*"
})
public class DumAemResourceEventHandler implements EventHandler {

    private static final String DAM_ASSET = "dam:Asset";
    private static final String DAM_ASSET_CONTENT = "dam:AssetContent";
    private static final String CONTENT_PREFIX = "/content";
    private static final String JCR_CONTENT_SUFFIX = "/jcr:content";

    @Reference
    private DumAemIndexerService dumAemIndexerService;

    @Override
    public void handleEvent(Event event) {
        String path = (String) event.getProperty(SlingConstants.PROPERTY_PATH);
        String resourceType = (String) event.getProperty(SlingConstants.PROPERTY_RESOURCE_TYPE);

        if (path == null || resourceType == null) {
            return;
        }

        if (!isAssetEvent(resourceType)) {
            return;
        }

        String assetPath = path.endsWith(JCR_CONTENT_SUFFIX)
                ? path.substring(0, path.length() - JCR_CONTENT_SUFFIX.length())
                : path;

        if (!assetPath.startsWith(CONTENT_PREFIX)) {
            return;
        }

        log.info("Dumont: Resource event for asset: {}", assetPath);
        DumAemEventUtils.index(dumAemIndexerService.getConfig(), assetPath, DumAemEvent.INDEXING);
    }

    private boolean isAssetEvent(String resourceType) {
        return Objects.equals(resourceType, DAM_ASSET)
                || Objects.equals(resourceType, DAM_ASSET_CONTENT);
    }
}

package com.viglet.dumont.aem.server.core.events;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.EventConstants;

import com.viglet.dumont.aem.server.core.events.beans.DumAemEvent;
import com.viglet.dumont.aem.server.core.events.utils.DumAemEventUtils;
import com.viglet.dumont.aem.server.core.services.DumAemIndexerService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(service = EventHandler.class, immediate = true, property = {
        EventConstants.EVENT_TOPIC + "=org/apache/sling/distribution/Agent/EVENT_DISTRIBUTE"
})
public class DumAemContentFragmentDistributeEventHandler implements EventHandler {

    private static final String CONTENT_DAM_PREFIX = "/content/dam/";
    
    // Propriedades do Sling Content Distribution
    private static final String DIST_PATHS = "distribution.paths";
    private static final String DIST_TYPE = "distribution.type";

    @Reference
    private DumAemIndexerService dumAemIndexerService;

    @Override
    public void handleEvent(Event event) {
        String[] paths = (String[]) event.getProperty(DIST_PATHS);
        String type = (String) event.getProperty(DIST_TYPE);

        if (paths == null || type == null) {
            return;
        }

        List<String> cfPaths = Arrays.stream(paths)
                .filter(path -> path.startsWith(CONTENT_DAM_PREFIX))
                .collect(Collectors.toList());

        if (cfPaths.isEmpty()) {
            return;
        }

        processDistribution(type, cfPaths);
    }

    private void processDistribution(String type, List<String> paths) {
        // ADD = Publish, DELETE = Unpublish no contexto de SCD
        if ("ADD".equalsIgnoreCase(type)) {
            log.info("Dumont (SCD): Content Fragment(s) published: {}", paths);
            indexContentFragments(paths, DumAemEvent.PUBLISHING);
        } else if ("DELETE".equalsIgnoreCase(type)) {
            log.info("Dumont (SCD): Content Fragment(s) unpublished: {}", paths);
            indexContentFragments(paths, DumAemEvent.UNPUBLISHING);
        } else {
            log.debug("Distribution type {} ignored for Content Fragments", type);
        }
    }

    private void indexContentFragments(List<String> paths, DumAemEvent event) {
        try {
            DumAemEventUtils.index(dumAemIndexerService.getConfig(), paths, event);
        } catch (Exception e) {
            log.error("Error indexing Content Fragments {}: {}", paths, e.getMessage(), e);
        }
    }
}
package com.viglet.dumont.aem.server.core.events;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

import com.day.cq.replication.ReplicationAction;
import com.day.cq.replication.ReplicationActionType;
import com.viglet.dumont.aem.server.core.events.beans.DumAemEvent;
import com.viglet.dumont.aem.server.core.events.utils.DumAemEventUtils;
import com.viglet.dumont.aem.server.core.services.DumAemIndexerService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(service = EventHandler.class, immediate = true, property = {
        "event.topics=" + ReplicationAction.EVENT_TOPIC
})
public class DumAemContentFragmentReplicationEventHandler implements EventHandler {

    private static final String CONTENT_DAM_PREFIX = "/content/dam/";

    @Reference
    private DumAemIndexerService dumAemIndexerService;

    @Override
    public void handleEvent(Event event) {
        ReplicationAction action = ReplicationAction.fromEvent(event);
        if (action == null) {
            return;
        }

        List<String> cfPaths = Arrays.stream(action.getPaths())
                .filter(path -> path.startsWith(CONTENT_DAM_PREFIX))
                .collect(Collectors.toList());

        if (cfPaths.isEmpty()) {
            return;
        }

        processReplication(action.getType(), cfPaths);
    }

    private void processReplication(ReplicationActionType type, List<String> paths) {
        switch (type) {
            case ACTIVATE:
                log.info("Dumont: Content Fragment(s) published: {}", paths);
                indexContentFragments(paths, DumAemEvent.PUBLISHING);
                break;
            case DEACTIVATE:
                log.info("Dumont: Content Fragment(s) unpublished: {}", paths);
                indexContentFragments(paths, DumAemEvent.UNPUBLISHING);
                break;
            default:
                log.debug("Replication type {} ignored for Content Fragments", type);
                break;
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

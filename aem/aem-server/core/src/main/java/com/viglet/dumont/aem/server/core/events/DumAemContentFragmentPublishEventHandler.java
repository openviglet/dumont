package com.viglet.dumont.aem.server.core.events;

import java.util.Collections;
import java.util.List;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.apache.sling.event.jobs.JobManager;

import com.viglet.dumont.aem.server.core.events.beans.DumAemEvent;
import com.viglet.dumont.aem.server.core.events.utils.DumAemEventUtils;
import com.viglet.dumont.aem.server.core.services.DumAemIndexerService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(service = EventHandler.class, immediate = true, property = {
        EventConstants.EVENT_TOPIC + "=org/apache/sling/event/notification/job/START"
})
public class DumAemContentFragmentPublishEventHandler implements EventHandler {

    private static final String CONTENT_DAM_PREFIX = "/content/dam/";
    private static final String DAM_ASSET_PUBLISH_JOB_TOPIC = "com/adobe/cq/dam/sendassetpublishevent";
    private static final String PUBLISH_TIER = "publish";

    private static final String JOB_TOPIC = "event.job.topic";
    private static final String DIST_TYPE = "distribution.type";
    private static final String PATH = "path";
    private static final String TIER = "tier";

    @Reference
    private DumAemIndexerService dumAemIndexerService;

    @Reference
    private JobManager jobManager;

    @Override
    public void handleEvent(Event event) {
        if (!DAM_ASSET_PUBLISH_JOB_TOPIC.equals(event.getProperty(JOB_TOPIC))) {
            return;
        }
        if (!PUBLISH_TIER.equals(event.getProperty(TIER))) {
            return;
        }

        String path = (String) event.getProperty(PATH);
        String type = (String) event.getProperty(DIST_TYPE);

        if (path == null || type == null || !path.startsWith(CONTENT_DAM_PREFIX)) {
            return;
        }

        if (!isContentFragmentPath(path)) {
            log.debug("Path {} looks like a DAM asset (has extension), skipping", path);
            return;
        }

        processDistribution(type, Collections.singletonList(path));
    }

    // Content Fragments have no file extension on the last path segment;
    // DAM Assets always do (e.g. image.jpg, document.pdf).
    private static boolean isContentFragmentPath(String path) {
        int lastSlash = path.lastIndexOf('/');
        String lastSegment = (lastSlash >= 0) ? path.substring(lastSlash + 1) : path;
        return !lastSegment.isEmpty() && !lastSegment.contains(".");
    }

    private void processDistribution(String type, List<String> paths) {
        if ("ADD".equalsIgnoreCase(type)) {
            log.info("Dumont (AEMaaCS): Content Fragment(s) published: {}", paths);
            indexContentFragments(paths, DumAemEvent.PUBLISHING);
        } else if ("DELETE".equalsIgnoreCase(type)) {
            log.info("Dumont (AEMaaCS): Content Fragment(s) unpublished: {}", paths);
            indexContentFragments(paths, DumAemEvent.UNPUBLISHING);
        } else {
            log.debug("Distribution type {} ignored for Content Fragments", type);
        }
    }

    private void indexContentFragments(List<String> paths, DumAemEvent event) {
        try {
            DumAemEventUtils.enqueue(jobManager, dumAemIndexerService.getConfig(), paths, event);
        } catch (Exception e) {
            log.error("Error enqueuing index job for Content Fragments {}: {}", paths, e.getMessage(), e);
        }
    }
}

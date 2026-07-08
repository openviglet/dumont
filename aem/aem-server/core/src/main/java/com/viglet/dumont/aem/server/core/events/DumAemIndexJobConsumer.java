package com.viglet.dumont.aem.server.core.events;

import java.util.Arrays;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobConsumer;

import com.viglet.dumont.aem.server.core.events.beans.DumAemEvent;
import com.viglet.dumont.aem.server.core.events.utils.DumAemEventUtils;
import com.viglet.dumont.aem.server.core.services.DumAemIndexerService;

import lombok.extern.slf4j.Slf4j;

/**
 * Cluster-safe delivery of indexing requests to Dumont.
 * <p>
 * The event handlers merely enqueue jobs on {@link DumAemEventUtils#JOB_TOPIC}; the Sling Jobs
 * framework persists each job in the repository and dispatches it to exactly one node of the
 * topology, so the send is durable (survives restarts), de-duplicated across instances and
 * retried on failure. This removes the reliance on the event being processed on a healthy node
 * with a working synchronous HTTP path.
 */
@Slf4j
@Component(service = JobConsumer.class, property = {
        JobConsumer.PROPERTY_TOPICS + "=" + DumAemEventUtils.JOB_TOPIC
})
public class DumAemIndexJobConsumer implements JobConsumer {

    @Reference
    private DumAemIndexerService dumAemIndexerService;

    @Override
    public JobResult process(Job job) {
        String[] paths = (String[]) job.getProperty(DumAemEventUtils.PN_PATHS);
        String eventName = (String) job.getProperty(DumAemEventUtils.PN_EVENT);

        if (paths == null || paths.length == 0 || eventName == null) {
            log.warn("Discarding malformed Dumont index job {} (paths={}, event={})",
                    job.getId(), Arrays.toString(paths), eventName);
            return JobResult.CANCEL;
        }

        DumAemEvent event;
        try {
            event = DumAemEvent.valueOf(eventName);
        } catch (IllegalArgumentException e) {
            log.warn("Discarding Dumont index job {} with unknown event '{}'", job.getId(), eventName);
            return JobResult.CANCEL;
        }

        boolean success = DumAemEventUtils.index(dumAemIndexerService.getConfig(),
                Arrays.asList(paths), event);
        if (success) {
            return JobResult.OK;
        }
        log.warn("Dumont index job {} ({}) failed for paths {}; will retry",
                job.getId(), event, Arrays.toString(paths));
        return JobResult.FAILED;
    }
}

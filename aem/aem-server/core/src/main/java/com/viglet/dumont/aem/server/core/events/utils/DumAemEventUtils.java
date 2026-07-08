package com.viglet.dumont.aem.server.core.events.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.dumont.aem.server.config.DumAemIndexerConfig;
import com.viglet.dumont.aem.server.core.events.beans.DumAemEvent;
import com.viglet.dumont.aem.server.core.events.beans.DumAemPayload;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class DumAemEventUtils {

    private static final String API_DUMONT_AEM_INDEX = "/api/v2/aem/index/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Sling Job topic used to hand off indexing requests to the cluster.
     * The event handlers only enqueue jobs on this topic; {@code DumAemIndexJobConsumer}
     * performs the actual HTTP call to Dumont on exactly one node, with retry.
     */
    public static final String JOB_TOPIC = "com/viglet/dumont/aem/index";
    /** Job property: {@code String[]} of content paths. */
    public static final String PN_PATHS = "dum.paths";
    /** Job property: {@link DumAemEvent} name. */
    public static final String PN_EVENT = "dum.event";

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int SOCKET_TIMEOUT_MS = 15000;

    private static final RequestConfig REQUEST_CONFIG = RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setConnectionRequestTimeout(CONNECT_TIMEOUT_MS)
            .setSocketTimeout(SOCKET_TIMEOUT_MS)
            .build();

    // Reused across calls: creating a client per request is expensive and, without timeouts,
    // a slow/unresponsive Dumont could pin the calling thread indefinitely.
    private static final CloseableHttpClient HTTP_CLIENT = HttpClientBuilder.create()
            .setDefaultRequestConfig(REQUEST_CONFIG)
            .build();

    private DumAemEventUtils() {
    }

    /**
     * Enqueues a durable, cluster-distributed Sling Job for the given path.
     */
    public static void enqueue(JobManager jobManager, DumAemIndexerConfig config, String path,
            DumAemEvent event) {
        enqueue(jobManager, config, Collections.singletonList(path), event);
    }

    /**
     * Enqueues a durable, cluster-distributed Sling Job for the given paths instead of calling
     * Dumont synchronously on the event thread. The job is persisted in the repository, routed to
     * a single node of the topology and retried on failure. Falls back to a synchronous send only
     * when the Jobs framework is unavailable, so events are never silently dropped.
     */
    public static void enqueue(JobManager jobManager, DumAemIndexerConfig config, List<String> pathList,
            DumAemEvent event) {
        if (config == null || !config.enabled()) {
            return;
        }
        if (pathList == null || pathList.isEmpty()) {
            return;
        }
        if (jobManager == null) {
            log.warn("JobManager unavailable, sending index request synchronously for paths {}", pathList);
            index(config, pathList, event);
            return;
        }
        Map<String, Object> props = new HashMap<>();
        props.put(PN_PATHS, pathList.toArray(new String[0]));
        props.put(PN_EVENT, event.name());
        Job job = jobManager.addJob(JOB_TOPIC, props);
        if (job == null) {
            log.warn("Could not enqueue index job for paths {}; sending synchronously", pathList);
            index(config, pathList, event);
        } else {
            log.debug("Enqueued Dumont index job {} ({}) for paths {}", job.getId(), event, pathList);
        }
    }

    public static boolean index(DumAemIndexerConfig config, String path, DumAemEvent event) {
        return index(config, Collections.singletonList(path), event);
    }

    /**
     * Sends the index request to Dumont. Invoked by {@code DumAemIndexJobConsumer} (and, as a
     * fallback, directly by {@link #enqueue}). Returns {@code false} on failure so the caller can
     * trigger a retry.
     */
    public static boolean index(DumAemIndexerConfig config, List<String> pathList, DumAemEvent event) {
        if (config == null || !config.enabled()) {
            return true;
        }
        try {
            String payload = OBJECT_MAPPER.writeValueAsString(DumAemPayload.builder()
                    .paths(pathList)
                    .event(event)
                    .build());
            return sendPayload(config, payload);
        } catch (JsonProcessingException e) {
            // A serialization error will not recover on retry; log and treat as terminal.
            log.error("Failed to serialize payload for paths {}: {}", pathList, e.getMessage(), e);
            return true;
        }
    }

    private static boolean sendPayload(DumAemIndexerConfig config, String payload) {
        String url = config.host() + API_DUMONT_AEM_INDEX + config.configName();
        HttpPost post = new HttpPost(url);
        post.setConfig(REQUEST_CONFIG);
        post.setEntity(new StringEntity(payload, StandardCharsets.UTF_8));
        post.setHeader("Content-Type", ContentType.APPLICATION_JSON.getMimeType());
        try {
            HttpResponse response = HTTP_CLIENT.execute(post);
            int status = response.getStatusLine().getStatusCode();
            if (status >= 200 && status < 300) {
                log.debug("Dumont index response [{}]: {}", url, response.getStatusLine());
                return true;
            }
            log.warn("Dumont index request to {} returned non-success status: {}", url,
                    response.getStatusLine());
            return false;
        } catch (Exception e) {
            log.error("Failed to send index request to {}: {}", url, e.getMessage(), e);
            return false;
        }
    }
}

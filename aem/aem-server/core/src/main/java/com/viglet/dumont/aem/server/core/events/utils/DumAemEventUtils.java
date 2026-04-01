package com.viglet.dumont.aem.server.core.events.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.dumont.aem.server.config.DumAemIndexerConfig;
import com.viglet.dumont.aem.server.core.events.beans.DumAemEvent;
import com.viglet.dumont.aem.server.core.events.beans.DumAemPayload;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

@Slf4j
public class DumAemEventUtils {

    private static final String API_DUMONT_AEM_INDEX = "/api/v2/aem/index/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private DumAemEventUtils() {
    }

    public static void index(DumAemIndexerConfig config, String path, DumAemEvent event) {
        index(config, Collections.singletonList(path), event);
    }

    public static void index(DumAemIndexerConfig config, List<String> pathList, DumAemEvent event) {
        if (config == null || !config.enabled()) {
            return;
        }
        try {
            String payload = OBJECT_MAPPER.writeValueAsString(DumAemPayload.builder()
                    .paths(pathList)
                    .event(event)
                    .build());
            sendPayload(config, payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize payload for paths {}: {}", pathList, e.getMessage(), e);
        }
    }

    private static void sendPayload(DumAemIndexerConfig config, String payload) {
        String url = config.host() + API_DUMONT_AEM_INDEX + config.configName();
        HttpPost post = new HttpPost(url);
        post.setEntity(new StringEntity(payload, StandardCharsets.UTF_8));
        post.setHeader("Content-Type", ContentType.APPLICATION_JSON.getMimeType());
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpResponse response = httpClient.execute(post);
            log.debug("Dumont index response [{}]: {}", url, response.getStatusLine());
        } catch (Exception e) {
            log.error("Failed to send index request to {}: {}", url, e.getMessage(), e);
        }
    }
}

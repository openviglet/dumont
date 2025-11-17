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
import java.util.Optional;

@Slf4j
public class DumAemEventUtils {

    public static final String API_TURING_AEM_REINDEX = "/api/v2/aem/index/";
    public static final String CONTENT_TYPE = "Content-Type";

    public static void index(DumAemIndexerConfig config, String path, DumAemEvent event) {
        index(config, Collections.singletonList(path), event);
    }

    public static void index(DumAemIndexerConfig config, List<String> pathList, DumAemEvent event) {
        if (!config.enabled()) {
            return;
        }
        try {
            Optional.ofNullable(new ObjectMapper()
                    .writeValueAsString(DumAemPayload
                            .builder()
                            .paths(pathList)
                            .event(event)
                            .build()))
                    .ifPresent(payload -> indexContent(config, payload));
        } catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);
        }
    }

    public static void indexContent(DumAemIndexerConfig config, String payload) {
        HttpPost post = Optional.of(config.host() + API_TURING_AEM_REINDEX + config.configName())
                .map(HttpPost::new)
                .orElse(null);
        StringEntity entity = new StringEntity(payload, StandardCharsets.UTF_8);
        post.setEntity(entity);
        post.setHeader(CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpResponse response = httpClient.execute(post);
            log.debug("Response Body: {}", response.getEntity());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}

package com.viglet.dumont.connector.plugin.aem.api;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpHeaders;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.utils.DumAemCommonsUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v2/aem/source")
@Tag(name = "AEM Source Wizard", description = "AEM Source Wizard operations")
@Slf4j
public class DumAemSourceWizardApi {

    @Operation(summary = "Test connection to an AEM instance")
    @PostMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection(
            @RequestBody DumAemConnectionRequest request) {
        try {
            String testUrl = "%s/content.1.json".formatted(request.getEndpoint());
            String authHeader = "Basic " + Base64.getEncoder().encodeToString(
                    (request.getUsername() + ":" + request.getPassword()).getBytes());

            HttpGet httpGet = new HttpGet(URI.create(testUrl));
            httpGet.setHeader(HttpHeaders.AUTHORIZATION, authHeader);

            try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
                return client.execute(httpGet, response -> {
                    int statusCode = response.getCode();
                    log.info("AEM test-connection {} returned HTTP {}", testUrl, statusCode);
                    if (statusCode == 200) {
                        return ResponseEntity.ok(Map.of(
                                "success", true,
                                "message", "Connection successful"));
                    }
                    return ResponseEntity.ok(Map.of(
                            "success", false,
                            "message", "AEM returned HTTP %d. Check your credentials and endpoint."
                                    .formatted(statusCode)));
                });
            }
        } catch (Exception e) {
            log.error("AEM connection test failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Connection failed: " + e.getMessage()));
        }
    }

    @Operation(summary = "Browse AEM content tree")
    @PostMapping("/browse")
    public ResponseEntity<Map<String, Object>> browse(
            @RequestBody DumAemBrowseRequest request) {
        try {
            var config = DumAemConfiguration.builder()
                    .url(request.getEndpoint())
                    .username(request.getUsername())
                    .password(request.getPassword())
                    .rootPath(request.getPath())
                    .build();

            var response = DumAemCommonsUtils.getDepthJson(
                    request.getPath(), config, 1);

            if (response.isPresent()) {
                JSONObject json = response.get();
                List<Map<String, String>> children = new ArrayList<>();
                for (String key : json.keySet()) {
                    Object value = json.get(key);
                    if (value instanceof JSONObject child) {
                        String primaryType = child.optString("jcr:primaryType", "");
                        if (!primaryType.isEmpty()) {
                            String childPath = request.getPath().endsWith("/")
                                    ? request.getPath() + key
                                    : request.getPath() + "/" + key;
                            children.add(Map.of(
                                    "name", key,
                                    "path", childPath,
                                    "primaryType", primaryType));
                        }
                    }
                }
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "children", children));
            }
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "children", Collections.emptyList()));
        } catch (Exception e) {
            log.error("AEM browse failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "children", Collections.emptyList()));
        }
    }
}

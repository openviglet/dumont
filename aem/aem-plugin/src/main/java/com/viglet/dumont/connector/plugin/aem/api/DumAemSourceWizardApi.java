package com.viglet.dumont.connector.plugin.aem.api;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpHeaders;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.utils.DumAemCommonsUtils;
import com.viglet.dumont.connector.plugin.aem.service.DumAemExtractorScannerService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemExtractorScannerService.ExtractorClass;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v2/aem/source")
@Tag(name = "AEM Source Wizard", description = "AEM Source Wizard operations")
@Slf4j
public class DumAemSourceWizardApi {

    /** JCR content paths are always '/'-delimited (not the OS file separator). */
    private static final String JCR_PATH_SEPARATOR = "/";

    private static final String RESP_SUCCESS = "success";
    private static final String RESP_MESSAGE = "message";
    private static final String RESP_CHILDREN = "children";

    private final DumAemExtractorScannerService extractorScanner;

    public DumAemSourceWizardApi(DumAemExtractorScannerService extractorScanner) {
        this.extractorScanner = extractorScanner;
    }

    @Operation(summary = "List Java extractor classes available in the JVM classpath")
    @GetMapping("/extractors")
    public List<ExtractorClass> listExtractors(
            @RequestParam(defaultValue = "attribute") String category,
            @RequestParam(required = false) String query) {
        return extractorScanner.list(category, query);
    }

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
                                RESP_SUCCESS, true,
                                RESP_MESSAGE, "Connection successful"));
                    }
                    return ResponseEntity.ok(Map.of(
                            RESP_SUCCESS, false,
                            RESP_MESSAGE, "AEM returned HTTP %d. Check your credentials and endpoint."
                                    .formatted(statusCode)));
                });
            }
        } catch (Exception e) {
            log.error("AEM connection test failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    RESP_SUCCESS, false,
                    RESP_MESSAGE, "Connection failed: " + e.getMessage()));
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
                    toChildNode(request.getPath(), key, json.get(key)).ifPresent(children::add);
                }
                return ResponseEntity.ok(Map.of(
                        RESP_SUCCESS, true,
                        RESP_CHILDREN, children));
            }
            return ResponseEntity.ok(Map.of(
                    RESP_SUCCESS, false,
                    RESP_CHILDREN, Collections.emptyList()));
        } catch (Exception e) {
            log.error("AEM browse failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    RESP_SUCCESS, false,
                    RESP_CHILDREN, Collections.emptyList()));
        }
    }

    /** Maps one JCR child node to its {name, path, primaryType} entry, when it is a typed node. */
    private Optional<Map<String, String>> toChildNode(String basePath, String key, Object value) {
        if (!(value instanceof JSONObject child)) {
            return Optional.empty();
        }
        String primaryType = child.optString("jcr:primaryType", "");
        if (primaryType.isEmpty()) {
            return Optional.empty();
        }
        String childPath = basePath.endsWith(JCR_PATH_SEPARATOR) ? basePath + key
                : basePath + JCR_PATH_SEPARATOR + key;
        return Optional.of(Map.of("name", key, "path", childPath, "primaryType", primaryType));
    }
}

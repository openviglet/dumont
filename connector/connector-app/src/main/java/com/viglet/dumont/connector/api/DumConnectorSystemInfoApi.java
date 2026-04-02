/*
 *
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * REST API that exposes runtime system information for the Dumont connector,
 * consumed by Turing's Integration System Information page.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.18
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v2/connector/system-info")
@Tag(name = "System Info", description = "Connector System Information API")
public class DumConnectorSystemInfoApi {

    @Value("${dumont.indexing.provider:turing}")
    private String indexingProvider;

    @Value("${turing.url:#{null}}")
    private String turingUrl;

    @Value("${turing.apiKey:#{null}}")
    private String turingApiKey;

    @Value("${turing.solr.endpoint:#{null}}")
    private String turingSolrEndpoint;

    @Value("${dumont.indexing.solr.url:#{null}}")
    private String solrUrl;

    @Value("${dumont.indexing.solr.collection:#{null}}")
    private String solrCollection;

    @Value("${dumont.indexing.elasticsearch.url:#{null}}")
    private String elasticsearchUrl;

    @Value("${dumont.indexing.elasticsearch.index:#{null}}")
    private String elasticsearchIndex;

    @Value("${dumont.indexing.elasticsearch.username:#{null}}")
    private String elasticsearchUsername;

    @Operation(summary = "Get connector system information")
    @GetMapping
    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new LinkedHashMap<>();

        // Application
        String version = getClass().getPackage().getImplementationVersion();
        info.put("appVersion", version != null ? version : "dev");
        info.put("appName", "Viglet Dumont DEP");

        // Java
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("javaVendor", System.getProperty("java.vendor"));
        info.put("javaVmName", System.getProperty("java.vm.name"));

        // OS
        info.put("osName", System.getProperty("os.name"));
        info.put("osVersion", System.getProperty("os.version"));
        info.put("osArch", System.getProperty("os.arch"));

        // Memory
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        Map<String, Long> memory = new LinkedHashMap<>();
        memory.put("maxMemory", maxMemory);
        memory.put("totalMemory", totalMemory);
        memory.put("usedMemory", usedMemory);
        memory.put("freeMemory", freeMemory);

        long totalPhysicalMemory = -1;
        long freePhysicalMemory = -1;
        try {
            var osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean osMx) {
                totalPhysicalMemory = osMx.getTotalMemorySize();
                freePhysicalMemory = osMx.getFreeMemorySize();
            }
        } catch (Exception ignored) {
            // Not available on all JVMs
        }
        memory.put("totalPhysicalMemory", totalPhysicalMemory);
        memory.put("freePhysicalMemory", freePhysicalMemory);
        info.put("memory", memory);

        // Disk
        File root = new File(".");
        Map<String, Long> disk = new LinkedHashMap<>();
        disk.put("totalSpace", root.getTotalSpace());
        disk.put("usableSpace", root.getUsableSpace());
        disk.put("usedSpace", root.getTotalSpace() - root.getUsableSpace());
        info.put("disk", disk);

        // Indexing
        Map<String, Object> indexing = new LinkedHashMap<>();
        indexing.put("provider", propertyEntry(indexingProvider, "dumont.indexing.provider"));
        switch (indexingProvider) {
            case "turing" -> {
                indexing.put("turingUrl", propertyEntry(turingUrl, "turing.url"));
                indexing.put("turingSolrEndpoint", propertyEntry(turingSolrEndpoint, "turing.solr.endpoint"));
            }
            case "solr" -> {
                indexing.put("solrUrl", propertyEntry(solrUrl, "dumont.indexing.solr.url"));
                indexing.put("solrCollection", propertyEntry(solrCollection, "dumont.indexing.solr.collection"));
            }
            case "elasticsearch" -> {
                indexing.put("elasticsearchUrl", propertyEntry(elasticsearchUrl, "dumont.indexing.elasticsearch.url"));
                indexing.put("elasticsearchIndex", propertyEntry(elasticsearchIndex, "dumont.indexing.elasticsearch.index"));
                indexing.put("elasticsearchUsername", propertyEntry(elasticsearchUsername, "dumont.indexing.elasticsearch.username"));
            }
        }
        info.put("indexing", indexing);

        // Status
        info.put("status", "UP");

        return info;
    }

    @Operation(summary = "Get system properties and environment variables")
    @GetMapping("/variables")
    public Map<String, String> getVariables() {
        Map<String, String> variables = new TreeMap<>();
        System.getProperties().forEach((key, value) ->
                variables.put(String.valueOf(key), String.valueOf(value)));
        System.getenv().forEach((key, value) ->
                variables.put("env." + key, value));
        return variables;
    }

    private Map<String, String> propertyEntry(String value, String property) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("value", value);
        entry.put("property", property);
        return entry;
    }
}

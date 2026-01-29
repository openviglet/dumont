/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.plugin.aem.api;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.dumont.connector.plugin.aem.DumAemPluginProcess;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v2/aem")
@Tag(name = "AEM API", description = "AEM API")
public class DumAemApi {
    private final DumAemPluginProcess dumAemPluginProcess;
    private final List<String> currentContentIdList = new ArrayList<>();
    private LocalDateTime currentStandAloneUpdate = LocalDateTime.now();

    public DumAemApi(DumAemPluginProcess dumAemPluginProcess) {
        this.dumAemPluginProcess = dumAemPluginProcess;
    }

    @GetMapping("status")
    public Map<String, String> status() {
        return statusOk();
    }

    @PostMapping("index/{source}")
    public ResponseEntity<Map<String, String>> indexContentId(@PathVariable String source,
            @RequestBody DumAemPathList pathList) {
        System.out.println(pathList);
        if (hasNonRepeatedRequest(source, pathList.getPaths())) {
            dumAemPluginProcess.sentToIndexStandaloneAsync(source, pathList);
        }
        return ResponseEntity.ok(statusSent());
    }

    private void updateCurrentRequests(String name, List<String> paths) {
        currentContentIdList.clear();
        paths.forEach(path -> currentContentIdList.add(getSourceWithContentId(name, path)));
        currentStandAloneUpdate = LocalDateTime.now();
    }

    private boolean hasNonRepeatedRequest(String name, List<String> paths) {
        Duration duration = Duration.between(currentStandAloneUpdate, LocalDateTime.now());
        if (duration.getSeconds() > 30L)
            return true;
        new ArrayList<>(paths).forEach(path -> {
            String pathName = getSourceWithContentId(name, path);
            if (currentContentIdList.contains(pathName)) {
                paths.remove(path);
                log.warn("Skipping. Repeated request: {}", pathName);
            }
        });
        if (hasPath(paths))
            updateCurrentRequests(name, paths);
        return hasPath(paths);
    }

    private static boolean hasPath(List<String> paths) {
        return !paths.isEmpty();
    }

    private static @NotNull String getSourceWithContentId(String name, String path) {
        return name + "-" + path;
    }

    private static Map<String, String> statusOk() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "ok");
        return status;
    }

    private static Map<String, String> statusSent() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "sent");
        return status;
    }
}

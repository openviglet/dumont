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

package com.viglet.dumont.connector.plugin.aem.service;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.viglet.dumont.connector.aem.commons.DumAemObjectGeneric;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemContentMapping;
import com.viglet.dumont.connector.aem.commons.utils.DumAemCommonsUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Shared XF/CF dependency-harvest seam (roadmap <strong>D28</strong>).
 *
 * <p>
 * Collects every Experience Fragment / Content Fragment path a content embeds so that editing a
 * shared component reindexes every page that uses it (the incremental dependency-graph reindex). The
 * harvest was originally inline in {@code DumAemJobService}; it is lifted here so both the legacy job
 * pipeline and the structured-source path ({@code DumAemStructuredSource} / {@code DumAemPlugin},
 * roadmap D21) emit the same dependency set, persisted via {@code DumConnectorDependency} and matched
 * back by {@code DumConnectorContext#getObjectIdByDependency}.
 *
 * <p>
 * Gated by {@code dumont.dependencies.enabled} and the optional per-source {@code dependencyClass}
 * (via {@link DumAemContentDefinitionService#shouldIndexDependencies}); when either opts out, the
 * returned set is empty and no model.json request is issued.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class DumAemDependencyService {

    private static final int MAX_DEPENDENCY_PATH_LENGTH = 500;
    private static final String EXPERIENCE_FRAGMENTS_PREFIX = "/content/experience-fragments/";
    private static final String DAM_PREFIX = "/content/dam/";

    private final DumAemContentDefinitionService contentDefinitionService;
    private final boolean dependenciesEnabled;

    public DumAemDependencyService(DumAemContentDefinitionService contentDefinitionService,
            @Value("${dumont.dependencies.enabled:true}") boolean dependenciesEnabled) {
        this.contentDefinitionService = contentDefinitionService;
        this.dependenciesEnabled = dependenciesEnabled;
    }

    /**
     * When {@code dumont.dependencies.enabled=true} (and the optional {@code dependencyClass} does
     * not opt out), collects every {@code /content} path referenced in the content's infinity.json
     * and model.json, restricted to Experience Fragments and Content Fragments. Duplicates across the
     * two sources are deduplicated by the returned {@code Set}. When the flag is disabled or the
     * custom class opts out, returns an empty set and issues no model.json request.
     */
    public Set<String> collectDependencies(DumAemObjectGeneric aemObjectGeneric,
            DumAemConfiguration config, DumAemContentMapping contentMapping) {
        if (!dependenciesEnabled) {
            return Set.of();
        }
        if (!contentDefinitionService.shouldIndexDependencies(aemObjectGeneric, config,
                contentMapping)) {
            log.debug("Custom dependencyClass opted out of dependency indexing for {}",
                    aemObjectGeneric.getPath());
            return Set.of();
        }
        String currentPath = aemObjectGeneric.getPath();
        Set<String> dependencies = new HashSet<>(filterSelfReferences(currentPath,
                DumAemCommonsUtils.getDependencies(aemObjectGeneric.getJcrNode())));
        DumAemCommonsUtils.getModelJson(currentPath, config)
                .map(DumAemCommonsUtils::getDependencies)
                .map(deps -> filterSelfReferences(currentPath, deps))
                .ifPresent(dependencies::addAll);
        log.debug("Collected {} dependencies for {}", dependencies.size(), currentPath);
        return dependencies;
    }

    /**
     * Restricts dependencies to Experience Fragments and Content Fragments
     * (which live under {@code /content/dam}). Drops:
     * <ul>
     * <li>Paths outside {@code /content/experience-fragments/} and
     * {@code /content/dam/} — pages and other content are not tracked as
     * dependencies.</li>
     * <li>Paths with an extension on the last segment (e.g. {@code .html},
     * {@code .json}, {@code .svg}, {@code .jpg}). These are renditions or
     * URL variants; only the canonical XF/CF root is kept.</li>
     * <li>Paths pointing into a JCR content sub-tree — both the JCR form
     * ({@code .../jcr:content/...}) and the URL-encoded form
     * ({@code .../_jcr_content/...}).</li>
     * <li>Paths longer than the {@code objectId} column.</li>
     * </ul>
     */
    private static Set<String> filterSelfReferences(String currentPath, Set<String> deps) {
        if (deps == null || deps.isEmpty()) {
            return deps == null ? Set.of() : deps;
        }
        return deps.stream()
                .filter(dep -> dep.startsWith(EXPERIENCE_FRAGMENTS_PREFIX)
                        || dep.startsWith(DAM_PREFIX))
                .filter(dep -> !hasExtension(dep))
                .filter(dep -> !dep.contains("/jcr:content"))
                .filter(dep -> !dep.contains("/_jcr_content"))
                .filter(dep -> !dep.equals(currentPath))
                .filter(dep -> dep.length() <= MAX_DEPENDENCY_PATH_LENGTH)
                .collect(Collectors.toSet());
    }

    private static boolean hasExtension(String path) {
        int lastSlash = path.lastIndexOf('/');
        String lastSegment = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        return lastSegment.indexOf('.') >= 0;
    }
}

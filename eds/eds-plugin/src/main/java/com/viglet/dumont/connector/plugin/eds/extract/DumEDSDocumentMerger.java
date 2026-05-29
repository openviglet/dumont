/*
 * Copyright (C) 2016-2024 the original author or authors.
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

package com.viglet.dumont.connector.plugin.eds.extract;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Merges two metadata maps (from the EDS sheet JSON and from the page's HTML
 * meta tags) into a single result. Rules:
 *
 * <ul>
 *   <li>JSON-only keys are kept verbatim (e.g. {@code path}, {@code robots},
 *       and any custom column the site declared).</li>
 *   <li>HTML-only keys are added.</li>
 *   <li>On conflict, the <strong>HTML value wins</strong> — rationale in the
 *       spec: the HTML is rendered each publish so it is often fresher than the
 *       cached row in the sheet, and OG tags are explicitly authored.</li>
 *   <li>Empty/blank values never displace a non-empty value.</li>
 * </ul>
 */
@Component
public class DumEDSDocumentMerger {

    public Map<String, String> merge(Map<String, String> fromJson, Map<String, String> fromHtml) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (fromJson != null) {
            fromJson.forEach((k, v) -> putIfPresent(merged, k, v));
        }
        if (fromHtml != null) {
            fromHtml.forEach((k, v) -> {
                if (v != null && !v.isBlank()) {
                    merged.put(k, v.trim());
                }
            });
        }
        return merged;
    }

    private static void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value.trim());
        }
    }
}

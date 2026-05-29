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

import java.util.Collection;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Extracts a plaintext body from an HTML document. The work is done on a clone
 * of the document so the caller can still extract metadata from the original
 * after this runs. Pipeline:
 *
 * <ol>
 *   <li>Pick the {@code bodySelector} subtree (default {@code main}, falls back
 *       to the document body when no match).</li>
 *   <li>Remove every node matching any of {@code removeSelectors} — typically
 *       nav, footer, aside, and CTA-style fragments that bleed into {@code main}
 *       in many EDS templates.</li>
 *   <li>Strip {@code <script>}/{@code <style>} unconditionally.</li>
 *   <li>Take the visible text and collapse whitespace runs to single spaces
 *       while preserving paragraph breaks.</li>
 * </ol>
 */
@Component
public class DumEDSBodyExtractor {

    public static final String DEFAULT_SELECTOR = "main";

    private static final Pattern MULTI_WHITESPACE = Pattern.compile("[ \\t\\f]{2,}");
    private static final Pattern MULTI_NEWLINE = Pattern.compile("\\n{3,}");

    public String extract(Document doc, String bodySelector,
            Collection<String> removeSelectors) {
        if (doc == null) {
            return "";
        }
        Document working = doc.clone();
        String selector = (bodySelector == null || bodySelector.isBlank())
                ? DEFAULT_SELECTOR
                : bodySelector.trim();
        Element root = working.selectFirst(selector);
        if (root == null) {
            root = working.body();
            if (root == null) {
                return "";
            }
        }
        root.select("script, style, noscript").remove();
        if (removeSelectors != null) {
            for (String sel : removeSelectors) {
                if (sel != null && !sel.isBlank()) {
                    root.select(sel).remove();
                }
            }
        }
        return normalise(root.wholeText());
    }

    static String normalise(String raw) {
        if (raw == null) {
            return "";
        }
        String collapsed = MULTI_WHITESPACE.matcher(raw).replaceAll(" ");
        collapsed = MULTI_NEWLINE.matcher(collapsed).replaceAll("\n\n");
        StringBuilder sb = new StringBuilder(collapsed.length());
        for (String line : collapsed.split("\n", -1)) {
            sb.append(line.strip()).append('\n');
        }
        return sb.toString().strip();
    }
}

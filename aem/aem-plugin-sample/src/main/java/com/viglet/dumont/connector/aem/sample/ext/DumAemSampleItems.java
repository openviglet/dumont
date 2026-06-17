/*
 *
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

package com.viglet.dumont.connector.aem.sample.ext;

import java.util.Map;
import java.util.function.Consumer;

import com.viglet.dumont.connector.aem.sample.beans.DumAemSampleModel;
import com.viglet.dumont.connector.aem.sample.beans.DumAemSampleModel.DumAemSampleModelItem;

/**
 * Shared traversal mechanics for the sample component extractors.
 * <p>
 * AEM model.json content is a tree of nested {@code :items}. Each component
 * extractor cares about a single concern (paragraphs, texts, images, content
 * fragments…) but they all walk the same tree, so the recursion and HTML
 * stripping live here, keeping every extractor a small, single-responsibility
 * unit.
 */
final class DumAemSampleItems {

    private DumAemSampleItems() {
    }

    /**
     * Visits every item in the model's {@code :items} tree, depth-first,
     * including nested children. No-op when the model has no items.
     */
    static void forEach(DumAemSampleModel model, Consumer<DumAemSampleModelItem> visitor) {
        if (model == null || model.getItems() == null) {
            return;
        }
        visit(model.getItems(), visitor);
    }

    private static void visit(Map<String, DumAemSampleModelItem> items,
            Consumer<DumAemSampleModelItem> visitor) {
        for (DumAemSampleModelItem item : items.values()) {
            visitor.accept(item);
            if (item.getItems() != null) {
                visit(item.getItems(), visitor);
            }
        }
    }

    /**
     * Strips HTML tags and entities, collapsing whitespace. Returns an empty
     * string for null input.
     */
    static String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        return html.replaceAll("<[^>]+>", "")
                .replaceAll("&[a-zA-Z]+;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}

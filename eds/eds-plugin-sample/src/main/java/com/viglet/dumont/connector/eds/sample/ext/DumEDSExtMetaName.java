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

package com.viglet.dumont.connector.eds.sample.ext;

import java.util.Optional;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.viglet.dumont.connector.eds.commons.DumEDSContext;
import com.viglet.dumont.connector.eds.commons.ext.DumEDSExtInterface;
import com.viglet.turing.client.sn.TurMultiValue;

/**
 * Reference implementation of {@link DumEDSExtInterface} that reads a specific
 * {@code <meta name="...">} value from the page's HTML. Subclass and override
 * {@link #metaName()} to point at any custom meta tag — useful when an EDS
 * site exposes site-specific metadata that the built-in extractor (which only
 * understands {@code og:*} and the canonical {@code modified-time}/{@code
 * published-time} tags) doesn't pick up.
 *
 * <p>Example: extract a "category" tag the site renders as
 * {@code <meta name="x-section" content="finance">}:
 *
 * <pre>{@code
 * public class MyCategoryExt extends DumEDSExtMetaName {
 *     @Override
 *     protected String metaName() { return "x-section"; }
 * }
 * }</pre>
 *
 * <p>Calling {@link #consume} directly with a default instance reads the
 * generic {@code <meta name="keywords">} as a comma-separated multi-value
 * convenience.
 */
public class DumEDSExtMetaName implements DumEDSExtInterface {

    @Override
    public Optional<TurMultiValue> consume(DumEDSContext context) {
        if (context == null || context.getDocument() == null) {
            return Optional.empty();
        }
        Document doc = context.getDocument();
        Element meta = doc.selectFirst("meta[name=" + metaName() + "]");
        if (meta == null) {
            return Optional.empty();
        }
        String content = meta.attr("content");
        if (content.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(toMultiValue(content));
    }

    /**
     * The {@code name} attribute of the {@code <meta>} tag to read. Override
     * in subclasses; the default reads {@code keywords} for demonstration.
     */
    protected String metaName() {
        return "keywords";
    }

    /**
     * Default conversion to {@link TurMultiValue}: splits on commas to produce
     * a multi-valued attribute. Override for other separators or single-value
     * semantics.
     */
    protected TurMultiValue toMultiValue(String raw) {
        TurMultiValue out = new TurMultiValue();
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }
}

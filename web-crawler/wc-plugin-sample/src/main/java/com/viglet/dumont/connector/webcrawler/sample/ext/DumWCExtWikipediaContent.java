/*
 * Copyright (C) 2016-2024 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.viglet.dumont.connector.webcrawler.sample.ext;

import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.dumont.connector.webcrawler.commons.DumWCContext;
import com.viglet.dumont.connector.webcrawler.commons.ext.DumWCExtInterface;
import org.jsoup.nodes.Element;

import java.util.Optional;

/**
 * Extracts the main content from a Wikipedia article page
 * by selecting the #mw-content-text div and stripping references/tables.
 */
public class DumWCExtWikipediaContent implements DumWCExtInterface {

    @Override
    public Optional<TurMultiValue> consume(DumWCContext context) {
        Element contentDiv = context.getDocument().selectFirst("#mw-content-text .mw-parser-output");
        if (contentDiv == null) {
            return Optional.empty();
        }

        // Remove navigation boxes, references, tables, and edit sections
        contentDiv.select(".reflist, .navbox, .sidebar, .infobox, table, .mw-editsection, " +
                ".reference, sup.reference, .noprint, .mw-empty-elt, style, script").remove();

        String text = contentDiv.text();
        if (text.isBlank()) {
            return Optional.empty();
        }

        // Limit content to ~10000 chars to avoid oversized index entries
        if (text.length() > 10000) {
            text = text.substring(0, 10000);
        }

        return Optional.of(TurMultiValue.singleItem(text));
    }
}

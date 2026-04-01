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

import java.util.Optional;

/**
 * Extracts Wikipedia article title by removing the " - Wikipedia" suffix.
 */
public class DumWCExtWikipediaTitle implements DumWCExtInterface {
    private static final String WIKIPEDIA_SUFFIX = " - Wikipedia";

    @Override
    public Optional<TurMultiValue> consume(DumWCContext context) {
        String title = context.getDocument().title();
        if (title != null && title.endsWith(WIKIPEDIA_SUFFIX)) {
            title = title.substring(0, title.length() - WIKIPEDIA_SUFFIX.length());
        }
        return Optional.ofNullable(title)
                .filter(t -> !t.isBlank())
                .map(TurMultiValue::singleItem);
    }
}

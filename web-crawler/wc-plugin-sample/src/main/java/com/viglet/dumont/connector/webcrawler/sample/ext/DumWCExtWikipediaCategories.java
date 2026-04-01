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
import org.jsoup.select.Elements;

import java.util.List;
import java.util.Optional;

/**
 * Extracts categories from a Wikipedia article page.
 */
public class DumWCExtWikipediaCategories implements DumWCExtInterface {

    @Override
    public Optional<TurMultiValue> consume(DumWCContext context) {
        Elements categoryLinks = context.getDocument().select("#mw-normal-catlinks ul li a");
        if (categoryLinks.isEmpty()) {
            return Optional.empty();
        }
        List<String> categories = categoryLinks.stream()
                .map(Element::text)
                .filter(text -> !text.isBlank())
                .toList();
        if (categories.isEmpty()) {
            return Optional.empty();
        }
        TurMultiValue multiValue = new TurMultiValue();
        multiValue.addAll(categories);
        return Optional.of(multiValue);
    }
}

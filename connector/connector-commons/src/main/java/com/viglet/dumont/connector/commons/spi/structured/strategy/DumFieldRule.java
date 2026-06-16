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

package com.viglet.dumont.connector.commons.spi.structured.strategy;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Element;

/**
 * A declarative rule that maps one schema field to content inside a detail page, used by
 * {@link DumHtmlFieldExtractor} (roadmap <strong>D02</strong>).
 *
 * <p>
 * The {@link #selector() selector} is evaluated against the scope element; {@link #attribute()}
 * picks what to read from each match — the element's text when blank, otherwise an attribute (e.g.
 * {@code "abs:href"} for an absolute link, {@code "datetime"}, {@code "content"}). A
 * {@link #multiValued() multi-valued} rule collects every match; a single-valued one takes the
 * first. Values still pass through the {@code DumStructuredRecord} grounding contract, so a selector
 * that matches nothing simply leaves the field absent.
 *
 * @param field       the declared schema field this rule fills
 * @param selector    the CSS selector evaluated within the record's scope element
 * @param attribute   the attribute to read, or {@code null}/blank to read the element text
 * @param multiValued whether every match is collected (vs. just the first)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record DumFieldRule(String field, String selector, String attribute, boolean multiValued) {

    public DumFieldRule {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("Field rule field is required");
        }
        if (selector == null || selector.isBlank()) {
            throw new IllegalArgumentException("Field rule selector is required");
        }
    }

    /** A single-valued rule reading the matched element's text. */
    public static DumFieldRule text(String field, String selector) {
        return new DumFieldRule(field, selector, null, false);
    }

    /** A single-valued rule reading an attribute (e.g. {@code "abs:href"}) of the matched element. */
    public static DumFieldRule attr(String field, String selector, String attribute) {
        return new DumFieldRule(field, selector, attribute, false);
    }

    /** A multi-valued rule collecting the text of every matched element. */
    public static DumFieldRule multiText(String field, String selector) {
        return new DumFieldRule(field, selector, null, true);
    }

    /** Resolve this rule against a scope element into the value(s) to set on the record. */
    Object resolve(Element scope) {
        if (multiValued) {
            List<String> values = new ArrayList<>();
            scope.select(selector).forEach(element -> {
                String value = read(element);
                if (!value.isBlank()) {
                    values.add(value);
                }
            });
            return values;
        }
        Element first = scope.selectFirst(selector);
        return first == null ? null : read(first);
    }

    private String read(Element element) {
        return (attribute == null || attribute.isBlank()) ? element.text()
                : element.attr(attribute);
    }
}

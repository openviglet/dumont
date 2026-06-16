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
import org.jsoup.select.Elements;

/**
 * Maps one schema field to a column of a tabular listing, used by {@link DumTabularListingStrategy}
 * (roadmap <strong>D02</strong>).
 *
 * <p>
 * A column is addressed either by its zero-based {@link #columnIndex() position} among the row's
 * cells — the common SIGAA case — or by a {@link #selector() CSS selector} evaluated within the row
 * (e.g. to read an {@code <a href>} inside a cell). {@link #attribute()} selects what to read (text
 * when blank, otherwise an attribute such as {@code "abs:href"}), and {@link #multiValued()} governs
 * whether a selector collects every match. Values pass through the record grounding contract.
 *
 * @param field       the declared schema field this column fills
 * @param columnIndex the zero-based cell position, or a negative value when {@code selector} is used
 * @param selector    a CSS selector evaluated within the row; when set it overrides {@code columnIndex}
 * @param attribute   the attribute to read, or {@code null}/blank to read the cell text
 * @param multiValued whether a selector-based rule collects every match (vs. just the first)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record DumColumnRule(String field, int columnIndex, String selector, String attribute,
        boolean multiValued) {

    public DumColumnRule {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("Column rule field is required");
        }
        boolean hasSelector = selector != null && !selector.isBlank();
        if (!hasSelector && columnIndex < 0) {
            throw new IllegalArgumentException(
                    "Column rule '" + field + "' needs a column index or a selector");
        }
    }

    /** A rule reading the text of the cell at {@code columnIndex}. */
    public static DumColumnRule column(String field, int columnIndex) {
        return new DumColumnRule(field, columnIndex, null, null, false);
    }

    /** A rule reading an attribute of an element selected within the cell at {@code columnIndex}. */
    public static DumColumnRule column(String field, int columnIndex, String attribute) {
        return new DumColumnRule(field, columnIndex, null, attribute, false);
    }

    /** A rule reading the text of the first element matching {@code selector} within the row. */
    public static DumColumnRule selector(String field, String selector) {
        return new DumColumnRule(field, -1, selector, null, false);
    }

    /** A rule reading an attribute of the first element matching {@code selector} within the row. */
    public static DumColumnRule selector(String field, String selector, String attribute) {
        return new DumColumnRule(field, -1, selector, attribute, false);
    }

    /** Resolve this rule against a table row into the value(s) to set on the record. */
    Object resolve(Element row, Elements cells) {
        if (selector != null && !selector.isBlank()) {
            return resolveBySelector(row);
        }
        if (columnIndex >= cells.size()) {
            return multiValued ? List.of() : null;
        }
        return read(cells.get(columnIndex));
    }

    private Object resolveBySelector(Element row) {
        if (multiValued) {
            List<String> values = new ArrayList<>();
            row.select(selector).forEach(element -> {
                String value = read(element);
                if (!value.isBlank()) {
                    values.add(value);
                }
            });
            return values;
        }
        Element first = row.selectFirst(selector);
        return first == null ? null : read(first);
    }

    private String read(Element element) {
        return (attribute == null || attribute.isBlank()) ? element.text()
                : element.attr(attribute);
    }
}

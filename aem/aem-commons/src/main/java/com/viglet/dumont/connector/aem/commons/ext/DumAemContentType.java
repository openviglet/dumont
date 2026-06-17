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

package com.viglet.dumont.connector.aem.commons.ext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A content type an AEM extension declares (roadmap <strong>D25</strong>): a canonical name, the
 * fields that make up its schema, and the {@link DumAemComponentExtractor component-extractor}
 * composition that populates them.
 *
 * <p>
 * The redesign replaces "one source = one schema" with a content-type model. A template — collapsed
 * through the {@link DumAemExtModelJsonBase#templateNameAliases() alias map} (e.g. {@code noticias} /
 * {@code evento} / {@code educacao-executiva} / {@code institucional}) — routes to <em>one</em>
 * content type, and {@code extract(id)} runs only that type's extractor composition and binds the
 * record to that type's {@link #fields() schema}. Each content type is independently testable and
 * its field coverage/drift (D15) is reported on its own.
 *
 * @param <T> the root model bean type for the model.json response
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class DumAemContentType<T> {

    private final String name;
    private final List<DumAemContentTypeField> fields;
    private final List<DumAemComponentExtractor<T>> extractors;

    private DumAemContentType(String name, List<DumAemContentTypeField> fields,
            List<DumAemComponentExtractor<T>> extractors) {
        this.name = name;
        this.fields = Collections.unmodifiableList(fields);
        this.extractors = Collections.unmodifiableList(extractors);
    }

    /** The canonical content-type name a resolved template maps to. */
    public String name() {
        return name;
    }

    /** The fields this content type publishes — its schema, in declaration order. */
    public List<DumAemContentTypeField> fields() {
        return fields;
    }

    /** The extractor composition run for a page of this content type, in order. */
    public List<DumAemComponentExtractor<T>> extractors() {
        return extractors;
    }

    public static <T> Builder<T> builder(String name) {
        return new Builder<>(name);
    }

    public static final class Builder<T> {
        private final String name;
        private final List<DumAemContentTypeField> fields = new ArrayList<>();
        private final List<DumAemComponentExtractor<T>> extractors = new ArrayList<>();

        private Builder(String name) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Content-type name is required");
            }
            this.name = name;
        }

        public Builder<T> field(DumAemContentTypeField field) {
            fields.add(field);
            return this;
        }

        public Builder<T> fields(DumAemContentTypeField... fields) {
            for (DumAemContentTypeField field : fields) {
                this.fields.add(field);
            }
            return this;
        }

        public Builder<T> extractor(DumAemComponentExtractor<T> extractor) {
            extractors.add(extractor);
            return this;
        }

        @SafeVarargs
        public final Builder<T> extractors(DumAemComponentExtractor<T>... extractors) {
            for (DumAemComponentExtractor<T> extractor : extractors) {
                this.extractors.add(extractor);
            }
            return this;
        }

        public DumAemContentType<T> build() {
            if (fields.isEmpty()) {
                throw new IllegalStateException(
                        "Content type '" + name + "' must declare at least one field");
            }
            return new DumAemContentType<>(name, fields, extractors);
        }
    }
}

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

package com.viglet.dumont.connector.commons.spi.structured.infer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The editable <em>draft</em> proposed by the AI-assisted source inference (roadmap
 * <strong>D13</strong>): a classified {@link DumSourceShape}, the shape-specific strategy config and
 * a field manifest. It is a <strong>starting point a human edits and confirms</strong> — it never
 * auto-publishes and never indexes on its own.
 *
 * <p>
 * <strong>Grounding.</strong> A draft describes <em>structure</em> (which strategy, which selector,
 * which field type), never content. Fields with a blank name are dropped on construction rather than
 * carried as placeholders, and an inference that could not classify the source returns
 * {@link #unknown(String)} — declining is always preferred to guessing.
 *
 * @param shape          the classified source shape
 * @param confidence     the model's self-reported confidence in {@code [0,1]} (clamped)
 * @param strategyConfig shape-specific knobs (e.g. {@code rowSelector}, {@code idField},
 *                       {@code itemLinkSelector}, {@code sitemapUrl}); never {@code null}
 * @param fields         the proposed field manifest, blank-named entries removed; never {@code null}
 * @param notes          an optional human-readable rationale from the model
 *
 * @author Alexandre Oliveira
 * @since 2026.3.2
 */
public record DumSourceDraft(DumSourceShape shape, double confidence,
        Map<String, String> strategyConfig, List<DumInferredField> fields, String notes) {

    /** Strategy-config key for the data-row CSS selector of a {@link DumSourceShape#TABULAR_LISTING}. */
    public static final String ROW_SELECTOR = "rowSelector";

    /** Strategy-config key naming the field whose value identifies each record. */
    public static final String ID_FIELD = "idField";

    public DumSourceDraft {
        if (shape == null) {
            shape = DumSourceShape.UNKNOWN;
        }
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        strategyConfig = strategyConfig == null ? Map.of()
                : Map.copyOf(strategyConfig);
        fields = fields == null ? List.of()
                : fields.stream().filter(DumInferredField::hasName).toList();
    }

    /** A draft for a source whose shape could not be grounded — no config, no fields. */
    public static DumSourceDraft unknown(String notes) {
        return new DumSourceDraft(DumSourceShape.UNKNOWN, 0.0, Map.of(), List.of(), notes);
    }

    /** The value of a strategy-config key, or {@code null} when absent. */
    public String config(String key) {
        return strategyConfig.get(key);
    }

    /** Builder collecting strategy-config entries and proposed fields in declaration order. */
    public static Builder builder(DumSourceShape shape) {
        return new Builder(shape);
    }

    public static final class Builder {
        private final DumSourceShape shape;
        private final Map<String, String> strategyConfig = new LinkedHashMap<>();
        private final List<DumInferredField> fields = new java.util.ArrayList<>();
        private double confidence;
        private String notes;

        private Builder(DumSourceShape shape) {
            this.shape = shape;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public Builder config(String key, String value) {
            if (key != null && value != null && !value.isBlank()) {
                strategyConfig.put(key, value);
            }
            return this;
        }

        public Builder field(DumInferredField field) {
            if (field != null) {
                fields.add(field);
            }
            return this;
        }

        public DumSourceDraft build() {
            return new DumSourceDraft(shape, confidence, strategyConfig, fields, notes);
        }
    }
}

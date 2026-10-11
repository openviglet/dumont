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

package com.viglet.dumont.connector.plugin.jobs.source.seniority;

import java.util.Collection;
import java.util.Map;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnricher;
import com.viglet.dumont.connector.plugin.jobs.DumJobsSchema;

/**
 * The single canonical seniority step of every job record (trabalharei <strong>TRA21</strong>): a
 * D04 {@link DumEnricher} that writes {@code seniority} from the title alone, by
 * {@link DumJobSeniorityParser}'s rules. No partner sends a seniority today; {@code extract} leaves
 * the field off the record (D98) so this step stays the only writer when one does.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumJobSeniorityEnricher implements DumEnricher {

    /** The enricher name, also the provenance tag of the field it writes. */
    public static final String NAME = "job-seniority";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, Object> enrich(DumStructuredSource source, DumStructuredRecord rec) {
        Object title = rec.values().get(DumJobsSchema.TITLE);
        if (title instanceof Collection<?> values) {
            title = values.isEmpty() ? null : values.iterator().next();
        }
        if (title == null) {
            return Map.of();
        }
        return DumJobSeniorityParser.parse(title.toString())
                .<Map<String, Object>>map(level -> Map.of(DumJobsSchema.SENIORITY, level))
                .orElseGet(Map::of);
    }
}

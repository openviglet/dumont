/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.plugin.db.source;

import java.util.Collection;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourceProvider;
import com.viglet.dumont.connector.plugin.db.DumDbPlugin;
import com.viglet.dumont.connector.plugin.db.persistence.repository.DumDbSourceRepository;

/**
 * Resolves DB sources from the JPA store into {@link DumDbStructuredSource}s for the generic
 * structured indexing loop (roadmap <strong>D20</strong>). Every resolved source shares the one
 * {@link DumDbRowMapper}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class DumDbSourceProvider implements DumStructuredSourceProvider {

    private final DumDbSourceRepository sourceRepository;
    private final DumDbRowMapper rowMapper;

    public DumDbSourceProvider(DumDbSourceRepository sourceRepository, DumDbRowMapper rowMapper) {
        this.sourceRepository = sourceRepository;
        this.rowMapper = rowMapper;
    }

    @Override
    public String getProviderName() {
        return DumDbPlugin.PROVIDER_NAME;
    }

    @Override
    public Collection<String> sourceNames() {
        return sourceRepository.findAll().stream().map(source -> source.getName()).toList();
    }

    @Override
    public Optional<DumStructuredSource> resolve(String sourceName) {
        return sourceRepository.findByName(sourceName)
                .map(source -> new DumDbStructuredSource(source, rowMapper));
    }
}

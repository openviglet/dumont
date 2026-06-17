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

package com.viglet.dumont.connector.plugin.assets.source;

import java.util.Collection;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourceProvider;
import com.viglet.dumont.connector.plugin.assets.DumAssetsPlugin;
import com.viglet.dumont.connector.plugin.assets.persistence.repository.DumAssetsSourceRepository;

/**
 * Resolves Assets sources from the JPA store into {@link DumAssetsStructuredSource}s for the generic
 * structured indexing loop (roadmap <strong>D22</strong>).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class DumAssetsSourceProvider implements DumStructuredSourceProvider {

    private final DumAssetsSourceRepository sourceRepository;

    public DumAssetsSourceProvider(DumAssetsSourceRepository sourceRepository) {
        this.sourceRepository = sourceRepository;
    }

    @Override
    public String getProviderName() {
        return DumAssetsPlugin.PROVIDER_NAME;
    }

    @Override
    public Collection<String> sourceNames() {
        return sourceRepository.findAll().stream().map(source -> source.getName()).toList();
    }

    @Override
    public Optional<DumStructuredSource> resolve(String sourceName) {
        return sourceRepository.findByName(sourceName).map(DumAssetsStructuredSource::new);
    }
}

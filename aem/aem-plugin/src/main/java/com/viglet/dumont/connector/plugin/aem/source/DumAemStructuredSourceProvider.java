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

package com.viglet.dumont.connector.plugin.aem.source;

import static com.viglet.dumont.connector.aem.commons.DumAemConstants.AEM;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.aem.commons.bean.DumAemEnv;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourceProvider;
import com.viglet.dumont.connector.plugin.aem.navigator.AemNodeNavigator;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import com.viglet.dumont.connector.plugin.aem.service.DumAemContentDefinitionService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemObjectService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemSessionService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemSourceService;

/**
 * Resolves AEM sources from the JPA store into {@link DumAemStructuredSource}s for the generic
 * structured indexing loop (roadmap <strong>D21</strong>).
 *
 * <p>
 * An AEM source publishes to one or two <em>environments</em> (author and/or publish). Each is a
 * distinct {@link DumAemStructuredSource} bound to one {@link DumAemEnv}; the owning plugin drives
 * every {@link #enabledEnvironments(DumAemSource) enabled environment} of a source under a single
 * indexing session (so author/publish + delta semantics stay intact). {@link #resolve(String)}
 * returns the <em>primary</em> environment (author when enabled, otherwise publish), used by the
 * generic loop for ownership, discovery, preview and locale.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class DumAemStructuredSourceProvider implements DumStructuredSourceProvider {

    private final DumAemSourceService sourceService;
    private final DumAemSessionService sessionService;
    private final DumAemObjectService objectService;
    private final DumAemService aemService;
    private final AemNodeNavigator navigator;
    private final DumAemContentDefinitionService contentDefinitionService;
    private final boolean queryBuilderEnabled;

    public DumAemStructuredSourceProvider(DumAemSourceService sourceService,
            DumAemSessionService sessionService, DumAemObjectService objectService,
            DumAemService aemService, AemNodeNavigator navigator,
            DumAemContentDefinitionService contentDefinitionService,
            @Value("${dumont.aem.querybuilder:false}") boolean queryBuilderEnabled) {
        this.sourceService = sourceService;
        this.sessionService = sessionService;
        this.objectService = objectService;
        this.aemService = aemService;
        this.navigator = navigator;
        this.contentDefinitionService = contentDefinitionService;
        this.queryBuilderEnabled = queryBuilderEnabled;
    }

    @Override
    public String getProviderName() {
        return AEM;
    }

    @Override
    public Collection<String> sourceNames() {
        return sourceService.getAllSources().stream().map(DumAemSource::getName).toList();
    }

    @Override
    public Optional<DumStructuredSource> resolve(String sourceName) {
        return sourceService.getDumAemSourceByName(sourceName)
                .flatMap(source -> enabledEnvironments(source).stream().findFirst()
                        .map(env -> adapter(source, env)));
    }

    /** The enabled environments of a source, author first (the primary), then publish. */
    public List<DumAemEnv> enabledEnvironments(DumAemSource source) {
        List<DumAemEnv> environments = new ArrayList<>();
        if (source.isAuthor() && StringUtils.isNotBlank(source.getAuthorSNSite())) {
            environments.add(DumAemEnv.AUTHOR);
        }
        if (source.isPublish() && StringUtils.isNotBlank(source.getPublishSNSite())) {
            environments.add(DumAemEnv.PUBLISHING);
        }
        return environments;
    }

    /** A structured source bound to one source + environment. */
    public DumAemStructuredSource adapter(DumAemSource source, DumAemEnv environment) {
        return new DumAemStructuredSource(source, environment, sessionService, objectService,
                aemService, navigator, contentDefinitionService, queryBuilderEnabled);
    }
}

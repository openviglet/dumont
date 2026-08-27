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

package com.viglet.dumont.connector.plugin.aem.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.viglet.dumont.connector.aem.commons.bean.DumAemFetchStrategy;
import com.viglet.dumont.connector.plugin.aem.export.bean.DumAemSourceExchange;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;

/**
 * The single declaration of an AEM source's exported shape, both directions (roadmap
 * <strong>D65</strong>).
 *
 * <p>
 * The export and the import used to be two hand-written builder chains. They drifted: the exporter
 * set eleven fields and the importer read seventeen, so an exported source came back with no root
 * path, no content type, no credentials and no models — it did not fail, it indexed nothing. Two
 * lists of fields cannot be kept in step by discipline.
 *
 * <p>
 * {@code unmappedTargetPolicy = ERROR} is what replaces the discipline: adding a field to
 * {@link DumAemSource} or to {@link DumAemSourceExchange} without mapping it <em>fails the build</em>
 * rather than silently dropping it on the next export. The collections are the deliberate exceptions
 * — each is persisted through its own repository during import, and built by its own exchange bean
 * during export — and they are {@code ignore}d explicitly, so "not mapped here" is a statement in
 * the file rather than an omission.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DumAemSourceExchangeMapper {

    /**
     * The entity as it travels in an export. Collections are filled by the caller from their own
     * exchange beans, which carry the nested target/source attribute trees.
     */
    @Mapping(target = "attributes", ignore = true)
    @Mapping(target = "models", ignore = true)
    @Mapping(target = "localePaths", ignore = true)
    DumAemSourceExchange toExchange(DumAemSource source);

    /**
     * The entity an import rebuilds. The id travels so re-importing the same file updates rather
     * than duplicating — the caller's own "already present" check reads that id, and generating a
     * fresh one made the check unreachable.
     *
     * <p>
     * {@code fetchStrategy} is normalised through {@link DumAemFetchStrategy#from(String)} so an
     * older export, or a hand-edited one, lands on a value the connector recognises instead of a
     * string that silently disables the delivery path.
     */
    @Mapping(target = "fetchStrategy",
            expression = "java(com.viglet.dumont.connector.aem.commons.bean.DumAemFetchStrategy"
                    + ".from(exchange.getFetchStrategy()).name())")
    @Mapping(target = "localePaths", ignore = true)
    @Mapping(target = "attributeSpecifications", ignore = true)
    @Mapping(target = "models", ignore = true)
    DumAemSource toEntity(DumAemSourceExchange exchange);
}

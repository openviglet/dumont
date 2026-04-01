/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
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

import com.viglet.dumont.connector.aem.commons.bean.DumAemAttrMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Fluent API for extracting AEM component data and mapping it to target attributes.
 * <p>
 * Provides a declarative way to find components by type, navigate into nested structures,
 * map fields to target attributes, and execute custom logic — all in a single chain.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Simple: find first component, map fields
 * query.component("my-app/components/news", MyNews.class)
 *     .first()
 *     .attr("date", MyNews::getDate)
 *     .attr("title", MyNews::getTitle)
 *     .into(attrValues);
 *
 * // Navigate into nested objects
 * query.component("my-app/components/teacher", MyTeacher.class)
 *     .first()
 *     .via(MyTeacher::getElements)
 *     .attr("name", Elements::getName)
 *     .attr("bio", Elements::getBio)
 *     .into(attrValues);
 *
 * // Mix declarative attrs with custom logic
 * query.component("my-app/components/event", MyEvent.class)
 *     .first()
 *     .attr("date", MyEvent::getDate)
 *     .also((event, attrs) -> {
 *         attrs.addWithSingleValue("address",
 *             "%s %s".formatted(event.getCity(), event.getState()), false);
 *     })
 *     .into(attrValues);
 *
 * // Process all components of a type
 * query.component("my-app/components/tag", MyTag.class)
 *     .all()
 *     .attr("tags", MyTag::getName, false)
 *     .into(attrValues);
 * }</pre>
 *
 * @param <T> the component bean type
 */
public class DumAemComponentMapper<T> {

    private final List<T> items;
    private final List<AttrMapping<T>> mappings = new ArrayList<>();
    private final List<BiConsumer<T, DumAemAttrMap>> customActions = new ArrayList<>();
    private boolean firstOnly = false;

    DumAemComponentMapper(List<T> items) {
        this.items = items;
    }

    /**
     * Only process the first matching component.
     */
    public DumAemComponentMapper<T> first() {
        this.firstOnly = true;
        return this;
    }

    /**
     * Process all matching components.
     */
    public DumAemComponentMapper<T> all() {
        this.firstOnly = false;
        return this;
    }

    /**
     * Maps a component field to a target attribute (override = true).
     *
     * @param name   the target attribute name
     * @param getter function to extract the value from the component bean
     */
    public DumAemComponentMapper<T> attr(String name, Function<T, ?> getter) {
        return attr(name, getter, true);
    }

    /**
     * Maps a component field to a target attribute.
     *
     * @param name     the target attribute name
     * @param getter   function to extract the value from the component bean
     * @param override whether this value should override existing values
     */
    public DumAemComponentMapper<T> attr(String name, Function<T, ?> getter, boolean override) {
        mappings.add(new AttrMapping<>(name, getter, override));
        return this;
    }

    /**
     * Adds custom processing logic alongside declarative attribute mappings.
     * <p>
     * Use this for complex logic that cannot be expressed with simple {@code attr()} calls,
     * such as conditional mappings, computed values, or fallback logic.
     *
     * @param action a consumer that receives the component bean and the target attribute map
     */
    public DumAemComponentMapper<T> also(BiConsumer<T, DumAemAttrMap> action) {
        customActions.add(action);
        return this;
    }

    /**
     * Navigates into a nested object within each component.
     * <p>
     * Returns a new mapper operating on the nested type. Null navigations are filtered out.
     *
     * @param navigator function to extract the nested object
     * @param <U>       the nested object type
     * @return a new mapper for the nested type
     */
    public <U> DumAemComponentMapper<U> via(Function<T, U> navigator) {
        List<U> navigated = items.stream()
                .filter(Objects::nonNull)
                .map(navigator)
                .filter(Objects::nonNull)
                .toList();
        DumAemComponentMapper<U> mapper = new DumAemComponentMapper<>(navigated);
        mapper.firstOnly = this.firstOnly;
        return mapper;
    }

    /**
     * Executes all accumulated attribute mappings and custom actions,
     * populating the target attribute map.
     */
    public void into(DumAemAttrMap attrValues) {
        Stream<T> stream = items.stream().filter(Objects::nonNull);
        if (firstOnly) {
            stream.findFirst().ifPresent(item -> applyAll(item, attrValues));
        } else {
            stream.forEach(item -> applyAll(item, attrValues));
        }
    }

    /**
     * Returns the first non-null component for custom processing
     * outside of the fluent chain.
     */
    public Optional<T> findFirst() {
        return items.stream().filter(Objects::nonNull).findFirst();
    }

    /**
     * Returns a stream of non-null components for custom processing.
     */
    public Stream<T> stream() {
        return items.stream().filter(Objects::nonNull);
    }

    private void applyAll(T item, DumAemAttrMap attrValues) {
        for (var mapping : mappings) {
            Object value = mapping.getter().apply(item);
            attrValues.addWithValue(mapping.name(), value, mapping.override());
        }
        for (var action : customActions) {
            action.accept(item, attrValues);
        }
    }

    private record AttrMapping<T>(String name, Function<T, ?> getter, boolean override) {
    }
}

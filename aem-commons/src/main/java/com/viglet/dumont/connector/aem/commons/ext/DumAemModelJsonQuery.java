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

import com.jayway.jsonpath.Criteria;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.Filter;
import com.jayway.jsonpath.JsonPath;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Utility class for querying AEM model.json content using JsonPath.
 * <p>
 * Encapsulates the common pattern of finding AEM components by their {@code :type}
 * and deserializing them into typed Java objects.
 * <p>
 * Example usage:
 * <pre>{@code
 * DumAemModelJsonQuery query = new DumAemModelJsonQuery(json);
 *
 * // Find all components of a type
 * List<MyBean> items = query.findByComponentType("my-app/components/banner", MyBean.class);
 *
 * // Find the first component of a type
 * query.findFirstByComponentType("my-app/components/news", MyNews.class)
 *     .ifPresent(news -> attrValues.addWithSingleValue("date", news.getDate(), true));
 * }</pre>
 */
public class DumAemModelJsonQuery {

    private static final String JSONPATH_ALL_FILTER = "$..[?]";
    private static final String TYPE_FIELD = ":type";
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private final DocumentContext jsonContext;

    public DumAemModelJsonQuery(String json) {
        this.jsonContext = JsonPath.parse(json);
    }

    /**
     * Finds all components matching the given AEM component type.
     *
     * @param componentType the AEM component resource type (e.g. "my-app/components/banner")
     * @param itemClass     the class to deserialize each matched component into
     * @param <T>           the target type
     * @return a mutable list of deserialized components (never null, may contain nulls filtered out)
     */
    public <T> List<T> findByComponentType(String componentType, Class<T> itemClass) {
        Object result = jsonContext.read(JSONPATH_ALL_FILTER,
                Filter.filter(Criteria.where(TYPE_FIELD).eq(componentType)));
        return MAPPER.convertValue(result,
                MAPPER.getTypeFactory().constructCollectionType(List.class, itemClass));
    }

    /**
     * Finds the first non-null component matching the given AEM component type.
     *
     * @param componentType the AEM component resource type
     * @param itemClass     the class to deserialize into
     * @param <T>           the target type
     * @return an Optional containing the first matched component, or empty
     */
    public <T> Optional<T> findFirstByComponentType(String componentType, Class<T> itemClass) {
        return findByComponentType(componentType, itemClass).stream()
                .filter(Objects::nonNull)
                .findFirst();
    }

    /**
     * Returns the underlying DocumentContext for advanced JsonPath queries.
     */
    public DocumentContext getJsonContext() {
        return jsonContext;
    }
}

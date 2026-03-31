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

import com.viglet.dumont.commons.utils.DumCommonsUtils;
import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.bean.DumAemTargetAttrValueMap;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.utils.DumAemCommonsUtils;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Abstract base class for AEM model.json content extractors.
 * <p>
 * Implements the Template Method pattern, handling the boilerplate of fetching,
 * parsing, and error handling for model.json requests. Subclasses only need to
 * implement two methods:
 * <ul>
 *   <li>{@link #getModelClass()} - the root model bean class</li>
 *   <li>{@link #extractAttributes(Object, DumAemModelJsonQuery, DumAemObject, DumAemTargetAttrValueMap)}
 *       - the business logic for attribute extraction</li>
 * </ul>
 * <p>
 * Example:
 * <pre>{@code
 * public class MyModelJsonExtractor extends DumAemExtModelJsonBase<MyModel> {
 *
 *     @Override
 *     protected Class<MyModel> getModelClass() {
 *         return MyModel.class;
 *     }
 *
 *     @Override
 *     protected void extractAttributes(MyModel model, DumAemModelJsonQuery query,
 *             DumAemObject aemObject, DumAemTargetAttrValueMap attrValues) {
 *         attrValues.addWithSingleValue("title", model.getTitle(), true);
 *
 *         query.findFirstByComponentType("my-app/components/news", MyNews.class)
 *             .ifPresent(news -> attrValues.addWithSingleValue("date", news.getDate(), true));
 *     }
 * }
 * }</pre>
 *
 * @param <T> the root model bean type for the model.json response
 */
@Slf4j
public abstract class DumAemExtModelJsonBase<T> implements DumAemExtContentInterface {

    private static final String MODEL_JSON_EXTENSION = ".model.json";
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    /**
     * Returns the class used to deserialize the root model.json response.
     */
    protected abstract Class<T> getModelClass();

    /**
     * Extracts attributes from the parsed model and populates the target attribute map.
     *
     * @param model      the deserialized root model object
     * @param query      a query helper for finding AEM components by type via JsonPath;
     *                   use this to extract data from nested components
     * @param aemObject  the AEM content object being indexed
     * @param attrValues the attribute map to populate with extracted values
     */
    protected abstract void extractAttributes(T model, DumAemModelJsonQuery query,
            DumAemObject aemObject, DumAemTargetAttrValueMap attrValues);

    @Override
    public final DumAemTargetAttrValueMap consume(DumAemObject aemObject,
            DumAemConfiguration configuration) {
        String url = configuration.getUrl() + aemObject.getPath() + MODEL_JSON_EXTENSION;
        try {
            return DumAemCommonsUtils.getResponseBody(url, configuration, false)
                    .filter(DumCommonsUtils::isValidJson)
                    .map(json -> {
                        T model = MAPPER.readValue(json, getModelClass());
                        if (model == null) {
                            return new DumAemTargetAttrValueMap();
                        }
                        DumAemModelJsonQuery query = new DumAemModelJsonQuery(json);
                        DumAemTargetAttrValueMap attrValues = new DumAemTargetAttrValueMap();
                        extractAttributes(model, query, aemObject, attrValues);
                        return attrValues;
                    })
                    .orElseGet(DumAemTargetAttrValueMap::new);
        } catch (Exception e) {
            log.error("Error processing model.json from: {}", url, e);
            return new DumAemTargetAttrValueMap();
        }
    }
}

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
import com.viglet.dumont.connector.aem.commons.bean.DumAemAttrMap;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEnv;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.utils.DumAemCommonsUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

/**
 * Abstract base class for AEM model.json content extractors.
 * <p>
 * Implements the Template Method pattern, handling the boilerplate of fetching,
 * parsing, and error handling for model.json requests. Subclasses only need to
 * implement two methods:
 * <ul>
 *   <li>{@link #getModelClass()} - the root model bean class</li>
 *   <li>{@link #extractAttributes(Object, DumAemModelJsonQuery, DumAemObject, DumAemAttrMap)}
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
 *             DumAemObject aemObject, DumAemAttrMap attrValues) {
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
            DumAemObject aemObject, DumAemAttrMap attrValues);

    /**
     * Returns a map of template name aliases for normalization.
     * <p>
     * Override this to map multiple AEM template names to a canonical name.
     * For example, mapping "contact", "sub-home" → "institutional".
     * <p>
     * The map keys are the original template names, values are the canonical names.
     * Template names not in the map are returned as-is by {@link #resolveTemplateName(String)}.
     *
     * @return alias map (empty by default)
     */
    protected Map<String, String> templateNameAliases() {
        return Map.of();
    }

    /**
     * Resolves a template name using the configured aliases.
     *
     * @param templateName the original template name from AEM
     * @return the canonical name if an alias exists, otherwise the original name
     */
    protected String resolveTemplateName(String templateName) {
        return templateNameAliases().getOrDefault(templateName, templateName);
    }

    /**
     * Extracts the last modified date from an AEM object, falling back to the creation date.
     *
     * @param aemObject the AEM content object
     * @return the last modified date, or creation date if not available
     */
    protected static Date lastModifiedDate(DumAemObject aemObject) {
        return Optional.ofNullable(aemObject.getLastModified())
                .map(Calendar::getTime)
                .orElseGet(() -> aemObject.getCreatedDate().getTime());
    }

    /**
     * Builds the model.json URL for the given AEM object, routing through the
     * publish URL prefix with a {@code ?t=<timestamp>} cache-buster when the
     * object is being indexed for the {@link DumAemEnv#PUBLISHING} environment
     * and a {@code publishURLPrefix} is configured. Otherwise falls back to the
     * author URL ({@code configuration.getUrl()}).
     */
    private String buildModelJsonUrl(DumAemObject aemObject,
            DumAemConfiguration configuration) {
        if (aemObject.getEnvironment() == DumAemEnv.PUBLISHING
                && StringUtils.isNotBlank(configuration.getPublishURLPrefix())) {
            return configuration.getPublishURLPrefix() + aemObject.getPath()
                    + MODEL_JSON_EXTENSION + "?t=" + System.currentTimeMillis();
        }
        return configuration.getUrl() + aemObject.getPath() + MODEL_JSON_EXTENSION;
    }

    @Override
    public final DumAemAttrMap consume(DumAemObject aemObject,
            DumAemConfiguration configuration) {
        String url = buildModelJsonUrl(aemObject, configuration);
        try {
            return DumAemCommonsUtils.getResponseBody(url, configuration, false)
                    .filter(DumCommonsUtils::isValidJson)
                    .map(json -> {
                        T model = MAPPER.readValue(json, getModelClass());
                        if (model == null) {
                            return new DumAemAttrMap();
                        }
                        DumAemModelJsonQuery query = new DumAemModelJsonQuery(json);
                        DumAemAttrMap attrValues = new DumAemAttrMap();
                        extractAttributes(model, query, aemObject, attrValues);
                        return attrValues;
                    })
                    .orElseGet(DumAemAttrMap::new);
        } catch (Exception e) {
            log.error("Error processing model.json from: {}", url, e);
            return new DumAemAttrMap();
        }
    }
}

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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Abstract base class for AEM model.json content extractors.
 * <p>
 * Implements the Template Method pattern, handling the boilerplate of fetching,
 * parsing, and error handling for model.json requests. Subclasses declare the
 * root model bean class via {@link #getModelClass()} and then choose <b>one</b>
 * of two extraction styles:
 * <ul>
 *   <li><b>Composed (preferred)</b> — register a list of small, independently
 *       unit-tested {@link DumAemComponentExtractor}s via
 *       {@link #componentExtractors()}; the base composes them per page.</li>
 *   <li><b>Monolithic (legacy)</b> — override
 *       {@link #extractAttributes(Object, DumAemModelJsonQuery, DumAemObject, DumAemAttrMap)}
 *       directly with inlined extraction logic.</li>
 * </ul>
 * The default {@code extractAttributes} runs every registered component
 * extractor in order, so a subclass that overrides only
 * {@link #componentExtractors()} needs no other code.
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
     * Reserved attribute under which {@link #consume} surfaces the resolved content type
     * (roadmap <strong>D25</strong>) to the structured-source adapter. The adapter reads it to pick
     * the record's per-type schema and bucket coverage, then strips it — it is never published.
     */
    public static final String CONTENT_TYPE_ATTR = "dum:contentType";

    /**
     * Returns the class used to deserialize the root model.json response.
     */
    protected abstract Class<T> getModelClass();

    /**
     * Returns the ordered list of component extractors composed for each page.
     * <p>
     * Override this to express extraction as a registry of small, single-concern
     * {@link DumAemComponentExtractor}s (banner, course, teacher, event, news,
     * content fragment…) rather than one monolithic method. Each extractor is
     * independently unit-testable. Empty by default.
     *
     * @return the component extractors to run, in order (never null)
     */
    protected List<DumAemComponentExtractor<T>> componentExtractors() {
        return List.of();
    }

    /**
     * Returns the content types this extension declares (roadmap <strong>D25</strong>).
     * <p>
     * Override this to replace "one source = one schema" with a content-type model: each entry binds
     * a canonical content type (the value a template resolves to via {@link #templateNameAliases()})
     * to its own {@link DumAemContentType#fields() schema} and its own
     * {@link DumAemContentType#extractors() extractor composition}. When non-empty,
     * {@link #extractAttributes} routes each page to the single composition of its
     * {@link #resolveContentType(Object, DumAemObject) resolved type}, falling back to the flat
     * {@link #componentExtractors()} when the type is unknown. Empty by default (no routing).
     *
     * @return the declared content types (never null)
     */
    protected List<DumAemContentType<T>> contentTypes() {
        return List.of();
    }

    /** The declared content types, for the structured-source adapter to build per-type schemas. */
    public final List<DumAemContentType<T>> declaredContentTypes() {
        return contentTypes();
    }

    /**
     * Returns the raw AEM template name of this page, used to route it to a content type
     * (roadmap <strong>D25</strong>). Override when {@link #contentTypes()} is declared — the value
     * lives in a model-specific field. Returns {@code null} by default (no routing).
     */
    protected String templateName(T model, DumAemObject aemObject) {
        return null;
    }

    /**
     * Resolves this page's canonical content type: the {@link #templateName(Object, DumAemObject) raw
     * template} collapsed through {@link #templateNameAliases() the alias map}. Returns {@code null}
     * when no template is available, so the caller falls back to the flat composition.
     */
    public final String resolveContentType(T model, DumAemObject aemObject) {
        String template = templateName(model, aemObject);
        return StringUtils.isBlank(template) ? null : resolveTemplateName(template);
    }

    /**
     * Extracts attributes from the parsed model and populates the target attribute map.
     * <p>
     * The default implementation routes by content type when {@link #contentTypes()} is declared —
     * running only the {@link DumAemContentType#extractors() composition} of the page's
     * {@link #resolveContentType(Object, DumAemObject) resolved type} — and otherwise composes every
     * extractor returned by {@link #componentExtractors()}, in order. Override this directly only for
     * legacy monolithic extraction; prefer registering component extractors / content types.
     *
     * @param model      the deserialized root model object
     * @param query      a query helper for finding AEM components by type via JsonPath;
     *                   use this to extract data from nested components
     * @param aemObject  the AEM content object being indexed
     * @param attrValues the attribute map to populate with extracted values
     */
    protected void extractAttributes(T model, DumAemModelJsonQuery query,
            DumAemObject aemObject, DumAemAttrMap attrValues) {
        for (DumAemComponentExtractor<T> extractor : resolveExtractors(model, aemObject)) {
            extractor.contribute(model, query, aemObject, attrValues);
        }
    }

    /**
     * The extractor composition to run for this page: the resolved content type's composition when
     * content types are declared and the type is known, otherwise the flat {@link #componentExtractors()}.
     */
    private List<DumAemComponentExtractor<T>> resolveExtractors(T model, DumAemObject aemObject) {
        List<DumAemContentType<T>> contentTypes = contentTypes();
        if (!contentTypes.isEmpty()) {
            String resolved = resolveContentType(model, aemObject);
            for (DumAemContentType<T> contentType : contentTypes) {
                if (contentType.name().equals(resolved)) {
                    return contentType.extractors();
                }
            }
        }
        return componentExtractors();
    }

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

    /**
     * When content types are declared (roadmap <strong>D25</strong>), surface the resolved type under
     * the reserved {@link #CONTENT_TYPE_ATTR} so the structured-source adapter can pick the record's
     * per-type schema and bucket coverage. A no-op for untyped extensions.
     */
    private void surfaceContentType(T model, DumAemObject aemObject, DumAemAttrMap attrValues) {
        if (contentTypes().isEmpty()) {
            return;
        }
        String resolved = resolveContentType(model, aemObject);
        if (StringUtils.isNotBlank(resolved)) {
            attrValues.set(CONTENT_TYPE_ATTR, resolved);
        }
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
                        surfaceContentType(model, aemObject, attrValues);
                        return attrValues;
                    })
                    .orElseGet(DumAemAttrMap::new);
        } catch (Exception e) {
            log.error("Error processing model.json from: {}", url, e);
            return new DumAemAttrMap();
        }
    }
}

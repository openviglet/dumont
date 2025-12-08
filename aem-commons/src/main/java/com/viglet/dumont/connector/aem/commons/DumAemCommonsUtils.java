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

package com.viglet.dumont.connector.aem.commons;

import static com.viglet.dumont.connector.aem.commons.DumAemConstants.JCR;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.JCR_CONTENT;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.JSON;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.ONCE;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.SLING;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.TEXT;
import static org.apache.jackrabbit.JcrConstants.JCR_TITLE;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicHeader;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.Lists;
import com.google.common.net.UrlEscapers;
import com.viglet.dumont.commons.cache.DumCustomClassCache;
import com.viglet.dumont.commons.utils.DumCommonsUtils;
import com.viglet.dumont.connector.aem.commons.bean.DumAemContext;
import com.viglet.dumont.connector.aem.commons.bean.DumAemTargetAttrValueMap;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.context.DumAemLocalePathContext;
import com.viglet.dumont.connector.aem.commons.ext.DumAemExtContentInterface;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemModel;
import com.viglet.turing.client.sn.job.TurSNAttributeSpec;
import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DumAemCommonsUtils {

    private static final Cache<String, Optional<String>> responseBodyCache = Caffeine.newBuilder().maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(5)).build();

    private DumAemCommonsUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static Set<String> getDependencies(JSONObject infinityJson) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root;
        try {
            root = mapper.readTree(infinityJson.toString());
            return getContentValues(root);

        } catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);
        }
        return Collections.emptySet();
    }

    private static Set<String> getContentValues(JsonNode node) {
        Set<String> results = new HashSet<>();
        extractContentValues(node, results);
        return results;
    }

    private static void extractContentValues(JsonNode node, Set<String> results) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                extractContentValues(entry.getValue(), results);
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                extractContentValues(item, results);
            }
        } else if (node.isTextual()) {
            String value = node.asText();
            if (value.startsWith("/content")) {
                results.add(value);
            }
        }
    }

    public static boolean isTypeEqualContentType(DumAemObjectGeneric dumAemObject,
            DumAemConfiguration dumAemSourceContext) {
        return dumAemObject.getType().equals(dumAemSourceContext.getContentType());
    }

    public static Optional<String> getSiteName(DumAemConfiguration dumAemSourceContext,
            JSONObject jsonObject) {
        return getSiteName(jsonObject).map(Optional::of).orElseGet(() -> {
            log.error("No site name the {} root path ({})", dumAemSourceContext.getRootPath(),
                    dumAemSourceContext.getId());
            return Optional.empty();
        });
    }

    public static boolean usingContentTypeParameter(DumAemConfiguration dumAemSourceContext) {
        return StringUtils.isNotBlank(dumAemSourceContext.getContentType());
    }

    public static boolean isNotOnceConfig(String path, DumAemConfiguration config) {
        if (StringUtils.isNotBlank(config.getOncePattern())) {
            Pattern p = Pattern.compile(config.getOncePattern());
            Matcher m = p.matcher(path);
            return !m.lookingAt();
        }
        return true;
    }

    public static String configOnce(DumAemConfiguration dumAemSourceContext) {
        return "%s/%s".formatted(dumAemSourceContext.getId(), ONCE);
    }

    public static DumAemTargetAttrValueMap runCustomClassFromContentType(DumAemModel dumAemModel,
            DumAemObject aemObject, DumAemConfiguration dumAemSourceContext) {
        return StringUtils.isNotEmpty(dumAemModel.getClassName())
                ? DumCustomClassCache.getCustomClassMap(dumAemModel.getClassName())
                        .map(customClassMap -> ((DumAemExtContentInterface) customClassMap)
                                .consume(aemObject, dumAemSourceContext))
                        .orElseGet(DumAemTargetAttrValueMap::new)
                : new DumAemTargetAttrValueMap();
    }

    public static void addFirstItemToAttribute(String attributeName, String attributeValue,
            Map<String, Object> attributes) {
        attributes.put(attributeName, attributeValue);
    }

    @NotNull
    public static Date getDeltaDate(DumAemObjectGeneric aemObject) {
        if (aemObject.getLastModified() != null)
            return aemObject.getLastModified().getTime();
        if (aemObject.getCreatedDate() != null)
            return aemObject.getCreatedDate().getTime();
        return new Date();
    }

    public static List<TurSNAttributeSpec> getDefinitionFromModel(
            List<TurSNAttributeSpec> dumSNAttributeSpecList, Map<String, Object> targetAttrMap) {
        List<TurSNAttributeSpec> dumSNAttributeSpecFromModelList = new ArrayList<>();
        targetAttrMap.forEach((key, value) -> dumSNAttributeSpecList.stream()
                .filter(dumSNAttributeSpec -> dumSNAttributeSpec.getName() != null
                        && dumSNAttributeSpec.getName().equals(key))
                .findFirst().ifPresent(dumSNAttributeSpecFromModelList::add));
        return dumSNAttributeSpecFromModelList;
    }

    public static Optional<String> getSiteName(JSONObject jsonSite) {
        if (jsonSite.has(JCR_CONTENT) && jsonSite.getJSONObject(JCR_CONTENT).has(JCR_TITLE)) {
            return jsonSite.getJSONObject(JCR_CONTENT).getString(JCR_TITLE).describeConstable();
        }
        return Optional.empty();
    }

    public static boolean checkIfFileHasNotImageExtension(String s) {
        String[] imageExtensions = { ".jpg", ".png", ".jpeg", ".svg", ".webp" };
        return Arrays.stream(imageExtensions).noneMatch(suffix -> s.toLowerCase().endsWith(suffix));
    }

    public static void addItemInExistingAttribute(String attributeValue,
            Map<String, Object> attributes, String attributeName) {
        if (attributes.get(attributeName) instanceof ArrayList) {
            addItemToArray(attributes, attributeName, attributeValue);
        } else {
            convertAttributeSingleValueToArray(attributes, attributeName, attributeValue);
        }
    }

    private static void convertAttributeSingleValueToArray(Map<String, Object> attributes,
            String attributeName, String attributeValue) {
        attributes.put(attributeName,
                Lists.newArrayList(attributes.get(attributeName), attributeValue));
    }

    private static void addItemToArray(Map<String, Object> attributes, String attributeName,
            String attributeValue) {
        List<String> attributeValues = new ArrayList<>(((List<?>) attributes.get(attributeName))
                .stream().map(String.class::cast).toList());
        attributeValues.add(attributeValue);
        attributes.put(attributeName, attributeValues);

    }

    @NotNull
    public static List<TurSNJobAttributeSpec> castSpecToJobSpec(
            List<TurSNAttributeSpec> dumSNAttributeSpecList) {
        return dumSNAttributeSpecList.stream().filter(Objects::nonNull)
                .map(TurSNJobAttributeSpec.class::cast).toList();
    }

    public static Locale getLocaleByPath(DumAemConfiguration dumAemSourceContext, String path) {
        for (DumAemLocalePathContext dumAemSourceLocalePath : dumAemSourceContext
                .getLocalePaths()) {
            if (hasPath(dumAemSourceLocalePath, path)) {
                return dumAemSourceLocalePath.getLocale();
            }
        }
        return dumAemSourceContext.getDefaultLocale();
    }

    private static boolean hasPath(DumAemLocalePathContext dumAemSourceLocalePath, String path) {
        return path.startsWith(dumAemSourceLocalePath.getPath());
    }

    public static Locale getLocaleFromAemObject(DumAemConfiguration dumAemConfiguration,
            DumAemObjectGeneric aemObject) {
        return getLocaleByPath(dumAemConfiguration, aemObject.getPath());
    }

    public static Optional<JSONObject> getInfinityJson(String url,
            DumAemConfiguration dumAemConfiguration, boolean useCache) {
        String infinityJsonUrl = String.format(url.endsWith(JSON) ? "%s%s" : "%s%s.infinity.json",
                dumAemConfiguration.getUrl(), url);
        try {
            return getResponseBody(infinityJsonUrl, dumAemConfiguration, useCache)
                    .<Optional<JSONObject>>map(responseBody -> {
                        if (isResponseBodyJSONArray(responseBody) && !url.endsWith(JSON)) {
                            try {
                                return getInfinityJson(
                                        new JSONArray(responseBody).toList().getFirst().toString(),
                                        dumAemConfiguration, useCache);
                            } catch (JSONException e) {
                                log.error(e.getMessage(), e);
                                return getInfinityJsonNotFound(infinityJsonUrl);
                            }
                        } else if (isResponseBodyJSONObject(responseBody)) {
                            return Optional.of(new JSONObject(responseBody));
                        }
                        return getInfinityJsonNotFound(infinityJsonUrl);
                    }).orElseGet(() -> getInfinityJsonNotFound(infinityJsonUrl));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return getInfinityJsonNotFound(infinityJsonUrl);
        }

    }

    private static Optional<JSONObject> getInfinityJsonNotFound(String infinityJsonUrl) {
        log.warn("Request Not Found {}", infinityJsonUrl);
        return Optional.empty();
    }

    public static boolean hasProperty(JSONObject jsonObject, String property) {
        return jsonObject.has(property) && jsonObject.get(property) != null;
    }

    public static String getPropertyValue(Object property) {
        try {
            if (property instanceof JSONArray propertyArray) {
                return !propertyArray.isEmpty() ? propertyArray.get(0).toString() : "";
            } else if (property != null) {
                return property.toString();
            }
        } catch (IllegalStateException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    public static boolean isResponseBodyJSONArray(String responseBody) {
        return responseBody.startsWith("[");
    }

    public static boolean isResponseBodyJSONObject(String responseBody) {
        return responseBody.startsWith("{");
    }

    public static <T> Optional<T> getResponseBody(String url,
            DumAemConfiguration dumAemSourceContext, Class<T> clazz, boolean useCache) throws IOException {
        return getResponseBody(url, dumAemSourceContext, useCache).flatMap(json -> {
            if (!DumCommonsUtils.isValidJson(json)) {
                return Optional.empty();
            }
            try {
                return Optional.ofNullable(new ObjectMapper()
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                        .readValue(json, clazz));
            } catch (JsonProcessingException e) {
                log.error("URL {} - {}", url, e.getMessage(), e);
            }
            return Optional.empty();
        });
    }

    public static @NotNull Optional<String> getResponseBody(String url,
            DumAemConfiguration dumAemSourceContext, boolean useCache) throws IOException {
        if (useCache) {
            return fetchResponseBodyCached(url, dumAemSourceContext);
        } else {
            return fetchResponseBodyWithoutCache(url, dumAemSourceContext);
        }
    }

    public static @NotNull Optional<String> fetchResponseBodyWithoutCache(String url,
            DumAemConfiguration dumAemSourceContext) throws IOException {
        String escapedUrl = UrlEscapers.urlFragmentEscaper().escape(url);
        URI normalizedUri = URI.create(Objects.requireNonNull(escapedUrl, "URL cannot be null")).normalize();

        try (CloseableHttpClient httpClient = createHttpClient(dumAemSourceContext)) {
            HttpGet request = new HttpGet(normalizedUri);
            String json = executeRequest(httpClient, request, url);

            if (isValidJsonResponse(json, url)) {
                return Optional.of(json);
            }
            log.warn("Invalid JSON response from URL: {}", url);
            return Optional.empty();
        } catch (IOException e) {
            log.error("Failed to fetch response from URL: {} - {}", url, e.getMessage(), e);
            throw e;
        }
    }

    private static CloseableHttpClient createHttpClient(DumAemConfiguration dumAemSourceContext) {
        String authHeader = basicAuth(dumAemSourceContext.getUsername(), dumAemSourceContext.getPassword());
        return HttpClientBuilder.create()
                .setDefaultHeaders(List.of(new BasicHeader(HttpHeaders.AUTHORIZATION, authHeader)))
                .build();
    }

    private static String executeRequest(CloseableHttpClient httpClient, HttpGet request, String url)
            throws IOException {
        return httpClient.execute(request, response -> {
            int statusCode = response.getCode();
            log.debug("HTTP {} - {}", statusCode, url);

            HttpEntity entity = response.getEntity();
            if (entity == null) {
                log.warn("Empty response entity from URL: {}", url);
                return null;
            }
            return EntityUtils.toString(entity);
        });
    }

    private static boolean isValidJsonResponse(String json, String url) {
        if (DumCommonsUtils.isValidJson(json)) {
            log.debug("Valid JSON response - {}", url);
            return true;
        }
        return false;
    }

    public static @NotNull Optional<String> fetchResponseBodyCached(String url,
            DumAemConfiguration dumAemSourceContext) {
        if (responseBodyCache.asMap().containsKey(url))
            log.debug("Using Cache to request {}", url);
        else
            log.debug("Creating Cache to request {}", url);
        String cacheKey = url;
        return responseBodyCache.get(cacheKey,
                k -> {
                    try {
                        return fetchResponseBodyWithoutCache(url, dumAemSourceContext);
                    } catch (IOException e) {
                        log.error(e.getMessage(), e);
                        return Optional.empty();
                    }
                });
    }

    private static String basicAuth(String username, String password) {
        return "Basic "
                + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }

    public static String getJsonNodeToComponent(JSONObject jsonObject) {
        StringBuilder components = new StringBuilder();
        if (jsonObject.has(TEXT) && jsonObject.get(TEXT) instanceof String text) {
            components.append(text);
        }
        jsonObject.toMap().forEach((key, value) -> {
            if (!key.startsWith(JCR) && !key.startsWith(SLING)
                    && (jsonObject.get(key) instanceof JSONObject jsonObjectNode)) {
                components.append(getJsonNodeToComponent(jsonObjectNode));
            }
        });
        return components.toString();
    }

    public static Locale getLocaleFromContext(DumAemConfiguration dumAemSourceContext,
            DumAemContext context) {
        return getLocaleFromAemObject(dumAemSourceContext, context.getCmsObjectInstance());
    }
}

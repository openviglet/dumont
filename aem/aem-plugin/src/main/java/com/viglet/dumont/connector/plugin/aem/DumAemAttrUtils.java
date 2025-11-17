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

package com.viglet.dumont.connector.plugin.aem;

import static com.viglet.dumont.connector.aem.commons.DumAemConstants.DEFAULT;
import static com.viglet.turing.commons.se.field.TurSEFieldType.STRING;
import static org.apache.jackrabbit.JcrConstants.JCR_TITLE;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.collections4.KeyValue;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import com.viglet.dumont.commons.utils.DumCommonsUtils;
import com.viglet.dumont.connector.aem.commons.DumAemCommonsUtils;
import com.viglet.dumont.connector.aem.commons.DumAemObjectGeneric;
import com.viglet.dumont.connector.aem.commons.bean.DumAemContext;
import com.viglet.dumont.connector.aem.commons.bean.DumAemTargetAttrValueMap;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemSourceAttr;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemTargetAttr;
import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.client.sn.job.TurSNAttributeSpec;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DumAemAttrUtils {
    public static final String CQ_TAGS_PATH = "/content/_cq_tags";

    public static boolean hasCustomClass(DumAemTargetAttr targetAttr) {
        return targetAttr.getSourceAttrs() == null
                && StringUtils.isNotBlank(targetAttr.getClassName());
    }

    public static boolean hasTextValue(DumAemTargetAttr dumAemTargetAttr) {
        return StringUtils.isNotEmpty(dumAemTargetAttr.getTextValue());
    }

    @Nullable
    public static Object getJcrProperty(DumAemContext context, String sourceAttrName) {
        return Optional.ofNullable(sourceAttrName).map(attrName -> {
            DumAemObjectGeneric aemObject = context.getCmsObjectInstance();
            if (isValidNode(attrName, aemObject)) {
                return aemObject.getJcrContentNode().get(attrName);
            } else if (aemObject.getAttributes().containsKey(attrName))
                return aemObject.getAttributes().get(attrName);
            return null;
        }).orElse(null);
    }

    public static boolean isValidNode(String attrName, DumAemObjectGeneric aemObject) {
        return aemObject.getJcrContentNode() != null && aemObject.getJcrContentNode().has(attrName);
    }

    @NotNull
    public static TurSNAttributeSpec getTurSNAttributeSpec(String facet,
            Map<String, String> facetLabel) {
        return TurSNAttributeSpec.builder().name(facet).description(facetLabel.get(DEFAULT))
                .facetName(facetLabel).facet(true).mandatory(false).type(STRING).multiValued(true)
                .build();
    }

    public static Map<String, String> getTagLabels(JSONObject tagJson) {
        Map<String, String> labels = new HashMap<>();
        if (tagJson.has(JCR_TITLE))
            labels.put(DEFAULT, tagJson.getString(JCR_TITLE));
        Iterator<String> keys = tagJson.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String titleStartWith = JCR_TITLE + ".";
            if (key.startsWith(titleStartWith)) {
                String locale = normalizeLocale(key.replaceAll(titleStartWith, ""));
                labels.put(locale, tagJson.getString(key));
            }
        }
        return labels;
    }

    public static String normalizeLocale(String locale) {
        String[] parts = locale.split("_");
        if (parts.length == 2)
            return "%s_%s".formatted(parts[0].toLowerCase(), parts[1].toUpperCase());
        return locale;
    }

    public static DumAemTargetAttrValueMap addValuesToAttributes(DumAemTargetAttr dumAemTargetAttr,
            DumAemSourceAttr dumAemSourceAttr, Object jcrProperty) {

        if (dumAemSourceAttr.isConvertHtmlToText()) {
            return DumAemTargetAttrValueMap.singleItem(dumAemTargetAttr.getName(),
                    DumCommonsUtils.html2Text(DumAemCommonsUtils.getPropertyValue(jcrProperty)),
                    false);
        } else if (jcrProperty != null) {
            TurMultiValue turMultiValue = new TurMultiValue();
            if (isJSONArray(jcrProperty)) {
                ((JSONArray) jcrProperty).forEach(item -> turMultiValue.add(item.toString()));
            } else {
                turMultiValue.add(DumAemCommonsUtils.getPropertyValue(jcrProperty));
            }
            if (!turMultiValue.isEmpty()) {
                return DumAemTargetAttrValueMap.singleItem(dumAemTargetAttr.getName(),
                        turMultiValue, false);
            }
        }
        return new DumAemTargetAttrValueMap();
    }

    private static boolean isJSONArray(Object jcrProperty) {
        return jcrProperty instanceof JSONArray jsonArray && !jsonArray.isEmpty();
    }

    public static boolean hasCustomClass(DumAemContext context) {
        return StringUtils.isNotBlank(context.getDumAemSourceAttr().getClassName());
    }

    public static boolean hasJcrPropertyValue(Object jcrProperty) {
        return ObjectUtils.allNotNull(jcrProperty,
                DumAemCommonsUtils.getPropertyValue(jcrProperty));
    }

    public static DumAemTargetAttrValueMap getDumAttrDefUnique(DumAemTargetAttr dumAemTargetAttr,
            DumAemTargetAttrValueMap dumAemTargetAttrValueMap) {
        return DumAemTargetAttrValueMap
                .singleItem(
                        dumAemTargetAttr.getName(), dumAemTargetAttrValueMap
                                .get(dumAemTargetAttr.getName()).stream().distinct().toList(),
                        false);
    }

    public static TurSNAttributeSpec setTagFacet(DumAemConfiguration dumAemSourceContext,
            String facetId) {
        return DumAemCommonsUtils
                .getInfinityJson((CQ_TAGS_PATH + "/%s").formatted(facetId), dumAemSourceContext,
                        true)
                .map(infinityJson -> getTurSNAttributeSpec(facetId, getTagLabels(infinityJson)))
                .orElse(new TurSNAttributeSpec());
    }

    public static String addTagToAttrValueList(DumAemContext context,
            DumAemConfiguration dumAemSourceContext, String facet, String value) {
        return DumAemCommonsUtils.getInfinityJson((CQ_TAGS_PATH + "/%s/%s").formatted(facet, value),
                dumAemSourceContext, true).map(infinityJson -> {
                    Locale locale = DumAemCommonsUtils.getLocaleFromContext(dumAemSourceContext, context);
                    String titleLocale = locale.toString().toLowerCase();
                    String titleLanguage = locale.getLanguage().toLowerCase();
                    Map<String, String> tagLabels = getTagLabels(infinityJson);
                    if (tagLabels.containsKey(titleLocale))
                        return tagLabels.get(titleLocale);
                    else if (tagLabels.containsKey(titleLanguage))
                        return tagLabels.get(titleLanguage);
                    else
                        return tagLabels.getOrDefault(DEFAULT, value);
                }).orElse(value);
    }

    public static @NotNull DumAemTargetAttrValueMap getTextValue(DumAemContext context) {
        return DumAemTargetAttrValueMap.singleItem(context.getDumAemTargetAttr(), false);
    }

    public static void processTagsFromSourceAttr(DumAemContext context,
            DumAemConfiguration dumAemSourceContext,
            List<TurSNAttributeSpec> dumSNAttributeSpecList, String attributeName,
            DumAemTargetAttrValueMap dumAemTargetAttrValueMap) {
        Optional.ofNullable((JSONArray) getJcrProperty(context, attributeName)).ifPresent(
                property -> property.forEach(tag -> formatTags(context, dumAemSourceContext,
                        dumSNAttributeSpecList, tag.toString(), dumAemTargetAttrValueMap)));
    }

    public static void processTagsFromTargetAttr(DumAemContext context,
            DumAemConfiguration dumAemSourceContext,
            List<TurSNAttributeSpec> dumSNAttributeSpecList,
            DumAemTargetAttrValueMap dumAemTargetAttrValueMapFromClass, String targetName,
            DumAemTargetAttrValueMap dumAemTargetAttrValueMap) {
        dumAemTargetAttrValueMapFromClass.get(targetName)
                .forEach(tag -> formatTags(context,
                        dumAemSourceContext, dumSNAttributeSpecList, tag,
                        dumAemTargetAttrValueMap));
    }

    public static void formatTags(DumAemContext context, DumAemConfiguration dumAemSourceContext,
            List<TurSNAttributeSpec> dumSNAttributeSpecList, String tag,
            DumAemTargetAttrValueMap dumAemTargetAttrValueMap) {
        DumCommonsUtils.getKeyValueFromColon(tag)
                .ifPresent(
                        kv -> handleTagFacet(context, dumAemSourceContext, dumSNAttributeSpecList,
                                dumAemTargetAttrValueMap, kv));
    }

    private static void handleTagFacet(DumAemContext context,
            DumAemConfiguration dumAemSourceContext,
            List<TurSNAttributeSpec> dumSNAttributeSpecList,
            DumAemTargetAttrValueMap dumAemTargetAttrValueMap,
            KeyValue<String, String> kv) {
        Optional.ofNullable(kv.getKey()).ifPresent(facet -> {
            processTagFacet(context, dumAemSourceContext, dumSNAttributeSpecList,
                    dumAemTargetAttrValueMap, kv, facet);
        });
    }

    private static void processTagFacet(DumAemContext context,
            DumAemConfiguration dumAemSourceContext,
            List<TurSNAttributeSpec> dumSNAttributeSpecList,
            DumAemTargetAttrValueMap dumAemTargetAttrValueMap,
            KeyValue<String, String> kv,
            String facet) {
        dumSNAttributeSpecList.add(setTagFacet(dumAemSourceContext, facet));
        Optional.ofNullable(kv.getValue())
                .ifPresent(value -> dumAemTargetAttrValueMap
                        .addWithSingleValue(facet, addTagToAttrValueList(context,
                                dumAemSourceContext, facet, value),
                                false));
    }
}

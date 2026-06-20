/*
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

package com.viglet.dumont.connector.plugin.aem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.bean.DumAemAttrMap;
import com.viglet.dumont.connector.aem.commons.bean.DumAemContext;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEnv;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemSourceAttr;
import com.viglet.dumont.connector.aem.commons.utils.DumAemCommonsUtils;
import com.viglet.dumont.connector.plugin.aem.utils.DumAemAttrUtils;
import com.viglet.turing.client.sn.job.TurSNAttributeSpec;

/**
 * Covers the cq:tag-facet resolution helpers of {@link DumAemAttrUtils} that fan out to AEM over
 * HTTP. The {@code infinity.json} fetch ({@link DumAemCommonsUtils#getInfinityJson}) is stubbed via
 * {@code mockStatic} with {@code CALLS_REAL_METHODS} so the surrounding label/locale logic runs for
 * real.
 */
class DumAemAttrUtilsTagFacetTest {

    private static DumAemConfiguration config() {
        return DumAemConfiguration.builder().url("http://aem.invalid:4502")
                .defaultLocale(Locale.US).localePaths(new HashSet<>()).build();
    }

    private static DumAemContext contextWithTag(String tag) {
        JSONArray tags = new JSONArray();
        tags.put(tag);
        JSONObject jcrContent = new JSONObject();
        jcrContent.put("cq:tags", tags);
        JSONObject jcrNode = new JSONObject();
        jcrNode.put("jcr:primaryType", "cq:Page");
        jcrNode.put("jcr:content", jcrContent);
        return new DumAemContext(new DumAemObject("/content/test", jcrNode, DumAemEnv.AUTHOR));
    }

    private static JSONObject tagJson() {
        JSONObject tagJson = new JSONObject();
        tagJson.put("jcr:title", "Default Label");
        tagJson.put("jcr:title.en", "English Label");
        return tagJson;
    }

    @Test
    void setTagFacetBuildsSpecFromInfinityJson() {
        try (MockedStatic<DumAemCommonsUtils> mocked =
                mockStatic(DumAemCommonsUtils.class, CALLS_REAL_METHODS)) {
            mocked.when(() -> DumAemCommonsUtils.getInfinityJson(anyString(), any(), eq(true)))
                    .thenReturn(Optional.of(tagJson()));

            TurSNAttributeSpec spec = DumAemAttrUtils.setTagFacet(config(), "country");

            assertThat(spec.getName()).isEqualTo("country");
            assertThat(spec.getDescription()).isEqualTo("Default Label");
        }
    }

    @Test
    void setTagFacetReturnsEmptySpecWhenNotFound() {
        try (MockedStatic<DumAemCommonsUtils> mocked =
                mockStatic(DumAemCommonsUtils.class, CALLS_REAL_METHODS)) {
            mocked.when(() -> DumAemCommonsUtils.getInfinityJson(anyString(), any(), eq(true)))
                    .thenReturn(Optional.empty());

            assertThat(DumAemAttrUtils.setTagFacet(config(), "country").getName()).isNull();
        }
    }

    @Test
    void addTagToAttrValueListResolvesLanguageLabel() {
        DumAemContext context = contextWithTag("country:us");
        try (MockedStatic<DumAemCommonsUtils> mocked =
                mockStatic(DumAemCommonsUtils.class, CALLS_REAL_METHODS)) {
            mocked.when(() -> DumAemCommonsUtils.getInfinityJson(anyString(), any(), eq(true)))
                    .thenReturn(Optional.of(tagJson()));

            String label = DumAemAttrUtils.addTagToAttrValueList(context, config(), "country", "us");

            // locale US -> language "en" matches jcr:title.en.
            assertThat(label).isEqualTo("English Label");
        }
    }

    @Test
    void addTagToAttrValueListFallsBackToRawValueWhenNotFound() {
        DumAemContext context = contextWithTag("country:us");
        try (MockedStatic<DumAemCommonsUtils> mocked =
                mockStatic(DumAemCommonsUtils.class, CALLS_REAL_METHODS)) {
            mocked.when(() -> DumAemCommonsUtils.getInfinityJson(anyString(), any(), eq(true)))
                    .thenReturn(Optional.empty());

            assertThat(DumAemAttrUtils.addTagToAttrValueList(context, config(), "country", "us"))
                    .isEqualTo("us");
        }
    }

    @Test
    void processTagsFromSourceAttrAppendsFacetAndValue() {
        DumAemContext context = contextWithTag("country:us");
        List<TurSNAttributeSpec> specs = new ArrayList<>();
        DumAemAttrMap valueMap = new DumAemAttrMap();

        try (MockedStatic<DumAemCommonsUtils> mocked =
                mockStatic(DumAemCommonsUtils.class, CALLS_REAL_METHODS)) {
            mocked.when(() -> DumAemCommonsUtils.getInfinityJson(anyString(), any(), eq(true)))
                    .thenReturn(Optional.of(tagJson()));

            DumAemAttrUtils.processTagsFromSourceAttr(context, config(), specs, "cq:tags", valueMap);

            assertThat(specs).extracting(TurSNAttributeSpec::getName).containsExactly("country");
            assertThat(valueMap.get("country")).containsExactly("English Label");
        }
    }

    @Test
    void processTagsFromTargetAttrAppendsFacetAndValue() {
        DumAemContext context = contextWithTag("country:us");
        List<TurSNAttributeSpec> specs = new ArrayList<>();
        DumAemAttrMap fromClass = new DumAemAttrMap();
        fromClass.append("target", "country:us");
        DumAemAttrMap valueMap = new DumAemAttrMap();

        try (MockedStatic<DumAemCommonsUtils> mocked =
                mockStatic(DumAemCommonsUtils.class, CALLS_REAL_METHODS)) {
            mocked.when(() -> DumAemCommonsUtils.getInfinityJson(anyString(), any(), eq(true)))
                    .thenReturn(Optional.of(tagJson()));

            DumAemAttrUtils.processTagsFromTargetAttr(context, config(), specs, fromClass, "target",
                    valueMap);

            assertThat(specs).extracting(TurSNAttributeSpec::getName).containsExactly("country");
            assertThat(valueMap.get("country")).containsExactly("English Label");
        }
    }

    @Test
    void getJcrPropertyFallsBackToAttributesThenNull() {
        JSONObject jcrNode = new JSONObject();
        jcrNode.put("jcr:primaryType", "cq:Page");
        DumAemObject aemObject = new DumAemObject("/content/test", jcrNode, DumAemEnv.AUTHOR);
        aemObject.getAttributes().put("extra", "value");
        DumAemContext context = new DumAemContext(aemObject);

        assertThat(DumAemAttrUtils.getJcrProperty(context, "extra")).isEqualTo("value");
        assertThat(DumAemAttrUtils.getJcrProperty(context, "missing")).isNull();
        assertThat(DumAemAttrUtils.getJcrProperty(context, null)).isNull();
    }
}

/*
 * Copyright (C) 2016-2025 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.plugin.aem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.DumAemObjectGeneric;
import com.viglet.dumont.connector.aem.commons.bean.DumAemContext;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEnv;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEvent;
import com.viglet.dumont.connector.aem.commons.bean.DumAemTargetAttrValueMap;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemSourceAttr;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemTargetAttr;
import com.viglet.dumont.connector.plugin.aem.utils.DumAemAttrUtils;
import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.client.sn.job.TurSNAttributeSpec;

@DisplayName("DumAemAttrUtils Tests")
class DumAemAttrUtilsTest {

    @Nested
    @DisplayName("hasCustomClass Tests")
    class HasCustomClassTests {

        @Test
        @DisplayName("Should return true when sourceAttrs is null and className is not blank")
        void shouldReturnTrueWhenSourceAttrsNullAndClassNameNotBlank() {
            DumAemTargetAttr targetAttr = DumAemTargetAttr.builder()
                    .name("title")
                    .className("com.example.CustomHandler")
                    .build();

            assertTrue(DumAemAttrUtils.hasCustomClass(targetAttr));
        }

        @Test
        @DisplayName("Should return false when sourceAttrs is not null")
        void shouldReturnFalseWhenSourceAttrsNotNull() {
            DumAemTargetAttr targetAttr = DumAemTargetAttr.builder()
                    .name("title")
                    .className("com.example.CustomHandler")
                    .sourceAttrs(java.util.Arrays.asList(DumAemSourceAttr.builder().name("jcr:title").build()))
                    .build();

            assertFalse(DumAemAttrUtils.hasCustomClass(targetAttr));
        }

        @Test
        @DisplayName("Should return false when className is blank")
        void shouldReturnFalseWhenClassNameBlank() {
            DumAemTargetAttr targetAttr = DumAemTargetAttr.builder()
                    .name("title")
                    .className("")
                    .build();

            assertFalse(DumAemAttrUtils.hasCustomClass(targetAttr));
        }

        @Test
        @DisplayName("Should return false when className is null")
        void shouldReturnFalseWhenClassNameNull() {
            DumAemTargetAttr targetAttr = DumAemTargetAttr.builder()
                    .name("title")
                    .build();

            assertFalse(DumAemAttrUtils.hasCustomClass(targetAttr));
        }
    }

    @Nested
    @DisplayName("hasTextValue Tests")
    class HasTextValueTests {

        @Test
        @DisplayName("Should return true when textValue is not empty")
        void shouldReturnTrueWhenTextValueNotEmpty() {
            DumAemTargetAttr targetAttr = DumAemTargetAttr.builder()
                    .name("title")
                    .textValue("Static Text")
                    .build();

            assertTrue(DumAemAttrUtils.hasTextValue(targetAttr));
        }

        @Test
        @DisplayName("Should return false when textValue is empty")
        void shouldReturnFalseWhenTextValueEmpty() {
            DumAemTargetAttr targetAttr = DumAemTargetAttr.builder()
                    .name("title")
                    .textValue("")
                    .build();

            assertFalse(DumAemAttrUtils.hasTextValue(targetAttr));
        }

        @Test
        @DisplayName("Should return false when textValue is null")
        void shouldReturnFalseWhenTextValueNull() {
            DumAemTargetAttr targetAttr = DumAemTargetAttr.builder()
                    .name("title")
                    .build();

            assertFalse(DumAemAttrUtils.hasTextValue(targetAttr));
        }
    }

    @Nested
    @DisplayName("isValidNode Tests")
    class IsValidNodeTests {

        @Test
        @DisplayName("Should return true when jcrContentNode has the attribute")
        void shouldReturnTrueWhenJcrContentNodeHasAttribute() {
            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "cq:Page");

            JSONObject jcrContent = new JSONObject();
            jcrContent.put("jcr:title", "Test Title");
            jcrNode.put("jcr:content", jcrContent);

            DumAemObjectGeneric aemObject = new DumAemObjectGeneric("/content/test", jcrNode, DumAemEvent.NONE);

            assertTrue(DumAemAttrUtils.isValidNode("jcr:title", aemObject));
        }

        @Test
        @DisplayName("Should return false when jcrContentNode is null")
        void shouldReturnFalseWhenJcrContentNodeIsNull() {
            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "nt:unstructured");

            DumAemObjectGeneric aemObject = new DumAemObjectGeneric("/content/test", jcrNode, DumAemEvent.NONE);

            assertFalse(DumAemAttrUtils.isValidNode("jcr:title", aemObject));
        }
    }

    @Nested
    @DisplayName("normalizeLocale Tests")
    class NormalizeLocaleTests {

        @Test
        @DisplayName("Should normalize locale with underscore")
        void shouldNormalizeLocaleWithUnderscore() {
            assertEquals("en_US", DumAemAttrUtils.normalizeLocale("en_us"));
            assertEquals("pt_BR", DumAemAttrUtils.normalizeLocale("pt_br"));
            assertEquals("fr_FR", DumAemAttrUtils.normalizeLocale("FR_FR"));
        }

        @Test
        @DisplayName("Should return same locale when no underscore")
        void shouldReturnSameLocaleWhenNoUnderscore() {
            assertEquals("en", DumAemAttrUtils.normalizeLocale("en"));
            assertEquals("pt", DumAemAttrUtils.normalizeLocale("pt"));
        }
    }

    @Nested
    @DisplayName("getTagLabels Tests")
    class GetTagLabelsTests {

        @Test
        @DisplayName("Should extract tag labels from JSON")
        void shouldExtractTagLabelsFromJson() {
            JSONObject tagJson = new JSONObject();
            tagJson.put("jcr:title", "Default Title");
            tagJson.put("jcr:title.en_US", "English Title");
            tagJson.put("jcr:title.pt_BR", "Portuguese Title");

            Map<String, String> labels = DumAemAttrUtils.getTagLabels(tagJson);

            assertEquals("Default Title", labels.get("default"));
            assertEquals("English Title", labels.get("en_US"));
            assertEquals("Portuguese Title", labels.get("pt_BR"));
        }

        @Test
        @DisplayName("Should return empty map when no titles exist")
        void shouldReturnEmptyMapWhenNoTitlesExist() {
            JSONObject tagJson = new JSONObject();
            tagJson.put("sling:resourceType", "some/component");

            Map<String, String> labels = DumAemAttrUtils.getTagLabels(tagJson);

            assertFalse(labels.containsKey("default"));
        }
    }

    @Nested
    @DisplayName("getTurSNAttributeSpec Tests")
    class GetTurSNAttributeSpecTests {

        @Test
        @DisplayName("Should create TurSNAttributeSpec with facet")
        void shouldCreateTurSNAttributeSpecWithFacet() {
            Map<String, String> facetLabel = new HashMap<>();
            facetLabel.put("default", "Category");
            facetLabel.put("en_US", "Category");
            facetLabel.put("pt_BR", "Categoria");

            TurSNAttributeSpec spec = DumAemAttrUtils.getTurSNAttributeSpec("category", facetLabel);

            assertEquals("category", spec.getName());
            assertEquals("Category", spec.getDescription());
            assertTrue(spec.isFacet());
            assertFalse(spec.isMandatory());
            assertTrue(spec.isMultiValued());
        }
    }

    @Nested
    @DisplayName("addValuesToAttributes Tests")
    class AddValuesToAttributesTests {

        @Test
        @DisplayName("Should add string value to attributes")
        void shouldAddStringValueToAttributes() {
            DumAemTargetAttr targetAttr = DumAemTargetAttr.builder()
                    .name("title")
                    .build();
            DumAemSourceAttr sourceAttr = DumAemSourceAttr.builder()
                    .name("jcr:title")
                    .build();

            DumAemTargetAttrValueMap result = DumAemAttrUtils.addValuesToAttributes(
                    targetAttr, sourceAttr, "Test Title");

            assertTrue(result.containsKey("title"));
            assertEquals("Test Title", result.get("title").get(0));
        }

        @Test
        @DisplayName("Should convert HTML to text when flag is set")
        void shouldConvertHtmlToTextWhenFlagIsSet() {
            DumAemTargetAttr targetAttr = DumAemTargetAttr.builder()
                    .name("content")
                    .build();
            DumAemSourceAttr sourceAttr = DumAemSourceAttr.builder()
                    .name("htmlContent")
                    .convertHtmlToText(true)
                    .build();

            DumAemTargetAttrValueMap result = DumAemAttrUtils.addValuesToAttributes(
                    targetAttr, sourceAttr, "<p>Test Content</p>");

            assertTrue(result.containsKey("content"));
            assertFalse(result.get("content").get(0).contains("<p>"));
        }

        @Test
        @DisplayName("Should handle JSONArray values")
        void shouldHandleJsonArrayValues() {
            DumAemTargetAttr targetAttr = DumAemTargetAttr.builder()
                    .name("tags")
                    .build();
            DumAemSourceAttr sourceAttr = DumAemSourceAttr.builder()
                    .name("cq:tags")
                    .build();

            JSONArray jsonArray = new JSONArray();
            jsonArray.put("tag1");
            jsonArray.put("tag2");

            DumAemTargetAttrValueMap result = DumAemAttrUtils.addValuesToAttributes(
                    targetAttr, sourceAttr, jsonArray);

            assertTrue(result.containsKey("tags"));
            assertEquals(2, result.get("tags").size());
        }

        @Test
        @DisplayName("Should return empty map for null property")
        void shouldReturnEmptyMapForNullProperty() {
            DumAemTargetAttr targetAttr = DumAemTargetAttr.builder()
                    .name("title")
                    .build();
            DumAemSourceAttr sourceAttr = DumAemSourceAttr.builder()
                    .name("jcr:title")
                    .build();

            DumAemTargetAttrValueMap result = DumAemAttrUtils.addValuesToAttributes(
                    targetAttr, sourceAttr, null);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("hasJcrPropertyValue Tests")
    class HasJcrPropertyValueTests {

        @Test
        @DisplayName("Should return true for valid property value")
        void shouldReturnTrueForValidPropertyValue() {
            assertTrue(DumAemAttrUtils.hasJcrPropertyValue("Some Value"));
        }

        @Test
        @DisplayName("Should return false for null property")
        void shouldReturnFalseForNullProperty() {
            assertFalse(DumAemAttrUtils.hasJcrPropertyValue(null));
        }
    }

    @Nested
    @DisplayName("getDumAttrDefUnique Tests")
    class GetDumAttrDefUniqueTests {

        @Test
        @DisplayName("Should return unique values only")
        void shouldReturnUniqueValuesOnly() {
            DumAemTargetAttr targetAttr = DumAemTargetAttr.builder()
                    .name("tags")
                    .build();

            DumAemTargetAttrValueMap valueMap = new DumAemTargetAttrValueMap();
            TurMultiValue multiValue = new TurMultiValue();
            multiValue.add("tag1");
            multiValue.add("tag2");
            multiValue.add("tag1");
            multiValue.add("tag3");
            valueMap.put("tags", multiValue);

            DumAemTargetAttrValueMap result = DumAemAttrUtils.getDumAttrDefUnique(targetAttr, valueMap);

            assertEquals(3, result.get("tags").size());
        }
    }

    @Nested
    @DisplayName("getTextValue Tests")
    class GetTextValueTests {

        @Test
        @DisplayName("Should return target attr value map with text value")
        void shouldReturnTargetAttrValueMapWithTextValue() {
            DumAemTargetAttr targetAttr = DumAemTargetAttr.builder()
                    .name("staticField")
                    .textValue("Static Value")
                    .build();

            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "cq:Page");

            DumAemObject aemObject = new DumAemObject("/content/test", jcrNode, DumAemEnv.AUTHOR);
            DumAemContext context = new DumAemContext(aemObject);
            context.setDumAemTargetAttr(targetAttr);
            context.setDumAemSourceAttr(DumAemSourceAttr.builder().build());

            DumAemTargetAttrValueMap result = DumAemAttrUtils.getTextValue(context);

            assertNotNull(result);
            assertTrue(result.containsKey("staticField"));
        }
    }

    @Nested
    @DisplayName("Constants Tests")
    class ConstantsTests {

        @Test
        @DisplayName("Should have correct CQ_TAGS_PATH constant")
        void shouldHaveCorrectCqTagsPathConstant() {
            assertEquals("/content/_cq_tags", DumAemAttrUtils.CQ_TAGS_PATH);
        }
    }
}

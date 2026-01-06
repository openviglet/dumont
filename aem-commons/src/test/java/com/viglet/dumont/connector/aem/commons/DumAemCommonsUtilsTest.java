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

package com.viglet.dumont.connector.aem.commons;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.context.DumAemLocalePathContext;
import com.viglet.turing.client.sn.job.TurSNAttributeSpec;

@DisplayName("DumAemCommonsUtils Tests")
class DumAemCommonsUtilsTest {

    private DumAemConfiguration configuration;

    @BeforeEach
    void setUp() {
        configuration = DumAemConfiguration.builder()
                .id("test-config")
                .url("http://localhost:4502")
                .rootPath("/content/mysite")
                .contentType("cq:Page")
                .defaultLocale(Locale.ENGLISH)
                .localePaths(new HashSet<>())
                .build();
    }

    @Nested
    @DisplayName("getDependencies Tests")
    class GetDependenciesTests {

        @Test
        @DisplayName("Should extract content paths from JSON")
        void shouldExtractContentPathsFromJson() {
            JSONObject json = new JSONObject();
            json.put("reference", "/content/dam/mysite/image.jpg");
            json.put("link", "/content/mysite/en/page");

            Set<String> dependencies = DumAemCommonsUtils.getDependencies(json);

            assertTrue(dependencies.contains("/content/dam/mysite/image.jpg"));
            assertTrue(dependencies.contains("/content/mysite/en/page"));
        }

        @Test
        @DisplayName("Should handle nested JSON objects")
        void shouldHandleNestedJsonObjects() {
            JSONObject nested = new JSONObject();
            nested.put("image", "/content/dam/image.png");

            JSONObject json = new JSONObject();
            json.put("nested", nested);

            Set<String> dependencies = DumAemCommonsUtils.getDependencies(json);

            assertTrue(dependencies.contains("/content/dam/image.png"));
        }

        @Test
        @DisplayName("Should handle JSON arrays")
        void shouldHandleJsonArrays() {
            JSONArray array = new JSONArray();
            array.put("/content/page1");
            array.put("/content/page2");

            JSONObject json = new JSONObject();
            json.put("references", array);

            Set<String> dependencies = DumAemCommonsUtils.getDependencies(json);

            assertTrue(dependencies.contains("/content/page1"));
            assertTrue(dependencies.contains("/content/page2"));
        }

        @Test
        @DisplayName("Should return empty set for JSON without content paths")
        void shouldReturnEmptySetForJsonWithoutContentPaths() {
            JSONObject json = new JSONObject();
            json.put("title", "Test Title");
            json.put("number", 123);

            Set<String> dependencies = DumAemCommonsUtils.getDependencies(json);

            assertTrue(dependencies.isEmpty());
        }
    }

    @Nested
    @DisplayName("isTypeEqualContentType Tests")
    class IsTypeEqualContentTypeTests {

        @Test
        @DisplayName("Should return true when types match")
        void shouldReturnTrueWhenTypesMatch() {
            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "cq:Page");

            DumAemObjectGeneric aemObject = new DumAemObjectGeneric("/content/test", jcrNode);

            assertTrue(DumAemCommonsUtils.isTypeEqualContentType(aemObject, configuration));
        }

        @Test
        @DisplayName("Should return false when types don't match")
        void shouldReturnFalseWhenTypesDontMatch() {
            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "dam:Asset");

            DumAemObjectGeneric aemObject = new DumAemObjectGeneric("/content/test", jcrNode);

            assertFalse(DumAemCommonsUtils.isTypeEqualContentType(aemObject, configuration));
        }
    }

    @Nested
    @DisplayName("usingContentTypeParameter Tests")
    class UsingContentTypeParameterTests {

        @Test
        @DisplayName("Should return true when content type is set")
        void shouldReturnTrueWhenContentTypeIsSet() {
            assertTrue(DumAemCommonsUtils.usingContentTypeParameter(configuration));
        }

        @Test
        @DisplayName("Should return false when content type is blank")
        void shouldReturnFalseWhenContentTypeIsBlank() {
            DumAemConfiguration config = DumAemConfiguration.builder()
                    .contentType("")
                    .build();

            assertFalse(DumAemCommonsUtils.usingContentTypeParameter(config));
        }

        @Test
        @DisplayName("Should return false when content type is null")
        void shouldReturnFalseWhenContentTypeIsNull() {
            DumAemConfiguration config = DumAemConfiguration.builder()
                    .contentType(null)
                    .build();

            assertFalse(DumAemCommonsUtils.usingContentTypeParameter(config));
        }
    }

    @Nested
    @DisplayName("isNotOnceConfig Tests")
    class IsNotOnceConfigTests {

        @Test
        @DisplayName("Should return true when no once pattern is set")
        void shouldReturnTrueWhenNoOncePatternIsSet() {
            assertTrue(DumAemCommonsUtils.isNotOnceConfig("/content/test", configuration));
        }

        @Test
        @DisplayName("Should return false when path matches once pattern")
        void shouldReturnFalseWhenPathMatchesOncePattern() {
            DumAemConfiguration config = DumAemConfiguration.builder()
                    .oncePattern("/content/once.*")
                    .build();

            assertFalse(DumAemCommonsUtils.isNotOnceConfig("/content/once/page", config));
        }

        @Test
        @DisplayName("Should return true when path does not match once pattern")
        void shouldReturnTrueWhenPathDoesNotMatchOncePattern() {
            DumAemConfiguration config = DumAemConfiguration.builder()
                    .oncePattern("/content/once.*")
                    .build();

            assertTrue(DumAemCommonsUtils.isNotOnceConfig("/content/normal/page", config));
        }
    }

    @Nested
    @DisplayName("configOnce Tests")
    class ConfigOnceTests {

        @Test
        @DisplayName("Should format config once correctly")
        void shouldFormatConfigOnceCorrectly() {
            String result = DumAemCommonsUtils.configOnce(configuration);

            assertEquals("test-config/once", result);
        }
    }

    @Nested
    @DisplayName("addFirstItemToAttribute Tests")
    class AddFirstItemToAttributeTests {

        @Test
        @DisplayName("Should add item to attributes map")
        void shouldAddItemToAttributesMap() {
            Map<String, Object> attributes = new HashMap<>();

            DumAemCommonsUtils.addFirstItemToAttribute("title", "Test Title", attributes);

            assertEquals("Test Title", attributes.get("title"));
        }
    }

    @Nested
    @DisplayName("getDeltaDate Tests")
    class GetDeltaDateTests {

        @Test
        @DisplayName("Should return last modified date when available")
        void shouldReturnLastModifiedDateWhenAvailable() {
            JSONObject jcrContent = new JSONObject();
            jcrContent.put("jcr:lastModified", "Mon Jan 01 2024 10:30:00 GMT+0000");

            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "cq:Page");
            jcrNode.put("jcr:content", jcrContent);

            DumAemObjectGeneric aemObject = new DumAemObjectGeneric("/content/test", jcrNode);

            Date result = DumAemCommonsUtils.getDeltaDate(aemObject);

            assertNotNull(result);
        }

        @Test
        @DisplayName("Should return current date when no dates available")
        void shouldReturnCurrentDateWhenNoDatesAvailable() {
            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "cq:Page");

            DumAemObjectGeneric aemObject = new DumAemObjectGeneric("/content/test", jcrNode);

            Date result = DumAemCommonsUtils.getDeltaDate(aemObject);

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("getDefinitionFromModel Tests")
    class GetDefinitionFromModelTests {

        @Test
        @DisplayName("Should return matching attribute specs")
        void shouldReturnMatchingAttributeSpecs() {
            TurSNAttributeSpec spec1 = TurSNAttributeSpec.builder().name("title").build();
            TurSNAttributeSpec spec2 = TurSNAttributeSpec.builder().name("description").build();
            List<TurSNAttributeSpec> specList = new ArrayList<>(Arrays.asList(spec1, spec2));

            Map<String, Object> targetAttrMap = new HashMap<>();
            targetAttrMap.put("title", "Test");

            List<TurSNAttributeSpec> result = DumAemCommonsUtils.getDefinitionFromModel(specList, targetAttrMap);

            assertEquals(1, result.size());
            assertEquals("title", result.get(0).getName());
        }

        @Test
        @DisplayName("Should return empty list when no matches")
        void shouldReturnEmptyListWhenNoMatches() {
            TurSNAttributeSpec spec1 = TurSNAttributeSpec.builder().name("author").build();
            List<TurSNAttributeSpec> specList = new ArrayList<>(Collections.singletonList(spec1));

            Map<String, Object> targetAttrMap = new HashMap<>();
            targetAttrMap.put("title", "Test");

            List<TurSNAttributeSpec> result = DumAemCommonsUtils.getDefinitionFromModel(specList, targetAttrMap);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("getSiteName Tests")
    class GetSiteNameTests {

        @Test
        @DisplayName("Should extract site name from JSON")
        void shouldExtractSiteNameFromJson() {
            JSONObject jcrContent = new JSONObject();
            jcrContent.put("jcr:title", "My Site");

            JSONObject jsonSite = new JSONObject();
            jsonSite.put("jcr:content", jcrContent);

            Optional<String> result = DumAemCommonsUtils.getSiteName(jsonSite);

            assertTrue(result.isPresent());
            assertEquals("My Site", result.get());
        }

        @Test
        @DisplayName("Should return empty when no title")
        void shouldReturnEmptyWhenNoTitle() {
            JSONObject jsonSite = new JSONObject();
            jsonSite.put("jcr:content", new JSONObject());

            Optional<String> result = DumAemCommonsUtils.getSiteName(jsonSite);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty when no jcr:content")
        void shouldReturnEmptyWhenNoJcrContent() {
            JSONObject jsonSite = new JSONObject();

            Optional<String> result = DumAemCommonsUtils.getSiteName(jsonSite);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("checkIfFileHasNotImageExtension Tests")
    class CheckIfFileHasNotImageExtensionTests {

        @Test
        @DisplayName("Should return true for non-image files")
        void shouldReturnTrueForNonImageFiles() {
            assertTrue(DumAemCommonsUtils.checkIfFileHasNotImageExtension("document.pdf"));
            assertTrue(DumAemCommonsUtils.checkIfFileHasNotImageExtension("file.txt"));
            assertTrue(DumAemCommonsUtils.checkIfFileHasNotImageExtension("data.json"));
        }

        @Test
        @DisplayName("Should return false for image files")
        void shouldReturnFalseForImageFiles() {
            assertFalse(DumAemCommonsUtils.checkIfFileHasNotImageExtension("image.jpg"));
            assertFalse(DumAemCommonsUtils.checkIfFileHasNotImageExtension("photo.png"));
            assertFalse(DumAemCommonsUtils.checkIfFileHasNotImageExtension("icon.svg"));
            assertFalse(DumAemCommonsUtils.checkIfFileHasNotImageExtension("picture.jpeg"));
            assertFalse(DumAemCommonsUtils.checkIfFileHasNotImageExtension("banner.webp"));
        }

        @Test
        @DisplayName("Should be case insensitive")
        void shouldBeCaseInsensitive() {
            assertFalse(DumAemCommonsUtils.checkIfFileHasNotImageExtension("IMAGE.JPG"));
            assertFalse(DumAemCommonsUtils.checkIfFileHasNotImageExtension("Photo.PNG"));
        }
    }

    @Nested
    @DisplayName("addItemInExistingAttribute Tests")
    class AddItemInExistingAttributeTests {

        @Test
        @DisplayName("Should convert single value to array when adding item")
        void shouldConvertSingleValueToArrayWhenAddingItem() {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("tags", "tag1");

            DumAemCommonsUtils.addItemInExistingAttribute("tag2", attributes, "tags");

            assertTrue(attributes.get("tags") instanceof List);
            List<?> tags = (List<?>) attributes.get("tags");
            assertEquals(2, tags.size());
        }

        @Test
        @DisplayName("Should add to existing array")
        void shouldAddToExistingArray() {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("tags", new ArrayList<>(Arrays.asList("tag1", "tag2")));

            DumAemCommonsUtils.addItemInExistingAttribute("tag3", attributes, "tags");

            List<?> tags = (List<?>) attributes.get("tags");
            assertEquals(3, tags.size());
        }
    }

    @Nested
    @DisplayName("hasProperty Tests")
    class HasPropertyTests {

        @Test
        @DisplayName("Should return true when property exists and is not null")
        void shouldReturnTrueWhenPropertyExistsAndIsNotNull() {
            JSONObject json = new JSONObject();
            json.put("title", "Test");

            assertTrue(DumAemCommonsUtils.hasProperty(json, "title"));
        }

        @Test
        @DisplayName("Should return false when property does not exist")
        void shouldReturnFalseWhenPropertyDoesNotExist() {
            JSONObject json = new JSONObject();

            assertFalse(DumAemCommonsUtils.hasProperty(json, "title"));
        }
    }

    @Nested
    @DisplayName("getPropertyValue Tests")
    class GetPropertyValueTests {

        @Test
        @DisplayName("Should return string value")
        void shouldReturnStringValue() {
            String result = DumAemCommonsUtils.getPropertyValue("Test Value");

            assertEquals("Test Value", result);
        }

        @Test
        @DisplayName("Should return first item from array")
        void shouldReturnFirstItemFromArray() {
            JSONArray array = new JSONArray();
            array.put("first");
            array.put("second");

            String result = DumAemCommonsUtils.getPropertyValue(array);

            assertEquals("first", result);
        }

        @Test
        @DisplayName("Should return empty string for empty array")
        void shouldReturnEmptyStringForEmptyArray() {
            JSONArray array = new JSONArray();

            String result = DumAemCommonsUtils.getPropertyValue(array);

            assertEquals("", result);
        }

        @Test
        @DisplayName("Should return null for null input")
        void shouldReturnNullForNullInput() {
            String result = DumAemCommonsUtils.getPropertyValue(null);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("isResponseBodyJSONArray Tests")
    class IsResponseBodyJSONArrayTests {

        @Test
        @DisplayName("Should return true for JSON array string")
        void shouldReturnTrueForJsonArrayString() {
            assertTrue(DumAemCommonsUtils.isResponseBodyJSONArray("[{\"key\":\"value\"}]"));
        }

        @Test
        @DisplayName("Should return false for JSON object string")
        void shouldReturnFalseForJsonObjectString() {
            assertFalse(DumAemCommonsUtils.isResponseBodyJSONArray("{\"key\":\"value\"}"));
        }
    }

    @Nested
    @DisplayName("isResponseBodyJSONObject Tests")
    class IsResponseBodyJSONObjectTests {

        @Test
        @DisplayName("Should return true for JSON object string")
        void shouldReturnTrueForJsonObjectString() {
            assertTrue(DumAemCommonsUtils.isResponseBodyJSONObject("{\"key\":\"value\"}"));
        }

        @Test
        @DisplayName("Should return false for JSON array string")
        void shouldReturnFalseForJsonArrayString() {
            assertFalse(DumAemCommonsUtils.isResponseBodyJSONObject("[{\"key\":\"value\"}]"));
        }
    }

    @Nested
    @DisplayName("getJsonNodeToComponent Tests")
    class GetJsonNodeToComponentTests {

        @Test
        @DisplayName("Should extract text content from JSON")
        void shouldExtractTextContentFromJson() {
            JSONObject json = new JSONObject();
            json.put("text", "Hello World");

            String result = DumAemCommonsUtils.getJsonNodeToComponent(json);

            assertEquals("Hello World", result);
        }

        @Test
        @DisplayName("Should extract nested text content")
        void shouldExtractNestedTextContent() {
            JSONObject nested = new JSONObject();
            nested.put("text", " Nested");

            JSONObject json = new JSONObject();
            json.put("text", "Parent");
            json.put("component", nested);

            String result = DumAemCommonsUtils.getJsonNodeToComponent(json);

            assertTrue(result.contains("Parent"));
            assertTrue(result.contains("Nested"));
        }
    }

    @Nested
    @DisplayName("getLocaleByPath Tests")
    class GetLocaleByPathTests {

        @Test
        @DisplayName("Should return locale for matching path")
        void shouldReturnLocaleForMatchingPath() {
            DumAemLocalePathContext localePath = DumAemLocalePathContext.builder()
                    .path("/content/mysite/pt")
                    .locale(new Locale("pt", "BR"))
                    .build();

            DumAemConfiguration config = DumAemConfiguration.builder()
                    .defaultLocale(Locale.ENGLISH)
                    .localePaths(Collections.singleton(localePath))
                    .build();

            Locale result = DumAemCommonsUtils.getLocaleByPath(config, "/content/mysite/pt/home");

            assertEquals(new Locale("pt", "BR"), result);
        }

        @Test
        @DisplayName("Should return default locale when no path matches")
        void shouldReturnDefaultLocaleWhenNoPathMatches() {
            DumAemConfiguration config = DumAemConfiguration.builder()
                    .defaultLocale(Locale.ENGLISH)
                    .localePaths(new HashSet<>())
                    .build();

            Locale result = DumAemCommonsUtils.getLocaleByPath(config, "/content/mysite/unknown/home");

            assertEquals(Locale.ENGLISH, result);
        }
    }
}

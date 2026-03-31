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

package com.viglet.dumont.connector.aem.commons.bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.viglet.turing.client.sn.TurMultiValue;

@DisplayName("DumAemAttrMap Tests")
class DumAemAttrMapTest {

    private DumAemAttrMap valueMap;

    @BeforeEach
    void setUp() {
        valueMap = new DumAemAttrMap();
    }

    @Nested
    @DisplayName("set() Tests")
    class SetTests {

        @Test
        @DisplayName("Should add string value")
        void shouldAddStringValue() {
            valueMap.set("title", "Test Title");

            assertTrue(valueMap.containsKey("title"));
            assertEquals("Test Title", valueMap.get("title").get(0));
        }

        @Test
        @DisplayName("Should not add null value")
        void shouldNotAddNullValue() {
            valueMap.set("title", null);

            assertFalse(valueMap.containsKey("title"));
        }

        @Test
        @DisplayName("Should override existing value")
        void shouldOverrideExistingValue() {
            valueMap.set("title", "First");
            valueMap.set("title", "Second");

            assertEquals("Second", valueMap.get("title").get(0));
        }

        @Test
        @DisplayName("Should add date value")
        void shouldAddDateValue() {
            Date date = new Date();
            valueMap.set("date", date);

            assertTrue(valueMap.containsKey("date"));
        }

        @Test
        @DisplayName("Should add boolean value")
        void shouldAddBooleanValue() {
            valueMap.set("active", true);

            assertTrue(valueMap.containsKey("active"));
        }

        @Test
        @DisplayName("Should add integer value")
        void shouldAddIntegerValue() {
            valueMap.set("count", 42);

            assertTrue(valueMap.containsKey("count"));
        }

        @Test
        @DisplayName("Should add long value")
        void shouldAddLongValue() {
            valueMap.set("id", 123456789L);

            assertTrue(valueMap.containsKey("id"));
        }

        @Test
        @DisplayName("Should add double value")
        void shouldAddDoubleValue() {
            valueMap.set("price", 19.99);

            assertTrue(valueMap.containsKey("price"));
        }

        @Test
        @DisplayName("Should add float value")
        void shouldAddFloatValue() {
            valueMap.set("rating", 4.5f);

            assertTrue(valueMap.containsKey("rating"));
        }

        @Test
        @DisplayName("Should support fluent chaining")
        void shouldSupportFluentChaining() {
            valueMap.set("title", "Hello")
                    .set("count", 1)
                    .set("active", true);

            assertEquals(3, valueMap.size());
        }
    }

    @Nested
    @DisplayName("append() Tests")
    class AppendTests {

        @Test
        @DisplayName("Should merge values")
        void shouldMergeValues() {
            valueMap.append("tags", "tag1");
            valueMap.append("tags", "tag2");

            assertEquals(2, valueMap.get("tags").size());
        }

        @Test
        @DisplayName("Should not add null value")
        void shouldNotAddNullValue() {
            valueMap.append("tags", null);

            assertFalse(valueMap.containsKey("tags"));
        }

        @Test
        @DisplayName("Should support fluent chaining")
        void shouldSupportFluentChaining() {
            valueMap.append("text", "bio")
                    .append("text", "name");

            assertEquals(2, valueMap.get("text").size());
        }
    }

    @Nested
    @DisplayName("setIfAbsent() Tests")
    class SetIfAbsentTests {

        @Test
        @DisplayName("Should set when absent")
        void shouldSetWhenAbsent() {
            valueMap.setIfAbsent("abstract", "description");

            assertTrue(valueMap.containsKey("abstract"));
            assertEquals("description", valueMap.get("abstract").get(0));
        }

        @Test
        @DisplayName("Should not set when present")
        void shouldNotSetWhenPresent() {
            valueMap.set("abstract", "first");
            valueMap.setIfAbsent("abstract", "second");

            assertEquals("first", valueMap.get("abstract").get(0));
        }

        @Test
        @DisplayName("Should ignore null value")
        void shouldIgnoreNullValue() {
            valueMap.setIfAbsent("abstract", null);

            assertFalse(valueMap.containsKey("abstract"));
        }
    }

    @Nested
    @DisplayName("setAll() / setAllDates() Tests")
    class CollectionTests {

        @Test
        @DisplayName("Should add string collection")
        void shouldAddStringCollection() {
            List<String> tags = Arrays.asList("tag1", "tag2", "tag3");
            valueMap.setAll("tags", tags);

            assertTrue(valueMap.containsKey("tags"));
            assertEquals(3, valueMap.get("tags").size());
        }

        @Test
        @DisplayName("Should add date collection")
        void shouldAddDateCollection() {
            List<Date> dates = Arrays.asList(new Date(), new Date());
            valueMap.setAllDates("dates", dates);

            assertTrue(valueMap.containsKey("dates"));
        }

        @Test
        @DisplayName("Should not add null collection")
        void shouldNotAddNullCollection() {
            valueMap.setAll("tags", null);

            assertFalse(valueMap.containsKey("tags"));
        }

        @Test
        @DisplayName("appendAll should merge string collections")
        void appendAllShouldMerge() {
            valueMap.setAll("tags", List.of("a", "b"));
            valueMap.appendAll("tags", List.of("c"));

            assertEquals(3, valueMap.get("tags").size());
        }
    }

    @Nested
    @DisplayName("addWithValue() TurMultiValue Tests")
    class TurMultiValueTests {

        @Test
        @DisplayName("Should add TurMultiValue")
        void shouldAddTurMultiValue() {
            TurMultiValue multiValue = TurMultiValue.singleItem("value");
            valueMap.set("attr", multiValue);

            assertTrue(valueMap.containsKey("attr"));
        }
    }

    @Nested
    @DisplayName("merge() Tests")
    class MergeTests {

        @Test
        @DisplayName("Should merge two maps")
        void shouldMergeTwoMaps() {
            valueMap.set("attr1", "value1");

            DumAemAttrMap otherMap = new DumAemAttrMap();
            otherMap.set("attr2", "value2");

            valueMap.merge(otherMap);

            assertTrue(valueMap.containsKey("attr1"));
            assertTrue(valueMap.containsKey("attr2"));
        }

        @Test
        @DisplayName("Should append values during merge when override is false")
        void shouldAppendValuesDuringMerge() {
            valueMap.append("attr", "original");

            DumAemAttrMap otherMap = new DumAemAttrMap();
            otherMap.append("attr", "appended");

            valueMap.merge(otherMap);

            assertEquals(2, valueMap.get("attr").size());
        }
    }

    @Nested
    @DisplayName("Static Factory Tests")
    class StaticFactoryTests {

        @Test
        @DisplayName("Should create map with of()")
        void shouldCreateMapWithOf() {
            DumAemAttrMap result = DumAemAttrMap.of("title", "Test");

            assertTrue(result.containsKey("title"));
            assertEquals("Test", result.get("title").get(0));
        }

        @Test
        @DisplayName("Should create map with ofAppend()")
        void shouldCreateMapWithOfAppend() {
            DumAemAttrMap result = DumAemAttrMap.ofAppend("tag", "value");

            assertTrue(result.containsKey("tag"));
        }

        @Test
        @DisplayName("Should create map with ofAppendAll()")
        void shouldCreateMapWithOfAppendAll() {
            List<String> values = Arrays.asList("v1", "v2");

            DumAemAttrMap result = DumAemAttrMap.ofAppendAll("attr", values);

            assertTrue(result.containsKey("attr"));
            assertEquals(2, result.get("attr").size());
        }

        @Test
        @DisplayName("Should create map with TurMultiValue via ofAppend()")
        void shouldCreateMapWithTurMultiValue() {
            TurMultiValue multiValue = TurMultiValue.singleItem("value");

            DumAemAttrMap result = DumAemAttrMap.ofAppend("attr", multiValue);

            assertTrue(result.containsKey("attr"));
        }
    }
}

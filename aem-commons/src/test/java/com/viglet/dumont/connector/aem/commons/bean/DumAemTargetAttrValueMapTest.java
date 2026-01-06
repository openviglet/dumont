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

@DisplayName("DumAemTargetAttrValueMap Tests")
class DumAemTargetAttrValueMapTest {

    private DumAemTargetAttrValueMap valueMap;

    @BeforeEach
    void setUp() {
        valueMap = new DumAemTargetAttrValueMap();
    }

    @Nested
    @DisplayName("addWithSingleValue String Tests")
    class AddWithSingleValueStringTests {

        @Test
        @DisplayName("Should add string value")
        void shouldAddStringValue() {
            valueMap.addWithSingleValue("title", "Test Title", false);

            assertTrue(valueMap.containsKey("title"));
            assertEquals("Test Title", valueMap.get("title").get(0));
        }

        @Test
        @DisplayName("Should not add null string value")
        void shouldNotAddNullStringValue() {
            valueMap.addWithSingleValue("title", (String) null, false);

            assertFalse(valueMap.containsKey("title"));
        }

        @Test
        @DisplayName("Should override existing value when override is true")
        void shouldOverrideExistingValue() {
            valueMap.addWithSingleValue("title", "First", false);
            valueMap.addWithSingleValue("title", "Second", true);

            assertEquals("Second", valueMap.get("title").get(0));
        }

        @Test
        @DisplayName("Should merge values when override is false")
        void shouldMergeValues() {
            valueMap.addWithSingleValue("tags", "tag1", false);
            valueMap.addWithSingleValue("tags", "tag2", false);

            assertEquals(2, valueMap.get("tags").size());
        }
    }

    @Nested
    @DisplayName("addWithSingleValue Date Tests")
    class AddWithSingleValueDateTests {

        @Test
        @DisplayName("Should add date value")
        void shouldAddDateValue() {
            Date date = new Date();
            valueMap.addWithSingleValue("date", date, false);

            assertTrue(valueMap.containsKey("date"));
        }

        @Test
        @DisplayName("Should not add null date value")
        void shouldNotAddNullDateValue() {
            valueMap.addWithSingleValue("date", (Date) null, false);

            assertFalse(valueMap.containsKey("date"));
        }
    }

    @Nested
    @DisplayName("addWithSingleValue Boolean Tests")
    class AddWithSingleValueBooleanTests {

        @Test
        @DisplayName("Should add boolean value")
        void shouldAddBooleanValue() {
            valueMap.addWithSingleValue("active", true, false);

            assertTrue(valueMap.containsKey("active"));
        }

        @Test
        @DisplayName("Should not add null boolean value")
        void shouldNotAddNullBooleanValue() {
            valueMap.addWithSingleValue("active", (Boolean) null, false);

            assertFalse(valueMap.containsKey("active"));
        }
    }

    @Nested
    @DisplayName("addWithSingleValue Integer Tests")
    class AddWithSingleValueIntegerTests {

        @Test
        @DisplayName("Should add integer value")
        void shouldAddIntegerValue() {
            valueMap.addWithSingleValue("count", 42, false);

            assertTrue(valueMap.containsKey("count"));
        }

        @Test
        @DisplayName("Should not add null integer value")
        void shouldNotAddNullIntegerValue() {
            valueMap.addWithSingleValue("count", (Integer) null, false);

            assertFalse(valueMap.containsKey("count"));
        }
    }

    @Nested
    @DisplayName("addWithSingleValue Long Tests")
    class AddWithSingleValueLongTests {

        @Test
        @DisplayName("Should add long value")
        void shouldAddLongValue() {
            valueMap.addWithSingleValue("id", 123456789L, false);

            assertTrue(valueMap.containsKey("id"));
        }

        @Test
        @DisplayName("Should not add null long value")
        void shouldNotAddNullLongValue() {
            valueMap.addWithSingleValue("id", (Long) null, false);

            assertFalse(valueMap.containsKey("id"));
        }
    }

    @Nested
    @DisplayName("addWithSingleValue Double Tests")
    class AddWithSingleValueDoubleTests {

        @Test
        @DisplayName("Should add double value")
        void shouldAddDoubleValue() {
            valueMap.addWithSingleValue("price", 19.99, false);

            assertTrue(valueMap.containsKey("price"));
        }

        @Test
        @DisplayName("Should not add null double value")
        void shouldNotAddNullDoubleValue() {
            valueMap.addWithSingleValue("price", (Double) null, false);

            assertFalse(valueMap.containsKey("price"));
        }
    }

    @Nested
    @DisplayName("addWithSingleValue Float Tests")
    class AddWithSingleValueFloatTests {

        @Test
        @DisplayName("Should add float value")
        void shouldAddFloatValue() {
            valueMap.addWithSingleValue("rating", 4.5f, false);

            assertTrue(valueMap.containsKey("rating"));
        }

        @Test
        @DisplayName("Should not add null float value")
        void shouldNotAddNullFloatValue() {
            valueMap.addWithSingleValue("rating", (Float) null, false);

            assertFalse(valueMap.containsKey("rating"));
        }
    }

    @Nested
    @DisplayName("addWithStringCollectionValue Tests")
    class AddWithStringCollectionValueTests {

        @Test
        @DisplayName("Should add string collection value")
        void shouldAddStringCollectionValue() {
            List<String> tags = Arrays.asList("tag1", "tag2", "tag3");
            valueMap.addWithStringCollectionValue("tags", tags, false);

            assertTrue(valueMap.containsKey("tags"));
            assertEquals(3, valueMap.get("tags").size());
        }

        @Test
        @DisplayName("Should not add null collection")
        void shouldNotAddNullCollection() {
            valueMap.addWithStringCollectionValue("tags", null, false);

            assertFalse(valueMap.containsKey("tags"));
        }
    }

    @Nested
    @DisplayName("addWithDateCollectionValue Tests")
    class AddWithDateCollectionValueTests {

        @Test
        @DisplayName("Should add date collection value")
        void shouldAddDateCollectionValue() {
            List<Date> dates = Arrays.asList(new Date(), new Date());
            valueMap.addWithDateCollectionValue("dates", dates, false);

            assertTrue(valueMap.containsKey("dates"));
        }

        @Test
        @DisplayName("Should not add null date collection")
        void shouldNotAddNullDateCollection() {
            valueMap.addWithDateCollectionValue("dates", null, false);

            assertFalse(valueMap.containsKey("dates"));
        }
    }

    @Nested
    @DisplayName("addWithSingleValue TurMultiValue Tests")
    class AddWithTurMultiValueTests {

        @Test
        @DisplayName("Should add TurMultiValue")
        void shouldAddTurMultiValue() {
            TurMultiValue multiValue = TurMultiValue.singleItem("value");
            valueMap.addWithSingleValue("attr", multiValue, false);

            assertTrue(valueMap.containsKey("attr"));
        }

        @Test
        @DisplayName("Should not add null TurMultiValue")
        void shouldNotAddNullTurMultiValue() {
            valueMap.addWithSingleValue("attr", (TurMultiValue) null, false);

            assertFalse(valueMap.containsKey("attr"));
        }
    }

    @Nested
    @DisplayName("merge Tests")
    class MergeTests {

        @Test
        @DisplayName("Should merge two maps")
        void shouldMergeTwoMaps() {
            valueMap.addWithSingleValue("attr1", "value1", false);

            DumAemTargetAttrValueMap otherMap = new DumAemTargetAttrValueMap();
            otherMap.addWithSingleValue("attr2", "value2", false);

            valueMap.merge(otherMap);

            assertTrue(valueMap.containsKey("attr1"));
            assertTrue(valueMap.containsKey("attr2"));
        }

        @Test
        @DisplayName("Should append values during merge when override is false")
        void shouldAppendValuesDuringMerge() {
            valueMap.addWithSingleValue("attr", "original", false);

            DumAemTargetAttrValueMap otherMap = new DumAemTargetAttrValueMap();
            otherMap.addWithSingleValue("attr", "appended", false);

            valueMap.merge(otherMap);

            assertEquals(2, valueMap.get("attr").size());
        }
    }

    @Nested
    @DisplayName("singleItem Static Factory Tests")
    class SingleItemTests {

        @Test
        @DisplayName("Should create map with DumAemTargetAttrValue")
        void shouldCreateMapWithTargetAttrValue() {
            TurMultiValue multiValue = TurMultiValue.singleItem("test");
            DumAemTargetAttrValue attrValue = new DumAemTargetAttrValue("attr", multiValue);

            DumAemTargetAttrValueMap result = DumAemTargetAttrValueMap.singleItem(attrValue);

            assertTrue(result.containsKey("attr"));
        }

        @Test
        @DisplayName("Should create map with string list")
        void shouldCreateMapWithStringList() {
            List<String> values = Arrays.asList("v1", "v2");

            DumAemTargetAttrValueMap result = DumAemTargetAttrValueMap.singleItem("attr", values, false);

            assertTrue(result.containsKey("attr"));
            assertEquals(2, result.get("attr").size());
        }

        @Test
        @DisplayName("Should create map with TurMultiValue and override")
        void shouldCreateMapWithTurMultiValueAndOverride() {
            TurMultiValue multiValue = TurMultiValue.singleItem("test");

            DumAemTargetAttrValueMap result = DumAemTargetAttrValueMap.singleItem("attr", multiValue, false);

            assertTrue(result.containsKey("attr"));
        }

        @Test
        @DisplayName("Should create map with string value")
        void shouldCreateMapWithStringValue() {
            DumAemTargetAttrValueMap result = DumAemTargetAttrValueMap.singleItem("title", "Test", false);

            assertTrue(result.containsKey("title"));
            assertEquals("Test", result.get("title").get(0));
        }

        @Test
        @DisplayName("Should create map with date value")
        void shouldCreateMapWithDateValue() {
            Date date = new Date();

            DumAemTargetAttrValueMap result = DumAemTargetAttrValueMap.singleItem("date", date, false);

            assertTrue(result.containsKey("date"));
        }

        @Test
        @DisplayName("Should create map with boolean value")
        void shouldCreateMapWithBooleanValue() {
            DumAemTargetAttrValueMap result = DumAemTargetAttrValueMap.singleItem("active", true, false);

            assertTrue(result.containsKey("active"));
        }

        @Test
        @DisplayName("Should create map with integer value")
        void shouldCreateMapWithIntegerValue() {
            DumAemTargetAttrValueMap result = DumAemTargetAttrValueMap.singleItem("count", 10, false);

            assertTrue(result.containsKey("count"));
        }

        @Test
        @DisplayName("Should create map with double value")
        void shouldCreateMapWithDoubleValue() {
            DumAemTargetAttrValueMap result = DumAemTargetAttrValueMap.singleItem("price", 19.99, false);

            assertTrue(result.containsKey("price"));
        }

        @Test
        @DisplayName("Should create map with float value")
        void shouldCreateMapWithFloatValue() {
            DumAemTargetAttrValueMap result = DumAemTargetAttrValueMap.singleItem("rating", 4.5f, false);

            assertTrue(result.containsKey("rating"));
        }

        @Test
        @DisplayName("Should create map with long value")
        void shouldCreateMapWithLongValue() {
            DumAemTargetAttrValueMap result = DumAemTargetAttrValueMap.singleItem("id", 123456789L, false);

            assertTrue(result.containsKey("id"));
        }

        @Test
        @DisplayName("Should create map with TurMultiValue only")
        void shouldCreateMapWithTurMultiValueOnly() {
            TurMultiValue multiValue = TurMultiValue.singleItem("value");

            DumAemTargetAttrValueMap result = DumAemTargetAttrValueMap.singleItem("attr", multiValue);

            assertTrue(result.containsKey("attr"));
        }
    }
}

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

package com.viglet.dumont.connector.aem.commons.mappers;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.viglet.turing.client.sn.job.TurSNAttributeSpec;

@DisplayName("DumAemContentMapping Tests")
class DumAemContentMappingTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should build content mapping with all fields")
        void shouldBuildContentMappingWithAllFields() {
            List<TurSNAttributeSpec> targetAttrDefinitions = Arrays.asList(
                    DumAemTargetAttrDefinition.builder().name("title").build(),
                    DumAemTargetAttrDefinition.builder().name("description").build());

            List<DumAemModel> models = Arrays.asList(
                    DumAemModel.builder().className("com.example.Model1").build(),
                    DumAemModel.builder().className("com.example.Model2").build());

            DumAemContentMapping mapping = DumAemContentMapping.builder()
                    .targetAttrDefinitions(targetAttrDefinitions)
                    .models(models)
                    .deltaClassName("com.example.DeltaClass")
                    .build();

            assertEquals(2, mapping.getTargetAttrDefinitions().size());
            assertEquals(2, mapping.getModels().size());
            assertEquals("com.example.DeltaClass", mapping.getDeltaClassName());
        }

        @Test
        @DisplayName("Should build content mapping with minimal fields")
        void shouldBuildContentMappingWithMinimalFields() {
            DumAemContentMapping mapping = DumAemContentMapping.builder().build();

            assertNull(mapping.getTargetAttrDefinitions());
            assertNull(mapping.getModels());
            assertNull(mapping.getDeltaClassName());
        }
    }

    @Nested
    @DisplayName("Getter and Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Should set and get targetAttrDefinitions")
        void shouldSetAndGetTargetAttrDefinitions() {
            DumAemContentMapping mapping = DumAemContentMapping.builder().build();
            List<TurSNAttributeSpec> definitions = Arrays.asList(
                    DumAemTargetAttrDefinition.builder().name("attr1").build());
            mapping.setTargetAttrDefinitions(definitions);

            assertEquals(1, mapping.getTargetAttrDefinitions().size());
        }

        @Test
        @DisplayName("Should set and get models")
        void shouldSetAndGetModels() {
            DumAemContentMapping mapping = DumAemContentMapping.builder().build();
            List<DumAemModel> models = Arrays.asList(
                    DumAemModel.builder().className("TestModel").build());
            mapping.setModels(models);

            assertEquals(1, mapping.getModels().size());
        }

        @Test
        @DisplayName("Should set and get deltaClassName")
        void shouldSetAndGetDeltaClassName() {
            DumAemContentMapping mapping = DumAemContentMapping.builder().build();
            mapping.setDeltaClassName("NewDeltaClass");

            assertEquals("NewDeltaClass", mapping.getDeltaClassName());
        }
    }

    @Nested
    @DisplayName("ToString Tests")
    class ToStringTests {

        @Test
        @DisplayName("Should have toString implementation")
        void shouldHaveToStringImplementation() {
            DumAemContentMapping mapping = DumAemContentMapping.builder()
                    .deltaClassName("TestDelta")
                    .build();

            String result = mapping.toString();

            assertNotNull(result);
            assertTrue(result.contains("TestDelta"));
        }
    }
}

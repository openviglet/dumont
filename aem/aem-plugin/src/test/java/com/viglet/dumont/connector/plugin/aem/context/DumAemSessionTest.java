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

package com.viglet.dumont.connector.plugin.aem.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.aem.commons.bean.DumAemEvent;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemContentMapping;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemModel;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemTargetAttr;
import com.viglet.turing.client.sn.job.TurSNAttributeSpec;

@DisplayName("DumAemSession Tests")
class DumAemSessionTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should build session with all fields")
        void shouldBuildSessionWithAllFields() {
            DumAemConfiguration configuration = DumAemConfiguration.builder()
                    .url("http://localhost:4502")
                    .build();

            DumAemModel model = DumAemModel.builder()
                    .className("TestModel")
                    .targetAttrs(Arrays.asList(
                            DumAemTargetAttr.builder().name("title").build()))
                    .build();

            DumAemContentMapping contentMapping = DumAemContentMapping.builder().build();

            DumAemEvent event = DumAemEvent.PUBLISHING;

            List<TurSNAttributeSpec> attributeSpecs = Arrays.asList(
                    TurSNAttributeSpec.builder().name("attr1").build());

            DumAemSession session = DumAemSession.builder()
                    .configuration(configuration)
                    .model(model)
                    .contentMapping(contentMapping)
                    .event(event)
                    .standalone(true)
                    .indexChildren(true)
                    .attributeSpecs(attributeSpecs)
                    .build();

            assertNotNull(session.getConfiguration());
            assertNotNull(session.getModel());
            assertNotNull(session.getContentMapping());
            assertNotNull(session.getEvent());
            assertTrue(session.isStandalone());
            assertTrue(session.isIndexChildren());
            assertEquals(1, session.getAttributeSpecs().size());
        }

        @Test
        @DisplayName("Should build session with minimal fields")
        void shouldBuildSessionWithMinimalFields() {
            DumAemSession session = DumAemSession.builder().build();

            assertNull(session.getConfiguration());
            assertNull(session.getModel());
            assertNull(session.getContentMapping());
            assertNull(session.getEvent());
            assertFalse(session.isStandalone());
            assertFalse(session.isIndexChildren());
            assertNull(session.getAttributeSpecs());
        }
    }

    @Nested
    @DisplayName("NoArgs Constructor Tests")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("Should create session with default values")
        void shouldCreateSessionWithDefaultValues() {
            DumAemSession session = DumAemSession.builder().build();

            assertNull(session.getConfiguration());
            assertNull(session.getModel());
            assertFalse(session.isStandalone());
            assertFalse(session.isIndexChildren());
        }
    }

    @Nested
    @DisplayName("AllArgs Constructor Tests")
    class AllArgsConstructorTests {

        @Test
        @DisplayName("Should create session with all arguments")
        void shouldCreateSessionWithAllArguments() {
            DumAemConfiguration configuration = DumAemConfiguration.builder().build();
            DumAemModel model = DumAemModel.builder().build();
            DumAemContentMapping contentMapping = DumAemContentMapping.builder().build();
            DumAemEvent event = DumAemEvent.NONE;
            List<TurSNAttributeSpec> attributeSpecs = Arrays.asList();

            DumAemSession session = DumAemSession.builder()
                    .configuration(configuration)
                    .model(model)
                    .contentMapping(contentMapping)
                    .event(event)
                    .standalone(true)
                    .indexChildren(true)
                    .attributeSpecs(attributeSpecs)
                    .build();

            assertEquals(configuration, session.getConfiguration());
            assertEquals(model, session.getModel());
            assertEquals(contentMapping, session.getContentMapping());
            assertEquals(event, session.getEvent());
            assertTrue(session.isStandalone());
            assertTrue(session.isIndexChildren());
            assertEquals(attributeSpecs, session.getAttributeSpecs());
        }
    }

    @Nested
    @DisplayName("Inheritance Tests")
    class InheritanceTests {

        @Test
        @DisplayName("Should inherit from DumConnectorSession")
        void shouldInheritFromDumConnectorSession() {
            DumAemSession session = DumAemSession.builder()
                    .transactionId("tx-123")
                    .source("test-source")
                    .build();

            assertEquals("tx-123", session.getTransactionId());
            assertEquals("test-source", session.getSource());
        }
    }
}

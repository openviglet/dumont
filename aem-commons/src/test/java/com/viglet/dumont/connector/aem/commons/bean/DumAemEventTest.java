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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DumAemEvent Tests")
class DumAemEventTest {

    @Test
    @DisplayName("Should have UNPUBLISHING enum value")
    void shouldHaveUnpublishingEnumValue() {
        assertEquals("UNPUBLISHING", DumAemEvent.UNPUBLISHING.toString());
    }

    @Test
    @DisplayName("Should have PUBLISHING enum value")
    void shouldHavePublishingEnumValue() {
        assertEquals("PUBLISHING", DumAemEvent.PUBLISHING.toString());
    }

    @Test
    @DisplayName("Should have NONE enum value")
    void shouldHaveNoneEnumValue() {
        assertEquals("NONE", DumAemEvent.NONE.toString());
    }

    @Test
    @DisplayName("Should have correct number of enum values")
    void shouldHaveCorrectNumberOfEnumValues() {
        assertEquals(3, DumAemEvent.values().length);
    }

    @Test
    @DisplayName("Should get enum by name")
    void shouldGetEnumByName() {
        assertEquals(DumAemEvent.UNPUBLISHING, DumAemEvent.valueOf("UNPUBLISHING"));
        assertEquals(DumAemEvent.PUBLISHING, DumAemEvent.valueOf("PUBLISHING"));
        assertEquals(DumAemEvent.NONE, DumAemEvent.valueOf("NONE"));
    }
}

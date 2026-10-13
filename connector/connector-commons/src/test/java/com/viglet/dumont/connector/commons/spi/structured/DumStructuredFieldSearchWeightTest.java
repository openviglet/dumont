/*
 *
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

package com.viglet.dumont.connector.commons.spi.structured;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/** A field's search weight reaches the site manifest (roadmap D140). */
class DumStructuredFieldSearchWeightTest {

    @Test
    @DisplayName("A declared search weight is carried into the manifest's field spec")
    void aDeclaredWeightReachesTheAttributeSpec() {
        DumStructuredSchema schema = DumStructuredSchema.builder()
                .field(DumStructuredField.builder("title").type(TurSEFieldType.TEXT).searchWeight(10).build())
                .text("text")
                .build();

        List<TurSNJobAttributeSpec> specs = schema.toAttributeSpecs();

        assertEquals(10f, specs.get(0).getSearchWeight());
        assertNull(specs.get(1).getSearchWeight(), "an undeclared weight is left to Turing's default");
    }

    @Test
    @DisplayName("A weight outside 1 to 100 is a declaration mistake")
    void aWeightOutsideTheRangeIsRefused() {
        DumStructuredField.Builder field = DumStructuredField.builder("title").type(TurSEFieldType.TEXT);
        assertThrows(IllegalArgumentException.class, () -> field.searchWeight(0).build());
        assertThrows(IllegalArgumentException.class, () -> field.searchWeight(101).build());
    }

    @Test
    @DisplayName("The constructor without a weight still builds a field at the default")
    void theEarlierConstructorKeepsTheDefault() {
        DumStructuredField field = new DumStructuredField("company", TurSEFieldType.TEXT, false, false, false,
                null, DumEmbeddingRole.NONE);
        assertNull(field.searchWeight());
        assertNull(field.toAttributeSpec().getSearchWeight());
    }
}

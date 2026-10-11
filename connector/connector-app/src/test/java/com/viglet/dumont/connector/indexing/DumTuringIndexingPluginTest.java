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

package com.viglet.dumont.connector.indexing;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.dumont.connector.commons.domain.DumIndexingSchema;
import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;

@SpringBootTest
class DumTuringIndexingPluginTest {

    @Test
    void testDumTuringIndexingPluginClassExists() {
        // Assert
        assertNotNull(DumTuringIndexingPlugin.class);
    }

    @Test
    void testDumTuringIndexingPluginHasComponentAnnotation() {
        // Assert
        assertNotNull(DumTuringIndexingPlugin.class.getAnnotation(
                org.springframework.stereotype.Component.class));
    }

    /**
     * D63: a manifest that cannot be sent must not abort the run. The SDK wraps every failure —
     * unreachable host included — in {@code TurSNManifestException}, and the plugin reports it and
     * lets the documents flow with the site's current schema.
     */
    @Test
    void provisionDoesNotThrowWhenTuringIsUnreachable() {
        DumTuringIndexingPlugin plugin =
                new DumTuringIndexingPlugin("http://localhost:1/turing", "key");

        assertDoesNotThrow(() -> plugin.provision(new DumIndexingSchema("Sample", Locale.US,
                List.of(new TurSNJobAttributeSpec()))));
    }

    /** D139: the run is told why, so its record does not read clean. */
    @Test
    void provisionAnswersWhyWhenTuringIsUnreachable() {
        DumTuringIndexingPlugin plugin =
                new DumTuringIndexingPlugin("http://localhost:1/turing", "key");

        Optional<String> refused = plugin.provision(new DumIndexingSchema("Sample", Locale.US,
                List.of(new TurSNJobAttributeSpec())));

        assertTrue(refused.isPresent());
        assertTrue(refused.get().startsWith("site 'Sample' was not provisioned"), refused.get());
    }
}

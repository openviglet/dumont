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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.dumont.connector.commons.domain.DumIndexingSchema;
import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.client.sn.manifest.TurSNManifest;
import com.viglet.turing.client.sn.manifest.TurSNManifestCustomFacet;
import com.viglet.turing.client.sn.manifest.TurSNManifestCustomFacetItem;
import com.viglet.turing.client.sn.manifest.TurSNManifestCustomSort;
import com.viglet.turing.client.sn.manifest.TurSNManifestCustomSortItem;
import com.viglet.turing.client.sn.manifest.TurSNManifestException;
import com.viglet.turing.client.sn.manifest.TurSNManifestFieldChange;
import com.viglet.turing.client.sn.manifest.TurSNManifestMigration;
import com.viglet.turing.client.sn.manifest.TurSNManifestResult;

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

    /** D144: the declared custom facets ride the manifest beside the fields. */
    @Test
    void manifestCarriesTheDeclaredCustomFacets() {
        TurSNManifestCustomFacet recent = TurSNManifestCustomFacet.of("postedAt", "postedRecently",
                "Posted", List.of(TurSNManifestCustomFacetItem.since("Last 7 days", "NOW-7DAYS")));

        TurSNManifest manifest = DumTuringIndexingPlugin.manifest(new DumIndexingSchema("Sample",
                Locale.US, List.of(new TurSNJobAttributeSpec()), List.of(recent)));

        assertEquals(List.of(recent), manifest.customFacets());
    }

    /** D144: none declared sends none, so the site keeps the facets its console configured. */
    @Test
    void manifestLeavesTheSiteFacetsAloneWhenNoneIsDeclared() {
        TurSNManifest manifest = DumTuringIndexingPlugin.manifest(new DumIndexingSchema("Sample",
                Locale.US, List.of(new TurSNJobAttributeSpec())));

        assertNull(manifest.customFacets());
    }

    /** D144: the declared custom sorts ride the manifest beside the facets. */
    @Test
    void manifestCarriesTheDeclaredCustomSorts() {
        TurSNManifestCustomSort closingSoonest = TurSNManifestCustomSort.of("closingSoonest",
                List.of(TurSNManifestCustomSortItem.asc("expiresAt").missingLast()));

        TurSNManifest manifest = DumTuringIndexingPlugin.manifest(new DumIndexingSchema("Sample",
                Locale.US, List.of(new TurSNJobAttributeSpec()), List.of(), List.of(closingSoonest)));

        assertEquals(List.of(closingSoonest), manifest.customSorts());
    }

    /** D144: none declared sends none, so the site keeps the sorts its console configured. */
    @Test
    void manifestLeavesTheSiteSortsAloneWhenNoneIsDeclared() {
        TurSNManifest manifest = DumTuringIndexingPlugin.manifest(new DumIndexingSchema("Sample",
                Locale.US, List.of(new TurSNJobAttributeSpec())));

        assertNull(manifest.customSorts());
    }

    private static final TurSNManifestResult PROVISIONED = new TurSNManifestResult("id", "Sample", true,
            List.of("author"), List.of(), List.of(), List.of("en_US"), null);

    private static DumIndexingSchema sample() {
        return new DumIndexingSchema("Sample", Locale.US, List.of(new TurSNJobAttributeSpec()));
    }

    /** D340: a site that does not exist is created on the first instance Turing lists. */
    @Test
    void aMissingSiteIsCreatedOnTheListedSearchEngineInstance() {
        List<TurSNManifest> sent = new ArrayList<>();
        DumTuringIndexingPlugin plugin = new DumTuringIndexingPlugin("http://turing", "key", m -> {
            sent.add(m);
            if (m.seInstanceId() == null) {
                throw new TurSNManifestException("Manifest POST returned 400: seInstanceId is required "
                        + "to create a new site", 400, null);
            }
            return PROVISIONED;
        }, () -> Optional.of("se1"));

        assertEquals(Optional.empty(), plugin.provision(sample()));
        assertEquals(2, sent.size());
        assertEquals("se1", sent.get(1).seInstanceId());
    }

    /** D340: a field held with another cardinality is recreated, naming exactly the fields refused. */
    @Test
    void aFieldWithAnotherCardinalityIsRecreated() {
        List<TurSNManifest> sent = new ArrayList<>();
        DumTuringIndexingPlugin plugin = new DumTuringIndexingPlugin("http://turing", "key", m -> {
            sent.add(m);
            if (m.migrations() == null) {
                throw new TurSNManifestException("Manifest rejected", 409, List.of(
                        new TurSNManifestFieldChange("author", "multiValued", "false", "true", false)));
            }
            return PROVISIONED;
        }, Optional::empty);

        assertEquals(Optional.empty(), plugin.provision(sample()));
        assertEquals(2, sent.size());
        assertEquals(List.of("author"), sent.get(1).migrations().stream()
                .map(TurSNManifestMigration::field).toList());
    }

    /** D340: both answers in one run — create the site, then recreate the template's field. */
    @Test
    void bothAnswersComposeAndEachIsGivenOnce() {
        List<TurSNManifest> sent = new ArrayList<>();
        DumTuringIndexingPlugin plugin = new DumTuringIndexingPlugin("http://turing", "key", m -> {
            sent.add(m);
            if (m.seInstanceId() == null) {
                throw new TurSNManifestException("seInstanceId is required to create a new site", 400, null);
            }
            throw new TurSNManifestException("Manifest rejected", 409, List.of(
                    new TurSNManifestFieldChange("author", "multiValued", "false", "true", false)));
        }, () -> Optional.of("se1"));

        Optional<String> refused = plugin.provision(sample());

        assertEquals(3, sent.size(), "one plain call, one naming the instance, one recreating");
        assertTrue(refused.orElseThrow().contains("needs a migration"), refused.get());
    }

    /** D340: with no instance to create it on, the run is told why rather than retrying. */
    @Test
    void noListedInstanceIsReported() {
        DumTuringIndexingPlugin plugin = new DumTuringIndexingPlugin("http://turing", "key", m -> {
            throw new TurSNManifestException("seInstanceId is required to create a new site", 400, null);
        }, Optional::empty);

        assertTrue(plugin.provision(sample()).orElseThrow().contains("seInstanceId"));
    }

    @Test
    void theFirstEnabledInstanceIsChosenElseTheFirst() {
        assertEquals(Optional.of("b"), DumTuringIndexingPlugin.chooseSeInstance(List.of(
                Map.of("id", "a", "enabled", 0), Map.of("id", "b", "enabled", 1))));
        assertEquals(Optional.of("a"), DumTuringIndexingPlugin.chooseSeInstance(List.of(
                Map.of("id", "a", "enabled", 0))));
        assertEquals(Optional.empty(), DumTuringIndexingPlugin.chooseSeInstance(List.of()));
    }
}

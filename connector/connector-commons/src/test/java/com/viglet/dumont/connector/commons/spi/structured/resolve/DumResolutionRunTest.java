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

package com.viglet.dumont.connector.commons.spi.structured.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;

/** The per-run half of cross-run resolution (roadmap D109), below the plugin. */
class DumResolutionRunTest {

    private static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder()
            .text("code").text("title").build();

    private final DumResolutionIndex index = new DumInMemoryResolutionIndex();
    private final DumCanonicalKey key = DumCanonicalKey.field("code");

    private static DumStructuredRecord rec(String id, String code, String title) {
        return DumStructuredRecord.builder(id, SCHEMA).set("code", code).set("title", title).build();
    }

    private DumResolutionRun run(String source) {
        return new DumResolutionRun(key, index, new NamedSource(source));
    }

    @Test
    void aRecordWhoseKeyChangedLeavesAHeldMemberInItsOldGroup() {
        run("a").resolve(rec("a-1", "CS101", "Algorithms"));
        run("b").resolve(rec("b-1", "CS101", "Algorithms II"));

        run("a").resolve(rec("a-1", "CS102", "Algorithms"));

        assertEquals(Optional.of("cs102"), index.canonicalIdOf("a", "a-1"));
        List<DumResolutionIndex.Member> old = index.members("cs101");
        assertEquals(2, old.size(), "a's row stays in the old group, held, so a keeps publishing it");
        assertFalse(old.get(0).live());
        assertTrue(old.get(1).live());
    }

    @Test
    void anExpiredMemberKeepsItsDocumentWhileAnotherIsLive() {
        run("a").resolve(rec("a-1", "CS101", "Algorithms"));
        run("b").resolve(rec("b-1", "CS101", "Algorithms II"));

        DumResolutionRun.Expiry expiry = run("a").expire(List.of("a-1", "a-unkeyed"));

        assertEquals(List.of("a-unkeyed"), expiry.deindex(), "only the unresolved id is deindexed as is");
        assertEquals(1, expiry.republish().size());
        assertEquals("Algorithms II", expiry.republish().get(0).values().get("title"));
    }

    @Test
    void anExpiredLastMemberDeindexesTheCanonicalDocument() {
        run("a").resolve(rec("a-1", "CS101", "Algorithms"));

        DumResolutionRun.Expiry expiry = run("a").expire(List.of("a-1"));

        assertEquals(List.of("cs101"), expiry.deindex());
        assertTrue(expiry.republish().isEmpty());
        assertTrue(index.members("cs101").isEmpty(), "the dead group's rows are dropped");
    }

    @Test
    void unchangedRecordsStayAliveUnderTheirCanonicalIdAndAreNotHeld() {
        run("a").resolve(rec("a-1", "CS101", "Algorithms"));

        DumResolutionRun second = run("a");
        assertEquals(List.of("cs101", "a-9"), second.documentIds(List.of("a-1", "a-9")));
        assertTrue(second.closeComplete().isEmpty());
        assertTrue(index.members("cs101").get(0).live(), "an unchanged record was seen, not held");
    }

    /** A source that only has a name: the run reads nothing else from it. */
    private record NamedSource(String name) implements DumStructuredSource {

        @Override
        public String getSourceName() {
            return name;
        }

        @Override
        public String getProviderName() {
            return "TEST";
        }

        @Override
        public Collection<String> getSiteNames() {
            return List.of();
        }

        @Override
        public DumStructuredSchema schema() {
            return SCHEMA;
        }

        @Override
        public List<String> discover() {
            return List.of();
        }

        @Override
        public Optional<DumStructuredRecord> extract(String contentId) {
            return Optional.empty();
        }
    }
}

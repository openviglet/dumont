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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnrichmentChain;

/** What aborted a run reaches its record, and survives a finish that fails too (roadmap D126). */
class DumAbortCauseTest {

    private static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder().text("title").build();
    private static final IllegalStateException CAUSE = new IllegalStateException("partner down");

    @Test
    void theCauseIsRecordedNextToTheReason() {
        Recording context = new Recording(null);

        new Plugin(context).indexAll("jobs");

        assertEquals(List.of("aborted by an exception", "cause: IllegalStateException: partner down"),
                context.notes);
    }

    @Test
    void aFailingFinishCarriesTheCauseInsteadOfReplacingIt() {
        IllegalStateException finishFailure = new IllegalStateException("Column TENANT_ID not found");
        Recording context = new Recording(finishFailure);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> new Plugin(context).indexAll("jobs"));

        assertSame(finishFailure, thrown);
        assertSame(CAUSE, thrown.getSuppressed()[0]);
        assertEquals("cause: IllegalStateException: partner down", context.notes.get(1));
    }

    @Test
    void aCauseWithNoMessageIsNamedByItsClass() {
        assertEquals("cause: NullPointerException",
                DumStructuredSourcePlugin.causeNote(new NullPointerException()));
    }

    private static final class Source implements DumStructuredSource {
        @Override
        public String getSourceName() {
            return "jobs";
        }

        @Override
        public String getProviderName() {
            return "JOBS";
        }

        @Override
        public Collection<String> getSiteNames() {
            return List.of("Sample");
        }

        @Override
        public DumStructuredSchema schema() {
            return SCHEMA;
        }

        @Override
        public List<String> discover() {
            throw CAUSE;
        }

        @Override
        public Optional<DumStructuredRecord> extract(String contentId) {
            return Optional.empty();
        }
    }

    private static final class Plugin extends DumStructuredSourcePlugin implements DumStructuredSourceProvider {
        private final DumStructuredSource source = new Source();

        Plugin(DumConnectorContext context) {
            super(context, DumEnrichmentChain.empty());
        }

        @Override
        protected DumStructuredSourceProvider provider() {
            return this;
        }

        @Override
        public String getProviderName() {
            return "JOBS";
        }

        @Override
        public Collection<String> sourceNames() {
            return List.of(source.getSourceName());
        }

        @Override
        public Optional<DumStructuredSource> resolve(String sourceName) {
            return source.getSourceName().equals(sourceName) ? Optional.of(source) : Optional.empty();
        }
    }

    private static final class Recording implements DumConnectorContext {
        private final RuntimeException finishFailure;
        private final List<String> notes = new ArrayList<>();

        Recording(RuntimeException finishFailure) {
            this.finishFailure = finishFailure;
        }

        @Override
        public void recordRunOutcome(DumConnectorSession session, boolean partial, List<String> runNotes) {
            notes.addAll(runNotes);
        }

        @Override
        public boolean addJobItem(DumJobItemWithSession item) {
            return true;
        }

        @Override
        public void finishIndexing(DumConnectorSession session, boolean standalone) {
            if (finishFailure != null) {
                throw finishFailure;
            }
        }

        @Override
        public List<DumConnectorIndexing> getIndexingItem(String objectId, String source, String provider) {
            return List.of();
        }

        @Override
        public List<String> getObjectIdByDependency(String source, String provider,
                List<String> dependenciesObjectIdList) {
            return List.of();
        }
    }
}

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

package com.viglet.dumont.connector.commons.spi.structured.oaipmh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourcePlugin;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourceProvider;

/** Harvesting an OAI-PMH 2.0 repository as a structured source (roadmap D315). */
class DumOaiPmhSourceTest {

    private static final String BASE = "https://repo.example/oai/request";

    private final List<String> urls = new ArrayList<>();

    @Test
    void followsTheResumptionTokenAcrossTwoPages() {
        DumOaiPmhSource source = source(pages(
                listRecords("2026-11-01T10:00:00Z", "tok/1", record("oai:repo:1", "One"),
                        record("oai:repo:2", "Two")),
                listRecords("2026-11-01T10:00:05Z", "", record("oai:repo:3", "Three"))),
                new DumInMemoryOaiPmhState(), "col_7");

        assertEquals(List.of("oai:repo:1", "oai:repo:2", "oai:repo:3"), source.discover());
        assertTrue(source.discoveryComplete());
        assertFalse(source.incremental(), "a first harvest is a full one");
        assertEquals(List.of(BASE + "?verb=ListRecords&metadataPrefix=oai_dc&set=col_7",
                BASE + "?verb=ListRecords&resumptionToken=tok%2F1"), urls);
    }

    @Test
    void aFailureWithNoMessageIsNamedByItsType() {
        // What the JDK client throws when nothing listens on the repository's port.
        DumOaiPmhSource source = source(url -> {
            throw new java.net.ConnectException();
        }, new DumInMemoryOaiPmhState(), null);

        assertEquals(List.of(), source.discover());
        assertFalse(source.discoveryComplete());
        assertEquals(List.of("stopped: call 1 failed (ConnectException)"), source.runNotes());
    }

    @Test
    void noRecordsMatchIsAnEmptyCompleteHarvest() {
        DumInMemoryOaiPmhState state = new DumInMemoryOaiPmhState();
        DumOaiPmhSource source = source(pages(error("2026-11-02T08:00:00Z", "noRecordsMatch",
                "No records match")), state, null);

        assertEquals(List.of(), source.discover());
        assertTrue(source.discoveryComplete());
        assertEquals(List.of(), source.runNotes());
        assertEquals(Optional.of("2026-11-02T08:00:00Z"), state.watermark());
    }

    @Test
    void aDeletedHeaderIsGoneNotDiscovered() {
        DumOaiPmhSource source = source(pages(listRecords("2026-11-01T10:00:00Z", null,
                record("oai:repo:1", "One"), deleted("oai:repo:2"))), new DumInMemoryOaiPmhState(), null);

        assertEquals(List.of("oai:repo:1"), source.discover());
        assertEquals(List.of("oai:repo:2"), source.goneIds());
        assertTrue(source.extract("oai:repo:2").isEmpty());
    }

    @Test
    void aLaterDeletionOfTheSameRecordWins() {
        DumOaiPmhSource source = source(pages(
                listRecords("2026-11-01T10:00:00Z", "t", record("oai:repo:1", "One")),
                listRecords("2026-11-01T10:00:01Z", null, deleted("oai:repo:1"))),
                new DumInMemoryOaiPmhState(), null);

        assertEquals(List.of(), source.discover());
        assertEquals(List.of("oai:repo:1"), source.goneIds());
    }

    @Test
    void theNextRunAsksFromTheWatermarkWhichOnlyACompleteHarvestAdvances() {
        DumInMemoryOaiPmhState state = new DumInMemoryOaiPmhState();

        DumOaiPmhSource first = source(pages(listRecords("2026-11-01T10:00:00Z", null,
                record("oai:repo:1", "One"))), state, null);
        first.discover();
        assertEquals(Optional.of("2026-11-01T10:00:00Z"), state.watermark());

        // The second run fails on its second page: what it saw is kept, the watermark is not moved.
        urls.clear();
        DumOaiPmhSource second = source(url -> {
            if (urls.size() == 2) {
                throw new IOException("HTTP 500 from " + BASE);
            }
            return listRecords("2026-11-03T09:00:00Z", "t", record("oai:repo:2", "Two"));
        }, state, null);
        assertEquals(List.of("oai:repo:2"), second.discover());
        assertEquals(BASE + "?verb=ListRecords&metadataPrefix=oai_dc&from=2026-11-01", urls.getFirst());
        assertTrue(second.incremental(), "a run that sent from is incremental and never sweeps");
        assertFalse(second.discoveryComplete());
        assertEquals(List.of("stopped: call 2 failed (HTTP 500 from " + BASE + ")"), second.runNotes());
        assertEquals(Optional.of("2026-11-01T10:00:00Z"), state.watermark());

        // The third run asks again from the old watermark, completes, and moves it.
        urls.clear();
        DumOaiPmhSource third = source(pages(listRecords("2026-11-04T07:30:00Z", null,
                record("oai:repo:2", "Two"))), state, null);
        third.discover();
        assertTrue(urls.getFirst().endsWith("&from=2026-11-01"));
        assertEquals(Optional.of("2026-11-04T07:30:00Z"), state.watermark());
    }

    @Test
    void aSourceThatIsNotIncrementalHarvestsInFullEveryRun() {
        DumInMemoryOaiPmhState state = new DumInMemoryOaiPmhState();
        state.watermark("2026-11-01T10:00:00Z");
        DumOaiPmhSource source = DumOaiPmhSource.builder(BASE, recording(pages(listRecords(
                "2026-11-02T10:00:00Z", null, record("oai:repo:1", "One"))))).sourceName("repo")
                .state(state).incremental(false).build();

        source.discover();
        assertEquals(BASE + "?verb=ListRecords&metadataPrefix=oai_dc", urls.getFirst());
        assertFalse(source.incremental());
    }

    @Test
    void anOaiErrorOtherThanNoRecordsMatchLeavesTheHarvestIncomplete() {
        DumInMemoryOaiPmhState state = new DumInMemoryOaiPmhState();
        DumOaiPmhSource source = source(pages(error("2026-11-02T08:00:00Z", "badArgument",
                "Illegal argument 'from'")), state, null);

        assertEquals(List.of(), source.discover());
        assertFalse(source.discoveryComplete());
        assertEquals(List.of("stopped: the repository answered badArgument: Illegal argument 'from'"),
                source.runNotes());
        assertTrue(state.watermark().isEmpty(), "a failed harvest sets no watermark");
    }

    @Test
    void aRepeatedResumptionTokenStopsTheHarvest() {
        DumOaiPmhSource source = source(url -> listRecords("2026-11-01T10:00:00Z", "same",
                record("oai:repo:" + urls.size(), "Item")), new DumInMemoryOaiPmhState(), null);

        assertEquals(2, source.discover().size());
        assertFalse(source.discoveryComplete());
        assertEquals(2, source.calls());
    }

    @Test
    void aDoctypeIsRefusedSoNoEntityIsEverResolved() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE OAI-PMH [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/">
                  <responseDate>2026-11-01T10:00:00Z</responseDate>
                  <ListRecords>%s</ListRecords>
                </OAI-PMH>
                """.formatted(record("oai:repo:1", "&xxe;"));
        DumOaiPmhSource source = source(pages(xxe), new DumInMemoryOaiPmhState(), null);

        assertEquals(List.of(), source.discover());
        assertFalse(source.discoveryComplete());
        assertEquals(List.of("stopped: call 1 returned no OAI-PMH response"), source.runNotes());
        IOException e = assertThrows(IOException.class, () -> DumOaiPmhResponse.parse(xxe));
        assertTrue(e.getMessage().contains("DOCTYPE"), e.getMessage());
    }

    @Test
    void mapsDublinCoreWithoutInventingValues() {
        String rec = """
                <record><header><identifier>oai:repo:9</identifier><datestamp>2026-10-30</datestamp></header>
                <metadata><oai_dc:dc xmlns:oai_dc="http://www.openarchives.org/OAI/2.0/oai_dc/"
                    xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <dc:title>Soil carbon in the Cerrado</dc:title>
                  <dc:creator>Silva, Ana</dc:creator><dc:creator>Souza, Bruno</dc:creator>
                  <dc:subject>Soil</dc:subject><dc:subject>Carbon</dc:subject>
                  <dc:description>First paragraph.</dc:description><dc:description>Second.</dc:description>
                  <dc:date>2021-03</dc:date>
                  <dc:type>Article</dc:type>
                  <dc:publisher>Repo Press</dc:publisher>
                  <dc:language>por</dc:language>
                  <dc:identifier>oai:repo:9</dc:identifier>
                  <dc:identifier>https://repo.example/items/9</dc:identifier>
                  <dc:identifier>https://doi.org/10.1234/abc</dc:identifier>
                  <dc:relation>https://repo.example/collections/1</dc:relation>
                </oai_dc:dc></metadata></record>
                """;
        DumOaiPmhSource source = source(pages(listRecords("2026-11-01T10:00:00Z", null, rec)),
                new DumInMemoryOaiPmhState(), null);
        source.discover();

        DumStructuredRecord r = source.extract("oai:repo:9").orElseThrow();
        assertEquals("Soil carbon in the Cerrado", r.values().get("title"));
        assertEquals("First paragraph.\n\nSecond.", r.values().get("text"));
        // Turing's own names, the ones a WordPress post sends (D326).
        assertEquals("First paragraph.", r.values().get("abstract"));
        assertEquals(List.of("Silva, Ana", "Souza, Bruno"), r.values().get("author"));
        assertEquals(List.of("Soil", "Carbon"), r.values().get("subjects"));
        assertEquals("2021-03-01T00:00:00Z", r.values().get("publication_date"));
        assertNull(r.values().get("dateText"));
        assertEquals(List.of("Article"), r.values().get("type"));
        assertEquals(List.of("Repo Press"), r.values().get("publisher"));
        assertEquals(List.of("por"), r.values().get("language"));
        assertEquals("https://doi.org/10.1234/abc", r.values().get("url"));
        assertEquals(List.of("https://repo.example/collections/1"), r.values().get("relations"));
        assertEquals("oai-pmh", r.provenance().get("author").strategy());
        assertEquals("dc:creator", r.provenance().get("author").selector());
        assertTrue(r.missingMandatoryFields().isEmpty());
    }

    @Test
    void anUnreadableDateIsKeptAsWrittenAndARecordWithNoUrlHasNone() {
        DumOaiPmhSource source = source(pages(listRecords("2026-11-01T10:00:00Z", null,
                record("oai:repo:1", "One", "<dc:date>circa 1890</dc:date><dc:identifier>oai:repo:1</dc:identifier>"))),
                new DumInMemoryOaiPmhState(), null);
        source.discover();

        DumStructuredRecord r = source.extract("oai:repo:1").orElseThrow();
        assertNull(r.values().get("publication_date"));
        assertEquals("circa 1890", r.values().get("dateText"));
        assertNull(r.values().get("url"), "a URL is never built from the OAI identifier");
    }

    @Test
    void theUrlRulePrefersAHandleOrDoiOverAPlainUrl() {
        assertEquals(Optional.of("http://hdl.handle.net/123/456"), DumOaiPmhSource.url(List.of(
                "oai:repo:1", "https://repo.example/items/1", "http://hdl.handle.net/123/456"), "repo.example"));
        assertEquals(Optional.of("https://repo.example/handle/123/456"), DumOaiPmhSource.url(List.of(
                "https://repo.example/bitstream/1.pdf", "https://repo.example/handle/123/456"), "repo.example"));
        assertEquals(Optional.of("https://repo.example/items/1"), DumOaiPmhSource.url(List.of(
                "10.1234/abc", "https://repo.example/items/1", "https://repo.example/items/2"), "repo.example"));
        assertEquals(Optional.empty(), DumOaiPmhSource.url(List.of("oai:repo:1", "ISBN 978-0"), "repo.example"));
    }

    @Test
    void theUrlRuleNeverPicksAnAddressAVisitorCannotOpen() {
        assertEquals(Optional.of("https://repo.example/items/1"), DumOaiPmhSource.url(List.of(
                "http://10.17.50.146:4000/handle/123/4970", "https://repo.example/items/1"), "repo.example"),
                "a private IP's handle link loses to a public URL");
        assertEquals(Optional.of("https://repo.example/items/1"), DumOaiPmhSource.url(List.of(
                "https://mirror.example/items/1", "https://repo.example/items/1"), "repo.example"),
                "the base URL's own host comes before another");
        assertEquals(Optional.empty(), DumOaiPmhSource.url(List.of("http://127.0.0.1/a", "http://intranet/b",
                "http://[fd00::1]/c", "http://192.168.0.2/d", "http://169.254.1.1/e"), "repo.example"));
        assertTrue(DumOaiPmhSource.isPublic("https://8.8.8.8/x"));
        assertTrue(DumOaiPmhSource.isPublic("https://[2001:db8::1]/x"));
        assertFalse(DumOaiPmhSource.isPublic("http://172.20.0.1/x"));
        assertFalse(DumOaiPmhSource.isPublic("http://repo.localhost/x"));
    }

    @Test
    void readsYearsMonthsDaysAndInstantsAsDates() {
        assertEquals(Optional.of("1998-01-01T00:00:00Z"), DumOaiPmhSource.isoDate("1998"));
        assertEquals(Optional.of("1998-07-01T00:00:00Z"), DumOaiPmhSource.isoDate("1998-07"));
        assertEquals(Optional.of("1998-07-15T00:00:00Z"), DumOaiPmhSource.isoDate("1998-07-15"));
        assertEquals(Optional.of("2019-05-10T12:34:56Z"), DumOaiPmhSource.isoDate("2019-05-10T12:34:56Z"));
        assertEquals(Optional.empty(), DumOaiPmhSource.isoDate("1998-13"));
        assertEquals(Optional.empty(), DumOaiPmhSource.isoDate("[s.d.]"));
    }

    @Test
    void theRunDeindexesWhatTheRepositoryDeleted() {
        // The plugin half of D315: a deleted header leaves the index through goneIds (D114).
        DumInMemoryOaiPmhState state = new DumInMemoryOaiPmhState();
        state.watermark("2026-11-01T10:00:00Z");
        DumOaiPmhSource source = source(pages(listRecords("2026-11-02T10:00:00Z", null,
                record("oai:repo:1", "One"), deleted("oai:repo:2"))), state, null);
        CapturingContext context = new CapturingContext();

        new OaiPmhPlugin(context, source).indexAll("repo");

        assertEquals(1, context.items.size());
        assertEquals(List.of("oai:repo:2"), context.deindexed);
        assertTrue(context.standalone, "an incremental harvest never sweeps what it was not sent");
    }

    @Test
    void fullTextIsOffUnlessAsked() {
        DumOaiPmhSource source = source(pages(listRecords("2026-11-01T10:00:00Z", null, record("oai:repo:1",
                "One", "<dc:description>Abstract.</dc:description>"
                        + "<dc:identifier>https://repo.example/bitstream/1/paper.pdf</dc:identifier>"))),
                new DumInMemoryOaiPmhState(), null);
        source.discover();

        assertEquals("Abstract.", source.extract("oai:repo:1").orElseThrow().values().get("text"));
    }

    @Test
    void fullTextAppendsASameHostPdfAfterTheDescription() {
        List<String> fetched = new ArrayList<>();
        DumOaiPmhSource source = DumOaiPmhSource.builder(BASE, recording(pages(listRecords(
                "2026-11-01T10:00:00Z", null, record("oai:repo:1", "One",
                        "<dc:description>Abstract.</dc:description>"
                                + "<dc:identifier>https://repo.example/items/1</dc:identifier>"
                                + "<dc:identifier>https://mirror.example/paper.pdf</dc:identifier>"
                                + "<dc:relation>https://REPO.example/bitstream/1/paper.PDF</dc:relation>")))))
                .sourceName("repo").fullText((url, host) -> {
                    fetched.add(url + " @ " + host);
                    return url.endsWith(".PDF") ? Optional.of("Body of the paper.") : Optional.empty();
                }).build();
        source.discover();

        DumStructuredRecord r = source.extract("oai:repo:1").orElseThrow();
        assertEquals("Abstract.\n\nBody of the paper.", r.values().get("text"));
        assertEquals(List.of("https://REPO.example/bitstream/1/paper.PDF @ repo.example"), fetched,
                "the .pdf on the repository's host is tried first; the other host never");
        assertEquals("dc:description + fulltext:https://REPO.example/bitstream/1/paper.PDF",
                r.provenance().get("text").selector());
    }

    @Test
    void fullTextFallsBackToTheOtherSameHostUrlsAndSurvivesAFailure() {
        List<String> fetched = new ArrayList<>();
        DumOaiPmhSource source = DumOaiPmhSource.builder(BASE, recording(pages(listRecords(
                "2026-11-01T10:00:00Z", null, record("oai:repo:1", "One",
                        "<dc:identifier>https://repo.example/bitstream/1/broken.pdf</dc:identifier>"
                                + "<dc:identifier>https://repo.example/items/1</dc:identifier>"
                                + "<dc:relation>https://repo.example/download/1</dc:relation>")))))
                .sourceName("repo").fullText((url, host) -> {
                    fetched.add(url);
                    if (url.endsWith("broken.pdf")) {
                        throw new IOException("HTTP 500");
                    }
                    return url.endsWith("/download/1") ? Optional.of("Served as application/pdf.")
                            : Optional.empty();
                }).build();
        source.discover();

        DumStructuredRecord r = source.extract("oai:repo:1").orElseThrow();
        assertEquals("Served as application/pdf.", r.values().get("text"));
        assertEquals(List.of("https://repo.example/bitstream/1/broken.pdf", "https://repo.example/items/1",
                "https://repo.example/download/1"), fetched);
    }

    @Test
    void fullTextCandidatesAreSameHostHttpPdfFirstAndBounded() {
        DumOaiPmhResponse.Item item = new DumOaiPmhResponse.Item("oai:repo:1", null, false, java.util.Map.of(
                "identifier", List.of("oai:repo:1", "https://repo.example/a", "ftp://repo.example/b.pdf",
                        "https://repo.example.evil/c.pdf", "https://repo.example/d.pdf"),
                "relation", List.of("https://repo.example/e", "https://repo.example/f", "not a url")));

        assertEquals(List.of("https://repo.example/d.pdf", "https://repo.example/a", "https://repo.example/e"),
                DumOaiPmhSource.fullTextCandidates(item, "repo.example"));
    }

    @Test
    void refusesABaseUrlThatIsNotHttp() {
        assertThrows(IllegalArgumentException.class,
                () -> DumOaiPmhSource.builder("ftp://repo.example/oai", url -> "").sourceName("r").build());
        assertThrows(IllegalArgumentException.class,
                () -> DumOaiPmhSource.builder(BASE, url -> "").build());
    }

    // --- helpers -------------------------------------------------------------------------------

    private DumOaiPmhSource source(DumOaiPmhFetcher responses, DumOaiPmhState state, String set) {
        return DumOaiPmhSource.builder(BASE, recording(responses)).sourceName("repo")
                .siteNames(List.of("site")).state(state).set(set).build();
    }

    private DumOaiPmhFetcher recording(DumOaiPmhFetcher responses) {
        return url -> {
            urls.add(url);
            return responses.fetch(url);
        };
    }

    /** Answers the n-th call with the n-th body; past the last, noRecordsMatch. */
    private DumOaiPmhFetcher pages(String... bodies) {
        return url -> {
            int index = urls.size() - 1;
            return index < bodies.length ? bodies[index]
                    : error("2026-11-01T10:00:00Z", "noRecordsMatch", "none");
        };
    }

    private static String listRecords(String responseDate, String token, String... records) {
        String resumption = token == null ? "" : "<resumptionToken>%s</resumptionToken>".formatted(token);
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/">
                  <responseDate>%s</responseDate>
                  <request verb="ListRecords">%s</request>
                  <ListRecords>%s%s</ListRecords>
                </OAI-PMH>
                """.formatted(responseDate, BASE, String.join("", records), resumption);
    }

    private static String error(String responseDate, String code, String message) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/">
                  <responseDate>%s</responseDate>
                  <request verb="ListRecords">%s</request>
                  <error code="%s">%s</error>
                </OAI-PMH>
                """.formatted(responseDate, BASE, code, message);
    }

    private static String record(String identifier, String title) {
        return record(identifier, title, "");
    }

    private static String record(String identifier, String title, String extraDc) {
        return """
                <record><header><identifier>%s</identifier><datestamp>2026-10-30</datestamp></header>
                <metadata><oai_dc:dc xmlns:oai_dc="http://www.openarchives.org/OAI/2.0/oai_dc/"
                    xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>%s</dc:title>%s</oai_dc:dc></metadata>
                </record>
                """.formatted(identifier, title, extraDc);
    }

    private static String deleted(String identifier) {
        return """
                <record><header status="deleted"><identifier>%s</identifier>
                <datestamp>2026-10-31</datestamp></header></record>
                """.formatted(identifier);
    }

    private static final class OaiPmhPlugin extends DumStructuredSourcePlugin {
        private final DumStructuredSource source;

        OaiPmhPlugin(DumConnectorContext context, DumStructuredSource source) {
            super(context);
            this.source = source;
        }

        @Override
        protected DumStructuredSourceProvider provider() {
            return new DumStructuredSourceProvider() {
                @Override
                public String getProviderName() {
                    return source.getProviderName();
                }

                @Override
                public Collection<String> sourceNames() {
                    return List.of(source.getSourceName());
                }

                @Override
                public Optional<DumStructuredSource> resolve(String sourceName) {
                    return Optional.of(source).filter(s -> s.getSourceName().equals(sourceName));
                }
            };
        }
    }

    private static final class CapturingContext implements DumConnectorContext {
        private final List<DumJobItemWithSession> items = new ArrayList<>();
        private final List<String> deindexed = new ArrayList<>();
        private boolean standalone;

        @Override
        public int deIndexById(DumConnectorSession session, Collection<String> objectIds,
                List<String> sites) {
            deindexed.addAll(objectIds);
            return objectIds.size();
        }

        @Override
        public boolean addJobItem(DumJobItemWithSession dumJobItemWithSession) {
            return items.add(dumJobItemWithSession);
        }

        @Override
        public void finishIndexing(DumConnectorSession session, boolean standalone) {
            this.standalone = standalone;
        }

        @Override
        public List<DumConnectorIndexing> getIndexingItem(String objectId, String source,
                String provider) {
            return List.of();
        }

        @Override
        public List<String> getObjectIdByDependency(String source, String provider,
                List<String> dependenciesObjectIdList) {
            return List.of();
        }
    }
}

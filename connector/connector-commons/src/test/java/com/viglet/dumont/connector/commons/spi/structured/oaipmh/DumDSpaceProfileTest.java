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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;

/** The DSpace profile of an OAI-PMH source (roadmap D314), over canned dim XML and REST JSON. */
class DumDSpaceProfileTest {

    private static final String OAI = "https://repo.example/server-x/oai/request";
    /** Deliberately not {@code /server/api}: the profile must use what it is given. */
    private static final String REST = "https://repo.example/server-x/api";
    private static final String ID = "oai:repo.example:123456789/7";

    private final List<String> oaiUrls = new ArrayList<>();
    private final FakeRest rest = new FakeRest();

    @Test
    void mapsDimKeepingAuthorsAndAdvisorsApart() {
        rest.publicItem("123456789/7", "u7", bundles());
        DumStructuredRecord r = harvest(true, null).extract(ID).orElseThrow();

        assertEquals("Solos do Cerrado", r.values().get("title"));
        assertEquals(List.of("Silva, Ana", "Souza, Bruno"), r.values().get("authors"));
        assertEquals(List.of("Lima, Carla", "Rocha, Davi"), r.values().get("advisors"));
        assertEquals("2021-03-01T00:00:00Z", r.values().get("date"));
        assertEquals("Resumo da tese.", r.values().get("text"));
        assertEquals(List.of("Tese"), r.values().get("type"));
        assertEquals(List.of("Carbono", "CNPQ::CIENCIAS AGRARIAS"), r.values().get("subjects"));
        assertEquals(List.of("por"), r.values().get("language"));
        assertEquals(List.of("Universidade Exemplo"), r.values().get("publisher"));
        assertEquals("https://repo.example/handle/123456789/7", r.values().get("url"));
        assertEquals("dim:dc.contributor.advisor", r.provenance().get("advisors").selector());
        assertTrue(r.missingMandatoryFields().isEmpty());
    }

    @Test
    void neverInventsWhatTheRecordDoesNotSay() {
        rest.status.put(REST + "/pid/find?id=123456789/7", 404);
        String bare = dimRecord(ID, List.of(), "<dim:field mdschema=\"dc\" element=\"title\">Só título</dim:field>"
                + "<dim:field mdschema=\"dc\" element=\"date\" qualifier=\"issued\">[s.d.]</dim:field>");
        DumStructuredRecord r = harvest(listRecords(bare), true, null).extract(ID).orElseThrow();

        assertEquals("Só título", r.values().get("title"));
        assertNull(r.values().get("date"));
        assertEquals("[s.d.]", r.values().get("dateText"));
        for (String absent : List.of("authors", "advisors", "url", "text", "community", "collection", "image")) {
            assertNull(r.values().get(absent), absent);
        }
    }

    @Test
    void communityAndCollectionAreFacetsByNameReadOncePerRun() {
        rest.publicItem("123456789/7", "u7", bundles());
        rest.publicItem("123456789/8", "u8", "{\"_embedded\":{\"bundles\":[]}}");
        String second = dimRecord("oai:repo.example:123456789/8", List.of("com_123456789_1", "col_123456789_9"),
                "<dim:field mdschema=\"dc\" element=\"title\">Outro</dim:field>");
        DumOaiPmhSource source = harvest(listRecords(record(), second), true, null);

        DumStructuredRecord r = source.extract(ID).orElseThrow();
        assertEquals(List.of("Programa de Pós-Graduação", "Ciências Agrárias"), r.values().get("community"));
        assertEquals(List.of("Teses"), r.values().get("collection"), "an unnamed set is left out, not indexed as its spec");
        source.extract("oai:repo.example:123456789/8");

        assertEquals(1, rest.calls("/core/communities?page=0"));
        assertEquals(1, rest.calls("/core/communities?page=1"), "the second page is read");
        assertEquals(1, rest.calls("/core/collections?page=0"));
    }

    @Test
    void theTextBundleIsPreferredOverThePdf() {
        rest.publicItem("123456789/7", "u7", bundles());
        rest.text.put(REST + "/core/bitstreams/txt1/content", Optional.of("Texto do DSpace."));
        rest.text.put(REST + "/core/bitstreams/pdf1/content", Optional.of("Texto do PDF."));

        DumStructuredRecord r = harvest(true, null).extract(ID).orElseThrow();

        assertEquals("Resumo da tese.\n\nTexto do DSpace.", r.values().get("text"));
        assertTrue(r.provenance().get("text").selector().contains("fulltext:TEXT:"));
        assertEquals(List.of(REST + "/core/bitstreams/txt1/content"), rest.textCalls);
    }

    @Test
    void aPdfIsReadWhenThereIsNoTextBundle() {
        rest.publicItem("123456789/7", "u7", """
                {"_embedded":{"bundles":[
                  {"name":"ORIGINAL","uuid":"b1","_embedded":{"bitstreams":{"_embedded":{"bitstreams":[
                    {"uuid":"lic","name":"license.txt"},{"uuid":"pdf1","name":"Tese.PDF"}]}}}}]}}
                """);
        rest.text.put(REST + "/core/bitstreams/pdf1/content", Optional.of("Texto do PDF."));

        DumStructuredRecord r = harvest(true, null).extract(ID).orElseThrow();

        assertEquals("Resumo da tese.\n\nTexto do PDF.", r.values().get("text"));
        assertTrue(r.provenance().get("text").selector().contains("fulltext:ORIGINAL:"));
        assertEquals(List.of(REST + "/core/bitstreams/pdf1/content"), rest.textCalls, "only a PDF is read");
    }

    @Test
    void theThumbnailIsTheImageAndBitstreamsAreFetchedWhenNotEmbedded() {
        rest.publicItem("123456789/7", "u7", """
                {"_embedded":{"bundles":[{"name":"THUMBNAIL","uuid":"bt"}]}}
                """);
        rest.json.put(REST + "/core/bundles/bt/bitstreams",
                "{\"_embedded\":{\"bitstreams\":[{\"uuid\":\"th1\",\"name\":\"capa.jpg\"},{\"uuid\":\"th2\"}]}}");

        DumStructuredRecord r = harvest(false, null).extract(ID).orElseThrow();

        assertEquals(REST + "/core/bitstreams/th1/content", r.values().get("image"));
    }

    @Test
    void fullTextOffReadsNoBitstream() {
        rest.publicItem("123456789/7", "u7", bundles());
        DumStructuredRecord r = harvest(false, null).extract(ID).orElseThrow();

        assertEquals("Resumo da tese.", r.values().get("text"));
        assertEquals(REST + "/core/bitstreams/th1/content", r.values().get("image"));
        assertEquals(List.of(), rest.textCalls);
    }

    @Test
    void aWithdrawnOrHiddenOrPrivateItemIsGoneNeverIndexed() {
        rest.json.put(REST + "/pid/find?id=123456789/7", "{\"uuid\":\"u7\",\"withdrawn\":true,\"discoverable\":true}");
        DumOaiPmhSource withdrawn = harvest(true, null);
        assertTrue(withdrawn.extract(ID).isEmpty());
        assertEquals(List.of(ID), withdrawn.goneIds());

        rest.json.put(REST + "/pid/find?id=123456789/7", "{\"uuid\":\"u7\",\"withdrawn\":false,\"discoverable\":false}");
        DumOaiPmhSource hidden = harvest(true, null);
        assertTrue(hidden.extract(ID).isEmpty());
        assertEquals(List.of(ID), hidden.goneIds());

        rest.json.remove(REST + "/pid/find?id=123456789/7");
        rest.status.put(REST + "/pid/find?id=123456789/7", 401);
        DumOaiPmhSource closed = harvest(true, null);
        assertTrue(closed.extract(ID).isEmpty());
        assertEquals(List.of(ID), closed.goneIds());
        assertEquals(List.of(), rest.textCalls, "nothing of a gone item is downloaded");
    }

    @Test
    void anUnreadableBitstreamGivesNoTextButTheItemIsIndexed() {
        rest.publicItem("123456789/7", "u7", bundles());
        rest.failing.put(REST + "/core/bitstreams/txt1/content", "HTTP 403 from " + REST);
        rest.failing.put(REST + "/core/bitstreams/pdf1/content", "HTTP 401 from " + REST);

        DumOaiPmhSource source = harvest(true, null);
        DumStructuredRecord r = source.extract(ID).orElseThrow();

        assertEquals("Resumo da tese.", r.values().get("text"));
        assertEquals(List.of(), source.goneIds());
    }

    @Test
    void anItemRestCannotFindIsIndexedFromItsMetadata() {
        rest.status.put(REST + "/pid/find?id=123456789/7", 404);
        DumOaiPmhSource source = harvest(true, null);

        DumStructuredRecord r = source.extract(ID).orElseThrow();
        assertEquals("Solos do Cerrado", r.values().get("title"));
        assertNull(r.values().get("image"));
        assertEquals(List.of(), source.goneIds());
    }

    @Test
    void everyRecordCarriesTheSiteKey() {
        rest.publicItem("123456789/7", "u7", bundles());
        assertEquals("repo.example", harvest(false, null).extract(ID).orElseThrow().values().get("site_key"),
                "the default is the OAI-PMH base URL's host");
        assertEquals("acervo", harvest(false, " acervo ").extract(ID).orElseThrow().values().get("site_key"));
    }

    @Test
    void theRestBaseIsTheConfiguredOneAndTheHarvestAsksForDim() {
        rest.publicItem("123456789/7", "u7", bundles());
        DumOaiPmhSource source = DumOaiPmhSource.builder(OAI, recording(listRecords(record())))
                .sourceName("repo").metadataPrefix("oai_dc")
                .dspace(DumDSpaceProfile.builder(REST + "/", rest).build()).build();
        source.discover();
        source.extract(ID);

        assertEquals(OAI + "?verb=ListRecords&metadataPrefix=dim", oaiUrls.getFirst());
        assertSame(DumDSpaceProfile.SCHEMA, source.schema());
        assertFalse(rest.urls.isEmpty());
        assertTrue(rest.urls.stream().allMatch(u -> u.startsWith(REST + "/")), rest.urls.toString());
        assertTrue(rest.urls.stream().noneMatch(u -> u.contains("/server/")), rest.urls.toString());
        assertTrue(rest.hosts.stream().allMatch("repo.example"::equals));
    }

    @Test
    void namesThatCannotBeReadAreARunNote() {
        rest.publicItem("123456789/7", "u7", bundles());
        rest.status.put(REST + "/core/communities?page=0&size=100", 500);
        DumOaiPmhSource source = harvest(false, null);
        DumStructuredRecord r = source.extract(ID).orElseThrow();

        assertNull(r.values().get("community"));
        assertEquals(List.of("Teses"), r.values().get("collection"));
        assertTrue(source.runNotes().getFirst().startsWith("DSpace communities names unavailable: HTTP 500"));
    }

    @Test
    void theHandleComesFromTheUriElseTheOaiIdentifier() {
        assertEquals(Optional.of("123456789/7"), DumDSpaceProfile.handle(item(ID, "http://hdl.handle.net/123456789/7/")));
        assertEquals(Optional.of("10.1/77"), DumDSpaceProfile.handle(item("oai:x:other", "https://r.example/handle/10.1/77?x=1")));
        assertEquals(Optional.of("123456789/7"), DumDSpaceProfile.handle(item(ID, "https://doi.org/10.1/abc")));
        assertEquals(Optional.empty(), DumDSpaceProfile.handle(item("oai:x:42", "urn:nbn:1")));
        assertEquals("col_123456789_9", DumDSpaceProfile.setSpec("col_", "123456789/9"));
        assertEquals(Optional.of("https://r.example/handle/1/2"), DumDSpaceProfile.handleUrl(
                List.of("https://doi.org/10.1/x", "https://r.example/handle/1/2"), "r.example"));
        assertEquals(Optional.of("https://r.example/handle/1/2"), DumDSpaceProfile.handleUrl(
                List.of("http://10.17.50.146:4000/handle/1/2", "https://r.example/handle/1/2"), "r.example"),
                "a handle link on a private address is never the record's URL");
    }

    @Test
    void refusesARestUrlThatIsNotHttp() {
        assertThrows(IllegalArgumentException.class, () -> DumDSpaceProfile.builder(null, rest));
        assertThrows(IllegalArgumentException.class, () -> DumDSpaceProfile.builder("ftp://repo.example/api", rest));
    }

    // --- helpers -------------------------------------------------------------------------------

    private DumOaiPmhSource harvest(boolean fullText, String siteKey) {
        return harvest(listRecords(record()), fullText, siteKey);
    }

    private DumOaiPmhSource harvest(String page, boolean fullText, String siteKey) {
        DumOaiPmhSource source = DumOaiPmhSource.builder(OAI, recording(page)).sourceName("repo")
                .dspace(DumDSpaceProfile.builder(REST, rest).fullText(fullText).siteKey(siteKey).build())
                .build();
        source.discover();
        return source;
    }

    private DumOaiPmhFetcher recording(String page) {
        return url -> {
            oaiUrls.add(url);
            return page;
        };
    }

    private static DumOaiPmhResponse.Item item(String identifier, String uri) {
        return new DumOaiPmhResponse.Item(identifier, null, false, Map.of(), List.of(),
                List.of(new DumOaiPmhResponse.DimField("dc", "identifier", "uri", uri)));
    }

    private static String record() {
        return dimRecord(ID, List.of("com_123456789_1", "com_123456789_2", "col_123456789_3", "col_123456789_99"), """
                <dim:field mdschema="dc" element="title" lang="pt_BR">Solos do Cerrado</dim:field>
                <dim:field mdschema="dc" element="contributor" qualifier="author">Silva, Ana</dim:field>
                <dim:field mdschema="dc" element="contributor" qualifier="author">Souza, Bruno</dim:field>
                <dim:field mdschema="dc" element="contributor" qualifier="advisor1">Lima, Carla</dim:field>
                <dim:field mdschema="dc" element="contributor" qualifier="advisor-co1">Rocha, Davi</dim:field>
                <dim:field mdschema="dc" element="contributor" qualifier="referee1">Banca, Eva</dim:field>
                <dim:field mdschema="dc" element="date" qualifier="accessioned">2026-01-01T10:00:00Z</dim:field>
                <dim:field mdschema="dc" element="date" qualifier="issued">2021-03</dim:field>
                <dim:field mdschema="dc" element="description" qualifier="abstract">Resumo da tese.</dim:field>
                <dim:field mdschema="dc" element="description" qualifier="provenance">Submitted by x</dim:field>
                <dim:field mdschema="dc" element="type">Tese</dim:field>
                <dim:field mdschema="dc" element="subject">Carbono</dim:field>
                <dim:field mdschema="dc" element="subject" qualifier="cnpq">CNPQ::CIENCIAS AGRARIAS</dim:field>
                <dim:field mdschema="dc" element="language" qualifier="iso">por</dim:field>
                <dim:field mdschema="dc" element="publisher">Universidade Exemplo</dim:field>
                <dim:field mdschema="dc" element="publisher" qualifier="program">PPG Solos</dim:field>
                <dim:field mdschema="dc" element="identifier" qualifier="citation">SILVA, A. Solos.</dim:field>
                <dim:field mdschema="dc" element="identifier" qualifier="uri">https://repo.example/handle/123456789/7</dim:field>
                """);
    }

    private static String dimRecord(String identifier, List<String> sets, String fields) {
        StringBuilder specs = new StringBuilder();
        sets.forEach(s -> specs.append("<setSpec>").append(s).append("</setSpec>"));
        return """
                <record><header><identifier>%s</identifier><datestamp>2026-10-30T00:00:00Z</datestamp>%s</header>
                <metadata><dim:dim xmlns:dim="http://www.dspace.org/xmlns/dspace/dim">%s</dim:dim></metadata>
                </record>
                """.formatted(identifier, specs, fields);
    }

    private static String listRecords(String... records) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/">
                  <responseDate>2026-11-05T11:00:00Z</responseDate>
                  <ListRecords>%s</ListRecords>
                </OAI-PMH>
                """.formatted(String.join("", records));
    }

    private static String bundles() {
        return """
                {"_embedded":{"bundles":[
                  {"name":"ORIGINAL","uuid":"b1","_embedded":{"bitstreams":{"_embedded":{"bitstreams":[
                    {"uuid":"pdf1","name":"tese.pdf"}]}}}},
                  {"name":"TEXT","uuid":"b2","_embedded":{"bitstreams":{"_embedded":{"bitstreams":[
                    {"uuid":"txt1","name":"tese.pdf.txt"}]}}}},
                  {"name":"THUMBNAIL","uuid":"b3","_embedded":{"bitstreams":{"_embedded":{"bitstreams":[
                    {"uuid":"th1","name":"tese.pdf.jpg"}]}}}}]}}
                """;
    }

    /** Canned REST answers, keyed by URL; anything unknown is a 404. */
    private static final class FakeRest implements DumDSpaceRest {
        final Map<String, String> json = new HashMap<>();
        final Map<String, Integer> status = new HashMap<>();
        final Map<String, Optional<String>> text = new HashMap<>();
        final Map<String, String> failing = new HashMap<>();
        final List<String> urls = new ArrayList<>();
        final List<String> hosts = new ArrayList<>();
        final List<String> textCalls = new ArrayList<>();

        FakeRest() {
            json.put(REST + "/core/communities?page=0&size=100", """
                    {"_embedded":{"communities":[
                      {"handle":"123456789/1","name":"Programa de Pós-Graduação"}]},
                     "page":{"number":0,"totalPages":2}}
                    """);
            json.put(REST + "/core/communities?page=1&size=100", """
                    {"_embedded":{"communities":[{"handle":"123456789/2","name":"Ciências Agrárias"}]},
                     "page":{"number":1,"totalPages":2}}
                    """);
            json.put(REST + "/core/collections?page=0&size=100", """
                    {"_embedded":{"collections":[{"handle":"123456789/3","name":"Teses"}]},
                     "page":{"number":0,"totalPages":1}}
                    """);
        }

        void publicItem(String handle, String uuid, String bundles) {
            json.put(REST + "/pid/find?id=" + handle,
                    "{\"uuid\":\"%s\",\"withdrawn\":false,\"discoverable\":true}".formatted(uuid));
            json.put(REST + "/core/items/" + uuid + "/bundles?size=100&embed=bitstreams", bundles);
        }

        long calls(String fragment) {
            return urls.stream().filter(u -> u.contains(fragment)).count();
        }

        @Override
        public Response get(String url, String host) {
            urls.add(url);
            hosts.add(host);
            if (status.containsKey(url)) {
                return new Response(status.get(url), "{}");
            }
            String body = json.get(url);
            return body == null ? new Response(404, "{}") : new Response(200, body);
        }

        @Override
        public Optional<String> text(String url, String host) throws IOException {
            urls.add(url);
            hosts.add(host);
            textCalls.add(url);
            if (failing.containsKey(url)) {
                throw new IOException(failing.get(url));
            }
            return text.getOrDefault(url, Optional.empty());
        }
    }
}

/*
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

package com.viglet.dumont.commons.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.commons.indexing.DumIndexingStatus;
import com.viglet.dumont.commons.indexing.DumLoggingStatus;

import tools.jackson.databind.json.JsonMapper;

class DumLoggingTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void generalBuilderAndGettersRoundTrip() {
        DumLoggingGeneral general = DumLoggingGeneral.builder()
                .clusterNode("node-1").level("INFO").logger("com.viglet")
                .message("hello").stackTrace("trace")
                .date(Instant.ofEpochMilli(1_700_000_000_000L)).build();

        assertThat(general.getClusterNode()).isEqualTo("node-1");
        assertThat(general.getLevel()).isEqualTo("INFO");
        assertThat(general.getMessage()).isEqualTo("hello");
        general.setMessage("changed");
        assertThat(general.getMessage()).isEqualTo("changed");
    }

    @Test
    void indexingBuilderAndGetters() {
        DumLoggingIndexing indexing = DumLoggingIndexing.builder()
                .status(DumIndexingStatus.INDEXED).source("source-1").contentId("c1")
                .url("http://x").sites(List.of("site")).environment("prod")
                .locale(Locale.US).transactionId("tx").checksum("sum")
                .resultStatus(DumLoggingStatus.SUCCESS).details("ok")
                .date(Instant.EPOCH).build();

        assertThat(indexing.getStatus()).isEqualTo(DumIndexingStatus.INDEXED);
        assertThat(indexing.getResultStatus()).isEqualTo(DumLoggingStatus.SUCCESS);
        assertThat(indexing.getSites()).containsExactly("site");
        indexing.setDetails("changed");
        assertThat(indexing.getDetails()).isEqualTo("changed");
    }

    @Test
    void isoDateSerializerEmitsAnIsoStringNotMongoShellSyntax() {
        // D73 — it used to write a raw `ISODate("…")`, which Mongo's Document.parse accepts and
        // every JSON reader rejects. The Redis appender writes the same payload and the log API
        // reads it back with Jackson, so one engine's convenience was the other's outage.
        DumLoggingGeneral general = DumLoggingGeneral.builder()
                .message("m").date(Instant.ofEpochMilli(1_700_000_000_000L)).build();

        String json = mapper.writeValueAsString(general);

        assertThat(json).doesNotContain("ISODate(");
        assertThat(mapper.readTree(json).get("date").asString())
                .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(1_700_000_000_000L),
                        ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

    @Test
    void aMongoRowStillStoresARealDate() {
        // The BSON date the serializer used to smuggle out is now made at the appender, so the
        // date-range filter still compares dates to dates.
        DumLoggingIndexing entry = DumLoggingIndexing.builder().contentId("c1")
                .date(Instant.ofEpochMilli(1_700_000_000_000L)).build();

        Document document = DumMongoDBIndexingAppender
                .withBsonDate(Document.parse(mapper.writeValueAsString(entry)));

        assertThat(document.get("date")).isInstanceOf(Date.class);
        assertThat(((Date) document.get("date")).toInstant())
                .isEqualTo(Instant.ofEpochMilli(1_700_000_000_000L));
    }

    @Test
    void anUnreadableDateKeepsTheRowRatherThanLosingTheEvent() {
        Document document = DumMongoDBIndexingAppender
                .withBsonDate(Document.parse("{\"contentId\":\"c1\",\"date\":\"not-a-date\"}"));

        assertThat(document.get("date")).isEqualTo("not-a-date");
        assertThat(document.get("contentId")).isEqualTo("c1");
    }

    @Test
    void isoDateSerializerWritesNullForNullInstant() {
        DumLoggingGeneral general = DumLoggingGeneral.builder().message("m").date(null).build();

        String json = mapper.writeValueAsString(general);

        assertThat(json).contains("\"date\":null");
    }

    @Test
    void indexingRowCanBeReadBackAsJson() {
        DumLoggingIndexing entry = DumLoggingIndexing.builder().contentId("c1").source("wknd")
                .date(Instant.ofEpochMilli(1_700_000_000_000L)).build();

        String json = mapper.writeValueAsString(entry);

        assertThatCode(() -> mapper.readTree(json)).doesNotThrowAnyException();
    }

    @Test
    void indexingLogSetStatusDoesNotThrow() {
        DumLoggingIndexing entry = DumLoggingIndexing.builder()
                .status(DumIndexingStatus.INDEXED).source("s").build();
        assertThatCode(() -> DumLoggingIndexingLog.setStatus(entry)).doesNotThrowAnyException();
    }

    @Test
    void statusEnumsExposeValues() {
        assertThat(DumIndexingStatus.valueOf("INDEXED")).isEqualTo(DumIndexingStatus.INDEXED);
        assertThat(DumIndexingStatus.values()).contains(DumIndexingStatus.DEINDEXED);
        assertThat(DumLoggingStatus.valueOf("ERROR")).isEqualTo(DumLoggingStatus.ERROR);
        assertThat(DumLoggingStatus.values()).hasSize(2);
    }
}

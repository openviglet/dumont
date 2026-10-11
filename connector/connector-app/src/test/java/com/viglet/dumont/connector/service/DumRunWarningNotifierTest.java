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

package com.viglet.dumont.connector.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingStatsModel;

/** A warned run reaches the operator's webhook, and only there (roadmap D130). */
class DumRunWarningNotifierTest {

    private static final String URL = "https://alerts.example.com/hook";

    private static DumConnectorIndexingStatsModel warnedRun() {
        return DumConnectorIndexingStatsModel.builder().source("adzuna").provider("JOBS").documentCount(12)
                .warnings(new ArrayList<>(List.of("new postings fell 92% under the 7-day median"))).build();
    }

    @Test
    void postsTheRunAndItsWarningsWithoutTuringsKey() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"event":"run.warned","source":"adzuna","provider":"JOBS","documentCount":12,
                         "warnings":["new postings fell 92% under the 7-day median"]}"""))
                .andExpect(request -> assertThat(request.getHeaders().containsHeader("Key")).isFalse())
                .andRespond(withSuccess());

        new DumRunWarningNotifier(URL, builder.build()).runStartedWarning(warnedRun());

        server.verify();
    }

    @Test
    void aFailedDeliveryIsLoggedAndNeverThrown() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andExpect(header("Content-Type", "application/json"))
                .andRespond(withServerError());

        assertThatCode(() -> new DumRunWarningNotifier(URL, builder.build()).runStartedWarning(warnedRun()))
                .doesNotThrowAnyException();
    }

    @Test
    void withNoUrlItIsOffAndSendsNothing() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DumRunWarningNotifier off = new DumRunWarningNotifier("", builder.build());

        off.runStartedWarning(warnedRun());

        assertThat(off.enabled()).isFalse();
        server.verify();
    }
}
